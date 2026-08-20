#!/usr/bin/env python3
"""Remove only GMD emulator processes absent from the pre-lane baseline."""

from __future__ import annotations

import argparse
import json
import os
import signal
import subprocess
import time
from pathlib import Path


def baseline_pids(path: Path) -> set[int]:
    result = set()
    for line in path.read_text(encoding="utf-8", errors="strict").splitlines():
        first = line.strip().split(maxsplit=1)[0] if line.strip() else ""
        if first.isdigit():
            result.add(int(first))
    return result


def emulator_pids() -> set[int]:
    result = subprocess.run(["ps", "-eo", "pid=,args="], text=True, capture_output=True, check=True)
    pids = set()
    for line in result.stdout.splitlines():
        pieces = line.strip().split(maxsplit=1)
        if len(pieces) == 2 and pieces[0].isdigit() and "/emulator" in pieces[1]:
            pids.add(int(pieces[0]))
    return pids


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline-processes", type=Path, required=True)
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    if arguments.output.exists() or arguments.output.is_symlink():
        raise ValueError("GMD cleanup receipt already exists")
    baseline = baseline_pids(arguments.baseline_processes)
    observed = emulator_pids()
    owned = sorted(observed - baseline)
    failures = []
    for pid in owned:
        try:
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            continue
        except OSError:
            failures.append(pid)
    deadline = time.monotonic() + 20
    live = set(owned)
    while live and time.monotonic() < deadline:
        time.sleep(0.5)
        live = emulator_pids().intersection(owned)
    adb = subprocess.run([str(arguments.adb), "devices", "-l"], text=True, capture_output=True, check=False)
    targets = [line.strip() for line in adb.stdout.splitlines()[1:] if line.strip()]
    receipt = {
        "schemaVersion": 1,
        "kind": "gmd",
        "timedOut": False,
        "baselinePids": sorted(baseline),
        "observedPids": sorted(observed),
        "killedPids": owned,
        "killFailures": sorted(failures),
        "livePids": sorted(live),
        "adbExitCode": adb.returncode,
        "adbTargets": targets,
    }
    arguments.output.write_text(json.dumps(receipt, sort_keys=True, separators=(",", ":")) + "\n", encoding="utf-8")
    return 0 if not failures and not live and adb.returncode == 0 and not targets else 1


if __name__ == "__main__":
    raise SystemExit(main())
