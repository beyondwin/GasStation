#!/usr/bin/env python3
"""Consolidate device-originated Test Storage facts into one raw task receipt."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

QUALITY = Path(__file__).resolve().parents[1]
ROOT = QUALITY.parents[1]
sys.path.insert(0, str(QUALITY))

from device_evidence import DeviceEvidenceError, canonical_json_bytes, load_policy  # noqa: E402


DEVICE_FIELDS = {
    "abi",
    "apiLevel",
    "fingerprint",
    "imagePackage",
    "imageSource",
    "locale",
    "permissionControllerPackage",
    "permissionControllerRevision",
    "profile",
    "serial",
}


def read_lines(path: Path) -> list[str]:
    if path.is_symlink() or not path.is_file():
        raise DeviceEvidenceError(f"GMD teardown observation missing: {path.name}")
    return [line.strip() for line in path.read_text(encoding="utf-8", errors="strict").splitlines() if line.strip()]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--lane", required=True)
    parser.add_argument("--task-index", type=int, required=True)
    parser.add_argument("--attempt-root", type=Path, required=True)
    parser.add_argument("--exit-code", type=int, required=True)
    parser.add_argument("--baseline-processes", type=Path, required=True)
    parser.add_argument("--final-processes", type=Path, required=True)
    parser.add_argument("--final-adb-devices", type=Path, required=True)
    arguments = parser.parse_args()

    policy = load_policy(ROOT / "config/quality/device-evidence-policy.json")
    if arguments.lane not in policy["lanes"]:
        raise DeviceEvidenceError("unknown GMD lane")
    lane = policy["lanes"][arguments.lane]
    if lane["device"]["kind"] != "gmd" or arguments.task_index not in range(len(lane["gradleTasks"])):
        raise DeviceEvidenceError("GMD task index is outside the selected lane")
    attempt_root = arguments.attempt_root.resolve()
    canonical_attempt_parent = (ROOT / "build/device-evidence").resolve()
    if canonical_attempt_parent not in attempt_root.parents or attempt_root.is_symlink():
        raise DeviceEvidenceError("attempt root is outside the canonical evidence tree")

    task_roots = lane["resultRoots"][arguments.task_index * 3 : arguments.task_index * 3 + 3]
    candidates: list[dict] = []
    for relative in task_roots:
        root = ROOT / relative
        if root.is_symlink() or not root.is_dir():
            continue
        for path in sorted(root.rglob("device-evidence-device.json")):
            if path.is_symlink() or not path.is_file():
                raise DeviceEvidenceError("GMD device receipt is not a regular file")
            try:
                value = json.loads(path.read_text(encoding="utf-8", errors="strict"))
            except (UnicodeDecodeError, json.JSONDecodeError) as error:
                raise DeviceEvidenceError(f"invalid pulled GMD device receipt: {path}") from error
            if not isinstance(value, dict) or set(value) != DEVICE_FIELDS:
                raise DeviceEvidenceError("pulled GMD device receipt fields differ")
            candidates.append(value)
    if not candidates or any(value != candidates[0] for value in candidates[1:]):
        raise DeviceEvidenceError("GMD task requires one consistent device-originated receipt")

    baseline = set(read_lines(arguments.baseline_processes))
    final = set(read_lines(arguments.final_processes))
    adb_lines = read_lines(arguments.final_adb_devices)
    adb_targets = [line for line in adb_lines if line != "List of devices attached"]
    timed_out = arguments.exit_code in {124, 137}
    teardown_passed = not timed_out and not (final - baseline) and not adb_targets
    receipt = {
        "schemaVersion": 1,
        "producer": "agp-utp",
        "task": lane["gradleTasks"][arguments.task_index],
        "device": candidates[0],
        "execution": {"shards": 1},
        "teardown": {"status": "SUCCESS" if teardown_passed else "FAILED", "timedOut": timed_out},
    }
    output = attempt_root / "raw" / f"gmd-task-{arguments.task_index}.json"
    if output.exists() or output.is_symlink():
        raise DeviceEvidenceError("GMD task receipt already exists")
    output.write_bytes(canonical_json_bytes(receipt))
    return 0 if teardown_passed else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except DeviceEvidenceError as error:
        print(f"device evidence error: {error}", file=sys.stderr)
        raise SystemExit(2)
