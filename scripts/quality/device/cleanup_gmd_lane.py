#!/usr/bin/env python3
"""Remove only GMD emulator processes carrying the attempt owner token."""

from __future__ import annotations

import argparse
import os
import re
import signal
import subprocess
import sys
import time
from pathlib import Path

QUALITY = Path(__file__).resolve().parents[1]
ROOT = QUALITY.parents[1]
sys.path.insert(0, str(QUALITY))

from device_evidence import (  # noqa: E402
    DeviceEvidenceError,
    canonical_json_bytes,
    load_policy,
    read_json_value,
)
from device.gmd_processes import (  # noqa: E402
    discover_processes,
    read_snapshot,
    validate_processes,
)


def _identity(process: dict) -> tuple[int, str, str]:
    return process["pid"], process["executable"], process["avdName"]


def terminate_owned(
    *, avd_name: str, owner_token: str, wait_seconds: float = 20
) -> tuple[list[dict], list[dict], list[dict], list[dict]]:
    observed = discover_processes(owner_token=owner_token)
    owned = [process for process in observed if process["avdName"] == avd_name]
    killed: list[dict] = []
    failures: list[dict] = []
    for process in owned:
        current_identities = {
            _identity(item)
            for item in discover_processes(owner_token=owner_token)
            if item["avdName"] == avd_name
        }
        if _identity(process) not in current_identities:
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
    live = discover_processes(owner_token=owner_token)
    while live and time.monotonic() < deadline:
        time.sleep(0.5)
        live = discover_processes(owner_token=owner_token)
    return observed, killed, failures, live


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--attempt-root", type=Path, required=True)
    parser.add_argument("--baseline-processes", type=Path, required=True)
    parser.add_argument("--lane", required=True)
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    if arguments.output.exists() or arguments.output.is_symlink():
        raise ValueError("GMD cleanup receipt already exists")
    attempt_root = arguments.attempt_root.resolve()
    canonical_attempt_parent = (ROOT / "build/device-evidence").resolve()
    if canonical_attempt_parent not in attempt_root.parents or arguments.attempt_root.is_symlink():
        raise DeviceEvidenceError("cleanup attempt root is outside the canonical evidence tree")
    if arguments.baseline_processes.resolve() != attempt_root / "raw/gmd-baseline-processes.json":
        raise DeviceEvidenceError("cleanup baseline path differs")
    if arguments.output.resolve() != attempt_root / "raw/gmd-teardown.json":
        raise DeviceEvidenceError("cleanup output path differs")
    attempt = read_json_value(attempt_root / "attempt.json", name="cleanup attempt receipt")
    if not isinstance(attempt, dict) or not re.fullmatch(
        r"[A-Za-z0-9._-]{1,80}", attempt.get("attemptId", "")
    ):
        raise DeviceEvidenceError("cleanup attempt owner token is malformed")
    owner_token = attempt["attemptId"]
    policy = load_policy(ROOT / "config/quality/device-evidence-policy.json")
    if (
        arguments.lane not in policy["lanes"]
        or policy["lanes"][arguments.lane]["device"]["kind"] != "gmd"
    ):
        raise DeviceEvidenceError("cleanup lane is not a canonical GMD lane")
    avd_name = policy["lanes"][arguments.lane]["device"]["name"]
    baseline = read_snapshot(arguments.baseline_processes)
    observed, killed, failures, live = terminate_owned(
        avd_name=avd_name, owner_token=owner_token
    )
    validate_processes(observed)
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
