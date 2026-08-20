from __future__ import annotations

import hashlib
import io
import json
import os
import shutil
import tarfile
import tempfile
import threading
import unittest
from unittest import mock
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from scripts.quality.build_inputs.archive import ArchiveError, safe_extract_tar
from scripts.quality.build_inputs.contracts import (
    BuildInputError,
    canonical_json_bytes,
    load_policy,
    scan_dependency_verification_bypasses,
    scan_dynamic_dependency_selectors,
    validate_gradle_arguments,
    validate_protected_environment,
    verify_wrapper,
)
from scripts.quality.build_inputs.downloader import DownloadError, download_verified
from scripts.quality.build_inputs.docs_gradle_validation_bridge import (
    BridgeError,
    _guarded_docs_runtime,
)
from scripts.quality.build_inputs.receipts import (
    canonical_receipt,
    load_canonical_receipt,
    relative_evidence_rows,
    write_canonical_receipt,
)
from scripts.quality.build_inputs.reproducibility import reproducibility_receipt, safe_zip_comparison
from scripts.quality.build_inputs.workflow import build_inputs_is_promoted, verify_repository_workflows
from scripts.quality.verify_build_inputs import _apply_reviewed_metadata_superset, verify_repository
from scripts.agent.check_contracts import check_documentation_contracts


ROOT = Path(__file__).resolve().parents[3]
POLICY = ROOT / "config/quality/build-inputs.json"


class CanonicalPolicyTest(unittest.TestCase):
    def test_checked_in_policy_is_canonical_closed_and_self_consistent(self) -> None:
        policy = load_policy(POLICY, root=ROOT)

        self.assertEqual(1, policy["schemaVersion"])
        self.assertEqual(b"{", POLICY.read_bytes()[:1])
        self.assertEqual(canonical_json_bytes(policy), POLICY.read_bytes())
        self.assertEqual("strict", policy["dependencyVerification"]["mode"])
        self.assertEqual([], policy["dependencyVerification"]["allowedInitScripts"])
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

    def test_policy_forbids_static_hashes_for_docs_facade_and_extensions(self) -> None:
        policy = load_policy(POLICY, root=ROOT)
        paths = {row["path"] for row in policy["staticSourceHashes"]}
        self.assertIn(
            "scripts/quality/build_inputs/docs_gradle_validation_bridge.py",
            paths,
        )
        self.assertNotIn("scripts/docs/validate.py", paths)
        self.assertFalse(any(path.startswith("scripts/docs/extensions/") for path in paths))

    def test_superseded_runtime_and_cross_wired_jdk_roles_fail_closed(self) -> None:
        baseline = json.loads(POLICY.read_text(encoding="utf-8"))
        mutations = []
        superseded = json.loads(json.dumps(baseline))
        superseded["jdks"]["runtime"]["version"] = "21.0.12+8"
        mutations.append(superseded)
        cross_wired = json.loads(json.dumps(baseline))
        cross_wired["jdks"]["compile"], cross_wired["jdks"]["runtime"] = (
            cross_wired["jdks"]["runtime"],
            cross_wired["jdks"]["compile"],
        )
        mutations.append(cross_wired)
        major_only = json.loads(json.dumps(baseline))
        major_only["jdks"]["runtime"]["version"] = "21"
        mutations.append(major_only)

        with tempfile.TemporaryDirectory() as directory:
            for index, mutation in enumerate(mutations):
                candidate = Path(directory) / f"jdk-mutation-{index}.json"
                candidate.write_bytes(canonical_json_bytes(mutation))
                with self.subTest(index=index), self.assertRaisesRegex(
                    BuildInputError,
                    "reviewed exact identity",
                ):
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
            "ORG_GRADLE_PROJECT_org.gradle.dependency.verification",
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
            "--dependency-verification",
            "strict",
            "-Dorg.gradle.java.installations.auto-detect=false",
            "-Dorg.gradle.java.installations.auto-download=false",
            "-Dorg.gradle.java.installations.paths=/verified/compile,/verified/runtime",
        ]
        validate_gradle_arguments(accepted)
        for injected in (
            accepted + ["-I", "/tmp/evil.gradle"],
            accepted + ["--init-script=/tmp/evil.gradle"],
            [token for token in accepted if token != "strict"] + ["off"],
            accepted + ["-Dorg.gradle.dependency.verification=lenient"],
            accepted + ["--write-verification-metadata", "sha256"],
        ):
            with self.subTest(argv=injected), self.assertRaises(BuildInputError):
                validate_gradle_arguments(injected)

    def test_active_source_scanner_rejects_every_dependency_verification_bypass(self) -> None:
        mutations = {
            "build.gradle.kts": "configurations.all { resolutionStrategy.disableDependencyVerification() }\n",
            "gradle.properties": "org.gradle.dependency.verification=lenient\n",
            "script.sh": "JAVA_OPTS=-Dorg.gradle.dependency.verification=off ./gradlew help\n",
            "runner.py": "subprocess.run(['./gradlew', '--dependency-verification=off'])\n",
            "workflow.yml": "run: ./gradlew help --init-script=/tmp/evil.gradle\n",
            "gradlew": "DEFAULT_JVM_OPTS='-Dorg.gradle.dependency.verification=off'\n",
        }
        for relative, content in mutations.items():
            with self.subTest(path=relative), tempfile.TemporaryDirectory() as directory:
                fixture = Path(directory)
                (fixture / relative).parent.mkdir(parents=True, exist_ok=True)
                (fixture / relative).write_text(content, encoding="utf-8")
                issues = scan_dependency_verification_bypasses(fixture)
                self.assertEqual(1, len(issues), issues)

    def test_scanner_ignores_test_fixtures_but_rejects_dynamic_versions(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory)
            test_source = fixture / "scripts/quality/tests/fixture.py"
            test_source.parent.mkdir(parents=True)
            test_source.write_text("value = '--dependency-verification off'\n", encoding="utf-8")
            catalog = fixture / "gradle/libs.versions.toml"
            catalog.parent.mkdir(parents=True)
            catalog.write_text('[versions]\nexample = "1.+"\n', encoding="utf-8")

            self.assertEqual([], scan_dependency_verification_bypasses(fixture))
            self.assertEqual(
                ["gradle/libs.versions.toml:2: dynamic dependency selector: 1.+"],
                scan_dynamic_dependency_selectors(fixture),
            )

    def test_scanner_checks_active_src_test_code_but_ignores_fixture_literals(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory)
            source = fixture / "build-logic/convention/src/test/kotlin/ActiveEscape.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                'val fixtureText = "disableDependencyVerification()"\n'
                "fun active(configurations: ConfigurationContainer) {\n"
                "  configurations.all { resolutionStrategy.disableDependencyVerification() }\n"
                "}\n",
                encoding="utf-8",
            )

            self.assertEqual(
                [
                    "build-logic/convention/src/test/kotlin/ActiveEscape.kt:3: "
                    "dependency verification bypass is forbidden",
                ],
                scan_dependency_verification_bypasses(fixture),
            )

    def test_scanner_rejects_unregistered_testkit_process_construction(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory)
            source = fixture / "build-logic/convention/src/test/kotlin/DirectRunner.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                'fun escape() = GradleRunner.create().withArguments("help").build()\n',
                encoding="utf-8",
            )
            issues = scan_dependency_verification_bypasses(fixture)
            self.assertEqual(1, len(issues), issues)
            self.assertIn("unregistered GradleRunner construction", issues[0])


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
    def test_metadata_capture_applies_only_checksum_preserving_superset(self) -> None:
        def metadata(rows: list[tuple[str, str]]) -> str:
            artifacts = "".join(
                f'<artifact name="{name}"><sha256 value="{digest}" origin="test"/></artifact>'
                for name, digest in rows
            )
            return (
                '<?xml version="1.0" encoding="UTF-8"?>\n'
                '<verification-metadata xmlns="https://schema.gradle.org/dependency-verification">'
                '<configuration><verify-metadata>true</verify-metadata></configuration>'
                '<components><component group="example" name="component" version="1">'
                f"{artifacts}</component></components></verification-metadata>\n"
            )

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            baseline = root / "baseline.xml"
            candidate = root / "candidate.xml"
            baseline.write_text(metadata([("one.jar", "1" * 64)]), encoding="utf-8")
            candidate.write_text(
                metadata([("one.jar", "1" * 64), ("two.jar", "2" * 64)]),
                encoding="utf-8",
            )
            self.assertEqual((0, 1), _apply_reviewed_metadata_superset(candidate, baseline))
            self.assertEqual(candidate.read_bytes(), baseline.read_bytes())

            candidate.write_text(metadata([("one.jar", "3" * 64)]), encoding="utf-8")
            with self.assertRaisesRegex(BuildInputError, "preserve"):
                _apply_reviewed_metadata_superset(candidate, baseline)

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
