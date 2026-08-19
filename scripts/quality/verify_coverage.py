#!/usr/bin/env python3
"""Fail-closed helpers and CLI for GasStation coverage evidence.

The verifier deliberately uses only the Python standard library.  Coverage ratios are
represented as integer counters/basis points so gate decisions never depend on floats.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import unicodedata
import xml.etree.ElementTree as ET
import os
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


class CoverageError(RuntimeError):
    """Coverage input is incomplete, ambiguous, or violates the contract."""


def _normalize(value: Any) -> Any:
    if isinstance(value, str):
        return unicodedata.normalize("NFC", value)
    if isinstance(value, list):
        return [_normalize(item) for item in value]
    if isinstance(value, dict):
        return {_normalize(key): _normalize(item) for key, item in value.items()}
    return value


def canonical_json_bytes(value: Any) -> bytes:
    normalized = _normalize(value)
    encoded = json.dumps(normalized, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    # json.dumps uses short escapes for five control characters.  RFC 8785 permits
    # those, but this repository's byte contract deliberately chooses lowercase
    # hexadecimal escapes for every control character.  Count slash runs so a
    # literal backslash followed by "n" is not confused with a newline escape.
    short = {"b": 8, "t": 9, "n": 10, "f": 12, "r": 13}
    output: list[str] = []
    index = 0
    while index < len(encoded):
        if encoded[index] != "\\":
            output.append(encoded[index])
            index += 1
            continue
        end = index
        while end < len(encoded) and encoded[end] == "\\":
            end += 1
        slash_count = end - index
        output.append("\\" * (slash_count - slash_count % 2))
        if slash_count % 2 and end < len(encoded) and encoded[end] in short:
            output.append(f"\\u{short[encoded[end]]:04x}")
            index = end + 1
        else:
            output.append("\\" * (slash_count % 2))
            index = end
    return "".join(output).encode("utf-8")


def _reject_duplicate_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        normalized = unicodedata.normalize("NFC", key)
        if normalized in result:
            raise CoverageError(f"duplicate JSON key: {normalized}")
        result[normalized] = value
    return result


def read_json(path: Path) -> Any:
    try:
        return _normalize(json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=_reject_duplicate_pairs))
    except CoverageError:
        raise
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise CoverageError(f"invalid JSON {path}: {error}") from error


def require_basis_points(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or not 0 <= value <= 10_000:
        raise CoverageError(f"{label} must be an integer from 0 through 10000 basis points")
    return value


def validate_unit_floor_schema(unit: dict[str, Any], mode: str, branch_total: int) -> None:
    line_key = "lineFloorBasisPoints"
    branch_key = "branchFloorBasisPoints"
    floor_keys = {line_key, branch_key}.intersection(unit)
    if mode == "measurement":
        if floor_keys:
            raise CoverageError("floor keys must be absent in measurement policy")
        return
    if mode != "blocking":
        raise CoverageError(f"unknown enforcement mode: {mode}")
    if line_key not in unit:
        raise CoverageError(f"line floor is required for blocking unit {unit.get('id')}")
    require_basis_points(unit[line_key], f"{unit.get('id')} line floor")
    if branch_total > 0:
        if branch_key not in unit:
            raise CoverageError(f"branch floor is required for executable branches in {unit.get('id')}")
        require_basis_points(unit[branch_key], f"{unit.get('id')} branch floor")
    elif branch_key in unit:
        raise CoverageError(f"branch floor must be absent for branchless unit {unit.get('id')}")


def _strip_comments_and_strings(source: str, suffix: str) -> str:
    out: list[str] = []
    i = 0
    block_depth = 0
    while i < len(source):
        if block_depth:
            if source.startswith("/*", i):
                block_depth += 1
                out.extend("  ")
                i += 2
            elif source.startswith("*/", i):
                block_depth -= 1
                out.extend("  ")
                i += 2
            else:
                out.append("\n" if source[i] == "\n" else " ")
                i += 1
            continue
        if source.startswith("/*", i):
            block_depth = 1
            out.extend("  ")
            i += 2
            continue
        if source.startswith("//", i):
            end = source.find("\n", i)
            end = len(source) if end < 0 else end
            out.extend(" " * (end - i))
            i = end
            continue
        triple = source.startswith('"""', i)
        if triple:
            end = source.find('"""', i + 3)
            if end < 0:
                raise CoverageError("unterminated triple-quoted string")
            segment = source[i : end + 3]
            out.extend("\n" if character == "\n" else " " for character in segment)
            i = end + 3
            continue
        if source[i] in {'"', "'"}:
            quote = source[i]
            start = i
            i += 1
            escaped = False
            while i < len(source):
                character = source[i]
                i += 1
                if escaped:
                    escaped = False
                elif character == "\\":
                    escaped = True
                elif character == quote:
                    break
            else:
                raise CoverageError("unterminated quoted literal")
            segment = source[start:i]
            out.extend("\n" if character == "\n" else " " for character in segment)
            continue
        out.append(source[i])
        i += 1
    if block_depth:
        raise CoverageError("unterminated block comment")
    return "".join(out)


def parse_package_declaration(source: bytes, suffix: str) -> str:
    if suffix not in {".kt", ".java"}:
        raise CoverageError(f"unsupported authored source suffix: {suffix}")
    try:
        decoded = source.decode("utf-8")
    except UnicodeDecodeError as error:
        raise CoverageError("authored source is not UTF-8") from error
    if suffix == ".java" and re.search(r"\\u+[0-9a-fA-F]{4}", decoded):
        raise CoverageError("Java Unicode escape is forbidden before package lexing")
    cleaned = _strip_comments_and_strings(decoded, suffix)
    if suffix == ".java":
        candidates = re.findall(r"(?m)^\s*package\s+([^;\n]+)(;?)", cleaned)
        if candidates and any(semicolon != ";" for _, semicolon in candidates):
            raise CoverageError("Java package declaration requires semicolon")
    else:
        candidates = [(match, "") for match in re.findall(r"(?m)^\s*package\s+([^\s;]+)", cleaned)]
    if len(candidates) != 1:
        raise CoverageError(f"expected exactly one package declaration, found {len(candidates)}")
    package = candidates[0][0].strip()
    identifier = r"[A-Za-z_][A-Za-z0-9_]*"
    if not re.fullmatch(rf"{identifier}(?:\.{identifier})*", package):
        raise CoverageError(f"invalid package declaration: {package}")
    return package


def _class_belongs_to_authored_file(source_path: Path, source: bytes, class_internal_name: str) -> bool:
    cleaned = _strip_comments_and_strings(source.decode("utf-8"), source_path.suffix)
    outer = class_internal_name.rsplit("/", 1)[-1].split("$", 1)[0]
    base = source_path.stem
    allowed = {base, f"{base}Kt"}
    allowed.update(
        re.findall(
            r"\b(?:class|object|interface|enum\s+class|annotation\s+class)\s+([A-Za-z_][A-Za-z0-9_]*)",
            cleaned,
        ),
    )
    if source_path.suffix == ".kt":
        jvm_name = re.search(r'@file:\s*JvmName\s*\(\s*"([A-Za-z_$][A-Za-z0-9_$]*)"\s*\)', source.decode("utf-8"))
        if jvm_name:
            allowed.add(jvm_name.group(1))
    return outer in allowed


@dataclass(frozen=True)
class ParsedJacoco:
    report_id: str
    sources: dict[tuple[str, str], dict[int, tuple[int, int, int, int]]]
    source_classes: dict[tuple[str, str], set[str]]
    semantic_records: tuple[dict[str, Any], ...]
    semantic_sha256: str


def _strict_nonnegative_int(value: str | None, label: str) -> int:
    if value is None or not re.fullmatch(r"0|[1-9][0-9]*", value):
        raise CoverageError(f"{label} must be a canonical non-negative integer")
    return int(value)


def _require_string(value: Any, label: str) -> str:
    if not isinstance(value, str):
        raise CoverageError(f"{label} must be a string")
    return value


def _require_hex_string(value: Any, length: int, label: str, *, nonzero: bool = False) -> str:
    text = _require_string(value, label)
    if not re.fullmatch(rf"[0-9a-f]{{{length}}}", text) or (nonzero and text == "0" * length):
        suffix = "non-zero " if nonzero else ""
        raise CoverageError(f"{label} must be {suffix}{length}-hex")
    return text


def parse_jacoco_xml(xml_bytes: bytes, report_id: str) -> ParsedJacoco:
    try:
        root = ET.fromstring(xml_bytes)
    except ET.ParseError as error:
        raise CoverageError(f"invalid JaCoCo XML for {report_id}: {error}") from error
    if root.tag != "report":
        raise CoverageError(f"JaCoCo XML root must be report for {report_id}")
    report_name = root.get("name")
    if report_name is None:
        raise CoverageError(f"JaCoCo report without name for {report_id}")
    allowed_counters = {"INSTRUCTION", "BRANCH", "LINE", "COMPLEXITY", "METHOD", "CLASS"}
    sources: dict[tuple[str, str], dict[int, tuple[int, int, int, int]]] = {}
    source_classes: dict[tuple[str, str], set[str]] = {}
    records: list[dict[str, Any]] = [
        {"kind": "report-identity", "reportId": report_id, "name": report_name},
    ]
    seen_records: set[bytes] = set()

    def append(record: dict[str, Any]) -> None:
        identity = canonical_json_bytes(record)
        if identity in seen_records:
            raise CoverageError(f"duplicate XML semantic identity in {report_id}: {record}")
        seen_records.add(identity)
        records.append(record)

    def counters(parent: ET.Element, kind: str, fields: dict[str, Any]) -> None:
        seen_types: set[str] = set()
        for counter in parent.findall("counter"):
            counter_type = counter.get("type")
            if counter_type not in allowed_counters:
                raise CoverageError(f"unknown JaCoCo counter type in {report_id}: {counter_type}")
            if counter_type in seen_types:
                raise CoverageError(f"duplicate {kind} counter {counter_type} in {report_id}")
            seen_types.add(counter_type)
            append({
                "kind": f"{kind}-counter",
                **fields,
                "type": counter_type,
                "missed": _strict_nonnegative_int(counter.get("missed"), f"{kind} counter missed"),
                "covered": _strict_nonnegative_int(counter.get("covered"), f"{kind} counter covered"),
            })

    counters(root, "report", {"reportId": report_id})
    for package in root.findall("package"):
        package_name = package.get("name")
        if package_name is None:
            raise CoverageError(f"package without name in {report_id}")
        append({"kind": "package-identity", "reportId": report_id, "package": package_name})
        counters(package, "package", {"reportId": report_id, "package": package_name})
        for class_node in package.findall("class"):
            class_name = class_node.get("name")
            if class_name is None or class_name.rsplit("/", 1)[0] != package_name:
                raise CoverageError(f"class/package mismatch in {report_id}: {class_name}")
            source_name = class_node.get("sourcefilename")
            class_fields = {
                "reportId": report_id,
                "package": package_name,
                "class": class_name,
                "source": source_name,
            }
            append({"kind": "class-identity", **class_fields})
            counters(class_node, "class", class_fields)
            if source_name is not None:
                source_classes.setdefault((package_name, source_name), set()).add(class_name)
            for method in class_node.findall("method"):
                method_name = method.get("name")
                descriptor = method.get("desc")
                if method_name is None or descriptor is None:
                    raise CoverageError(f"method without name/descriptor in {report_id}: {class_name}")
                raw_line = method.get("line")
                declared_line = None if raw_line is None else _strict_nonnegative_int(raw_line, "method line")
                method_fields = {
                    **class_fields,
                    "method": method_name,
                    "descriptor": descriptor,
                    "declaredLine": declared_line,
                }
                append({"kind": "method-identity", **method_fields})
                counters(method, "method", method_fields)
        for source in package.findall("sourcefile"):
            source_name = source.get("name")
            if source_name is None:
                raise CoverageError(f"sourcefile without name in {report_id}")
            identity = (package_name, source_name)
            if identity in sources:
                raise CoverageError(f"duplicate XML identity {package_name}/{source_name}")
            source_fields = {"reportId": report_id, "package": package_name, "source": source_name}
            append({"kind": "source-identity", **source_fields})
            counters(source, "source", source_fields)
            lines: dict[int, tuple[int, int, int, int]] = {}
            for line in source.findall("line"):
                number = _strict_nonnegative_int(line.get("nr"), "line number")
                if number == 0 or number in lines:
                    raise CoverageError(f"duplicate XML identity {package_name}/{source_name}:{number}")
                line_counters = tuple(
                    _strict_nonnegative_int(line.get(attribute), f"{identity}:{number} {attribute}")
                    for attribute in ("mi", "ci", "mb", "cb")
                )
                lines[number] = line_counters  # type: ignore[assignment]
                append({
                    "kind": "source-line",
                    **source_fields,
                    "line": number,
                    "mi": line_counters[0],
                    "ci": line_counters[1],
                    "mb": line_counters[2],
                    "cb": line_counters[3],
                })
            sources[identity] = lines
    ordered = tuple(sorted(records, key=canonical_json_bytes))
    digest = hashlib.sha256(canonical_json_bytes(ordered)).hexdigest()
    return ParsedJacoco(
        report_id=report_id,
        sources=sources,
        source_classes=source_classes,
        semantic_records=ordered,
        semantic_sha256=digest,
    )


def authored_counters(
    parsed: ParsedJacoco,
    authored: set[tuple[str, str]],
) -> tuple[dict[str, dict[str, int]], list[tuple[str, str]]]:
    missing = sorted(authored.difference(parsed.sources))
    if missing:
        raise CoverageError(f"missing authored source in {parsed.report_id}: {missing[0]}")
    line_missed = line_covered = branch_missed = branch_covered = 0
    for identity in sorted(authored):
        for missed, covered, missed_branches, covered_branches in parsed.sources[identity].values():
            if covered > 0:
                line_covered += 1
            elif missed > 0:
                line_missed += 1
            branch_missed += missed_branches
            branch_covered += covered_branches
    counters = {
        "line": {"covered": line_covered, "missed": line_missed, "total": line_covered + line_missed},
        "branch": {
            "covered": branch_covered,
            "missed": branch_missed,
            "total": branch_covered + branch_missed,
        },
    }
    return counters, sorted(set(parsed.sources).difference(authored))


def _safe_relative_file(root: Path, relative: Any, label: str) -> Path:
    if not isinstance(relative, str) or not relative or Path(relative).is_absolute():
        raise CoverageError(f"{label} must be one repository-relative path")
    if "\\" in relative or any(part in {"", ".", ".."} for part in relative.split("/")):
        raise CoverageError(f"{label} has a malformed path: {relative}")
    resolved = (root / relative).resolve()
    try:
        resolved.relative_to(root.resolve())
    except ValueError as error:
        raise CoverageError(f"{label} escapes repository: {relative}") from error
    if not resolved.is_file():
        raise CoverageError(f"{label} is missing: {relative}")
    return resolved


def _safe_relative_location(root: Path, relative: Any, label: str) -> Path:
    if not isinstance(relative, str) or not relative or Path(relative).is_absolute():
        raise CoverageError(f"{label} must be one repository-relative path")
    if "\\" in relative or any(part in {"", ".", ".."} for part in relative.split("/")):
        raise CoverageError(f"{label} has a malformed path: {relative}")
    resolved = (root / relative).resolve()
    try:
        resolved.relative_to(root.resolve())
    except ValueError as error:
        raise CoverageError(f"{label} escapes repository: {relative}") from error
    if not resolved.exists():
        raise CoverageError(f"{label} is missing: {relative}")
    return resolved


def _class_artifact_identity(path: Path, kind: str) -> tuple[int, str]:
    if kind == "file":
        if not path.is_file():
            raise CoverageError(f"input class artifact is not a file: {path}")
        return 1, hashlib.sha256(path.read_bytes()).hexdigest()
    if kind != "directory" or not path.is_dir():
        raise CoverageError(f"input class artifact has invalid kind: {kind}")
    records: list[dict[str, str]] = []
    for child in path.rglob("*"):
        if not child.is_file():
            continue
        resolved = child.resolve()
        try:
            relative = resolved.relative_to(path.resolve()).as_posix()
        except ValueError as error:
            raise CoverageError(f"input class artifact entry escapes directory: {child}") from error
        records.append({"path": relative, "sha256": hashlib.sha256(resolved.read_bytes()).hexdigest()})
    records.sort(key=lambda item: item["path"])
    return len(records), hashlib.sha256(canonical_json_bytes(records)).hexdigest()


def validate_entry_evidence(root: Path, entry: dict[str, Any]) -> ParsedJacoco:
    """Bind every producer-owned raw and semantic identity to current files."""
    report_id = entry.get("reportId", "<unknown>")
    if entry.get("testInputIdentitySha256") != hashlib.sha256(
        canonical_json_bytes(entry.get("testSources", [])),
    ).hexdigest():
        raise CoverageError(f"{report_id} test input semantic hash mismatch")
    execution_paths = entry.get("executionData")
    if not isinstance(execution_paths, list) or len(execution_paths) != 1:
        raise CoverageError(f"{report_id} requires exactly one execution data file")
    execution_file = _safe_relative_file(root, execution_paths[0], f"{report_id} execution data")
    execution_digest = hashlib.sha256(execution_file.read_bytes()).hexdigest()
    if entry.get("executionFileSha256") != execution_digest:
        raise CoverageError(f"{report_id} execution data hash mismatch")

    class_records = entry.get("classes")
    class_count = entry.get("classFileCount")
    if not isinstance(class_records, list) or not class_records or class_count != len(class_records):
        raise CoverageError(f"{report_id} class inventory/count mismatch or zero classes")
    prepared = entry.get("preparedClassDirectory")
    if not isinstance(prepared, str):
        raise CoverageError(f"{report_id} prepared class directory is missing")
    prepared_root = (root / prepared).resolve()
    if not prepared_root.is_dir() or not prepared_root.is_relative_to(root.resolve()):
        raise CoverageError(f"{report_id} prepared class directory is invalid")
    artifacts = entry.get("inputClassArtifacts")
    if not isinstance(artifacts, list) or not artifacts:
        raise CoverageError(f"{report_id} has no provider-owned input class artifact identity")
    for artifact in artifacts:
        if not isinstance(artifact, dict):
            raise CoverageError(f"{report_id} malformed input class artifact identity")
        artifact_path = _safe_relative_location(root, artifact.get("path"), f"{report_id} input class artifact")
        if artifact_path == prepared_root:
            raise CoverageError(f"{report_id} prepared class output cannot be its own provider input")
        count, digest = _class_artifact_identity(artifact_path, artifact.get("kind"))
        if artifact.get("entryCount") != count or artifact.get("sha256") != digest:
            raise CoverageError(f"{report_id} input class artifact identity mismatch: {artifact.get('path')}")
    class_ids: set[str] = set()
    class_names_by_id: dict[str, str] = {}
    class_paths: set[str] = set()
    for record in class_records:
        if not isinstance(record, dict) or set(record) != {"path", "sha256", "jacocoClassId"}:
            raise CoverageError(f"{report_id} malformed class record")
        path = record["path"]
        class_id = record["jacocoClassId"]
        if (
            not isinstance(path, str) or not path.endswith(".class") or path in class_paths
            or not isinstance(class_id, str) or not re.fullmatch(r"[0-9a-f]{16}", class_id)
            or class_id in class_ids
        ):
            raise CoverageError(f"{report_id} duplicate or malformed class identity")
        class_file = _safe_relative_file(root, f"{prepared.rstrip('/')}/{path}", f"{report_id} class")
        if hashlib.sha256(class_file.read_bytes()).hexdigest() != record["sha256"]:
            raise CoverageError(f"{report_id} class hash mismatch: {path}")
        class_paths.add(path)
        class_ids.add(class_id)
        class_names_by_id[class_id] = path[:-len(".class")]
    if [record["path"] for record in class_records] != sorted(class_paths):
        raise CoverageError(f"{report_id} class records are not canonically sorted")
    physical_class_paths = {
        path.relative_to(prepared_root).as_posix()
        for path in prepared_root.rglob("*.class")
        if path.is_file()
    }
    if physical_class_paths != class_paths:
        missing = sorted(class_paths.difference(physical_class_paths))
        extra = sorted(physical_class_paths.difference(class_paths))
        raise CoverageError(
            f"{report_id} physical prepared class inventory differs from manifest: missing={missing} extra={extra}",
        )

    execution_records = entry.get("executionRecords")
    if not isinstance(execution_records, list):
        raise CoverageError(f"{report_id} execution records must be an array")
    execution_keys: set[tuple[int, str]] = set()
    for record in execution_records:
        if not isinstance(record, dict) or set(record) != {"classId", "name", "probes"}:
            raise CoverageError(f"{report_id} malformed execution record")
        class_id = record["classId"]
        name = record["name"]
        probes = record["probes"]
        if (
            not isinstance(class_id, str) or not re.fullmatch(r"[0-9a-f]{16}", class_id)
            or class_id not in class_ids or not isinstance(name, str) or name != class_names_by_id.get(class_id)
            or not isinstance(probes, str) or not re.fullmatch(r"[01]+", probes)
        ):
            raise CoverageError(f"{report_id} invalid project execution record")
        key = (int(class_id, 16), name)
        if key in execution_keys:
            raise CoverageError(f"{report_id} duplicate execution record")
        execution_keys.add(key)
    expected_execution = sorted(execution_records, key=lambda item: (int(item["classId"], 16), item["name"]))
    if execution_records != expected_execution:
        raise CoverageError(f"{report_id} execution records are not canonically sorted")
    ignored = entry.get("ignoredNonProjectExecutionRecordCount")
    if isinstance(ignored, bool) or not isinstance(ignored, int) or ignored < 0:
        raise CoverageError(f"{report_id} invalid ignored execution record count")
    if entry.get("executionSemanticSha256") != hashlib.sha256(
        canonical_json_bytes(execution_records),
    ).hexdigest():
        raise CoverageError(f"{report_id} execution semantic hash mismatch")

    xml_file = _safe_relative_file(root, entry.get("xmlReport"), f"{report_id} XML report")
    xml_bytes = xml_file.read_bytes()
    if entry.get("xmlFileSha256") != hashlib.sha256(xml_bytes).hexdigest():
        raise CoverageError(f"{report_id} XML hash mismatch")
    parsed = parse_jacoco_xml(xml_bytes, report_id)
    xml_classes = {name for names in parsed.source_classes.values() for name in names}
    missing_xml_classes = sorted(xml_classes.difference(class_names_by_id.values()))
    if missing_xml_classes:
        raise CoverageError(f"{report_id} XML class is absent from prepared inventory: {missing_xml_classes[0]}")
    if entry.get("reportSemanticSha256") != parsed.semantic_sha256:
        raise CoverageError(f"{report_id} XML semantic hash mismatch")
    return parsed


def ratio_below_basis_points(covered: int, total: int, threshold: int) -> bool:
    require_basis_points(threshold, "threshold")
    if min(covered, total) < 0 or covered > total or total == 0:
        raise CoverageError("coverage ratio requires 0 <= covered <= total and total > 0")
    return covered * 10_000 < threshold * total


def baseline_drop_exceeded(
    current_covered: int,
    current_total: int,
    baseline_covered: int,
    baseline_total: int,
    maximum_drop_basis_points: int,
) -> bool:
    require_basis_points(maximum_drop_basis_points, "maximum baseline drop")
    if min(current_total, baseline_total) <= 0:
        raise CoverageError("baseline drop comparison requires non-zero totals")
    return (
        baseline_covered * current_total - current_covered * baseline_total
        > maximum_drop_basis_points * current_total * baseline_total // 10_000
    )


def changed_counters(
    lines: dict[int, tuple[int, int, int, int]],
    changed_lines: set[int],
) -> dict[str, dict[str, int] | None]:
    selected = [lines[number] for number in sorted(changed_lines.intersection(lines))]
    line_missed = sum(1 for item in selected if item[1] == 0 and item[0] > 0)
    line_covered = sum(1 for item in selected if item[1] > 0)
    branch_missed = sum(item[2] for item in selected)
    branch_covered = sum(item[3] for item in selected)
    return {
        "line": {"covered": line_covered, "missed": line_missed, "total": line_covered + line_missed},
        "branch": None
        if branch_missed + branch_covered == 0
        else {
            "covered": branch_covered,
            "missed": branch_missed,
            "total": branch_missed + branch_covered,
        },
    }


def validate_floor_transition(previous: int, current: int, maximum_raise: int) -> None:
    require_basis_points(previous, "previous floor")
    require_basis_points(current, "current floor")
    require_basis_points(maximum_raise, "maximum floor raise")
    if current < previous:
        raise CoverageError(f"coverage floor decrease is forbidden: {previous} -> {current}")
    if current - previous > maximum_raise:
        raise CoverageError(f"coverage floor raise exceeds {maximum_raise} basis points")


def _require_nonnegative_integer(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise CoverageError(f"{label} must be a non-negative integer")
    return value


def validate_baseline_schema(baseline: Any) -> dict[str, Any]:
    if not isinstance(baseline, dict):
        raise CoverageError("coverage baseline must be an object")
    _require_keys(
        baseline,
        {"schemaVersion", "sourceCommit", "policySha256", "manifestSchemaVersion", "predecessor", "reports", "units"},
        set(),
        "coverage baseline",
    )
    if any(
        isinstance(baseline[key], bool) or not isinstance(baseline[key], int) or baseline[key] != 1
        for key in ("schemaVersion", "manifestSchemaVersion")
    ):
        raise CoverageError("coverage baseline schema versions must equal 1")
    _require_hex_string(baseline["sourceCommit"], 40, "coverage baseline sourceCommit", nonzero=True)
    _require_hex_string(baseline["policySha256"], 64, "coverage baseline policySha256")
    predecessor = baseline["predecessor"]
    if predecessor is not None:
        if not isinstance(predecessor, dict):
            raise CoverageError("coverage baseline predecessor must be null or an object")
        _require_keys(
            predecessor,
            {"commit", "baselineBlobSha256", "policyBlobSha256"},
            set(),
            "coverage baseline predecessor",
        )
        _require_hex_string(predecessor["commit"], 40, "coverage predecessor commit")
        for key in ("baselineBlobSha256", "policyBlobSha256"):
            _require_hex_string(predecessor[key], 64, f"coverage predecessor {key}")
    if not isinstance(baseline["reports"], list) or not isinstance(baseline["units"], list):
        raise CoverageError("coverage baseline reports and units must be arrays")
    report_ids: set[str] = set()
    for index, report in enumerate(baseline["reports"]):
        if not isinstance(report, dict):
            raise CoverageError(f"coverage baseline report {index} must be an object")
        _require_keys(
            report,
            {"reportId", "inputIdentitySha256", "measuredTestInputIdentitySha256", "measuredTestSources"},
            set(),
            f"coverage baseline report {index}",
        )
        report_id = _require_string(report["reportId"], "coverage baseline reportId")
        if report_id in report_ids:
            raise CoverageError(f"duplicate coverage baseline report: {report['reportId']}")
        report_ids.add(report_id)
        for key in ("inputIdentitySha256", "measuredTestInputIdentitySha256"):
            _require_hex_string(report[key], 64, f"coverage baseline report {key}")
        if not isinstance(report["measuredTestSources"], list):
            raise CoverageError("measuredTestSources must be an array")
        paths: list[str] = []
        for source in report["measuredTestSources"]:
            if not isinstance(source, dict):
                raise CoverageError("measured test source must be an object")
            _require_keys(source, {"path", "filename", "sha256"}, set(), "measured test source")
            _require_hex_string(source["sha256"], 64, "measured test source sha256")
            paths.append(_require_string(source["path"], "measured test source path"))
            _require_string(source["filename"], "measured test source filename")
        if paths != sorted(set(paths)):
            raise CoverageError("measured test sources must be sorted and unique")
    unit_ids: set[str] = set()
    for index, unit in enumerate(baseline["units"]):
        if not isinstance(unit, dict):
            raise CoverageError(f"coverage baseline unit {index} must be an object")
        _require_keys(
            unit,
            {"id", "line", "branch", "authoredSourceCount", "executableLineCount", "branchCount", "classCount"},
            {"lineFloorBasisPointsAtCapture", "branchFloorBasisPointsAtCapture"},
            f"coverage baseline unit {index}",
        )
        unit_id = _require_string(unit["id"], "coverage baseline unit id")
        if unit_id in unit_ids:
            raise CoverageError(f"duplicate coverage baseline unit: {unit['id']}")
        unit_ids.add(unit_id)
        for metric in ("line", "branch"):
            counters = unit[metric]
            if not isinstance(counters, dict):
                raise CoverageError(f"coverage baseline {unit['id']} {metric} must be an object")
            _require_keys(counters, {"covered", "missed", "total"}, set(), f"coverage baseline {metric}")
            covered = _require_nonnegative_integer(counters["covered"], f"{unit['id']} {metric} covered")
            missed = _require_nonnegative_integer(counters["missed"], f"{unit['id']} {metric} missed")
            total = _require_nonnegative_integer(counters["total"], f"{unit['id']} {metric} total")
            if covered + missed != total:
                raise CoverageError(f"coverage baseline {unit['id']} {metric} counters do not sum")
        for key in ("authoredSourceCount", "executableLineCount", "branchCount", "classCount"):
            _require_nonnegative_integer(unit[key], f"{unit['id']} {key}")
        for key in ("lineFloorBasisPointsAtCapture", "branchFloorBasisPointsAtCapture"):
            if key in unit:
                require_basis_points(unit[key], f"{unit['id']} {key}")
    return baseline


def _git_file_bytes(root: Path, commit: str, relative: str) -> bytes:
    try:
        return _git(root, "show", f"{commit}:{relative}")
    except CoverageError as error:
        raise CoverageError(f"required historical blob is missing: {commit}:{relative}") from error


def validate_baseline_lineage(root: Path, baseline: Any, current_commit: str) -> dict[str, Any] | None:
    candidate = validate_baseline_schema(baseline)
    source = candidate["sourceCommit"]
    try:
        _git(root, "cat-file", "-e", f"{source}^{{commit}}")
        _git(root, "cat-file", "-e", f"{current_commit}^{{commit}}")
        _git(root, "merge-base", "--is-ancestor", source, current_commit)
    except CoverageError as error:
        raise CoverageError("coverage baseline sourceCommit must resolve and be an ancestor of current source") from error
    predecessor = candidate["predecessor"]
    baseline_relative = "config/quality/coverage-baseline.json"
    policy_relative = "config/quality/coverage-policy.json"
    if predecessor is None:
        exists = subprocess.run(
            ["git", "cat-file", "-e", f"{source}:{baseline_relative}"],
            cwd=root,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        ).returncode == 0
        if exists:
            raise CoverageError("first baseline source already contains a baseline blob")
        return None
    if predecessor["commit"] != source:
        raise CoverageError("coverage predecessor commit must equal baseline sourceCommit")
    old_baseline_bytes = _git_file_bytes(root, source, baseline_relative)
    old_policy_bytes = _git_file_bytes(root, source, policy_relative)
    if hashlib.sha256(old_baseline_bytes).hexdigest() != predecessor["baselineBlobSha256"]:
        raise CoverageError("coverage predecessor baseline blob hash mismatch")
    if hashlib.sha256(old_policy_bytes).hexdigest() != predecessor["policyBlobSha256"]:
        raise CoverageError("coverage predecessor policy blob hash mismatch")
    try:
        old_baseline = _normalize(json.loads(old_baseline_bytes, object_pairs_hook=_reject_duplicate_pairs))
        old_policy = _normalize(json.loads(old_policy_bytes, object_pairs_hook=_reject_duplicate_pairs))
    except (UnicodeError, json.JSONDecodeError) as error:
        raise CoverageError("invalid historical baseline or policy JSON") from error
    validate_baseline_schema(old_baseline)
    validate_policy(old_policy)
    if old_baseline["policySha256"] != hashlib.sha256(old_policy_bytes).hexdigest():
        raise CoverageError("historical baseline policy hash mismatch")
    return old_baseline


def validate_predecessor_floor_transitions(old: dict[str, Any], new: dict[str, Any], maximum_raise: int) -> None:
    old_units = {unit["id"]: unit for unit in old["units"]}
    new_units = {unit["id"]: unit for unit in new["units"]}
    if set(old_units) != set(new_units):
        raise CoverageError("predecessor/current baseline unit topology mismatch")
    for unit_id, previous in old_units.items():
        current = new_units[unit_id]
        for metric in ("line", "branch"):
            floor_key = f"{metric}FloorBasisPointsAtCapture"
            if previous[metric]["total"] > 0:
                if current[metric]["total"] == 0:
                    raise CoverageError(f"{unit_id} {metric} became N/A across baseline replacement")
                previous_has_floor = floor_key in previous
                current_has_floor = floor_key in current
                if previous_has_floor != current_has_floor:
                    raise CoverageError(f"{unit_id} {metric} captured floor disappeared")
                if previous_has_floor:
                    validate_floor_transition(previous[floor_key], current[floor_key], maximum_raise)


def _git(root: Path, *arguments: str, input_bytes: bytes | None = None) -> bytes:
    try:
        return subprocess.run(
            ["git", *arguments],
            cwd=root,
            input=input_bytes,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        ).stdout
    except subprocess.CalledProcessError as error:
        raise CoverageError(f"git {' '.join(arguments)} failed: {error.stderr.decode(errors='replace').strip()}") from error


def _paths_under_roots(paths: Iterable[str], roots: Iterable[str]) -> set[str]:
    prefixes = tuple(root.rstrip("/") + "/" for root in roots)
    return {path for path in paths if path.endswith((".kt", ".java")) and path.startswith(prefixes)}


def verify_git_source_set(
    root: Path,
    source_commit: str,
    roots: list[str],
    records: list[dict[str, Any]],
    label: str,
) -> None:
    tree_output = _git(root, "ls-tree", "-rz", source_commit, "--", *roots)
    tree_objects: dict[str, str] = {}
    for part in tree_output.split(b"\0"):
        if not part:
            continue
        metadata, raw_path = part.split(b"\t", 1)
        mode, object_type, object_id = metadata.decode("ascii").split()
        if object_type != "blob" or not mode.startswith("100"):
            continue
        tree_objects[raw_path.decode("utf-8")] = object_id
    committed = _paths_under_roots(tree_objects, roots)
    declared = {record.get("path") for record in records}
    missing = sorted(committed.difference(declared))
    extra = sorted(declared.difference(committed))
    if missing:
        raise CoverageError(f"{label} manifest missing committed source: {missing[0]}")
    if extra:
        raise CoverageError(f"{label} manifest has extra source: {extra[0]}")
    committed_blobs = _batch_git_blobs(root, {path: tree_objects[path] for path in committed})
    for record in records:
        relative = record["path"]
        path = root / relative
        if not path.is_file():
            raise CoverageError(f"{label} source missing from worktree: {relative}")
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if digest != record.get("sha256"):
            raise CoverageError(f"{label} source hash mismatch: {relative}")
        committed_blob = committed_blobs[relative]
        if hashlib.sha256(committed_blob).hexdigest() != digest:
            raise CoverageError(f"{label} source hash differs from source commit: {relative}")
        if "package" in record:
            package = parse_package_declaration(committed_blob, Path(relative).suffix)
            if record["package"] != package:
                raise CoverageError(f"{label} lexical package mismatch: {relative}")


def _batch_git_blobs(root: Path, objects: dict[str, str]) -> dict[str, bytes]:
    ordered = sorted(objects.items())
    if not ordered:
        return {}
    process = subprocess.Popen(
        ["git", "cat-file", "--batch"],
        cwd=root,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    stdout, stderr = process.communicate("".join(f"{object_id}\n" for _, object_id in ordered).encode("ascii"))
    if process.returncode != 0:
        raise CoverageError(f"git cat-file --batch failed: {stderr.decode(errors='replace').strip()}")
    cursor = 0
    result: dict[str, bytes] = {}
    for path, object_id in ordered:
        header_end = stdout.find(b"\n", cursor)
        if header_end < 0:
            raise CoverageError(f"truncated git batch header for {path}")
        header = stdout[cursor:header_end].decode("ascii").split()
        if len(header) != 3 or header[0] != object_id or header[1] != "blob":
            raise CoverageError(f"unexpected git batch header for {path}")
        size = int(header[2])
        start = header_end + 1
        end = start + size
        if end >= len(stdout) or stdout[end:end + 1] != b"\n":
            raise CoverageError(f"truncated git batch blob for {path}")
        result[path] = stdout[start:end]
        cursor = end + 1
    if cursor != len(stdout):
        raise CoverageError("unexpected trailing git batch output")
    return result


def _git_blob_ids(root: Path, commit: str, paths: Iterable[str]) -> dict[str, str]:
    selected = sorted(set(paths))
    if not selected:
        return {}
    output = _git(root, "ls-tree", "-rz", commit, "--", *selected)
    result: dict[str, str] = {}
    for record in output.split(b"\0"):
        if not record:
            continue
        try:
            metadata, raw_path = record.split(b"\t", 1)
            mode, object_type, object_id = metadata.decode("ascii").split()
            path = raw_path.decode("utf-8")
        except (ValueError, UnicodeError) as error:
            raise CoverageError("malformed git tree blob record") from error
        if object_type != "blob" or not mode.startswith("100"):
            raise CoverageError(f"changed path is not a regular blob at {commit}: {path}")
        result[path] = object_id
    return result


@dataclass
class ChangedFile:
    status: str
    old_path: str | None
    new_path: str
    new_lines: set[int]
    hunk_count: int = 0


def _decode_path(token: bytes) -> str:
    if token.startswith(b'"') and token.endswith(b'"'):
        token = token[1:-1]
        output = bytearray()
        i = 0
        while i < len(token):
            if token[i] != 92:
                output.append(token[i])
                i += 1
            elif i + 3 < len(token) and all(48 <= item <= 55 for item in token[i + 1 : i + 4]):
                output.append(int(token[i + 1 : i + 4], 8))
                i += 4
            else:
                escapes = {ord("t"): 9, ord("n"): 10, ord("r"): 13, ord('"'): 34, 92: 92}
                i += 1
                output.append(escapes.get(token[i], token[i]))
                i += 1
        token = bytes(output)
    return token.decode("utf-8")


def _strip_patch_prefix(path: str, prefix: str) -> str:
    if not path.startswith(prefix):
        raise CoverageError(f"patch header path lacks {prefix!r} prefix: {path}")
    return path[len(prefix):]


def _diff_git_paths(line: bytes) -> tuple[str, str]:
    payload = line[len(b"diff --git "):]
    tokens: list[bytes] = []
    index = 0
    while index < len(payload):
        while index < len(payload) and payload[index:index + 1] == b" ":
            index += 1
        if index == len(payload):
            break
        start = index
        if payload[index:index + 1] == b'"':
            index += 1
            escaped = False
            while index < len(payload):
                byte = payload[index]
                index += 1
                if escaped:
                    escaped = False
                elif byte == 92:
                    escaped = True
                elif byte == 34:
                    break
            else:
                raise CoverageError(f"unterminated quoted diff header: {line!r}")
        else:
            while index < len(payload) and payload[index:index + 1] != b" ":
                index += 1
        tokens.append(payload[start:index])
    if len(tokens) != 2:
        raise CoverageError(f"invalid diff --git header: {line!r}")
    return (
        _strip_patch_prefix(_decode_path(tokens[0]), "a/"),
        _strip_patch_prefix(_decode_path(tokens[1]), "b/"),
    )


def parse_zero_context_diff(
    name_status_z: bytes,
    patch: bytes,
    *,
    changed_blob_paths: set[str] | None = None,
    blob_contents: dict[str, tuple[bytes, bytes]] | None = None,
) -> dict[str, ChangedFile]:
    if name_status_z and not name_status_z.endswith(b"\0"):
        raise CoverageError("name-status stream is not NUL terminated")
    parts = name_status_z.split(b"\0")
    changes: dict[str, ChangedFile] = {}
    index = 0
    while index < len(parts) and parts[index]:
        try:
            status_token = parts[index].decode("ascii")
        except UnicodeDecodeError as error:
            raise CoverageError("name-status contains a non-ASCII status") from error
        index += 1
        status = status_token[0]
        if not (
            status_token in {"A", "M"}
            or (status in {"R", "C"} and re.fullmatch(r"[RC][0-9]{1,3}", status_token))
        ):
            raise CoverageError(f"unexpected ACMR status: {status_token}")
        if status in {"R", "C"} and int(status_token[1:]) > 100:
            raise CoverageError(f"rename/copy score is outside 0..100: {status_token}")
        if status in {"R", "C"}:
            if index + 1 >= len(parts):
                raise CoverageError(f"truncated rename/copy status: {status_token}")
            old_path = _decode_path(parts[index])
            new_path = _decode_path(parts[index + 1])
            index += 2
        else:
            if index >= len(parts):
                raise CoverageError(f"truncated name-status record: {status_token}")
            old_path = None if status == "A" else _decode_path(parts[index])
            new_path = _decode_path(parts[index])
            index += 1
        if new_path in changes:
            raise CoverageError(f"duplicate authoritative status path: {new_path}")
        changes[new_path] = ChangedFile(status=status, old_path=old_path, new_path=new_path, new_lines=set())
    if any(parts[index:]):
        raise CoverageError("malformed trailing name-status data")
    current: ChangedFile | None = None
    section_header: tuple[str, str] | None = None
    section_old: str | None = None
    section_new: str | None = None
    seen_old_header = False
    seen_new_header = False
    seen_sections: set[str] = set()
    hunks: dict[str, list[tuple[int, int, int, int, list[bytes], list[bytes]]]] = {}
    active_hunk: list[Any] | None = None

    def finish_hunk() -> None:
        nonlocal active_hunk
        if active_hunk is None or current is None:
            return
        old_start, old_count, new_start, new_count, removed, added = active_hunk
        if len(removed) != old_count or len(added) != new_count:
            raise CoverageError(f"hunk payload count disagrees with header: {current.new_path}")
        hunks.setdefault(current.new_path, []).append(
            (old_start, old_count, new_start, new_count, removed, added),
        )
        active_hunk = None

    def finish_section() -> None:
        nonlocal current, section_header, section_old, section_new, seen_old_header, seen_new_header
        finish_hunk()
        if section_header is None:
            return
        old_header, new_header = section_header
        change = changes.get(new_header)
        if change is None:
            raise CoverageError(f"patch header is absent from authoritative status: {new_header}")
        expected_old = change.old_path if change.old_path is not None else change.new_path
        if old_header != expected_old:
            raise CoverageError(f"patch header old path disagrees with status: {old_header} != {expected_old}")
        if change.hunk_count and not (seen_old_header and seen_new_header):
            raise CoverageError(f"patch section with hunks requires exact --- and +++ headers: {new_header}")
        if seen_old_header and section_old != change.old_path:
            raise CoverageError(f"patch --- header disagrees with status: {section_old}")
        if seen_new_header and section_new != change.new_path:
            raise CoverageError(f"patch +++ header disagrees with status: {section_new}")
        if new_header in seen_sections:
            raise CoverageError(f"duplicate patch section: {new_header}")
        seen_sections.add(new_header)
        current = None
        section_header = None
        section_old = None
        section_new = None
        seen_old_header = False
        seen_new_header = False

    for raw_line in patch.split(b"\n"):
        metadata_line = raw_line[:-1] if raw_line.endswith(b"\r") else raw_line
        if metadata_line.startswith(b"diff --git "):
            finish_section()
            section_header = _diff_git_paths(metadata_line)
            current = changes.get(section_header[1])
            if current is None:
                raise CoverageError(f"patch header is absent from authoritative status: {section_header[1]}")
        elif metadata_line.startswith(b"--- "):
            finish_hunk()
            if section_header is None:
                raise CoverageError("patch --- header appears outside a diff section")
            if seen_old_header:
                raise CoverageError("patch section requires exactly one --- header")
            if seen_new_header:
                raise CoverageError("patch --- header must appear before +++ header")
            seen_old_header = True
            token = metadata_line[4:]
            section_old = None if token == b"/dev/null" else _strip_patch_prefix(_decode_path(token), "a/")
        elif metadata_line.startswith(b"+++ "):
            if section_header is None:
                raise CoverageError("patch +++ header appears outside a diff section")
            if not seen_old_header:
                raise CoverageError("patch --- header must appear before +++ header")
            if seen_new_header:
                raise CoverageError("patch section requires exactly one +++ header")
            seen_new_header = True
            token = metadata_line[4:]
            if token == b"/dev/null":
                section_new = None
                current = None
                continue
            section_new = _strip_patch_prefix(_decode_path(token), "b/")
            current = changes.get(section_new)
        elif metadata_line.startswith(b"@@ ") and current is not None:
            finish_hunk()
            if not (seen_old_header and seen_new_header):
                raise CoverageError(f"patch section with hunks requires exact --- and +++ headers: {current.new_path}")
            match = re.match(
                rb"@@ -([0-9]+)(?:,([0-9]+))? \+([0-9]+)(?:,([0-9]+))? @@",
                metadata_line,
            )
            if not match:
                raise CoverageError(f"invalid zero-context hunk header: {metadata_line!r}")
            old_start = int(match.group(1))
            old_count = int(match.group(2)) if match.group(2) is not None else 1
            new_start = int(match.group(3))
            new_count = int(match.group(4)) if match.group(4) is not None else 1
            current.new_lines.update(range(new_start, new_start + new_count))
            current.hunk_count += 1
            active_hunk = [old_start, old_count, new_start, new_count, [], []]
        elif active_hunk is not None and raw_line.startswith(b"-"):
            active_hunk[4].append(raw_line[1:])
        elif active_hunk is not None and raw_line.startswith(b"+"):
            active_hunk[5].append(raw_line[1:])
    finish_section()
    required = changed_blob_paths if changed_blob_paths is not None else set()
    for path in sorted(required):
        if path not in changes:
            raise CoverageError(f"changed blob is absent from authoritative status: {path}")
        if changes[path].hunk_count == 0:
            raise CoverageError(f"changed blob has no hunk: {path}")
    if blob_contents is not None:
        for path, (old_bytes, new_bytes) in blob_contents.items():
            old_lines = [] if not old_bytes else old_bytes.split(b"\n")
            new_lines = [] if not new_bytes else new_bytes.split(b"\n")
            if old_bytes.endswith(b"\n"):
                old_lines.pop()
            if new_bytes.endswith(b"\n"):
                new_lines.pop()
            rebuilt: list[bytes] = []
            cursor = 0
            for old_start, old_count, new_start, _new_count, removed, added in hunks.get(path, []):
                old_index = 0 if old_start == 0 else old_start - 1
                if old_index < cursor or old_lines[old_index:old_index + old_count] != removed:
                    raise CoverageError(f"hunk payload disagrees with raw old blob: {path}")
                rebuilt.extend(old_lines[cursor:old_index])
                new_index = 0 if new_start == 0 else new_start - 1
                if new_index != len(rebuilt):
                    raise CoverageError(f"hunk new range disagrees with reconstructed blob: {path}")
                rebuilt.extend(added)
                cursor = old_index + old_count
            rebuilt.extend(old_lines[cursor:])
            if rebuilt != new_lines:
                raise CoverageError(f"hunk payload disagrees with raw new blob: {path}")
    return changes


def classify_exact_sources(sources: set[str], units: list[dict[str, Any]]) -> list[str]:
    ownership: dict[str, str] = {}
    for unit in units:
        unit_id = unit.get("id", "<unknown>")
        if unit.get("family") in {"rendering", "tool"} and {
            "lineFloorBasisPoints",
            "branchFloorBasisPoints",
        }.intersection(unit):
            raise CoverageError(f"{unit.get('family')} unit {unit_id} must not define a floor")
        for source in unit.get("sources", []):
            if source in ownership:
                raise CoverageError(f"coverage source overlap: {source} in {ownership[source]} and {unit_id}")
            ownership[source] = unit_id
    missing = sorted(sources.difference(ownership))
    extra = sorted(set(ownership).difference(sources))
    if missing:
        raise CoverageError(f"unclassified coverage source: {missing[0]}")
    if extra:
        raise CoverageError(f"classified source is not authored: {extra[0]}")
    return sorted(ownership)


def write_summary(
    path: Path,
    *,
    source_commit: str,
    event: str,
    status: str,
    violations: list[str],
    artifacts: list[str],
    base_ref: str | None = None,
    merge_base: str | None = None,
    changed_coverage: list[dict[str, Any]] | None = None,
    reports: list[dict[str, Any]] | None = None,
    units: list[dict[str, Any]] | None = None,
) -> None:
    payload = {
        "schemaVersion": 1,
        "sourceCommit": source_commit,
        "event": event,
        "baseRef": base_ref,
        "mergeBase": merge_base,
        "status": status,
        "violations": sorted(set(violations)),
        "artifacts": sorted(set(artifacts)),
        "changedCoverage": sorted(changed_coverage or [], key=lambda item: item["reportId"]),
        "reports": sorted(reports or [], key=lambda item: item["reportId"]),
        "units": sorted(units or [], key=lambda item: item["id"]),
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(canonical_json_bytes(payload) + b"\n")


def _sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _require_keys(value: dict[str, Any], required: set[str], optional: set[str], label: str) -> None:
    missing = sorted(required.difference(value))
    unknown = sorted(set(value).difference(required).difference(optional))
    if missing:
        raise CoverageError(f"{label} missing key: {missing[0]}")
    if unknown:
        raise CoverageError(f"{label} unknown key: {unknown[0]}")


def validate_policy(policy: Any) -> dict[str, Any]:
    if not isinstance(policy, dict):
        raise CoverageError("coverage policy must be an object")
    _require_keys(
        policy,
        {
            "schemaVersion", "enforcementMode", "activeModules", "excludedModules", "reports", "units",
            "changedThresholds", "maximumBaselineDropBasisPoints", "maximumFloorRaiseBasisPoints",
            "nonExecutableExceptions", "unclassifiedAuthoredSource",
        },
        set(),
        "coverage policy",
    )
    if isinstance(policy["schemaVersion"], bool) or not isinstance(policy["schemaVersion"], int) or policy["schemaVersion"] != 1:
        raise CoverageError("coverage policy schemaVersion must equal 1")
    mode = policy["enforcementMode"]
    _require_string(mode, "coverage policy enforcementMode")
    if mode not in {"measurement", "blocking"}:
        raise CoverageError("coverage policy enforcementMode must be measurement or blocking")
    if policy["unclassifiedAuthoredSource"] != "fail":
        raise CoverageError("unclassifiedAuthoredSource must equal fail")
    if not isinstance(policy["activeModules"], list) or any(not isinstance(item, str) for item in policy["activeModules"]):
        raise CoverageError("activeModules must be a string array")
    if sorted(policy["activeModules"]) != sorted(set(policy["activeModules"])):
        raise CoverageError("activeModules must be unique")
    if policy["excludedModules"] != [
        {"module": ":benchmark", "reason": "connected macrobenchmark and device performance evidence owns this module"},
    ]:
        raise CoverageError("only the reviewed :benchmark coverage exclusion is allowed")
    thresholds = policy["changedThresholds"]
    if not isinstance(thresholds, dict) or set(thresholds) != {"lineBasisPoints", "branchBasisPoints"}:
        raise CoverageError("changedThresholds has an invalid schema")
    if require_basis_points(thresholds["lineBasisPoints"], "changed line threshold") != 8000:
        raise CoverageError("changed line threshold must equal 8000")
    if require_basis_points(thresholds["branchBasisPoints"], "changed branch threshold") != 7000:
        raise CoverageError("changed branch threshold must equal 7000")
    if require_basis_points(policy["maximumBaselineDropBasisPoints"], "maximum baseline drop") != 50:
        raise CoverageError("maximum baseline drop must equal 50")
    if require_basis_points(policy["maximumFloorRaiseBasisPoints"], "maximum floor raise") != 200:
        raise CoverageError("maximum floor raise must equal 200")
    reports = policy["reports"]
    if not isinstance(reports, list):
        raise CoverageError("policy reports must be an array")
    report_ids: set[str] = set()
    for index, report in enumerate(reports):
        if not isinstance(report, dict):
            raise CoverageError(f"policy report {index} must be an object")
        _require_keys(
            report,
            {"id", "module", "platform", "variant", "testTask", "sourceRoots", "testSourceRoots", "ownedSourceRoots"},
            set(),
            f"policy report {index}",
        )
        for key in ("id", "module", "platform", "variant", "testTask"):
            _require_string(report[key], f"policy report {index} {key}")
        if report["id"] in report_ids:
            raise CoverageError(f"duplicate policy report: {report['id']}")
        report_ids.add(report["id"])
        for key in ("sourceRoots", "testSourceRoots", "ownedSourceRoots"):
            if not isinstance(report[key], list) or any(not isinstance(item, str) for item in report[key]):
                raise CoverageError(f"{report['id']} {key} must be a string array")
            if report[key] != sorted(set(report[key])):
                raise CoverageError(f"{report['id']} {key} must be sorted and unique")
        if not set(report["ownedSourceRoots"]).issubset(report["sourceRoots"]):
            raise CoverageError(f"{report['id']} ownedSourceRoots must be a sourceRoots subset")
    units = policy["units"]
    if not isinstance(units, list):
        raise CoverageError("policy units must be an array")
    unit_ids: set[str] = set()
    for index, unit in enumerate(units):
        if not isinstance(unit, dict):
            raise CoverageError(f"policy unit {index} must be an object")
        _require_keys(
            unit,
            {"id", "family", "selection", "reportIds", "sources"},
            {"lineTargetBasisPoints", "branchTargetBasisPoints", "lineFloorBasisPoints", "branchFloorBasisPoints"},
            f"policy unit {index}",
        )
        for key in ("id", "family", "selection"):
            _require_string(unit[key], f"policy unit {index} {key}")
        if unit["id"] in unit_ids:
            raise CoverageError(f"duplicate policy unit: {unit['id']}")
        unit_ids.add(unit["id"])
        if unit["selection"] not in {"all", "exact"}:
            raise CoverageError(f"invalid selection for {unit['id']}")
        if unit["family"] not in {"contract", "data", "state", "assembly", "rendering", "tool"}:
            raise CoverageError(f"invalid family for {unit['id']}")
        if (
            not isinstance(unit["sources"], list)
            or any(not isinstance(item, str) for item in unit["sources"])
            or unit["sources"] != sorted(set(unit["sources"]))
        ):
            raise CoverageError(f"{unit['id']} sources must be a sorted unique array")
        if (unit["selection"] == "all") != (unit["sources"] == []):
            raise CoverageError(f"{unit['id']} all selection requires an empty sources array")
        if (
            not isinstance(unit["reportIds"], list)
            or any(not isinstance(item, str) or item not in report_ids for item in unit["reportIds"])
        ):
            raise CoverageError(f"{unit['id']} references an unknown report")
        for key in ("lineTargetBasisPoints", "branchTargetBasisPoints", "lineFloorBasisPoints", "branchFloorBasisPoints"):
            if key in unit:
                require_basis_points(unit[key], f"{unit['id']} {key}")
        if unit["family"] in {"rendering", "tool", "assembly"} and any(
            key in unit for key in ("lineTargetBasisPoints", "branchTargetBasisPoints", "lineFloorBasisPoints", "branchFloorBasisPoints")
        ):
            raise CoverageError(f"{unit['family']} unit {unit['id']} must not define target or floor")
        if unit["family"] not in {"rendering", "tool", "assembly"}:
            validate_unit_floor_schema(unit, mode, branch_total=1 if "branchFloorBasisPoints" in unit else 0)
    if policy["nonExecutableExceptions"] != []:
        if not isinstance(policy["nonExecutableExceptions"], list):
            raise CoverageError("nonExecutableExceptions must be an array")
        for exception in policy["nonExecutableExceptions"]:
            if not isinstance(exception, dict):
                raise CoverageError("non-executable exception must be an object")
            _require_keys(exception, {"path", "sha256", "reason"}, set(), "non-executable exception")
            _require_string(exception["path"], "non-executable exception path")
            _require_hex_string(exception["sha256"], 64, "non-executable exception sha256")
            _require_string(exception["reason"], "non-executable exception reason")
    return policy


def _entry_sources(entry: dict[str, Any]) -> dict[str, dict[str, Any]]:
    records: dict[str, dict[str, Any]] = {}
    for record in entry["sources"]:
        path = record["path"]
        if path in records:
            raise CoverageError(f"duplicate manifest source path: {path}")
        records[path] = record
    return records


def validate_manifest_schema(manifest: Any) -> dict[str, Any]:
    if not isinstance(manifest, dict):
        raise CoverageError("coverage manifest must be an object")
    _require_keys(
        manifest,
        {"schemaVersion", "sourceCommit", "gradleProjects", "buildModules", "entries"},
        set(),
        "coverage manifest",
    )
    if isinstance(manifest["schemaVersion"], bool) or not isinstance(manifest["schemaVersion"], int) or manifest["schemaVersion"] != 1:
        raise CoverageError("manifest schemaVersion must equal 1")
    _require_hex_string(manifest["sourceCommit"], 40, "manifest sourceCommit", nonzero=True)
    for key in ("gradleProjects", "buildModules", "entries"):
        value = manifest[key]
        if not isinstance(value, list) or any(not isinstance(item, str) for item in value):
            raise CoverageError(f"manifest {key} must be a string array")
        if value != sorted(set(value)):
            raise CoverageError(f"manifest {key} must be sorted and unique")
    return manifest


def validate_entry_schema(entry: Any, relative: str) -> dict[str, Any]:
    if not isinstance(entry, dict):
        raise CoverageError(f"coverage entry must be an object: {relative}")
    _require_keys(
        entry,
        {
            "schemaVersion", "sourceCommit", "reportId", "module", "platform", "variant", "testTask",
            "xmlReport", "sourceRoots", "sources", "testSourceRoots", "testSources",
            "testInputIdentitySha256", "inputClassArtifacts", "preparedClassDirectory", "classFileCount",
            "classes", "executionData", "executionFileSha256", "executionRecords",
            "ignoredNonProjectExecutionRecordCount", "executionSemanticSha256", "xmlFileSha256",
            "reportSemanticSha256",
        },
        set(),
        f"coverage entry {relative}",
    )
    if isinstance(entry["schemaVersion"], bool) or not isinstance(entry["schemaVersion"], int) or entry["schemaVersion"] != 1:
        raise CoverageError(f"coverage entry schemaVersion must equal 1: {relative}")
    _require_hex_string(entry["sourceCommit"], 40, "coverage entry sourceCommit", nonzero=True)
    for key in ("reportId", "module", "platform", "variant", "testTask", "xmlReport", "preparedClassDirectory"):
        _require_string(entry[key], f"coverage entry {key}")
    for key in (
        "testInputIdentitySha256", "executionFileSha256", "executionSemanticSha256",
        "xmlFileSha256", "reportSemanticSha256",
    ):
        _require_hex_string(entry[key], 64, f"coverage entry {key}")
    for key in ("sourceRoots", "testSourceRoots", "executionData"):
        if not isinstance(entry[key], list) or any(not isinstance(item, str) for item in entry[key]):
            raise CoverageError(f"coverage entry {key} must be a string array: {relative}")
        if entry[key] != sorted(set(entry[key])):
            raise CoverageError(f"coverage entry {key} must be sorted and unique: {relative}")
    artifacts = entry["inputClassArtifacts"]
    if not isinstance(artifacts, list) or not artifacts:
        raise CoverageError(f"coverage entry inputClassArtifacts must be a non-empty array: {relative}")
    artifact_paths: list[str] = []
    for artifact in artifacts:
        if not isinstance(artifact, dict):
            raise CoverageError(f"coverage entry inputClassArtifacts record must be an object: {relative}")
        _require_keys(artifact, {"entryCount", "kind", "path", "sha256"}, set(), "input class artifact")
        _require_nonnegative_integer(artifact["entryCount"], "input class artifact entryCount")
        kind = _require_string(artifact["kind"], "input class artifact kind")
        if kind not in {"file", "directory"}:
            raise CoverageError("input class artifact kind must be file or directory")
        artifact_paths.append(_require_string(artifact["path"], "input class artifact path"))
        _require_hex_string(artifact["sha256"], 64, "input class artifact sha256")
    if artifact_paths != sorted(set(artifact_paths)):
        raise CoverageError(f"coverage entry inputClassArtifacts must be sorted by unique path: {relative}")
    for key, required_keys in (
        ("sources", {"path", "package", "filename", "sha256"}),
        ("testSources", {"path", "filename", "sha256"}),
    ):
        if not isinstance(entry[key], list):
            raise CoverageError(f"coverage entry {key} must be an array: {relative}")
        paths: list[str] = []
        for record in entry[key]:
            if not isinstance(record, dict):
                raise CoverageError(f"coverage entry {key} record must be an object: {relative}")
            _require_keys(record, required_keys, set(), f"coverage entry {key} record")
            _require_hex_string(record["sha256"], 64, f"coverage entry {key} source hash")
            paths.append(_require_string(record["path"], f"coverage entry {key} source path"))
            _require_string(record["filename"], f"coverage entry {key} filename")
            if key == "sources":
                _require_string(record["package"], "coverage entry source package")
        if paths != sorted(set(paths)):
            raise CoverageError(f"coverage entry {key} must be sorted by unique path: {relative}")
    _require_nonnegative_integer(entry["classFileCount"], "coverage entry classFileCount")
    _require_nonnegative_integer(
        entry["ignoredNonProjectExecutionRecordCount"],
        "coverage entry ignoredNonProjectExecutionRecordCount",
    )
    if not isinstance(entry["classes"], list) or not isinstance(entry["executionRecords"], list):
        raise CoverageError("coverage entry classes and executionRecords must be arrays")
    return entry


def _under_roots(path: str, roots: list[str]) -> bool:
    return any(path.startswith(root.rstrip("/") + "/") for root in roots)


def _load_run(manifest_path: Path, policy_path: Path, source_commit: str) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any], Path]:
    root = Path(_git(manifest_path.resolve().parents[3], "rev-parse", "--show-toplevel").decode().strip())
    if not re.fullmatch(r"[0-9a-f]{40}", source_commit) or source_commit == "0" * 40:
        raise CoverageError("CLI source commit must be one non-zero 40-hex commit")
    manifest = validate_manifest_schema(read_json(manifest_path))
    policy = validate_policy(read_json(policy_path))
    if manifest.get("sourceCommit") != source_commit:
        raise CoverageError("manifest/CLI source commit mismatch")
    head = _git(root, "rev-parse", "HEAD").decode().strip()
    if head != source_commit:
        raise CoverageError("CLI source commit must equal HEAD")
    if sorted(manifest.get("buildModules", [])) != sorted(policy["activeModules"]):
        raise CoverageError("policy activeModules differ from settings manifest")
    entries: dict[str, Any] = {}
    for relative in manifest.get("entries", []):
        path = root / relative
        entry = validate_entry_schema(read_json(path), relative)
        report_id = entry.get("reportId")
        if report_id in entries:
            raise CoverageError(f"duplicate manifest report: {report_id}")
        if entry.get("sourceCommit") != source_commit:
            raise CoverageError(f"entry source commit mismatch: {relative}")
        entries[report_id] = entry
    policy_reports = {report["id"]: report for report in policy["reports"]}
    if set(entries) != set(policy_reports):
        raise CoverageError(f"manifest/policy report topology mismatch: manifest={sorted(entries)}, policy={sorted(policy_reports)}")
    for report_id, report in policy_reports.items():
        entry = entries[report_id]
        for policy_key, entry_key in (
            ("module", "module"), ("platform", "platform"), ("variant", "variant"), ("testTask", "testTask"),
            ("sourceRoots", "sourceRoots"), ("testSourceRoots", "testSourceRoots"),
        ):
            if report[policy_key] != entry[entry_key]:
                raise CoverageError(f"{report_id} {policy_key} differs from manifest")
        manifest_sources = _entry_sources(entry)
        verify_git_source_set(root, source_commit, entry["sourceRoots"], entry["sources"], f"{report_id} production")
        verify_git_source_set(root, source_commit, entry["testSourceRoots"], entry["testSources"], f"{report_id} test")
    authored_keys: dict[tuple[str, str], set[str]] = {}
    for report_id, entry in entries.items():
        for source in entry["sources"]:
            key = (source["package"], source["filename"])
            authored_keys.setdefault(key, set()).add(entry["module"])
    collisions = sorted((key, modules) for key, modules in authored_keys.items() if len(modules) > 1)
    if collisions:
        key, modules = collisions[0]
        raise CoverageError(f"authored package/filename key crosses modules: {key} in {sorted(modules)}")
    owned_by_report = {
        report["id"]: sorted(
            path for path in _entry_sources(entries[report["id"]]) if _under_roots(path, report["ownedSourceRoots"])
        )
        for report in policy["reports"]
    }
    owned = [path for paths in owned_by_report.values() for path in paths]
    if len(owned) != len(set(owned)):
        raise CoverageError("report ownership overlap is forbidden")
    expanded_units = []
    for unit in policy["units"]:
        expanded = dict(unit)
        if unit["selection"] == "all":
            expanded["sources"] = sorted(
                path for report_id in unit["reportIds"] for path in owned_by_report[report_id]
            )
        expanded_units.append(expanded)
    classified = {source for unit in expanded_units for source in unit["sources"]}
    classify_exact_sources(set(owned), expanded_units)
    if classified != set(owned):
        raise CoverageError("policy unit classification differs from report ownership")
    return manifest, policy, entries, root


def _report_audit_evidence(
    report: dict[str, Any],
    entry: dict[str, Any],
    excluded: list[str],
) -> dict[str, Any]:
    topology = {
        "reportId": report["id"],
        "module": report["module"],
        "platform": report["platform"],
        "variant": report["variant"],
        "testTask": report["testTask"],
        "sourceRoots": report["sourceRoots"],
        "testSourceRoots": report["testSourceRoots"],
        "ownedSourceRoots": report["ownedSourceRoots"],
    }
    return {
        **topology,
        "inputIdentitySha256": _sha256_bytes(canonical_json_bytes(topology)),
        "measuredTestInputIdentitySha256": entry["testInputIdentitySha256"],
        "measuredTestSources": entry["testSources"],
        "inputClassArtifacts": entry["inputClassArtifacts"],
        "preparedClassDirectory": entry["preparedClassDirectory"],
        "classFileCount": entry["classFileCount"],
        "executionData": entry["executionData"],
        "executionFileSha256": entry["executionFileSha256"],
        "executionSemanticSha256": entry["executionSemanticSha256"],
        "xmlReport": entry["xmlReport"],
        "xmlFileSha256": entry["xmlFileSha256"],
        "reportSemanticSha256": entry["reportSemanticSha256"],
        "excludedNonAuthoredXmlEntries": excluded,
    }


def _unit_audit_evidence(
    current: dict[str, Any],
    baseline: dict[str, Any],
    definition: dict[str, Any],
    maximum_drop_basis_points: int,
) -> dict[str, Any]:
    denominator_keys = ("authoredSourceCount", "executableLineCount", "branchCount", "classCount")
    ratios: dict[str, Any] = {}
    for metric in ("line", "branch"):
        now = current[metric]
        before = baseline[metric]
        floor = definition.get(f"{metric}FloorBasisPoints")
        captured_floor = baseline.get(f"{metric}FloorBasisPointsAtCapture")
        current_floor_passed = now["total"] == 0 or floor is None or not ratio_below_basis_points(
            now["covered"], now["total"], floor,
        )
        captured_floor_passed = (
            before["total"] == 0
            or captured_floor is None
            or not ratio_below_basis_points(before["covered"], before["total"], captured_floor)
        )
        drop_passed = (
            before["total"] == 0
            or now["total"] == 0
            or not baseline_drop_exceeded(
                now["covered"], now["total"], before["covered"], before["total"], maximum_drop_basis_points,
            )
        )
        ratios[metric] = {
            "current": {"covered": now["covered"], "total": now["total"]},
            "baseline": {"covered": before["covered"], "total": before["total"]},
            "capturedFloorBasisPoints": captured_floor,
            "policyFloorBasisPoints": floor,
            "targetBasisPoints": definition.get(f"{metric}TargetBasisPoints"),
            "ratchetOutcomes": {
                "currentFloorPassed": current_floor_passed,
                "baselineCapturedFloorPassed": captured_floor_passed,
                "baselineDropPassed": drop_passed,
            },
        }
    return {
        **current,
        "baselineCounters": {metric: baseline[metric] for metric in ("line", "branch")},
        "ratios": ratios,
        "denominatorDelta": {key: current[key] - baseline[key] for key in denominator_keys},
        "denominatorRatchetOutcomes": {key: current[key] >= baseline[key] for key in denominator_keys},
    }


def _measure(manifest_path: Path, policy_path: Path, source_commit: str) -> dict[str, Any]:
    manifest, policy, entries, root = _load_run(manifest_path, policy_path, source_commit)
    source_lines: dict[tuple[str, str], dict[int, tuple[int, int, int, int]]] = {}
    source_classes: dict[tuple[str, str], set[str]] = {}
    report_measurements: list[dict[str, Any]] = []
    report_evidence: list[dict[str, Any]] = []
    for report in policy["reports"]:
        report_id = report["id"]
        entry = entries[report_id]
        parsed = validate_entry_evidence(root, entry)
        identity_to_path: dict[tuple[str, str], str] = {}
        for path, record in _entry_sources(entry).items():
            identity = (record["package"].replace(".", "/"), record["filename"])
            if identity in identity_to_path:
                raise CoverageError(f"package/filename collision in {report_id}: {identity}")
            identity_to_path[identity] = path
        authored_counters(parsed, set(identity_to_path))
        for identity, path in identity_to_path.items():
            source_lines[(report_id, path)] = parsed.sources[identity]
            source_classes[(report_id, path)] = parsed.source_classes.get(identity, set())
            source_bytes = (root / path).read_bytes()
            for class_name in source_classes[(report_id, path)]:
                if not _class_belongs_to_authored_file(Path(path), source_bytes, class_name):
                    raise CoverageError(
                        f"ambiguous external-inline source/class collision: {report_id} {path} <- {class_name}",
                    )
        topology = {
            "reportId": report_id,
            "module": report["module"],
            "platform": report["platform"],
            "variant": report["variant"],
            "testTask": report["testTask"],
            "sourceRoots": report["sourceRoots"],
            "testSourceRoots": report["testSourceRoots"],
            "ownedSourceRoots": report["ownedSourceRoots"],
        }
        report_measurements.append({
            "reportId": report_id,
            "inputIdentitySha256": _sha256_bytes(canonical_json_bytes(topology)),
            "measuredTestInputIdentitySha256": entry["testInputIdentitySha256"],
            "measuredTestSources": entry["testSources"],
        })
        _, excluded = authored_counters(parsed, set(identity_to_path))
        report_evidence.append(_report_audit_evidence(
            report,
            entry,
            [f"{package}/{source}" for package, source in excluded],
        ))
    units: list[dict[str, Any]] = []
    ownership = {
        path: report["id"] for report in policy["reports"]
        for path in _entry_sources(entries[report["id"]]) if _under_roots(path, report["ownedSourceRoots"])
    }
    for unit in policy["units"]:
        unit_sources = unit["sources"] if unit["selection"] == "exact" else sorted(
            path for report_id in unit["reportIds"] for path, owner in ownership.items() if owner == report_id
        )
        selected = [(path, source_lines[(ownership[path], path)]) for path in unit_sources]
        all_lines = [counters for _, lines in selected for counters in lines.values()]
        line_missed = sum(1 for item in all_lines if item[1] == 0 and item[0] > 0)
        line_covered = sum(1 for item in all_lines if item[1] > 0)
        branch_missed = sum(item[2] for item in all_lines)
        branch_covered = sum(item[3] for item in all_lines)
        attributable_classes = {
            (ownership[path], class_name)
            for path in unit_sources
            for class_name in source_classes[(ownership[path], path)]
        }
        units.append({
            "id": unit["id"],
            "line": {"covered": line_covered, "missed": line_missed, "total": line_covered + line_missed},
            "branch": {"covered": branch_covered, "missed": branch_missed, "total": branch_covered + branch_missed},
            "authoredSourceCount": len(unit_sources),
            "executableLineCount": line_covered + line_missed,
            "branchCount": branch_covered + branch_missed,
            "classCount": len(attributable_classes),
        })
    return {
        "schemaVersion": 1,
        "sourceCommit": source_commit,
        "policySha256": _sha256_bytes(policy_path.read_bytes()),
        "manifestSchemaVersion": manifest["schemaVersion"],
        "reports": sorted(report_measurements, key=lambda item: item["reportId"]),
        "units": sorted(units, key=lambda item: item["id"]),
        "reportEvidence": sorted(report_evidence, key=lambda item: item["reportId"]),
    }


def _write_atomic(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp")
    temporary.write_bytes(canonical_json_bytes(payload) + b"\n")
    temporary.replace(path)


def _capture(
    measurement: dict[str, Any],
    policy: dict[str, Any],
    root: Path,
    predecessor_commit: str | None = None,
) -> dict[str, Any]:
    if policy["enforcementMode"] != "blocking":
        raise CoverageError("capture requires blocking policy")
    measured = {unit["id"]: unit for unit in measurement["units"]}
    units: list[dict[str, Any]] = []
    for policy_unit in policy["units"]:
        current = dict(measured[policy_unit["id"]])
        if policy_unit["family"] not in {"rendering", "tool", "assembly"}:
            validate_unit_floor_schema(policy_unit, "blocking", current["branch"]["total"])
            if current["line"]["total"] and ratio_below_basis_points(
                current["line"]["covered"], current["line"]["total"], policy_unit["lineFloorBasisPoints"],
            ):
                raise CoverageError(f"line floor exceeds measured result for {policy_unit['id']}")
            current["lineFloorBasisPointsAtCapture"] = policy_unit["lineFloorBasisPoints"]
            if current["branch"]["total"]:
                if ratio_below_basis_points(
                    current["branch"]["covered"], current["branch"]["total"], policy_unit["branchFloorBasisPoints"],
                ):
                    raise CoverageError(f"branch floor exceeds measured result for {policy_unit['id']}")
                current["branchFloorBasisPointsAtCapture"] = policy_unit["branchFloorBasisPoints"]
        units.append(current)
    baseline_path = root / "config/quality/coverage-baseline.json"
    tree_has_baseline = subprocess.run(
        ["git", "cat-file", "-e", f"{measurement['sourceCommit']}:config/quality/coverage-baseline.json"],
        cwd=root,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    ).returncode == 0
    if tree_has_baseline and predecessor_commit != measurement["sourceCommit"]:
        raise CoverageError("baseline replacement requires predecessor commit equal to sourceCommit")
    if not tree_has_baseline and predecessor_commit is not None:
        raise CoverageError("first baseline must not declare a predecessor")
    predecessor = None
    if tree_has_baseline:
        old_baseline_bytes = _git_file_bytes(root, measurement["sourceCommit"], "config/quality/coverage-baseline.json")
        old_policy_bytes = _git_file_bytes(root, measurement["sourceCommit"], "config/quality/coverage-policy.json")
        predecessor = {
            "commit": measurement["sourceCommit"],
            "baselineBlobSha256": hashlib.sha256(old_baseline_bytes).hexdigest(),
            "policyBlobSha256": hashlib.sha256(old_policy_bytes).hexdigest(),
        }
    candidate = {
        "schemaVersion": measurement["schemaVersion"],
        "sourceCommit": measurement["sourceCommit"],
        "policySha256": measurement["policySha256"],
        "manifestSchemaVersion": measurement["manifestSchemaVersion"],
        "predecessor": predecessor,
        "reports": sorted(measurement["reports"], key=lambda item: item["reportId"]),
        "units": sorted(units, key=lambda item: item["id"]),
    }
    historical = validate_baseline_lineage(root, candidate, measurement["sourceCommit"])
    if historical is not None:
        validate_predecessor_floor_transitions(
            historical,
            candidate,
            policy["maximumFloorRaiseBasisPoints"],
        )
    return candidate


def _verify_current(measurement: dict[str, Any], policy: dict[str, Any], baseline: dict[str, Any]) -> list[str]:
    violations: list[str] = []
    if policy["enforcementMode"] != "blocking":
        raise CoverageError("verify requires blocking policy")
    validate_baseline_schema(baseline)
    if baseline.get("policySha256") != measurement["policySha256"]:
        raise CoverageError("baseline policy hash mismatch")
    baseline_report_ids = {
        item["reportId"]: item["inputIdentitySha256"] for item in baseline.get("reports", [])
    }
    current_report_ids = {
        item["reportId"]: item["inputIdentitySha256"] for item in measurement["reports"]
    }
    if baseline_report_ids != current_report_ids:
        raise CoverageError("baseline report topology identity differs from current evidence")
    current_units = {item["id"]: item for item in measurement["units"]}
    baseline_units = {item["id"]: item for item in baseline.get("units", [])}
    policy_units = {item["id"]: item for item in policy["units"]}
    if set(current_units) != set(baseline_units) or set(current_units) != set(policy_units):
        raise CoverageError("baseline/current/policy unit topology mismatch")
    for unit_id, current in current_units.items():
        old = baseline_units[unit_id]
        definition = policy_units[unit_id]
        for key in ("authoredSourceCount", "executableLineCount", "branchCount", "classCount"):
            if current[key] < old[key]:
                violations.append(f"{unit_id} denominator decreased: {key} {old[key]} -> {current[key]}")
        if definition["family"] in {"rendering", "tool", "assembly"}:
            continue
        for metric, floor_key in (("line", "lineFloorBasisPoints"), ("branch", "branchFloorBasisPoints")):
            now = current[metric]
            before = old[metric]
            if now["total"] == 0:
                if before["total"] != 0:
                    violations.append(f"{unit_id} {metric} became N/A")
                continue
            floor = definition[floor_key]
            if ratio_below_basis_points(now["covered"], now["total"], floor):
                violations.append(f"{unit_id} {metric} is below floor {floor}bp")
            if before["total"] and baseline_drop_exceeded(
                now["covered"], now["total"], before["covered"], before["total"],
                policy["maximumBaselineDropBasisPoints"],
            ):
                violations.append(f"{unit_id} {metric} dropped more than 50bp from baseline")
            if before["total"] and ratio_below_basis_points(before["covered"], before["total"], floor):
                violations.append(f"{unit_id} baseline {metric} is below policy floor {floor}bp")
            captured_key = f"{metric}FloorBasisPointsAtCapture"
            if before["total"] and captured_key in old and ratio_below_basis_points(
                before["covered"], before["total"], old[captured_key],
            ):
                violations.append(f"{unit_id} baseline {metric} is below captured floor {old[captured_key]}bp")
            if captured_key in old and floor != old[captured_key]:
                violations.append(f"{unit_id} {metric} floor differs from captured baseline")
    return violations


def _hardened_diff(root: Path, merge_base: str) -> dict[str, ChangedFile]:
    with tempfile.TemporaryDirectory(prefix="gasstation-coverage-diff-") as directory:
        attributes = Path(directory, "attributes")
        attributes.write_bytes(b"")
        configuration = [
            "-c", "core.quotePath=true",
            "-c", f"core.attributesFile={attributes}",
            "-c", "diff.noPrefix=false",
            "-c", "diff.mnemonicPrefix=false",
            "-c", "diff.srcPrefix=a/",
            "-c", "diff.dstPrefix=b/",
            "-c", "diff.indentHeuristic=false",
            "-c", "diff.interHunkContext=0",
            "-c", "diff.relative=false",
            "-c", "diff.renameLimit=0",
        ]
        common = [
            "--src-prefix=a/", "--dst-prefix=b/", "--no-relative", "--no-indent-heuristic",
            "--inter-hunk-context=0", "--text", "--no-ext-diff", "--no-textconv",
            "--diff-algorithm=myers", "--find-renames=50%", "-l0", "--diff-filter=ACMR",
            f"{merge_base}...HEAD", "--",
        ]
        environment = os.environ.copy()
        environment["GIT_ATTR_NOSYSTEM"] = "1"
        patch = subprocess.run(
            ["git", *configuration, "diff", "--unified=0", "--no-color", *common],
            cwd=root, env=environment, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        ).stdout
        status = subprocess.run(
            ["git", *configuration, "diff", "--name-status", "-z", *common],
            cwd=root, env=environment, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        ).stdout
    preliminary = parse_zero_context_diff(status, patch)
    old_ids = _git_blob_ids(
        root,
        merge_base,
        (change.old_path for change in preliminary.values() if change.old_path is not None),
    )
    new_ids = _git_blob_ids(root, "HEAD", preliminary)
    old_blobs = _batch_git_blobs(root, old_ids)
    new_blobs = _batch_git_blobs(root, new_ids)
    required: set[str] = set()
    for path, change in preliminary.items():
        if path not in new_blobs:
            raise CoverageError(f"authoritative changed path has no HEAD blob: {path}")
        old_bytes = b"" if change.old_path is None else old_blobs.get(change.old_path)
        if old_bytes is None:
            raise CoverageError(f"authoritative changed path has no base blob: {change.old_path}")
        if new_blobs[path] != old_bytes and new_blobs[path]:
            required.add(path)
    blob_contents = {
        path: (b"" if change.old_path is None else old_blobs[change.old_path], new_blobs[path])
        for path, change in preliminary.items()
        if path in required
    }
    return parse_zero_context_diff(
        status,
        patch,
        changed_blob_paths=required,
        blob_contents=blob_contents,
    )


def _changed_coverage(
    manifest_path: Path,
    policy: dict[str, Any],
    entries: dict[str, Any],
    root: Path,
    event: str,
    base_ref: str | None,
) -> tuple[list[str], list[dict[str, Any]], str | None]:
    if event not in {"pull-request", "main", "tag", "local"}:
        raise CoverageError(f"unknown coverage event: {event}")
    if event == "tag":
        return [], [], None
    if not base_ref:
        if event == "pull-request":
            raise CoverageError("pull-request coverage requires an explicit base ref")
        return [], [], None
    if not re.fullmatch(r"[0-9a-f]{40}", base_ref) or base_ref == "0" * 40:
        if event == "main":
            return [], [], None
        raise CoverageError("coverage base ref must be one non-zero 40-hex commit")
    try:
        _git(root, "cat-file", "-e", f"{base_ref}^{{commit}}")
        merge_base = _git(root, "merge-base", base_ref, "HEAD").decode().strip()
    except CoverageError:
        if event == "main":
            return [], [], None
        raise
    changes = _hardened_diff(root, merge_base)
    authored_paths = {
        record["path"] for entry in entries.values() for record in entry["sources"]
    }
    changed_authored = {path: change for path, change in changes.items() if path in authored_paths}
    violations: list[str] = []
    details: list[dict[str, Any]] = []
    line_floor = policy["changedThresholds"]["lineBasisPoints"]
    branch_floor = policy["changedThresholds"]["branchBasisPoints"]
    for report_id, entry in sorted(entries.items()):
        selected_paths = sorted(set(_entry_sources(entry)).intersection(changed_authored))
        if not selected_paths:
            continue
        parsed = parse_jacoco_xml((root / entry["xmlReport"]).read_bytes(), report_id)
        line_covered = line_missed = branch_covered = branch_missed = 0
        source_lines: list[str] = []
        for path in selected_paths:
            record = _entry_sources(entry)[path]
            identity = (record["package"].replace(".", "/"), record["filename"])
            if identity not in parsed.sources:
                raise CoverageError(f"changed authored source missing from XML: {report_id} {path}")
            counters = changed_counters(parsed.sources[identity], changed_authored[path].new_lines)
            source_lines.extend(
                f"{path}:{number}"
                for number in sorted(changed_authored[path].new_lines.intersection(parsed.sources[identity]))
            )
            line_covered += counters["line"]["covered"]
            line_missed += counters["line"]["missed"]
            if counters["branch"] is not None:
                branch_covered += counters["branch"]["covered"]
                branch_missed += counters["branch"]["missed"]
        line_total = line_covered + line_missed
        branch_total = branch_covered + branch_missed
        if line_total and ratio_below_basis_points(line_covered, line_total, line_floor):
            violations.append(f"{report_id} changed line coverage is below {line_floor}bp")
        if branch_total and ratio_below_basis_points(branch_covered, branch_total, branch_floor):
            violations.append(f"{report_id} changed branch coverage is below {branch_floor}bp")
        details.append({
            "reportId": report_id,
            "line": {"covered": line_covered, "missed": line_missed, "total": line_total},
            "branch": None if branch_total == 0 else {
                "covered": branch_covered, "missed": branch_missed, "total": branch_total,
            },
            "sourceLines": sorted(source_lines),
        })
    return violations, details, merge_base


def _changed_violations(
    manifest_path: Path,
    policy: dict[str, Any],
    entries: dict[str, Any],
    root: Path,
    event: str,
    base_ref: str | None,
) -> list[str]:
    return _changed_coverage(manifest_path, policy, entries, root, event, base_ref)[0]


def _main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    for command in ("measure", "capture", "verify"):
        subparser = subparsers.add_parser(command)
        subparser.add_argument("--manifest", type=Path, required=True)
        subparser.add_argument("--policy", type=Path, required=True)
        subparser.add_argument("--source-commit", required=True)
        subparser.add_argument("--output", type=Path, required=True)
        if command == "verify":
            subparser.add_argument("--baseline", type=Path, required=True)
            subparser.add_argument("--event", choices=("pull-request", "main", "tag", "local"), required=True)
            subparser.add_argument("--base-ref")
        if command == "capture":
            subparser.add_argument("--predecessor-commit")
    args = parser.parse_args()
    violations: list[str] = []
    changed_coverage: list[dict[str, Any]] = []
    report_evidence: list[dict[str, Any]] = []
    unit_evidence: list[dict[str, Any]] = []
    merge_base: str | None = None
    try:
        measurement = _measure(args.manifest, args.policy, args.source_commit)
        report_evidence = measurement.get("reportEvidence", [])
        unit_evidence = measurement["units"]
        policy = validate_policy(read_json(args.policy))
        if args.command == "measure":
            if policy["enforcementMode"] != "measurement":
                raise CoverageError("measure requires measurement policy")
            _write_atomic(args.output, measurement)
            return 0
        root = Path(_git(args.manifest.resolve().parents[3], "rev-parse", "--show-toplevel").decode().strip())
        if args.command == "capture":
            _write_atomic(
                args.output,
                _capture(measurement, policy, root, args.predecessor_commit),
            )
            return 0
        baseline = read_json(args.baseline)
        historical = validate_baseline_lineage(root, baseline, args.source_commit)
        if historical is not None:
            validate_predecessor_floor_transitions(
                historical,
                baseline,
                policy["maximumFloorRaiseBasisPoints"],
            )
        violations.extend(_verify_current(measurement, policy, baseline))
        baseline_units = {unit["id"]: unit for unit in baseline["units"]}
        policy_units = {unit["id"]: unit for unit in policy["units"]}
        unit_evidence = [
            _unit_audit_evidence(
                unit,
                baseline_units[unit["id"]],
                policy_units[unit["id"]],
                policy["maximumBaselineDropBasisPoints"],
            )
            for unit in measurement["units"]
        ]
        _, _, entries, root = _load_run(args.manifest, args.policy, args.source_commit)
        changed_violations, changed_coverage, merge_base = _changed_coverage(
            args.manifest, policy, entries, root, args.event, args.base_ref,
        )
        violations.extend(changed_violations)
    except CoverageError as error:
        violations.append(str(error))
    if args.command == "capture":
        for violation in violations:
            print(f"coverage violation: {violation}")
        return 1
    write_summary(
        args.output,
        source_commit=args.source_commit,
        event=getattr(args, "event", args.command),
        status="fail" if violations else "pass",
        violations=violations,
        artifacts=["build/reports/coverage/report-manifest.json", "config/quality/coverage-policy.json"] +
        (["config/quality/coverage-baseline.json"] if hasattr(args, "baseline") else []),
        base_ref=getattr(args, "base_ref", None),
        merge_base=merge_base,
        changed_coverage=changed_coverage,
        reports=report_evidence,
        units=unit_evidence,
    )
    if violations:
        for violation in violations:
            print(f"coverage violation: {violation}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(_main())
