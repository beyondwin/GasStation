import json
import os
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from device.write_manifest import connected_metadata, derive_cleanup_status, gmd_metadata, safe_segment
from device_evidence import DeviceEvidenceError, sha256_file


ROOT = Path(__file__).resolve().parents[3]
DEVICE = ROOT / "scripts/quality/device"


class DeviceScriptTest(unittest.TestCase):
    def test_gmd_python_helpers_are_standalone_entrypoints(self):
        for helper in ("gmd_processes.py", "cleanup_gmd_lane.py"):
            with self.subTest(helper=helper):
                result = subprocess.run(
                    [sys.executable, str(DEVICE / helper), "--help"],
                    cwd=ROOT,
                    text=True,
                    capture_output=True,
                    env={"PATH": os.environ["PATH"]},
                )
                self.assertEqual(0, result.returncode, result.stderr)

    def test_process_discovery_is_shared_and_preserves_preexisting_launcher_and_qemu_child(self):
        from device.gmd_processes import introduced_processes, parse_processes

        baseline = parse_processes(
            "101 emulator /sdk/emulator/emulator -avd old\n"
            "102 qemu-system-x86_64 /sdk/emulator/qemu/linux-x86_64/qemu-system-x86_64 -avd old\n"
        )
        observed = parse_processes(
            "101 emulator /sdk/emulator/emulator -avd old\n"
            "102 qemu-system-x86_64 /sdk/emulator/qemu/linux-x86_64/qemu-system-x86_64 -avd old\n"
            "201 emulator /sdk/emulator/emulator -avd task\n"
            "999 unrelated /tmp/emulator-helper\n"
        )

        self.assertEqual(
            [{"pid": 201, "executable": "emulator", "avdName": "task"}],
            introduced_processes(baseline, observed),
        )

    def test_gmd_cleanup_signals_only_task_introduced_stable_process_identity(self):
        from device import cleanup_gmd_lane

        baseline = [
            {"pid": 101, "executable": "emulator", "avdName": "old"},
            {"pid": 102, "executable": "qemu-system-x86_64", "avdName": "old"},
        ]
        owned = {"pid": 201, "executable": "emulator", "avdName": "task"}
        observed = [*baseline, owned]
        with mock.patch.object(cleanup_gmd_lane, "discover_processes", side_effect=[observed, observed, baseline]), \
             mock.patch.object(cleanup_gmd_lane.os, "kill") as kill:
            snapshot, killed, failures, live = cleanup_gmd_lane.terminate_introduced(
                baseline, avd_name="task", wait_seconds=0
            )
        kill.assert_called_once_with(201, cleanup_gmd_lane.signal.SIGTERM)
        self.assertEqual(observed, snapshot)
        self.assertEqual([owned], killed)
        self.assertEqual([], failures)
        self.assertEqual([], live)

    def test_gmd_cleanup_does_not_signal_unrelated_process_started_after_baseline(self):
        from device import cleanup_gmd_lane

        unrelated = {"pid": 901, "executable": "emulator", "avdName": "personal_avd"}
        owned = {"pid": 902, "executable": "emulator", "avdName": "gasstationPixel2Api28"}
        observed = [unrelated, owned]
        with mock.patch.object(
            cleanup_gmd_lane,
            "discover_processes",
            side_effect=[observed, observed, [unrelated]],
        ), mock.patch.object(cleanup_gmd_lane.os, "kill") as kill:
            snapshot, killed, failures, live = cleanup_gmd_lane.terminate_introduced(
                [], avd_name="gasstationPixel2Api28", wait_seconds=0
            )
        kill.assert_called_once_with(902, cleanup_gmd_lane.signal.SIGTERM)
        self.assertEqual(observed, snapshot)
        self.assertEqual([owned], killed)
        self.assertEqual([], failures)
        self.assertEqual([], live)

    def test_gmd_cleanup_cli_with_fake_tools_preserves_post_baseline_other_avd(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            baseline = root / "baseline.json"
            output = root / "teardown.json"
            baseline.write_text('{"processes":[],"schemaVersion":1}\n', encoding="utf-8")
            child = subprocess.Popen(["sleep", "60"])
            try:
                fake_ps = root / "ps"
                fake_ps.write_text(
                    "#!/usr/bin/env bash\n"
                    f"if kill -0 {child.pid} 2>/dev/null; then\n"
                    f"  printf '%s\\n' '{child.pid} emulator /sdk/emulator/emulator -avd personal_avd'\n"
                    "fi\n",
                    encoding="utf-8",
                )
                fake_ps.chmod(0o755)
                fake_adb = root / "adb"
                fake_adb.write_text(
                    "#!/usr/bin/env bash\nprintf 'List of devices attached\\n'\n",
                    encoding="utf-8",
                )
                fake_adb.chmod(0o755)
                result = subprocess.run(
                    [
                        sys.executable,
                        str(DEVICE / "cleanup_gmd_lane.py"),
                        "--baseline-processes",
                        str(baseline),
                        "--lane",
                        "api28-pr-smoke",
                        "--adb",
                        str(fake_adb),
                        "--output",
                        str(output),
                    ],
                    cwd=ROOT,
                    text=True,
                    capture_output=True,
                    env={
                        "PATH": f"{root}{os.pathsep}{os.environ['PATH']}",
                        "PYTHONDONTWRITEBYTECODE": "1",
                    },
                )
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIsNone(child.poll(), "cleanup signalled the unrelated fake emulator")
                receipt = json.loads(output.read_text(encoding="utf-8"))
                self.assertEqual([], receipt["killedProcesses"])
                self.assertEqual("personal_avd", receipt["observedProcesses"][0]["avdName"])
            finally:
                if child.poll() is None:
                    child.terminate()
                child.wait(timeout=5)

    def test_connected_cleanup_removes_validated_avd_without_emulator_pid(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            runner_temp = root / "runner"
            avd_home = runner_temp / "gasstation-device-fixture-1"
            attempt = root / "attempt"
            (attempt / "raw").mkdir(parents=True)
            avd_home.mkdir(parents=True)
            fake_adb = root / "adb"
            fake_adb.write_text("#!/usr/bin/env bash\nprintf 'List of devices attached\\n'\n", encoding="utf-8")
            fake_adb.chmod(0o755)
            result = subprocess.run(
                ["bash", str(DEVICE / "cleanup_connected_avd.sh"), str(attempt), str(fake_adb), str(avd_home), "0", "0"],
                env={"PATH": os.environ["PATH"], "RUNNER_TEMP": str(runner_temp)},
                text=True,
                capture_output=True,
            )
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertFalse(avd_home.exists())
            self.assertTrue((attempt / "raw/teardown.json").is_file())

    def test_manifest_rejects_oversized_raw_json_before_decode(self):
        from device.write_manifest import read_json_object

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "raw.json"
            path.write_bytes(b'{"padding":"' + b"x" * (2 * 1024 * 1024) + b'"}')
            with self.assertRaises(DeviceEvidenceError):
                read_json_object(path, "oversized fixture")

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
                "[ro.boot.qemu.avd_name]: [gasstation_api24]\n"
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
                "[ro.boot.qemu.avd_name]: [gasstation_api24]\n"
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
                    "[ro.boot.qemu.avd_name]: [gasstation_api24]\n"
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
                self._write_gmd_task_receipt(attempt, index, lane)
            metadata = gmd_metadata(attempt, lane_name, lane)
            self.assertEqual("aosp/fixture/api28", metadata["fingerprint"])
            self.assertEqual("x86_64", metadata["abi"])

            source_path = attempt / f"collected/{lane['resultRoots'][3]}/device-evidence-device.json"
            missing_field = json.loads(source_path.read_text(encoding="utf-8"))
            missing_field.pop("permissionControllerRevision")
            source_path.write_text(json.dumps(missing_field, sort_keys=True) + "\n", encoding="utf-8")
            with self.assertRaises(DeviceEvidenceError):
                gmd_metadata(attempt, lane_name, lane)

            self._write_gmd_task_receipt(attempt, 1, lane, locale="en-US")
            with self.assertRaises(DeviceEvidenceError):
                gmd_metadata(attempt, lane_name, lane)

            self._write_gmd_task_receipt(attempt, 1, lane)

            self._write_gmd_task_receipt(attempt, 1, lane, raw_task=lane["gradleTasks"][0])
            with self.assertRaises(DeviceEvidenceError):
                gmd_metadata(attempt, lane_name, lane)

            self._write_gmd_task_receipt(attempt, 1, lane)

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
                        "baselineProcesses": [],
                        "observedProcesses": [],
                        "killedProcesses": [],
                        "killFailures": [],
                        "liveProcesses": [],
                        "adbExitCode": 0,
                        "adbTargets": [],
                    },
                    sort_keys=True,
                ) + "\n",
                encoding="utf-8",
            )
            lane = json.loads((ROOT / "config/quality/device-evidence-policy.json").read_text(encoding="utf-8"))["lanes"]["api28-pr-smoke"]
            self._write_gmd_task_receipt(attempt, 0, lane)
            base = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual("PASS", derive_cleanup_status(attempt, "gmd", [task]))
            cleanup = json.loads(cleanup_path.read_text(encoding="utf-8"))
            process = {"pid": 4321, "executable": "emulator", "avdName": "gasstationPixel2Api28"}
            cleanup.update(observedProcesses=[process], killedProcesses=[process], liveProcesses=[process])
            cleanup_path.write_text(json.dumps(cleanup, sort_keys=True) + "\n", encoding="utf-8")
            self.assertEqual("FAIL", derive_cleanup_status(attempt, "gmd", [task]))
            cleanup.update(observedProcesses=[], killedProcesses=[], liveProcesses=[])
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

    @staticmethod
    def _write_gmd_task_receipt(attempt, index, lane, *, locale="ko-KR", raw_task=None):
        source_path = attempt / f"collected/{lane['resultRoots'][index * 3]}/device-evidence-device.json"
        source_path.parent.mkdir(parents=True, exist_ok=True)
        source = {
            "schemaVersion": 1,
            "producer": "androidx-test-storage",
            "abi": "x86_64",
            "apiLevel": 28,
            "avdName": "gasstationPixel2Api28",
            "fingerprint": "aosp/fixture/api28",
            "googleServicesRevision": None,
            "imagePackage": "system-images;android-28;aosp;x86_64",
            "imageSource": "aosp",
            "locale": locale,
            "permissionControllerPackage": "com.android.packageinstaller",
            "permissionControllerRevision": "28",
            "profile": "Pixel 2",
            "shards": 1,
            "task": raw_task or lane["gradleTasks"][index],
        }
        source_path.write_text(json.dumps(source, sort_keys=True) + "\n", encoding="utf-8")
        receipt = {
            "schemaVersion": 1,
            "producer": "gasstation-gmd-observation",
            "deviceSource": {
                "path": source_path.relative_to(attempt).as_posix(),
                "sha256": sha256_file(source_path),
            },
            "teardown": {"status": "SUCCESS", "timedOut": False},
        }
        receipt_path = attempt / f"raw/gmd-task-{index}.json"
        receipt_path.write_text(json.dumps(receipt, sort_keys=True) + "\n", encoding="utf-8")

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
