import copy
import json
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from device_evidence import (
    DeviceEvidenceError,
    canonical_json_bytes,
    classify_lane_artifact,
    load_policy,
    load_quarantine,
    instrumentation_receipt_required,
    sha256_file,
    validate_policy,
    verify_attempt,
)


ROOT = Path(__file__).resolve().parents[3]
POLICY = ROOT / "config/quality/device-evidence-policy.json"


class DeviceEvidencePolicyTest(unittest.TestCase):
    def test_closed_instrumentation_identity_allows_api24_app_and_room_without_gmd_receipt(self):
        self.assertFalse(instrumentation_receipt_required("api24-scheduled", "app", 24, "gasstation_api24"))
        self.assertFalse(instrumentation_receipt_required("api24-scheduled", "core:database", 24, "gasstation_api24"))
        self.assertTrue(instrumentation_receipt_required("api28-pr-smoke", "app", 28, "gasstationPixel2Api28"))
        for mutation in (
            ("api24-scheduled", "app", 28, "gasstation_api24"),
            ("api24-scheduled", "app", 24, "gasstationPixel2Api28"),
            ("api24-scheduled", "core:location", 24, "gasstation_api24"),
        ):
            with self.assertRaises(DeviceEvidenceError):
                instrumentation_receipt_required(*mutation)
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
        for relative in (
            "scripts/quality/device/run_api24_avd.sh",
            "scripts/quality/device/run_gmd_lane.sh",
            "scripts/quality/verify_device_evidence.py",
        ):
            destination = self.root / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / relative, destination)
        self.attempt_root = self.root / "attempt"
        self.attempt_root.mkdir()

    def tearDown(self):
        self.temp.cleanup()

    def test_exact_pr_smoke_attempt_passes(self):
        result = self._write_complete_attempt("api28-pr-smoke")

        self.assertEqual("PASS", result["status"])
        self.assertEqual(5, result["counters"]["tests"])
        self.assertEqual(0, result["counters"]["skipped"])

    def test_exact_api24_app_and_room_attempt_passes_without_gmd_receipts(self):
        result = self._write_complete_attempt("api24-scheduled")

        self.assertEqual("PASS", result["status"])
        self.assertEqual(16, result["counters"]["tests"])
        self.assertFalse(any(path.name.startswith("gmd-task-") for path in (self.attempt_root / "raw").iterdir()))

    def test_each_selected_task_requires_own_log_junit_html_apk_and_raw_receipt(self):
        mutators = (
            lambda root: (root / "logs/gradle-1.log").unlink(),
            lambda root: self._junit_path("api28-scheduled", 1).unlink(),
            lambda root: self._html_path("api28-scheduled", 1).unlink(),
            lambda root: self._apk_path("api28-scheduled", 2).unlink(),
            lambda root: (root / "raw/gmd-task-1.json").unlink(),
            lambda root: (root / "raw/gmd-task-1-processes.json").unlink(),
        )
        for mutator in mutators:
            with self.subTest(mutator=mutator):
                self._reset_attempt_root()
                with self.assertRaises(DeviceEvidenceError):
                    self._write_complete_attempt("api28-scheduled", file_mutator=mutator)

    def test_mutations_fail_closed(self):
        mutators = {
            "wrong-api": lambda facts, completion, junit: facts.update(apiLevel=36),
            "wrong-serial": lambda facts, completion, junit: facts.update(serial="untrusted-device"),
            "wrong-profile": lambda facts, completion, junit: facts.update(profile="policy-echo"),
            "wrong-image": lambda facts, completion, junit: facts.update(imagePackage="system-images;android-28;google;x86_64"),
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
                file_mutator=lambda root: self._junit_path("api28-pr-smoke").write_bytes(b"\xff"),
            )

        self._reset_attempt_root()
        with self.assertRaises(DeviceEvidenceError):
            self._write_complete_attempt(
                "api28-pr-smoke",
                file_mutator=lambda root: (root / "logs/gradle-0.log").write_text(
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
        log = self.attempt_root / "logs/gradle-0.log"
        log.write_text("changed after completion\n", encoding="utf-8")
        with self.assertRaises(DeviceEvidenceError):
            verify_attempt(self.policy_path, self.attempt_root)

        self._reset_attempt_root()
        outside = self.root / "outside.xml"
        outside.write_text("outside\n", encoding="utf-8")
        junit_path = self._junit_path("api28-pr-smoke")
        junit_path.parent.mkdir(parents=True)
        os.symlink(outside, junit_path)
        with self.assertRaises(DeviceEvidenceError):
            self._write_complete_attempt("api28-pr-smoke", preserve_junit=True)

    def test_tool_hashes_are_bound_to_current_selected_wrapper_and_verifier(self):
        with self.assertRaises(DeviceEvidenceError):
            self._write_complete_attempt(
                "api28-pr-smoke",
                attempt_mutator=lambda attempt: attempt.update(wrapperSha256="2" * 64),
            )

    def test_empty_and_arbitrary_artifacts_fail_closed(self):
        mutations = (
            lambda root: (root / "logs/gradle-0.log").write_bytes(b""),
            lambda root: self._html_path("api28-pr-smoke").write_bytes(b""),
            lambda root: self._apk_path("api28-pr-smoke", 0).write_bytes(b""),
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                self._reset_attempt_root()
                with self.assertRaises(DeviceEvidenceError):
                    self._write_complete_attempt("api28-pr-smoke", file_mutator=mutation)

        self._reset_attempt_root()
        result = self._write_complete_attempt("api28-pr-smoke")
        completion_path = self.attempt_root / "completion.json"
        completion = json.loads(completion_path.read_text(encoding="utf-8"))
        arbitrary = self.attempt_root / "arbitrary.xml"
        arbitrary.write_text("<testsuite><testcase classname=\"com.gasstation.Fake\" name=\"fake\"/></testsuite>\n", encoding="utf-8")
        completion["artifacts"].append({"path": "arbitrary.xml", "kind": "junit", "sha256": sha256_file(arbitrary)})
        self._write_json(completion_path, completion)
        with self.assertRaises(DeviceEvidenceError):
            verify_attempt(self.policy_path, self.attempt_root)
        self.assertEqual("PASS", result["status"])

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
            "wrapperSha256": sha256_file(
                self.root / "scripts/quality/device" /
                ("run_api24_avd.sh" if lane_policy["device"]["kind"] == "connected-avd" else "run_gmd_lane.sh")
            ),
            "verifierSha256": sha256_file(self.root / "scripts/quality/verify_device_evidence.py"),
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
            remaining = list(junit["tests"])
            for index, inventory_name in enumerate(lane_policy["inventories"]):
                canonical = set(self.policy["inventories"][inventory_name])
                identities = [identity for identity in remaining if identity in canonical]
                if index == 0:
                    identities.extend(identity for identity in remaining if identity not in {
                        item for name in lane_policy["inventories"] for item in self.policy["inventories"][name]
                    })
                task_junit = {
                    "tests": identities,
                    "skipped": junit["skipped"].intersection(identities),
                    "failed": junit["failed"].intersection(identities),
                    "errors": junit["errors"].intersection(identities),
                }
                self._write_junit(self._junit_path(lane, index), task_junit)
        self._write_json(self.attempt_root / "raw/device-metadata.json", facts)
        (self.attempt_root / "logs").mkdir(parents=True, exist_ok=True)
        for index in range(len(lane_policy["gradleTasks"])):
            html_path = self._html_path(lane, index)
            html_path.parent.mkdir(parents=True, exist_ok=True)
            html_path.write_text("<html>fixture</html>\n", encoding="utf-8")
            (self.attempt_root / f"logs/gradle-{index}.log").write_text("task executed\n", encoding="utf-8")
        if lane_policy["device"]["kind"] == "connected-avd":
            (self.attempt_root / "logs/logcat.txt").write_text("logcat\n", encoding="utf-8")
        self._write_json(self.attempt_root / "raw/commands.json", completion["commands"])
        if lane_policy["device"]["kind"] == "gmd":
            self._write_json(
                self.attempt_root / "raw/gmd-baseline-processes.json",
                {"schemaVersion": 1, "processes": []},
            )
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
                        "imagePackage": facts["imagePackage"],
                        "imageSource": facts["imageSource"],
                        "locale": facts["locale"],
                        "permissionControllerPackage": facts["permissionControllerPackage"],
                        "permissionControllerRevision": facts["permissionControllerRevision"],
                        "profile": facts["profile"],
                        "shards": facts["shards"],
                        "task": task,
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
                    self.attempt_root / f"raw/gmd-task-{index}-processes.json",
                    {"schemaVersion": 1, "processes": []},
                )
                (self.attempt_root / f"raw/gmd-task-{index}-adb-devices.txt").write_text(
                    "List of devices attached\n", encoding="utf-8"
                )
            self._write_json(
                self.attempt_root / "raw/gmd-teardown.json",
                {
                    "schemaVersion": 1,
                    "kind": "gmd",
                    "timedOut": False,
                    "baselineProcesses": [],
                    "observedProcesses": [],
                    "killedProcesses": [],
                    "killFailures": [],
                    "liveProcesses": [],
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
                "[ro.boot.qemu.avd_name]: [gasstation_api24]\n"
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
            (self.attempt_root / "raw/cleanup-adb-devices.txt").write_text(
                "List of devices attached\n", encoding="utf-8"
            )
            (self.attempt_root / "raw/disk.txt").write_text("fixture disk\n", encoding="utf-8")
            (self.attempt_root / "raw/meminfo.txt").write_text("fixture meminfo\n", encoding="utf-8")
            (self.attempt_root / "raw/emulator.pid").write_text("4321\n", encoding="utf-8")
            (self.attempt_root / "raw/logcat.pid").write_text("4322\n", encoding="utf-8")
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
        for index in range(len(lane_policy["apkRoots"])):
            apk_path = self._apk_path(lane, index)
            apk_path.parent.mkdir(parents=True, exist_ok=True)
            apk_path.write_bytes(b"PK\x03\x04fixture-apk")

        if file_mutator:
            file_mutator(self.attempt_root)

        failure_artifacts = []
        if include_failure_artifacts:
            for identity in junit["failed"] | junit["errors"]:
                class_name, method_name = identity.split("#", 1)
                safe_class = "".join(character if character.isalnum() or character in "_-" else "_" for character in class_name)
                safe_method = "".join(character if character.isalnum() or character in "_-" else "_" for character in method_name)
                stem = f"failure-fixture-run-1-{safe_class}-{safe_method}-api{facts['apiLevel']}"
                owner = next(
                    index for index, name in enumerate(lane_policy["inventories"])
                    if identity in self.policy["inventories"][name]
                )
                additional_root = lane_policy["resultRoots"][owner * 3 + 1]
                failure_root = self.attempt_root / "collected" / additional_root
                failure_root.mkdir(parents=True, exist_ok=True)
                (failure_root / f"{stem}.png").write_bytes(b"\x89PNG\r\n\x1a\nfixture")
                self._write_json(
                    failure_root / f"{stem}.txt",
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
                        ((failure_root / f"{stem}.png").relative_to(self.attempt_root).as_posix(), "failure-png"),
                        ((failure_root / f"{stem}.txt").relative_to(self.attempt_root).as_posix(), "failure-diagnostic"),
                    )
                )

        for path in sorted(self.attempt_root.rglob("*")):
            if not path.is_file() or path.name in {"attempt.json", "completion.json"}:
                continue
            relative = path.relative_to(self.attempt_root).as_posix()
            kind = classify_lane_artifact(lane_policy, relative)
            self.assertIsNotNone(kind, relative)
            completion["artifacts"].append(
                {"path": relative, "kind": kind, "sha256": sha256_file(path)}
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

    def _junit_path(self, lane, index=0):
        lane_policy = self.policy["lanes"][lane]
        return self.attempt_root / "collected" / lane_policy["resultRoots"][index * 3] / "TEST-fixture.xml"

    def _html_path(self, lane, index=0):
        lane_policy = self.policy["lanes"][lane]
        return self.attempt_root / "collected" / lane_policy["resultRoots"][index * 3 + 2] / "index.html"

    def _apk_path(self, lane, index):
        lane_policy = self.policy["lanes"][lane]
        return self.attempt_root / "collected" / lane_policy["apkRoots"][index] / f"fixture-{index}.apk"

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
