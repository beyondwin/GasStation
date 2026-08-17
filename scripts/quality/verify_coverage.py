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


@dataclass(frozen=True)
class ParsedJacoco:
    report_id: str
    sources: dict[tuple[str, str], dict[int, tuple[int, int, int, int]]]
    semantic_sha256: str


def _strict_nonnegative_int(value: str | None, label: str) -> int:
    if value is None or not re.fullmatch(r"0|[1-9][0-9]*", value):
        raise CoverageError(f"{label} must be a canonical non-negative integer")
    return int(value)


def parse_jacoco_xml(xml_bytes: bytes, report_id: str) -> ParsedJacoco:
    try:
        root = ET.fromstring(xml_bytes)
    except ET.ParseError as error:
        raise CoverageError(f"invalid JaCoCo XML for {report_id}: {error}") from error
    if root.tag != "report":
        raise CoverageError(f"JaCoCo XML root must be report for {report_id}")
    sources: dict[tuple[str, str], dict[int, tuple[int, int, int, int]]] = {}
    identities: list[dict[str, Any]] = []
    for package in root.findall("package"):
        package_name = package.get("name")
        if package_name is None:
            raise CoverageError(f"package without name in {report_id}")
        for source in package.findall("sourcefile"):
            source_name = source.get("name")
            if source_name is None:
                raise CoverageError(f"sourcefile without name in {report_id}")
            identity = (package_name, source_name)
            if identity in sources:
                raise CoverageError(f"duplicate XML identity {package_name}/{source_name}")
            lines: dict[int, tuple[int, int, int, int]] = {}
            for line in source.findall("line"):
                number = _strict_nonnegative_int(line.get("nr"), "line number")
                if number == 0 or number in lines:
                    raise CoverageError(f"duplicate XML identity {package_name}/{source_name}:{number}")
                counters = tuple(
                    _strict_nonnegative_int(line.get(attribute), f"{identity}:{number} {attribute}")
                    for attribute in ("mi", "ci", "mb", "cb")
                )
                lines[number] = counters  # type: ignore[assignment]
                identities.append(
                    {"package": package_name, "source": source_name, "line": number, "counters": list(counters)},
                )
            sources[identity] = lines
    digest = hashlib.sha256(canonical_json_bytes({"reportId": report_id, "lines": identities})).hexdigest()
    return ParsedJacoco(report_id=report_id, sources=sources, semantic_sha256=digest)


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


def parse_zero_context_diff(
    name_status_z: bytes,
    patch: bytes,
    *,
    changed_blob_paths: set[str] | None = None,
) -> dict[str, ChangedFile]:
    parts = name_status_z.split(b"\0")
    changes: dict[str, ChangedFile] = {}
    index = 0
    while index < len(parts) and parts[index]:
        status_token = parts[index].decode("ascii")
        index += 1
        status = status_token[0]
        if status in {"R", "C"}:
            old_path = _decode_path(parts[index])
            new_path = _decode_path(parts[index + 1])
            index += 2
        else:
            old_path = None if status == "A" else _decode_path(parts[index])
            new_path = _decode_path(parts[index])
            index += 1
        changes[new_path] = ChangedFile(status=status, old_path=old_path, new_path=new_path, new_lines=set())
    current: ChangedFile | None = None
    for raw_line in patch.replace(b"\r\n", b"\n").splitlines():
        if raw_line.startswith(b"diff --git "):
            current = None
        elif raw_line.startswith(b"+++ "):
            token = raw_line[4:]
            if token == b"/dev/null":
                continue
            path = _decode_path(token)
            if path.startswith("b/"):
                path = path[2:]
            current = changes.get(path)
        elif raw_line.startswith(b"@@ ") and current is not None:
            match = re.match(rb"@@ -[0-9]+(?:,[0-9]+)? \+([0-9]+)(?:,([0-9]+))? @@", raw_line)
            if not match:
                raise CoverageError(f"invalid zero-context hunk header: {raw_line!r}")
            start = int(match.group(1))
            count = int(match.group(2)) if match.group(2) is not None else 1
            current.new_lines.update(range(start, start + count))
            current.hunk_count += 1
    required = changed_blob_paths if changed_blob_paths is not None else set()
    for path in sorted(required):
        if path in changes and changes[path].status != "D" and changes[path].hunk_count == 0:
            raise CoverageError(f"changed blob has no hunk: {path}")
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
) -> None:
    payload = {
        "schemaVersion": 1,
        "sourceCommit": source_commit,
        "event": event,
        "status": status,
        "violations": sorted(set(violations)),
        "artifacts": sorted(set(artifacts)),
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
    if policy["schemaVersion"] != 1:
        raise CoverageError("coverage policy schemaVersion must equal 1")
    mode = policy["enforcementMode"]
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
        if unit["id"] in unit_ids:
            raise CoverageError(f"duplicate policy unit: {unit['id']}")
        unit_ids.add(unit["id"])
        if unit["selection"] not in {"all", "exact"}:
            raise CoverageError(f"invalid selection for {unit['id']}")
        if unit["family"] not in {"contract", "data", "state", "assembly", "rendering", "tool"}:
            raise CoverageError(f"invalid family for {unit['id']}")
        if not isinstance(unit["sources"], list) or unit["sources"] != sorted(set(unit["sources"])):
            raise CoverageError(f"{unit['id']} sources must be a sorted unique array")
        if (unit["selection"] == "all") != (unit["sources"] == []):
            raise CoverageError(f"{unit['id']} all selection requires an empty sources array")
        if not isinstance(unit["reportIds"], list) or any(item not in report_ids for item in unit["reportIds"]):
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
        for exception in policy["nonExecutableExceptions"]:
            _require_keys(exception, {"path", "sha256", "reason"}, set(), "non-executable exception")
    return policy


def _entry_sources(entry: dict[str, Any]) -> dict[str, dict[str, Any]]:
    records: dict[str, dict[str, Any]] = {}
    for record in entry["sources"]:
        path = record["path"]
        if path in records:
            raise CoverageError(f"duplicate manifest source path: {path}")
        records[path] = record
    return records


def _under_roots(path: str, roots: list[str]) -> bool:
    return any(path.startswith(root.rstrip("/") + "/") for root in roots)


def _load_run(manifest_path: Path, policy_path: Path, source_commit: str) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any], Path]:
    root = Path(_git(manifest_path.resolve().parents[3], "rev-parse", "--show-toplevel").decode().strip())
    manifest = read_json(manifest_path)
    policy = validate_policy(read_json(policy_path))
    if manifest.get("schemaVersion") != 1:
        raise CoverageError("manifest schemaVersion must equal 1")
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
        entry = read_json(path)
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


def _measure(manifest_path: Path, policy_path: Path, source_commit: str) -> dict[str, Any]:
    manifest, policy, entries, root = _load_run(manifest_path, policy_path, source_commit)
    source_lines: dict[tuple[str, str], dict[int, tuple[int, int, int, int]]] = {}
    report_measurements: list[dict[str, Any]] = []
    for report in policy["reports"]:
        report_id = report["id"]
        entry = entries[report_id]
        parsed = parse_jacoco_xml((root / entry["xmlReport"]).read_bytes(), report_id)
        identity_to_path: dict[tuple[str, str], str] = {}
        for path, record in _entry_sources(entry).items():
            identity = (record["package"].replace(".", "/"), record["filename"])
            if identity in identity_to_path:
                raise CoverageError(f"package/filename collision in {report_id}: {identity}")
            identity_to_path[identity] = path
        authored_counters(parsed, set(identity_to_path))
        for identity, path in identity_to_path.items():
            source_lines[(report_id, path)] = parsed.sources[identity]
        topology = {
            "reportId": report_id,
            "module": report["module"],
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
        units.append({
            "id": unit["id"],
            "line": {"covered": line_covered, "missed": line_missed, "total": line_covered + line_missed},
            "branch": {"covered": branch_covered, "missed": branch_missed, "total": branch_covered + branch_missed},
            "authoredSourceCount": len(unit_sources),
            "executableLineCount": line_covered + line_missed,
            "branchCount": branch_covered + branch_missed,
            "classCount": len(unit["sources"]),
        })
    return {
        "schemaVersion": 1,
        "sourceCommit": source_commit,
        "policySha256": _sha256_bytes(policy_path.read_bytes()),
        "manifestSchemaVersion": manifest["schemaVersion"],
        "reports": sorted(report_measurements, key=lambda item: item["reportId"]),
        "units": sorted(units, key=lambda item: item["id"]),
    }


def _write_atomic(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp")
    temporary.write_bytes(canonical_json_bytes(payload) + b"\n")
    temporary.replace(path)


def _capture(measurement: dict[str, Any], policy: dict[str, Any], root: Path) -> dict[str, Any]:
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
    if tree_has_baseline:
        raise CoverageError("baseline replacement requires an exact predecessor record")
    return {**measurement, "predecessor": None, "units": units}


def _verify_current(measurement: dict[str, Any], policy: dict[str, Any], baseline: dict[str, Any]) -> list[str]:
    violations: list[str] = []
    if policy["enforcementMode"] != "blocking":
        raise CoverageError("verify requires blocking policy")
    if baseline.get("schemaVersion") != 1 or baseline.get("policySha256") != measurement["policySha256"]:
        raise CoverageError("baseline policy hash mismatch")
    if baseline.get("reports") != measurement["reports"]:
        raise CoverageError("baseline report topology or test identity differs from current evidence")
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
            captured_key = f"{metric}FloorBasisPointsAtCapture"
            if captured_key in old and floor != old[captured_key]:
                violations.append(f"{unit_id} {metric} floor differs from captured baseline")
    return violations


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
    args = parser.parse_args()
    violations: list[str] = []
    try:
        measurement = _measure(args.manifest, args.policy, args.source_commit)
        policy = validate_policy(read_json(args.policy))
        if args.command == "measure":
            if policy["enforcementMode"] != "measurement":
                raise CoverageError("measure requires measurement policy")
            _write_atomic(args.output, measurement)
            return 0
        root = Path(_git(args.manifest.resolve().parents[3], "rev-parse", "--show-toplevel").decode().strip())
        if args.command == "capture":
            _write_atomic(args.output, _capture(measurement, policy, root))
            return 0
        baseline = read_json(args.baseline)
        violations.extend(_verify_current(measurement, policy, baseline))
    except CoverageError as error:
        violations.append(str(error))
    write_summary(
        args.output,
        source_commit=args.source_commit,
        event=getattr(args, "event", args.command),
        status="fail" if violations else "pass",
        violations=violations,
        artifacts=[args.manifest.as_posix(), args.policy.as_posix()] +
        ([args.baseline.as_posix()] if hasattr(args, "baseline") else []),
    )
    if violations:
        for violation in violations:
            print(f"coverage violation: {violation}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(_main())
