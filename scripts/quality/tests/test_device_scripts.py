import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from device.write_manifest import connected_metadata, safe_segment
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
                "[ro.product.cpu.abi]: [x86_64]\n",
                encoding="utf-8",
            )
            (attempt / "raw/avd-config.ini").write_text(
                "hw.device.name = pixel_2\n"
                "image.sysdir.1 = system-images/android-24/google_apis/x86_64/\n",
                encoding="utf-8",
            )
            metadata = connected_metadata(attempt, "api24-scheduled")
            self.assertEqual(24, metadata["apiLevel"])
            self.assertEqual("emulator-5554", metadata["serial"])

            (attempt / "raw/getprop.txt").write_text(
                "[ro.build.version.sdk]: [28]\n"
                "[ro.build.fingerprint]: [fixture/fingerprint]\n"
                "[ro.product.cpu.abi]: [x86_64]\n",
                encoding="utf-8",
            )
            with self.assertRaises(DeviceEvidenceError):
                metadata = connected_metadata(attempt, "api24-scheduled")
                if metadata["apiLevel"] != 24:
                    raise DeviceEvidenceError("wrong API")

    def test_attempt_segment_rejects_traversal_and_absolute_values(self):
        for value in ("../attempt", "/tmp/attempt", "", "a" * 81):
            with self.subTest(value=value):
                with self.assertRaises(DeviceEvidenceError):
                    safe_segment(value, "fixture")

    def test_gmd_wrapper_has_one_invocation_contract_and_no_retry_or_shard(self):
        text = (DEVICE / "run_gmd_lane.sh").read_text(encoding="utf-8")
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
