"""Strict PIT policy primitives shared by routing, capture, and verification."""

from __future__ import annotations

import hashlib
import json
import os
import re
import struct
import subprocess
import unicodedata
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Callable, Iterable, Mapping, Sequence


class MutationPolicyError(RuntimeError):
    pass


class GitCommand(Enum):
    REV_PARSE = "rev-parse"
    FOR_EACH_REF = "for-each-ref"
    MERGE_BASE = "merge-base"
    DIFF = "diff"
    LS_TREE = "ls-tree"
    CAT_FILE = "cat-file"
    STATUS = "status"
    SHOW = "show"
    LOG = "log"
    CONFIG = "config"


_GIT_ENVIRONMENT = {
    "CI": "true",
    "GIT_ATTR_NOSYSTEM": "1",
    "GIT_CONFIG_GLOBAL": "/dev/null",
    "GIT_CONFIG_NOSYSTEM": "1",
    "GIT_CONFIG_SYSTEM": "/dev/null",
    "GIT_NO_REPLACE_OBJECTS": "1",
    "GIT_OPTIONAL_LOCKS": "0",
    "GIT_PAGER": "",
    "GIT_TERMINAL_PROMPT": "0",
    "LANG": "C",
    "LC_ALL": "C",
    "PYTHONDONTWRITEBYTECODE": "1",
    "TERM": "dumb",
    "TZ": "UTC",
}


class GitExecutor:
    """One original-object Git subprocess owner with an immutable argv prefix."""

    def __init__(
        self,
        repository_root: Path,
        *,
        git_path: Path = Path("/usr/bin/git"),
        home: Path | None = None,
        tmpdir: Path | None = None,
    ) -> None:
        root = repository_root.resolve(strict=True)
        tool = git_path.resolve(strict=True)
        if not tool.is_absolute() or root == root.parent:
            raise MutationPolicyError("Git executable and repository root must be absolute")
        self.root = root
        self.git_path = tool
        self.environment = {
            **_GIT_ENVIRONMENT,
            "HOME": str((home or (root / ".quality-git-home")).resolve(strict=False)),
            "TMPDIR": str((tmpdir or (root / ".quality-git-tmp")).resolve(strict=False)),
        }
        self.recorded_argv: list[tuple[str, ...]] = []
        self.recorded_environment: list[dict[str, str]] = []

    def _argv(self, command: GitCommand, arguments: Sequence[str]) -> tuple[str, ...]:
        if not isinstance(command, GitCommand):
            raise MutationPolicyError("Git command must use the closed GitCommand enum")
        if any("\x00" in argument for argument in arguments):
            raise MutationPolicyError("Git arguments must not contain NUL")
        return (
            str(self.git_path),
            "--no-replace-objects",
            "-C",
            str(self.root),
            command.value,
            *arguments,
        )

    def bytes(self, command: GitCommand, *arguments: str, input_bytes: bytes | None = None) -> bytes:
        argv = self._argv(command, arguments)
        environment = dict(self.environment)
        self.recorded_argv.append(argv)
        self.recorded_environment.append(environment)
        completed = subprocess.run(
            argv,
            input=input_bytes,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env=environment,
            check=False,
        )
        if completed.returncode != 0:
            diagnostic = completed.stderr.decode("utf-8", errors="replace").strip()
            raise MutationPolicyError(f"guarded Git {command.value} failed: {diagnostic}")
        return completed.stdout

    def text(self, command: GitCommand, *arguments: str) -> str:
        return self.bytes(command, *arguments).decode("utf-8", errors="strict").strip()

    def assert_original_full_history(self) -> dict[str, str]:
        replacements = self.text(GitCommand.FOR_EACH_REF, "--format=%(refname)", "refs/replace/")
        if replacements:
            raise MutationPolicyError("replacement refs are forbidden by the Git object-view policy")
        git_dir = _resolve_git_metadata_path(self.root, self.text(GitCommand.REV_PARSE, "--git-dir"))
        common_dir = _resolve_git_metadata_path(self.root, self.text(GitCommand.REV_PARSE, "--git-common-dir"))
        prohibited = {
            "legacy grafts": common_dir / "info" / "grafts",
            "object alternates": common_dir / "objects" / "info" / "alternates",
            "shallow metadata": git_dir / "shallow",
        }
        for label, path in prohibited.items():
            if path.exists():
                raise MutationPolicyError(f"{label} are forbidden by the Git object-view policy")
        return {
            "policy": "original-object-view-v1",
            "prefixSha256": hashlib.sha256("\0".join(self._argv(GitCommand.REV_PARSE, ("HEAD",))[:4]).encode()).hexdigest(),
            "inventorySha256": hashlib.sha256(b"replace=0\ngrafts=0\nalternates=0\nshallow=0\n").hexdigest(),
        }


def _resolve_git_metadata_path(root: Path, value: str) -> Path:
    candidate = Path(value)
    return candidate.resolve(strict=True) if candidate.is_absolute() else (root / candidate).resolve(strict=True)


@dataclass(frozen=True)
class MutationRecord:
    source_path: str
    source_file: str
    mutated_class: str
    mutated_method: str
    method_description: str
    line_number: int
    mutator: str
    indexes: tuple[int, ...]
    blocks: tuple[int, ...]
    status: str
    detected: bool
    tests_run: int
    killing_test: str
    description: str
    class_path: str
    class_sha256: str

    @property
    def identity(self) -> tuple[object, ...]:
        return (
            self.mutated_class,
            self.mutated_method,
            self.method_description,
            self.mutator,
            self.indexes,
        )


@dataclass(frozen=True)
class PitestReport:
    module: str
    raw_sha256: str
    semantic_sha256: str
    records: tuple[MutationRecord, ...]
    counters: dict[str, int]
    package_counters: dict[str, dict[str, int]]
    class_counters: dict[str, dict[str, int]]

    @property
    def mutation_score(self) -> tuple[int, int]:
        return self.counters["KILLED"], self.counters["total"]

    @property
    def test_strength(self) -> tuple[int, int] | None:
        denominator = self.counters["KILLED"] + self.counters["SURVIVED"]
        return None if denominator == 0 else (self.counters["KILLED"], denominator)

    def rational_summary(self) -> dict[str, object]:
        strength = self.test_strength
        return {
            "mutationScore": _rational(*self.mutation_score),
            "testStrength": (
                {"state": "not-applicable", "numerator": 0, "denominator": 0, "value": None}
                if strength is None
                else _rational(*strength)
            ),
            "noCoverageRate": _rational(self.counters["NO_COVERAGE"], self.counters["total"]),
        }


def _rational(numerator: int, denominator: int) -> dict[str, object]:
    return {
        "state": "applicable",
        "numerator": numerator,
        "denominator": denominator,
        "value": f"{numerator}/{denominator}",
    }


def compare_floor(killed: int, total: int, floor_percent: int) -> bool:
    if any(isinstance(value, bool) or not isinstance(value, int) for value in (killed, total, floor_percent)):
        raise MutationPolicyError("mutation floor inputs must be integers")
    if killed < 0 or total <= 0 or killed > total or not 0 <= floor_percent <= 100:
        raise MutationPolicyError("mutation floor inputs are outside valid bounds")
    return 100 * killed >= floor_percent * total


def compare_no_coverage(
    baseline: Mapping[str, int],
    current: Mapping[str, int],
    changed_packages: Iterable[str],
) -> list[str]:
    violations: list[str] = []
    for package in sorted(set(changed_packages)):
        old = baseline.get(package, 0)
        new = current.get(package, 0)
        if any(isinstance(value, bool) or not isinstance(value, int) or value < 0 for value in (old, new)):
            raise MutationPolicyError("NO_COVERAGE counters must be non-negative integers")
        if new > old:
            violations.append(f"{package} NO_COVERAGE increased: baseline={old} current={new}")
    return violations


def _reject_duplicate_pairs(pairs: list[tuple[str, object]]) -> dict[str, object]:
    value: dict[str, object] = {}
    for key, item in pairs:
        if key in value:
            raise MutationPolicyError(f"duplicate JSON key: {key}")
        value[key] = item
    return value


def read_strict_json(data: bytes) -> object:
    try:
        text = data.decode("utf-8", errors="strict")
        return json.loads(text, object_pairs_hook=_reject_duplicate_pairs)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise MutationPolicyError(f"invalid JSON: {error}") from error


def receipt(schema: str, predecessors: Mapping[str, bytes], **fields: object) -> dict[str, object]:
    if not re.fullmatch(r"[a-z][a-z0-9-]*-v[1-9][0-9]*", schema):
        raise MutationPolicyError("receipt schema must be a versioned identifier")
    return {
        "schema": schema,
        "predecessors": {
            name: hashlib.sha256(payload).hexdigest()
            for name, payload in sorted(predecessors.items())
        },
        **fields,
    }


def parse_pitest_xml(
    xml_bytes: bytes,
    *,
    module: str,
    package_root: str,
    source_lookup: Callable[[str, str], str] | None = None,
    class_lookup: Callable[[str], tuple[str, bytes]] | None = None,
    archived_provenance_lookup: Callable[
        [tuple[object, ...], str], tuple[str, str, str]
    ] | None = None,
    maximum_bytes: int = 64 * 1024 * 1024,
) -> PitestReport:
    if len(xml_bytes) > maximum_bytes:
        raise MutationPolicyError("PIT XML exceeds the fixed input-size ceiling")
    if b"<!DOCTYPE" in xml_bytes.upper() or b"<!ENTITY" in xml_bytes.upper():
        raise MutationPolicyError("DTD and entity declarations are forbidden")
    try:
        xml_bytes.decode("utf-8", errors="strict")
        root = ET.fromstring(xml_bytes)
    except (UnicodeDecodeError, ET.ParseError) as error:
        raise MutationPolicyError(f"invalid PIT XML: {error}") from error
    if root.tag != "mutations" or root.attrib != {"partial": "true"}:
        raise MutationPolicyError("PIT root must be mutations partial=true")
    records: list[MutationRecord] = []
    identities: set[tuple[object, ...]] = set()
    for node in list(root):
        if node.tag != "mutation" or set(node.attrib) != {"detected", "status", "numberOfTestsRun"}:
            raise MutationPolicyError("mutation attributes differ from the PIT 1.25.7 contract")
        children = list(node)
        expected = [
            "sourceFile", "mutatedClass", "mutatedMethod", "methodDescription", "lineNumber",
            "mutator", "indexes", "blocks", "killingTest", "description",
        ]
        if [child.tag for child in children] != expected:
            raise MutationPolicyError("mutation child shape differs from the PIT 1.25.7 contract")
        for child in children:
            if child.attrib:
                raise MutationPolicyError(f"PIT {child.tag} attributes are forbidden")
            if child.tag not in {"indexes", "blocks"} and list(child):
                raise MutationPolicyError(f"PIT {child.tag} must not contain child elements")
        values = {child.tag: (child.text or "") for child in children}
        status = node.attrib["status"]
        if status not in {"KILLED", "SURVIVED", "NO_COVERAGE"}:
            raise MutationPolicyError(f"unsupported PIT status: {status}")
        detected = _strict_boolean(node.attrib["detected"], "detected")
        tests_run = _nonnegative_decimal(node.attrib["numberOfTestsRun"], "numberOfTestsRun")
        line = _nonnegative_decimal(values["lineNumber"], "lineNumber")
        if line == 0:
            raise MutationPolicyError("lineNumber must be positive")
        for label in ("sourceFile", "mutatedClass", "mutatedMethod", "methodDescription", "mutator", "description"):
            if not values[label].strip():
                raise MutationPolicyError(f"{label} must be non-empty")
        _validate_descriptor(values["methodDescription"])
        mutated_class = values["mutatedClass"]
        if not mutated_class.startswith(package_root + ".") and mutated_class != package_root:
            raise MutationPolicyError("mutated class escapes the declared package root")
        indexes = _nested_decimal_values(children[6], "index")
        blocks = _nested_decimal_values(children[7], "block")
        killing_test = values["killingTest"]
        if status == "KILLED" and (not detected or tests_run < 1 or not killing_test):
            raise MutationPolicyError("KILLED mutation must be detected with tests and a killing test")
        if status == "SURVIVED" and (detected or tests_run < 1 or killing_test):
            raise MutationPolicyError("SURVIVED mutation contradicts detected/tests/killingTest")
        if status == "NO_COVERAGE" and (detected or tests_run != 0 or killing_test):
            raise MutationPolicyError("NO_COVERAGE mutation contradicts detected/tests/killingTest")
        source_file = values["sourceFile"]
        if Path(source_file).name != source_file or source_file in {".", ".."}:
            raise MutationPolicyError("sourceFile must be a safe basename")
        identity = (
            mutated_class,
            values["mutatedMethod"],
            values["methodDescription"],
            values["mutator"],
            indexes,
        )
        if archived_provenance_lookup is not None:
            if source_lookup is not None or class_lookup is not None:
                raise MutationPolicyError("archived PIT provenance cannot mix live lookups")
            source_path, class_path, class_sha256 = archived_provenance_lookup(
                identity,
                source_file,
            )
            if (
                not source_path
                or Path(source_path).is_absolute()
                or ".." in Path(source_path).parts
                or Path(source_path).name != source_file
                or not class_path
                or Path(class_path).is_absolute()
                or ".." in Path(class_path).parts
                or not re.fullmatch(r"[0-9a-f]{64}", class_sha256)
            ):
                raise MutationPolicyError("archived PIT source/class provenance is invalid")
        else:
            if source_lookup is None or class_lookup is None:
                raise MutationPolicyError("live PIT parsing requires source and class lookups")
            source_path = source_lookup(mutated_class, source_file)
            if not source_path or Path(source_path).is_absolute() or ".." in Path(source_path).parts:
                raise MutationPolicyError("resolved source path escapes the repository")
            class_path, class_bytes = class_lookup(mutated_class)
            internal_name, compiled_source = _parse_class_source_file(class_bytes)
            if internal_name != mutated_class.replace(".", "/"):
                raise MutationPolicyError("class-file this_class differs from mutatedClass")
            if compiled_source != source_file:
                raise MutationPolicyError("class-file SourceFile differs from PIT sourceFile")
            class_sha256 = hashlib.sha256(class_bytes).hexdigest()
        record = MutationRecord(
            source_path=source_path,
            source_file=source_file,
            mutated_class=mutated_class,
            mutated_method=values["mutatedMethod"],
            method_description=values["methodDescription"],
            line_number=line,
            mutator=values["mutator"],
            indexes=indexes,
            blocks=blocks,
            status=status,
            detected=detected,
            tests_run=tests_run,
            killing_test=killing_test,
            description=values["description"],
            class_path=class_path,
            class_sha256=class_sha256,
        )
        if record.identity in identities:
            raise MutationPolicyError("duplicate PIT mutation identity")
        identities.add(record.identity)
        records.append(record)
    if not records:
        raise MutationPolicyError("PIT report contains no mutations")
    ordered = tuple(sorted(records, key=lambda item: tuple(str(part) for part in item.identity)))
    semantic = [
        {
            "mutatedClass": item.mutated_class,
            "mutatedMethod": item.mutated_method,
            "methodDescription": item.method_description,
            "mutator": item.mutator,
            "indexes": list(item.indexes),
            "status": item.status,
        }
        for item in ordered
    ]
    counters = _counters(ordered)
    package_counters = _group_counters(ordered, lambda item: item.mutated_class.rsplit(".", 1)[0])
    class_counters = _group_counters(ordered, lambda item: item.mutated_class)
    return PitestReport(
        module=module,
        raw_sha256=hashlib.sha256(xml_bytes).hexdigest(),
        semantic_sha256=hashlib.sha256(canonical_json_bytes(semantic)).hexdigest(),
        records=ordered,
        counters=counters,
        package_counters=package_counters,
        class_counters=class_counters,
    )


def canonical_json_bytes(value: object) -> bytes:
    normalized = _normalize(value)
    return (json.dumps(normalized, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def _normalize(value: object) -> object:
    if isinstance(value, str):
        return unicodedata.normalize("NFC", value)
    if isinstance(value, dict):
        return {_normalize(str(key)): _normalize(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_normalize(item) for item in value]
    return value


def _strict_boolean(value: str, label: str) -> bool:
    if value == "true":
        return True
    if value == "false":
        return False
    raise MutationPolicyError(f"{label} must be true or false")


def _nonnegative_decimal(value: str, label: str) -> int:
    if not re.fullmatch(r"0|[1-9][0-9]*", value):
        raise MutationPolicyError(f"{label} must be a non-negative decimal integer")
    return int(value)


def _nested_decimal_values(node: ET.Element, expected_tag: str) -> tuple[int, ...]:
    children = list(node)
    if not children or any(child.tag != expected_tag or child.attrib or list(child) for child in children):
        raise MutationPolicyError(f"{node.tag} must contain non-empty {expected_tag} children")
    values = tuple(_nonnegative_decimal(child.text or "", expected_tag) for child in children)
    if len(set(values)) != len(values):
        raise MutationPolicyError(f"duplicate {expected_tag} value")
    return values


def _validate_descriptor(value: str) -> None:
    index = 0

    def field(*, allow_void: bool = False) -> None:
        nonlocal index
        dimensions = 0
        while index < len(value) and value[index] == "[":
            index += 1
            dimensions += 1
            if dimensions > 255:
                raise MutationPolicyError("malformed JVM method descriptor")
        if index >= len(value):
            raise MutationPolicyError("malformed JVM method descriptor")
        code = value[index]
        index += 1
        if code in "BCDFIJSZ":
            return
        if code == "V":
            if allow_void and dimensions == 0:
                return
            raise MutationPolicyError("malformed JVM method descriptor")
        if code == "L":
            end = value.find(";", index)
            internal = value[index:end] if end >= 0 else ""
            if (
                not internal
                or "." in internal
                or "[" in internal
                or ";" in internal
                or internal.startswith("/")
                or internal.endswith("/")
                or "//" in internal
            ):
                raise MutationPolicyError("malformed JVM method descriptor")
            index = end + 1
            return
        raise MutationPolicyError("malformed JVM method descriptor")

    if not value.startswith("("):
        raise MutationPolicyError("malformed JVM method descriptor")
    index = 1
    while index < len(value) and value[index] != ")":
        field()
    if index >= len(value) or value[index] != ")":
        raise MutationPolicyError("malformed JVM method descriptor")
    index += 1
    field(allow_void=True)
    if index != len(value):
        raise MutationPolicyError("malformed JVM method descriptor")


def _parse_class_source_file(data: bytes) -> tuple[str, str]:
    if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
        raise MutationPolicyError("malformed class file")
    offset = 8
    count = int.from_bytes(data[offset:offset + 2], "big")
    offset += 2
    pool: list[object | None] = [None] * count
    tags: list[int | None] = [None] * count
    index = 1
    while index < count:
        if offset >= len(data):
            raise MutationPolicyError("truncated class constant pool")
        tag = data[offset]
        offset += 1
        tags[index] = tag
        if tag == 1:
            length, offset = _u2(data, offset)
            raw, offset = _take(data, offset, length)
            pool[index] = _decode_modified_utf8(raw)
        elif tag in {3, 4}:
            _, offset = _take(data, offset, 4)
        elif tag in {5, 6}:
            _, offset = _take(data, offset, 8)
            index += 1
        elif tag in {7, 8, 16, 19, 20}:
            pool[index], offset = _u2(data, offset)
        elif tag in {9, 10, 11, 12, 17, 18}:
            first, offset = _u2(data, offset)
            second, offset = _u2(data, offset)
            pool[index] = (first, second)
        elif tag == 15:
            reference_kind = data[offset] if offset < len(data) else 0
            reference_index, offset = _u2(data, offset + 1)
            pool[index] = (reference_kind, reference_index)
        else:
            raise MutationPolicyError(f"unsupported class constant-pool tag: {tag}")
        index += 1
    _validate_constant_pool(pool, tags)
    _, offset = _u2(data, offset)
    this_class, offset = _u2(data, offset)
    super_class, offset = _u2(data, offset)
    class_name_index = _pool_index_tag(pool, tags, this_class, 7, int)
    internal_name = _pool_index(pool, class_name_index, str)
    if super_class != 0:
        _pool_index_tag(pool, tags, super_class, 7, int)
    interface_count, offset = _u2(data, offset)
    for _ in range(interface_count):
        interface_index, offset = _u2(data, offset)
        _pool_index_tag(pool, tags, interface_index, 7, int)
    for _ in range(2):
        member_count, offset = _u2(data, offset)
        for _member in range(member_count):
            _, offset = _u2(data, offset)
            name_index, offset = _u2(data, offset)
            descriptor_index, offset = _u2(data, offset)
            _pool_index_tag(pool, tags, name_index, 1, str)
            _pool_index_tag(pool, tags, descriptor_index, 1, str)
            attribute_count, offset = _u2(data, offset)
            offset = _skip_attributes(data, offset, attribute_count, pool, tags)
    attribute_count, offset = _u2(data, offset)
    source_files: list[str] = []
    for _ in range(attribute_count):
        name_index, offset = _u2(data, offset)
        length, offset = _u4(data, offset)
        payload, offset = _take(data, offset, length)
        name = _pool_index_tag(pool, tags, name_index, 1, str)
        if name == "SourceFile":
            if length != 2:
                raise MutationPolicyError("malformed SourceFile attribute")
            source_files.append(_pool_index(pool, int.from_bytes(payload, "big"), str))
    if offset != len(data):
        raise MutationPolicyError("class file has trailing bytes")
    if len(source_files) != 1:
        raise MutationPolicyError("class file must contain exactly one SourceFile attribute")
    return internal_name, source_files[0]


def _validate_constant_pool(pool: list[object | None], tags: list[int | None]) -> None:
    for index in range(1, len(pool)):
        tag = tags[index]
        value = pool[index]
        if tag is None:
            continue
        if tag in {7, 8, 16, 19, 20}:
            _pool_index_tag(pool, tags, value, 1, str)
        elif tag in {9, 10, 11}:
            owner, name_and_type = value if isinstance(value, tuple) else (0, 0)
            _pool_index_tag(pool, tags, owner, 7, int)
            _pool_index_tag(pool, tags, name_and_type, 12, tuple)
        elif tag == 12:
            name, descriptor = value if isinstance(value, tuple) else (0, 0)
            _pool_index_tag(pool, tags, name, 1, str)
            _pool_index_tag(pool, tags, descriptor, 1, str)
        elif tag in {17, 18}:
            _, name_and_type = value if isinstance(value, tuple) else (0, 0)
            _pool_index_tag(pool, tags, name_and_type, 12, tuple)
        elif tag == 15:
            reference_kind, reference_index = value if isinstance(value, tuple) else (0, 0)
            if not isinstance(reference_kind, int) or not 1 <= reference_kind <= 9:
                raise MutationPolicyError("invalid class constant-pool method-handle kind")
            allowed = (
                {9} if reference_kind in {1, 2, 3, 4}
                else {10} if reference_kind in {5, 8}
                else {10, 11} if reference_kind in {6, 7}
                else {11}
            )
            if not isinstance(reference_index, int) or reference_index <= 0 or reference_index >= len(tags) or tags[reference_index] not in allowed:
                raise MutationPolicyError("invalid class constant-pool method-handle reference")


def _decode_modified_utf8(raw: bytes) -> str:
    code_units: list[int] = []
    offset = 0
    while offset < len(raw):
        first = raw[offset]
        offset += 1
        if 0x01 <= first <= 0x7F:
            code_units.append(first)
            continue
        if 0xC0 <= first <= 0xDF:
            if offset >= len(raw) or raw[offset] & 0xC0 != 0x80:
                raise MutationPolicyError("invalid class UTF-8 constant")
            second = raw[offset]
            offset += 1
            value = ((first & 0x1F) << 6) | (second & 0x3F)
            if value == 0:
                if first != 0xC0 or second != 0x80:
                    raise MutationPolicyError("invalid class UTF-8 constant")
            elif value < 0x80:
                raise MutationPolicyError("invalid class UTF-8 constant")
            code_units.append(value)
            continue
        if 0xE0 <= first <= 0xEF:
            if offset + 1 >= len(raw) or raw[offset] & 0xC0 != 0x80 or raw[offset + 1] & 0xC0 != 0x80:
                raise MutationPolicyError("invalid class UTF-8 constant")
            second, third = raw[offset:offset + 2]
            offset += 2
            value = ((first & 0x0F) << 12) | ((second & 0x3F) << 6) | (third & 0x3F)
            if value < 0x800:
                raise MutationPolicyError("invalid class UTF-8 constant")
            code_units.append(value)
            continue
        raise MutationPolicyError("invalid class UTF-8 constant")
    encoded = b"".join(code_unit.to_bytes(2, "big") for code_unit in code_units)
    return encoded.decode("utf-16-be", errors="surrogatepass")


def _skip_attributes(
    data: bytes,
    offset: int,
    count: int,
    pool: list[object | None],
    tags: list[int | None],
) -> int:
    for _ in range(count):
        name_index, offset = _u2(data, offset)
        _pool_index_tag(pool, tags, name_index, 1, str)
        length, offset = _u4(data, offset)
        _, offset = _take(data, offset, length)
    return offset


def _pool_index(pool: list[object | None], index: int, expected: type):
    if index <= 0 or index >= len(pool) or not isinstance(pool[index], expected):
        raise MutationPolicyError("invalid class constant-pool reference")
    return pool[index]


def _pool_index_tag(
    pool: list[object | None],
    tags: list[int | None],
    index: object,
    expected_tag: int,
    expected_type: type,
):
    if not isinstance(index, int) or index <= 0 or index >= len(pool) or tags[index] != expected_tag:
        raise MutationPolicyError("invalid class constant-pool reference")
    return _pool_index(pool, index, expected_type)


def _take(data: bytes, offset: int, length: int) -> tuple[bytes, int]:
    end = offset + length
    if length < 0 or end > len(data):
        raise MutationPolicyError("truncated class file")
    return data[offset:end], end


def _u2(data: bytes, offset: int) -> tuple[int, int]:
    raw, offset = _take(data, offset, 2)
    return int.from_bytes(raw, "big"), offset


def _u4(data: bytes, offset: int) -> tuple[int, int]:
    raw, offset = _take(data, offset, 4)
    return int.from_bytes(raw, "big"), offset


def _counters(records: Iterable[MutationRecord]) -> dict[str, int]:
    result = {"KILLED": 0, "SURVIVED": 0, "NO_COVERAGE": 0, "total": 0}
    for record in records:
        result[record.status] += 1
        result["total"] += 1
    return result


def _group_counters(records: Iterable[MutationRecord], key: Callable[[MutationRecord], str]) -> dict[str, dict[str, int]]:
    grouped: dict[str, list[MutationRecord]] = {}
    for record in records:
        grouped.setdefault(key(record), []).append(record)
    return {identity: _counters(grouped[identity]) for identity in sorted(grouped)}


_ALL_MODULES = ["location", "settings", "station"]
_SHARED_EXACT = {
    "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradle/libs.versions.toml",
    "gradlew", "gradlew.bat",
    "scripts/agent/check_contracts.py", "scripts/agent/verify.sh", "scripts/agent/test.sh",
    "scripts/agent/tests/check_contracts_test.sh", "scripts/agent/tests/verify_test.sh",
    ".github/workflows/android.yml", ".github/workflows/mutation-schedule.yml",
    "config/quality/mutation-policy.json", "config/quality/mutation-baseline.json",
}
_SHARED_PREFIXES = (
    "core/model/", "build-logic/", "gradle/wrapper/", "scripts/quality/",
    "config/quality/mutation-transitions/",
)


def route_changed_paths(changes: Iterable[tuple[str, str | None, str | None]]) -> list[str]:
    selected: set[str] = set()
    for status, old_path, new_path in changes:
        if status not in {"A", "C", "M", "R", "D"}:
            raise MutationPolicyError(f"unsupported changed-path status: {status}")
        for path in (old_path, new_path):
            if path is None:
                continue
            normalized = Path(path).as_posix()
            if Path(normalized).is_absolute() or ".." in Path(normalized).parts:
                raise MutationPolicyError("changed path escapes repository")
            for module in _ALL_MODULES:
                if normalized == f"domain/{module}/build.gradle.kts" or normalized.startswith(f"domain/{module}/src/main/") or normalized.startswith(f"domain/{module}/src/test/"):
                    selected.add(module)
            if normalized in _SHARED_EXACT or normalized.startswith(_SHARED_PREFIXES):
                selected.update(_ALL_MODULES)
            elif normalized.startswith("config/quality/mutation-"):
                raise MutationPolicyError(f"unclassified mutation-related path: {normalized}")
    return sorted(selected)


__all__ = [
    "GitCommand",
    "GitExecutor",
    "MutationPolicyError",
    "PitestReport",
    "canonical_json_bytes",
    "compare_floor",
    "compare_no_coverage",
    "parse_pitest_xml",
    "read_strict_json",
    "receipt",
    "route_changed_paths",
]
