#!/usr/bin/env python3
import json
import subprocess
import sys
from pathlib import Path


def warning(message: str) -> None:
    print(json.dumps({"continue": True, "systemMessage": message[:2400]}))


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except (json.JSONDecodeError, UnicodeDecodeError):
        warning("Invalid Stop hook input; completion checks were skipped.")
        return 0
    if not isinstance(payload, dict):
        warning("Invalid Stop hook input; completion checks were skipped.")
        return 0
    if "stop_hook_active" in payload and not isinstance(
        payload["stop_hook_active"], bool
    ):
        warning("Invalid Stop hook input; completion checks were skipped.")
        return 0
    if payload.get("stop_hook_active") is True:
        print("{}")
        return 0

    root = Path(
        subprocess.check_output(
            ["git", "rev-parse", "--show-toplevel"], text=True
        ).strip()
    )
    check = subprocess.run(
        [str(root / "scripts/agent/check-contracts.sh"), "--quick"],
        text=True,
        capture_output=True,
    )
    dirty = subprocess.check_output(
        ["git", "-C", str(root), "status", "--short"], text=True
    ).strip()

    warnings = []
    if check.returncode:
        warnings.append(check.stderr.strip())
    if dirty:
        warnings.append(
            "Working tree has changes; run scripts/agent/verify.sh auto before "
            "claiming completion."
        )
    if warnings:
        warning("\n".join(warnings))
    else:
        print("{}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
