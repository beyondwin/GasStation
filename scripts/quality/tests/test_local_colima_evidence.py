from __future__ import annotations

import json
import hashlib
import os
import subprocess
import stat
import tempfile
import unittest
import warnings
import zipfile
from unittest import mock
from pathlib import Path

from scripts.quality.build_inputs.android_repository import (
    capture_installed_android_packages,
    validate_android_repository_inventory,
)
from scripts.quality.build_inputs.contracts import BuildInputError, canonical_json_bytes, load_policy
from scripts.quality.build_inputs.local_colima_evidence import (
    CLEANUP_PHASES,
    CONFIG_DESCRIPTOR,
    CONTAINER_INHERITED_LABELS,
    DELETE_ARGV,
    INDEX_DESCRIPTOR,
    HOST_MINIMUM,
    LAYER_DESCRIPTORS,
    SELECTED_MANIFEST_DESCRIPTOR,
    START_ARGV,
    _recover_prior_attempts,
    _run_governed_container_command,
    _write_failed_attempt_package,
    _container_labels,
    _owned_labels,
    _runtime_data_identity,
    _safe_error,
    _profile_config,
    aggregate_receipt,
    command_line_tools_bootstrap_commands,
    docker_argv,
    isolated_runtime_root,
    next_attempt,
    ownership_marker,
    safe_extract_command_line_tools,
    sanitized_host_environment,
    validate_bundle_heads,
    validate_cli,
    validate_cleanup_proof,
    validate_command_line_tools_bootstrap_commands,
    validate_context_inventory,
    validate_container_selection,
    validate_effective_config,
    validate_image_identity,
    validate_inner_architecture,
    validate_host_resources,
    validate_runtime_absence,
    validate_governed_command_evidence,
)
from scripts.quality.build_inputs.generate_policy import policy as generated_policy
from scripts.quality.verify_build_inputs import _run_group


SOURCE = "1" * 40
BASE = "7b8c149c9f792aaf43cc00a94ba671929008979e"
POLICY_SHA = "2" * 64


def repository_xml_fixture(*, coordinate: str = "platforms;android-37.0", revision: int = 2) -> bytes:
    return f"""<?xml version='1.0' encoding='utf-8'?>
<sdk:sdk-repository xmlns:sdk="http://schemas.android.com/sdk/android/repo/repository2/03" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <remotePackage path="{coordinate}">
    <type-details xsi:type="sdk:platformDetailsType">
      <api-level>37.0</api-level><codename></codename><extension-level>22</extension-level>
      <base-extension>true</base-extension><layoutlib api="15"/>
    </type-details>
    <revision><major>{revision}</major></revision>
    <display-name>Android SDK Platform 37.0</display-name>
    <channelRef ref="channel-0"/>
    <archives><archive><complete><size>67281901</size>
      <checksum type="sha1">ed8ebf7f8822a4de5686d427f237d2fa30ff7410</checksum>
      <url>platform-37.0_r02.zip</url>
    </complete></archive></archives>
  </remotePackage>
</sdk:sdk-repository>
""".encode()


def image_inspect_fixture() -> str:
    return json.dumps(
        [
            {
                "Architecture": "",
                "Config": {},
                "Descriptor": dict(INDEX_DESCRIPTOR),
                "Id": INDEX_DESCRIPTOR["digest"],
                "Os": "",
                "RepoDigests": ["ubuntu@" + INDEX_DESCRIPTOR["digest"]],
                "RootFS": {},
                "Size": 7112,
            },
        ],
    )


def manifest_inspect_fixture() -> str:
    return json.dumps(
        [
            {
                "Descriptor": dict(SELECTED_MANIFEST_DESCRIPTOR),
                "OCIManifest": {
                    "config": dict(CONFIG_DESCRIPTOR),
                    "layers": [dict(row) for row in LAYER_DESCRIPTORS],
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "schemaVersion": 2,
                },
                "Ref": "docker.io/library/ubuntu@" + INDEX_DESCRIPTOR["digest"],
            },
            {
                "Descriptor": {
                    "digest": "sha256:" + "9" * 64,
                    "mediaType": "application/vnd.oci.image.manifest.v1+json",
                    "platform": {"architecture": "arm64", "os": "linux"},
                    "size": 424,
                },
                "OCIManifest": {},
            },
        ],
    )


class LocalColimaEvidenceContractTest(unittest.TestCase):
    def test_failed_governed_command_is_recorded_before_raise_with_terminal_cause(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            attempt = Path(directory)
            completed = subprocess.CompletedProcess(
                args=["docker"],
                returncode=9,
                stdout=b"prefix\nterminal failure token=secret-value /opt/android-sdk/private\n",
            )

            def complete_after_start(*_args: object, **_kwargs: object) -> subprocess.CompletedProcess[bytes]:
                self.assertTrue(
                    (attempt / "command-evidence/strict-complete.started.json").is_file(),
                    "the immutable STARTED receipt must exist before Docker execution",
                )
                return completed

            with mock.patch(
                "scripts.quality.build_inputs.local_colima_evidence._container_exec_completed",
                side_effect=complete_after_start,
            ):
                with self.assertRaisesRegex(BuildInputError, "strict-complete"):
                    _run_governed_container_command(
                        Path("/tmp/docker-client"),
                        attempt=attempt,
                        name="strict-complete",
                        shell="python3 scripts/quality/verify_build_inputs.py metadata-capture",
                    )

            evidence = attempt / "command-evidence"
            rows = validate_governed_command_evidence(evidence)
            self.assertEqual("FAIL", rows[0]["status"])
            self.assertEqual(9, rows[0]["exitCode"])
            log = (evidence / "strict-complete.log").read_text()
            self.assertIn("terminal failure", log)
            self.assertIn("<redacted-secret>", log)
            self.assertIn("<redacted-path>", log)
            self.assertNotIn("secret-value", log)

            failure = {"error": "governed failure", "schemaVersion": 1, "status": "FAIL"}
            _write_failed_attempt_package(attempt, failure)
            package = attempt / "failure-package"
            manifest = json.loads((package / "failed-attempt-package.json").read_text())
            paths = {row["path"] for row in manifest["files"]}
            self.assertIn("failure.json", paths)
            self.assertIn("command-evidence/strict-complete.log", paths)
            self.assertEqual(
                hashlib.sha256((package / "command-evidence/strict-complete.log").read_bytes()).hexdigest(),
                next(row["sha256"] for row in manifest["files"] if row["path"].endswith(".log")),
            )

    def test_governed_command_evidence_rejects_missing_log_generic_collapse_and_nonzero_pass(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            evidence = Path(directory)
            started = {
                "commandSha256": "1" * 64,
                "name": "strict-complete",
                "schemaVersion": 1,
                "status": "STARTED",
            }
            (evidence / "strict-complete.started.json").write_bytes(canonical_json_bytes(started))
            result = {
                "commandSha256": "1" * 64,
                "exitCode": 2,
                "logSha256": hashlib.sha256(b"generic\n").hexdigest(),
                "logSize": 8,
                "name": "strict-complete",
                "schemaVersion": 1,
                "status": "FAIL",
                "truncated": False,
            }
            (evidence / "strict-complete.result.json").write_bytes(canonical_json_bytes(result))
            with self.assertRaisesRegex(BuildInputError, "missing log"):
                validate_governed_command_evidence(evidence)

            generic = b"build-input verification failed: governed command failed: gradlew\n"
            (evidence / "strict-complete.log").write_bytes(generic)
            result["logSha256"] = hashlib.sha256(generic).hexdigest()
            result["logSize"] = len(generic)
            (evidence / "strict-complete.result.json").write_bytes(canonical_json_bytes(result))
            with self.assertRaisesRegex(BuildInputError, "generic-only"):
                validate_governed_command_evidence(evidence)

            detailed = b"terminal dependency verification cause\n"
            (evidence / "strict-complete.log").write_bytes(detailed)
            result.update(
                logSha256=hashlib.sha256(detailed).hexdigest(),
                logSize=len(detailed),
                status="PASS",
            )
            (evidence / "strict-complete.result.json").write_bytes(canonical_json_bytes(result))
            with self.assertRaisesRegex(BuildInputError, "nonzero"):
                validate_governed_command_evidence(evidence)

    def test_android_repository_inventory_is_exact_and_source_bound(self) -> None:
        body = repository_xml_fixture()
        contract = json.loads(json.dumps(generated_policy()["android"]["repositoryInventory"]))
        contract["repositorySha256"] = hashlib.sha256(body).hexdigest()
        receipt = validate_android_repository_inventory(body, contract)
        self.assertEqual("PASS", receipt["status"])
        self.assertEqual(["platforms;android-37"], receipt["absentCoordinates"])
        self.assertEqual("platforms;android-37.0", receipt["acceptedRecord"]["coordinate"])
        self.assertEqual(len(body), receipt["repositorySize"])

        record = body.split(b"<remotePackage", 1)[1].split(b"</remotePackage>", 1)[0]
        duplicate = body.replace(
            b"</sdk:sdk-repository>",
            b"<remotePackage" + record + b"</remotePackage>\n</sdk:sdk-repository>",
        )
        mutations = (
            repository_xml_fixture(coordinate="platforms;android-37"),
            repository_xml_fixture(revision=1),
            body.replace(b"<api-level>37.0", b"<api-level>37.1"),
            body.replace(b"<extension-level>22", b"<extension-level>21"),
            body.replace(b"layoutlib api=\"15\"", b"layoutlib api=\"14\""),
            body.replace(b"Android SDK Platform 37.0", b"Android SDK Platform 37.1"),
            body.replace(b"channel-0", b"channel-1"),
            body.replace(b"platform-37.0_r02.zip", b"platform-37.zip"),
            body.replace(b"<size>67281901", b"<size>67281900"),
            body.replace(b"<base-extension>true", b"<base-extension>false"),
            body.replace(b"ed8ebf7f8822a4de5686d427f237d2fa30ff7410", b"0" * 40),
            duplicate,
        )
        for index, mutation in enumerate(mutations):
            mutated_contract = json.loads(json.dumps(contract))
            mutated_contract["repositorySha256"] = hashlib.sha256(mutation).hexdigest()
            with self.subTest(index=index), self.assertRaises(BuildInputError):
                validate_android_repository_inventory(mutation, mutated_contract)
        with self.assertRaisesRegex(BuildInputError, "SHA-256"):
            validate_android_repository_inventory(body + b" ", contract)

    def test_installed_android_packages_bind_source_three_packages_and_six_binary_roles(self) -> None:
        body = repository_xml_fixture()
        policy = json.loads(json.dumps(generated_policy()))
        android = policy["android"]
        contract = android["repositoryInventory"]
        contract["repositorySha256"] = hashlib.sha256(body).hexdigest()
        source_receipt = validate_android_repository_inventory(body, contract)
        package_rows = {
            "build-tools/36.0.0": (
                "build-tools;36.0.0",
                "<revision><major>36</major></revision><display-name>Android SDK Build-Tools 36</display-name>",
            ),
            "platforms/android-37.0": (
                "platforms;android-37.0",
                "<type-details xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:type='sdk:platformDetailsType'>"
                "<api-level>37.0</api-level><codename></codename><extension-level>22</extension-level>"
                "<base-extension>true</base-extension><layoutlib api='15'/></type-details>"
                "<revision><major>2</major></revision><display-name>Android SDK Platform 37.0</display-name>",
            ),
            "platform-tools": (
                "platform-tools",
                "<revision><major>36</major></revision><display-name>Android SDK Platform-Tools</display-name>",
            ),
        }
        with tempfile.TemporaryDirectory() as directory:
            sdk = Path(directory)
            source_properties = sdk / "cmdline-tools/latest/source.properties"
            source_properties.parent.mkdir(parents=True)
            source_properties.write_bytes(
                b"Pkg.Revision=22.0\n"
                b"Pkg.Path=cmdline-tools;22.0\n"
                b"Pkg.Desc=Android SDK Command-line Tools\n"
            )
            source_properties.chmod(0o644)
            for relative, (coordinate, details) in package_rows.items():
                package = sdk / relative / "package.xml"
                package.parent.mkdir(parents=True)
                package.write_text(
                    f"<repository><localPackage path='{coordinate}'>{details}</localPackage></repository>\n",
                    encoding="utf-8",
                )
                package.chmod(0o644)
            for relative in (
                "build-tools/36.0.0/aapt2",
                "build-tools/36.0.0/apksigner",
                "build-tools/36.0.0/zipalign",
                "cmdline-tools/latest/bin/avdmanager",
                "cmdline-tools/latest/bin/sdkmanager",
                "platform-tools/adb",
            ):
                binary = sdk / relative
                binary.parent.mkdir(parents=True, exist_ok=True)
                binary.write_bytes(relative.encode())
                binary.chmod(0o755)
            receipt = capture_installed_android_packages(policy, sdk, source_receipt)
            self.assertEqual("PASS", receipt["status"])
            self.assertEqual("platforms;android-37.0", receipt["requestedPlatformCoordinate"])
            self.assertEqual(3, len(receipt["packages"]))
            self.assertEqual(6, len(receipt["binaries"]))
            self.assertEqual(
                {
                    "coordinate": "cmdline-tools;22.0",
                    "fields": [
                        "Pkg.Revision=22.0",
                        "Pkg.Path=cmdline-tools;22.0",
                        "Pkg.Desc=Android SDK Command-line Tools",
                    ],
                    "mode": "0644",
                    "ownerRole": "command-line-tools-archive",
                    "relativePath": "cmdline-tools/latest/source.properties",
                    "sha256": "166bcdfe54f73296b09e5e6aa6d96b9a752b78b418c56e9f3f3a13c15fac74e5",
                    "size": 86,
                },
                receipt["commandLineToolsSource"],
            )
            self.assertEqual(
                {
                    "build-tools;36.0.0",
                    "platform-tools",
                    "platforms;android-37.0",
                },
                {row["ownerRole"] for row in receipt["packages"]},
            )
            self.assertTrue(all(row["packageXml"]["mode"] == "0644" for row in receipt["packages"]))
            self.assertTrue(all(row["mode"] == "0755" for row in receipt["binaries"]))
            self.assertEqual(
                [
                    "build-tools/36.0.0/aapt2",
                    "build-tools/36.0.0/apksigner",
                    "build-tools/36.0.0/zipalign",
                    "cmdline-tools/latest/bin/avdmanager",
                    "cmdline-tools/latest/bin/sdkmanager",
                    "platform-tools/adb",
                ],
                [row["relativePath"] for row in receipt["binaries"]],
            )

            platform = sdk / "platforms/android-37.0/package.xml"
            original = platform.read_text(encoding="utf-8")
            platform.write_text(original.replace("<major>2", "<major>1"), encoding="utf-8")
            with self.assertRaises(BuildInputError):
                capture_installed_android_packages(policy, sdk, source_receipt)
            platform.write_text(original.replace("android-37.0", "android-37"), encoding="utf-8")
            with self.assertRaises(BuildInputError):
                capture_installed_android_packages(policy, sdk, source_receipt)

            platform.write_text(original, encoding="utf-8")
            source_properties.write_bytes(source_properties.read_bytes().replace(b"22.0", b"21.0", 1))
            with self.assertRaises(BuildInputError):
                capture_installed_android_packages(policy, sdk, source_receipt)
            source_properties.write_bytes(
                b"Pkg.Revision=22.0\nPkg.Path=cmdline-tools;22.0\n"
                b"Pkg.Desc=Android SDK Command-line Tools\n"
            )
            source_properties.chmod(0o755)
            with self.assertRaises(BuildInputError):
                capture_installed_android_packages(policy, sdk, source_receipt)

            source_properties.chmod(0o644)
            fake_package = sdk / "cmdline-tools/latest/package.xml"
            fake_package.write_text("<repository/>\n", encoding="utf-8")
            with self.assertRaises(BuildInputError):
                capture_installed_android_packages(policy, sdk, source_receipt)
            fake_package.unlink()

            platform.chmod(0o600)
            with self.assertRaises(BuildInputError):
                capture_installed_android_packages(policy, sdk, source_receipt)
            platform.chmod(0o644)

            aapt2 = sdk / "build-tools/36.0.0/aapt2"
            aapt2.chmod(0o644)
            with self.assertRaises(BuildInputError):
                capture_installed_android_packages(policy, sdk, source_receipt)

    @staticmethod
    def _write_command_line_tools_zip(
        path: Path,
        *,
        extras: list[tuple[str, int, bytes, int]] | None = None,
        sdkmanager_mode: int = stat.S_IFREG | 0o755,
    ) -> None:
        rows = [
            ("cmdline-tools/bin/sdkmanager", sdkmanager_mode, b"#!/bin/sh\n", 3),
            ("cmdline-tools/bin/avdmanager", stat.S_IFREG | 0o755, b"#!/bin/sh\n", 3),
            # The reviewed Google archive marks ordinary payload files executable;
            # extraction must narrow those modes to non-executable regular files.
            ("cmdline-tools/NOTICE.txt", stat.S_IFREG | 0o755, b"notice\n", 3),
            (
                "cmdline-tools/source.properties",
                stat.S_IFREG | 0o755,
                b"Pkg.Revision=22.0\nPkg.Path=cmdline-tools;22.0\n"
                b"Pkg.Desc=Android SDK Command-line Tools\n",
                3,
            ),
        ]
        rows.extend(extras or [])
        with zipfile.ZipFile(path, "w") as archive, warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            for name, mode, payload, create_system in rows:
                info = zipfile.ZipInfo(name)
                info.create_system = create_system
                info.external_attr = mode << 16
                archive.writestr(info, payload)

    def test_command_line_tools_extraction_restores_only_reviewed_executables(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "command-line-tools.zip"
            destination = root / "extracted"
            self._write_command_line_tools_zip(archive)

            safe_extract_command_line_tools(archive, destination)

            self.assertEqual(
                0o755,
                stat.S_IMODE((destination / "cmdline-tools/bin/sdkmanager").stat().st_mode),
            )
            self.assertEqual(
                0o755,
                stat.S_IMODE((destination / "cmdline-tools/bin/avdmanager").stat().st_mode),
            )
            self.assertEqual(
                0o644,
                stat.S_IMODE((destination / "cmdline-tools/NOTICE.txt").stat().st_mode),
            )
            self.assertEqual(
                0o644,
                stat.S_IMODE((destination / "cmdline-tools/source.properties").stat().st_mode),
            )

    def test_command_line_tools_archive_inventory_rejects_fabricated_package_xml(self) -> None:
        source_bytes = (
            b"Pkg.Revision=22.0\nPkg.Path=cmdline-tools;22.0\n"
            b"Pkg.Desc=Android SDK Command-line Tools\n"
        )

        def contract_for(archive_path: Path) -> dict[str, object]:
            with zipfile.ZipFile(archive_path) as archive:
                names = [row.filename for row in archive.infolist()]
            return {
                "archiveMemberCount": len(names),
                "archiveMemberListingSha256": hashlib.sha256(
                    ("\n".join(names) + "\n").encode("utf-8"),
                ).hexdigest(),
                "archiveSha256": hashlib.sha256(archive_path.read_bytes()).hexdigest(),
                "archiveSize": archive_path.stat().st_size,
                "archiveUrl": "https://dl.google.com/android/repository/fixture.zip",
                "sourceProperties": {
                    "coordinate": "cmdline-tools;22.0",
                    "fields": [
                        "Pkg.Revision=22.0",
                        "Pkg.Path=cmdline-tools;22.0",
                        "Pkg.Desc=Android SDK Command-line Tools",
                    ],
                    "mode": "0644",
                    "relativePath": "cmdline-tools/latest/source.properties",
                    "sha256": hashlib.sha256(source_bytes).hexdigest(),
                    "size": len(source_bytes),
                    "storedMode": "100755",
                },
            }

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            valid = root / "valid.zip"
            self._write_command_line_tools_zip(valid)
            safe_extract_command_line_tools(
                valid,
                root / "valid-out",
                command_line_tools=contract_for(valid),
            )

            fabricated = root / "fabricated-package.zip"
            self._write_command_line_tools_zip(
                fabricated,
                extras=[("cmdline-tools/package.xml", stat.S_IFREG | 0o755, b"<repository/>\n", 3)],
            )
            with self.assertRaisesRegex(BuildInputError, "archive inventory"):
                safe_extract_command_line_tools(
                    fabricated,
                    root / "fabricated-out",
                    command_line_tools=contract_for(fabricated),
                )

    def test_command_line_tools_extraction_rejects_unsafe_archive_mutations(self) -> None:
        mutations = {
            "traversal": [("../escape", stat.S_IFREG | 0o644, b"x", 3)],
            "absolute": [("/absolute", stat.S_IFREG | 0o644, b"x", 3)],
            "backslash": [("cmdline-tools\\escape", stat.S_IFREG | 0o644, b"x", 3)],
            "symlink": [("cmdline-tools/link", stat.S_IFLNK | 0o777, b"target", 3)],
            "special": [("cmdline-tools/fifo", stat.S_IFIFO | 0o644, b"", 3)],
            "unsupported-mode": [("cmdline-tools/too-open", stat.S_IFREG | 0o777, b"x", 3)],
            "non-unix": [("cmdline-tools/non-unix", stat.S_IFREG | 0o644, b"x", 0)],
            "duplicate": [("cmdline-tools/NOTICE.txt", stat.S_IFREG | 0o755, b"again", 3)],
            "case-collision": [("CMDLINE-TOOLS/NOTICE.TXT", stat.S_IFREG | 0o755, b"again", 3)],
            "file-directory-collision": [("cmdline-tools/bin", stat.S_IFREG | 0o644, b"x", 3)],
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for label, extras in mutations.items():
                archive = root / f"{label}.zip"
                destination = root / f"{label}-out"
                self._write_command_line_tools_zip(archive, extras=extras)
                with self.subTest(label=label), self.assertRaises(BuildInputError):
                    safe_extract_command_line_tools(archive, destination)
                self.assertFalse(destination.exists())
            bad_sdkmanager = root / "bad-sdkmanager.zip"
            self._write_command_line_tools_zip(
                bad_sdkmanager,
                sdkmanager_mode=stat.S_IFREG | 0o644,
            )
            with self.assertRaises(BuildInputError):
                safe_extract_command_line_tools(bad_sdkmanager, root / "bad-sdkmanager-out")

    def test_command_line_tools_bootstrap_installs_jdk_before_sdkmanager_with_sealed_environment(self) -> None:
        commands = command_line_tools_bootstrap_commands()
        install_index = next(index for index, command in enumerate(commands) if " install-jdks " in command)
        archive_index = next(index for index, command in enumerate(commands) if "cmdline.zip" in command)
        sdkmanager_indexes = [
            index
            for index, command in enumerate(commands)
            if "/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager" in command
        ]
        self.assertLess(install_index, archive_index)
        self.assertTrue(sdkmanager_indexes)
        self.assertTrue(all(install_index < index for index in sdkmanager_indexes))
        repository_index = next(index for index, command in enumerate(commands) if "android_repository fetch" in command)
        installed_index = next(index for index, command in enumerate(commands) if "android_repository installed" in command)
        package_install_index = next(
            index for index, command in enumerate(commands) if "'platforms;android-37.0'" in command
        )
        self.assertLess(repository_index, package_install_index)
        self.assertLess(package_install_index, installed_index)
        self.assertFalse(any("'platforms;android-37'" in command for command in commands))
        expected_environment = (
            "env JAVA_HOME=/evidence-work/bootstrap-jdks/"
            "runtime-ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94 "
            "PATH=/evidence-work/bootstrap-jdks/"
            "runtime-ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94/"
            "bin:/usr/local/bin:/usr/bin:/bin"
        )
        self.assertTrue(all(expected_environment in commands[index] for index in sdkmanager_indexes))
        self.assertEqual(tuple(commands), validate_command_line_tools_bootstrap_commands(commands))

    def test_command_line_tools_bootstrap_rejects_order_and_environment_mutations(self) -> None:
        baseline = list(command_line_tools_bootstrap_commands())
        install_index = next(index for index, command in enumerate(baseline) if " install-jdks " in command)
        archive_index = next(index for index, command in enumerate(baseline) if "cmdline.zip" in command)
        sdkmanager_index = next(
            index
            for index, command in enumerate(baseline)
            if "/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager" in command
        )
        mutations = []
        missing_install = list(baseline)
        missing_install.pop(install_index)
        mutations.append(missing_install)
        reordered = list(baseline)
        reordered[install_index], reordered[archive_index] = reordered[archive_index], reordered[install_index]
        mutations.append(reordered)
        missing_environment = list(baseline)
        missing_environment[sdkmanager_index] = missing_environment[sdkmanager_index].replace("env JAVA_HOME=", "env HOME=")
        mutations.append(missing_environment)
        wrong_runtime = list(baseline)
        wrong_runtime[sdkmanager_index] = wrong_runtime[sdkmanager_index].replace("runtime-ce79869e", "runtime-00000000")
        mutations.append(wrong_runtime)
        stale_platform = [command.replace("platforms;android-37.0", "platforms;android-37") for command in baseline]
        mutations.append(stale_platform)
        missing_repository_source = [command for command in baseline if "android_repository fetch" not in command]
        mutations.append(missing_repository_source)
        missing_installed_capture = [command for command in baseline if "android_repository installed" not in command]
        mutations.append(missing_installed_capture)
        extra = [*baseline, "java -version"]
        mutations.append(extra)
        for index, mutation in enumerate(mutations):
            with self.subTest(index=index), self.assertRaises(BuildInputError):
                validate_command_line_tools_bootstrap_commands(mutation)

    def test_generated_policy_fixes_the_sole_host_and_aggregate_entrypoint(self) -> None:
        host = generated_policy()["localEvidenceHost"]
        self.assertEqual("gasstation-task9-linux-amd64", host["profile"])
        self.assertEqual("colima-gasstation-task9-linux-amd64", host["context"])
        self.assertEqual(BASE, host["mainBaseCommit"])
        self.assertEqual("refs/heads/main", host["mainBaseRef"])
        self.assertEqual(list(START_ARGV), host["startArgv"])
        self.assertEqual(HOST_MINIMUM, host["hostMinimum"])
        self.assertEqual(14, host["effectiveConfig"]["cpu"])
        self.assertEqual(32, host["effectiveConfig"]["memory"])
        self.assertEqual("14", host["startArgv"][host["startArgv"].index("--cpus") + 1])
        self.assertEqual("32", host["startArgv"][host["startArgv"].index("--memory") + 1])
        self.assertEqual(list(DELETE_ARGV), host["deleteArgv"])
        self.assertEqual([], host["hostMounts"])
        self.assertEqual(sorted(_owned_labels({
            "attemptId": "attempt-000001",
            "mainBaseCommit": BASE,
            "markerSha256": "3" * 64,
            "policySha256": POLICY_SHA,
            "sourceCommit": SOURCE,
            "taskId": "quality-task-9-local-linux-evidence",
        })), host["ownedLabelKeys"])
        self.assertEqual(
            {
                "configDescriptor": CONFIG_DESCRIPTOR,
                "containerSelection": {
                    "configImage": "docker.io/library/ubuntu@" + INDEX_DESCRIPTOR["digest"],
                    "image": INDEX_DESCRIPTOR["digest"],
                    "inheritedLabels": CONTAINER_INHERITED_LABELS,
                    "platform": "linux",
                },
                "indexDescriptor": INDEX_DESCRIPTOR,
                "indexReference": "docker.io/library/ubuntu@" + INDEX_DESCRIPTOR["digest"],
                "layerDescriptors": list(LAYER_DESCRIPTORS),
                "platform": "linux/amd64",
                "selectedManifestDescriptor": SELECTED_MANIFEST_DESCRIPTOR,
                "storeObservation": {
                    "architecture": "",
                    "config": {},
                    "id": INDEX_DESCRIPTOR["digest"],
                    "os": "",
                    "repoDigests": ["ubuntu@" + INDEX_DESCRIPTOR["digest"]],
                    "rootFS": {},
                    "size": 7112,
                },
            },
            host["image"],
        )
        self.assertEqual(
            "scripts/quality/build_inputs/local_colima_evidence.py",
            next(
                row["owner"]
                for row in generated_policy()["evidenceGradleEntrypoints"]
                if row["id"] == "local-colima/aggregate"
            ),
        )

    def test_local_host_policy_mutations_fail_closed(self) -> None:
        baseline = generated_policy()
        mutations = []
        for field, value in (
            ("mainBaseCommit", "4" * 40),
            ("deleteArgv", ["/opt/homebrew/bin/colima", "delete", "gasstation-task9-linux-amd64", "--force"]),
            ("hostMounts", ["/Users/example/source"]),
        ):
            candidate = json.loads(json.dumps(baseline))
            candidate["localEvidenceHost"][field] = value
            mutations.append(candidate)
        unknown = json.loads(json.dumps(baseline))
        unknown["localEvidenceHost"]["effectiveConfig"]["unknown"] = True
        mutations.append(unknown)
        partial = json.loads(json.dumps(baseline))
        partial["localEvidenceHost"]["requiredEvidenceRows"].remove("releaseBinding")
        mutations.append(partial)
        missing_index = json.loads(json.dumps(baseline))
        del missing_index["localEvidenceHost"]["image"]["indexDescriptor"]
        mutations.append(missing_index)
        manifest_as_config = json.loads(json.dumps(baseline))
        manifest_as_config["localEvidenceHost"]["image"]["configDescriptor"] = {
            key: value
            for key, value in manifest_as_config["localEvidenceHost"]["image"]["selectedManifestDescriptor"].items()
            if key != "platform"
        }
        mutations.append(manifest_as_config)
        legacy_alias = json.loads(json.dumps(baseline))
        legacy_alias["localEvidenceHost"]["image"]["configurationDigest"] = SELECTED_MANIFEST_DESCRIPTOR["digest"]
        mutations.append(legacy_alias)
        wrong_container_image = json.loads(json.dumps(baseline))
        wrong_container_image["localEvidenceHost"]["image"]["containerSelection"]["image"] = CONFIG_DESCRIPTOR["digest"]
        mutations.append(wrong_container_image)
        missing_inherited_label = json.loads(json.dumps(baseline))
        missing_inherited_label["localEvidenceHost"]["image"]["containerSelection"]["inheritedLabels"] = {}
        mutations.append(missing_inherited_label)
        missing_owned_label = json.loads(json.dumps(baseline))
        missing_owned_label["localEvidenceHost"]["ownedLabelKeys"].pop()
        mutations.append(missing_owned_label)
        weak_host = json.loads(json.dumps(baseline))
        weak_host["localEvidenceHost"]["hostMinimum"]["physicalMemoryBytes"] -= 1
        mutations.append(weak_host)
        stale_guest = json.loads(json.dumps(baseline))
        stale_guest["localEvidenceHost"]["effectiveConfig"]["cpu"] = 8
        stale_guest["localEvidenceHost"]["effectiveConfig"]["memory"] = 16
        mutations.append(stale_guest)
        for repo_digests in (
            [],
            ["ubuntu@sha256:" + "0" * 64],
            ["docker.io/library/ubuntu@" + INDEX_DESCRIPTOR["digest"]],
            ["ubuntu@" + INDEX_DESCRIPTOR["digest"], "extra@" + INDEX_DESCRIPTOR["digest"]],
        ):
            store_observation = json.loads(json.dumps(baseline))
            store_observation["localEvidenceHost"]["image"]["storeObservation"]["repoDigests"] = repo_digests
            mutations.append(store_observation)
        with tempfile.TemporaryDirectory() as directory:
            for index, mutation in enumerate(mutations):
                path = Path(directory) / f"mutation-{index}.json"
                path.write_bytes(canonical_json_bytes(mutation))
                with self.subTest(index=index), self.assertRaises(BuildInputError):
                    load_policy(path, root=Path.cwd())

    def test_host_resources_bind_policy_minima_and_fresh_sysctl_observation(self) -> None:
        receipt = validate_host_resources("14\n14\n51539607552\n", HOST_MINIMUM)
        self.assertEqual(HOST_MINIMUM, receipt["minimum"])
        self.assertEqual(HOST_MINIMUM, receipt["observed"])

        for output, policy in (
            ("13\n14\n51539607552\n", HOST_MINIMUM),
            ("14\n13\n51539607552\n", HOST_MINIMUM),
            ("14\n14\n51539607551\n", HOST_MINIMUM),
            ("14\n14\n51539607552\n", {**HOST_MINIMUM, "logicalCpu": 13}),
            ("14\n14\n51539607552\n", {**HOST_MINIMUM, "physicalMemoryBytes": 34359738368}),
            ("14\n14\n", HOST_MINIMUM),
            ("14\nphysical\n51539607552\n", HOST_MINIMUM),
        ):
            with self.subTest(output=output, policy=policy), self.assertRaises(BuildInputError):
                validate_host_resources(output, policy)

    def test_complete_strict_matrix_reuses_the_same_home_for_offline_replay(self) -> None:
        session = Path(tempfile.mkdtemp())
        installed = object()
        environment = {"GRADLE_USER_HOME": str(session / "gradle-home")}
        policy = {"dependencyVerification": {"offlineRepresentative": ["./gradlew", "help"]}}
        with mock.patch(
            "scripts.quality.verify_build_inputs._prepare_session",
            return_value=(session, installed, environment),
        ), mock.patch(
            "scripts.quality.verify_build_inputs._run_closed_command",
            return_value="BUILD SUCCESSFUL",
        ) as run:
            _run_group(policy, [["./gradlew", "help"]], label="strict-complete")
        self.assertEqual(2, run.call_count)
        self.assertEqual(["./gradlew", "help", "--offline"], run.call_args_list[-1].args[0])
        self.assertEqual(environment, run.call_args_list[-1].kwargs["environment"])

    def test_cli_accepts_only_policy_and_full_source_commit(self) -> None:
        self.assertEqual(
            ("config/quality/build-inputs.json", SOURCE),
            validate_cli(
                [
                    "--policy",
                    "config/quality/build-inputs.json",
                    "--source-commit",
                    SOURCE,
                ],
            ),
        )
        for argv in (
            ["--policy", "config/quality/build-inputs.json"],
            ["--policy", "config/quality/build-inputs.json", "--source-commit", "1" * 39],
            ["--policy", "config/quality/build-inputs.json", "--source-commit", SOURCE, "--attempt", "1"],
            ["--policy", "config/quality/build-inputs.json", "--source-commit", SOURCE, "--", "sh"],
        ):
            with self.subTest(argv=argv), self.assertRaises(BuildInputError):
                validate_cli(argv)

    def test_host_environment_is_closed_and_rejects_inherited_configuration(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "runtime"
            environment = sanitized_host_environment(root, inherited={})
            self.assertEqual(
                {"COLIMA_HOME", "DOCKER_CONFIG", "HOME", "LANG", "LC_ALL", "PATH", "TZ"},
                set(environment),
            )
            self.assertEqual("UTC", environment["TZ"])
            for name in (
                "COLIMA_HOME",
                "LIMA_HOME",
                "DOCKER_CONFIG",
                "DOCKER_AUTH_CONFIG",
                "DOCKER_CONTEXT",
                "DOCKER_HOST",
                "XDG_CONFIG_HOME",
                "SSH_AUTH_SOCK",
                "GIT_CONFIG_GLOBAL",
                "HTTPS_PROXY",
            ):
                with self.subTest(name=name), self.assertRaisesRegex(
                    BuildInputError,
                    "inherited host configuration",
                ):
                    sanitized_host_environment(root, inherited={name: "poison"})

    def test_runtime_roots_are_short_opaque_marker_derived_and_not_attempt_paths(self) -> None:
        first = isolated_runtime_root(SOURCE, POLICY_SHA, "attempt-000001")
        replay = isolated_runtime_root(SOURCE, POLICY_SHA, "attempt-000001")
        next_attempt_root = isolated_runtime_root(SOURCE, POLICY_SHA, "attempt-000002")
        self.assertEqual(first, replay)
        self.assertNotEqual(first, next_attempt_root)
        self.assertEqual(Path("/tmp"), first.parent)
        self.assertRegex(first.name, r"^[0-9a-f]{10}$")
        self.assertLess(
            len(str(first.resolve() / "colima-home" / "_lima" / "colima-gasstation-task9-linux-amd64" / "lima-guestagent.sock")),
            104,
        )
        self.assertNotIn(SOURCE, str(first))
        self.assertNotIn(POLICY_SHA, str(first))
        with self.assertRaises(BuildInputError):
            isolated_runtime_root(SOURCE, POLICY_SHA, "attempt-user")

    def test_private_tmp_diagnostics_are_fully_redacted(self) -> None:
        safe = _safe_error(BuildInputError('fatal socket "/private/tmp/gst9-secret/agent.sock"'))
        self.assertNotIn("/private", safe)
        self.assertNotIn("gst9-secret", safe)
        self.assertIn("<opaque-path>", safe)

    def test_profile_config_ignores_lima_runtime_copy(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            exact = home / "gasstation-task9-linux-amd64/colima.yaml"
            lima_copy = home / "_lima/colima-gasstation-task9-linux-amd64/colima.yaml"
            exact.parent.mkdir(parents=True)
            lima_copy.parent.mkdir(parents=True)
            exact.write_text("profile: exact\n")
            lima_copy.write_text("profile: lima-copy\n")
            self.assertEqual(exact, _profile_config(home))

    def test_context_inventory_allows_only_active_owned_and_inactive_builtin_default(self) -> None:
        endpoint = "unix:///tmp/opaque/colima-home/gasstation-task9-linux-amd64/docker.sock"
        valid = "\n".join(
            json.dumps(row, sort_keys=True)
            for row in (
                {
                    "Current": True,
                    "Description": "colima [profile=gasstation-task9-linux-amd64]",
                    "DockerEndpoint": endpoint,
                    "Error": "",
                    "Name": "colima-gasstation-task9-linux-amd64",
                },
                {
                    "Current": False,
                    "Description": "Current DOCKER_HOST based configuration",
                    "DockerEndpoint": "unix:///var/run/docker.sock",
                    "Error": "",
                    "Name": "default",
                },
            )
        )
        self.assertEqual(
            {"active": "colima-gasstation-task9-linux-amd64", "builtinInactive": "default"},
            validate_context_inventory(valid, expected_endpoint=endpoint),
        )
        for mutation in (
            valid.replace('"Current": false', '"Current": true'),
            valid + "\n" + json.dumps({"Name": "foreign"}),
            valid.splitlines()[0],
            valid.replace(endpoint, "unix:///var/run/docker.sock"),
        ):
            with self.subTest(mutation=mutation), self.assertRaises(BuildInputError):
                validate_context_inventory(mutation, expected_endpoint=endpoint)

    def test_effective_config_parser_is_complete_duplicate_free_and_exact(self) -> None:
        expected = {
            "arch": "aarch64",
            "autoActivate": False,
            "docker": {},
            "env": {},
            "kubernetes": {"enabled": False, "k3sArgs": ["--disable=traefik"]},
            "mounts": [],
            "rosetta": True,
        }
        valid = (
            "arch: aarch64\n"
            "autoActivate: false\n"
            "docker: {}\n"
            "env: {}\n"
            "kubernetes:\n"
            "  enabled: false\n"
            "  k3sArgs: [--disable=traefik]\n"
            "mounts: []\n"
            "rosetta: true\n"
        )
        self.assertEqual(expected, validate_effective_config(valid, expected))
        block_list = valid.replace(
            "  k3sArgs: [--disable=traefik]\n",
            "  k3sArgs:\n    - --disable=traefik\n",
        )
        self.assertEqual(expected, validate_effective_config(block_list, expected))
        for mutation in (
            valid.replace("rosetta: true", "rosetta: false"),
            valid + "unknown: true\n",
            valid + "arch: aarch64\n",
            valid.replace("mounts: []\n", ""),
        ):
            with self.subTest(mutation=mutation), self.assertRaises(BuildInputError):
                validate_effective_config(mutation, expected)

    def test_attempt_ids_are_generated_monotonically_and_not_reused(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.assertEqual("attempt-000001", next_attempt(root).name)
            (root / "attempt-000001").mkdir()
            (root / "attempt-000003").mkdir()
            self.assertEqual("attempt-000004", next_attempt(root).name)
            (root / "attempt-user").mkdir()
            with self.assertRaisesRegex(BuildInputError, "foreign attempt entry"):
                next_attempt(root)

    def test_marker_is_canonical_and_binds_literal_base_and_runtime_data(self) -> None:
        marker = ownership_marker(
            source_commit=SOURCE,
            policy_sha256=POLICY_SHA,
            attempt_id="attempt-000001",
            main_base_commit=BASE,
            runtime_data_id="3" * 64,
        )
        self.assertEqual(BASE, marker["mainBaseCommit"])
        self.assertEqual("gasstation-task9-evidence", marker["container"])
        self.assertEqual(
            marker["markerSha256"],
            __import__("hashlib").sha256(
                canonical_json_bytes({key: value for key, value in marker.items() if key != "markerSha256"}),
            ).hexdigest(),
        )
        bad = dict(marker)
        bad["mainBaseCommit"] = "4" * 40
        with self.assertRaisesRegex(BuildInputError, "main base"):
            ownership_marker(existing=bad)

    def test_prior_no_runtime_attempt_is_preserved_and_mixed_marker_refused(self) -> None:
        host = generated_policy()["localEvidenceHost"]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            attempt = root / "attempt-000001"
            attempt.mkdir()
            runtime_root = isolated_runtime_root(SOURCE, POLICY_SHA, attempt.name)
            marker = ownership_marker(
                source_commit=SOURCE,
                policy_sha256=POLICY_SHA,
                attempt_id=attempt.name,
                main_base_commit=BASE,
                runtime_data_id=_runtime_data_identity(
                    runtime_root / "colima-home", SOURCE, POLICY_SHA, attempt.name,
                ),
            )
            (attempt / "ownership-marker.json").write_bytes(canonical_json_bytes(marker))
            self.assertFalse(runtime_root.exists())
            try:
                sanitized_host_environment(runtime_root, inherited={})
                (runtime_root / "ownership-marker.json").write_bytes(canonical_json_bytes(marker))
                _recover_prior_attempts(
                    root,
                    source_commit=SOURCE,
                    policy_sha256=POLICY_SHA,
                    host_policy=host,
                )
            finally:
                marker_path = runtime_root / "ownership-marker.json"
                if marker_path.exists():
                    marker_path.unlink()
                for name in ("docker-client", "colima-home", "host-home"):
                    path = runtime_root / name
                    if path.exists():
                        path.rmdir()
                if runtime_root.exists():
                    runtime_root.rmdir()
            self.assertEqual("PASS", json.loads((attempt / "recovery.json").read_text())["status"])

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            attempt = root / "attempt-000001"
            attempt.mkdir()
            marker = ownership_marker(
                source_commit=SOURCE,
                policy_sha256="4" * 64,
                attempt_id=attempt.name,
                main_base_commit=BASE,
                runtime_data_id="3" * 64,
            )
            (attempt / "ownership-marker.json").write_bytes(canonical_json_bytes(marker))
            with self.assertRaisesRegex(BuildInputError, "mixed source/policy/base"):
                _recover_prior_attempts(
                    root,
                    source_commit=SOURCE,
                    policy_sha256=POLICY_SHA,
                    host_policy=host,
                )

    def test_bundle_heads_must_be_exactly_head_and_literal_main(self) -> None:
        valid = f"{SOURCE} HEAD\n{BASE} refs/heads/main\n"
        self.assertEqual(
            {"HEAD": SOURCE, "refs/heads/main": BASE},
            validate_bundle_heads(valid, source_commit=SOURCE, main_base_commit=BASE),
        )
        for mutation in (
            f"{SOURCE} HEAD\n",
            f"{SOURCE} HEAD\n{'4' * 40} refs/heads/main\n",
            valid + f"{'5' * 40} refs/heads/extra\n",
            f"{BASE} refs/heads/main\n{SOURCE} refs/heads/main\n",
        ):
            with self.subTest(mutation=mutation), self.assertRaises(BuildInputError):
                validate_bundle_heads(mutation, source_commit=SOURCE, main_base_commit=BASE)

    def test_every_docker_command_has_literal_isolated_config_and_context(self) -> None:
        argv = docker_argv(Path("/opaque/client"), "version", "--format", "{{json .}}")
        self.assertEqual(
            [
                "/opt/homebrew/bin/docker",
                "--config",
                "/opaque/client",
                "--context",
                "colima-gasstation-task9-linux-amd64",
                "version",
                "--format",
                "{{json .}}",
            ],
            argv,
        )
        with self.assertRaises(BuildInputError):
            docker_argv(Path("relative"), "version")
        with self.assertRaises(BuildInputError):
            docker_argv(Path("/opaque/client"), "system", "prune")

    def test_image_identity_separates_index_store_manifest_config_and_layer_roles(self) -> None:
        identity = validate_image_identity(image_inspect_fixture(), manifest_inspect_fixture())
        self.assertEqual(INDEX_DESCRIPTOR, identity["indexDescriptor"])
        self.assertEqual(SELECTED_MANIFEST_DESCRIPTOR, identity["selectedManifestDescriptor"])
        self.assertEqual(CONFIG_DESCRIPTOR, identity["configDescriptor"])
        self.assertEqual(list(LAYER_DESCRIPTORS), identity["layerDescriptors"])
        self.assertEqual(
            {
                "architecture": "",
                "config": {},
                "id": INDEX_DESCRIPTOR["digest"],
                "os": "",
                "repoDigests": ["ubuntu@" + INDEX_DESCRIPTOR["digest"]],
                "rootFS": {},
                "size": 7112,
            },
            identity["storeObservation"],
        )

    def test_image_identity_mutations_fail_closed_and_valid_fixture_recovers(self) -> None:
        baseline_image = json.loads(image_inspect_fixture())
        baseline_manifest = json.loads(manifest_inspect_fixture())
        mutations: list[tuple[object, object]] = []
        for field, value in (
            ("digest", CONFIG_DESCRIPTOR["digest"]),
            ("mediaType", "application/vnd.oci.image.manifest.v1+json"),
            ("size", 6689),
        ):
            image = json.loads(json.dumps(baseline_image))
            image[0]["Descriptor"][field] = value
            mutations.append((image, baseline_manifest))
        for descriptor_name, field, value in (
            ("Descriptor", "digest", CONFIG_DESCRIPTOR["digest"]),
            ("Descriptor", "mediaType", CONFIG_DESCRIPTOR["mediaType"]),
            ("Descriptor", "size", 425),
            ("config", "digest", SELECTED_MANIFEST_DESCRIPTOR["digest"]),
            ("config", "mediaType", SELECTED_MANIFEST_DESCRIPTOR["mediaType"]),
            ("config", "size", 2053),
            ("layer", "digest", CONFIG_DESCRIPTOR["digest"]),
            ("layer", "mediaType", CONFIG_DESCRIPTOR["mediaType"]),
            ("layer", "size", 29752808),
        ):
            manifest = json.loads(json.dumps(baseline_manifest))
            target = manifest[0]["Descriptor"] if descriptor_name == "Descriptor" else (
                manifest[0]["OCIManifest"]["config"] if descriptor_name == "config" else manifest[0]["OCIManifest"]["layers"][0]
            )
            target[field] = value
            mutations.append((baseline_image, manifest))
        wrong_platform = json.loads(json.dumps(baseline_manifest))
        wrong_platform[0]["Descriptor"]["platform"]["architecture"] = ""
        mutations.append((baseline_image, wrong_platform))
        duplicate_amd64 = json.loads(json.dumps(baseline_manifest))
        duplicate_amd64.append(json.loads(json.dumps(duplicate_amd64[0])))
        mutations.append((baseline_image, duplicate_amd64))
        promoted_store = json.loads(json.dumps(baseline_image))
        promoted_store[0]["Id"] = CONFIG_DESCRIPTOR["digest"]
        promoted_store[0]["Architecture"] = "amd64"
        mutations.append((promoted_store, baseline_manifest))
        for field, value in (
            ("Config", {"Labels": {"unexpected": "label"}}),
            ("RootFS", {"Type": "layers"}),
            ("RepoDigests", []),
            ("Size", 7113),
        ):
            image = json.loads(json.dumps(baseline_image))
            image[0][field] = value
            mutations.append((image, baseline_manifest))
        for repo_digests in (
            ["ubuntu@sha256:" + "0" * 64],
            ["docker.io/library/ubuntu@" + INDEX_DESCRIPTOR["digest"]],
            ["ubuntu@" + INDEX_DESCRIPTOR["digest"], "extra@" + INDEX_DESCRIPTOR["digest"]],
        ):
            image = json.loads(json.dumps(baseline_image))
            image[0]["RepoDigests"] = repo_digests
            mutations.append((image, baseline_manifest))
        for image, manifest in mutations:
            with self.subTest(image=image, manifest=manifest), self.assertRaises(BuildInputError):
                validate_image_identity(json.dumps(image), json.dumps(manifest))
            self.assertEqual(
                INDEX_DESCRIPTOR,
                validate_image_identity(image_inspect_fixture(), manifest_inspect_fixture())["indexDescriptor"],
            )

    def test_container_selection_binds_config_platform_and_labels(self) -> None:
        marker = {
            "attemptId": "attempt-000001",
            "mainBaseCommit": BASE,
            "markerSha256": "3" * 64,
            "policySha256": POLICY_SHA,
            "sourceCommit": SOURCE,
            "taskId": "quality-task-9-local-linux-evidence",
        }
        owned_labels = _owned_labels(marker)
        labels = _container_labels(marker)
        valid = json.dumps([
            {
                "Config": {
                    "Image": "docker.io/library/ubuntu@" + INDEX_DESCRIPTOR["digest"],
                    "Labels": labels,
                },
                "Image": INDEX_DESCRIPTOR["digest"],
                "Platform": "linux",
            },
        ])
        self.assertEqual(
            {
                "configImage": "docker.io/library/ubuntu@" + INDEX_DESCRIPTOR["digest"],
                "image": INDEX_DESCRIPTOR["digest"],
                "inheritedLabels": CONTAINER_INHERITED_LABELS,
                "ownedLabels": owned_labels,
                "platform": "linux",
            },
            validate_container_selection(valid, expected_owned_labels=owned_labels),
        )
        mutations = [
            valid.replace(INDEX_DESCRIPTOR["digest"], SELECTED_MANIFEST_DESCRIPTOR["digest"], 1),
            valid.replace("docker.io/library/ubuntu@" + INDEX_DESCRIPTOR["digest"], SELECTED_MANIFEST_DESCRIPTOR["digest"]),
            valid.replace('"Platform": "linux"', '"Platform": ""'),
            valid.replace("3" * 64, "4" * 64),
            valid.replace('"org.opencontainers.image.version": "24.04"', '"org.opencontainers.image.version": "latest"'),
        ]
        missing_inherited = json.loads(valid)
        del missing_inherited[0]["Config"]["Labels"]["org.opencontainers.image.version"]
        mutations.append(json.dumps(missing_inherited))
        extra_inherited = json.loads(valid)
        extra_inherited[0]["Config"]["Labels"]["org.opencontainers.image.revision"] = "unexpected"
        mutations.append(json.dumps(extra_inherited))
        for mutation in mutations:
            with self.subTest(mutation=mutation), self.assertRaises(BuildInputError):
                validate_container_selection(mutation, expected_owned_labels=owned_labels)

    def test_inner_architecture_requires_exact_uname_and_dpkg_facts(self) -> None:
        self.assertEqual(
            {"dpkg": "amd64", "uname": "x86_64"},
            validate_inner_architecture("uname=x86_64\ndpkg=amd64\n"),
        )
        for mutation in (
            "uname=aarch64\ndpkg=amd64\n",
            "uname=x86_64\ndpkg=arm64\n",
            "uname=x86_64\ndpkg=amd64\nextra=true\n",
            "x86_64 amd64\n",
        ):
            with self.subTest(mutation=mutation), self.assertRaises(BuildInputError):
                validate_inner_architecture(mutation)

    def test_cleanup_is_exactly_ordered_and_requires_data_delete_and_live_absence(self) -> None:
        valid = {
            "phases": list(CLEANUP_PHASES),
            "containerAbsentWhileDaemonLive": True,
            "volumesAbsentWhileDaemonLive": True,
            "deleteArgv": [
                "/opt/homebrew/bin/colima",
                "delete",
                "gasstation-task9-linux-amd64",
                "--data",
                "--force",
            ],
            "profileAbsent": True,
            "contextAbsent": True,
            "runtimeDataAbsent": True,
        }
        validate_cleanup_proof(valid)
        mutations = []
        reordered = json.loads(json.dumps(valid))
        reordered["phases"][1], reordered["phases"][2] = reordered["phases"][2], reordered["phases"][1]
        mutations.append(reordered)
        no_data = json.loads(json.dumps(valid))
        no_data["deleteArgv"].remove("--data")
        mutations.append(no_data)
        no_live = json.loads(json.dumps(valid))
        no_live["volumesAbsentWhileDaemonLive"] = False
        mutations.append(no_live)
        retained = json.loads(json.dumps(valid))
        retained["runtimeDataAbsent"] = False
        mutations.append(retained)
        for mutation in mutations:
            with self.subTest(mutation=mutation), self.assertRaises(BuildInputError):
                validate_cleanup_proof(mutation)

    def test_runtime_absence_checks_profile_lima_instance_disk_and_context(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            colima_home = root / "colima-home"
            docker_config = root / "docker-client"
            colima_home.mkdir()
            docker_config.mkdir()
            self.assertTrue(validate_runtime_absence(colima_home, docker_config))
            retained = (
                colima_home / "gasstation-task9-linux-amd64",
                colima_home / "_lima/colima-gasstation-task9-linux-amd64",
                colima_home / "_lima/_disks/colima-gasstation-task9-linux-amd64",
            )
            for path in retained:
                path.mkdir(parents=True)
                with self.subTest(path=path):
                    self.assertFalse(validate_runtime_absence(colima_home, docker_config))
                path.rmdir()
            meta = docker_config / "contexts/meta/owned/meta.json"
            meta.parent.mkdir(parents=True)
            meta.write_text(json.dumps({"Name": "colima-gasstation-task9-linux-amd64"}))
            self.assertFalse(validate_runtime_absence(colima_home, docker_config))

    def test_aggregate_is_all_or_nothing_and_same_identity(self) -> None:
        rows = {
            name: {"status": "PASS", "sourceCommit": SOURCE, "policySha256": POLICY_SHA}
            for name in (
                "configurationCache",
                "evidenceSessions",
                "metadataCapture",
                "mutations",
                "offlineStrict",
                "onlineColdStrict",
                "productStrict",
                "releaseBinding",
                "reproducibility",
            )
        }
        receipt = aggregate_receipt(
            source_commit=SOURCE,
            policy_sha256=POLICY_SHA,
            attempt_id="attempt-000001",
            rows=rows,
            cleanup_status="PASS",
        )
        self.assertEqual("PASS", receipt["status"])
        partial = dict(rows)
        partial.pop("offlineStrict")
        with self.assertRaisesRegex(BuildInputError, "exact required evidence rows"):
            aggregate_receipt(
                source_commit=SOURCE,
                policy_sha256=POLICY_SHA,
                attempt_id="attempt-000001",
                rows=partial,
                cleanup_status="PASS",
            )
        mixed = json.loads(json.dumps(rows))
        mixed["mutations"]["sourceCommit"] = "6" * 40
        with self.assertRaisesRegex(BuildInputError, "mixed evidence identity"):
            aggregate_receipt(
                source_commit=SOURCE,
                policy_sha256=POLICY_SHA,
                attempt_id="attempt-000001",
                rows=mixed,
                cleanup_status="PASS",
            )


if __name__ == "__main__":
    unittest.main()
