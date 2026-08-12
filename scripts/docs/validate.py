#!/usr/bin/env python3
"""Validate GasStation's cataloged live documentation without network access."""

from __future__ import annotations

import argparse
import json
import re
import shlex
import subprocess
import sys
from collections import Counter, deque
from pathlib import Path
from typing import Optional
from urllib.parse import unquote, urlsplit


CATALOG_PATH = "docs/documentation-catalog.json"
HUB_PATH = "docs/README.md"
STATION_DATA_POLICY_PATH = "docs/station-data-policy.json"
STATION_DATA_POLICY_CONSUMERS_PATH = "docs/station-data-policy-consumers.json"
STATION_DATA_POLICY_OWNER = "docs/offline-strategy.md"
STATION_DATA_POLICY_START = "<!-- station-data-policy:start -->"
STATION_DATA_POLICY_END = "<!-- station-data-policy:end -->"
STATION_DATA_POLICY_REFERENCE = re.compile(
    r"<!--\s*station-data-policy-ref:\s*(retry|freshness|schema|superseded)\s*-->"
)
STATION_DATA_POLICY_FIELDS = {"retry", "freshness", "schema", "superseded"}
REQUIRED_FIELDS = (
    "path",
    "kind",
    "owner",
    "authoritativeSources",
    "reviewTriggers",
    "verificationScope",
)
ALLOWED_KINDS = {"product", "contract", "runbook", "decision", "evidence", "history"}
EXPECTED_LIVE_PATHS = {
    "AGENTS.md",
    "README.md",
    "CONTRIBUTING.md",
    "CHANGELOG.md",
    ".impeccable.md",
    "docs/README.md",
    "docs/AGENTS.md",
    "docs/onboarding/developer-onboarding-guide.md",
    "docs/agent-workflow.md",
    "docs/project-reading-guide.md",
    "docs/architecture.md",
    "docs/module-contracts.md",
    "docs/state-model.md",
    "docs/offline-strategy.md",
    "docs/test-strategy.md",
    "docs/verification-matrix.md",
    "docs/security-trade-offs.md",
    "docs/deployment.md",
    "docs/performance.md",
    "docs/build-velocity.md",
    "core/database/AGENTS.md",
    "benchmark/AGENTS.md",
    "docs/adr/2026-05-18-backend-proxy-escalation.md",
}

FENCE = re.compile(r"^\s*(```|~~~)")
INLINE_CODE = re.compile(r"`[^`\n]*`")
INLINE_VALUE = re.compile(r"`([^`\n]+)`")
LINK_LABEL = re.compile(r"!?\[[^\]]*\]\(")
HEADING = re.compile(r"^\s{0,3}#{1,6}\s+(.+?)\s*#*\s*$")
COMMAND_OWNER = re.compile(r"<!--\s*command-owner:\s*([A-Za-z0-9][A-Za-z0-9_.-]*)\s*-->")
PERSONAL_HOME = re.compile(r"(?:/Users/[^/\s]+(?:/|\b)|/home/[^/\s]+(?:/|\b)|[A-Za-z]:\\Users\\[^\\\s]+(?:\\|\b))")
SECRET_ASSIGNMENT = re.compile(
    r"(?im)(?<![A-Za-z0-9_.-])([A-Za-z0-9_.-]*(?:API[_-]?KEY|TOKEN|SECRET)[A-Za-z0-9_.-]*)[ \t]*=[ \t]*([^\s#]+)"
)
CI_REFERENCE = re.compile(r"\bCI(?:\s+job)?\s+`([A-Za-z0-9_-]+)`", re.IGNORECASE)
GRADLE_TOKEN = re.compile(r"^:?[A-Za-z0-9_-]+(?::[A-Za-z0-9_-]+)+$")
REPOSITORY_TOP_LEVELS = {
    ".github", ".codex", ".claude", "app", "benchmark", "build-logic", "core",
    "data", "docs", "domain", "feature", "gradle", "scripts", "tools",
}

EXPECTED_STATION_DATA_POLICY = {
    "schemaVersion": 1,
    "contractId": "station-data-correctness-v1",
    "retry": {
        "maxRetries": 1,
        "delayMs": 500,
        "retryableReasons": ["Timeout", "Network"],
        "retryableHttpStatuses": [408, 429],
        "retryableHttpRanges": [{"minInclusive": 500, "maxInclusive": 599}],
        "nonRetryableReasons": ["InvalidPayload", "Unknown", "Cancellation", "Superseded"],
        "nonRetryableHttpRanges": [
            {"minInclusive": 400, "maxInclusive": 407},
            {"minInclusive": 409, "maxInclusive": 428},
            {"minInclusive": 430, "maxInclusive": 499},
        ],
        "unlistedHttpRetryable": False,
    },
    "freshness": {
        "storagePrecisionMs": 1,
        "fresh": {"operator": "<=", "ageMs": 300000},
        "stale": {"operator": ">", "ageMs": 300000},
        "firstStaleAgeMs": 300001,
        "timeCrossingWithoutRoomMutation": True,
        "tickerOwnership": "one_cancellable_ticker_per_atomic_snapshot",
        "metadataSubscriptionsRestartOnTimeCrossing": False,
    },
    "superseded": {
        "completion": "normal_silent",
        "forbiddenSideEffects": [
            "Retry", "FailureReport", "SnapshotWrite", "HistoryWrite", "Prune",
            "SearchRefreshed", "RetryAttempted",
        ],
    },
    "schema": {
        "exportedVersions": [
            {"version": 1, "introducingCommit": "e64634f", "room": "2.6.1", "ksp": "1.9.23-1.0.20"},
            {"version": 2, "introducingCommit": "a705fdb", "room": "2.8.4", "ksp": "2.3.6"},
            {"version": 3, "introducingCommit": "9b070ab", "room": "2.8.4", "ksp": "2.3.6"},
            {"version": 4, "introducingCommit": "014127f", "room": "2.8.4", "ksp": "2.3.6"},
            {"version": 5, "introducingCommit": "da96a5f", "room": "2.8.4", "ksp": "2.3.7"},
        ],
        "currentVersion": 5,
        "currentV5MatchesHistorical": True,
        "supportedMigrationStarts": [1, 2, 3, 4],
        "v2ToV3PriceHistory": "intentional_disposable_reset",
        "v4SuccessfulEmptyMarkerPreserved": True,
        "migrationEvidence": {
            "status": "compiled_assets_verified",
            "hostRobolectricExecuted": True,
            "instrumentedCompiled": True,
            "assetsCompared": True,
            "connectedDeviceAvailable": False,
            "connectedDeviceExecuted": False,
            "unavailableReason": "no_connected_device",
        },
    },
}


def location(path: str, line: int, message: str) -> str:
    return f"{path}:{line}: {message}"


def line_number(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def strip_fenced_code(text: str) -> str:
    output: list[str] = []
    marker: Optional[str] = None
    for line in text.splitlines(keepends=True):
        match = FENCE.match(line)
        if match and marker is None:
            marker = match.group(1)
            output.append("\n" if line.endswith("\n") else "")
        elif match and marker and match.group(1) == marker:
            marker = None
            output.append("\n" if line.endswith("\n") else "")
        elif marker:
            output.append("\n" if line.endswith("\n") else "")
        else:
            output.append(line)
    return "".join(output)


def strip_fenced_code_preserving_offsets(text: str) -> str:
    output: list[str] = []
    marker: Optional[str] = None
    for line in text.splitlines(keepends=True):
        match = FENCE.match(line)
        hidden = marker is not None or match is not None
        if match and marker is None:
            marker = match.group(1)
        elif match and marker and match.group(1) == marker:
            marker = None
        if hidden:
            newline = "\n" if line.endswith("\n") else ""
            output.append(" " * (len(line) - len(newline)) + newline)
        else:
            output.append(line)
    return "".join(output)


def strip_code(text: str) -> str:
    return INLINE_CODE.sub("", strip_fenced_code(text))


def github_slug(value: str) -> str:
    value = unquote(value).strip().lower()
    value = re.sub(r"[^\w\- ]", "", value, flags=re.UNICODE)
    return value.replace(" ", "-")


def headings(text: str) -> set[str]:
    result: set[str] = set()
    counts: Counter[str] = Counter()
    for line in strip_fenced_code(text).splitlines():
        match = HEADING.match(line)
        if not match:
            continue
        base = github_slug(INLINE_CODE.sub(lambda item: item.group(0).strip("`"), match.group(1)))
        suffix = counts[base]
        counts[base] += 1
        result.add(base if suffix == 0 else f"{base}-{suffix}")
    return result


def safe_repo_path(root: Path, raw: str) -> Optional[Path]:
    candidate = Path(unquote(raw))
    if candidate.is_absolute():
        return None
    resolved = (root / candidate).resolve()
    try:
        resolved.relative_to(root)
    except ValueError:
        return None
    return resolved


def load_catalog(root: Path) -> tuple[list[dict[str, object]], list[str]]:
    issues: list[str] = []
    catalog = root / CATALOG_PATH
    if not catalog.is_file():
        return [], [location(CATALOG_PATH, 1, "catalog missing")]
    try:
        payload = json.loads(catalog.read_text(errors="replace"))
    except json.JSONDecodeError as error:
        return [], [location(CATALOG_PATH, error.lineno, "malformed catalog JSON")]
    if (
        not isinstance(payload, dict)
        or type(payload.get("schemaVersion")) is not int
        or payload.get("schemaVersion") != 1
    ):
        issues.append(location(CATALOG_PATH, 1, "schemaVersion must be 1"))
    documents = payload.get("documents") if isinstance(payload, dict) else None
    if not isinstance(documents, list):
        return [], issues + [location(CATALOG_PATH, 1, "documents must be an array")]
    valid_entries: list[dict[str, object]] = []
    paths: list[str] = []
    for index, entry in enumerate(documents, 1):
        if not isinstance(entry, dict):
            issues.append(location(CATALOG_PATH, index, "catalog entry must be an object"))
            continue
        valid_entries.append(entry)
        path_value = entry.get("path")
        if isinstance(path_value, str) and path_value.strip():
            paths.append(path_value)
        for field in REQUIRED_FIELDS:
            value = entry.get(field)
            if value is None or value == "" or value == []:
                issues.append(location(CATALOG_PATH, index, f"required field missing or empty: {field}"))
        for string_field in ("path", "owner", "verificationScope"):
            value = entry.get(string_field)
            if value is not None and (not isinstance(value, str) or not value.strip()):
                issues.append(location(CATALOG_PATH, index, f"{string_field} must be a non-empty string"))
        if entry.get("kind") not in ALLOWED_KINDS:
            issues.append(location(CATALOG_PATH, index, f"invalid document kind: {entry.get('kind')}"))
        for list_field in ("authoritativeSources", "reviewTriggers"):
            value = entry.get(list_field)
            if value is not None and (not isinstance(value, list) or not all(isinstance(item, str) and item for item in value)):
                issues.append(location(CATALOG_PATH, index, f"{list_field} must be a non-empty string array"))
    for path, count in Counter(paths).items():
        if count > 1:
            issues.append(location(CATALOG_PATH, 1, f"duplicate catalog entry: {path}"))
    actual = set(paths)
    for path in sorted(EXPECTED_LIVE_PATHS - actual):
        issues.append(location(CATALOG_PATH, 1, f"live document missing from catalog: {path}"))
    for path in sorted(actual - EXPECTED_LIVE_PATHS):
        issues.append(location(CATALOG_PATH, 1, f"unexpected live catalog entry: {path}"))
    for path in sorted(actual):
        resolved = safe_repo_path(root, path)
        if resolved is None or not resolved.is_file():
            issues.append(location(CATALOG_PATH, 1, f"catalog path does not exist: {path}"))
    return valid_entries, issues


def markdown_link_destinations(text: str):
    """Yield Markdown inline destinations with balanced parentheses."""
    for label in LINK_LABEL.finditer(text):
        index = label.end()
        while index < len(text) and text[index].isspace():
            index += 1
        if index >= len(text):
            continue
        if text[index] == "<":
            end = text.find(">", index + 1)
            if end < 0:
                continue
            destination = text[index + 1:end]
            cursor = end + 1
        else:
            start = index
            depth = 0
            while index < len(text):
                character = text[index]
                if character == "\\":
                    index += 2
                    continue
                if character == "(":
                    depth += 1
                elif character == ")":
                    if depth == 0:
                        break
                    depth -= 1
                elif character.isspace() and depth == 0:
                    break
                index += 1
            destination = text[start:index]
            cursor = index
        while cursor < len(text) and text[cursor].isspace():
            cursor += 1
        if cursor < len(text) and text[cursor] in {'"', "'"}:
            quote = text[cursor]
            cursor += 1
            while cursor < len(text) and text[cursor] != quote:
                cursor += 2 if text[cursor] == "\\" else 1
            if cursor >= len(text):
                continue
            cursor += 1
            while cursor < len(text) and text[cursor].isspace():
                cursor += 1
        if cursor < len(text) and text[cursor] == ")":
            yield destination, label.start(), cursor + 1


def parse_links(root: Path, relative: str, text: str) -> tuple[list[tuple[str, int]], list[str]]:
    links: list[tuple[str, int]] = []
    issues: list[str] = []
    clean = strip_code(text)
    source = root / relative
    for raw, offset, _ in markdown_link_destinations(clean):
        parsed = urlsplit(raw)
        if parsed.scheme or raw.startswith("//"):
            continue
        target_part = unquote(parsed.path)
        if not target_part:
            target = source
        else:
            target = (source.parent / target_part).resolve()
        try:
            target_relative = target.relative_to(root).as_posix()
        except ValueError:
            issues.append(location(relative, line_number(clean, offset), f"link escapes repository: {raw}"))
            continue
        if not target.exists():
            issues.append(location(relative, line_number(clean, offset), f"missing link target: {target_part}"))
            continue
        links.append((target_relative, line_number(clean, offset)))
        if parsed.fragment and target.is_file() and target.suffix.lower() == ".md":
            wanted = github_slug(parsed.fragment)
            if wanted not in headings(target.read_text(errors="replace")):
                issues.append(location(relative, line_number(clean, offset), f"missing link anchor: {raw}"))
    return links, issues


def active_modules(root: Path) -> set[str]:
    settings = (root / "settings.gradle.kts").read_text(errors="replace")
    return set(re.findall(r"['\"](:[A-Za-z0-9_-]+(?::[A-Za-z0-9_-]+)*)['\"]", settings))


def ci_jobs(root: Path) -> set[str]:
    workflow = root / ".github/workflows/android.yml"
    if not workflow.is_file():
        return set()
    return set(re.findall(r"(?m)^  ([A-Za-z0-9_-]+):\s*(?:#.*)?$", workflow.read_text(errors="replace")))


def is_example_secret(value: str) -> bool:
    lowered = value.strip("'\"").lower()
    return (
        not lowered
        or lowered.startswith(("<", "${", "$"))
        or lowered.endswith(">")
        or any(word in lowered for word in ("example", "placeholder", "redacted", "changeme", "dummy"))
        or set(lowered) <= {"x", "*"}
    )


def repository_reference_issues(
    root: Path,
    relative: str,
    text: str,
    modules: set[str],
    jobs: set[str],
) -> list[str]:
    issues: list[str] = []
    for match in PERSONAL_HOME.finditer(text):
        issues.append(location(relative, line_number(text, match.start()), "personal home path"))
    for match in SECRET_ASSIGNMENT.finditer(text):
        if not is_example_secret(match.group(2)):
            issues.append(location(relative, line_number(text, match.start()), f"likely secret assignment: {match.group(1)}"))

    without_fences = strip_fenced_code(text)
    for match in INLINE_VALUE.finditer(without_fences):
        token = match.group(1).strip().strip(".,;:()[]{}")
        if "*" not in token and GRADLE_TOKEN.fullmatch(token):
            module = token if token.startswith(":") else f":{token}"
            if module not in modules:
                components = module.split(":")
                task_owner = ":".join(components[:-1])
                if task_owner not in modules:
                    issues.append(location(relative, line_number(without_fences, match.start()), f"inactive Gradle module: {module}"))
        try:
            path_token = shlex.split(token, comments=True, posix=True)[0]
        except (ValueError, IndexError):
            path_token = token.split()[0] if token.split() else ""
        path_token = path_token.split("#", 1)[0]
        first = path_token.split("/", 1)[0]
        is_generated = "/build/" in f"/{path_token}"
        is_template = any(char in path_token for char in "*$<>{}") or "YYYY-MM-DD" in path_token
        if "/" in path_token and first in REPOSITORY_TOP_LEVELS and not is_generated and not is_template:
            path = safe_repo_path(root, path_token)
            if path is None or not repository_path_exists(root, path_token):
                issues.append(location(relative, line_number(without_fences, match.start()), f"missing repository path: {path_token}"))
    for match in CI_REFERENCE.finditer(without_fences):
        if match.group(1) not in jobs:
            issues.append(location(relative, line_number(without_fences, match.start()), f"missing CI job: {match.group(1)}"))
    return issues


def repository_path_exists(root: Path, path_token: str) -> bool:
    direct = safe_repo_path(root, path_token)
    if direct is not None and direct.exists():
        return True
    parts = Path(path_token).parts
    if len(parts) < 2:
        return False
    module_root = root / parts[0]
    if len(parts) >= 3 and (root / parts[0] / parts[1]).is_dir():
        module_root = root / parts[0] / parts[1]
        suffix = Path(*parts[2:])
    else:
        suffix = Path(*parts[1:])
    source_root = module_root / "src"
    if not source_root.is_dir():
        return False
    return any(candidate.is_file() and candidate.as_posix().endswith(suffix.as_posix()) for candidate in source_root.rglob(suffix.name))


def catalog_source_issues(root: Path, entries: list[dict[str, object]]) -> list[str]:
    issues: list[str] = []
    for entry in entries:
        path = entry.get("path")
        sources = entry.get("authoritativeSources")
        if not isinstance(path, str) or not isinstance(sources, list):
            continue
        for source in sources:
            if not isinstance(source, str):
                continue
            resolved = safe_repo_path(root, source)
            if resolved is None or not resolved.exists():
                issues.append(location(CATALOG_PATH, 1, f"authoritative source does not exist for {path}: {source}"))
    return issues


def reject_duplicate_json_keys(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def strict_json_loads(payload: str) -> object:
    return json.loads(payload, object_pairs_hook=reject_duplicate_json_keys)


def validate_station_data_policy(policy: object) -> None:
    actual = json.dumps(policy, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    expected = json.dumps(EXPECTED_STATION_DATA_POLICY, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    if actual != expected:
        raise AssertionError("station data policy fields differ from the approved contract")


def parse_station_data_policy_block(text: str) -> dict[str, object]:
    if text.count(STATION_DATA_POLICY_START) != 1 or text.count(STATION_DATA_POLICY_END) != 1:
        raise AssertionError("station data policy block must occur exactly once")
    start = text.index(STATION_DATA_POLICY_START) + len(STATION_DATA_POLICY_START)
    end = text.index(STATION_DATA_POLICY_END, start)
    block = text[start:end].strip()
    if not block.startswith("```json\n") or not block.endswith("\n```"):
        raise AssertionError("station data policy block must contain one JSON fence")
    payload = block.removeprefix("```json\n").removesuffix("\n```")
    try:
        parsed = strict_json_loads(payload)
    except (json.JSONDecodeError, ValueError) as error:
        raise AssertionError(f"station data policy block is invalid JSON: {error}") from error
    if not isinstance(parsed, dict):
        raise AssertionError("station data policy block must be a JSON object")
    return parsed


def station_data_policy_issues(
    root: Path,
    entries: list[dict[str, object]],
    texts: dict[str, str],
) -> list[str]:
    issues: list[str] = []
    source_path = root / STATION_DATA_POLICY_PATH
    try:
        source_policy = strict_json_loads(source_path.read_text(encoding="utf-8"))
        validate_station_data_policy(source_policy)
    except OSError as error:
        return [location(STATION_DATA_POLICY_PATH, 1, f"station data policy unavailable: {error}")]
    except (json.JSONDecodeError, ValueError) as error:
        line = error.lineno if isinstance(error, json.JSONDecodeError) else 1
        return [location(STATION_DATA_POLICY_PATH, line, f"station data policy is invalid JSON: {error}")]
    except AssertionError as error:
        issues.append(location(STATION_DATA_POLICY_PATH, 1, str(error)))

    owners = [path for path, text in texts.items() if STATION_DATA_POLICY_START in text or STATION_DATA_POLICY_END in text]
    if owners != [STATION_DATA_POLICY_OWNER]:
        issues.append(
            location(
                STATION_DATA_POLICY_OWNER,
                1,
                f"station data policy block owner must be exactly {STATION_DATA_POLICY_OWNER}: {owners}",
            )
        )
    owner_text = texts.get(STATION_DATA_POLICY_OWNER)
    if owner_text is not None:
        try:
            rendered_policy = parse_station_data_policy_block(owner_text)
            validate_station_data_policy(rendered_policy)
            if rendered_policy != source_policy:
                raise AssertionError("offline policy block does not equal the structured policy source")
        except AssertionError as error:
            issues.append(location(STATION_DATA_POLICY_OWNER, 1, str(error)))

    catalog_owners = [
        entry.get("path")
        for entry in entries
        if isinstance(entry.get("authoritativeSources"), list)
        for source in entry["authoritativeSources"]
        if source == STATION_DATA_POLICY_PATH
    ]
    if catalog_owners != [STATION_DATA_POLICY_OWNER]:
        issues.append(
            location(
                CATALOG_PATH,
                1,
                f"structured station policy catalog owner must be exactly {STATION_DATA_POLICY_OWNER}: {catalog_owners}",
            )
        )
    return issues


def load_station_data_policy_consumers(root: Path) -> tuple[dict[str, object], list[str]]:
    path = root / STATION_DATA_POLICY_CONSUMERS_PATH
    try:
        payload = strict_json_loads(path.read_text(encoding="utf-8"))
    except OSError as error:
        return {}, [location(STATION_DATA_POLICY_CONSUMERS_PATH, 1, f"consumer manifest unavailable: {error}")]
    except (json.JSONDecodeError, ValueError) as error:
        line = error.lineno if isinstance(error, json.JSONDecodeError) else 1
        return {}, [location(STATION_DATA_POLICY_CONSUMERS_PATH, line, f"invalid consumer manifest: {error}")]
    if not isinstance(payload, dict):
        return {}, [location(STATION_DATA_POLICY_CONSUMERS_PATH, 1, "consumer manifest must be an object")]
    expected_keys = {"schemaVersion", "canonicalOwner", "canonicalAnchor", "statementMode", "consumers"}
    issues: list[str] = []
    if set(payload) != expected_keys:
        issues.append(location(STATION_DATA_POLICY_CONSUMERS_PATH, 1, "consumer manifest keys differ"))
    if type(payload.get("schemaVersion")) is not int or payload.get("schemaVersion") != 1:
        issues.append(location(STATION_DATA_POLICY_CONSUMERS_PATH, 1, "consumer schemaVersion must be 1"))
    if payload.get("canonicalOwner") != STATION_DATA_POLICY_OWNER:
        issues.append(location(STATION_DATA_POLICY_CONSUMERS_PATH, 1, "consumer canonicalOwner differs"))
    if payload.get("canonicalAnchor") != "기계-판독-정책-계약":
        issues.append(location(STATION_DATA_POLICY_CONSUMERS_PATH, 1, "consumer canonicalAnchor differs"))
    if payload.get("statementMode") != "reference_only":
        issues.append(location(STATION_DATA_POLICY_CONSUMERS_PATH, 1, "consumer statementMode differs"))
    consumers = payload.get("consumers")
    if not isinstance(consumers, dict):
        issues.append(location(STATION_DATA_POLICY_CONSUMERS_PATH, 1, "consumers must be an object"))
        return payload, issues
    for consumer, counts in consumers.items():
        if not isinstance(consumer, str) or not consumer.endswith(".md") or not isinstance(counts, dict):
            issues.append(location(STATION_DATA_POLICY_CONSUMERS_PATH, 1, "invalid consumer entry"))
            continue
        if not counts or set(counts) - STATION_DATA_POLICY_FIELDS:
            issues.append(location(STATION_DATA_POLICY_CONSUMERS_PATH, 1, f"invalid policy fields for {consumer}"))
        if any(type(count) is not int or count <= 0 for count in counts.values()):
            issues.append(location(STATION_DATA_POLICY_CONSUMERS_PATH, 1, f"invalid policy count for {consumer}"))
    return payload, issues


def resolve_policy_link(root: Path, relative: str, raw: str) -> Optional[tuple[str, str]]:
    parsed = urlsplit(raw)
    if parsed.scheme or parsed.netloc or raw.startswith("//"):
        return None
    source = root / relative
    target_part = unquote(parsed.path)
    target = source if not target_part else (source.parent / target_part).resolve()
    try:
        target_relative = target.relative_to(root).as_posix()
    except ValueError:
        return None
    return target_relative, github_slug(unquote(parsed.fragment))


def station_policy_reference_issues(
    root: Path,
    entries: list[dict[str, object]],
    texts: dict[str, str],
) -> list[str]:
    manifest, issues = load_station_data_policy_consumers(root)
    consumers = manifest.get("consumers") if isinstance(manifest, dict) else None
    if not isinstance(consumers, dict):
        return issues
    live_paths = {entry.get("path") for entry in entries if isinstance(entry.get("path"), str)}
    canonical_owner = manifest.get("canonicalOwner")
    canonical_anchor = github_slug(str(manifest.get("canonicalAnchor", "")))
    manifest_catalog_owners = [
        entry.get("path")
        for entry in entries
        if isinstance(entry.get("authoritativeSources"), list)
        for source in entry["authoritativeSources"]
        if source == STATION_DATA_POLICY_CONSUMERS_PATH
    ]
    if manifest_catalog_owners != [canonical_owner]:
        issues.append(
            location(
                CATALOG_PATH,
                1,
                f"station policy consumer manifest catalog owner must be exactly {canonical_owner}",
            )
        )

    actual: dict[str, Counter[str]] = {}
    for path, text in texts.items():
        matches = list(STATION_DATA_POLICY_REFERENCE.finditer(strip_fenced_code_preserving_offsets(text)))
        if matches:
            actual[path] = Counter(match.group(1) for match in matches)
        for match in matches:
            line_no = line_number(text, match.start())
            line_start = text.rfind("\n", 0, match.start()) + 1
            line_end = text.find("\n", match.end())
            if line_end < 0:
                line_end = len(text)
            suffix = text[match.end():line_end].strip()
            links = list(markdown_link_destinations(suffix))
            policy = match.group(1)
            reference_only = (
                len(links) == 1
                and links[0][1] == 0
                and links[0][2] == len(suffix)
                and f"`{policy}`" in suffix[: suffix.find("](")]
            )
            if not reference_only:
                issues.append(location(path, line_no, "station data policy marker must be a reference-only statement"))
                continue
            resolved = resolve_policy_link(root, path, links[0][0])
            if resolved != (canonical_owner, canonical_anchor):
                issues.append(location(path, line_no, "station data policy link must resolve to the canonical owner and anchor"))

    for path in sorted(set(actual) - set(consumers)):
        issues.append(location(path, 1, "unregistered station data policy consumer"))
    for path, expected in consumers.items():
        if path not in live_paths or path not in texts:
            issues.append(location(STATION_DATA_POLICY_CONSUMERS_PATH, 1, f"consumer is not cataloged live Markdown: {path}"))
            continue
        expected_counter = Counter(expected)
        if actual.get(path, Counter()) != expected_counter:
            issues.append(location(path, 1, f"station data policy marker counts differ: expected {dict(expected_counter)}"))
    return issues


def station_policy_claim_issues(texts: dict[str, str]) -> list[str]:
    """Target high-risk duplicate values; structural references remain the primary gate."""
    issues: list[str] = []
    for path, text in texts.items():
        if path == STATION_DATA_POLICY_OWNER:
            continue
        prose = strip_fenced_code(text)
        for line_no, line in enumerate(prose.splitlines(), 1):
            if STATION_DATA_POLICY_REFERENCE.search(line):
                continue
            normalized = INLINE_CODE.sub(lambda match: match.group(0).strip("`"), line).casefold()
            timeout_category = "timeout" in normalized or "타임아웃" in normalized
            network_category = "network" in normalized or "네트워크" in normalized or re.search(
                r"통신\s*장애", normalized
            )
            exclusive = any(token in normalized for token in ("only", "한해", "경우에만"))
            retry_action = any(token in normalized for token in ("retry", "재시도", "더 요청"))
            bounded = any(
                token in normalized
                for token in ("1회", "한 번", "한번", "한 차례", "500ms", "500 ms", "반 초")
            )
            if timeout_category and network_category and exclusive and retry_action and bounded:
                issues.append(location(path, line_no, "duplicate automatic retry policy claim"))

            fresh_state = "fresh" in normalized or "최신" in normalized
            stale_state = "stale" in normalized or "오래된" in normalized
            boundary = any(
                re.search(pattern, normalized)
                for pattern in (r"300\s*초", r"5\s*분", r"300000\s*ms", r"300001\s*ms")
            )
            if fresh_state and stale_state and boundary:
                issues.append(location(path, line_no, "duplicate freshness boundary claim"))
    return issues


def navigation_issues(live_paths: set[str], graph: dict[str, set[str]]) -> list[str]:
    distances = {HUB_PATH: 0}
    queue = deque([HUB_PATH])
    while queue:
        current = queue.popleft()
        if distances[current] >= 2:
            continue
        for target in graph.get(current, set()):
            if target not in distances:
                distances[target] = distances[current] + 1
                queue.append(target)
    return [
        location(path, 1, f"not reachable from {HUB_PATH} within 2 links: {path}")
        for path in sorted(live_paths)
        if path.endswith(".md") and path != HUB_PATH and path not in distances
    ]


def owner_issues(texts: dict[str, str]) -> list[str]:
    owners: dict[str, list[tuple[str, int]]] = {}
    for path, text in texts.items():
        for match in COMMAND_OWNER.finditer(text):
            owners.setdefault(match.group(1), []).append((path, line_number(text, match.start())))
    issues: list[str] = []
    for owner, locations in owners.items():
        if len(locations) > 1:
            path, line = locations[1]
            issues.append(location(path, line, f"duplicate command-owner id: {owner}"))
    return issues


def canonical_gradle_tasks(texts: dict[str, str]) -> set[str]:
    tasks: set[str] = set()
    for text in texts.values():
        for owner_match in COMMAND_OWNER.finditer(text):
            section = text[owner_match.end():]
            next_owner = COMMAND_OWNER.search(section)
            if next_owner:
                section = section[:next_owner.start()]
            blocks = re.findall(r"(?ms)^```(?:bash|sh|shell)?\s*\n(.*?)^```\s*$", section)
            for block in blocks:
                command = block.replace("\\\n", " ")
                if "./gradlew" not in command:
                    continue
                try:
                    words = shlex.split(command, comments=True, posix=True)
                except ValueError:
                    continue
                start = words.index("./gradlew") + 1
                option_takes_value = {
                    "--build-file", "-b", "--configuration-cache-problems",
                    "--console", "--dependency-verification", "--gradle-user-home",
                    "-g", "--include-build", "--init-script", "-I", "--max-workers",
                    "--priority", "--project-cache-dir", "--project-dir", "-p",
                    "--settings-file", "-c", "--tests", "--warning-mode",
                    "--write-verification-metadata",
                }
                skip_value = False
                for token in words[start:]:
                    if skip_value:
                        skip_value = False
                        continue
                    if token in option_takes_value:
                        skip_value = True
                        continue
                    if token.startswith("-"):
                        continue
                    if "=" in token:
                        continue
                    if re.fullmatch(r":[A-Za-z0-9_-]+(?::[A-Za-z0-9_-]+)+|[A-Za-z][A-Za-z0-9_-]*", token):
                        tasks.add(token)
    return tasks


def check_gradle_tasks(root: Path, texts: dict[str, str]) -> list[str]:
    expected = canonical_gradle_tasks(texts)
    if not expected:
        return []
    wrapper = root / "gradlew"
    if not wrapper.is_file():
        return [location("gradlew", 1, "Gradle wrapper missing for task validation")]
    try:
        result = subprocess.run(
            [str(wrapper), "tasks", "--all"],
            cwd=root,
            text=True,
            capture_output=True,
            timeout=30,
        )
    except subprocess.TimeoutExpired:
        return [location("gradlew", 1, "Gradle task discovery timed out after 30 seconds")]
    except OSError as error:
        return [location("gradlew", 1, f"Gradle task discovery could not start: {error}")]
    if result.returncode:
        return [location("gradlew", 1, "Gradle task discovery failed")]
    discovered: set[str] = set()
    for line in result.stdout.splitlines():
        match = re.match(r"^\s*(:?[A-Za-z0-9_-]+(?::[A-Za-z0-9_-]+)*)\s+-\s", line)
        if match:
            discovered.add(match.group(1))
            discovered.add(match.group(1).lstrip(":"))
    return [location(CATALOG_PATH, 1, f"missing Gradle task: {task}") for task in sorted(expected) if task not in discovered and task.lstrip(":") not in discovered]


def validate(root: Path, include_gradle_tasks: bool = False) -> list[str]:
    entries, issues = load_catalog(root)
    live_paths = {entry["path"] for entry in entries if isinstance(entry.get("path"), str)}
    issues.extend(catalog_source_issues(root, entries))
    modules = active_modules(root)
    jobs = ci_jobs(root)
    texts: dict[str, str] = {}
    graph: dict[str, set[str]] = {}
    for relative in sorted(live_paths):
        path = root / relative
        if not path.is_file() or path.suffix.lower() != ".md":
            continue
        text = path.read_text(errors="replace")
        texts[relative] = text
        links, link_issues = parse_links(root, relative, text)
        graph[relative] = {target for target, _ in links}
        issues.extend(link_issues)
        issues.extend(repository_reference_issues(root, relative, text, modules, jobs))
    issues.extend(navigation_issues(live_paths, graph))
    issues.extend(owner_issues(texts))
    issues.extend(station_data_policy_issues(root, entries, texts))
    issues.extend(station_policy_reference_issues(root, entries, texts))
    issues.extend(station_policy_claim_issues(texts))
    if include_gradle_tasks:
        issues.extend(check_gradle_tasks(root, texts))
    return sorted(set(issues))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path)
    parser.add_argument("--check-gradle-tasks", action="store_true")
    args = parser.parse_args()
    if args.root:
        root = args.root.resolve()
    else:
        root = Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip()).resolve()
    issues = validate(root, include_gradle_tasks=args.check_gradle_tasks)
    if issues:
        for item in issues:
            print(f"ERROR: {item}", file=sys.stderr)
        return 1
    print("docs-validation: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
