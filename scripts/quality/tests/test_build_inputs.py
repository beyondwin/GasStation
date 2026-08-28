from __future__ import annotations

import hashlib
import io
import json
import os
import re
import base64
import shutil
import tarfile
import tempfile
import threading
import unittest
import urllib.parse
import uuid
from unittest import mock
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from scripts.quality.build_inputs.archive import ArchiveError, safe_extract_tar
from scripts.quality.build_inputs.contracts import (
    BuildInputError,
    canonical_json_bytes,
    load_policy,
    scan_dynamic_dependency_selectors,
    validate_gradle_arguments,
    validate_protected_environment,
    verify_wrapper,
)
from scripts.quality.build_inputs.downloader import (
    DownloadError,
    download_verified,
    download_verified_github_release_asset,
    validate_github_release_asset_redirect,
)
from scripts.quality.build_inputs.docs_gradle_validation_bridge import (
    BridgeError,
    _guarded_docs_runtime,
)
from scripts.quality.build_inputs.generate_policy import policy as generated_policy
from scripts.quality.build_inputs.receipts import (
    canonical_receipt,
    load_canonical_receipt,
    relative_evidence_rows,
    write_canonical_receipt,
)
from scripts.quality.build_inputs.reproducibility import reproducibility_receipt, safe_zip_comparison
from scripts.quality.build_inputs.workflow import build_inputs_is_promoted, verify_repository_workflows
from scripts.quality.verify_build_inputs import (
    _capture_android_sdk,
    _configuration_cache_commands,
    _run_closed_command,
    verify_repository,
)
from scripts.agent.check_contracts import check_documentation_contracts


ROOT = Path(__file__).resolve().parents[3]
POLICY = ROOT / "config/quality/build-inputs.json"

EXPECTED_TESTKIT_WORKER_GRAMMAR = {
    "acceptedDirectConstructionCount": 3,
    "acceptedPropertyConstructionCount": 16,
    "acceptedUnsupportedDisjoint": True,
    "acceptedWorkerControlConstructionCount": 19,
    "caseSensitive": True,
    "consumeOptionTokenAsPropertyPayload": False,
    "consumeTerminatorAsPropertyPayload": False,
    "directDoubleDashOption": "--max-workers",
    "directForms": ["doubleDashEquals", "doubleDashSeparated", "singleDashSeparated"],
    "directSingleDashOption": "-max-workers",
    "doubleDashLongOptions": ["--system-prop", "--project-prop"],
    "doubleDashShortOptions": ["--D", "--P"],
    "equalsOptionCount": 6,
    "failClosedPayload": ["emptyPayload", "emptyKey"],
    "failClosedSeparated": ["missingToken", "emptyToken", "terminatorToken", "optionToken"],
    "fixtureRejectTerminator": True,
    "fixtureRejectedSeparatedStateCount": 4,
    "joinedOptionCount": 2,
    "key": "org.gradle.workers.max",
    "loneDashConvertedValue": "",
    "loneDashPayload": "-",
    "optionTokenPattern": "(?s)-.+",
    "preserveSeparatedStates": ["loneDashPayload", "payload"],
    "preserveUnrelated": True,
    "preservedSeparatedStateCount": 2,
    "propertyForms": [
        "shortJoined",
        "shortSeparated",
        "shortEquals",
        "doubleDashShortEquals",
        "doubleDashShortSeparated",
        "doubleDashLongEquals",
        "doubleDashLongSeparated",
        "singleDashLongSeparated",
    ],
    "rejectTargetValueStates": ["absent", "empty", "nonempty"],
    "separatedOptionCount": 8,
    "separatedStateCount": 6,
    "separatedStatePrecedence": [
        "missingToken",
        "emptyToken",
        "loneDashPayload",
        "terminatorToken",
        "optionToken",
        "payload",
    ],
    "separatedStates": [
        "missingToken",
        "emptyToken",
        "loneDashPayload",
        "terminatorToken",
        "optionToken",
        "payload",
    ],
    "shortEqualsBeforeJoined": True,
    "shortOptions": ["-D", "-P"],
    "singleDashLongOptions": ["-system-prop", "-project-prop"],
    "split": "firstEquals",
    "terminatorGradleTransition": "AfterOptions",
    "terminatorPendingProperty": "absent",
    "terminatorToken": "--",
    "trim": False,
    "unacceptedDirectForms": [
        "singleDashEquals",
        "doubleDashJoined",
        "singleDashJoined",
        "plainSeparated",
    ],
    "unacceptedDoubleDashShortJoined": ["--D<payload>", "--P<payload>"],
    "unacceptedPropertyForms": [
        "doubleDashShortJoined",
        "doubleDashLongJoined",
        "singleDashLongEquals",
        "singleDashLongJoined",
    ],
    "unacceptedSingleDashLongEquals": [
        "-max-workers=",
        "-system-prop=",
        "-project-prop=",
    ],
    "unsupportedConstructionCount": 12,
}


class CanonicalPolicyTest(unittest.TestCase):
    def test_checked_in_policy_is_canonical_closed_and_self_consistent(self) -> None:
        policy = load_policy(POLICY, root=ROOT)

        self.assertEqual(1, policy["schemaVersion"])
        self.assertEqual(b"{", POLICY.read_bytes()[:1])
        self.assertEqual(canonical_json_bytes(policy), POLICY.read_bytes())
        self.assertNotIn("dependencyVerification", policy)
        self.assertEqual(2, len(policy["configurationCacheChecks"]))
        self.assertEqual(
            [
                [
                    "python3",
                    "scripts/quality/build_inputs/docs_gradle_validation_bridge.py",
                    "--check-gradle-tasks",
                ],
                ["scripts/agent/verify-room-schemas.sh"],
                ["scripts/agent/verify.sh", "auto"],
                ["scripts/agent/verify.sh", "docs"],
            ],
            policy["evidenceSessionCommands"],
        )

    def test_duplicate_key_noncanonical_bytes_and_unknown_top_level_fail(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            duplicate = root / "duplicate.json"
            duplicate.write_text('{"schemaVersion":1,"schemaVersion":1}\n', encoding="utf-8")
            with self.assertRaisesRegex(BuildInputError, "duplicate JSON key"):
                load_policy(duplicate, root=ROOT)

            unknown = json.loads(POLICY.read_text(encoding="utf-8"))
            unknown["unexpected"] = True
            candidate = root / "unknown.json"
            candidate.write_bytes(canonical_json_bytes(unknown))
            with self.assertRaisesRegex(BuildInputError, "unknown keys"):
                load_policy(candidate, root=ROOT)

            candidate.write_bytes(canonical_json_bytes(load_policy(POLICY, root=ROOT))[:-1])
            with self.assertRaisesRegex(BuildInputError, "canonical JSON"):
                load_policy(candidate, root=ROOT)

    def test_standalone_configuration_cache_probe_rejects_route_dependent_tasks(self) -> None:
        policy = {
            "configurationCacheChecks": [[
                "./gradlew",
                "verifyModuleBoundaries",
                "verifyPitestConfiguration",
            ]],
        }

        with self.assertRaisesRegex(BuildInputError, "generated PIT route evidence"):
            _configuration_cache_commands(policy)

    def test_policy_forbids_static_hashes_for_docs_facade_and_extensions(self) -> None:
        policy = load_policy(POLICY, root=ROOT)
        paths = {row["path"] for row in policy["staticSourceHashes"]}
        self.assertIn(
            "scripts/quality/build_inputs/docs_gradle_validation_bridge.py",
            paths,
        )
        self.assertNotIn("scripts/docs/validate.py", paths)
        self.assertFalse(any(path.startswith("scripts/docs/extensions/") for path in paths))

    def test_nested_testkit_worker_control_policy_is_exact_and_outer_is_uncapped(self) -> None:
        policy = load_policy(POLICY, root=ROOT)
        rows = {row["id"]: row for row in policy["evidenceGradleEntrypoints"]}
        nested_ids = {
            "testkit/shared-normal",
            "testkit/shared-configuration-cache",
            "testkit/adversarial",
        }
        for identity in sorted(nested_ids):
            with self.subTest(identity=identity):
                row = rows[identity]
                self.assertEqual(2, row["maxWorkers"])
                self.assertEqual("--max-workers=2", row["maxWorkersArgument"])
                self.assertEqual(1, row["maxWorkersArgumentCount"])
                self.assertEqual("final", row["maxWorkersArgumentPosition"])
                self.assertTrue(row["rejectCallerWorkerControls"])
                self.assertTrue(row["sanitizeWorkerEnvironment"])
                self.assertEqual(EXPECTED_TESTKIT_WORKER_GRAMMAR, row["workerPropertyConflictGrammar"])
                self.assertEqual("--max-workers=2", row["argv"][-1])
                self.assertEqual(1, row["argv"].count("--max-workers=2"))

        outer = rows["android/static-analysis/convention-testkit"]
        for forbidden in (
            "maxWorkers",
            "maxWorkersArgument",
            "maxWorkersArgumentCount",
            "maxWorkersArgumentPosition",
            "rejectCallerWorkerControls",
            "sanitizeWorkerEnvironment",
            "workerPropertyConflictGrammar",
        ):
            self.assertNotIn(forbidden, outer)
        self.assertNotIn("--max-workers=2", outer["argv"])
        self.assertEqual(
            [
                "scripts/quality/build_inputs/run_gradle.sh",
                ":build-logic:convention:test",
                "--no-configuration-cache",
                "--warning-mode",
                "fail",
            ],
            outer["argv"],
        )

        def direct(name: str) -> tuple[str, ...]:
            return (f"--{name}=<value>", f"--{name} <value>", f"-{name} <value>")

        def properties(short: str, long_name: str) -> tuple[str, ...]:
            return (
                f"-{short}<payload>",
                f"-{short} <payload>",
                f"-{short}=<payload>",
                f"--{short}=<payload>",
                f"--{short} <payload>",
                f"--{long_name}=<payload>",
                f"--{long_name} <payload>",
                f"-{long_name} <payload>",
            )

        derived = {
            "direct": direct("max-workers"),
            "system": properties("D", "system-prop"),
            "project": properties("P", "project-prop"),
        }
        expected = {
            "direct": ("--max-workers=<value>", "--max-workers <value>", "-max-workers <value>"),
            "system": (
                "-D<payload>", "-D <payload>", "-D=<payload>", "--D=<payload>",
                "--D <payload>", "--system-prop=<payload>", "--system-prop <payload>",
                "-system-prop <payload>",
            ),
            "project": (
                "-P<payload>", "-P <payload>", "-P=<payload>", "--P=<payload>",
                "--P <payload>", "--project-prop=<payload>", "--project-prop <payload>",
                "-project-prop <payload>",
            ),
        }
        unsupported = {
            "-max-workers=<value>", "--max-workers<value>", "-max-workers<value>",
            "max-workers <value>", "--D<payload>", "--P<payload>",
            "--system-prop<payload>", "--project-prop<payload>",
            "-system-prop<payload>", "-project-prop<payload>",
            "-system-prop=<payload>", "-project-prop=<payload>",
        }
        self.assertEqual(expected, derived)
        accepted = set(derived["direct"] + derived["system"] + derived["project"])
        self.assertEqual(19, len(accepted))
        self.assertEqual(12, len(unsupported))
        self.assertTrue(unsupported.isdisjoint(accepted))

        def state(token: str | None) -> str:
            if token is None:
                return "missingToken"
            if token == "":
                return "emptyToken"
            if token == "-":
                return "loneDashPayload"
            if token == "--":
                return "terminatorToken"
            if re.fullmatch(r"(?s)-.+", token):
                return "optionToken"
            return "payload"

        self.assertEqual(
            EXPECTED_TESTKIT_WORKER_GRAMMAR["separatedStatePrecedence"],
            [state(token) for token in (None, "", "-", "--", "-x", "example=value")],
        )

    def test_nested_worker_policy_mutations_fail_closed(self) -> None:
        baseline = json.loads(POLICY.read_text(encoding="utf-8"))
        nested_id = "testkit/shared-normal"
        mutations: list[dict[str, object]] = []

        def mutation(path: tuple[str, ...], value: object) -> None:
            candidate = json.loads(json.dumps(baseline))
            row = next(row for row in candidate["evidenceGradleEntrypoints"] if row["id"] == nested_id)
            target = row
            for key in path[:-1]:
                target = target[key]
            target[path[-1]] = value
            mutations.append(candidate)

        mutation(("maxWorkers",), 3)
        mutation(("maxWorkersArgument",), "--max-workers=3")
        mutation(("maxWorkersArgumentCount",), 2)
        mutation(("maxWorkersArgumentPosition",), "before-java-paths")
        mutation(("rejectCallerWorkerControls",), False)
        mutation(("sanitizeWorkerEnvironment",), False)
        mutation(("workerPropertyConflictGrammar", "directForms"), ["doubleDashEquals"])
        mutation(("workerPropertyConflictGrammar", "doubleDashShortOptions"), ["--D"])
        mutation(("workerPropertyConflictGrammar", "singleDashLongOptions"), ["-system-prop"])
        mutation(("workerPropertyConflictGrammar", "shortEqualsBeforeJoined"), False)
        mutation(("workerPropertyConflictGrammar", "split"), "lastEquals")
        mutation(("workerPropertyConflictGrammar", "preserveUnrelated"), False)
        mutation(("workerPropertyConflictGrammar", "separatedStates"), ["payload"])
        mutation(("workerPropertyConflictGrammar", "separatedStatePrecedence"), list(reversed(EXPECTED_TESTKIT_WORKER_GRAMMAR["separatedStatePrecedence"])))
        mutation(("workerPropertyConflictGrammar", "loneDashConvertedValue"), "-")
        mutation(("workerPropertyConflictGrammar", "fixtureRejectTerminator"), False)
        mutation(("workerPropertyConflictGrammar", "consumeOptionTokenAsPropertyPayload"), True)
        mutation(("workerPropertyConflictGrammar", "unsupportedConstructionCount"), 11)

        with tempfile.TemporaryDirectory() as directory:
            for index, candidate_policy in enumerate(mutations):
                candidate = Path(directory) / f"worker-mutation-{index}.json"
                candidate.write_bytes(canonical_json_bytes(candidate_policy))
                with self.subTest(index=index), self.assertRaisesRegex(BuildInputError, "worker"):
                    load_policy(candidate, root=ROOT)

    def test_superseded_runtime_and_cross_wired_jdk_roles_fail_closed(self) -> None:
        baseline = json.loads(POLICY.read_text(encoding="utf-8"))
        mutations: list[tuple[dict[str, object], str]] = []
        superseded = json.loads(json.dumps(baseline))
        superseded["jdks"]["runtime"]["version"] = "21.0.12+8"
        mutations.append((superseded, "reviewed exact identity"))
        cross_wired = json.loads(json.dumps(baseline))
        cross_wired["jdks"]["compile"], cross_wired["jdks"]["runtime"] = (
            cross_wired["jdks"]["runtime"],
            cross_wired["jdks"]["compile"],
        )
        mutations.append((cross_wired, "reviewed exact identity"))
        major_only = json.loads(json.dumps(baseline))
        major_only["jdks"]["runtime"]["version"] = "21"
        mutations.append((major_only, "reviewed exact identity"))
        redirect_path = json.loads(json.dumps(baseline))
        redirect_path["jdks"]["compile"]["releaseAssetRedirect"]["finalPath"] = "/github-production-release-asset/602574963/be5ef440-7bad-40e3-9188-9e7648842040"
        mutations.append((redirect_path, "reviewed exact contract"))
        redirect_initial = json.loads(json.dumps(baseline))
        redirect_initial["jdks"]["runtime"]["releaseAssetRedirect"]["initialUrl"] = redirect_initial["jdks"]["compile"]["archiveUrl"]
        mutations.append((redirect_initial, "reviewed exact contract"))
        redirect_key = json.loads(json.dumps(baseline))
        redirect_key["jdks"]["compile"]["releaseAssetRedirect"]["queryKeys"].pop()
        mutations.append((redirect_key, "reviewed exact contract"))
        redirect_header = json.loads(json.dumps(baseline))
        redirect_header["jdks"]["runtime"]["releaseAssetRedirect"]["finalHeaders"]["contentLength"] -= 1
        mutations.append((redirect_header, "reviewed exact contract"))

        with tempfile.TemporaryDirectory() as directory:
            for index, (mutation, expected_error) in enumerate(mutations):
                candidate = Path(directory) / f"jdk-mutation-{index}.json"
                candidate.write_bytes(canonical_json_bytes(mutation))
                with self.subTest(index=index), self.assertRaisesRegex(
                    BuildInputError,
                    expected_error,
                ):
                    load_policy(candidate, root=ROOT)

    def test_android_repository_source_and_platform_record_fail_closed(self) -> None:
        baseline = json.loads(POLICY.read_text(encoding="utf-8"))
        mutations = []
        old_coordinate = json.loads(json.dumps(baseline))
        platform = next(
            row for row in old_coordinate["android"]["packages"]
            if row["coordinate"] == "platforms;android-37.0"
        )
        platform["coordinate"] = "platforms;android-37"
        mutations.append(old_coordinate)
        wrong_revision = json.loads(json.dumps(baseline))
        wrong_revision["android"]["repositoryInventory"]["acceptedRecord"]["revisionMajor"] = 1
        mutations.append(wrong_revision)
        wrong_hash = json.loads(json.dumps(baseline))
        wrong_hash["android"]["repositoryInventory"]["repositorySha256"] = "0" * 64
        mutations.append(wrong_hash)
        wrong_archive = json.loads(json.dumps(baseline))
        wrong_archive["android"]["repositoryInventory"]["acceptedRecord"]["archive"]["resolvedUrl"] = (
            "https://example.com/platform-37.0_r02.zip"
        )
        mutations.append(wrong_archive)
        missing_absence = json.loads(json.dumps(baseline))
        missing_absence["android"]["repositoryInventory"]["absentCoordinates"] = []
        mutations.append(missing_absence)

        with tempfile.TemporaryDirectory() as directory:
            for index, mutation in enumerate(mutations):
                candidate = Path(directory) / f"android-repository-mutation-{index}.json"
                candidate.write_bytes(canonical_json_bytes(mutation))
                with self.subTest(index=index), self.assertRaisesRegex(BuildInputError, "Android"):
                    load_policy(candidate, root=ROOT)

    def test_android_installed_inventory_and_command_line_source_fail_closed(self) -> None:
        baseline = json.loads(POLICY.read_text(encoding="utf-8"))
        command_tools = baseline["localEvidenceHost"]["commandLineTools"]
        self.assertEqual(141, command_tools["archiveMemberCount"])
        self.assertEqual(
            "cmdline-tools/latest/source.properties",
            command_tools["sourceProperties"]["relativePath"],
        )
        self.assertEqual("cmdline-tools;22.0", command_tools["sourceProperties"]["coordinate"])
        inventory = baseline["android"]["installedInventory"]
        self.assertEqual(3, len(inventory["packageXmlFiles"]))
        self.assertEqual(6, len(inventory["selectedBinaries"]))

        mutations = []
        for path, value in (
            (("localEvidenceHost", "commandLineTools", "archiveMemberCount"), 140),
            (("localEvidenceHost", "commandLineTools", "archiveMemberListingSha256"), "0" * 64),
            (("localEvidenceHost", "commandLineTools", "sourceProperties", "mode"), "0755"),
            (("localEvidenceHost", "commandLineTools", "sourceProperties", "coordinate"), "cmdline-tools;latest"),
        ):
            mutation = json.loads(json.dumps(baseline))
            target = mutation
            for key in path[:-1]:
                target = target[key]
            target[path[-1]] = value
            mutations.append(mutation)
        missing_package = json.loads(json.dumps(baseline))
        missing_package["android"]["installedInventory"]["packageXmlFiles"].pop()
        mutations.append(missing_package)
        swapped_binary = json.loads(json.dumps(baseline))
        swapped_binary["android"]["installedInventory"]["selectedBinaries"][0]["ownerRole"] = "platform-tools"
        mutations.append(swapped_binary)

        with tempfile.TemporaryDirectory() as directory:
            for index, mutation in enumerate(mutations):
                candidate = Path(directory) / f"android-installed-mutation-{index}.json"
                candidate.write_bytes(canonical_json_bytes(mutation))
                with self.subTest(index=index), self.assertRaisesRegex(BuildInputError, "Android"):
                    load_policy(candidate, root=ROOT)

    def test_repository_verification_rejects_deleted_or_reclassified_entrypoint_rows(self) -> None:
        baseline = load_policy(POLICY, root=ROOT)
        mutations = []
        deleted = json.loads(json.dumps(baseline))
        deleted["evidenceGradleEntrypoints"] = [
            row
            for row in deleted["evidenceGradleEntrypoints"]
            if row["id"] != "device-scheduled-api36/gmd"
        ]
        mutations.append(deleted)
        reclassified = json.loads(json.dumps(baseline))
        row = next(
            row
            for row in reclassified["evidenceGradleEntrypoints"]
            if row["id"] == "android/unit-tests/room-schema-child"
        )
        row["relationship"] = "direct"
        mutations.append(reclassified)

        for mutation in mutations:
            with self.subTest(rows=len(mutation["evidenceGradleEntrypoints"])):
                with self.assertRaisesRegex(BuildInputError, "entrypoint inventory mismatch"):
                    verify_repository(mutation)


class WrapperAndInvocationTest(unittest.TestCase):
    def test_governed_command_failure_preserves_redacted_bounded_terminal_cause(self) -> None:
        completed = mock.Mock(
            returncode=7,
            stdout=("prefix\n" + ("x" * 70000) + "\nterminal-cause token=very-secret /tmp/private/file\n"),
        )
        with mock.patch("scripts.quality.verify_build_inputs.subprocess.run", return_value=completed):
            with self.assertRaises(BuildInputError) as raised:
                _run_closed_command(
                    ["python3", "governed.py"],
                    installed=mock.Mock(),
                    environment={},
                    cwd=ROOT,
                )

        message = str(raised.exception)
        self.assertIn("terminal-cause", message)
        self.assertIn("<redacted-secret>", message)
        self.assertIn("<redacted-path>", message)
        self.assertNotIn("very-secret", message)
        self.assertLessEqual(len(message.encode("utf-8")), 65536 + 512)

    def test_wrapper_matches_official_policy_bytes(self) -> None:
        verify_wrapper(ROOT, load_policy(POLICY, root=ROOT))

    def test_protected_environment_and_gradle_argv_fail_closed(self) -> None:
        clean = {
            "CI": "true",
            "LANG": "C.UTF-8",
            "PATH": "/verified/runtime/bin:/usr/bin:/bin",
            "JAVA_HOME": "/verified/runtime",
            "JAVA_HOME_17_X64": "/verified/compile",
            "JAVA_HOME_21_X64": "/verified/runtime",
            "GRADLE_USER_HOME": "/fresh/gradle-home",
        }
        validate_protected_environment(
            clean,
            compile_home="/verified/compile",
            runtime_home="/verified/runtime",
            gradle_home="/fresh/gradle-home",
        )
        for name in (
            "JAVA_OPTS",
            "GRADLE_OPTS",
            "JAVA_TOOL_OPTIONS",
            "JDK_JAVA_OPTIONS",
            "_JAVA_OPTIONS",
        ):
            mutated = dict(clean)
            mutated[name] = "-Dorg.gradle.dependency.verification=off"
            with self.subTest(name=name), self.assertRaisesRegex(
                BuildInputError,
                "protected environment",
            ):
                validate_protected_environment(
                    mutated,
                    compile_home="/verified/compile",
                    runtime_home="/verified/runtime",
                    gradle_home="/fresh/gradle-home",
                )

        accepted = [
            "./gradlew",
            "help",
            "-Dorg.gradle.java.installations.auto-detect=false",
            "-Dorg.gradle.java.installations.auto-download=false",
            "-Dorg.gradle.java.installations.paths=/verified/compile,/verified/runtime",
        ]
        validate_gradle_arguments(accepted)
        for injected in (
            accepted + ["-I", "/tmp/evil.gradle"],
            accepted + ["--init-script=/tmp/evil.gradle"],
        ):
            with self.subTest(argv=injected), self.assertRaises(BuildInputError):
                validate_gradle_arguments(injected)

    def test_dynamic_selector_scanner_rejects_catalog_ranges(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory)
            catalog = fixture / "gradle/libs.versions.toml"
            catalog.parent.mkdir(parents=True)
            catalog.write_text('[versions]\nexample = "1.+"\n', encoding="utf-8")

            self.assertEqual(
                ["gradle/libs.versions.toml:2: dynamic dependency selector: 1.+"],
                scan_dynamic_dependency_selectors(fixture),
            )

    def test_scanner_ignores_preserved_codex_evidence_but_not_active_sources(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory)
            preserved = fixture / ".codex/task-cache/attempt/raw-survivor/build.gradle.kts"
            preserved.parent.mkdir(parents=True)
            preserved.write_text('implementation("example:artifact:1.+")\n', encoding="utf-8")
            active = fixture / "feature/sample/build.gradle.kts"
            active.parent.mkdir(parents=True)
            active.write_text('implementation("example:artifact:2.+")\n', encoding="utf-8")

            self.assertEqual(
                ["feature/sample/build.gradle.kts:1: dynamic dependency selector: example:artifact:2.+"],
                scan_dynamic_dependency_selectors(fixture),
            )

    def test_dynamic_selector_scanner_does_not_cross_escaped_or_adjacent_literals(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory)
            source = fixture / "build.gradle.kts"
            source.write_text(
                'val escaped = "\\\"" + value.replace("\\\\", "\\\\\\\\")\n'
                'val adjacent = ")" + "("\n'
                'val actual = "example:artifact:3.+"\n',
                encoding="utf-8",
            )

            self.assertEqual(
                ["build.gradle.kts:3: dynamic dependency selector: example:artifact:3.+"],
                scan_dynamic_dependency_selectors(fixture),
            )

class SafeArchiveTest(unittest.TestCase):
    def _archive(self, rows: list[tuple[tarfile.TarInfo, bytes]]) -> bytes:
        output = io.BytesIO()
        with tarfile.open(fileobj=output, mode="w:gz") as archive:
            for info, content in rows:
                archive.addfile(info, io.BytesIO(content) if info.isreg() else None)
        return output.getvalue()

    def test_safe_extract_accepts_one_declared_root_and_rejects_traversal(self) -> None:
        directory = tarfile.TarInfo("jdk-root/")
        directory.type = tarfile.DIRTYPE
        java = tarfile.TarInfo("jdk-root/bin/java")
        java.mode = 0o755
        content = b"verified-java"
        java.size = len(content)
        archive = self._archive([(directory, b""), (java, content)])

        with tempfile.TemporaryDirectory() as temporary:
            destination = Path(temporary) / "jdk"
            safe_extract_tar(archive, destination=destination, archive_root="jdk-root")
            self.assertEqual(content, (destination / "bin/java").read_bytes())

        traversal = tarfile.TarInfo("jdk-root/../../escape")
        traversal.size = 1
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(ArchiveError, "unsafe archive path"):
                safe_extract_tar(
                    self._archive([(traversal, b"x")]),
                    destination=Path(temporary) / "jdk",
                    archive_root="jdk-root",
                )

    def test_safe_extract_rejects_hardlinks_devices_duplicate_and_existing_root(self) -> None:
        cases: list[tarfile.TarInfo] = []
        hardlink = tarfile.TarInfo("jdk-root/hard")
        hardlink.type = tarfile.LNKTYPE
        hardlink.linkname = "jdk-root/bin/java"
        cases.append(hardlink)
        device = tarfile.TarInfo("jdk-root/device")
        device.type = tarfile.CHRTYPE
        cases.append(device)
        for member in cases:
            with self.subTest(type=member.type), tempfile.TemporaryDirectory() as temporary:
                with self.assertRaises(ArchiveError):
                    safe_extract_tar(
                        self._archive([(member, b"")]),
                        destination=Path(temporary) / "jdk",
                        archive_root="jdk-root",
                    )

        first = tarfile.TarInfo("jdk-root/bin/java")
        first.size = 1
        second = tarfile.TarInfo("jdk-root/bin/java")
        second.size = 1
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(ArchiveError, "duplicate archive path"):
                safe_extract_tar(
                    self._archive([(first, b"a"), (second, b"b")]),
                    destination=Path(temporary) / "jdk",
                    archive_root="jdk-root",
                )
            existing = Path(temporary) / "existing"
            existing.mkdir()
            with self.assertRaisesRegex(ArchiveError, "already exists"):
                safe_extract_tar(
                    self._archive([(first, b"a")]),
                    destination=existing,
                    archive_root="jdk-root",
                )


class VerifiedDownloadTest(unittest.TestCase):
    SIGNED_KEYS = [
        "jwt",
        "response-content-disposition",
        "response-content-type",
        "rscd",
        "rsct",
        "se",
        "sig",
        "ske",
        "skoid",
        "sks",
        "skt",
        "sktid",
        "skv",
        "sp",
        "spr",
        "sr",
        "sv",
    ]

    @classmethod
    def _signed_values(cls, filename: str) -> dict[str, str]:
        jwt = ".".join(("a" * 100, "b" * 100, "c" * 101))
        return {
            "jwt": jwt,
            "response-content-disposition": f"attachment; filename={filename}",
            "response-content-type": "application/octet-stream",
            "rscd": f"attachment; filename={filename}",
            "rsct": "application/octet-stream",
            "se": "2026-08-21T03:00:00Z",
            "sig": base64.b64encode(b"s" * 32).decode("ascii"),
            "ske": "2026-08-21T04:00:00Z",
            "skoid": str(uuid.UUID("11111111-1111-1111-1111-111111111111")),
            "sks": "b",
            "skt": "2026-08-21T02:00:00Z",
            "sktid": str(uuid.UUID("22222222-2222-2222-2222-222222222222")),
            "skv": "2018-11-09",
            "sp": "r",
            "spr": "https",
            "sr": "b",
            "sv": "2018-11-09",
        }

    @classmethod
    def _redirect_contract(
        cls,
        *,
        initial_url: str,
        final_host: str,
        final_path: str,
        filename: str,
        size: int,
    ) -> dict[str, object]:
        values = cls._signed_values(filename)
        return {
            "finalHeaders": {
                "acceptRanges": "bytes",
                "contentLength": size,
                "contentType": "application/octet-stream",
            },
            "finalHost": final_host,
            "finalPath": final_path,
            "finalStatus": 200,
            "fixedQueryValues": {
                key: values[key]
                for key in (
                    "response-content-disposition",
                    "response-content-type",
                    "rscd",
                    "rsct",
                    "sks",
                    "skv",
                    "sp",
                    "spr",
                    "sr",
                    "sv",
                )
            },
            "initialStatus": 302,
            "initialUrl": initial_url,
            "jwtLength": 303,
            "queryKeys": cls.SIGNED_KEYS,
            "redirectCount": 1,
            "signatureLength": 44,
            "timestampKeys": ["skt", "se", "ske"],
            "uuidKeys": ["skoid", "sktid"],
        }

    def test_github_release_asset_download_validates_one_hop_and_redacts_receipt(self) -> None:
        payload = b"reviewed-jdk-archive"
        filename = "OpenJDK17U-jdk_x64_linux_hotspot_17.0.20_8.tar.gz"
        values = self._signed_values(filename)
        query = urllib.parse.urlencode(values)

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self) -> None:  # noqa: N802
                parsed = urllib.parse.urlsplit(self.path)
                if parsed.path == "/initial":
                    self.send_response(302)
                    self.send_header(
                        "Location",
                        f"http://127.0.0.1:{self.server.server_port}/asset/compile?{query}",
                    )
                    self.end_headers()
                    return
                self.send_response(200)
                self.send_header("Accept-Ranges", "bytes")
                self.send_header("Content-Length", str(len(payload)))
                self.send_header("Content-Type", "application/octet-stream")
                self.end_headers()
                self.wfile.write(payload)

            def log_message(self, _format: str, *_args: object) -> None:
                return

        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            source = f"http://127.0.0.1:{server.server_port}/initial"
            contract = self._redirect_contract(
                initial_url=source,
                final_host="127.0.0.1",
                final_path="/asset/compile",
                filename=filename,
                size=len(payload),
            )
            with tempfile.TemporaryDirectory() as directory:
                target = Path(directory) / "archive"
                result = download_verified_github_release_asset(
                    source,
                    destination=target,
                    expected_size=len(payload),
                    expected_sha256=hashlib.sha256(payload).hexdigest(),
                    redirect_contract=contract,
                    allow_loopback_http=True,
                )
                self.assertEqual(payload, result.path.read_bytes())
                encoded = canonical_receipt(result.receipt)
                for secret in (
                    values["jwt"],
                    values["sig"],
                    values["skoid"],
                    values["sktid"],
                    values["skt"],
                    query,
                ):
                    self.assertNotIn(secret.encode(), encoded)
                self.assertEqual(self.SIGNED_KEYS, result.receipt["location"]["queryKeys"])
                self.assertEqual(hashlib.sha256(payload).hexdigest(), result.receipt["archiveSha256"])
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)

    def test_signed_redirect_parser_rejects_query_role_and_grammar_mutations_without_leakage(self) -> None:
        filename = "OpenJDK17U-jdk_x64_linux_hotspot_17.0.20_8.tar.gz"
        initial = "https://github.com/adoptium/temurin17-binaries/releases/download/tag/asset.tar.gz"
        contract = self._redirect_contract(
            initial_url=initial,
            final_host="release-assets.githubusercontent.com",
            final_path="/github-production-release-asset/1/compile",
            filename=filename,
            size=10,
        )
        valid = self._signed_values(filename)
        mutations: list[tuple[str, dict[str, str], str]] = []
        for key in self.SIGNED_KEYS:
            missing = dict(valid)
            del missing[key]
            mutations.append((f"missing-{key}", missing, "/github-production-release-asset/1/compile"))
        for label, key, value in (
            ("fixed", "sp", "w"),
            ("jwt", "jwt", "secret.invalid"),
            ("sig", "sig", "secret-signature"),
            ("uuid", "skoid", "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA"),
            ("time", "skt", "2026-08-21T05:00:00Z"),
            ("blank", "sr", ""),
            ("filename", "rscd", "attachment; filename=other.tar.gz"),
        ):
            candidate = dict(valid)
            candidate[key] = value
            mutations.append((label, candidate, "/github-production-release-asset/1/compile"))
        extra = dict(valid)
        extra["unknown"] = "x"
        mutations.append(("extra", extra, "/github-production-release-asset/1/compile"))
        mutations.append(("cross-role", dict(valid), "/github-production-release-asset/2/runtime"))

        for label, values, path in mutations:
            location = "https://release-assets.githubusercontent.com" + path + "?" + urllib.parse.urlencode(values)
            with self.subTest(label=label), self.assertRaises(DownloadError) as raised:
                validate_github_release_asset_redirect(location, redirect_contract=contract)
            message = str(raised.exception)
            self.assertNotIn(values.get("jwt", "never"), message)
            self.assertNotIn(values.get("sig", "never"), message)

        valid_query = urllib.parse.urlencode(valid)
        valid_path = "/github-production-release-asset/1/compile"
        raw_mutations = {
            "duplicate": valid_query + "&jwt=duplicate",
            "empty-segment": valid_query + "&&extra=x",
            "malformed-percent": valid_query.replace("jwt=", "%ZZ=", 1),
            "control": valid_query.replace("sr=b", "sr=%00", 1),
        }
        for label, query in raw_mutations.items():
            location = f"https://release-assets.githubusercontent.com{valid_path}?{query}"
            with self.subTest(label=label), self.assertRaises(DownloadError):
                validate_github_release_asset_redirect(location, redirect_contract=contract)
        for label, location in (
            ("relative", f"{valid_path}?{valid_query}"),
            ("downgrade", f"http://release-assets.githubusercontent.com{valid_path}?{valid_query}"),
            ("port", f"https://release-assets.githubusercontent.com:443{valid_path}?{valid_query}"),
            ("fragment", f"https://release-assets.githubusercontent.com{valid_path}?{valid_query}#fragment"),
            ("literal-host", f"https://RELEASE-ASSETS.GITHUBUSERCONTENT.COM{valid_path}?{valid_query}"),
        ):
            with self.subTest(label=label), self.assertRaises(DownloadError):
                validate_github_release_asset_redirect(location, redirect_contract=contract)

        reverse_time = dict(valid)
        reverse_time["skt"], reverse_time["ske"] = reverse_time["ske"], reverse_time["skt"]
        with self.assertRaises(DownloadError):
            validate_github_release_asset_redirect(
                f"https://release-assets.githubusercontent.com{valid_path}?{urllib.parse.urlencode(reverse_time)}",
                redirect_contract=contract,
            )

    def test_github_release_asset_download_rejects_hop_status_header_and_byte_mutations(self) -> None:
        payload = b"reviewed-jdk-archive"
        filename = "OpenJDK17U-jdk_x64_linux_hotspot_17.0.20_8.tar.gz"
        query = urllib.parse.urlencode(self._signed_values(filename))

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self) -> None:  # noqa: N802
                parsed = urllib.parse.urlsplit(self.path)
                mode = parsed.path.rsplit("/", 1)[-1]
                if parsed.path.startswith("/initial/"):
                    if mode == "zero":
                        self.send_response(200)
                        self.end_headers()
                        return
                    self.send_response(302)
                    self.send_header(
                        "Location",
                        f"http://127.0.0.1:{self.server.server_port}/asset/{mode}?{query}",
                    )
                    self.end_headers()
                    return
                if mode == "second":
                    self.send_response(302)
                    self.send_header("Location", self.path)
                    self.end_headers()
                    return
                if mode == "status":
                    self.send_response(404)
                    self.end_headers()
                    return
                content = payload[:-1] if mode == "truncated" else payload
                self.send_response(200)
                self.send_header("Accept-Ranges", "none" if mode == "header" else "bytes")
                self.send_header("Content-Length", str(len(payload)))
                self.send_header("Content-Type", "application/octet-stream")
                self.end_headers()
                self.wfile.write(content)

            def log_message(self, _format: str, *_args: object) -> None:
                return

        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with tempfile.TemporaryDirectory() as directory:
                for mode in ("zero", "second", "status", "header", "truncated", "hash"):
                    source = f"http://127.0.0.1:{server.server_port}/initial/{mode}"
                    contract = self._redirect_contract(
                        initial_url=source,
                        final_host="127.0.0.1",
                        final_path=f"/asset/{mode}",
                        filename=filename,
                        size=len(payload),
                    )
                    destination = Path(directory) / mode
                    digest = "0" * 64 if mode == "hash" else hashlib.sha256(payload).hexdigest()
                    with self.subTest(mode=mode), self.assertRaises(DownloadError) as raised:
                        download_verified_github_release_asset(
                            source,
                            destination=destination,
                            expected_size=len(payload),
                            expected_sha256=digest,
                            redirect_contract=contract,
                            allow_loopback_http=True,
                        )
                    self.assertNotIn(query, str(raised.exception))
                    self.assertFalse(destination.exists())
                    self.assertFalse((destination.parent / f".{destination.name}.partial").exists())
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)

    def test_loopback_download_checks_exact_size_hash_redirect_and_partial_cleanup(self) -> None:
        payload = b"official-versioned-payload"

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self) -> None:  # noqa: N802
                if self.path == "/redirect":
                    self.send_response(302)
                    self.send_header("Location", f"http://127.0.0.1:{self.server.server_port}/payload")
                    self.end_headers()
                    return
                if self.path == "/truncated":
                    content = payload[:-1]
                elif self.path == "/oversized":
                    content = payload + b"x"
                else:
                    content = payload
                self.send_response(200)
                self.send_header("Content-Length", str(len(content)))
                self.end_headers()
                self.wfile.write(content)

            def log_message(self, _format: str, *_args: object) -> None:
                return

        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with tempfile.TemporaryDirectory() as directory:
                target = Path(directory) / "payload"
                digest = hashlib.sha256(payload).hexdigest()
                result = download_verified(
                    f"http://127.0.0.1:{server.server_port}/redirect",
                    destination=target,
                    expected_size=len(payload),
                    expected_sha256=digest,
                    allowed_hosts={"127.0.0.1"},
                    allow_loopback_http=True,
                )
                self.assertEqual(target, result)
                self.assertEqual(payload, target.read_bytes())

                for endpoint, size, sha in (
                    ("truncated", len(payload), digest),
                    ("oversized", len(payload), digest),
                    ("payload", len(payload), "0" * 64),
                ):
                    failed = Path(directory) / f"failed-{endpoint}"
                    with self.subTest(endpoint=endpoint), self.assertRaises(DownloadError):
                        download_verified(
                            f"http://127.0.0.1:{server.server_port}/{endpoint}",
                            destination=failed,
                            expected_size=size,
                            expected_sha256=sha,
                            allowed_hosts={"127.0.0.1"},
                            allow_loopback_http=True,
                        )
                    self.assertFalse(failed.exists())
                    self.assertFalse((failed.parent / f".{failed.name}.partial").exists())
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)

    def test_download_rejects_http_userinfo_query_and_unapproved_redirect_host(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / "payload"
            for url in (
                "http://example.com/payload",
                "https://user@example.com/payload",
                "https://example.com/payload?latest=true",
            ):
                with self.subTest(url=url), self.assertRaises(DownloadError):
                    download_verified(
                        url,
                        destination=destination,
                        expected_size=1,
                        expected_sha256="0" * 64,
                        allowed_hosts={"github.com"},
                    )


class WorkflowContractTest(unittest.TestCase):
    def test_release_assemble_uses_reproducibility_source_epoch(self) -> None:
        policy = load_policy(POLICY, root=ROOT)
        workflow = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
        governed_release = (
            "      - name: Release Assemble\n"
            "        run: |\n"
            "          source_date_epoch=$(git show -s --format=%ct \"$GITHUB_SHA\")\n"
            "          test \"$source_date_epoch\" -gt 0\n"
            "          SOURCE_DATE_EPOCH=\"$source_date_epoch\" \\\n"
            "            scripts/quality/build_inputs/run_gradle.sh :app:assembleProdRelease --warning-mode fail\n"
        )
        self.assertIn(governed_release, workflow)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shutil.copytree(ROOT / ".github", root / ".github")
            candidate = workflow.replace(
                governed_release,
                "      - name: Release Assemble\n"
                "        run: scripts/quality/build_inputs/run_gradle.sh :app:assembleProdRelease --warning-mode fail\n",
                1,
            )
            self.assertNotEqual(workflow, candidate)
            (root / ".github/workflows/android.yml").write_text(candidate, encoding="utf-8")
            with self.assertRaisesRegex(BuildInputError, "source epoch"):
                verify_repository_workflows(root, policy, promoted=True)

    def test_promotion_detection_ignores_non_build_input_allowances(self) -> None:
        workflow = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
        self.assertIn("continue-on-error: true", workflow)
        self.assertTrue(build_inputs_is_promoted(workflow))

    def test_checked_in_workflows_match_full_sha_and_closed_jdk_contract(self) -> None:
        verify_repository_workflows(ROOT, load_policy(POLICY, root=ROOT), promoted=True)

    def test_blocking_workflow_rejects_release_binding_mutations(self) -> None:
        policy = load_policy(POLICY, root=ROOT)
        workflow = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
        mutations = (
            ("    timeout-minutes: 60\n", "    timeout-minutes: 60\n    continue-on-error: true\n"),
            ("    needs: build-inputs\n", ""),
            (
                "name: reproducible-prod-release-receipt-${{ github.sha }}",
                "reproducible-prod-release-receipt-latest",
            ),
            ("verify_build_inputs.py release-bind", "verify_build_inputs.py verify"),
        )
        for old, new in mutations:
            with self.subTest(old=old), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                shutil.copytree(ROOT / ".github", root / ".github")
                candidate = workflow.replace(old, new, 1)
                self.assertNotEqual(workflow, candidate)
                (root / ".github/workflows/android.yml").write_text(candidate, encoding="utf-8")
                with self.assertRaises(BuildInputError):
                    verify_repository_workflows(root, policy, promoted=True)

    def test_workflow_rejects_environment_file_shadows_and_broad_codecov_secret(self) -> None:
        policy = load_policy(POLICY, root=ROOT)
        workflow = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
        mutations = (
            workflow.replace(
                "      - name: Verify reviewed build inputs\n",
                "      - name: Shadow Java\n"
                "        run: echo 'JAVA_HOME=/tmp/unreviewed' >> \"$GITHUB_ENV\"\n"
                "      - name: Verify reviewed build inputs\n",
                1,
            ),
            workflow.replace(
                "  coverage:\n",
                "  coverage:\n    env:\n      CODECOV_TOKEN: ${{ secrets.CODECOV_TOKEN }}\n",
                1,
            ),
        )
        for candidate in mutations:
            with self.subTest(), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                shutil.copytree(ROOT / ".github", root / ".github")
                (root / ".github/workflows/android.yml").write_text(candidate, encoding="utf-8")
                with self.assertRaises(BuildInputError):
                    verify_repository_workflows(root, policy, promoted=True)

    def test_workflow_rejects_extra_governed_process_edge(self) -> None:
        policy = load_policy(POLICY, root=ROOT)
        workflow = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
        candidate = workflow.replace(
            "      - name: Verify reviewed build inputs\n",
            "      - name: Undeclared Gradle child\n"
            "        run: scripts/quality/build_inputs/run_gradle.sh help\n"
            "      - name: Verify reviewed build inputs\n",
            1,
        )
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shutil.copytree(ROOT / ".github", root / ".github")
            (root / ".github/workflows/android.yml").write_text(candidate, encoding="utf-8")
            with self.assertRaisesRegex(BuildInputError, "process inventory mismatch"):
                verify_repository_workflows(root, policy, promoted=True)

    def test_build_input_evidence_upload_is_fail_closed(self) -> None:
        policy = load_policy(POLICY, root=ROOT)
        workflow = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
        build_inputs = workflow.split("  build-inputs:\n", 1)[1].split("\n  static-analysis:\n", 1)[0]
        self.assertIn(
            "      - name: Upload build-input evidence\n"
            "        if: always()\n",
            build_inputs,
        )
        self.assertIn("          if-no-files-found: error\n", build_inputs)
        candidate = workflow.replace(
            "          path: build/reports/build-inputs/**\n"
            "          if-no-files-found: error\n",
            "          path: build/reports/build-inputs/**\n"
            "          if-no-files-found: warn\n",
            1,
        )
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shutil.copytree(ROOT / ".github", root / ".github")
            (root / ".github/workflows/android.yml").write_text(candidate, encoding="utf-8")
            with self.assertRaises(BuildInputError):
                verify_repository_workflows(root, policy, promoted=True)

    def test_policy_static_source_hashes_match_current_bytes(self) -> None:
        policy = load_policy(POLICY, root=ROOT)
        for row in policy["staticSourceHashes"]:
            with self.subTest(path=row["path"]):
                data = (ROOT / row["path"]).read_bytes()
                self.assertEqual(row["sha256"], hashlib.sha256(data).hexdigest())


class DocumentationImportBoundaryTest(unittest.TestCase):
    def test_docs_runtime_rejects_preloaded_repository_module_outside_docs(self) -> None:
        with self.assertRaisesRegex(BridgeError, "non-docs module"):
            with _guarded_docs_runtime():
                from scripts.quality import build_inputs as _forbidden  # noqa: F401

    def test_docs_runtime_path_construction_does_not_recurse_through_import_hook(self) -> None:
        with _guarded_docs_runtime():
            Path(".").resolve()

    def test_governed_contract_checker_routes_docs_through_stable_bridge(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "docs").mkdir()
            (root / "docs/documentation-catalog.json").write_text("{}\n", encoding="utf-8")
            (root / "config/quality").mkdir(parents=True)
            (root / "config/quality/build-inputs.json").write_text("{}\n", encoding="utf-8")
            facade = root / "scripts/docs/validate.py"
            facade.parent.mkdir(parents=True)
            facade.write_text(
                "from pathlib import Path\nPath('direct-facade-ran').write_text('bad')\n",
                encoding="utf-8",
            )
            bridge = root / "scripts/quality/build_inputs/docs_gradle_validation_bridge.py"
            bridge.parent.mkdir(parents=True)
            bridge.write_text(
                "from pathlib import Path\nPath('stable-bridge-ran').write_text('ok')\n",
                encoding="utf-8",
            )

            previous = Path.cwd()
            os.chdir(root)
            try:
                with mock.patch.dict(
                    os.environ,
                    {"GASSTATION_BUILD_INPUT_EVIDENCE": "sealed-v1"},
                    clear=False,
                ):
                    self.assertEqual([], check_documentation_contracts(root))
            finally:
                os.chdir(previous)
            self.assertTrue((root / "stable-bridge-ran").is_file())
            self.assertFalse((root / "direct-facade-ran").exists())


class ReceiptAndReproducibilityTest(unittest.TestCase):
    def test_hosted_receipt_captures_only_build_required_android_packages(self) -> None:
        policy = generated_policy()
        self.assertEqual(
            [
                {"coordinate": "build-tools;36.0.0", "revision": "36.0.0"},
                {"coordinate": "platform-tools", "revision": "NOT RUN"},
                {"coordinate": "platforms;android-37.0", "revision": "2"},
            ],
            policy["android"]["requiredPackages"],
        )

        with tempfile.TemporaryDirectory() as directory:
            sdk = Path(directory)
            package_details = {
                "build-tools;36.0.0": ("36.0.0", "Android SDK Build-Tools 36"),
                "platform-tools": ("37.0.0", "Android SDK Platform-Tools"),
                "platforms;android-37.0": ("2", "Android SDK Platform 37.0"),
            }
            for coordinate, (revision, display_name) in package_details.items():
                package_xml = sdk.joinpath(*coordinate.split(";"), "package.xml")
                package_xml.parent.mkdir(parents=True, exist_ok=True)
                revision_nodes = "".join(
                    f"<{name}>{value}</{name}>"
                    for name, value in zip(("major", "minor", "micro"), revision.split("."))
                )
                package_xml.write_text(
                    "<repository><localPackage path='"
                    f"{coordinate}'><revision>{revision_nodes}</revision>"
                    f"<display-name>{display_name}</display-name>"
                    "</localPackage></repository>\n",
                    encoding="utf-8",
                )
            for relative in (
                "build-tools/36.0.0/aapt2",
                "build-tools/36.0.0/zipalign",
                "platform-tools/adb",
            ):
                executable = sdk / relative
                executable.parent.mkdir(parents=True, exist_ok=True)
                executable.write_bytes(relative.encode("utf-8"))

            with (
                mock.patch.dict(os.environ, {"ANDROID_SDK_ROOT": str(sdk)}, clear=False),
                mock.patch("scripts.quality.verify_build_inputs._tool_version", return_value="version"),
            ):
                receipt = _capture_android_sdk(policy)

        self.assertIsNotNone(receipt)
        self.assertEqual(
            ["build-tools;36.0.0", "platform-tools", "platforms;android-37.0"],
            [row["coordinate"] for row in receipt["packages"]],
        )
        self.assertEqual(["aapt2", "adb", "zipalign"], [row["name"] for row in receipt["tools"]])

    def test_receipt_rejects_duplicate_secret_absolute_path_and_stale_output(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            duplicate = root / "duplicate.json"
            duplicate.write_text('{"schemaVersion":1,"schemaVersion":1}\n', encoding="utf-8")
            with self.assertRaisesRegex(BuildInputError, "duplicate receipt key"):
                load_canonical_receipt(duplicate)

            for value in (
                {"schemaVersion": 1, "token": "must-not-serialize"},
                {"schemaVersion": 1, "value": "Bearer abcdefghijklmnop"},
                {"schemaVersion": 1, "path": "/Users/example/private"},
            ):
                with self.subTest(value=value), self.assertRaises(BuildInputError):
                    canonical_receipt(value)

            output = root / "receipt.json"
            write_canonical_receipt(output, {"schemaVersion": 1, "status": "PASS"})
            self.assertEqual(
                {"schemaVersion": 1, "status": "PASS"},
                load_canonical_receipt(output),
            )
            with self.assertRaisesRegex(BuildInputError, "already exists"):
                write_canonical_receipt(output, {"schemaVersion": 1, "status": "PASS"})

    def test_evidence_rows_reject_duplicate_and_symlink(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence = root / "build/reports/evidence.json"
            evidence.parent.mkdir(parents=True)
            evidence.write_text("{}\n", encoding="utf-8")
            self.assertEqual(
                "build/reports/evidence.json",
                relative_evidence_rows(root, [evidence])[0]["path"],
            )
            with self.assertRaisesRegex(BuildInputError, "duplicate evidence"):
                relative_evidence_rows(root, [evidence, evidence])
            alias = root / "alias.json"
            alias.symlink_to(evidence)
            with self.assertRaisesRegex(BuildInputError, "symlink"):
                relative_evidence_rows(root, [alias])

    def test_reproducibility_status_must_match_exact_byte_equality(self) -> None:
        common = {
            "source_sha": "1" * 40,
            "policy_sha256": "2" * 64,
            "task": ":app:assembleProdRelease",
            "output_identity": "app/build/outputs/apk/prod/release/*.apk",
        }
        equal = [
            {"id": "build-a", "sha256": "3" * 64, "size": 42},
            {"id": "build-b", "sha256": "3" * 64, "size": 42},
        ]
        self.assertEqual("PASS", reproducibility_receipt(**common, builds=equal, status="PASS")["status"])
        with self.assertRaisesRegex(BuildInputError, "does not match"):
            reproducibility_receipt(**common, builds=equal, status="FAIL")
        unequal = [equal[0], {"id": "build-b", "sha256": "4" * 64, "size": 42}]
        with self.assertRaisesRegex(BuildInputError, "does not match"):
            reproducibility_receipt(**common, builds=unequal, status="PASS")

    def test_zip_comparison_reports_changed_entry_without_normalizing(self) -> None:
        import zipfile

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first = root / "first.apk"
            second = root / "second.apk"
            with zipfile.ZipFile(first, "w") as archive:
                archive.writestr("classes.dex", b"first")
            with zipfile.ZipFile(second, "w") as archive:
                archive.writestr("classes.dex", b"second")
            differences = safe_zip_comparison(first, second)
            self.assertEqual(["classes.dex"], [row["entry"] for row in differences])


if __name__ == "__main__":
    unittest.main()
