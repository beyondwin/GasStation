import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from device.write_manifest import connected_metadata, derive_cleanup_status, gmd_metadata, safe_segment
from device_evidence import DeviceEvidenceError


ROOT = Path(__file__).resolve().parents[3]
DEVICE = ROOT / "scripts/quality/device"


class DeviceScriptTest(unittest.TestCase):
    def test_wrappers_reject_unknown_lane_before_touching_host(self):
        cases = (
            (DEVICE / "run_gmd_lane.sh", ["--lane", "api24-scheduled"]),
            (DEVICE / "run_api24_avd.sh", ["--lane", "api28-pr-smoke"]),
        )
        for script, arguments in cases:
            with self.subTest(script=script.name):
                result = subprocess.run(
                    ["bash", str(script), *arguments],
                    cwd=ROOT,
                    text=True,
                    capture_output=True,
                    env={"PATH": os.environ["PATH"]},
                )
                self.assertEqual(2, result.returncode)

    def test_reserved_serial_from_fake_adb_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            fake_adb = Path(directory) / "adb"
            fake_adb.write_text(
                "#!/usr/bin/env bash\nprintf 'List of devices attached\\nemulator-5554\\tdevice\\n'\n",
                encoding="utf-8",
            )
            fake_adb.chmod(0o755)
            command = (
                f"source {DEVICE / 'common.sh'}; "
                f"require_free_emulator_5554 {fake_adb}"
            )
            result = subprocess.run(
                ["bash", "-c", command],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertNotEqual(0, result.returncode)
            self.assertIn("already occupied", result.stderr)

    def test_record_command_rejects_cached_outcome_semantically(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            log = root / "gradle.log"
            output = root / "commands.json"
            task = ":app:gasstationPixel2Api28DemoDebugAndroidTest"
            log.write_text(f"> Task {task} FROM-CACHE\n", encoding="utf-8")
            subprocess.run(
                [
                    "python3",
                    str(DEVICE / "record_command.py"),
                    "--output",
                    str(output),
                    "--task",
                    task,
                    "--exit-code",
                    "0",
                    "--log",
                    str(log),
                ],
                check=True,
            )
            self.assertEqual("FROM-CACHE", json.loads(output.read_text(encoding="utf-8"))[0]["outcome"])

    def test_connected_metadata_is_derived_from_raw_adb_and_avd_receipts(self):
        with tempfile.TemporaryDirectory() as directory:
            attempt = Path(directory)
            (attempt / "raw").mkdir()
            (attempt / "raw/adb-devices.txt").write_text(
                "List of devices attached\nemulator-5554 device product:sdk model:sdk\n",
                encoding="utf-8",
            )
            (attempt / "raw/getprop.txt").write_text(
                "[ro.build.version.sdk]: [24]\n"
                "[ro.build.fingerprint]: [fixture/fingerprint]\n"
                "[ro.product.cpu.abi]: [x86_64]\n"
                "[persist.sys.locale]: [ko-KR]\n",
                encoding="utf-8",
            )
            (attempt / "raw/avd-config.ini").write_text(
                "hw.device.name = pixel_2\n"
                "image.sysdir.1 = system-images/android-24/google_apis/x86_64/\n",
                encoding="utf-8",
            )
            (attempt / "raw/permission-controller-package.txt").write_text(
                "com.google.android.packageinstaller\n", encoding="utf-8"
            )
            (attempt / "raw/permission-controller-revision.txt").write_text("341210000\n", encoding="utf-8")
            metadata = connected_metadata(attempt, "api24-scheduled")
            self.assertEqual(24, metadata["apiLevel"])
            self.assertEqual("emulator-5554", metadata["serial"])
            self.assertEqual("x86_64", metadata["abi"])
            self.assertEqual("fixture/fingerprint", metadata["fingerprint"])
            self.assertEqual("ko-KR", metadata["locale"])

            (attempt / "raw/getprop.txt").write_text(
                "[ro.build.version.sdk]: [28]\n"
                "[ro.build.fingerprint]: [fixture/fingerprint]\n"
                "[ro.product.cpu.abi]: [x86_64]\n"
                "[persist.sys.locale]: [ko-KR]\n",
                encoding="utf-8",
            )
            with self.assertRaises(DeviceEvidenceError):
                metadata = connected_metadata(attempt, "api24-scheduled")
                if metadata["apiLevel"] != 24:
                    raise DeviceEvidenceError("wrong API")

    def test_connected_metadata_rejects_extra_or_unhealthy_targets_and_wrong_abi(self):
        mutations = (
            ("emulator-5556 device product:sdk", "x86_64"),
            ("emulator-5556 offline", "x86_64"),
            ("emulator-5556 unauthorized", "x86_64"),
            ("", "arm64-v8a"),
        )
        for extra, abi in mutations:
            with self.subTest(extra=extra, abi=abi), tempfile.TemporaryDirectory() as directory:
                attempt = Path(directory)
                (attempt / "raw").mkdir()
                lines = ["List of devices attached", "emulator-5554 device product:sdk"]
                if extra:
                    lines.append(extra)
                (attempt / "raw/adb-devices.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
                (attempt / "raw/getprop.txt").write_text(
                    "[ro.build.version.sdk]: [24]\n"
                    "[ro.build.fingerprint]: [fixture/fingerprint]\n"
                    f"[ro.product.cpu.abi]: [{abi}]\n"
                    "[persist.sys.locale]: [ko-KR]\n",
                    encoding="utf-8",
                )
                (attempt / "raw/avd-config.ini").write_text(
                    "hw.device.name = pixel_2\n"
                    "image.sysdir.1 = system-images/android-24/google_apis/x86_64/\n",
                    encoding="utf-8",
                )
                (attempt / "raw/permission-controller-package.txt").write_text(
                    "com.google.android.packageinstaller\n", encoding="utf-8"
                )
                (attempt / "raw/permission-controller-revision.txt").write_text(
                    "341210000\n", encoding="utf-8"
                )
                with self.assertRaises(DeviceEvidenceError):
                    connected_metadata(attempt, "api24-scheduled")

    def test_gmd_metadata_requires_one_structured_raw_receipt_per_task(self):
        policy = json.loads((ROOT / "config/quality/device-evidence-policy.json").read_text(encoding="utf-8"))
        lane_name = "api28-scheduled"
        lane = policy["lanes"][lane_name]
        with tempfile.TemporaryDirectory() as directory:
            attempt = Path(directory)
            (attempt / "raw").mkdir()
            for index, task in enumerate(lane["gradleTasks"]):
                receipt = {
                    "schemaVersion": 1,
                    "producer": "agp-utp",
                    "task": task,
                    "device": {
                        "abi": "x86_64",
                        "apiLevel": 28,
                        "fingerprint": "aosp/fixture/api28",
                        "imagePackage": "system-images;android-28;aosp;x86_64",
                        "imageSource": "aosp",
                        "locale": "ko-KR",
                        "permissionControllerPackage": "com.android.packageinstaller",
                        "permissionControllerRevision": "28",
                        "profile": "Pixel 2",
                        "serial": "gasstationPixel2Api28",
                    },
                    "execution": {"shards": 1},
                    "teardown": {"status": "SUCCESS", "timedOut": False},
                }
                (attempt / f"raw/gmd-task-{index}.json").write_text(
                    json.dumps(receipt, sort_keys=True) + "\n", encoding="utf-8"
                )
            metadata = gmd_metadata(attempt, lane_name, lane)
            self.assertEqual("aosp/fixture/api28", metadata["fingerprint"])
            self.assertEqual("x86_64", metadata["abi"])

            missing_field = json.loads((attempt / "raw/gmd-task-1.json").read_text(encoding="utf-8"))
            missing_field["device"].pop("permissionControllerRevision")
            (attempt / "raw/gmd-task-1.json").write_text(
                json.dumps(missing_field, sort_keys=True) + "\n", encoding="utf-8"
            )
            with self.assertRaises(DeviceEvidenceError):
                gmd_metadata(attempt, lane_name, lane)

            (attempt / "raw/gmd-task-1.json").write_text(
                (attempt / "raw/gmd-task-0.json").read_text(encoding="utf-8").replace(
                    lane["gradleTasks"][0], lane["gradleTasks"][1]
                ),
                encoding="utf-8",
            )
            conflicting = json.loads((attempt / "raw/gmd-task-1.json").read_text(encoding="utf-8"))
            conflicting["device"]["locale"] = "en-US"
            (attempt / "raw/gmd-task-1.json").write_text(
                json.dumps(conflicting, sort_keys=True) + "\n", encoding="utf-8"
            )
            with self.assertRaises(DeviceEvidenceError):
                gmd_metadata(attempt, lane_name, lane)

            (attempt / "raw/gmd-task-2.json").write_text(
                (attempt / "raw/gmd-task-0.json").read_text(encoding="utf-8"), encoding="utf-8"
            )
            with self.assertRaises(DeviceEvidenceError):
                gmd_metadata(attempt, lane_name, lane)
            (attempt / "raw/gmd-task-2.json").unlink()

            (attempt / "raw/gmd-task-1.json").unlink()
            (attempt / "logs").mkdir()
            (attempt / "logs/gradle-0.log").write_text(
                "api28-scheduled Pixel 2 28 aosp managed device UTP\n", encoding="utf-8"
            )
            with self.assertRaises(DeviceEvidenceError):
                gmd_metadata(attempt, lane_name, lane)

    def test_cleanup_status_is_derived_from_raw_teardown_observations(self):
        base = {
            "schemaVersion": 1,
            "kind": "connected-avd",
            "timedOut": False,
            "logcatStopExitCode": 0,
            "emulatorKillExitCode": 0,
            "emulatorPidAlive": False,
            "serialPresent": False,
            "portsFree": True,
            "avdRemoved": True,
        }
        mutations = {
            "kill-failure": {"emulatorKillExitCode": 1},
            "live-pid": {"emulatorPidAlive": True},
            "live-serial": {"serialPresent": True},
            "occupied-port": {"portsFree": False},
            "timeout": {"timedOut": True},
        }
        with tempfile.TemporaryDirectory() as directory:
            attempt = Path(directory)
            (attempt / "raw").mkdir()
            path = attempt / "raw/teardown.json"
            path.write_text(json.dumps(base, sort_keys=True) + "\n", encoding="utf-8")
            self.assertEqual("PASS", derive_cleanup_status(attempt, "connected-avd", []))
            for name, mutation in mutations.items():
                with self.subTest(name=name):
                    value = dict(base)
                    value.update(mutation)
                    path.write_text(json.dumps(value, sort_keys=True) + "\n", encoding="utf-8")
                    self.assertEqual("FAIL", derive_cleanup_status(attempt, "connected-avd", []))
            path.unlink()
            with self.assertRaises(DeviceEvidenceError):
                derive_cleanup_status(attempt, "connected-avd", [])

    def test_gmd_cleanup_rejects_missing_failed_or_timed_out_teardown(self):
        task = ":app:gasstationPixel2Api28DemoDebugAndroidTest"
        base = {
            "schemaVersion": 1,
            "producer": "agp-utp",
            "task": task,
            "device": {
                "abi": "x86_64",
                "apiLevel": 28,
                "fingerprint": "aosp/fixture/api28",
                "imagePackage": "system-images;android-28;aosp;x86_64",
                "imageSource": "aosp",
                "locale": "ko-KR",
                "permissionControllerPackage": "com.android.packageinstaller",
                "permissionControllerRevision": "28",
                "profile": "Pixel 2",
                "serial": "gasstationPixel2Api28",
            },
            "execution": {"shards": 1},
            "teardown": {"status": "SUCCESS", "timedOut": False},
        }
        with tempfile.TemporaryDirectory() as directory:
            attempt = Path(directory)
            (attempt / "raw").mkdir()
            path = attempt / "raw/gmd-task-0.json"
            cleanup_path = attempt / "raw/gmd-teardown.json"
            cleanup_path.write_text(
                json.dumps(
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
                    sort_keys=True,
                ) + "\n",
                encoding="utf-8",
            )
            path.write_text(json.dumps(base, sort_keys=True) + "\n", encoding="utf-8")
            self.assertEqual("PASS", derive_cleanup_status(attempt, "gmd", [task]))
            cleanup = json.loads(cleanup_path.read_text(encoding="utf-8"))
            cleanup.update(observedPids=[4321], killedPids=[4321], livePids=[4321])
            cleanup_path.write_text(json.dumps(cleanup, sort_keys=True) + "\n", encoding="utf-8")
            self.assertEqual("FAIL", derive_cleanup_status(attempt, "gmd", [task]))
            cleanup.update(observedPids=[], killedPids=[], livePids=[])
            cleanup_path.write_text(json.dumps(cleanup, sort_keys=True) + "\n", encoding="utf-8")
            cleanup_path.unlink()
            with self.assertRaises(DeviceEvidenceError):
                derive_cleanup_status(attempt, "gmd", [task])
            cleanup_path.write_text(json.dumps(cleanup, sort_keys=True) + "\n", encoding="utf-8")
            for teardown in (
                {"status": "FAILED", "timedOut": False},
                {"status": "FAILED", "timedOut": True},
            ):
                with self.subTest(teardown=teardown):
                    value = json.loads(json.dumps(base))
                    value["teardown"] = teardown
                    path.write_text(json.dumps(value, sort_keys=True) + "\n", encoding="utf-8")
                    self.assertEqual("FAIL", derive_cleanup_status(attempt, "gmd", [task]))
            value = json.loads(json.dumps(base))
            value["teardown"].pop("timedOut")
            path.write_text(json.dumps(value, sort_keys=True) + "\n", encoding="utf-8")
            with self.assertRaises(DeviceEvidenceError):
                derive_cleanup_status(attempt, "gmd", [task])
            path.unlink()
            with self.assertRaises(DeviceEvidenceError):
                derive_cleanup_status(attempt, "gmd", [task])

    def test_attempt_segment_rejects_traversal_and_absolute_values(self):
        for value in ("../attempt", "/tmp/attempt", "", "a" * 81):
            with self.subTest(value=value):
                with self.assertRaises(DeviceEvidenceError):
                    safe_segment(value, "fixture")

    def test_gmd_wrapper_has_one_invocation_contract_and_no_retry_or_shard(self):
        text = "\n".join(
            (DEVICE / name).read_text(encoding="utf-8")
            for name in ("run_gmd_lane.sh", "execute_gmd_task.sh")
        )
        for anchor in (
            "--no-parallel",
            "--max-workers=1",
            "--rerun-tasks",
            "--configuration-cache",
            "android.testoptions.manageddevices.emulator.gpu=swiftshader_indirect",
        ):
            self.assertIn(anchor, text)
        self.assertNotIn("numManagedDeviceShards", text)
        self.assertNotIn("retry", text.lower())
        self.assertNotIn("|| true", text)
        self.assertNotIn("|| true", (DEVICE / "run_api24_avd.sh").read_text(encoding="utf-8"))

    def test_attempt_cleanup_clears_result_and_apk_roots_before_execution(self):
        common = (DEVICE / "common.sh").read_text(encoding="utf-8")
        self.assertIn('*policy["lanes"][lane]["resultRoots"]', common)
        self.assertIn('*policy["lanes"][lane]["apkRoots"]', common)

    def test_wrappers_open_attempt_before_host_preflight(self):
        for name in ("run_gmd_lane.sh", "run_api24_avd.sh"):
            with self.subTest(name=name):
                text = (DEVICE / name).read_text(encoding="utf-8")
                self.assertLess(text.index("prepare_device_attempt"), text.index("verify_host.sh"))
                self.assertLess(text.index("trap on_exit EXIT"), text.index("verify_host.sh"))

    def test_every_lane_requires_linux_x86_64_kvm(self):
        preflight = (DEVICE / "verify_host.sh").read_text(encoding="utf-8")
        conditional = preflight.index("if [[ $lane == api24-scheduled ]]")
        for anchor in ("[[ $(uname -s) == Linux ]]", "[[ $(uname -m) == x86_64 ]]", "test -c /dev/kvm", "test -r /dev/kvm", "test -w /dev/kvm"):
            self.assertLess(preflight.index(anchor), conditional)

    def test_android_test_storage_and_pr_annotation_contract_is_exact(self):
        app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        versions = (ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8")
        rule = (ROOT / "app/src/androidTest/java/com/gasstation/test/DeviceFailureArtifactRule.kt").read_text(
            encoding="utf-8"
        )
        self.assertIn('androidxTestServices = "1.6.0"', versions)
        self.assertIn('version.ref = "androidxTestServices"', versions)
        self.assertIn('testInstrumentationRunnerArguments["useTestStorageService"] = "true"', app_build)
        self.assertIn("androidTestUtil(libs.androidx.test.services)", app_build)
        self.assertNotIn("additionalTestOutputDir", app_build)
        self.assertNotIn("/sdcard", rule)
        self.assertIn("writeToTestStorage", rule)
        self.assertIn("PlatformTestStorageRegistry", rule)
        for module in ("core/database", "core/location"):
            build = (ROOT / module / "build.gradle.kts").read_text(encoding="utf-8")
            self.assertIn('testInstrumentationRunnerArguments["useTestStorageService"] = "true"', build)
            self.assertIn("androidTestUtil(libs.androidx.test.services)", build)
            self.assertIn("androidTestUtil(libs.androidx.test.orchestrator)", build)

        permission = (ROOT / "app/src/demoAndroidTest/kotlin/com/gasstation/DemoPermissionFlowTest.kt").read_text(
            encoding="utf-8"
        )
        location = (ROOT / "app/src/demoAndroidTest/kotlin/com/gasstation/DemoLocationHookIntegrationTest.kt").read_text(
            encoding="utf-8"
        )
        portfolio = (ROOT / "app/src/androidTest/java/com/gasstation/StationPortfolioFlowTest.kt").read_text(
            encoding="utf-8"
        )
        self.assertEqual(3, permission.count("@DevicePrSmoke"))
        self.assertEqual(1, location.count("@DevicePrSmoke"))
        self.assertEqual(1, portfolio.count("@DevicePrSmoke"))
        self.assertNotIn("By.res(resourceName)", permission)


if __name__ == "__main__":
    unittest.main()
