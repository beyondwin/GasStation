from __future__ import annotations

import base64
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET
from unittest import mock
from pathlib import Path

from scripts.quality.build_inputs.contracts import BuildInputError, canonical_json_bytes
from scripts.quality.build_inputs.testkit_failure import (
    export_testkit_failure_evidence,
    validate_live_stage_manifest,
    validate_testkit_failure_evidence,
)
from scripts.quality.build_inputs.local_colima_evidence import (
    _copy_container_testkit_failure,
    _run_governed_container_command,
    _write_failed_attempt_package,
    ownership_marker,
    sealed_outer_timeout_marker,
)
from scripts.quality.verify_build_inputs import (
    _capture_metadata,
    _outer_timeout_arguments,
    _testkit_failure_output_path,
)

REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def _encoded(value: str) -> str:
    return base64.urlsafe_b64encode(value.encode("utf-8")).decode("ascii").rstrip("=")


def _write_failure_fixture(root: Path) -> tuple[Path, Path]:
    results = root / "test-results"
    results.mkdir(parents=True)
    (results / "TEST-com.example.SlowTest.xml").write_text(
        """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.SlowTest" tests="2" skipped="0" failures="1" errors="0" timestamp="2026-08-21T00:00:00" hostname="runner-secret" time="1.500">
  <properties/>
  <testcase name="passes" classname="com.example.SlowTest" time="0.250"/>
  <testcase name="fails" classname="com.example.SlowTest" time="1.250">
    <failure message="token=secret-value at /evidence-work/repository/private.gradle" type="org.gradle.testkit.runner.UnexpectedBuildFailure">Nested build failed at /home/runner/project/build.gradle token=secret-value
    at com.example.SecretTest.run(SecretTest.kt:42)
    at org.gradle.testkit.runner.internal.DefaultGradleRunner.run(DefaultGradleRunner.java:12)</failure>
  </testcase>
  <system-out>Nested stdout token=secret-value at /home/runner/output.log
</system-out>
  <system-err></system-err>
</testsuite>
""",
        encoding="utf-8",
    )
    trace = root / "worker-events.tsv"
    rows = [
        ("START", "Gradle Test Executor 1", "com.example.SlowTest", "passes", "1000"),
        (
            "OUTPUT",
            "Gradle Test Executor 1",
            "com.example.SlowTest",
            "passes",
            "StdOut",
            "Nested stdout token=secret-value at /home/runner/output.log\n",
        ),
        ("END", "Gradle Test Executor 1", "com.example.SlowTest", "passes", "SUCCESS", "250"),
        ("START", "Gradle Test Executor 2", "com.example.SlowTest", "fails", "1100"),
        ("END", "Gradle Test Executor 2", "com.example.SlowTest", "fails", "FAILURE", "1250"),
        ("START", "Gradle Test Executor 3", "com.example.PendingTest", "stillRunning", "1200"),
    ]
    encoded_rows = []
    for row in rows:
        identity = tuple(_encoded(value) for value in row[1:4])
        if row[0] == "START":
            encoded_rows.append("\t".join((row[0], *identity, row[4])))
        elif row[0] == "OUTPUT":
            encoded_rows.append("\t".join((row[0], *identity, row[4], _encoded(row[5]))))
        else:
            encoded_rows.append("\t".join((row[0], *identity, row[4], row[5])))
    trace.write_text("\n".join(encoded_rows) + "\n", encoding="utf-8")
    return results, trace


def _write_outer_timeout_fixture(attempt: Path, marker: dict[str, object], name: str) -> None:
    sealed = sealed_outer_timeout_marker(marker, governed_command=name)
    marker_path = attempt / "timeout-markers" / f"{name}.json"
    marker_path.parent.mkdir(parents=True, exist_ok=True)
    (attempt / "command-evidence").mkdir(parents=True, exist_ok=True)
    marker_body = canonical_json_bytes(sealed)
    marker_path.write_bytes(marker_body)
    receipt = {
        "mode": "0600",
        "path": "/evidence-work/task9-local-linux-ownership-marker.json",
        "schemaVersion": 1,
        "sha256": hashlib.sha256(marker_body).hexdigest(),
        "size": len(marker_body),
        "status": "PASS",
    }
    (attempt / "command-evidence" / f"{name}.timeout.json").write_bytes(canonical_json_bytes(receipt))


class TestKitFailureEvidenceTest(unittest.TestCase):
    def test_failure_export_is_canonical_redacted_and_worker_bound(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            results, trace = _write_failure_fixture(root)
            output = root / "sealed"

            summary = export_testkit_failure_evidence(results, trace, output)

            self.assertEqual(canonical_json_bytes(summary), (output / "summary.json").read_bytes())
            self.assertEqual(
                {"durationSeconds": "1.500", "errors": 0, "failures": 1, "skipped": 0, "tests": 2},
                summary["totals"],
            )
            self.assertEqual(
                [
                    {
                        "className": "com.example.SlowTest",
                        "durationSeconds": "1.250",
                        "name": "fails",
                        "status": "FAILURE",
                        "suite": "com.example.SlowTest",
                        "worker": "Gradle Test Executor 2",
                    },
                    {
                        "className": "com.example.SlowTest",
                        "durationSeconds": "0.250",
                        "name": "passes",
                        "status": "SUCCESS",
                        "suite": "com.example.SlowTest",
                        "worker": "Gradle Test Executor 1",
                    },
                ],
                summary["cases"],
            )
            self.assertEqual(
                ["Gradle Test Executor 1", "Gradle Test Executor 2", "Gradle Test Executor 3"],
                [row["worker"] for row in summary["workers"]],
            )
            self.assertEqual(1, len(summary["incompleteTests"]))
            self.assertEqual(1, len(summary["testLogs"]))
            self.assertEqual("com.example.SlowTest#passes", summary["testLogs"][0]["owner"])
            self.assertEqual("StdOut", summary["testLogs"][0]["destination"])
            self.assertEqual("org.gradle.testkit.runner.UnexpectedBuildFailure", summary["exceptions"][0]["type"])
            self.assertEqual("FAILURE", summary["exceptions"][0]["outcome"])
            self.assertEqual(64, len(summary["exceptions"][0]["summarySha256"]))
            self.assertEqual(64, len(summary["exceptions"][0]["logSha256"]))
            self.assertIn("<redacted-secret>", summary["exceptions"][0]["message"])
            self.assertIn("<redacted-path>", summary["exceptions"][0]["message"])
            inventory = validate_testkit_failure_evidence(output)
            self.assertEqual(summary, inventory)

            sealed_bytes = b"".join(path.read_bytes() for path in sorted(output.rglob("*")) if path.is_file())
            self.assertNotIn(b"secret-value", sealed_bytes)
            self.assertNotIn(b"hunter2", sealed_bytes)
            self.assertNotIn(b"/evidence-work", sealed_bytes)
            self.assertNotIn(b"/home/runner", sealed_bytes)
            self.assertNotIn(b"/Users/person", sealed_bytes)
            self.assertNotIn(b"SecretTest.kt:42", sealed_bytes)
            self.assertIn(b"<redacted-stack>", sealed_bytes)
            for artifact in summary["artifacts"]:
                body = (output / artifact["path"]).read_bytes()
                self.assertEqual(len(body), artifact["size"])
                self.assertEqual(hashlib.sha256(body).hexdigest(), artifact["sha256"])
                self.assertIsInstance(artifact["truncated"], bool)

    def test_failure_export_and_validator_reject_missing_or_mutated_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            results, trace = _write_failure_fixture(root)
            output = root / "sealed"

            (results / "TEST-com.example.SlowTest.xml").unlink()
            with self.assertRaisesRegex(BuildInputError, "JUnit XML"):
                export_testkit_failure_evidence(results, trace, output)

            results, trace = _write_failure_fixture(root / "second")
            trace.unlink()
            with self.assertRaisesRegex(BuildInputError, "worker trace"):
                export_testkit_failure_evidence(results, trace, root / "second-sealed")

            results, trace = _write_failure_fixture(root / "third")
            sealed = root / "third-sealed"
            export_testkit_failure_evidence(results, trace, sealed)
            artifact = sealed / json.loads((sealed / "summary.json").read_text())["artifacts"][0]["path"]
            artifact.write_bytes(artifact.read_bytes() + b"mutation")
            with self.assertRaisesRegex(BuildInputError, "hash or size"):
                validate_testkit_failure_evidence(sealed)

            results, trace = _write_failure_fixture(root / "fourth")
            sealed = root / "fourth-sealed"
            summary = export_testkit_failure_evidence(results, trace, sealed)
            exception_log = sealed / summary["exceptions"][0]["logPath"]
            exception_log.unlink()
            with self.assertRaisesRegex(BuildInputError, "artifact is missing"):
                validate_testkit_failure_evidence(sealed)

            results, trace = _write_failure_fixture(root / "fifth")
            sealed = root / "fifth-sealed"
            export_testkit_failure_evidence(results, trace, sealed)
            summary_path = sealed / "summary.json"
            summary = json.loads(summary_path.read_text())
            summary["workers"] = []
            summary_path.write_bytes(canonical_json_bytes(summary))
            with self.assertRaisesRegex(BuildInputError, "worker summary"):
                validate_testkit_failure_evidence(sealed)

            results, trace = _write_failure_fixture(root / "sixth")
            sealed = root / "sixth-sealed"
            export_testkit_failure_evidence(results, trace, sealed)
            summary_path = sealed / "summary.json"
            summary = json.loads(summary_path.read_text())
            summary["artifacts"][0]["truncated"] = True
            summary_path.write_bytes(canonical_json_bytes(summary))
            with self.assertRaisesRegex(BuildInputError, "truncation marker"):
                validate_testkit_failure_evidence(sealed)

            results, trace = _write_failure_fixture(root / "seventh")
            sealed = root / "seventh-sealed"
            export_testkit_failure_evidence(results, trace, sealed)
            summary_path = sealed / "summary.json"
            summary = json.loads(summary_path.read_text())
            summary["artifacts"][0]["owner"] = "unowned-test"
            summary_path.write_bytes(canonical_json_bytes(summary))
            with self.assertRaisesRegex(BuildInputError, "artifact ownership"):
                validate_testkit_failure_evidence(sealed)

    def test_failure_export_rejects_unmatched_worker_identity_and_unsafe_xml(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            results, trace = _write_failure_fixture(root)
            trace.write_text(
                trace.read_text().replace(_encoded("Gradle Test Executor 2"), _encoded("unknown worker")),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(BuildInputError, "worker identity"):
                export_testkit_failure_evidence(results, trace, root / "bad-worker")

            results, trace = _write_failure_fixture(root / "xml")
            xml = results / "TEST-com.example.SlowTest.xml"
            xml.write_text("<!DOCTYPE testsuite [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><testsuite/>")
            with self.assertRaisesRegex(BuildInputError, "DTD or entity"):
                export_testkit_failure_evidence(results, trace, root / "unsafe-xml")

            results, trace = _write_failure_fixture(root / "ambiguous")
            xml = results / "TEST-com.example.SlowTest.xml"
            xml.write_text(xml.read_text().replace("Nested stdout token=", "unowned output\nNested stdout token="))
            with self.assertRaisesRegex(BuildInputError, "stream differs from owned output trace"):
                export_testkit_failure_evidence(results, trace, root / "ambiguous-log")

    def test_failure_export_rejects_completed_worker_without_live_junit(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            results, trace = _write_failure_fixture(root)
            trace.write_text(
                trace.read_text()
                + "\t".join(
                    ("START", _encoded("Gradle Test Executor 1"), _encoded("com.example.Missing"), _encoded("missing"), "2"),
                )
                + "\n"
                + "\t".join(
                    ("END", _encoded("Gradle Test Executor 1"), _encoded("com.example.Missing"), _encoded("missing"), "SUCCESS", "1"),
                )
                + "\n",
            )
            with self.assertRaisesRegex(BuildInputError, "completed worker.*JUnit"):
                export_testkit_failure_evidence(results, trace, root / "sealed")

    def test_metadata_capture_seals_failure_before_deleting_temporary_session(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            session = root / "session"
            session.mkdir()
            output = root / "sealed"
            environment: dict[str, str] = {}

            def fail_after_writing_testkit_output(
                _command: object,
                *,
                installed: object,
                environment: dict[str, str],
                cwd: Path,
                metadata_write: bool,
            ) -> str:
                del installed, metadata_write
                self.assertEqual(str(output), environment["GASSTATION_TESTKIT_FAILURE_OUTPUT"])
                stable_results = Path(environment["GASSTATION_TESTKIT_FAILURE_OUTPUT"])
                results, trace = _write_failure_fixture(stable_results.parent / "generated")
                stable_results.mkdir()
                (results / "TEST-com.example.SlowTest.xml").replace(stable_results / ("TEST-" + "1" * 64 + ".xml"))
                trace.replace(stable_results / "worker-events.tsv")
                Path(environment["GASSTATION_TESTKIT_WORKER_TRACE"]).write_bytes(
                    (stable_results / "worker-events.tsv").read_bytes(),
                )
                artifacts = []
                for path in sorted(stable_results.iterdir()):
                    body = path.read_bytes()
                    artifacts.append({"path": path.name, "sha256": hashlib.sha256(body).hexdigest(), "size": len(body)})
                (stable_results / "live-stage-manifest.json").write_bytes(
                    canonical_json_bytes({"artifacts": artifacts, "schemaVersion": 1, "status": "SEALED"}),
                )
                raise BuildInputError("nested TestKit failure")

            git_result = subprocess.CompletedProcess(["git"], 0, stdout="1" * 40 + "\n", stderr="")
            with (
                mock.patch.dict(os.environ, {"GASSTATION_TESTKIT_FAILURE_OUTPUT": "set"}, clear=False),
                mock.patch(
                    "scripts.quality.verify_build_inputs._prepare_session",
                    return_value=(session, object(), environment),
                ),
                mock.patch("scripts.quality.verify_build_inputs._copy_capture_source"),
                mock.patch("scripts.quality.verify_build_inputs.subprocess.run", return_value=git_result),
                mock.patch(
                    "scripts.quality.verify_build_inputs._testkit_failure_output_path",
                    return_value=output,
                ),
                mock.patch(
                    "scripts.quality.verify_build_inputs._run_closed_command",
                    side_effect=fail_after_writing_testkit_output,
                ),
            ):
                with self.assertRaisesRegex(BuildInputError, "nested TestKit failure"):
                    _capture_metadata({}, [["./gradlew", ":build-logic:convention:test"]])

            self.assertFalse(session.exists())
            self.assertFalse(Path(str(output) + ".sealed").exists())
            self.assertEqual("FAIL", validate_testkit_failure_evidence(output)["status"])

    def test_convention_test_streams_live_junit_to_the_closed_failure_stage(self) -> None:
        source = (REPOSITORY_ROOT / "build-logic/convention/build.gradle.kts").read_text(encoding="utf-8")
        for literal in (
            'providers.environmentVariable("GASSTATION_TESTKIT_FAILURE_OUTPUT")',
            '"/evidence-work/testkit-failures"',
            'val liveJunit = failureOutput',
            'val liveJunitParent = liveJunit.parentFile',
            'liveJunitParent.mkdir()',
            'Files.isDirectory(liveJunitParent.toPath())',
            '!Files.isSymbolicLink(liveJunitParent.toPath())',
            'Files.move(',
            'StandardCopyOption.ATOMIC_MOVE',
            'result.exceptions',
            'system-out',
            'system-err',
            'finalizedBy(',
            'live-stage-manifest.json',
        ):
            with self.subTest(literal=literal):
                self.assertIn(literal, source)

    def test_outer_timeout_binds_the_actual_included_build_task_identity(self) -> None:
        source = (REPOSITORY_ROOT / "build-logic/convention/build.gradle.kts").read_text(encoding="utf-8")
        for literal in (
            'path == ":convention:test"',
            'project.path == ":convention"',
            'project.rootProject.name == "build-logic"',
            'project.gradle.buildPath == ":build-logic"',
            'project.gradle.buildPath + path == ":build-logic:convention:test"',
            'project.gradle.parent != null',
        ):
            with self.subTest(literal=literal):
                self.assertIn(literal, source)
        self.assertNotIn('project.path == ":",', source)
        self.assertIn('keys.joinToString(separator = ",", prefix = "{", postfix = "}\\n")', source)

    def test_exact_aapt2_read_only_seed_is_wired_to_every_nested_fixture(self) -> None:
        source = (REPOSITORY_ROOT / "build-logic/convention/build.gradle.kts").read_text(encoding="utf-8")
        fixture = (
            REPOSITORY_ROOT
            / "build-logic/convention/src/test/kotlin/fixtures/GradlePluginTestProject.kt"
        ).read_text(encoding="utf-8")
        for literal in (
            'val testKitAapt2Version = "9.3.2-15703166"',
            '"com.android.tools.build:aapt2:$testKitAapt2Version:linux"',
            '"com.android.tools.build:aapt2:$testKitAapt2Version:osx"',
            'tasks.register("prepareTestKitReadOnlyDependencyCache")',
            'dependsOn(captureTestKitDependencyVerificationMetadata)',
            'tasks.register("verifyTestKitReadOnlyDependencyCache")',
            'finalizedBy(verifyTestKitReadOnlyDependencyCache)',
            '"GRADLE_RO_DEP_CACHE"',
            '"seed-manifest.tsv"',
            'name.endsWith(".lock") || name == "gc.properties"',
            "val inventoryPaths =",
            "inventoryPaths.forEach { path ->",
            'Files.readString(manifest, Charsets.UTF_8) == seedInventory',
            'check(artifacts.size == testKitAapt2Artifacts.size)',
        ):
            with self.subTest(literal=literal):
                self.assertIn(literal, source)
        for literal in (
            'System.getenv("GRADLE_RO_DEP_CACHE")',
            '"GRADLE_RO_DEP_CACHE" to readOnlyDependencyCache.canonicalPath',
            '"TestKit requires the prepared read-only dependency cache"',
            '"seed-manifest.tsv"',
        ):
            with self.subTest(fixture_literal=literal):
                self.assertIn(literal, fixture)
        self.assertEqual(2, fixture.count(".withEnvironment(sanitizedEnvironment())"))
        self.assertNotIn("mustRunAfter(captureTestKitDependencyVerificationMetadata)", source)
        self.assertNotIn("onlyIf { testKitReadOnlyDependencyCache", source)

        metadata = ET.parse(REPOSITORY_ROOT / "gradle/verification-metadata.xml").getroot()
        components = [
            component
            for component in metadata.findall("./{*}components/{*}component")
            if component.attrib
            == {
                "group": "com.android.tools.build",
                "name": "aapt2",
                "version": "9.3.2-15703166",
            }
        ]
        self.assertEqual(1, len(components))
        artifacts = {
            artifact.attrib.get("name"): [node.attrib.get("value") for node in artifact.findall("./{*}sha256")]
            for artifact in components[0].findall("./{*}artifact")
        }
        self.assertEqual(
            {
                "aapt2-9.3.2-15703166-linux.jar",
                "aapt2-9.3.2-15703166-osx.jar",
                "aapt2-9.3.2-15703166.pom",
            },
            set(artifacts),
        )
        expected_checksums = {
            "aapt2-9.3.2-15703166-linux.jar": "e772a3dae8354764f1b0793903218427f483982445207f2e4ffc8c2026755bd4",
            "aapt2-9.3.2-15703166-osx.jar": "1e35bc2ce18c3aae840be2a29659ce50d6043e907a44d98ee1cf375d044fa29c",
            "aapt2-9.3.2-15703166.pom": "96be995aec595ca9d9fc3ae347ea0f22575e7f01f1e5212018a453d9c86e64a3",
        }
        for name, checksums in artifacts.items():
            with self.subTest(name=name):
                self.assertEqual([expected_checksums[name]], checksums)
                self.assertIn(expected_checksums[name], source)

    def test_live_stage_manifest_rehashes_exact_xml_and_worker_stream(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            stage = Path(directory)
            results, trace = _write_failure_fixture(stage / "fixture")
            xml = next(results.glob("TEST-*.xml"))
            destination = stage / ("TEST-" + "1" * 64 + ".xml")
            xml.replace(destination)
            trace.replace(stage / "worker-events.tsv")
            shutil.rmtree(stage / "fixture")
            artifacts = []
            for path in sorted((destination, stage / "worker-events.tsv")):
                body = path.read_bytes()
                artifacts.append({"path": path.name, "sha256": hashlib.sha256(body).hexdigest(), "size": len(body)})
            manifest = {"artifacts": artifacts, "schemaVersion": 1, "status": "SEALED"}
            (stage / "live-stage-manifest.json").write_bytes(canonical_json_bytes(manifest))

            self.assertEqual(manifest, validate_live_stage_manifest(stage))
            destination.write_bytes(destination.read_bytes() + b"mutation")
            with self.assertRaisesRegex(BuildInputError, "hash or size"):
                validate_live_stage_manifest(stage)

    def test_failure_output_path_and_container_copy_are_closed_and_validated(self) -> None:
        self.assertEqual(
            Path("/evidence-work/testkit-failures/metadata-capture-1"),
            _testkit_failure_output_path("/evidence-work/testkit-failures/metadata-capture-1"),
        )
        for mutation in (
            "/evidence-work/testkit-failures/metadata-capture-3",
            "/tmp/metadata-capture-1",
            "/evidence-work/testkit-failures/../escape",
            "relative/output",
        ):
            with self.subTest(mutation=mutation), self.assertRaisesRegex(BuildInputError, "failure output"):
                _testkit_failure_output_path(mutation)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            results, trace = _write_failure_fixture(root / "source")
            source = root / "sealed-source"
            export_testkit_failure_evidence(results, trace, source)
            attempt = root / "attempt-000001"
            attempt.mkdir()
            marker = ownership_marker(
                source_commit="1" * 40,
                policy_sha256="2" * 64,
                attempt_id="attempt-000001",
                main_base_commit="7b8c149c9f792aaf43cc00a94ba671929008979e",
                runtime_data_id="3" * 64,
            )
            (attempt / "ownership-marker.json").write_bytes(canonical_json_bytes(marker))
            _write_outer_timeout_fixture(attempt, marker, "metadata-capture-1")
            evidence = attempt / "command-evidence"
            evidence.mkdir(exist_ok=True)
            command_sha = "4" * 64
            log = b"nested timeout\n"
            (evidence / "metadata-capture-1.started.json").write_bytes(
                canonical_json_bytes(
                    {"commandSha256": command_sha, "name": "metadata-capture-1", "schemaVersion": 1, "status": "STARTED"},
                ),
            )
            (evidence / "metadata-capture-1.log").write_bytes(log)
            (evidence / "metadata-capture-1.result.json").write_bytes(
                canonical_json_bytes(
                    {
                        "commandSha256": command_sha,
                        "exitCode": 9,
                        "logSha256": hashlib.sha256(log).hexdigest(),
                        "logSize": len(log),
                        "name": "metadata-capture-1",
                        "schemaVersion": 1,
                        "status": "FAIL",
                        "truncated": False,
                    },
                ),
            )

            def docker_copy(_config: Path, *arguments: str, **_kwargs: object) -> subprocess.CompletedProcess[bytes]:
                destination = Path(arguments[-1])
                shutil.copytree(source, destination)
                return subprocess.CompletedProcess(["docker"], 0, stdout=b"", stderr=b"")

            with mock.patch(
                "scripts.quality.build_inputs.local_colima_evidence._docker",
                side_effect=docker_copy,
            ):
                descriptor = _copy_container_testkit_failure(
                    Path("/tmp/docker-config"),
                    attempt=attempt,
                    name="metadata-capture-1",
                )
            copied = attempt / "testkit-failures/metadata-capture-1"
            self.assertEqual("FAIL", validate_testkit_failure_evidence(copied, require_final=True)["status"])
            final_path = copied / "testkit-failure-summary.json"
            self.assertEqual(hashlib.sha256(final_path.read_bytes()).hexdigest(), descriptor["sha256"])
            self.assertEqual(final_path.stat().st_size, descriptor["size"])
            self.assertFalse(descriptor["truncated"])

    def test_generated_sealed_outer_timeout_marker_injects_exact_35_minute_argument(self) -> None:
        policy_path = REPOSITORY_ROOT / "config/quality/build-inputs.json"
        policy = json.loads(policy_path.read_bytes())
        marker = ownership_marker(
            source_commit="1" * 40,
            policy_sha256=hashlib.sha256(canonical_json_bytes(policy)).hexdigest(),
            attempt_id="attempt-000001",
            main_base_commit="7b8c149c9f792aaf43cc00a94ba671929008979e",
            runtime_data_id="3" * 64,
        )
        sealed = sealed_outer_timeout_marker(marker, governed_command="metadata-capture-1")

        with tempfile.TemporaryDirectory() as directory:
            marker_path = Path(directory) / "task9-local-linux-ownership-marker.json"
            marker_path.write_bytes(canonical_json_bytes(sealed))
            marker_path.chmod(0o600)
            with (
                mock.patch.dict(
                    os.environ,
                    {"GASSTATION_TASK9_LOCAL_LINUX_OWNERSHIP_MARKER": str(marker_path)},
                    clear=False,
                ),
                mock.patch(
                    "scripts.quality.verify_build_inputs._OUTER_TIMEOUT_MARKER_PATH",
                    str(marker_path),
                ),
            ):
                try:
                    arguments = _outer_timeout_arguments(
                        policy,
                        Path("/evidence-work/testkit-failures/metadata-capture-1"),
                    )
                except BuildInputError as error:
                    self.fail(f"generated sealed marker must pass the verifier handoff: {error}")

        self.assertEqual(
            ["-Pgasstation.task9LocalLinuxConventionTestTimeoutMinutes=35"],
            arguments,
        )

    def test_failed_metadata_command_exports_inner_evidence_before_propagating_nonzero(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            results, trace = _write_failure_fixture(root / "source")
            source = root / "sealed-source"
            export_testkit_failure_evidence(results, trace, source)
            attempt = root / "attempt-000001"
            attempt.mkdir()
            marker = ownership_marker(
                source_commit="1" * 40,
                policy_sha256="2" * 64,
                attempt_id="attempt-000001",
                main_base_commit="7b8c149c9f792aaf43cc00a94ba671929008979e",
                runtime_data_id="3" * 64,
            )
            (attempt / "ownership-marker.json").write_bytes(canonical_json_bytes(marker))

            def complete(
                _config: Path,
                _command: str,
                *,
                timeout: int,
                testkit_failure_output: str | None,
                outer_timeout_marker: str | None,
            ) -> subprocess.CompletedProcess[bytes]:
                del timeout
                self.assertEqual(
                    "/evidence-work/testkit-failures/metadata-capture-1",
                    testkit_failure_output,
                )
                self.assertEqual(
                    "/evidence-work/task9-local-linux-ownership-marker.json",
                    outer_timeout_marker,
                )
                return subprocess.CompletedProcess(["docker"], 9, stdout=b"nested timeout\n", stderr=b"")

            def docker_copy(_config: Path, *arguments: str, **_kwargs: object) -> subprocess.CompletedProcess[bytes]:
                if str(arguments[-1]).startswith("gasstation-task9-evidence:"):
                    return subprocess.CompletedProcess(["docker"], 0, stdout=b"", stderr=b"")
                shutil.copytree(source, Path(arguments[-1]))
                return subprocess.CompletedProcess(["docker"], 0, stdout=b"", stderr=b"")

            with (
                mock.patch(
                    "scripts.quality.build_inputs.local_colima_evidence._container_exec_completed",
                    side_effect=complete,
                ),
                mock.patch(
                    "scripts.quality.build_inputs.local_colima_evidence.validate_outer_timeout_marker",
                    return_value={},
                ),
                mock.patch(
                    "scripts.quality.build_inputs.local_colima_evidence._container_exec",
                    return_value=hashlib.sha256(
                        canonical_json_bytes(
                            sealed_outer_timeout_marker(marker, governed_command="metadata-capture-1"),
                        ),
                    ).hexdigest(),
                ),
                mock.patch(
                    "scripts.quality.build_inputs.local_colima_evidence._docker",
                    side_effect=docker_copy,
                ),
            ):
                with self.assertRaisesRegex(BuildInputError, "metadata-capture-1"):
                    _run_governed_container_command(
                        Path("/tmp/docker-config"),
                        attempt=attempt,
                        name="metadata-capture-1",
                        shell="python3 scripts/quality/verify_build_inputs.py metadata-capture",
                        original_marker=marker,
                    )

            evidence = attempt / "command-evidence"
            rows = json.loads((evidence / "metadata-capture-1.testkit.json").read_text())
            self.assertEqual("FAIL", rows["status"])
            self.assertEqual("nested timeout\n", (evidence / "metadata-capture-1.log").read_text())
            testkit_output = attempt / "testkit-failures/metadata-capture-1"
            validate_testkit_failure_evidence(testkit_output, require_final=True)
            final_summary = json.loads((testkit_output / "testkit-failure-summary.json").read_text())
            self.assertEqual("1" * 40, final_summary["sourceCommit"])
            self.assertEqual("2" * 64, final_summary["policySha256"])
            self.assertEqual("attempt-000001", final_summary["attemptId"])
            self.assertEqual(marker["markerSha256"], final_summary["markerSha256"])
            self.assertEqual(2100, final_summary["testContract"]["outerTimeoutSeconds"])
            self.assertEqual(2400, final_summary["testContract"]["repositoryAndNestedTimeoutSeconds"])
            self.assertEqual(5, final_summary["testContract"]["maxParallelForks"])
            self.assertEqual(90, final_summary["testContract"]["expectedTests"])
            self.assertEqual(52, final_summary["testContract"]["expectedOwners"])
            self.assertEqual(
                "94346faebdd4989670c3518513cf0998bcf871c6775d2c8d71687a1200692930",
                final_summary["testContract"]["dispatchSha256"],
            )
            self.assertEqual(
                "763bf9c30b2582b8b09a1ee4b5ce25a6234baf8c10d49238083a1e7c56015bd3",
                final_summary["testContract"]["lanesSha256"],
            )
            self.assertEqual("35", final_summary["outerTimeout"]["propertyValue"])
            self.assertEqual(marker["markerSha256"], final_summary["outerTimeout"]["ownershipMarkerSha256"])
            self.assertEqual(9, final_summary["governedCommand"]["exitCode"])
            self.assertEqual(64, len(final_summary["governedCommand"]["commandSha256"]))
            self.assertEqual(64, len(final_summary["governedCommand"]["logSha256"]))
            _write_failed_attempt_package(
                attempt,
                {"error": "metadata failure", "schemaVersion": 1, "status": "FAIL"},
            )
            packaged = attempt / "failure-package/testkit-failures/metadata-capture-1"
            self.assertEqual("FAIL", validate_testkit_failure_evidence(packaged, require_final=True)["status"])
            package_manifest = json.loads(
                (attempt / "failure-package/failed-attempt-package.json").read_text(),
            )
            self.assertIn(
                "testkit-failures/metadata-capture-1/testkit-failure-summary.json",
                {row["path"] for row in package_manifest["files"]},
            )


if __name__ == "__main__":
    unittest.main()
