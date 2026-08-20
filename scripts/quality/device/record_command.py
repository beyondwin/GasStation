#!/usr/bin/env python3
"""Append one closed command outcome without hiding cached/skipped execution."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--task", required=True)
    parser.add_argument("--exit-code", type=int, required=True)
    parser.add_argument("--log", type=Path, required=True)
    arguments = parser.parse_args()
    text = arguments.log.read_text(encoding="utf-8", errors="strict")
    terminal = None
    for outcome in ("UP-TO-DATE", "FROM-CACHE", "NO-SOURCE", "SKIPPED"):
        if f"> Task {arguments.task} {outcome}" in text:
            terminal = outcome
            break
    if terminal is None and f"> Task {arguments.task}" in text:
        terminal = "EXECUTED"
    if terminal is None:
        terminal = "MISSING"
    records = []
    if arguments.output.exists():
        records = json.loads(arguments.output.read_text(encoding="utf-8"))
    records.append({"task": arguments.task, "exitCode": arguments.exit_code, "outcome": terminal})
    arguments.output.write_text(
        json.dumps(records, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
