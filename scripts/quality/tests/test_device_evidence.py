import copy
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from device_evidence import (
    DeviceEvidenceError,
    canonical_json_bytes,
    load_policy,
    load_quarantine,
    sha256_file,
    validate_policy,
    verify_attempt,
)


ROOT = Path(__file__).resolve().parents[3]
POLICY = ROOT / "config/quality/device-evidence-policy.json"


class DeviceEvidencePolicyTest(unittest.TestCase):
    def test_checked_in_policy_is_closed_and_exact(self):
        policy = load_policy(POLICY)

        self.assertEqual(
            ["api24-scheduled", "api28-pr-smoke", "api28-scheduled", "api36-scheduled"],
            sorted(policy["lanes"]),
        )
        self.assertEqual(5, len(policy["inventories"]["appPrSmoke"]))
        self.assertEqual(10, len(policy["inventories"]["appFull"]))
        self.assertEqual(6, len(policy["inventories"]["roomMigrations"]))
        self.assertEqual(1, len(policy["inventories"]["locationGeocoder"]))
        self.assertEqual([], load_quarantine(POLICY, policy))

    def test_policy_rejects_unknown_fields_duplicate_inventory_and_budget_collision(self):
        policy = load_policy(POLICY)
        mutations = []

        unknown = copy.deepcopy(policy)
        unknown["trustedActualApi"] = 28
        mutations.append(unknown)

        duplicate = copy.deepcopy(policy)
        duplicate["inventories"]["appFull"].append(duplicate["inventories"]["appFull"][0])
        mutations.append(duplicate)

        collision = copy.deepcopy(policy)
        collision["lanes"]["api28-pr-smoke"]["budgets"]["reserveMinutes"] = 0
        mutations.append(collision)

        api24_gmd = copy.deepcopy(policy)
        api24_gmd["lanes"]["api24-scheduled"]["device"]["kind"] = "gmd"
        mutations.append(api24_gmd)

        unbounded_collection = copy.deepcopy(policy)
        unbounded_collection["lanes"]["api28-pr-smoke"]["phaseSeconds"]["collection"] = 0
        mutations.append(unbounded_collection)

        phase_overrun = copy.deepcopy(policy)
        phase_overrun["lanes"]["api24-scheduled"]["phaseSeconds"]["provision"] += 60
        mutations.append(phase_overrun)

        for mutated in mutations:
            with self.subTest(mutated=mutated):
                with self.assertRaises(DeviceEvidenceError):
                    validate_policy(mutated)

    def test_quarantine_is_identity_bound_expiring_and_never_changes_inventory(self):
        policy = load_policy(POLICY)
        entries = [
            {
                "test": policy["inventories"]["appFull"][0],
                "owner": "quality-owner",
                "issue": "https://example.invalid/issues/123",
                "reason": "hosted image regression",
                "created": "2026-08-20",
                "expires": "2026-08-27",
            }
        ]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            policy_path = root / "config/quality/device-evidence-policy.json"
            overlay_path = root / "config/quality/device-evidence-quarantine.json"
            self._write_json(policy_path, policy)
            self._write_json(overlay_path, {"schemaVersion": 1, "entries": entries})
            self.assertEqual(entries, load_quarantine(policy_path, policy, today="2026-08-20"))
            with self.assertRaises(DeviceEvidenceError):
                load_quarantine(policy_path, policy, today="2026-08-28")
            entries[0]["test"] = "com.gasstation.*"
            self._write_json(overlay_path, {"schemaVersion": 1, "entries": entries})
            with self.assertRaises(DeviceEvidenceError):
                load_quarantine(policy_path, policy, today="2026-08-20")

    @staticmethod
    def _write_json(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(canonical_json_bytes(value))


class DeviceEvidenceVerifierTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.policy = load_policy(POLICY)
        self.policy_path = self.root / "config/quality/device-evidence-policy.json"
        self._write_json(self.policy_path, self.policy)
        self.overlay_path = self.root / "config/quality/device-evidence-quarantine.json"
        self._write_json(self.overlay_path, {"schemaVersion": 1, "entries": []})
        self.attempt_root = self.root / "attempt"
        self.attempt_root.mkdir()

    def tearDown(self):
        self.temp.cleanup()

    def test_exact_pr_smoke_attempt_passes(self):
        result = self._write_complete_attempt("api28-pr-smoke")

        self.assertEqual("PASS", result["status"])
        self.assertEqual(5, result["counters"]["tests"])
        self.assertEqual(0, result["counters"]["skipped"])

    def test_mutations_fail_closed(self):
        mutators = {
            "wrong-api": lambda facts, completion, junit: facts.update(apiLevel=36),
            "wrong-serial": lambda facts, completion, junit: facts.update(serial="untrusted-device"),
            "sharded-run": lambda facts, completion, junit: facts.update(shards=2),
            "self-asserted-device": lambda facts, completion, junit: facts.update(source="policy"),
            "cached-task": lambda facts, completion, junit: completion["commands"][0].update(outcome="FROM-CACHE"),
            "nonzero-command": lambda facts, completion, junit: completion["commands"][0].update(exitCode=1),
            "required-skip": lambda facts, completion, junit: junit.update(skipped={junit["tests"][0]}),
            "missing-test": lambda facts, completion, junit: junit["tests"].pop(),
            "extra-test": lambda facts, completion, junit: junit["tests"].append("com.gasstation.UnreviewedTest#unexpected"),
            "duplicate-test": lambda facts, completion, junit: junit["tests"].append(junit["tests"][0]),
            "zero-test": lambda facts, completion, junit: junit["tests"].clear(),
            "dirty-post-run": lambda facts, completion, junit: completion.update(postRunStatus=" M source.kt"),
            "cleanup-failure": lambda facts, completion, junit: completion.update(cleanupStatus="FAIL"),
        }

        for name, mutator in mutators.items():
            with self.subTest(name=name):
                self._reset_attempt_root()
                with self.assertRaises(DeviceEvidenceError):
                    self._write_complete_attempt("api28-pr-smoke", mutator=mutator)

    def test_pre_and_post_receipt_mutations_fail_closed(self):
        attempt_mutators = {
            "dirty-checkout": lambda attempt: attempt.update(checkoutStatus=" M app/source.kt"),
            "wrong-event-sha": lambda attempt: attempt.update(eventSha="4" * 40),
            "wrong-filter": lambda attempt: attempt.update(filter=None),
            "wrong-command": lambda attempt: attempt["expectedCommands"].append(":app:unexpected"),
            "wrong-result-root": lambda attempt: attempt["resultRoots"].append("other/build/results"),
        }
        for name, mutator in attempt_mutators.items():
            with self.subTest(name=name):
                self._reset_attempt_root()
                with self.assertRaises(DeviceEvidenceError):
                    self._write_complete_attempt("api28-pr-smoke", attempt_mutator=mutator)

        self._reset_attempt_root()
        with self.assertRaises(DeviceEvidenceError):
            self._write_complete_attempt(
                "api28-pr-smoke",
                file_mutator=lambda root: (root / "results/app.xml").write_bytes(b"\xff"),
            )

        self._reset_attempt_root()
        with self.assertRaises(DeviceEvidenceError):
            self._write_complete_attempt(
                "api28-pr-smoke",
                file_mutator=lambda root: (root / "logs/gradle-app.log").write_text(
                    "Unified Test Platform error\n", encoding="utf-8"
                ),
            )

    def test_missing_completion_and_mismatched_command_receipt_fail_closed(self):
        self._write_complete_attempt("api28-pr-smoke")
        (self.attempt_root / "completion.json").unlink()
        with self.assertRaises(DeviceEvidenceError):
            verify_attempt(self.policy_path, self.attempt_root)

        self._reset_attempt_root()
        with self.assertRaises(DeviceEvidenceError):
            self._write_complete_attempt(
                "api28-pr-smoke",
                file_mutator=lambda root: (root / "raw/commands.json").write_text("[]\n", encoding="utf-8"),
            )

    def test_completion_hash_change_and_symlink_escape_fail_closed(self):
        self._write_complete_attempt("api28-pr-smoke")
        log = self.attempt_root / "logs/gradle-app.log"
        log.write_text("changed after completion\n", encoding="utf-8")
        with self.assertRaises(DeviceEvidenceError):
            verify_attempt(self.policy_path, self.attempt_root)

        self._reset_attempt_root()
        outside = self.root / "outside.xml"
        outside.write_text("outside\n", encoding="utf-8")
        (self.attempt_root / "results").mkdir(parents=True)
        os.symlink(outside, self.attempt_root / "results/app.xml")
        with self.assertRaises(DeviceEvidenceError):
            self._write_complete_attempt("api28-pr-smoke", preserve_junit=True)

    def test_active_quarantine_is_quarantined_and_nonpass(self):
        entries = [
            {
                "test": self.policy["inventories"]["appPrSmoke"][0],
                "owner": "quality-owner",
                "issue": "https://example.invalid/issues/123",
                "reason": "hosted image regression",
                "created": "2026-08-20",
                "expires": "2026-08-27",
            }
        ]
        self._write_json(self.overlay_path, {"schemaVersion": 1, "entries": entries})

        result = self._write_complete_attempt("api28-pr-smoke", today="2026-08-20")

        self.assertEqual("QUARANTINED", result["status"])
        self.assertNotEqual("PASS", result["status"])

    def test_failed_app_test_requires_exact_attempt_bound_png_and_diagnostic(self):
        def fail_one(facts, completion, junit):
            junit["failed"] = {junit["tests"][0]}
            completion["commands"][0]["exitCode"] = 1

        with self.assertRaises(DeviceEvidenceError):
            self._write_complete_attempt("api28-pr-smoke", mutator=fail_one)

        self._reset_attempt_root()
        result = self._write_complete_attempt(
            "api28-pr-smoke",
            mutator=fail_one,
            include_failure_artifacts=True,
        )
        self.assertEqual("FAIL", result["status"])

    def test_real_nonzero_test_failure_distinguishes_transport_collection_failure(self):
        def fail_one(facts, completion, junit):
            junit["failed"] = {junit["tests"][0]}
            completion["commands"][0]["exitCode"] = 1

        with self.assertRaisesRegex(DeviceEvidenceError, "lacks exact pulled PNG/diagnostic"):
            self._write_complete_attempt("api28-pr-smoke", mutator=fail_one)

        self._reset_attempt_root()
        result = self._write_complete_attempt(
            "api28-pr-smoke",
            mutator=fail_one,
            include_failure_artifacts=True,
        )
        self.assertEqual("FAIL", result["status"])

    def test_real_nonzero_junit_error_requires_app_failure_artifacts(self):
        def error_one(facts, completion, junit):
            junit["errors"] = {junit["tests"][0]}
            completion["commands"][0]["exitCode"] = 1

        with self.assertRaisesRegex(DeviceEvidenceError, "lacks exact pulled PNG/diagnostic"):
            self._write_complete_attempt("api28-pr-smoke", mutator=error_one)

        self._reset_attempt_root()
        result = self._write_complete_attempt(
            "api28-pr-smoke",
            mutator=error_one,
            include_failure_artifacts=True,
        )
        self.assertEqual("FAIL", result["status"])

    def test_metadata_policy_echo_and_cleanup_assertion_without_raw_match_fail_closed(self):
        with self.assertRaises(DeviceEvidenceError):
            self._write_complete_attempt(
                "api28-pr-smoke",
                file_mutator=lambda root: self._rewrite_json(
                    root / "raw/device-metadata.json",
                    lambda value: value.update(locale="policy-echo"),
                ),
            )

        self._reset_attempt_root()
        with self.assertRaises(DeviceEvidenceError):
            self._write_complete_attempt(
                "api28-pr-smoke",
                file_mutator=lambda root: (root / "raw/gmd-task-0.json").unlink(),
            )

        self._reset_attempt_root()
        with self.assertRaises(DeviceEvidenceError):
            self._write_complete_attempt(
                "api24-scheduled",
                file_mutator=lambda root: self._rewrite_json(
                    root / "raw/teardown.json",
                    lambda value: value.update(emulatorPidAlive=True),
                ),
            )

    def _write_complete_attempt(
        self,
        lane,
        mutator=None,
        attempt_mutator=None,
        file_mutator=None,
        preserve_junit=False,
        include_failure_artifacts=False,
        today="2026-08-20",
    ):
        lane_policy = load_policy(self.policy_path, today=today)["lanes"][lane]
        expected = []
        for inventory in lane_policy["inventories"]:
            expected.extend(load_policy(self.policy_path, today=today)["inventories"][inventory])
        junit = {"tests": expected[:], "skipped": set(), "failed": set(), "errors": set()}
        facts = {
            "schemaVersion": 1,
            "source": "agp-utp" if lane_policy["device"]["kind"] == "gmd" else "adb",
            "lane": lane,
            "kind": lane_policy["device"]["kind"],
            "abi": "x86_64",
            "apiLevel": lane_policy["device"]["apiLevel"],
            "fingerprint": f"fixture/{lane_policy['device']['apiLevel']}",
            "imagePackage": lane_policy["device"]["imagePackage"],
            "profile": lane_policy["device"]["profile"],
            "imageSource": lane_policy["device"]["imageSource"],
            "locale": "ko-KR",
            "permissionControllerPackage": (
                "com.android.packageinstaller"
                if lane_policy["device"]["apiLevel"] <= 28
                else "com.android.permissioncontroller"
            ),
            "permissionControllerRevision": str(lane_policy["device"]["apiLevel"]),
            "serial": lane_policy["device"].get("serial", lane_policy["device"]["name"]),
            "shards": 1,
        }
        completion = {
            "schemaVersion": 1,
            "attemptSha256": "",
            "policySha256": sha256_file(self.policy_path),
            "checkoutCommit": "1" * 40,
            "commands": [
                {"task": task, "exitCode": 0, "outcome": "EXECUTED"}
                for task in lane_policy["gradleTasks"]
            ],
            "artifacts": [],
            "postRunHead": "1" * 40,
            "postRunStatus": "",
            "cleanupStatus": "PASS",
            "completedAt": "2026-08-20T00:01:00Z",
        }
        if mutator:
            mutator(facts, completion, junit)

        attempt = {
            "schemaVersion": 1,
            "checkoutCommit": "1" * 40,
            "checkoutStatus": "",
            "eventSha": "1" * 40,
            "policySha256": sha256_file(self.policy_path),
            "wrapperSha256": "2" * 64,
            "verifierSha256": "3" * 64,
            "lane": lane,
            "runId": "fixture-run",
            "attemptNumber": "1",
            "attemptId": "fixture-run-1",
            "filter": lane_policy["filter"],
            "expectedCommands": lane_policy["gradleTasks"][:],
            "resultRoots": lane_policy["resultRoots"][:],
            "startedAt": "2026-08-20T00:00:00Z",
            "toolIdentities": {"java": "fixture-java", "gradle": "9.6.1"},
        }
        if attempt_mutator:
            attempt_mutator(attempt)
        self._write_json(self.attempt_root / "attempt.json", attempt)
        completion["attemptSha256"] = sha256_file(self.attempt_root / "attempt.json")

        if not preserve_junit:
            self._write_junit(self.attempt_root / "results/app.xml", junit)
        self._write_json(self.attempt_root / "raw/device-metadata.json", facts)
        (self.attempt_root / "reports/app").mkdir(parents=True, exist_ok=True)
        (self.attempt_root / "reports/app/index.html").write_text("<html>fixture</html>\n", encoding="utf-8")
        (self.attempt_root / "logs").mkdir(parents=True, exist_ok=True)
        (self.attempt_root / "logs/gradle-app.log").write_text("task executed\n", encoding="utf-8")
        (self.attempt_root / "logs/logcat.txt").write_text("logcat\n", encoding="utf-8")
        self._write_json(self.attempt_root / "raw/commands.json", completion["commands"])
        if lane_policy["device"]["kind"] == "gmd":
            for index, task in enumerate(lane_policy["gradleTasks"]):
                source_path = self.attempt_root / f"collected/{lane_policy['resultRoots'][index * 3]}/device-evidence-device.json"
                self._write_json(
                    source_path,
                    {
                        "schemaVersion": 1,
                        "producer": "androidx-test-storage",
                        "abi": facts["abi"],
                        "apiLevel": facts["apiLevel"],
                        "avdName": facts["serial"],
                        "fingerprint": facts["fingerprint"],
                        "googleServicesRevision": "36" if facts["imageSource"] == "google" else None,
                        "locale": facts["locale"],
                        "permissionControllerPackage": facts["permissionControllerPackage"],
                        "permissionControllerRevision": facts["permissionControllerRevision"],
                    },
                )
                self._write_json(
                    self.attempt_root / f"raw/gmd-task-{index}.json",
                    {
                        "schemaVersion": 1,
                        "producer": "gasstation-gmd-observation",
                        "deviceSource": {
                            "path": source_path.relative_to(self.attempt_root).as_posix(),
                            "sha256": sha256_file(source_path),
                        },
                        "teardown": {"status": "SUCCESS", "timedOut": False},
                    },
                )
            self._write_json(
                self.attempt_root / "raw/gmd-teardown.json",
                {
                    "schemaVersion": 1,
                    "kind": "gmd",
                    "timedOut": False,
                    "baselinePids": [],
                    "observedPids": [],
                    "killedPids": [],
                    "killFailures": [],
                    "livePids": [],
                    "adbExitCode": 0,
                    "adbTargets": [],
                },
            )
        else:
            (self.attempt_root / "raw/adb-devices.txt").write_text(
                "List of devices attached\nemulator-5554 device product:sdk\n", encoding="utf-8"
            )
            (self.attempt_root / "raw/getprop.txt").write_text(
                "[ro.build.version.sdk]: [24]\n"
                "[ro.build.fingerprint]: [fixture/24]\n"
                "[ro.product.cpu.abi]: [x86_64]\n"
                "[persist.sys.locale]: [ko-KR]\n",
                encoding="utf-8",
            )
            (self.attempt_root / "raw/avd-config.ini").write_text(
                "hw.device.name = pixel_2\n"
                "image.sysdir.1 = system-images/android-24/google_apis/x86_64/\n",
                encoding="utf-8",
            )
            (self.attempt_root / "raw/permission-controller-package.txt").write_text(
                "com.android.packageinstaller\n", encoding="utf-8"
            )
            (self.attempt_root / "raw/permission-controller-revision.txt").write_text("24\n", encoding="utf-8")
            self._write_json(
                self.attempt_root / "raw/teardown.json",
                {
                    "schemaVersion": 1,
                    "kind": "connected-avd",
                    "timedOut": False,
                    "logcatStopExitCode": 0,
                    "emulatorKillExitCode": 0,
                    "emulatorPidAlive": False,
                    "serialPresent": False,
                    "portsFree": True,
                    "avdRemoved": True,
                },
            )
        (self.attempt_root / "apks").mkdir(parents=True, exist_ok=True)
        (self.attempt_root / "apks/app.apk").write_bytes(b"app-apk")
        (self.attempt_root / "apks/test.apk").write_bytes(b"test-apk")

        if file_mutator:
            file_mutator(self.attempt_root)

        failure_artifacts = []
        if include_failure_artifacts:
            for identity in junit["failed"] | junit["errors"]:
                class_name, method_name = identity.split("#", 1)
                safe_class = "".join(character if character.isalnum() or character in "_-" else "_" for character in class_name)
                safe_method = "".join(character if character.isalnum() or character in "_-" else "_" for character in method_name)
                stem = f"failure-fixture-run-1-{safe_class}-{safe_method}-api{facts['apiLevel']}"
                (self.attempt_root / f"results/{stem}.png").write_bytes(b"fixture-png")
                self._write_json(
                    self.attempt_root / f"results/{stem}.txt",
                    {
                        "apiLevel": facts["apiLevel"],
                        "attemptId": "fixture-run-1",
                        "className": class_name,
                        "methodName": method_name,
                        "permissionSelection": None,
                    },
                )
                failure_artifacts.extend(
                    (
                        (f"results/{stem}.png", "failure-png"),
                        (f"results/{stem}.txt", "failure-diagnostic"),
                    )
                )

        for relative, kind in (
            ("results/app.xml", "junit"),
            ("raw/device-metadata.json", "device-metadata"),
            ("reports/app/index.html", "html"),
            ("logs/gradle-app.log", "gradle-log"),
            ("logs/logcat.txt", "logcat"),
            ("raw/commands.json", "command-receipt"),
            ("apks/app.apk", "app-apk"),
            ("apks/test.apk", "test-apk"),
            *failure_artifacts,
        ):
            path = self.attempt_root / relative
            if path.exists() or path.is_symlink():
                completion["artifacts"].append(
                    {"path": relative, "kind": kind, "sha256": sha256_file(path)}
                )
        for path in sorted((self.attempt_root / "raw").glob("*")):
            relative = path.relative_to(self.attempt_root).as_posix()
            if relative in {entry[0] for entry in (
                ("raw/device-metadata.json", "device-metadata"),
                ("raw/commands.json", "command-receipt"),
            )}:
                continue
            completion["artifacts"].append(
                {"path": relative, "kind": "raw-device", "sha256": sha256_file(path)}
            )
        for path in sorted(self.attempt_root.rglob("device-evidence-device.json")):
            relative = path.relative_to(self.attempt_root).as_posix()
            completion["artifacts"].append(
                {"path": relative, "kind": "raw-device", "sha256": sha256_file(path)}
            )
        self._write_json(self.attempt_root / "completion.json", completion)
        return verify_attempt(self.policy_path, self.attempt_root, today=today)

    def _write_junit(self, path, junit):
        path.parent.mkdir(parents=True, exist_ok=True)
        cases = []
        for identity in junit["tests"]:
            class_name, method = identity.split("#", 1)
            if identity in junit["failed"]:
                child = '<failure message="fixture failure" />'
            elif identity in junit["errors"]:
                child = '<error message="fixture error" />'
            elif identity in junit["skipped"]:
                child = "<skipped />"
            else:
                child = ""
            cases.append(f'<testcase classname="{class_name}" name="{method}">{child}</testcase>')
        failures = len(junit["failed"])
        errors = len(junit["errors"])
        skipped = len(junit["skipped"])
        path.write_text(
            f'<testsuite tests="{len(junit["tests"])}" failures="{failures}" errors="{errors}" skipped="{skipped}">' +
            "".join(cases) + "</testsuite>\n",
            encoding="utf-8",
        )

    def _write_json(self, path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(canonical_json_bytes(value))

    def _rewrite_json(self, path, mutator):
        value = json.loads(path.read_text(encoding="utf-8"))
        mutator(value)
        self._write_json(path, value)

    def _reset_attempt_root(self):
        for child in sorted(self.attempt_root.rglob("*"), reverse=True):
            if child.is_symlink() or child.is_file():
                child.unlink()
            else:
                child.rmdir()


if __name__ == "__main__":
    unittest.main()
