from __future__ import annotations

import json
import os
import re
from pathlib import Path
from typing import Any, Iterable, Mapping
from urllib.parse import urlsplit

from .contracts import BuildInputError, canonical_json_bytes, sha256_file


_SECRET_KEY = re.compile(r"(?:api[_-]?key|authorization|cookie|credential|password|secret|token)", re.IGNORECASE)
_SECRET_VALUE = re.compile(
    r"(?:github_pat_[A-Za-z0-9_]{10,}|gh[pousr]_[A-Za-z0-9]{10,}|"
    r"\bBearer\s+[A-Za-z0-9._~+/=-]{8,}|\bsk-[A-Za-z0-9_-]{12,})",
    re.IGNORECASE,
)
_ABSOLUTE_USER_PATH = re.compile(
    r"(?:^|[\s=:])(?:/(?!/)|[A-Za-z]:[\\/])",
)


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    value: dict[str, Any] = {}
    for key, item in pairs:
        if key in value:
            raise BuildInputError(f"duplicate receipt key: {key}")
        value[key] = item
    return value


def _validate_receipt_value(value: Any, *, key: str = "receipt") -> None:
    if value is None or isinstance(value, (bool, int)):
        return
    if isinstance(value, float):
        raise BuildInputError(f"{key}: floating-point receipt values are forbidden")
    if isinstance(value, str):
        if "\x00" in value or _SECRET_VALUE.search(value):
            raise BuildInputError(f"{key}: secret-like receipt value is forbidden")
        if _ABSOLUTE_USER_PATH.search(value):
            raise BuildInputError(f"{key}: absolute user/temp paths are forbidden")
        parsed = urlsplit(value)
        if parsed.scheme and (parsed.username or parsed.password or parsed.query):
            raise BuildInputError(f"{key}: URL userinfo/query is forbidden")
        return
    if isinstance(value, list):
        for index, item in enumerate(value):
            _validate_receipt_value(item, key=f"{key}[{index}]")
        return
    if isinstance(value, dict):
        for child_key, item in value.items():
            if not isinstance(child_key, str):
                raise BuildInputError(f"{key}: receipt keys must be strings")
            if _SECRET_KEY.search(child_key) and not (item is None or item is False or item == ""):
                raise BuildInputError(f"{key}.{child_key}: secret-bearing key is forbidden")
            _validate_receipt_value(item, key=f"{key}.{child_key}")
        return
    raise BuildInputError(f"{key}: unsupported receipt value type")


def canonical_receipt(value: Mapping[str, Any]) -> bytes:
    materialized = dict(value)
    _validate_receipt_value(materialized)
    return canonical_json_bytes(materialized)


def load_canonical_receipt(path: Path) -> dict[str, Any]:
    try:
        raw = path.read_bytes()
        text = raw.decode("utf-8")
    except (OSError, UnicodeDecodeError) as error:
        raise BuildInputError(f"receipt is not readable UTF-8: {error}") from error
    try:
        value = json.loads(text, object_pairs_hook=_reject_duplicate_keys)
    except (json.JSONDecodeError, BuildInputError) as error:
        if isinstance(error, BuildInputError):
            raise
        raise BuildInputError(f"receipt JSON is malformed: {error}") from error
    if not isinstance(value, dict):
        raise BuildInputError("receipt must be a JSON object")
    _validate_receipt_value(value)
    if raw != canonical_json_bytes(value):
        raise BuildInputError("receipt must be canonical JSON with one trailing newline")
    return value


def write_canonical_receipt(path: Path, value: Mapping[str, Any]) -> None:
    data = canonical_receipt(value)
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() or path.is_symlink():
        raise BuildInputError("receipt output already exists; stale receipts may not be overwritten")
    temporary = path.parent / f".{path.name}.partial-{os.getpid()}"
    if temporary.exists() or temporary.is_symlink():
        raise BuildInputError("stale receipt partial file exists")
    try:
        with temporary.open("xb") as output:
            output.write(data)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def relative_evidence_rows(root: Path, paths: Iterable[Path]) -> list[dict[str, Any]]:
    canonical_root = root.resolve()
    rows: list[dict[str, Any]] = []
    seen: set[str] = set()
    for candidate in paths:
        path = candidate if candidate.is_absolute() else root / candidate
        relative_parts = path.relative_to(root).parts if path.is_relative_to(root) else ()
        has_symlink_component = any(
            (root.joinpath(*relative_parts[:index])).is_symlink()
            for index in range(1, len(relative_parts) + 1)
        )
        if path.is_symlink() or has_symlink_component:
            raise BuildInputError("evidence file may not be a symlink")
        try:
            resolved = path.resolve(strict=True)
        except OSError as error:
            raise BuildInputError(f"evidence file is missing or unreadable: {candidate.name}") from error
        if not resolved.is_relative_to(canonical_root) or not resolved.is_file():
            raise BuildInputError("evidence file must be a regular repository file")
        relative = resolved.relative_to(canonical_root).as_posix()
        if relative in seen:
            raise BuildInputError(f"duplicate evidence path: {relative}")
        seen.add(relative)
        rows.append(
            {
                "path": relative,
                "sha256": sha256_file(resolved),
                "size": resolved.stat().st_size,
            },
        )
    return sorted(rows, key=lambda row: row["path"])


def parse_os_release(path: Path = Path("/etc/os-release")) -> dict[str, str]:
    allowed = {"ID", "ID_LIKE", "NAME", "PRETTY_NAME", "VERSION", "VERSION_ID"}
    result: dict[str, str] = {}
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError:
        return result
    for line in lines:
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, raw_value = line.split("=", 1)
        if key not in allowed:
            continue
        value = raw_value.strip().strip('"').strip("'")
        if "\n" not in value and "\r" not in value:
            result[key] = value
    return dict(sorted(result.items()))
