#!/usr/bin/env python3
"""Record connected-AVD teardown from final process, adb, port, and path observations."""

from __future__ import annotations

import argparse
import json
import os
import socket
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from device_evidence import read_text  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--adb-devices", type=Path, required=True)
    parser.add_argument("--avd-home", type=Path, required=True)
    parser.add_argument("--emulator-pid", type=int, required=True)
    parser.add_argument("--logcat-stop-exit", type=int, required=True)
    parser.add_argument("--emulator-kill-exit", type=int, required=True)
    arguments = parser.parse_args()
    if arguments.output.exists() or arguments.output.is_symlink():
        raise ValueError("teardown receipt already exists")
    lines = [line.strip() for line in read_text(arguments.adb_devices, name="cleanup adb devices").splitlines()]
    serial_present = any(line.startswith("emulator-5554") for line in lines)
    if arguments.emulator_pid <= 1:
        pid_alive = False
    else:
        try:
            os.kill(arguments.emulator_pid, 0)
            pid_alive = True
        except ProcessLookupError:
            pid_alive = False
    ports_free = True
    for port in (5554, 5555):
        probe = socket.socket()
        try:
            probe.bind(("127.0.0.1", port))
        except OSError:
            ports_free = False
        finally:
            probe.close()
    receipt = {
        "schemaVersion": 1,
        "kind": "connected-avd",
        "timedOut": False,
        "logcatStopExitCode": arguments.logcat_stop_exit,
        "emulatorKillExitCode": arguments.emulator_kill_exit,
        "emulatorPidAlive": pid_alive,
        "serialPresent": serial_present,
        "portsFree": ports_free,
        "avdRemoved": not arguments.avd_home.exists() and not arguments.avd_home.is_symlink(),
    }
    arguments.output.parent.mkdir(parents=True, exist_ok=True)
    arguments.output.write_text(json.dumps(receipt, sort_keys=True, separators=(",", ":")) + "\n", encoding="utf-8")
    return 0 if all(
        (
            receipt["logcatStopExitCode"] == 0,
            receipt["emulatorKillExitCode"] == 0,
            not receipt["emulatorPidAlive"],
            not receipt["serialPresent"],
            receipt["portsFree"],
            receipt["avdRemoved"],
        )
    ) else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, UnicodeDecodeError, ValueError) as error:
        print(f"device teardown error: {error}", file=sys.stderr)
        raise SystemExit(2)
