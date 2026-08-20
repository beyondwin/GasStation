#!/usr/bin/env python3
"""Discover reviewed emulator process identities for GMD ownership receipts."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
from pathlib import Path

from device_evidence import DeviceEvidenceError, canonical_json_bytes

MAX_PROCESS_BYTES = 2 * 1024 * 1024
PROCESS_FIELDS = {"pid", "executable"}


def _is_emulator_process(executable: str, arguments: str) -> bool:
    name = Path(executable).name
    if name == "emulator":
        return bool(re.search(r"(?:^|/)emulator(?:\s|$)", arguments))
    return bool(re.fullmatch(r"qemu-system-[A-Za-z0-9_-]+", name)) and "/emulator/qemu/" in arguments


def parse_processes(text: str) -> list[dict]:
    processes: list[dict] = []
    seen: set[int] = set()
    for line in text.splitlines():
        pieces = line.strip().split(maxsplit=2)
        if len(pieces) != 3 or not pieces[0].isdigit():
            continue
        pid = int(pieces[0])
        executable = Path(pieces[1]).name
        if pid <= 1 or pid in seen or not _is_emulator_process(executable, pieces[2]):
            continue
        seen.add(pid)
        processes.append({"pid": pid, "executable": executable})
    return sorted(processes, key=lambda item: (item["pid"], item["executable"]))


def discover_processes() -> list[dict]:
    result = subprocess.run(
        ["ps", "-eo", "pid=,comm=,args="],
        capture_output=True,
        check=True,
    )
    if not result.stdout or len(result.stdout) > MAX_PROCESS_BYTES:
        if not result.stdout:
            return []
        raise DeviceEvidenceError("process discovery output exceeds the evidence byte limit")
    try:
        text = result.stdout.decode("utf-8", errors="strict")
    except UnicodeDecodeError as error:
        raise DeviceEvidenceError("process discovery output is not UTF-8") from error
    return parse_processes(text)


def validate_processes(value: object) -> list[dict]:
    if not isinstance(value, list):
        raise DeviceEvidenceError("process snapshot must be a list")
    result: list[dict] = []
    seen: set[tuple[int, str]] = set()
    for item in value:
        if (
            not isinstance(item, dict)
            or set(item) != PROCESS_FIELDS
            or type(item["pid"]) is not int
            or item["pid"] <= 1
            or not isinstance(item["executable"], str)
            or not item["executable"]
            or "/" in item["executable"]
        ):
            raise DeviceEvidenceError("process snapshot identity is malformed")
        identity = (item["pid"], item["executable"])
        if identity in seen:
            raise DeviceEvidenceError("process snapshot contains duplicate identity")
        seen.add(identity)
        result.append(dict(item))
    if result != sorted(result, key=lambda item: (item["pid"], item["executable"])):
        raise DeviceEvidenceError("process snapshot must be canonically ordered")
    return result


def introduced_processes(baseline: list[dict], observed: list[dict]) -> list[dict]:
    baseline_identities = {(item["pid"], item["executable"]) for item in validate_processes(baseline)}
    return [
        item
        for item in validate_processes(observed)
        if (item["pid"], item["executable"]) not in baseline_identities
    ]


def read_snapshot(path: Path) -> list[dict]:
    if path.is_symlink() or not path.is_file():
        raise DeviceEvidenceError(f"process snapshot missing: {path.name}")
    raw = path.read_bytes()
    if not raw or len(raw) > MAX_PROCESS_BYTES:
        raise DeviceEvidenceError(f"process snapshot size invalid: {path.name}")
    try:
        value = json.loads(raw.decode("utf-8", errors="strict"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise DeviceEvidenceError(f"invalid process snapshot: {path.name}") from error
    if not isinstance(value, dict) or set(value) != {"schemaVersion", "processes"} or value["schemaVersion"] != 1:
        raise DeviceEvidenceError(f"process snapshot schema differs: {path.name}")
    return validate_processes(value["processes"])


def write_snapshot(path: Path, processes: list[dict]) -> None:
    if path.exists() or path.is_symlink():
        raise DeviceEvidenceError(f"process snapshot already exists: {path.name}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(canonical_json_bytes({"schemaVersion": 1, "processes": validate_processes(processes)}))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    write_snapshot(arguments.output, discover_processes())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
