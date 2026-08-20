#!/usr/bin/env python3
"""Verify one canonical Android device-evidence attempt."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from device_evidence import DeviceEvidenceError, canonical_json_bytes, verify_attempt


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--policy", type=Path, required=True)
    parser.add_argument("--attempt-root", type=Path, required=True)
    arguments = parser.parse_args()
    output = arguments.attempt_root / "verification.json"
    try:
        result = verify_attempt(arguments.policy, arguments.attempt_root)
    except (DeviceEvidenceError, OSError) as error:
        result = {"schemaVersion": 1, "status": "FAIL", "violations": [str(error)]}
        output.parent.mkdir(parents=True, exist_ok=True)
        temporary = output.with_suffix(".json.tmp")
        temporary.write_bytes(canonical_json_bytes(result))
        temporary.replace(output)
        print(f"device-evidence: FAIL: {error}", file=sys.stderr)
        return 1
    temporary = output.with_suffix(".json.tmp")
    temporary.write_bytes(canonical_json_bytes(result))
    temporary.replace(output)
    print(f"device-evidence: {result['status']}: lane={result['lane']} tests={result['counters']['tests']}")
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
