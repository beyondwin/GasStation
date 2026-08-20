import copy
import json
import os
import tempfile
import unittest
from pathlib import Path

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
            "self-asserted-device": lambda facts, completion, junit: facts.update(source="policy"),
            "cached-task": lambda facts, completion, junit: completion["commands"][0].update(outcome="FROM-CACHE"),
            "nonzero-command": lambda facts, completion, junit: completion["commands"][0].update(exitCode=1),
            "required-skip": lambda facts, completion, junit: junit.update(skipped={junit["tests"][0]}),
            "missing-test": lambda facts, completion, junit: junit["tests"].pop(),
            "extra-test": lambda facts, completion, junit: junit["tests"].append("com.gasstation.UnreviewedTest#unexpected"),
            "duplicate-test": lambda facts, completion, junit: junit["tests"].append(junit["tests"][0]),
            "dirty-post-run": lambda facts, completion, junit: completion.update(postRunStatus=" M source.kt"),
            "cleanup-failure": lambda facts, completion, junit: completion.update(cleanupStatus="FAIL"),
        }

        for name, mutator in mutators.items():
            with self.subTest(name=name):
                self._reset_attempt_root()
                with self.assertRaises(DeviceEvidenceError):
                    self._write_complete_attempt("api28-pr-smoke", mutator=mutator)

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

    def _write_complete_attempt(
        self,
        lane,
        mutator=None,
        preserve_junit=False,
        today="2026-08-20",
    ):
        lane_policy = load_policy(self.policy_path, today=today)["lanes"][lane]
        expected = []
        for inventory in lane_policy["inventories"]:
            expected.extend(load_policy(self.policy_path, today=today)["inventories"][inventory])
        junit = {"tests": expected[:], "skipped": set(), "failed": set()}
        facts = {
            "schemaVersion": 1,
            "source": "agp-utp" if lane_policy["device"]["kind"] == "gmd" else "adb",
            "lane": lane,
            "kind": lane_policy["device"]["kind"],
            "apiLevel": lane_policy["device"]["apiLevel"],
            "profile": lane_policy["device"]["profile"],
            "imageSource": lane_policy["device"]["imageSource"],
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
            "expectedCommands": lane_policy["gradleTasks"],
            "resultRoots": lane_policy["resultRoots"],
            "startedAt": "2026-08-20T00:00:00Z",
            "toolIdentities": {"java": "fixture-java", "gradle": "9.6.1"},
        }
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
        (self.attempt_root / "raw/utp-receipt.txt").write_text("raw device receipt\n", encoding="utf-8")
        (self.attempt_root / "apks").mkdir(parents=True, exist_ok=True)
        (self.attempt_root / "apks/app.apk").write_bytes(b"app-apk")
        (self.attempt_root / "apks/test.apk").write_bytes(b"test-apk")

        for relative, kind in (
            ("results/app.xml", "junit"),
            ("raw/device-metadata.json", "device-metadata"),
            ("reports/app/index.html", "html"),
            ("logs/gradle-app.log", "gradle-log"),
            ("logs/logcat.txt", "logcat"),
            ("raw/commands.json", "command-receipt"),
            ("raw/utp-receipt.txt", "raw-device"),
            ("apks/app.apk", "app-apk"),
            ("apks/test.apk", "test-apk"),
        ):
            path = self.attempt_root / relative
            if path.exists() or path.is_symlink():
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
            elif identity in junit["skipped"]:
                child = "<skipped />"
            else:
                child = ""
            cases.append(f'<testcase classname="{class_name}" name="{method}">{child}</testcase>')
        failures = len(junit["failed"])
        skipped = len(junit["skipped"])
        path.write_text(
            f'<testsuite tests="{len(junit["tests"])}" failures="{failures}" errors="0" skipped="{skipped}">' +
            "".join(cases) + "</testsuite>\n",
            encoding="utf-8",
        )

    def _write_json(self, path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(canonical_json_bytes(value))

    def _reset_attempt_root(self):
        for child in sorted(self.attempt_root.rglob("*"), reverse=True):
            if child.is_symlink() or child.is_file():
                child.unlink()
            else:
                child.rmdir()


if __name__ == "__main__":
    unittest.main()
