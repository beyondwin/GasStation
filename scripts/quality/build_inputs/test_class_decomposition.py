from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping


class DecompositionError(ValueError):
    pass


@dataclass(frozen=True)
class TestMethod:
    owner: str
    name: str
    body_sha256: str

    @property
    def key(self) -> str:
        return f"{self.owner}#{self.name}"


REVIEWED_BASELINE_ROOT_SHA256 = "a162545102177e92c32110722ac99812a5ccfeb15d0ed3b35ec9e1e97a15b0d9"
REVIEWED_BASELINE_COVERAGE_SHA256 = "702986f27eb1d14252261448aa4ed186c601cf085da7d84d11a44aeaf0ded7be"
REVIEWED_BASELINE_AFFECTED_SHA256 = "442a5c415ad7c281a6e6e1128c2ccc4fdd36e154d73643c07e4eeeb4f2e3b57d"
REVIEWED_ROOT_SUPPORT_SHA256 = "127586c5a11273f3ebb060a0f6e6693726ff75b31d2bf3da25f15ad13b5feaf2"
REVIEWED_COVERAGE_SUPPORT_SHA256 = "aca774dc6b6f4cf44ea440b8b3d7fe53d66732a734e67964815edce299d2ed7d"


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _canonical_json(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode()


def _masked_kotlin(source: str) -> str:
    result = list(source)
    index = 0
    size = len(source)
    while index < size:
        if source.startswith("//", index):
            end = source.find("\n", index)
            end = size if end < 0 else end
            for offset in range(index, end):
                result[offset] = " "
            index = end
        elif source.startswith("/*", index):
            depth = 1
            end = index + 2
            while end < size and depth:
                if source.startswith("/*", end):
                    depth += 1
                    end += 2
                elif source.startswith("*/", end):
                    depth -= 1
                    end += 2
                else:
                    end += 1
            if depth:
                raise DecompositionError("unterminated Kotlin block comment")
            for offset in range(index, end):
                if result[offset] != "\n":
                    result[offset] = " "
            index = end
        elif source.startswith('"""', index):
            end = source.find('"""', index + 3)
            if end < 0:
                raise DecompositionError("unterminated Kotlin raw string")
            end += 3
            for offset in range(index, end):
                if result[offset] != "\n":
                    result[offset] = " "
            index = end
        elif source[index] in {'"', "'"}:
            quote = source[index]
            end = index + 1
            while end < size:
                if source[end] == "\\":
                    end += 2
                elif source[end] == quote:
                    end += 1
                    break
                else:
                    end += 1
            else:
                raise DecompositionError("unterminated Kotlin quoted literal")
            for offset in range(index, min(end, size)):
                if result[offset] != "\n":
                    result[offset] = " "
            index = end
        else:
            index += 1
    return "".join(result)


def _depths(masked: str) -> list[int]:
    depth = 0
    values: list[int] = []
    for character in masked:
        values.append(depth)
        if character == "{":
            depth += 1
        elif character == "}":
            depth -= 1
            if depth < 0:
                raise DecompositionError("unbalanced Kotlin braces")
    if depth:
        raise DecompositionError("unbalanced Kotlin braces")
    return values


def _matching_brace(masked: str, opening: int) -> int:
    depth = 0
    for index in range(opening, len(masked)):
        if masked[index] == "{":
            depth += 1
        elif masked[index] == "}":
            depth -= 1
            if depth == 0:
                return index
    raise DecompositionError("Kotlin declaration has no closing brace")


def _normalized_method(source: str, start: int, end: int) -> bytes:
    lines = source[start:end].replace("\r\n", "\n").replace("\r", "\n").splitlines()
    while lines and not lines[0].strip():
        lines.pop(0)
    while lines and not lines[-1].strip():
        lines.pop()
    indents = [len(line) - len(line.lstrip(" ")) for line in lines if line.strip()]
    indent = min(indents, default=0)
    normalized = "\n".join(line[indent:].rstrip() for line in lines) + "\n"
    return normalized.encode()


def _normalized_declaration(source: str, start: int, end: int) -> bytes:
    normalized = _normalized_method(source, start, end).decode()
    normalized = re.sub(r"(?m)^(?:private|protected)\s+", "", normalized)
    return normalized.encode()


def _class_span(masked: str, class_name: str) -> tuple[int, int, int]:
    depths = _depths(masked)
    pattern = re.compile(rf"\b(?:abstract\s+)?class\s+{re.escape(class_name)}[^\n{{]*\{{")
    matches = [match for match in pattern.finditer(masked) if depths[match.start()] == 0]
    if len(matches) != 1:
        raise DecompositionError(f"expected one top-level Kotlin class: {class_name}")
    opening = masked.find("{", matches[0].start(), matches[0].end())
    return matches[0].start(), opening, _matching_brace(masked, opening)


def _function_end(masked: str, declaration_tail: int, limit: int) -> int:
    body_open = masked.find("{", declaration_tail, limit)
    equals = masked.find("=", declaration_tail, limit)
    if body_open >= 0 and (equals < 0 or body_open < equals):
        return _matching_brace(masked, body_open) + 1
    if equals >= 0:
        newline = masked.find("\n", equals, limit)
        return limit if newline < 0 else newline
    raise DecompositionError("Kotlin function has no executable body")


def _property_end(masked: str, start: int, limit: int, member_depth: int, depths: list[int]) -> int:
    parens = 0
    brackets = 0
    index = start
    while index < limit:
        character = masked[index]
        if character == "(":
            parens += 1
        elif character == ")":
            parens -= 1
        elif character == "[":
            brackets += 1
        elif character == "]":
            brackets -= 1
        elif character == "\n" and parens == 0 and brackets == 0 and depths[index] == member_depth:
            next_index = index + 1
            while next_index < limit and masked[next_index].isspace():
                next_index += 1
            if next_index >= limit or depths[next_index] <= member_depth:
                return index
        index += 1
    return limit


def support_behavior_inventory_source(
    source: str,
    class_name: str,
    *,
    excluded_test_names: frozenset[str] = frozenset(),
) -> dict[str, str]:
    masked = _masked_kotlin(source)
    depths = _depths(masked)
    _, class_open, class_close = _class_span(masked, class_name)
    inventory: dict[str, str] = {}

    function_pattern = re.compile(
        r"\bfun\s+((?:[A-Za-z_][A-Za-z0-9_<>?,. ]*\.)?[A-Za-z_][A-Za-z0-9_]*)\s*\(([^)]*)\)",
    )
    for match in function_pattern.finditer(masked, class_open + 1, class_close):
        if depths[match.start()] != 1:
            continue
        function_name = match.group(1).rsplit(".", 1)[-1]
        if function_name in excluded_test_names:
            continue
        end = _function_end(masked, match.end(), class_close)
        key = "fun:" + re.sub(r"\s+", " ", f"{match.group(1)}({match.group(2)})").strip()
        if key in inventory:
            raise DecompositionError(f"duplicate support helper signature: {key}")
        inventory[key] = _sha256(_normalized_declaration(source, match.start(), end))

    property_pattern = re.compile(r"\bval\s+([A-Za-z_][A-Za-z0-9_]*)\b")
    spans = [(class_open + 1, class_close, 1, "property")]
    companion = re.search(r"\bcompanion\s+object\s*\{", masked[class_open + 1 : class_close])
    if companion is not None:
        companion_start = class_open + 1 + companion.start()
        if depths[companion_start] == 1:
            companion_open = masked.find("{", companion_start, class_close)
            spans.append((companion_open + 1, _matching_brace(masked, companion_open), 2, "companion"))
    for start, limit, member_depth, prefix in spans:
        for match in property_pattern.finditer(masked, start, limit):
            if depths[match.start()] != member_depth:
                continue
            end = _property_end(masked, match.start(), limit, member_depth, depths)
            key = f"{prefix}:{match.group(1)}"
            if key in inventory:
                raise DecompositionError(f"duplicate support fixture declaration: {key}")
            inventory[key] = _sha256(_normalized_declaration(source, match.start(), end))
    return dict(sorted(inventory.items()))


def support_behavior_inventory(path: Path, class_name: str) -> dict[str, str]:
    return support_behavior_inventory_source(path.read_text(encoding="utf-8"), class_name)


def support_behavior_sha256(path: Path, class_name: str) -> str:
    return _sha256(_canonical_json(support_behavior_inventory(path, class_name)))


def kotlin_test_methods(path: Path) -> list[TestMethod]:
    source = path.read_text(encoding="utf-8")
    if "\r" in source:
        raise DecompositionError(f"Kotlin source is not LF-only: {path}")
    package_match = re.search(r"(?m)^package\s+([A-Za-z0-9_.]+)\s*$", source)
    package = package_match.group(1) if package_match is not None else ""
    masked = _masked_kotlin(source)
    depths = _depths(masked)
    methods: list[TestMethod] = []
    class_pattern = re.compile(r"\b(?:abstract\s+)?class\s+([A-Za-z_][A-Za-z0-9_]*)[^\n{]*\{")
    for class_match in class_pattern.finditer(masked):
        if depths[class_match.start()] != 0:
            continue
        class_name = class_match.group(1)
        class_open = masked.find("{", class_match.start(), class_match.end())
        class_close = _matching_brace(masked, class_open)
        owner = f"{package}.{class_name}" if package else class_name
        for annotation in re.finditer(r"@Test\b", masked[class_open + 1 : class_close]):
            annotation_start = class_open + 1 + annotation.start()
            if depths[annotation_start] != 1:
                continue
            function = re.search(
                r"\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\([^)]*\)",
                masked[annotation_start:class_close],
            )
            if function is None:
                raise DecompositionError(f"@Test has no block-bodied function in {path}")
            function_start = annotation_start + function.start()
            if depths[function_start] != 1:
                continue
            declaration_tail = annotation_start + function.end()
            next_annotation = masked.find("@Test", declaration_tail, class_close)
            declaration_limit = class_close if next_annotation < 0 else next_annotation
            body_open = masked.find("{", declaration_tail, declaration_limit)
            equals = masked.find("=", declaration_tail, declaration_limit)
            if body_open >= 0 and (equals < 0 or body_open < equals):
                body_close = _matching_brace(masked, body_open)
            elif equals >= 0:
                body_close = declaration_limit
            else:
                raise DecompositionError(f"@Test has no executable body in {path}")
            methods.append(
                TestMethod(
                    owner=owner,
                    name=function.group(1),
                    body_sha256=_sha256(_normalized_method(source, function_start, body_close + 1)),
                ),
            )
    keys = [method.key for method in methods]
    if len(keys) != len(set(keys)):
        raise DecompositionError(f"duplicate top-level JUnit owner/method in {path}")
    return methods


def _all_test_methods(root: Path) -> list[TestMethod]:
    source_root = root / "build-logic/convention/src/test/kotlin"
    paths = [path for path in sorted(source_root.rglob("*.kt")) if "fixtures" not in path.relative_to(source_root).parts]
    methods = [
        method
        for path in paths
        for method in kotlin_test_methods(path)
    ]
    raw_test_annotations = sum(len(re.findall(r"@Test\b", _masked_kotlin(path.read_text(encoding="utf-8")))) for path in paths)
    if raw_test_annotations != len(methods):
        raise DecompositionError("nested, inherited, or otherwise undiscovered @Test is forbidden")
    prohibited_annotations = ("@TestFactory", "@ParameterizedTest", "@RunWith(Parameterized::class)")
    for path in paths:
        source = _masked_kotlin(path.read_text(encoding="utf-8"))
        if any(annotation in source for annotation in prohibited_annotations):
            raise DecompositionError("dynamic or parameterized convention tests are forbidden")
    if len({method.key for method in methods}) != len(methods):
        raise DecompositionError("duplicate top-level JUnit owner/method in convention tests")
    return sorted(methods, key=lambda method: method.key)


def load_decomposition_contract(path: Path) -> dict[str, Any]:
    try:
        raw = path.read_bytes()
        value = json.loads(raw)
    except (OSError, json.JSONDecodeError) as error:
        raise DecompositionError("test-class decomposition contract is unreadable") from error
    if raw != _canonical_json(value):
        raise DecompositionError("test-class decomposition contract is not canonical JSON")
    if not isinstance(value, dict):
        raise DecompositionError("test-class decomposition contract must be an object")
    return value


def _require_sha(value: object, field: str) -> str:
    if not isinstance(value, str) or re.fullmatch(r"[0-9a-f]{64}", value) is None:
        raise DecompositionError(f"{field} must be lowercase SHA-256")
    return value


def _inventory_sha(keys: list[str]) -> str:
    return _sha256(("\n".join(sorted(keys)) + "\n").encode())


def _prohibited_matches(root: Path) -> list[str]:
    build = (root / "build-logic/convention/build.gradle.kts").read_text(encoding="utf-8")
    fixture = (root / "build-logic/convention/src/test/kotlin/fixtures/GradlePluginTestProject.kt").read_text(
        encoding="utf-8",
    )
    matches: list[str] = []
    if build.count("tasks.withType<Test>().configureEach") != 1:
        matches.append("test-task-topology")
    if build.count("maxParallelForks = 5") != 1:
        matches.append("max-parallel-forks")
    for literal in ("tasks.register<Test>", "forkEvery", "includeTestsMatching", "setIncludePatterns", "retry {"):
        if literal in build:
            matches.append(literal)
    if ".withGradleInstallation(" in fixture:
        matches.append("withGradleInstallation")
    if fixture.count(".withGradleVersion(EXACT_GRADLE_VERSION)") != 2:
        matches.append("exact-gradle-version-runners")
    return sorted(matches)


def verify_decomposition_data(root: Path, contract: Mapping[str, Any]) -> dict[str, Any]:
    expected_keys = {
        "baselineAffectedInventorySha256",
        "baselineCoverageSourceSha256",
        "baselineRootQualitySourceSha256",
        "expectedTotalMethods",
        "mappings",
        "schemaVersion",
        "unchangedMethods",
        "unchangedMethodsSha256",
    }
    if set(contract) != expected_keys or contract.get("schemaVersion") != 1:
        raise DecompositionError("test-class decomposition contract fields differ")
    if contract.get("expectedTotalMethods") != 90:
        raise DecompositionError("test-class decomposition total must remain exact 90")
    _require_sha(contract.get("baselineAffectedInventorySha256"), "baselineAffectedInventorySha256")
    if contract.get("baselineAffectedInventorySha256") != REVIEWED_BASELINE_AFFECTED_SHA256:
        raise DecompositionError("reviewed affected inventory anchor differs")
    if contract.get("baselineCoverageSourceSha256") != REVIEWED_BASELINE_COVERAGE_SHA256:
        raise DecompositionError("reviewed Coverage source anchor differs")
    if contract.get("baselineRootQualitySourceSha256") != REVIEWED_BASELINE_ROOT_SHA256:
        raise DecompositionError("reviewed RootQuality source anchor differs")
    unchanged_sha = _require_sha(contract.get("unchangedMethodsSha256"), "unchangedMethodsSha256")
    mappings = contract.get("mappings")
    unchanged = contract.get("unchangedMethods")
    if not isinstance(mappings, list) or len(mappings) != 23:
        raise DecompositionError("test-class decomposition must contain exact 23 mapping rows")
    if not isinstance(unchanged, list) or len(unchanged) != 67 or not all(isinstance(row, str) for row in unchanged):
        raise DecompositionError("unchanged convention test inventory must contain exact 67 rows")
    if unchanged != sorted(set(unchanged)) or _inventory_sha(unchanged) != unchanged_sha:
        raise DecompositionError("unchanged convention test inventory differs")

    row_keys = {"family", "methodBodySha256", "newMethod", "newOwner", "oldMethod", "oldOwner"}
    old_keys: list[str] = []
    new_keys: list[str] = []
    new_owners: list[str] = []
    for index, row in enumerate(mappings):
        if not isinstance(row, dict) or set(row) != row_keys:
            raise DecompositionError(f"mapping row {index} fields differ")
        if not all(isinstance(row[key], str) and row[key] for key in row_keys):
            raise DecompositionError(f"mapping row {index} contains a blank value")
        _require_sha(row["methodBodySha256"], f"mappings[{index}].methodBodySha256")
        old_key = f"{row['oldOwner']}#{row['oldMethod']}"
        new_key = f"{row['newOwner']}#{row['newMethod']}"
        if row["oldMethod"] != row["newMethod"]:
            raise DecompositionError("implicit test method rename is forbidden")
        old_is_root = row["oldOwner"].endswith(".GasStationRootQualityConventionPluginTest")
        old_is_coverage = row["oldOwner"].endswith(".CoverageConventionTest")
        new_is_coverage = ".quality.coverage." in row["newOwner"]
        if old_is_root == old_is_coverage or old_is_coverage != new_is_coverage:
            raise DecompositionError("RootQuality and Coverage families must not mix")
        old_keys.append(old_key)
        new_keys.append(new_key)
        new_owners.append(row["newOwner"])
    if old_keys != sorted(set(old_keys)) or len(new_keys) != len(set(new_keys)):
        raise DecompositionError("test-class decomposition mapping is not a sorted bijection")
    if len(new_owners) != len(set(new_owners)):
        raise DecompositionError("each reviewed semantic family must have one top-level owner")
    if _inventory_sha(old_keys) != contract["baselineAffectedInventorySha256"]:
        raise DecompositionError("baseline affected inventory differs")

    current = _all_test_methods(root)
    current_by_key = {method.key: method for method in current}
    for row, new_key in zip(mappings, new_keys, strict=True):
        method = current_by_key.get(new_key)
        if method is None or method.body_sha256 != row["methodBodySha256"]:
            raise DecompositionError(f"mapped method body/owner differs: {new_key}")
    current_unchanged = sorted(set(current_by_key) - set(new_keys))
    if current_unchanged != unchanged:
        raise DecompositionError("one of the other 67 convention tests changed")
    if len(current) != 90:
        raise DecompositionError("convention test discovery must remain exact 90")
    root_support = support_behavior_sha256(
        root / "build-logic/convention/src/test/kotlin/quality/GasStationRootQualityConventionPluginTest.kt",
        "RootQualityTestSupport",
    )
    coverage_support = support_behavior_sha256(
        root / "build-logic/convention/src/test/kotlin/quality/coverage/CoverageConventionTest.kt",
        "CoverageTestSupport",
    )
    if root_support != REVIEWED_ROOT_SUPPORT_SHA256 or coverage_support != REVIEWED_COVERAGE_SUPPORT_SHA256:
        raise DecompositionError("shared helper call-graph or fixture behavior differs")
    prohibited = _prohibited_matches(root)
    if prohibited:
        raise DecompositionError(f"prohibited TestKit scheduling surface: {prohibited}")
    return {
        "defaultTimeoutMinutes": 15,
        "mappedMethodCount": len(mappings),
        "mappingSha256": _sha256(_canonical_json(mappings)),
        "maxParallelForks": 5,
        "newOwners": new_owners,
        "prohibitedMatches": prohibited,
        "sealedOuterTimeoutMinutes": 30,
        "supportBehaviorSha256": {"coverage": coverage_support, "rootQuality": root_support},
        "testTaskTopology": "one-test-task-v1",
        "totalMethodCount": len(current),
        "unchangedMethodCount": len(current_unchanged),
        "unchangedMethodsSha256": _inventory_sha(current_unchanged),
    }


def verify_decomposition(root: Path, contract_path: Path) -> dict[str, Any]:
    return verify_decomposition_data(root, load_decomposition_contract(contract_path))
