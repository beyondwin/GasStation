"""Canonical, redacted evidence for failed Gradle TestKit executions."""

from __future__ import annotations

import base64
import hashlib
import re
import xml.etree.ElementTree as ET
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
from pathlib import Path
from typing import Any, Mapping

from scripts.quality.build_inputs.contracts import (
    BuildInputError,
    canonical_json_bytes,
)


TEXT_LIMIT = 65536
XML_LIMIT = 1024 * 1024
_WORKER = re.compile(r"Gradle Test Executor [1-9][0-9]*")
_FULL_SHA = re.compile(r"[0-9a-f]{64}")
_SENSITIVE_ASSIGNMENT = re.compile(
    r"(?i)\b(token|secret|password|credential|cookie|authorization)(\s*[=:]\s*)([^\s&]+)",
)
_SENSITIVE_TOKEN = re.compile(
    r"(?:github_pat_[A-Za-z0-9_]+|gh[pousr]_[A-Za-z0-9]+|\bBearer\s+\S+|\bsk-[A-Za-z0-9_-]+)",
    re.IGNORECASE,
)
_ABSOLUTE_PATH = re.compile(r"(?<![A-Za-z0-9:/<])/(?:[^\s'\"<>]+)")
_STACK_FRAMES = re.compile(
    r"(?m)(?:^[ \t]*at\s+\S+\([^\r\n]*\)[ \t]*(?:\r?\n|$))+",
)


def validate_live_stage_manifest(stage: Path) -> dict[str, Any]:
    """Rehash the Gradle-finalized live stage before the outer exporter reads it."""
    if not stage.is_dir() or stage.is_symlink():
        raise BuildInputError("TestKit live stage is missing or unsafe")
    manifest_path = stage / "live-stage-manifest.json"
    if not manifest_path.is_file() or manifest_path.is_symlink():
        raise BuildInputError("TestKit live stage manifest is missing or unsafe")
    manifest = _load_canonical_json(manifest_path, context="TestKit live stage manifest")
    artifacts = manifest.get("artifacts")
    if (
        set(manifest) != {"artifacts", "schemaVersion", "status"}
        or manifest.get("schemaVersion") != 1
        or manifest.get("status") != "SEALED"
        or not isinstance(artifacts, list)
    ):
        raise BuildInputError("TestKit live stage manifest schema differs")
    expected_paths = sorted(
        path.name
        for path in stage.iterdir()
        if path.name != manifest_path.name
    )
    if "worker-events.tsv" not in expected_paths or not any(
        re.fullmatch(r"TEST-[0-9a-f]{64}\.xml", name) for name in expected_paths
    ):
        raise BuildInputError("TestKit live stage inventory is incomplete")
    rows: list[dict[str, Any]] = []
    for name in expected_paths:
        path = stage / name
        if path.is_symlink() or not path.is_file() or "/" in name or "\\" in name:
            raise BuildInputError("TestKit live stage contains an unsafe artifact")
        body = path.read_bytes()
        rows.append({"path": name, "sha256": hashlib.sha256(body).hexdigest(), "size": len(body)})
    if artifacts != rows:
        raise BuildInputError("TestKit live stage artifact hash or size differs")
    return manifest


def _write_new(path: Path, body: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() or path.is_symlink():
        raise BuildInputError("TestKit failure evidence output already exists")
    with path.open("xb") as output:
        output.write(body)


def _redact_bounded(value: str, *, limit: int = TEXT_LIMIT) -> tuple[str, bool]:
    text = _SENSITIVE_TOKEN.sub("<redacted-secret>", value)
    text = _SENSITIVE_ASSIGNMENT.sub(r"\1\2<redacted-secret>", text)
    text = _ABSOLUTE_PATH.sub("<redacted-path>", text)
    text = _STACK_FRAMES.sub("<redacted-stack>\n", text)
    encoded = text.encode("utf-8", "replace")
    if len(encoded) <= limit:
        return text, False
    prefix = b"[truncated-prefix]\n"
    bounded = prefix + encoded[-(limit - len(prefix)) :]
    while len(bounded.decode("utf-8", "replace").encode("utf-8")) > limit:
        bounded = prefix + bounded[len(prefix) + 1 :]
    return bounded.decode("utf-8", "replace"), True


def _milliseconds(value: str, *, context: str) -> int:
    try:
        seconds = Decimal(value)
    except InvalidOperation as error:
        raise BuildInputError(f"TestKit {context} timing is malformed") from error
    if not seconds.is_finite() or seconds < 0:
        raise BuildInputError(f"TestKit {context} timing is malformed")
    return int((seconds * 1000).to_integral_value(rounding=ROUND_HALF_UP))


def _duration_seconds(value: str, *, context: str) -> str:
    try:
        seconds = Decimal(value)
    except InvalidOperation as error:
        raise BuildInputError(f"TestKit {context} timing is malformed") from error
    if not seconds.is_finite() or seconds < 0 or re.fullmatch(r"[0-9]+(?:\.[0-9]+)?", value) is None:
        raise BuildInputError(f"TestKit {context} timing is malformed")
    return value


def _integer(value: str | None, *, context: str) -> int:
    if value is None or re.fullmatch(r"[0-9]+", value) is None:
        raise BuildInputError(f"TestKit {context} count is malformed")
    return int(value)


def _decode_field(value: str) -> str:
    if not value or re.fullmatch(r"[A-Za-z0-9_-]+", value) is None:
        raise BuildInputError("TestKit worker trace field is malformed")
    try:
        decoded = base64.urlsafe_b64decode(value + "=" * (-len(value) % 4)).decode("utf-8")
    except (ValueError, UnicodeDecodeError) as error:
        raise BuildInputError("TestKit worker trace field is malformed") from error
    if not decoded or any(character in decoded for character in "\x00\r\n\t"):
        raise BuildInputError("TestKit worker trace field is malformed")
    return decoded


def _decode_payload(value: str) -> str:
    if re.fullmatch(r"[A-Za-z0-9_-]+", value) is None:
        raise BuildInputError("TestKit output trace payload is malformed")
    try:
        decoded = base64.urlsafe_b64decode(value + "=" * (-len(value) % 4)).decode("utf-8")
    except (ValueError, UnicodeDecodeError) as error:
        raise BuildInputError("TestKit output trace payload is malformed") from error
    if not decoded or "\x00" in decoded:
        raise BuildInputError("TestKit output trace payload is malformed")
    return decoded


def _parse_worker_trace(
    path: Path,
) -> tuple[list[dict[str, Any]], dict[tuple[str, str], dict[str, Any]], list[dict[str, Any]]]:
    if path.is_symlink() or not path.is_file():
        raise BuildInputError("TestKit worker trace is missing")
    if path.stat().st_size > 64 * 1024 * 1024:
        raise BuildInputError("TestKit worker trace is unbounded")
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeDecodeError) as error:
        raise BuildInputError("TestKit worker trace is malformed") from error
    if not lines or len(lines) > 16384:
        raise BuildInputError("TestKit worker trace is empty or unbounded")
    tests: dict[tuple[str, str], dict[str, Any]] = {}
    normalized: list[dict[str, Any]] = []
    outputs: list[dict[str, Any]] = []
    for line in lines:
        fields = line.split("\t")
        if len(fields) not in {5, 6} or fields[0] not in {"START", "END", "OUTPUT"}:
            raise BuildInputError("TestKit worker trace record is malformed")
        event, worker_raw, class_raw, name_raw = fields[:4]
        worker = _decode_field(worker_raw)
        class_name = _decode_field(class_raw)
        name = _decode_field(name_raw)
        if _WORKER.fullmatch(worker) is None:
            raise BuildInputError("TestKit worker identity is malformed")
        key = (class_name, name)
        if event == "START":
            if len(fields) != 5 or re.fullmatch(r"[0-9]+", fields[4]) is None or key in tests:
                raise BuildInputError("TestKit worker START record is malformed or duplicate")
            row = {
                "className": class_name,
                "name": name,
                "startedAtMillis": int(fields[4]),
                "worker": worker,
            }
            tests[key] = row
            normalized.append({"event": "START", **row})
        elif event == "END":
            if (
                len(fields) != 6
                or fields[4] not in {"FAILURE", "SKIPPED", "SUCCESS"}
                or re.fullmatch(r"[0-9]+", fields[5]) is None
                or key not in tests
                or "result" in tests[key]
                or tests[key]["worker"] != worker
            ):
                raise BuildInputError("TestKit worker END record is malformed, unmatched, or duplicate")
            tests[key]["result"] = fields[4]
            tests[key]["durationMillis"] = int(fields[5])
            normalized.append(
                {
                    "className": class_name,
                    "durationMillis": int(fields[5]),
                    "event": "END",
                    "name": name,
                    "result": fields[4],
                    "worker": worker,
                },
            )
        else:
            if (
                len(fields) != 6
                or fields[4] not in {"StdErr", "StdOut"}
                or key not in tests
                or "result" in tests[key]
                or tests[key]["worker"] != worker
            ):
                raise BuildInputError("TestKit OUTPUT record is malformed, unmatched, or late")
            outputs.append(
                {
                    "className": class_name,
                    "destination": fields[4],
                    "message": _decode_payload(fields[5]),
                    "name": name,
                    "worker": worker,
                },
            )
    return normalized, tests, outputs


def _artifact(path: str, body: bytes, *, kind: str, owner: str, truncated: bool) -> dict[str, Any]:
    return {
        "kind": kind,
        "owner": owner,
        "path": path,
        "sha256": hashlib.sha256(body).hexdigest(),
        "size": len(body),
        "truncated": truncated,
    }


def _safe_xml_files(results: Path) -> list[Path]:
    if results.is_symlink() or not results.is_dir():
        raise BuildInputError("TestKit JUnit XML directory is missing")
    all_xml = sorted(results.rglob("*.xml"))
    files = sorted(results.glob("TEST-*.xml"))
    if not files:
        raise BuildInputError("TestKit JUnit XML inventory is empty")
    if all_xml != files or len(files) > 512 or any(path.is_symlink() or not path.is_file() for path in files):
        raise BuildInputError("TestKit JUnit XML inventory is unsafe or unbounded")
    return files


def export_testkit_failure_evidence(results: Path, trace: Path, output: Path) -> dict[str, Any]:
    """Seal failed TestKit XML and worker timing before its temp tree is deleted."""

    if output.exists() or output.is_symlink():
        raise BuildInputError("TestKit failure evidence output already exists")
    output.mkdir(parents=True, mode=0o700)
    normalized_events, trace_tests, trace_outputs = _parse_worker_trace(trace)
    artifacts: list[dict[str, Any]] = []
    event_body = b"".join(canonical_json_bytes(row) for row in normalized_events)
    event_path = "workers/events.jsonl"
    _write_new(output / event_path, event_body)
    artifacts.append(
        _artifact(
            event_path,
            event_body,
            kind="worker-events",
            owner="build-logic:convention:test",
            truncated=False,
        ),
    )

    total_duration = Decimal("0")
    totals: dict[str, Any] = {"durationSeconds": "0", "errors": 0, "failures": 0, "skipped": 0, "tests": 0}
    suites: list[dict[str, Any]] = []
    cases: list[dict[str, Any]] = []
    exceptions: list[dict[str, Any]] = []
    test_logs: list[dict[str, Any]] = []
    completed_xml_tests: set[tuple[str, str]] = set()
    for suite_index, xml_path in enumerate(_safe_xml_files(results), 1):
        raw = xml_path.read_bytes()
        if b"<!DOCTYPE" in raw.upper() or b"<!ENTITY" in raw.upper():
            raise BuildInputError("TestKit JUnit XML contains a forbidden DTD or entity")
        try:
            source_root = ET.fromstring(raw)
        except ET.ParseError as error:
            raise BuildInputError("TestKit JUnit XML is malformed") from error
        if source_root.tag != "testsuite":
            raise BuildInputError("TestKit JUnit XML root is not testsuite")
        suite_name = source_root.get("name")
        if not suite_name:
            raise BuildInputError("TestKit JUnit XML suite identity is missing")
        declared = {
            "errors": _integer(source_root.get("errors"), context="suite error"),
            "failures": _integer(source_root.get("failures"), context="suite failure"),
            "skipped": _integer(source_root.get("skipped"), context="suite skipped"),
            "tests": _integer(source_root.get("tests"), context="suite test"),
        }
        duration = _duration_seconds(source_root.get("time", ""), context="suite")
        canonical_root = ET.Element(
            "testsuite",
            {
                "errors": str(declared["errors"]),
                "failures": str(declared["failures"]),
                "name": suite_name,
                "skipped": str(declared["skipped"]),
                "tests": str(declared["tests"]),
                "timeSeconds": duration,
            },
        )
        observed = {"errors": 0, "failures": 0, "skipped": 0, "tests": 0}
        suite_truncated = False
        suite_test_keys: set[tuple[str, str]] = set()
        for testcase in source_root.findall("testcase"):
            class_name = testcase.get("classname")
            name = testcase.get("name")
            if not class_name or not name:
                raise BuildInputError("TestKit JUnit XML testcase identity is missing")
            key = (class_name, name)
            trace_row = trace_tests.get(key)
            if trace_row is None or "result" not in trace_row:
                raise BuildInputError("TestKit JUnit XML testcase has no completed worker trace")
            if key in completed_xml_tests:
                raise BuildInputError("TestKit JUnit XML testcase identity is duplicate")
            completed_xml_tests.add(key)
            suite_test_keys.add(key)
            case_duration = _duration_seconds(testcase.get("time", ""), context="testcase")
            canonical_case = ET.SubElement(
                canonical_root,
                "testcase",
                {"classname": class_name, "name": name, "timeSeconds": case_duration, "worker": trace_row["worker"]},
            )
            outcome = "SUCCESS"
            child_count = 0
            for kind in ("failure", "error", "skipped"):
                for child in testcase.findall(kind):
                    child_count += 1
                    observed[{"error": "errors", "failure": "failures", "skipped": "skipped"}[kind]] += 1
                    outcome = "FAILURE" if kind in {"failure", "error"} else "SKIPPED"
                    message, message_truncated = _redact_bounded(child.get("message", ""))
                    body, body_truncated = _redact_bounded(child.text or "")
                    suite_truncated = suite_truncated or message_truncated or body_truncated
                    exception_type = child.get("type", "")
                    if kind in {"failure", "error"} and not exception_type:
                        raise BuildInputError("TestKit JUnit XML exception type is missing")
                    canonical_child = ET.SubElement(
                        canonical_case,
                        kind,
                        {"message": message, **({"type": exception_type} if exception_type else {})},
                    )
                    canonical_child.text = body
                    if kind in {"failure", "error"}:
                        log_path = f"logs/exception-{len(exceptions) + 1:04d}.log"
                        log_body = (body + ("\n" if body and not body.endswith("\n") else "")).encode("utf-8")
                        _write_new(output / log_path, log_body)
                        artifacts.append(
                            _artifact(
                                log_path,
                                log_body,
                                kind="exception-log",
                                owner=f"{class_name}#{name}",
                                truncated=body_truncated,
                            ),
                        )
                        summary_sha = hashlib.sha256(
                            canonical_json_bytes({"message": message, "text": body}),
                        ).hexdigest()
                        exceptions.append(
                            {
                                "className": class_name,
                                "kind": kind,
                                "logPath": log_path,
                                "logSha256": hashlib.sha256(log_body).hexdigest(),
                                "logSize": len(log_body),
                                "message": message,
                                "name": name,
                                "outcome": outcome,
                                "summarySha256": summary_sha,
                                "type": exception_type,
                                "worker": trace_row["worker"],
                            },
                        )
            if child_count > 1:
                raise BuildInputError("TestKit JUnit XML testcase has multiple outcome nodes")
            if trace_row["result"] != outcome:
                raise BuildInputError("TestKit JUnit XML outcome differs from worker trace")
            cases.append(
                {
                    "className": class_name,
                    "durationSeconds": case_duration,
                    "name": name,
                    "status": outcome,
                    "suite": suite_name,
                    "worker": trace_row["worker"],
                },
            )
            observed["tests"] += 1
        for stream_name in ("system-out", "system-err"):
            stream = source_root.find(stream_name)
            destination = {"system-out": "StdOut", "system-err": "StdErr"}[stream_name]
            expected_stream = "".join(
                row["message"]
                for row in trace_outputs
                if (row["className"], row["name"]) in suite_test_keys and row["destination"] == destination
            )
            actual_stream = (stream.text or "") if stream is not None else ""
            if actual_stream != expected_stream:
                raise BuildInputError("TestKit JUnit XML stream differs from owned output trace")
        if observed != declared:
            raise BuildInputError("TestKit JUnit XML declared counts differ from testcase inventory")
        canonical_xml = ET.tostring(canonical_root, encoding="utf-8", xml_declaration=True) + b"\n"
        if len(canonical_xml) > XML_LIMIT:
            raise BuildInputError("TestKit canonical JUnit XML exceeds the bounded limit")
        sealed_xml_path = f"junit/TEST-{suite_index:04d}.xml"
        _write_new(output / sealed_xml_path, canonical_xml)
        artifacts.append(
            _artifact(
                sealed_xml_path,
                canonical_xml,
                kind="junit-xml",
                owner=suite_name,
                truncated=suite_truncated,
            ),
        )
        suite_row = {"durationSeconds": duration, "name": suite_name, **declared, "xmlPath": sealed_xml_path}
        suites.append(suite_row)
        total_duration += Decimal(duration)
        for count_name in ("errors", "failures", "skipped", "tests"):
            totals[count_name] += suite_row[count_name]

    completed_trace_tests = {key for key, row in trace_tests.items() if "result" in row}
    if completed_xml_tests != completed_trace_tests:
        raise BuildInputError("TestKit completed worker inventory differs from live JUnit inventory")

    for class_name, name, destination in sorted(
        {(row["className"], row["name"], row["destination"]) for row in trace_outputs},
    ):
        key = (class_name, name)
        trace_row = trace_tests[key]
        raw_log = "".join(
            row["message"]
            for row in trace_outputs
            if (row["className"], row["name"], row["destination"]) == (class_name, name, destination)
        )
        log, truncated = _redact_bounded(raw_log)
        log_body = log.encode("utf-8")
        log_path = f"logs/test-output-{len(test_logs) + 1:04d}.log"
        owner = f"{class_name}#{name}"
        _write_new(output / log_path, log_body)
        artifacts.append(
            _artifact(log_path, log_body, kind="test-output-log", owner=owner, truncated=truncated),
        )
        test_logs.append(
            {
                "className": class_name,
                "destination": destination,
                "logPath": log_path,
                "logSha256": hashlib.sha256(log_body).hexdigest(),
                "logSize": len(log_body),
                "name": name,
                "outcome": trace_row.get("result", "INCOMPLETE"),
                "owner": owner,
                "truncated": truncated,
                "worker": trace_row["worker"],
            },
        )

    totals["durationSeconds"] = format(total_duration, "f")

    workers: list[dict[str, Any]] = []
    for worker in sorted({row["worker"] for row in trace_tests.values()}):
        rows = [row for row in trace_tests.values() if row["worker"] == worker]
        completed = [row for row in rows if "result" in row]
        workers.append(
            {
                "completed": len(completed),
                "durationMillis": sum(row["durationMillis"] for row in completed),
                "failed": sum(row["result"] == "FAILURE" for row in completed),
                "started": len(rows),
                "worker": worker,
            },
        )
    incomplete = [
        {"className": key[0], "name": key[1], "startedAtMillis": row["startedAtMillis"], "worker": row["worker"]}
        for key, row in sorted(trace_tests.items())
        if "result" not in row
    ]
    summary = {
        "artifacts": sorted(artifacts, key=lambda row: row["path"]),
        "cases": sorted(cases, key=lambda row: (row["suite"], row["className"], row["name"])),
        "exceptions": exceptions,
        "incompleteTests": incomplete,
        "schemaVersion": 1,
        "status": "FAIL",
        "suites": suites,
        "testLogs": test_logs,
        "totals": totals,
        "workers": workers,
    }
    _write_new(output / "summary.json", canonical_json_bytes(summary))
    validate_testkit_failure_evidence(output)
    return summary


def validate_testkit_failure_evidence(output: Path, *, require_final: bool = False) -> dict[str, Any]:
    if output.is_symlink() or not output.is_dir():
        raise BuildInputError("TestKit failure evidence directory is missing")
    summary_path = output / "summary.json"
    if summary_path.is_symlink() or not summary_path.is_file():
        raise BuildInputError("TestKit failure evidence summary is missing")
    try:
        import json

        summary = json.loads(summary_path.read_bytes())
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise BuildInputError("TestKit failure evidence summary is malformed") from error
    if not isinstance(summary, dict) or canonical_json_bytes(summary) != summary_path.read_bytes():
        raise BuildInputError("TestKit failure evidence summary is noncanonical")
    if set(summary) != {
        "artifacts", "cases", "exceptions", "incompleteTests", "schemaVersion", "status", "suites", "testLogs", "totals", "workers",
    } or summary.get("schemaVersion") != 1 or summary.get("status") != "FAIL":
        raise BuildInputError("TestKit failure evidence summary schema differs")
    artifacts = summary.get("artifacts")
    if not isinstance(artifacts, list) or not artifacts:
        raise BuildInputError("TestKit failure evidence artifact inventory is empty")
    expected_paths = {"summary.json"}
    artifact_rows: dict[str, dict[str, Any]] = {}
    for row in artifacts:
        if not isinstance(row, dict) or set(row) != {"kind", "owner", "path", "sha256", "size", "truncated"}:
            raise BuildInputError("TestKit failure evidence artifact row is malformed")
        relative = Path(row["path"]) if isinstance(row.get("path"), str) else Path()
        if relative.is_absolute() or not relative.parts or ".." in relative.parts or relative.as_posix() in expected_paths:
            raise BuildInputError("TestKit failure evidence artifact path is unsafe or duplicate")
        expected_paths.add(relative.as_posix())
        artifact_rows[relative.as_posix()] = row
        path = output / relative
        if path.is_symlink() or not path.is_file():
            raise BuildInputError("TestKit failure evidence artifact is missing")
        body = path.read_bytes()
        if (
            not isinstance(row.get("size"), int)
            or isinstance(row.get("size"), bool)
            or row["size"] != len(body)
            or _FULL_SHA.fullmatch(str(row.get("sha256"))) is None
            or row["sha256"] != hashlib.sha256(body).hexdigest()
        ):
            raise BuildInputError("TestKit failure evidence artifact hash or size differs")
        if not isinstance(row.get("truncated"), bool):
            raise BuildInputError("TestKit failure evidence truncation marker is malformed")
        decoded = body.decode("utf-8", "replace")
        if row["truncated"] != ("[truncated-prefix]\n" in decoded):
            raise BuildInputError("TestKit failure evidence truncation marker differs from bounded content")
        unredacted_assignment = any(
            match.group(3) != "<redacted-secret>" for match in _SENSITIVE_ASSIGNMENT.finditer(decoded)
        )
        if _SENSITIVE_TOKEN.search(decoded) or unredacted_assignment or _ABSOLUTE_PATH.search(decoded):
            raise BuildInputError("TestKit failure evidence contains an unredacted secret or absolute path")
        if _STACK_FRAMES.search(decoded):
            raise BuildInputError("TestKit failure evidence contains an unredacted stack dump")
    final_path = output / "testkit-failure-summary.json"
    if final_path.is_file() and not final_path.is_symlink():
        expected_paths.add("testkit-failure-summary.json")
    elif require_final:
        raise BuildInputError("final TestKit failure summary is missing")
    actual_paths = {
        path.relative_to(output).as_posix()
        for path in output.rglob("*")
        if path.is_file() and not path.is_symlink()
    }
    if actual_paths != expected_paths or any(path.is_symlink() for path in output.rglob("*")):
        raise BuildInputError("TestKit failure evidence file inventory differs from summary")

    event_rows = [row for row in artifacts if row["kind"] == "worker-events"]
    junit_rows = [row for row in artifacts if row["kind"] == "junit-xml"]
    exception_log_rows = [row for row in artifacts if row["kind"] == "exception-log"]
    test_output_rows = [row for row in artifacts if row["kind"] == "test-output-log"]
    if (
        len(event_rows) != 1
        or event_rows[0]["owner"] != "build-logic:convention:test"
        or not junit_rows
        or len(event_rows) + len(junit_rows) + len(exception_log_rows) + len(test_output_rows) != len(artifacts)
    ):
        raise BuildInputError("TestKit worker or JUnit artifact inventory is incomplete")
    try:
        normalized_events = [
            json.loads(line)
            for line in (output / event_rows[0]["path"]).read_text(encoding="utf-8").splitlines()
        ]
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise BuildInputError("TestKit normalized worker events are malformed") from error
    if not normalized_events:
        raise BuildInputError("TestKit normalized worker events are empty")
    traced: dict[tuple[str, str], dict[str, Any]] = {}
    for event in normalized_events:
        if not isinstance(event, dict) or event.get("event") not in {"START", "END"}:
            raise BuildInputError("TestKit normalized worker event schema differs")
        key = (event.get("className"), event.get("name"))
        worker = event.get("worker")
        if not all(isinstance(value, str) and value for value in (*key, worker)) or _WORKER.fullmatch(worker) is None:
            raise BuildInputError("TestKit normalized worker event identity differs")
        if event["event"] == "START":
            if set(event) != {"className", "event", "name", "startedAtMillis", "worker"} or key in traced:
                raise BuildInputError("TestKit normalized worker START event differs")
            if not isinstance(event.get("startedAtMillis"), int) or isinstance(event.get("startedAtMillis"), bool):
                raise BuildInputError("TestKit normalized worker START timing differs")
            traced[key] = dict(event)
        else:
            if set(event) != {"className", "durationMillis", "event", "name", "result", "worker"}:
                raise BuildInputError("TestKit normalized worker END event differs")
            if (
                key not in traced
                or "result" in traced[key]
                or traced[key]["worker"] != worker
                or event.get("result") not in {"FAILURE", "SKIPPED", "SUCCESS"}
                or not isinstance(event.get("durationMillis"), int)
                or isinstance(event.get("durationMillis"), bool)
                or event["durationMillis"] < 0
            ):
                raise BuildInputError("TestKit normalized worker END relation differs")
            traced[key].update(result=event["result"], durationMillis=event["durationMillis"])
    expected_workers = []
    for worker in sorted({row["worker"] for row in traced.values()}):
        worker_tests = [row for row in traced.values() if row["worker"] == worker]
        completed = [row for row in worker_tests if "result" in row]
        expected_workers.append(
            {
                "completed": len(completed),
                "durationMillis": sum(row["durationMillis"] for row in completed),
                "failed": sum(row["result"] == "FAILURE" for row in completed),
                "started": len(worker_tests),
                "worker": worker,
            },
        )
    if summary.get("workers") != expected_workers:
        raise BuildInputError("TestKit worker summary differs from normalized events")
    expected_incomplete = [
        {"className": key[0], "name": key[1], "startedAtMillis": row["startedAtMillis"], "worker": row["worker"]}
        for key, row in sorted(traced.items())
        if "result" not in row
    ]
    if summary.get("incompleteTests") != expected_incomplete:
        raise BuildInputError("TestKit incomplete-test summary differs from normalized events")

    expected_suites: list[dict[str, Any]] = []
    expected_exceptions: list[dict[str, Any]] = []
    for row in sorted(junit_rows, key=lambda value: value["path"]):
        try:
            root = ET.fromstring((output / row["path"]).read_bytes())
        except ET.ParseError as error:
            raise BuildInputError("TestKit canonical JUnit artifact is malformed") from error
        suite = {
            "durationSeconds": _duration_seconds(root.get("timeSeconds", ""), context="canonical suite"),
            "errors": _integer(root.get("errors"), context="canonical suite error"),
            "failures": _integer(root.get("failures"), context="canonical suite failure"),
            "name": root.get("name"),
            "skipped": _integer(root.get("skipped"), context="canonical suite skipped"),
            "tests": _integer(root.get("tests"), context="canonical suite test"),
            "xmlPath": row["path"],
        }
        if not isinstance(suite["name"], str) or not suite["name"]:
            raise BuildInputError("TestKit canonical suite identity is missing")
        if row["owner"] != suite["name"]:
            raise BuildInputError("TestKit JUnit artifact ownership differs")
        expected_suites.append(suite)
        for testcase in root.findall("testcase"):
            for kind in ("failure", "error"):
                for node in testcase.findall(kind):
                    index = len(expected_exceptions) + 1
                    log_path = f"logs/exception-{index:04d}.log"
                    log_row = artifact_rows.get(log_path)
                    if log_row is None or log_row.get("kind") != "exception-log":
                        raise BuildInputError("TestKit exception summary has a missing nested log")
                    if log_row.get("owner") != f"{testcase.get('classname')}#{testcase.get('name')}":
                        raise BuildInputError("TestKit exception artifact ownership differs")
                    log_body = (output / log_path).read_bytes()
                    message = node.get("message", "")
                    body = node.text or ""
                    expected_exceptions.append(
                        {
                            "className": testcase.get("classname"),
                            "kind": kind,
                            "logPath": log_path,
                            "logSha256": hashlib.sha256(log_body).hexdigest(),
                            "logSize": len(log_body),
                            "message": message,
                            "name": testcase.get("name"),
                            "outcome": "FAILURE",
                            "summarySha256": hashlib.sha256(
                                canonical_json_bytes({"message": message, "text": body}),
                            ).hexdigest(),
                            "type": node.get("type", ""),
                            "worker": testcase.get("worker"),
                        },
                    )
    if summary.get("suites") != expected_suites:
        raise BuildInputError("TestKit suite summary differs from canonical JUnit XML")
    if summary.get("exceptions") != expected_exceptions:
        raise BuildInputError("TestKit exception summary differs from canonical JUnit XML")
    if {row["path"] for row in exception_log_rows} != {row["logPath"] for row in expected_exceptions}:
        raise BuildInputError("TestKit exception artifact ownership inventory differs")
    expected_test_logs: list[dict[str, Any]] = []
    test_log_rows = summary.get("testLogs")
    if not isinstance(test_log_rows, list):
        raise BuildInputError("TestKit output-log summary is malformed")
    for index, log_summary in enumerate(test_log_rows, 1):
        if not isinstance(log_summary, dict) or set(log_summary) != {
            "className", "destination", "logPath", "logSha256", "logSize", "name",
            "outcome", "owner", "truncated", "worker",
        }:
            raise BuildInputError("TestKit output-log summary schema differs")
        key = (log_summary.get("className"), log_summary.get("name"))
        trace_row = traced.get(key)
        path = f"logs/test-output-{index:04d}.log"
        artifact = artifact_rows.get(path)
        body = (output / path).read_bytes() if artifact is not None else b""
        owner = f"{key[0]}#{key[1]}"
        expected_test_logs.append(
            {
                "className": key[0],
                "destination": log_summary.get("destination"),
                "logPath": path,
                "logSha256": hashlib.sha256(body).hexdigest(),
                "logSize": len(body),
                "name": key[1],
                "outcome": trace_row.get("result", "INCOMPLETE") if trace_row else None,
                "owner": owner,
                "truncated": artifact.get("truncated") if artifact else None,
                "worker": trace_row.get("worker") if trace_row else None,
            },
        )
        if (
            artifact is None
            or artifact.get("kind") != "test-output-log"
            or artifact.get("owner") != owner
            or log_summary.get("destination") not in {"StdErr", "StdOut"}
        ):
            raise BuildInputError("TestKit output artifact ownership differs")
    if test_log_rows != expected_test_logs or {row["path"] for row in test_output_rows} != {
        row["logPath"] for row in expected_test_logs
    }:
        raise BuildInputError("TestKit output-log summary differs")
    expected_cases = []
    for row in sorted(junit_rows, key=lambda value: value["path"]):
        root = ET.fromstring((output / row["path"]).read_bytes())
        for testcase in root.findall("testcase"):
            key = (testcase.get("classname"), testcase.get("name"))
            trace_row = traced.get(key)
            status = trace_row.get("result") if trace_row else None
            expected_cases.append(
                {
                    "className": key[0],
                    "durationSeconds": _duration_seconds(
                        testcase.get("timeSeconds", ""),
                        context="canonical testcase",
                    ),
                    "name": key[1],
                    "status": status,
                    "suite": root.get("name"),
                    "worker": testcase.get("worker"),
                },
            )
    expected_cases.sort(key=lambda row: (row["suite"], row["className"], row["name"]))
    if summary.get("cases") != expected_cases:
        raise BuildInputError("TestKit case summary differs from canonical JUnit XML")
    expected_totals: dict[str, Any] = {
        "durationSeconds": format(sum((Decimal(row["durationSeconds"]) for row in expected_suites), Decimal("0")), "f"),
        "errors": 0,
        "failures": 0,
        "skipped": 0,
        "tests": 0,
    }
    for suite in expected_suites:
        for key in ("errors", "failures", "skipped", "tests"):
            expected_totals[key] += suite[key]
    if summary.get("totals") != expected_totals:
        raise BuildInputError("TestKit totals differ from canonical JUnit XML")
    if final_path.is_file():
        _validate_final_summary(output, summary, final_path)
    return summary


def _load_canonical_json(path: Path, *, context: str) -> dict[str, Any]:
    import json

    try:
        value = json.loads(path.read_bytes())
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise BuildInputError(f"{context} is malformed") from error
    if not isinstance(value, dict) or canonical_json_bytes(value) != path.read_bytes():
        raise BuildInputError(f"{context} is noncanonical")
    return value


def _final_context(output: Path, name: str) -> tuple[dict[str, Any], dict[str, Any], bytes]:
    attempt = output.parents[1]
    marker = _load_canonical_json(attempt / "ownership-marker.json", context="ownership marker")
    marker_body = {key: value for key, value in marker.items() if key != "markerSha256"}
    if marker.get("markerSha256") != hashlib.sha256(canonical_json_bytes(marker_body)).hexdigest():
        raise BuildInputError("ownership marker digest differs")
    result = _load_canonical_json(
        attempt / "command-evidence" / f"{name}.result.json",
        context="governed command result",
    )
    started = _load_canonical_json(
        attempt / "command-evidence" / f"{name}.started.json",
        context="governed command start",
    )
    log = (attempt / "command-evidence" / f"{name}.log").read_bytes()
    if (
        result.get("status") != "FAIL"
        or result.get("exitCode") == 0
        or result.get("commandSha256") != started.get("commandSha256")
        or result.get("logSha256") != hashlib.sha256(log).hexdigest()
        or result.get("logSize") != len(log)
    ):
        raise BuildInputError("governed command failure binding differs")
    return marker, result, log


def _outer_timeout_context(output: Path, name: str, ownership_marker: Mapping[str, Any]) -> dict[str, Any]:
    attempt = output.parents[1]
    marker_path = attempt / "timeout-markers" / f"{name}.json"
    descriptor_path = attempt / "command-evidence" / f"{name}.timeout.json"
    marker = _load_canonical_json(marker_path, context="outer timeout marker")
    descriptor = _load_canonical_json(descriptor_path, context="outer timeout marker receipt")
    marker_body = marker_path.read_bytes()
    if descriptor != {
        "mode": "0600",
        "path": "/evidence-work/task9-local-linux-ownership-marker.json",
        "schemaVersion": 1,
        "sha256": hashlib.sha256(marker_body).hexdigest(),
        "size": len(marker_body),
        "status": "PASS",
    }:
        raise BuildInputError("outer timeout marker receipt binding differs")
    if (
        marker.get("attemptId") != ownership_marker.get("attemptId")
        or marker.get("sourceCommit") != ownership_marker.get("sourceCommit")
        or marker.get("policySha256") != ownership_marker.get("policySha256")
        or marker.get("ownershipMarkerSha256") != ownership_marker.get("markerSha256")
        or marker.get("governedCommand") != name
        or marker.get("outerConventionTestTimeoutMinutes") != 35
        or marker.get("methodLedgerSha256") != "11f019e4ab2f034a6fd3ab27302b5917bb50051bbe365cafb9d76b8bb2cca38b"
        or marker.get("ownerLedgerSha256") != "6e3d0fa1d2c5ecc4824595f989d092161e8225ad9ed9b6d386e262073e50e5ac"
        or marker.get("lanesSha256") != "763bf9c30b2582b8b09a1ee4b5ce25a6234baf8c10d49238083a1e7c56015bd3"
        or marker.get("dispatchSha256") != "94346faebdd4989670c3518513cf0998bcf871c6775d2c8d71687a1200692930"
        or marker.get("taskPath") != ":build-logic:convention:test"
    ):
        raise BuildInputError("outer timeout marker ownership binding differs")
    return {
        "markerEnvironment": "GASSTATION_TASK9_LOCAL_LINUX_OWNERSHIP_MARKER",
        "markerMode": "0600",
        "markerPath": descriptor["path"],
        "markerSha256": descriptor["sha256"],
        "ownershipMarkerSha256": ownership_marker.get("markerSha256"),
        "property": "gasstation.task9LocalLinuxConventionTestTimeoutMinutes",
        "propertyValue": "35",
    }


def finalize_testkit_failure_evidence(output: Path, *, name: str) -> dict[str, Any]:
    if name not in {"metadata-capture-1", "metadata-capture-2"}:
        raise BuildInputError("final TestKit command identity is not closed")
    summary = validate_testkit_failure_evidence(output)
    marker, result, _ = _final_context(output, name)
    outer_timeout = _outer_timeout_context(output, name, marker)
    summary_body = (output / "summary.json").read_bytes()
    final = {
        "attemptId": marker.get("attemptId"),
        "extractSummarySha256": hashlib.sha256(summary_body).hexdigest(),
        "governedCommand": {
            "commandSha256": result["commandSha256"],
            "exitCode": result["exitCode"],
            "logSha256": result["logSha256"],
            "logSize": result["logSize"],
            "name": name,
            "truncated": result["truncated"],
        },
        "markerSha256": marker.get("markerSha256"),
        "policySha256": marker.get("policySha256"),
        "outerTimeout": outer_timeout,
        "schemaVersion": 1,
        "sourceCommit": marker.get("sourceCommit"),
        "status": "FAIL",
        "testContract": {
            "dispatchSha256": "94346faebdd4989670c3518513cf0998bcf871c6775d2c8d71687a1200692930",
            "expectedOwners": 52,
            "expectedTests": 90,
            "lanesSha256": "763bf9c30b2582b8b09a1ee4b5ce25a6234baf8c10d49238083a1e7c56015bd3",
            "maxParallelForks": 5,
            "methodLedgerSha256": "11f019e4ab2f034a6fd3ab27302b5917bb50051bbe365cafb9d76b8bb2cca38b",
            "outerTimeoutSeconds": 2100,
            "ownerLedgerSha256": "6e3d0fa1d2c5ecc4824595f989d092161e8225ad9ed9b6d386e262073e50e5ac",
            "repositoryAndNestedTimeoutSeconds": 1620,
            "retry": False,
            "shard": False,
        },
        "testkit": summary,
    }
    _write_new(output / "testkit-failure-summary.json", canonical_json_bytes(final))
    validate_testkit_failure_evidence(output, require_final=True)
    return final


def _validate_final_summary(output: Path, summary: dict[str, Any], final_path: Path) -> None:
    final = _load_canonical_json(final_path, context="final TestKit failure summary")
    name = final.get("governedCommand", {}).get("name") if isinstance(final.get("governedCommand"), dict) else None
    if name not in {"metadata-capture-1", "metadata-capture-2"}:
        raise BuildInputError("final TestKit failure command identity differs")
    marker, result, _ = _final_context(output, name)
    outer_timeout = _outer_timeout_context(output, name, marker)
    summary_body = (output / "summary.json").read_bytes()
    expected = {
        "attemptId": marker.get("attemptId"),
        "extractSummarySha256": hashlib.sha256(summary_body).hexdigest(),
        "governedCommand": {
            "commandSha256": result["commandSha256"],
            "exitCode": result["exitCode"],
            "logSha256": result["logSha256"],
            "logSize": result["logSize"],
            "name": name,
            "truncated": result["truncated"],
        },
        "markerSha256": marker.get("markerSha256"),
        "policySha256": marker.get("policySha256"),
        "outerTimeout": outer_timeout,
        "schemaVersion": 1,
        "sourceCommit": marker.get("sourceCommit"),
        "status": "FAIL",
        "testContract": {
            "dispatchSha256": "94346faebdd4989670c3518513cf0998bcf871c6775d2c8d71687a1200692930",
            "expectedOwners": 52,
            "expectedTests": 90,
            "lanesSha256": "763bf9c30b2582b8b09a1ee4b5ce25a6234baf8c10d49238083a1e7c56015bd3",
            "maxParallelForks": 5,
            "methodLedgerSha256": "11f019e4ab2f034a6fd3ab27302b5917bb50051bbe365cafb9d76b8bb2cca38b",
            "outerTimeoutSeconds": 2100,
            "ownerLedgerSha256": "6e3d0fa1d2c5ecc4824595f989d092161e8225ad9ed9b6d386e262073e50e5ac",
            "repositoryAndNestedTimeoutSeconds": 1620,
            "retry": False,
            "shard": False,
        },
        "testkit": summary,
    }
    if final != expected:
        raise BuildInputError("final TestKit failure summary binding differs")
