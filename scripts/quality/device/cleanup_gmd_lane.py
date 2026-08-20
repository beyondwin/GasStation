#!/usr/bin/env python3
"""Remove only GMD emulator processes absent from the pre-lane baseline."""

from __future__ import annotations

import argparse
import os
import signal
import subprocess
import sys
import time
from pathlib import Path

QUALITY = Path(__file__).resolve().parents[1]
ROOT = QUALITY.parents[1]
sys.path.insert(0, str(QUALITY))

from device_evidence import DeviceEvidenceError, canonical_json_bytes, load_policy  # noqa: E402
from device.gmd_processes import (  # noqa: E402
    discover_processes,
    introduced_processes,
    read_snapshot,
)


def terminate_introduced(
    baseline: list[dict], *, avd_name: str, wait_seconds: float = 20
) -> tuple[list[dict], list[dict], list[dict], list[dict]]:
    observed = discover_processes()
    owned = introduced_processes(baseline, observed, avd_name=avd_name)
    killed: list[dict] = []
    failures: list[dict] = []
    for process in owned:
        current_identities = {
            (item["pid"], item["executable"])
            for item in introduced_processes(baseline, discover_processes(), avd_name=avd_name)
        }
        if (process["pid"], process["executable"]) not in current_identities:
            failures.append(process)
            continue
        try:
            os.kill(process["pid"], signal.SIGTERM)
            killed.append(process)
        except ProcessLookupError:
            failures.append(process)
        except OSError:
            failures.append(process)
    deadline = time.monotonic() + wait_seconds
    live = introduced_processes(baseline, discover_processes(), avd_name=avd_name)
    while live and time.monotonic() < deadline:
        time.sleep(0.5)
        live = introduced_processes(baseline, discover_processes(), avd_name=avd_name)
    owned_identities = {(item["pid"], item["executable"]) for item in owned}
    live = [item for item in live if (item["pid"], item["executable"]) in owned_identities]
    return observed, killed, failures, live


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline-processes", type=Path, required=True)
    parser.add_argument("--lane", required=True)
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    if arguments.output.exists() or arguments.output.is_symlink():
        raise ValueError("GMD cleanup receipt already exists")
    policy = load_policy(ROOT / "config/quality/device-evidence-policy.json")
    if (
        arguments.lane not in policy["lanes"]
        or policy["lanes"][arguments.lane]["device"]["kind"] != "gmd"
    ):
        raise DeviceEvidenceError("cleanup lane is not a canonical GMD lane")
    avd_name = policy["lanes"][arguments.lane]["device"]["name"]
    baseline = read_snapshot(arguments.baseline_processes)
    observed, killed, failures, live = terminate_introduced(baseline, avd_name=avd_name)
    adb = subprocess.run([str(arguments.adb), "devices", "-l"], text=True, capture_output=True, check=False)
    targets = [line.strip() for line in adb.stdout.splitlines()[1:] if line.strip()]
    receipt = {
        "schemaVersion": 1,
        "kind": "gmd",
        "timedOut": False,
        "baselineProcesses": baseline,
        "observedProcesses": observed,
        "killedProcesses": killed,
        "killFailures": failures,
        "liveProcesses": live,
        "adbExitCode": adb.returncode,
        "adbTargets": targets,
    }
    arguments.output.write_bytes(canonical_json_bytes(receipt))
    return 0 if not failures and not live and adb.returncode == 0 and not targets else 1


if __name__ == "__main__":
    raise SystemExit(main())
