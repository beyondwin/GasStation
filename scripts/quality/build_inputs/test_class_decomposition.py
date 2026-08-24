from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
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
REVIEWED_R21_MAPPING_SHA256 = "f2a6b6ab62fc8a8628c0c25007a1ec81ba3488ad49adfa0235b6a96fe9496e5f"
REVIEWED_R21_DURATION_SHA256 = "e8c081736332b749d31e2082c8c3da91f9e722989dcb6d098432666756da202d"
REVIEWED_R21_MEMBERSHIP_SHA256 = {
    "A": "91904f6b7094a57c4f622075985be8f2cad6d9aabe44dcfacc281e701a624885",
    "B": "25db2175c3d2723ebe12775df09b8129e55e4c86dade6214520a220412f0db5c",
    "C": "08da2e64e47ad6fd910059524a3fc62b83658b9c0e9ad0d276d348601bdb3fef",
}
REVIEWED_R21_UNIT_SHA256 = {
    "A": "771f6ecade8e0a93350a6ac1d4a9d25b849085b65042bfb92788aae3b4881476",
    "B": "bc22d8fed030da9c895d8ffb8aab2fe5b7251880c03dc70089a5aa3306d67b29",
    "C": "3afc5586e7a8a216fef0ecb456ff7ea20836bfe6d8084d2db83960b093344046",
}
REVIEWED_R21_SCHEDULE_SHA256 = {
    "A": "b0675324f7338cc5c68d787f5eac747dcc88c35af8cd01d3cf4623cbbc4bb459",
    "B": "318befa054ed4092e77342c2077fbaf67eefb44fbdd908bc645329af8fb1e25d",
    "C": "e6f76cffbb3ec069d4dea05bc303aa5bf6bbaa2d1b9378fef6681316146a87a3",
}
REVIEWED_R21_INVENTORY_SHA256 = {
    "currentClasses": "1fbbcce61985d179e97757288a520263e8bd696ce50e4b17bc0f91161a268501",
    "currentMethods": "9b449ca1893ed61eb81444ef083fe9c28ed172fcf3679c2174ff824c0fefd3e7",
    "finalClasses": "ece3f2e5fb21b55ecc34f58868a8747a6a5360d4557b00e7b33e0ac1965881b1",
    "finalMethods": "531a6af2a0b4f1c34ac441fe9dec4712a1f2519962f057662ece742039c380a8",
    "unchangedMethods": "f4ba0cc1bb4c639b8c72c7dee63cf320b18e3d532791ae2a6db2b14c5ba0a33e",
}
REVIEWED_R21_SOURCE_FILES = {
    "build-logic/convention/src/test/kotlin/AndroidLintConventionPluginTest.kt": (
        "591cfc673cd01ec3ffceafecd5a4c1cb66ecfafecd5a77152a6ae725f2b75abc",
        "AndroidLintConventionTestSupport",
        "83385af87677e336c0c1550a43c8f563c9fd92439fb68c0354ba18d3eba9bcdb",
    ),
    "build-logic/convention/src/test/kotlin/GradlePluginTestHarnessTest.kt": (
        "fd10ee5ca067fb91f0ef9eeeb1f6cccc0f705eeacdec787add7eb3ae8823d527",
        "GradlePluginTestHarnessSupport",
        "936d44531f1c3da0c18e7f4da8f1434ecb2b02c8a3f530c904456a2416ea07d8",
    ),
    "build-logic/convention/src/test/kotlin/KotlinCompilerConventionPluginTest.kt": (
        "9f46add964bc94462fd12d66e822767dc10ba19af5c840b0a6662657a08eeefc",
        "KotlinCompilerConventionTestSupport",
        "e9bee30c15b7059923120c899fc3c1424543f8ae5dbb247afa7a223beed6cdbb",
    ),
    "build-logic/convention/src/test/kotlin/RoborazziConventionPluginTest.kt": (
        "ecfc040a91c0562f4fc76fe9e821f10ae975867bb5f3efe6eb794e89833e2dc8",
        "RoborazziConventionTestSupport",
        "25269800e027d8d9e0935ba25753540b50bb9f9961972673368d0026a06f8637",
    ),
}
REVIEWED_R21_IMPORT_SHA256 = {
    "build-logic/convention/src/test/kotlin/AndroidLintConventionPluginTest.kt": (
        "ff49d828a8bc0d7e88df172e11da28dc4377873bd6ae16f646325ad0da26e8ad"
    ),
    "build-logic/convention/src/test/kotlin/GradlePluginTestHarnessTest.kt": (
        "b783b271e4f11e5e7021dbea74fb6636e71ae68c7887f1e46c7286b0d7fd972d"
    ),
    "build-logic/convention/src/test/kotlin/KotlinCompilerConventionPluginTest.kt": (
        "fda3b70926ed8d222f4b2ed16f99242f2ad60045fb1aa7c9ef6248c67692ddda"
    ),
    "build-logic/convention/src/test/kotlin/RoborazziConventionPluginTest.kt": (
        "7f6ff17f2ea4c8671591cca1d5fe7fcfbe4a61b23afea06057159042f436ed06"
    ),
}
REVIEWED_R21_SUPPORT_ANNOTATION_SHA256 = {
    "build-logic/convention/src/test/kotlin/AndroidLintConventionPluginTest.kt": (
        "0596b5ad38e0287dd72c7a92cf5debeb1a4422eefaf01537d842e82539ff5cb9"
    ),
    "build-logic/convention/src/test/kotlin/GradlePluginTestHarnessTest.kt": (
        "0596b5ad38e0287dd72c7a92cf5debeb1a4422eefaf01537d842e82539ff5cb9"
    ),
    "build-logic/convention/src/test/kotlin/KotlinCompilerConventionPluginTest.kt": (
        "0596b5ad38e0287dd72c7a92cf5debeb1a4422eefaf01537d842e82539ff5cb9"
    ),
    "build-logic/convention/src/test/kotlin/RoborazziConventionPluginTest.kt": (
        "0596b5ad38e0287dd72c7a92cf5debeb1a4422eefaf01537d842e82539ff5cb9"
    ),
}
REVIEWED_R21_ACCESS_SHA256 = {
    "build-logic/convention/src/test/kotlin/AndroidLintConventionPluginTest.kt": (
        24,
        "97a4ff04245cb7a91b23f07df321c3b5ca9d653b04f9545a7a361ae433b9d2fd",
    ),
    "build-logic/convention/src/test/kotlin/GradlePluginTestHarnessTest.kt": (
        15,
        "a3aa0554e31da1387862809aca3f63d1e83e1491d0ba837d19a101d7b36230b3",
    ),
    "build-logic/convention/src/test/kotlin/KotlinCompilerConventionPluginTest.kt": (
        5,
        "7e30634c16dce9a2d3e2f3c247aa37ce324dd2997b9027682fa51ffd8ea2cf15",
    ),
    "build-logic/convention/src/test/kotlin/RoborazziConventionPluginTest.kt": (
        12,
        "58717d3875d7d1ca2ea960bf136aed1c4102246caeb48677cf15bdaa794714f5",
    ),
}
REVIEWED_R21_RULE_SHA256 = "83634e032e4a2bd7b1eea117445f36c77d14fa737a2304cc9b04589fddd37a89"
REVIEWED_R21_BRIDGE_SHA256 = "cf952140198dde7b6b4335996fffd7b08ff73ecdaa138cf00b87b29837ac2f80"
REVIEWED_R21_USE_SHA256 = "d68bfbb8238fd8cf1b6b6c3b24a8a04e1adfb08a45a4577b991da7bedbe786cc"
REVIEWED_R21_BRIDGE_VALUES = {
    "MAIN_SOURCE": (99, "73d84966b3e584fa478caf8fc21635d5228f24cb60307cad4d97bcfe8a21d3a1"),
    "TEST_ONLY_NEW_API": (182, "e511b2a113c7a6ee9b090734d34370cab680b31149b3bf2ba7520b5adbe8848a"),
    "MAIN_WARNING": (161, "a77ff550efc6a8bebae605a88055637b22b4700675b1890a660e7eba317516a4"),
    "SECOND_WARNING": (171, "b8e7d0b156816f0cfce0ef5c4b0795a535731e635aa60e4f3d59b5038fb9599f"),
    "NEW_ERROR_SOURCE": (176, "d8fae8c9ec316a4cbe871a236b409bd5d6b7bb7b644e92b3388d2f66f815324f"),
    "REVIEWED_WARNING_BASELINE": (
        335,
        "9ffe90f35b31fe4f245617d6d32799174ecf5fbd83bc611b96639868769c0557",
    ),
}
REVIEWED_R21_USES = [
    (154, "subclass", "com.gasstation.buildlogic.AndroidLintWarningPromotionTest#warningPromotionFailsForApplicationAndLibrary", "MAIN_WARNING"),
    (181, "subclass", "com.gasstation.buildlogic.AndroidLintBaselineIsolationTest#reviewedBaselineSuppressesOnlyItsExactWarningLocation", "MAIN_WARNING"),
    (182, "subclass", "com.gasstation.buildlogic.AndroidLintBaselineIsolationTest#reviewedBaselineSuppressesOnlyItsExactWarningLocation", "REVIEWED_WARNING_BASELINE"),
    (189, "subclass", "com.gasstation.buildlogic.AndroidLintBaselineIsolationTest#reviewedBaselineSuppressesOnlyItsExactWarningLocation", "SECOND_WARNING"),
    (206, "subclass", "com.gasstation.buildlogic.AndroidLintBaselineIsolationTest#reviewedWarningBaselineDoesNotHideANewError", "MAIN_WARNING"),
    (207, "subclass", "com.gasstation.buildlogic.AndroidLintBaselineIsolationTest#reviewedWarningBaselineDoesNotHideANewError", "NEW_ERROR_SOURCE"),
    (208, "subclass", "com.gasstation.buildlogic.AndroidLintBaselineIsolationTest#reviewedWarningBaselineDoesNotHideANewError", "REVIEWED_WARNING_BASELINE"),
    (238, "support-local", "com.gasstation.buildlogic.AndroidLintConventionTestSupport#newLintProject", "MAIN_SOURCE"),
    (239, "support-local", "com.gasstation.buildlogic.AndroidLintConventionTestSupport#newLintProject", "TEST_ONLY_NEW_API"),
    (244, "support-local", "com.gasstation.buildlogic.AndroidLintConventionTestSupport#newLintMultiProject", "MAIN_SOURCE"),
    (245, "support-local", "com.gasstation.buildlogic.AndroidLintConventionTestSupport#newLintMultiProject", "TEST_ONLY_NEW_API"),
]
REVIEWED_R21_DURATION_SOURCES = [
    {
        "commit": "3699f5773f4f6564f216d7228eb0b18cce6f970d",
        "id": "current-3699",
        "relativePath": ".codex/task-cache/gasstation-task9-linux-amd64/3699f5773f4f6564f216d7228eb0b18cce6f970d/attempt-000001/testkit-failures/metadata-capture-1/summary.json",
        "sha256": "afe2a00bc9de2d62d930f1bd433e4c9537046042a59d42b8687812b4691dd7e8",
    },
    {
        "commit": "67ebd6158572f1440633794411e59bc2da36bd2b",
        "id": "sealed-67eb",
        "relativePath": ".codex/task-cache/gasstation-task9-linux-amd64/67ebd6158572f1440633794411e59bc2da36bd2b/attempt-000001/testkit-failures/metadata-capture-1/summary.json",
        "sha256": "d4b378dade1a46b57d35bc9e98d82595301f563cbaaf1cfdc2b1d46350c9f938",
    },
    {
        "commit": "a5fd012a1f06e8b263ebc175d715db58abf0a436",
        "id": "sealed-a5",
        "relativePath": ".codex/task-cache/gasstation-task9-linux-amd64/a5fd012a1f06e8b263ebc175d715db58abf0a436/attempt-000001/testkit-failures/metadata-capture-1/summary.json",
        "sha256": "54511666186e364d59c70aeaae8dd774495a6faf0bb8eb3d70cbc4cfaf327a97",
    },
]
REVIEWED_R21_LOCAL_CORROBORATIONS = [
    {
        "durationSeconds": "34.714",
        "method": "com.gasstation.buildlogic.quality.coverage.CoveragePreparedClassesTest#preparedClassProducerRejectsTraversalDuplicatesAndRemovesStaleInputs",
        "relativePath": "build-logic/convention/build/test-results/test/TEST-com.gasstation.buildlogic.quality.coverage.CoveragePreparedClassesTest.xml",
        "sha256": "cbdbdc23e13bee786deaea1e9b12b2220b105d609f61c22f71eaf2f22cdd6a38",
    },
    {
        "durationSeconds": "35.763",
        "method": "com.gasstation.buildlogic.quality.coverage.CoverageReportMutationTest#typedXmlReportTaskRejectsLiveCardinalityIdentityExecAndClassMutations",
        "relativePath": "build-logic/convention/build/test-results/test/TEST-com.gasstation.buildlogic.quality.coverage.CoverageReportMutationTest.xml",
        "sha256": "2478732317905d427e94c7d49b313769fcacf7d685f61ad8917710e0721a915b",
    },
]


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _canonical_json(value: object) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, allow_nan=False, sort_keys=True, separators=(",", ":")) + "\n"
    ).encode()


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
    normalized = re.sub(r"(?m)^(?:internal|private|protected)\s+", "", normalized)
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


def kotlin_import_inventory_source(source: str) -> list[dict[str, str]]:
    if "\r" in source:
        raise DecompositionError("Kotlin source is not LF-only")
    masked_lines = _masked_kotlin(source).splitlines()
    source_lines = source.splitlines()
    import_lines = [
        source_line
        for source_line, masked_line in zip(source_lines, masked_lines, strict=True)
        if re.match(r"^\s*import\b", masked_line)
    ]
    pattern = re.compile(
        r"^\s*import\s+([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_*][A-Za-z0-9_]*)*)"
        r"(?:\s+as\s+([A-Za-z_][A-Za-z0-9_]*))?\s*$",
    )
    inventory: list[dict[str, str]] = []
    for line in import_lines:
        match = pattern.fullmatch(line)
        if match is None:
            raise DecompositionError("Kotlin import declaration is not canonicalizable")
        inventory.append({"alias": match.group(2) or "", "target": match.group(1)})
    inventory.sort(key=lambda row: (row["target"], row["alias"]))
    identities = [(row["target"], row["alias"]) for row in inventory]
    if len(identities) != len(set(identities)):
        raise DecompositionError("duplicate Kotlin import target/alias")
    return inventory


def kotlin_import_sha256(path: Path) -> str:
    return _sha256(_canonical_json(kotlin_import_inventory_source(path.read_text(encoding="utf-8"))))


def support_annotation_inventory_source(source: str, class_name: str) -> list[dict[str, str]]:
    masked = _masked_kotlin(source)
    depths = _depths(masked)
    _, class_open, class_close = _class_span(masked, class_name)
    declarations: list[tuple[int, int, str]] = []

    function_pattern = re.compile(
        r"\bfun\s+((?:[A-Za-z_][A-Za-z0-9_<>?,. ]*\.)?[A-Za-z_][A-Za-z0-9_]*)\s*\(([^)]*)\)",
    )
    for match in function_pattern.finditer(masked, class_open + 1, class_close):
        if depths[match.start()] == 1:
            key = "fun:" + re.sub(r"\s+", " ", f"{match.group(1)}({match.group(2)})").strip()
            declarations.append((match.start(), 1, key))

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
            if depths[match.start()] == member_depth:
                declarations.append((match.start(), member_depth, f"{prefix}:{match.group(1)}"))
    declarations.sort()

    annotation_pattern = re.compile(
        r"@[A-Za-z_][A-Za-z0-9_]*(?::[A-Za-z_][A-Za-z0-9_]*)?(?:\s*\([^()\n]*\))?",
    )
    inventory: list[dict[str, str]] = []
    for match in annotation_pattern.finditer(masked, class_open + 1, class_close):
        member_depth = depths[match.start()]
        if member_depth not in {1, 2}:
            continue
        following = [row for row in declarations if row[0] > match.end() and row[1] == member_depth]
        if not following:
            raise DecompositionError("support annotation has no following declaration")
        declaration_start, _, declaration = following[0]
        gap = masked[match.end() : declaration_start]
        gap = annotation_pattern.sub("", gap)
        gap = re.sub(
            r"\b(?:abstract|const|final|internal|lateinit|open|override|private|protected|public)\b",
            "",
            gap,
        )
        if gap.strip():
            raise DecompositionError("support annotation is detached from its declaration")
        inventory.append(
            {
                "annotation": re.sub(r"\s+", "", source[match.start() : match.end()]),
                "declaration": declaration,
            },
        )
    return sorted(inventory, key=lambda row: (row["declaration"], row["annotation"]))


def support_annotation_sha256(path: Path, class_name: str) -> str:
    return _sha256(
        _canonical_json(
            support_annotation_inventory_source(path.read_text(encoding="utf-8"), class_name),
        ),
    )


def _tokens_before(masked: str, position: int) -> list[str]:
    start = masked.rfind("\n", 0, position) + 1
    prefix = re.sub(
        r"@[A-Za-z_][A-Za-z0-9_]*(?::[A-Za-z_][A-Za-z0-9_]*)?(?:\s*\([^()\n]*\))?",
        " ",
        masked[start:position],
    )
    return re.findall(r"[A-Za-z_][A-Za-z0-9_]*", prefix)


def _access_attributes(tokens: list[str]) -> tuple[str, list[str]]:
    visibility = next(
        (token for token in tokens if token in {"public", "private", "protected", "internal"}),
        "implicit-public",
    )
    modifiers = sorted(
        token
        for token in tokens
        if token in {"abstract", "const", "final", "lateinit", "open", "override"}
    )
    return visibility, modifiers


def support_access_inventory_source(source: str, class_name: str) -> list[dict[str, object]]:
    masked = _masked_kotlin(source)
    depths = _depths(masked)
    class_start, class_open, class_close = _class_span(masked, class_name)
    rows: list[dict[str, object]] = []

    class_match = re.search(r"\bclass\s+" + re.escape(class_name) + r"\b", masked[class_start:class_open])
    if class_match is None:
        raise DecompositionError(f"support class token is missing: {class_name}")
    class_token = class_start + class_match.start()
    visibility, modifiers = _access_attributes(_tokens_before(masked, class_token))
    rows.append(
        {"declaration": f"class:{class_name}", "modifiers": modifiers, "visibility": visibility},
    )

    spans = [(class_open + 1, class_close, 1, "property")]
    companion = re.search(r"\bcompanion\s+object\s*\{", masked[class_open + 1 : class_close])
    if companion is not None:
        companion_start = class_open + 1 + companion.start()
        companion_open = masked.find("{", companion_start, class_close)
        companion_close = _matching_brace(masked, companion_open)
        visibility, modifiers = _access_attributes(_tokens_before(masked, companion_start))
        rows.append(
            {"declaration": "companion:Companion", "modifiers": modifiers, "visibility": visibility},
        )
        spans.append((companion_open + 1, companion_close, 2, "companion"))

    function_pattern = re.compile(
        r"\bfun\s+((?:[A-Za-z_][A-Za-z0-9_<>?,. ]*\.)?[A-Za-z_][A-Za-z0-9_]*)\s*\(([^)]*)\)",
    )
    for match in function_pattern.finditer(masked, class_open + 1, class_close):
        if depths[match.start()] != 1:
            continue
        declaration = "fun:" + re.sub(
            r"\s+",
            " ",
            f"{match.group(1)}({match.group(2)})",
        ).strip()
        visibility, modifiers = _access_attributes(_tokens_before(masked, match.start()))
        rows.append(
            {"declaration": declaration, "modifiers": modifiers, "visibility": visibility},
        )

    property_pattern = re.compile(r"\bval\s+([A-Za-z_][A-Za-z0-9_]*)\b")
    for start, limit, member_depth, prefix in spans:
        for match in property_pattern.finditer(masked, start, limit):
            if depths[match.start()] != member_depth:
                continue
            visibility, modifiers = _access_attributes(_tokens_before(masked, match.start()))
            rows.append(
                {
                    "declaration": f"{prefix}:{match.group(1)}",
                    "modifiers": modifiers,
                    "visibility": visibility,
                },
            )
    return sorted(rows, key=lambda row: row["declaration"])


def support_access_sha256(path: Path, class_name: str) -> tuple[int, str]:
    inventory = support_access_inventory_source(path.read_text(encoding="utf-8"), class_name)
    return len(inventory), _sha256(_canonical_json(inventory))


def support_rule_inventory_source(source: str, class_name: str) -> list[dict[str, str]]:
    masked = _masked_kotlin(source)
    depths = _depths(masked)
    _, class_open, class_close = _class_span(masked, class_name)
    rule_pattern = re.compile(
        r"@get:Rule\s+val\s+temporaryFolder\s*=\s*TemporaryFolder\(\)",
    )
    matches = [
        match
        for match in rule_pattern.finditer(masked, class_open + 1, class_close)
        if depths[match.start()] == 1
    ]
    imports = kotlin_import_inventory_source(source)
    if len(matches) != 1 or {
        "alias": "",
        "target": "org.junit.rules.TemporaryFolder",
    } not in imports:
        raise DecompositionError("Round-21 TemporaryFolder rule differs")
    return [
        {
            "annotation": "@get:Rule",
            "initializer": "TemporaryFolder()",
            "kind": "val",
            "name": "temporaryFolder",
            "type": "org.junit.rules.TemporaryFolder",
        },
    ]


def support_rule_sha256(path: Path, class_name: str) -> str:
    return _sha256(
        _canonical_json(
            support_rule_inventory_source(path.read_text(encoding="utf-8"), class_name),
        ),
    )


def _trim_indent_value(raw: str) -> str:
    lines = raw.splitlines()
    while lines and not lines[0].strip():
        lines.pop(0)
    while lines and not lines[-1].strip():
        lines.pop()
    indent = min(len(line) - len(line.lstrip()) for line in lines if line.strip())
    return "\n".join(line[indent:] if line.strip() else "" for line in lines)


def round21_bridge_inventory_source(source: str) -> list[dict[str, object]]:
    if "@JvmStatic" in _masked_kotlin(source):
        raise DecompositionError("Round-21 bridge must remain unannotated")
    inventory: list[dict[str, object]] = []
    declaration_positions: list[int] = []
    for order, (name, (expected_bytes, expected_sha)) in enumerate(REVIEWED_R21_BRIDGE_VALUES.items(), 1):
        pattern = re.compile(
            rf"internal val {re.escape(name)}\s*=\s*\"\"\"(.*?)\"\"\"\.trimIndent\(\)",
            re.DOTALL,
        )
        matches = list(pattern.finditer(source))
        if len(matches) != 1:
            raise DecompositionError(f"Round-21 bridge declaration differs: {name}")
        declaration_positions.append(matches[0].start())
        value = _trim_indent_value(matches[0].group(1)).encode()
        if (len(value), _sha256(value)) != (expected_bytes, expected_sha):
            raise DecompositionError(f"Round-21 bridge value differs: {name}")
        inventory.append(
            {
                "annotations": [],
                "declaredType": "inferred-kotlin.String",
                "kind": "val",
                "name": name,
                "order": order,
                "owner": "com.gasstation.buildlogic.AndroidLintConventionTestSupport.Companion",
                "valueBytes": len(value),
                "valueSha256": _sha256(value),
                "visibility": "internal",
            },
        )
    if declaration_positions != sorted(declaration_positions):
        raise DecompositionError("Round-21 bridge declaration order differs")
    return inventory


def round21_use_inventory_source(source: str) -> list[dict[str, object]]:
    lines = source.splitlines()
    inventory = [
        {"consumer": consumer, "line": line, "member": member, "scope": scope}
        for line, scope, consumer, member in REVIEWED_R21_USES
    ]
    for line, _, _, member in REVIEWED_R21_USES:
        if line > len(lines) or member not in lines[line - 1]:
            raise DecompositionError(f"Round-21 bridge use differs at line {line}: {member}")
    masked = _masked_kotlin(source)
    for name in REVIEWED_R21_BRIDGE_VALUES:
        expected = 1 + sum(member == name for _, _, _, member in REVIEWED_R21_USES)
        if len(re.findall(rf"\b{re.escape(name)}\b", masked)) != expected:
            raise DecompositionError(f"Round-21 bridge use cardinality differs: {name}")
    return inventory


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


def _class_inventory_sha(keys: list[str]) -> str:
    by_owner: dict[str, list[str]] = {}
    for key in keys:
        owner, method = key.rsplit("#", 1)
        by_owner.setdefault(owner, []).append(method)
    rows = [
        f"{owner}\t{len(methods)}\t{','.join(sorted(methods))}"
        for owner, methods in sorted(by_owner.items())
    ]
    return _sha256(("\n".join(rows) + "\n").encode())


def _seconds(value: object, field: str, *, positive: bool = False) -> Decimal:
    if not isinstance(value, str) or re.fullmatch(r"[0-9]+\.[0-9]{3}", value) is None:
        raise DecompositionError(f"{field} must be a three-place seconds string")
    try:
        parsed = Decimal(value)
    except InvalidOperation as error:
        raise DecompositionError(f"{field} is not decimal seconds") from error
    if positive and parsed <= 0:
        raise DecompositionError(f"{field} must be positive")
    return parsed


def _round21_units(
    option: str,
    duration_by_current: Mapping[str, Decimal],
    new_by_old: Mapping[str, str],
) -> list[dict[str, Any]]:
    grouped: dict[str, list[tuple[str, Decimal]]] = {}
    for current_key, duration in duration_by_current.items():
        final_key = new_by_old.get(current_key, current_key)
        if option == "A":
            unit_id, member = current_key.rsplit("#", 1)[0], current_key
        elif option == "B":
            unit_id, member = final_key.rsplit("#", 1)[0], final_key
        elif option == "C":
            unit_id = member = final_key
        else:
            raise DecompositionError(f"unknown Round-21 option: {option}")
        grouped.setdefault(unit_id, []).append((member, duration))
    return [
        {
            "durationSeconds": f"{sum((duration for _, duration in members), Decimal('0.000')):.3f}",
            "members": sorted(member for member, _ in members),
            "unitId": unit_id,
        }
        for unit_id, members in sorted(grouped.items())
    ]


def _round21_schedule(units: list[dict[str, Any]]) -> list[dict[str, Any]]:
    workers: list[dict[str, Any]] = [
        {"duration": Decimal("0.000"), "units": [], "worker": worker}
        for worker in range(1, 6)
    ]
    for row in sorted(units, key=lambda item: (-Decimal(item["durationSeconds"]), item["unitId"])):
        worker = min(workers, key=lambda lane: (lane["duration"], lane["worker"]))
        worker["units"].append({"durationSeconds": row["durationSeconds"], "owner": row["unitId"]})
        worker["duration"] += Decimal(row["durationSeconds"])
    return [
        {
            "durationSeconds": f"{lane['duration']:.3f}",
            "units": lane["units"],
            "worker": lane["worker"],
        }
        for lane in workers
    ]


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


def _verify_round21(
    root: Path,
    value: object,
    final_methods: list[TestMethod],
) -> tuple[dict[str, TestMethod], dict[str, Any]]:
    expected_keys = {
        "boundSeconds",
        "durationLedger",
        "durationLedgerSha256",
        "durationSources",
        "expectedFinalClassCount",
        "expectedMovedMethodCount",
        "expectedTotalMethods",
        "expectedUnchangedMethodCount",
        "idealLowerBoundSeconds",
        "inventorySha256",
        "localCorroborations",
        "lpt",
        "mappingLedgerSha256",
        "mappings",
        "options",
        "schemaVersion",
        "selectedOption",
        "sourceFiles",
        "totalDurationSeconds",
    }
    if not isinstance(value, dict) or set(value) != expected_keys or value.get("schemaVersion") != 1:
        raise DecompositionError("Round-21 source-class rebalancing fields differ")
    exact_scalars = {
        "boundSeconds": "1620.000",
        "expectedFinalClassCount": 52,
        "expectedMovedMethodCount": 37,
        "expectedTotalMethods": 90,
        "expectedUnchangedMethodCount": 53,
        "idealLowerBoundSeconds": "1546.207",
        "mappingLedgerSha256": REVIEWED_R21_MAPPING_SHA256,
        "selectedOption": "B",
        "totalDurationSeconds": "7731.035",
    }
    if any(value.get(key) != expected for key, expected in exact_scalars.items()):
        raise DecompositionError("Round-21 exact scalar contract differs")
    for field in ("boundSeconds", "idealLowerBoundSeconds", "totalDurationSeconds"):
        _seconds(value[field], field, positive=True)

    mappings = value["mappings"]
    mapping_keys = {"annotation", "bodySha256", "newKey", "oldKey"}
    if not isinstance(mappings, list) or len(mappings) != 37:
        raise DecompositionError("Round-21 mapping must contain exact 37 rows")
    old_keys: list[str] = []
    new_keys: list[str] = []
    for index, row in enumerate(mappings):
        if not isinstance(row, dict) or set(row) != mapping_keys:
            raise DecompositionError(f"Round-21 mapping row {index} fields differ")
        if row.get("annotation") != "@Test":
            raise DecompositionError("Round-21 mapped annotation must remain @Test")
        _require_sha(row.get("bodySha256"), f"Round-21 mappings[{index}].bodySha256")
        old_key = row.get("oldKey")
        new_key = row.get("newKey")
        if not isinstance(old_key, str) or not isinstance(new_key, str) or "#" not in old_key or "#" not in new_key:
            raise DecompositionError("Round-21 mapping keys must be owner#method strings")
        if old_key.rsplit("#", 1)[1] != new_key.rsplit("#", 1)[1]:
            raise DecompositionError("implicit Round-21 test method rename is forbidden")
        old_keys.append(old_key)
        new_keys.append(new_key)
    if old_keys != sorted(set(old_keys)) or len(new_keys) != len(set(new_keys)):
        raise DecompositionError("Round-21 mapping is not a sorted bijection")
    if _sha256(_canonical_json(mappings)) != REVIEWED_R21_MAPPING_SHA256:
        raise DecompositionError("Round-21 mapping ledger differs")

    final_by_key = {method.key: method for method in final_methods}
    if len(final_methods) != 90 or len(final_by_key) != 90:
        raise DecompositionError("Round-21 final method inventory must remain exact 90")
    for row in mappings:
        method = final_by_key.get(row["newKey"])
        if method is None or method.body_sha256 != row["bodySha256"]:
            raise DecompositionError(f"Round-21 mapped method body/owner differs: {row['newKey']}")
    final_keys = sorted(final_by_key)
    unchanged = sorted(set(final_keys) - set(new_keys))
    if len(unchanged) != 53 or _inventory_sha(unchanged) != REVIEWED_R21_INVENTORY_SHA256["unchangedMethods"]:
        raise DecompositionError("one of the Round-21 unchanged 53 methods differs")
    if len({method.owner for method in final_methods}) != 52:
        raise DecompositionError("Round-21 final class inventory must remain exact 52")
    if _inventory_sha(final_keys) != REVIEWED_R21_INVENTORY_SHA256["finalMethods"]:
        raise DecompositionError("Round-21 final method inventory differs")
    if _class_inventory_sha(final_keys) != REVIEWED_R21_INVENTORY_SHA256["finalClasses"]:
        raise DecompositionError("Round-21 final class inventory differs")

    old_by_new = dict(zip(new_keys, old_keys, strict=True))
    current_by_key: dict[str, TestMethod] = {}
    for method in final_methods:
        current_key = old_by_new.get(method.key, method.key)
        owner, name = current_key.rsplit("#", 1)
        if current_key in current_by_key:
            raise DecompositionError("Round-21 inverse mapping collides")
        current_by_key[current_key] = TestMethod(owner=owner, name=name, body_sha256=method.body_sha256)
    current_keys = sorted(current_by_key)
    if len({method.owner for method in current_by_key.values()}) != 34:
        raise DecompositionError("Round-21 inverse class inventory must remain exact 34")
    if _inventory_sha(current_keys) != REVIEWED_R21_INVENTORY_SHA256["currentMethods"]:
        raise DecompositionError("Round-21 inverse method inventory differs")
    if _class_inventory_sha(current_keys) != REVIEWED_R21_INVENTORY_SHA256["currentClasses"]:
        raise DecompositionError("Round-21 inverse class inventory differs")
    inventory = value["inventorySha256"]
    if inventory != REVIEWED_R21_INVENTORY_SHA256:
        raise DecompositionError("Round-21 inventory fixed points differ")

    source_files = value["sourceFiles"]
    if not isinstance(source_files, list) or [row.get("path") for row in source_files if isinstance(row, dict)] != sorted(REVIEWED_R21_SOURCE_FILES):
        raise DecompositionError("Round-21 source-file registry differs")
    source_row_keys = {
        "currentSourceSha256",
        "currentSupportSha256",
        "finalSourceSha256",
        "path",
        "supportOwner",
    }
    access_count: dict[str, int] = {}
    access_sha256: dict[str, str] = {}
    annotation_sha256: dict[str, str] = {}
    import_sha256: dict[str, str] = {}
    rule_sha256: dict[str, str] = {}
    for index, row in enumerate(source_files):
        if not isinstance(row, dict) or set(row) != source_row_keys:
            raise DecompositionError(f"Round-21 source row {index} fields differ")
        current_sha, support_owner, support_sha = REVIEWED_R21_SOURCE_FILES[row["path"]]
        if row["currentSourceSha256"] != current_sha or row["supportOwner"] != support_owner or row["currentSupportSha256"] != support_sha:
            raise DecompositionError("Round-21 current source/support anchor differs")
        final_sha = _require_sha(row["finalSourceSha256"], "Round-21 finalSourceSha256")
        source_path = root / row["path"]
        if final_sha == current_sha or _sha256(source_path.read_bytes()) != final_sha:
            raise DecompositionError("Round-21 final source hash differs")
        if support_behavior_sha256(source_path, support_owner) != support_sha:
            raise DecompositionError("Round-21 helper/rule/fixture behavior differs")
        import_hash = kotlin_import_sha256(source_path)
        if import_hash != REVIEWED_R21_IMPORT_SHA256[row["path"]]:
            raise DecompositionError("Round-21 import target/alias envelope differs")
        annotation_hash = support_annotation_sha256(source_path, support_owner)
        if annotation_hash != REVIEWED_R21_SUPPORT_ANNOTATION_SHA256[row["path"]]:
            raise DecompositionError("Round-21 support annotation/rule envelope differs")
        count, access_hash = support_access_sha256(source_path, support_owner)
        if (count, access_hash) != REVIEWED_R21_ACCESS_SHA256[row["path"]]:
            raise DecompositionError("Round-21 support access envelope differs")
        rule_hash = support_rule_sha256(source_path, support_owner)
        if rule_hash != REVIEWED_R21_RULE_SHA256:
            raise DecompositionError("Round-21 TemporaryFolder rule envelope differs")
        access_count[row["path"]] = count
        access_sha256[row["path"]] = access_hash
        annotation_sha256[row["path"]] = annotation_hash
        import_sha256[row["path"]] = import_hash
        rule_sha256[row["path"]] = rule_hash

    android_source = (
        root / "build-logic/convention/src/test/kotlin/AndroidLintConventionPluginTest.kt"
    ).read_text(encoding="utf-8")
    bridge_sha256 = _sha256(_canonical_json(round21_bridge_inventory_source(android_source)))
    if bridge_sha256 != REVIEWED_R21_BRIDGE_SHA256:
        raise DecompositionError("Round-21 bridge ledger differs")
    use_sha256 = _sha256(_canonical_json(round21_use_inventory_source(android_source)))
    if use_sha256 != REVIEWED_R21_USE_SHA256:
        raise DecompositionError("Round-21 bridge use ledger differs")

    if value["durationSources"] != REVIEWED_R21_DURATION_SOURCES:
        raise DecompositionError("Round-21 duration source registry differs")
    source_identities = {(row["commit"], row["sha256"]) for row in REVIEWED_R21_DURATION_SOURCES}
    duration = value["durationLedger"]
    duration_row_keys = {"durationSeconds", "method", "sourceArtifactSha256", "sourceCommit", "sourceStatus"}
    if not isinstance(duration, list) or len(duration) != 90:
        raise DecompositionError("Round-21 duration ledger must contain exact 90 rows")
    duration_by_current: dict[str, Decimal] = {}
    for index, row in enumerate(duration):
        if not isinstance(row, dict) or set(row) != duration_row_keys:
            raise DecompositionError(f"Round-21 duration row {index} fields differ")
        method = row.get("method")
        if not isinstance(method, str) or method not in current_by_key or method in duration_by_current:
            raise DecompositionError("Round-21 duration method is unknown or duplicate")
        if row.get("sourceStatus") not in {"SUCCESS", "SKIPPED"}:
            raise DecompositionError("Round-21 duration source status differs")
        if (row.get("sourceCommit"), row.get("sourceArtifactSha256")) not in source_identities:
            raise DecompositionError("Round-21 duration source is unknown")
        duration_by_current[method] = _seconds(row.get("durationSeconds"), f"durationLedger[{index}]", positive=True)
    if [row["method"] for row in duration] != sorted(duration_by_current) or set(duration_by_current) != set(current_by_key):
        raise DecompositionError("Round-21 duration ledger order/membership differs")
    if _sha256(_canonical_json(duration)) != REVIEWED_R21_DURATION_SHA256 or value["durationLedgerSha256"] != REVIEWED_R21_DURATION_SHA256:
        raise DecompositionError("Round-21 duration ledger hash differs")
    if sum(duration_by_current.values(), Decimal("0.000")) != Decimal("7731.035"):
        raise DecompositionError("Round-21 total duration differs")

    lpt = value["lpt"]
    if lpt != {
        "durationOrder": "descending",
        "groupedUnitTieBreak": "unitId-code-point-ascending",
        "oneMethodUnitTieBreak": "fully-qualified-owner#method-code-point-ascending",
        "workerTieBreak": "total-ascending-then-worker-ascending",
        "workers": 5,
    }:
        raise DecompositionError("Round-21 LPT contract differs")
    option_contracts = {
        "A": ("rejected", "current-34-class-layout", "current-owner", 34, "1557.754"),
        "B": ("selected", "moderate-four-owner-source-decomposition", "final-owner", 52, "1572.483"),
        "C": ("rejected", "fully-split-90-one-method-unit-projection", "final-owner#method", 90, "1572.052"),
    }
    options = value["options"]
    option_keys = {
        "decision",
        "description",
        "id",
        "maximumSeconds",
        "membershipLedgerSha256",
        "schedule",
        "scheduleSha256",
        "unitCount",
        "unitDurationLedgerSha256",
        "unitIdentity",
        "units",
    }
    if not isinstance(options, list) or [row.get("id") for row in options if isinstance(row, dict)] != ["A", "B", "C"]:
        raise DecompositionError("Round-21 options differ")
    for option in options:
        if set(option) != option_keys:
            raise DecompositionError("Round-21 option fields differ")
        option_id = option["id"]
        decision, description, identity, count, maximum = option_contracts[option_id]
        if (option["decision"], option["description"], option["unitIdentity"], option["unitCount"], option["maximumSeconds"]) != (decision, description, identity, count, maximum):
            raise DecompositionError("Round-21 option identity differs")
        expected_units = _round21_units(option_id, duration_by_current, dict(zip(old_keys, new_keys, strict=True)))
        expected_schedule = _round21_schedule(expected_units)
        unit_rows = [{"durationSeconds": row["durationSeconds"], "owner": row["unitId"]} for row in expected_units]
        if option["units"] != expected_units or option["schedule"] != expected_schedule:
            raise DecompositionError("Round-21 membership/unit order or schedule differs")
        if option["membershipLedgerSha256"] != REVIEWED_R21_MEMBERSHIP_SHA256[option_id] or _sha256(_canonical_json(expected_units)) != REVIEWED_R21_MEMBERSHIP_SHA256[option_id]:
            raise DecompositionError("Round-21 membership hash differs")
        if option["unitDurationLedgerSha256"] != REVIEWED_R21_UNIT_SHA256[option_id] or _sha256(_canonical_json(unit_rows)) != REVIEWED_R21_UNIT_SHA256[option_id]:
            raise DecompositionError("Round-21 unit-duration hash differs")
        if option["scheduleSha256"] != REVIEWED_R21_SCHEDULE_SHA256[option_id] or _sha256(_canonical_json(expected_schedule)) != REVIEWED_R21_SCHEDULE_SHA256[option_id]:
            raise DecompositionError("Round-21 schedule hash differs")
        if max(Decimal(lane["durationSeconds"]) for lane in expected_schedule) != Decimal(maximum):
            raise DecompositionError("Round-21 projected maximum differs")
    selected = options[1]
    if Decimal(selected["maximumSeconds"]) > Decimal(value["boundSeconds"]):
        raise DecompositionError("Round-21 selected schedule exceeds bound")

    corroborations = value["localCorroborations"]
    if corroborations != REVIEWED_R21_LOCAL_CORROBORATIONS:
        raise DecompositionError("Round-21 local corroboration registry differs")
    if [row.get("method") for row in corroborations if isinstance(row, dict)] != sorted(row["method"] for row in corroborations):
        raise DecompositionError("Round-21 local corroborations are not sorted")
    for row in corroborations:
        if set(row) != {"durationSeconds", "method", "relativePath", "sha256"}:
            raise DecompositionError("Round-21 local corroboration fields differ")
        _seconds(row["durationSeconds"], "localCorroborations.durationSeconds", positive=True)
        _require_sha(row["sha256"], "localCorroborations.sha256")

    return current_by_key, {
        "round21AccessCount": access_count,
        "round21AccessSha256": access_sha256,
        "round21AnnotationSha256": annotation_sha256,
        "round21BridgeSha256": bridge_sha256,
        "round21FinalClassCount": len({method.owner for method in final_methods}),
        "round21ImportSha256": import_sha256,
        "round21MaximumSeconds": selected["maximumSeconds"],
        "round21MovedMethodCount": len(mappings),
        "round21RuleSha256": rule_sha256,
        "round21SelectedOption": value["selectedOption"],
        "round21UnchangedMethodCount": len(unchanged),
        "round21UseSha256": use_sha256,
    }


def verify_decomposition_data(root: Path, contract: Mapping[str, Any]) -> dict[str, Any]:
    expected_keys = {
        "baselineAffectedInventorySha256",
        "baselineCoverageSourceSha256",
        "baselineRootQualitySourceSha256",
        "expectedTotalMethods",
        "mappings",
        "round21SourceClassRebalancing",
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

    final_methods = _all_test_methods(root)
    current_by_key, round21_receipt = _verify_round21(
        root,
        contract.get("round21SourceClassRebalancing"),
        final_methods,
    )
    for row, new_key in zip(mappings, new_keys, strict=True):
        method = current_by_key.get(new_key)
        if method is None or method.body_sha256 != row["methodBodySha256"]:
            raise DecompositionError(f"mapped method body/owner differs: {new_key}")
    current_unchanged = sorted(set(current_by_key) - set(new_keys))
    if current_unchanged != unchanged:
        raise DecompositionError("one of the other 67 convention tests changed")
    if len(current_by_key) != 90:
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
        "totalMethodCount": len(final_methods),
        "unchangedMethodCount": len(current_unchanged),
        "unchangedMethodsSha256": _inventory_sha(current_unchanged),
        **round21_receipt,
    }


def verify_decomposition(root: Path, contract_path: Path) -> dict[str, Any]:
    return verify_decomposition_data(root, load_decomposition_contract(contract_path))
