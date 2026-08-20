import tempfile
import shutil
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from device_workflow import check_device_contracts


ROOT = Path(__file__).resolve().parents[3]


class DeviceWorkflowContractTest(unittest.TestCase):
    def test_checked_in_workflow_matches_closed_device_contract(self):
        self.assertEqual([], check_device_contracts(ROOT))

    def test_required_workflow_mutations_fail_closed(self):
        workflow = (ROOT / ".github/workflows/device-evidence.yml").read_text(encoding="utf-8")
        mutations = {
            "blocking-pr": workflow.replace("    continue-on-error: true\n", "", 1),
            "allowed-scheduled": workflow.replace(
                "  device-scheduled-api24:\n",
                "  device-scheduled-api24:\n    continue-on-error: true\n",
                1,
            ),
            "cancel": workflow.replace("cancel-in-progress: false", "cancel-in-progress: true"),
            "cancel-comment-decoy": workflow.replace("  cancel-in-progress: false\n", "", 1) + "\n#   cancel-in-progress: false\n",
            "wrong-timeout": workflow.replace("    timeout-minutes: 55", "    timeout-minutes: 54", 1),
            "missing-filter-lane": workflow.replace("--lane api28-pr-smoke", "--lane api28-scheduled", 1),
            "missing-kvm": workflow.replace("          test -w /dev/kvm\n", "", 1),
            "echoed-kvm": workflow.replace("          test -c /dev/kvm", "          echo 'test -c /dev/kvm'", 1),
            "bad-upload": workflow.replace("          if-no-files-found: error", "          if-no-files-found: warn", 1),
            "missing-digest": workflow.replace("steps.upload.outputs.artifact-digest", "steps.upload.outputs.unknown", 1),
            "wrong-step-timeout": workflow.replace("        timeout-minutes: 34", "        timeout-minutes: 33", 1),
            "missing-setup-timeout": workflow.replace("        timeout-minutes: 2\n", "", 1),
            "missing-upload-timeout": workflow.replace(
                "        timeout-minutes: 3\n        uses: actions/upload-artifact@v7",
                "        uses: actions/upload-artifact@v7",
                1,
            ),
            "missing-summary-timeout": workflow.replace(
                "      - name: Record uploaded artifact identity\n        if: always()\n        timeout-minutes: 1\n",
                "      - name: Record uploaded artifact identity\n        if: always()\n",
                1,
            ),
            "retry-pipe": workflow.replace(
                "run: scripts/quality/device/run_gmd_lane.sh --lane api28-pr-smoke",
                "run: scripts/quality/device/run_gmd_lane.sh --lane api28-pr-smoke || true",
                1,
            ),
            "step-error-suppression": workflow.replace(
                "      - name: Run canonical report-only API 28 smoke\n",
                "      - name: Run canonical report-only API 28 smoke\n        continue-on-error: true\n",
                1,
            ),
        }
        for name, mutated in mutations.items():
            with self.subTest(name=name):
                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    (root / ".github/workflows").mkdir(parents=True)
                    (root / ".github/workflows/device-evidence.yml").write_text(mutated, encoding="utf-8")
                    (root / ".github/workflows/android.yml").write_text(
                        (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8"),
                        encoding="utf-8",
                    )
                    (root / "config/quality").mkdir(parents=True)
                    for name in ("device-evidence-policy.json", "device-evidence-quarantine.json"):
                        (root / f"config/quality/{name}").write_text(
                            (ROOT / f"config/quality/{name}").read_text(encoding="utf-8"),
                            encoding="utf-8",
                        )
                    (root / "gradle.properties").write_text("", encoding="utf-8")
                    self.assertNotEqual([], check_device_contracts(root))

    def test_executable_phase_bound_mutations_fail_closed(self):
        mutations = {
            "missing-provision-bound": ("run_api24_avd.sh", 'run_device_phase "$lane" provision'),
            "missing-boot-bound": ("run_api24_avd.sh", 'run_device_phase "$lane" boot'),
            "missing-command-bound": ("run_gmd_lane.sh", 'run_device_seconds "$seconds"'),
            "missing-collection-bound": ("run_gmd_lane.sh", 'run_device_phase "$lane" collection'),
            "missing-cleanup-bound": ("run_api24_avd.sh", 'run_device_phase "$lane" cleanup'),
            "missing-completion-bound": ("run_gmd_lane.sh", 'run_device_phase "$lane" completion'),
            "missing-verifier-bound": ("run_api24_avd.sh", 'run_device_phase "$lane" verify'),
        }
        for name, (filename, anchor) in mutations.items():
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                (root / ".github/workflows").mkdir(parents=True)
                for workflow in ("device-evidence.yml", "android.yml"):
                    shutil.copyfile(ROOT / f".github/workflows/{workflow}", root / f".github/workflows/{workflow}")
                shutil.copytree(ROOT / "config/quality", root / "config/quality")
                shutil.copytree(ROOT / "scripts/quality/device", root / "scripts/quality/device")
                shutil.copyfile(ROOT / "gradle.properties", root / "gradle.properties")
                path = root / "scripts/quality/device" / filename
                path.write_text(path.read_text(encoding="utf-8").replace(anchor, "unbounded_phase", 1), encoding="utf-8")
                self.assertNotEqual([], check_device_contracts(root))

    def test_timeout_helper_mutations_fail_closed(self):
        mutations = {
            "direct-call": (
                '"$timeout_command" --signal=TERM --kill-after="${grace}s" "$((seconds - grace))s" "$@"',
                '"$@"',
            ),
            "five-minute-kill-grace": ('--kill-after="${grace}s"', '--kill-after="${grace}m"'),
            "undeclared-kill-grace": ('"$((seconds - grace))s"', '"${seconds}s"'),
        }
        for name, (anchor, replacement) in mutations.items():
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                (root / ".github/workflows").mkdir(parents=True)
                for workflow in ("device-evidence.yml", "android.yml"):
                    shutil.copyfile(ROOT / f".github/workflows/{workflow}", root / f".github/workflows/{workflow}")
                shutil.copytree(ROOT / "config/quality", root / "config/quality")
                shutil.copytree(ROOT / "scripts/quality/device", root / "scripts/quality/device")
                shutil.copyfile(ROOT / "gradle.properties", root / "gradle.properties")
                path = root / "scripts/quality/device/common.sh"
                original = path.read_text(encoding="utf-8")
                mutated = original.replace(anchor, replacement, 1)
                self.assertNotEqual(original, mutated)
                path.write_text(mutated, encoding="utf-8")
                self.assertNotEqual([], check_device_contracts(root))


if __name__ == "__main__":
    unittest.main()
