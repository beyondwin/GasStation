from __future__ import annotations

import hashlib
import json
import os
import re
from pathlib import Path
from typing import Any, Iterable, Mapping
from urllib.parse import urlsplit


HEX64 = re.compile(r"^[0-9a-f]{64}$")
HEX40 = re.compile(r"^[0-9a-f]{40}$")
PROTECTED_OPTION_ENVIRONMENT = (
    "GRADLE_OPTS",
    "JAVA_OPTS",
    "JAVA_TOOL_OPTIONS",
    "JDK_JAVA_OPTIONS",
    "_JAVA_OPTIONS",
)
TOP_LEVEL_KEYS = {
    "actions",
    "android",
    "codecovCli",
    "dependencyVerification",
    "docsValidation",
    "evidence",
    "evidenceGradleEntrypoints",
    "evidenceSessionCommands",
    "gradleWrapper",
    "jdks",
    "reproducibleArtifact",
    "runner",
    "schemaVersion",
    "staticSourceHashes",
}


class BuildInputError(ValueError):
    """A checked build-input contract is malformed or does not match the repository."""


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise BuildInputError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def canonical_json_bytes(value: Any) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n"
    ).encode("utf-8")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _require_keys(value: Any, expected: set[str], context: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise BuildInputError(f"{context} must be an object")
    actual = set(value)
    if actual != expected:
        missing = sorted(expected - actual)
        unknown = sorted(actual - expected)
        details = []
        if missing:
            details.append(f"missing keys {missing}")
        if unknown:
            details.append(f"unknown keys {unknown}")
        raise BuildInputError(f"{context}: {'; '.join(details)}")
    return value


def _require_https(value: Any, context: str) -> str:
    if not isinstance(value, str):
        raise BuildInputError(f"{context} must be a string")
    parsed = urlsplit(value)
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
    ):
        raise BuildInputError(f"{context} must be a canonical HTTPS URL without userinfo/query/fragment")
    return value


def _require_sha256(value: Any, context: str) -> str:
    if not isinstance(value, str) or HEX64.fullmatch(value) is None:
        raise BuildInputError(f"{context} must be a lowercase SHA-256")
    return value


def _require_relative_path(value: Any, context: str) -> str:
    if not isinstance(value, str) or not value:
        raise BuildInputError(f"{context} must be a repository-relative path")
    path = Path(value)
    if path.is_absolute() or ".." in path.parts or value.startswith("~"):
        raise BuildInputError(f"{context} must be a repository-relative path")
    return path.as_posix()


def _require_sorted_unique(values: Any, context: str, *, key=None) -> list[Any]:
    if not isinstance(values, list):
        raise BuildInputError(f"{context} must be an array")
    ordered = sorted(values, key=key)
    if values != ordered:
        raise BuildInputError(f"{context} must be sorted")
    identities = [key(value) if key else canonical_json_bytes(value) for value in values]
    if len(identities) != len(set(identities)):
        raise BuildInputError(f"{context} must not contain duplicates")
    return values


def load_policy(path: Path, *, root: Path) -> dict[str, Any]:
    try:
        raw = path.read_bytes()
        text = raw.decode("utf-8")
    except (OSError, UnicodeDecodeError) as error:
        raise BuildInputError(f"policy is not readable UTF-8: {error}") from error
    try:
        value = json.loads(text, object_pairs_hook=_reject_duplicate_keys)
    except (json.JSONDecodeError, BuildInputError) as error:
        if isinstance(error, BuildInputError):
            raise
        raise BuildInputError(f"policy JSON is malformed: {error}") from error
    policy = _require_keys(value, TOP_LEVEL_KEYS, "policy")
    if raw != canonical_json_bytes(policy):
        raise BuildInputError("policy must use canonical JSON with one trailing newline")
    if type(policy["schemaVersion"]) is not int or policy["schemaVersion"] != 1:
        raise BuildInputError("schemaVersion must be integer 1")

    wrapper = _require_keys(
        policy["gradleWrapper"],
        {
            "distributionSha256",
            "distributionUrl",
            "networkTimeout",
            "retries",
            "retryBackOffMs",
            "validateDistributionUrl",
            "version",
            "wrapperJarPath",
            "wrapperJarSha256",
        },
        "gradleWrapper",
    )
    _require_https(wrapper["distributionUrl"], "gradleWrapper.distributionUrl")
    _require_sha256(wrapper["distributionSha256"], "gradleWrapper.distributionSha256")
    _require_sha256(wrapper["wrapperJarSha256"], "gradleWrapper.wrapperJarSha256")
    _require_relative_path(wrapper["wrapperJarPath"], "gradleWrapper.wrapperJarPath")
    if (
        wrapper["version"] != "9.6.1"
        or wrapper["networkTimeout"] != 10000
        or wrapper["retries"] != 0
        or wrapper["retryBackOffMs"] != 500
        or wrapper["validateDistributionUrl"] is not True
    ):
        raise BuildInputError("Gradle wrapper policy drift")

    dependency = policy["dependencyVerification"]
    dependency = _require_keys(
        dependency,
        {
            "allowedInitScripts",
            "bypassDenylist",
            "checksumAlgorithms",
            "configurationCache",
            "generationMatrix",
            "metadataPath",
            "mode",
            "nestedTestKit",
            "strictGroups",
            "verifyMetadata",
        },
        "dependencyVerification",
    )
    if dependency.get("mode") != "strict":
        raise BuildInputError("dependencyVerification.mode must be strict")
    if dependency.get("checksumAlgorithms") != ["sha256"]:
        raise BuildInputError("dependency verification must use SHA-256 only")
    if dependency.get("allowedInitScripts") != []:
        raise BuildInputError("allowed init-script list must be empty")
    _require_relative_path(dependency.get("metadataPath"), "dependencyVerification.metadataPath")
    if dependency["verifyMetadata"] is not True:
        raise BuildInputError("dependency verification must verify module metadata")
    if dependency["nestedTestKit"] != {
        "copyRootMetadata": True,
        "freshGradleHome": True,
        "rejectCallerOverrides": True,
        "sanitizedEnvironment": True,
    }:
        raise BuildInputError("nested TestKit verification contract drift")
    denylist = dependency["bypassDenylist"]
    if not isinstance(denylist, list) or set(denylist) != {
        "--dependency-verification " + "off|lenient",
        "-Dorg.gradle.dependency.verification=" + "off|lenient",
        "-" + "I|--init-" + "script",
        "ResolutionStrategy.disableDependency" + "Verification()",
        "disableDependency" + "Verification()",
    }:
        raise BuildInputError("dependency-verification bypass denylist drift")
    for section in ("generationMatrix", "configurationCache"):
        rows = dependency[section]
        if not isinstance(rows, list) or not rows:
            raise BuildInputError(f"dependencyVerification.{section} must be nonempty")
        for index, argv in enumerate(rows):
            if not isinstance(argv, list) or not argv or argv[0] != "./gradlew" or any(not isinstance(item, str) or not item for item in argv):
                raise BuildInputError(f"dependencyVerification.{section}[{index}] must be a literal wrapper argv")
    groups = _require_keys(dependency["strictGroups"], {"complete", "product-regressions"}, "dependencyVerification.strictGroups")
    for name, rows in groups.items():
        if not isinstance(rows, list) or not rows:
            raise BuildInputError(f"dependencyVerification.strictGroups.{name} must be nonempty")

    android = _require_keys(
        policy["android"],
        {"buildTools", "compileSdk", "minSdk", "packages", "targetSdk"},
        "android",
    )
    if (android["compileSdk"], android["minSdk"], android["targetSdk"], android["buildTools"]) != (37, 24, 36, "36.0.0"):
        raise BuildInputError("Android catalog/effective SDK contract drift")
    packages = _require_sorted_unique(
        android["packages"],
        "android.packages",
        key=lambda row: row.get("coordinate", "") if isinstance(row, dict) else "",
    )
    expected_coordinates = {
        "build-tools;36.0.0",
        "cmdline-tools;latest",
        "emulator",
        "platform-tools",
        "platforms;android-37",
        "system-images;android-24;google_apis;x86_64",
        "system-images;android-28;default;x86_64",
        "system-images;android-36;google_apis;x86_64",
    }
    if {row.get("coordinate") for row in packages if isinstance(row, dict)} != expected_coordinates:
        raise BuildInputError("Android package coordinate inventory drift")
    logical = {
        row["coordinate"]: row.get("logicalIdentity")
        for row in packages
        if isinstance(row, dict) and "logicalIdentity" in row
    }
    if logical != {
        "system-images;android-24;google_apis;x86_64": "system-images;android-24;google_apis;x86_64",
        "system-images;android-28;default;x86_64": "system-images;android-28;aosp;x86_64",
        "system-images;android-36;google_apis;x86_64": "system-images;android-36;google;x86_64",
    }:
        raise BuildInputError("Task-8 logical-to-installed system-image mapping drift")

    runner = policy["runner"]
    if runner != {
        "architecture": "x64",
        "label": "ubuntu-24.04",
        "mutableHostedImage": True,
        "os": "Linux",
    }:
        raise BuildInputError("runner must retain the explicit mutable ubuntu-24.04 Linux x64 boundary")

    docs = _require_keys(
        policy["docsValidation"],
        {
            "aggregateAlgorithm",
            "argv",
            "bridgePath",
            "bridgeSha256",
            "excludedRoots",
            "facadeCallable",
            "facadePath",
            "forbiddenRepositoryImportRoots",
            "loadedModuleRoots",
            "parentEdges",
            "receiptPath",
            "receiptSchemaVersion",
            "sourceRoots",
        },
        "docsValidation",
    )
    bridge = _require_relative_path(docs["bridgePath"], "docsValidation.bridgePath")
    _require_sha256(docs["bridgeSha256"], "docsValidation.bridgeSha256")
    if sha256_file(root / bridge) != docs["bridgeSha256"]:
        raise BuildInputError("stable docs bridge hash mismatch")
    if docs["argv"] != ["python3", bridge, "--check-gradle-tasks"]:
        raise BuildInputError("stable docs bridge argv drift")
    if docs["facadePath"] != "scripts/docs/validate.py" or docs["facadeCallable"] != "validate_repository(root: pathlib.Path, *, discovered_gradle_tasks: frozenset[str] | None) -> list[str]":
        raise BuildInputError("documentation facade seam drift")
    if docs["sourceRoots"] != ["scripts/docs/extensions"] or docs["loadedModuleRoots"] != ["scripts/docs"]:
        raise BuildInputError("documentation dynamic source closure roots drift")

    reproducible = _require_keys(
        policy["reproducibleArtifact"],
        {
            "artifactName",
            "buildCache",
            "configurationCache",
            "gradleHomes",
            "outputGlob",
            "outputIdentity",
            "receiptPath",
            "receiptSchemaVersion",
            "requiredCardinality",
            "signingSecretsAllowed",
            "sourceCopies",
            "strictDependencyVerification",
            "task",
            "unsigned",
        },
        "reproducibleArtifact",
    )
    if reproducible != {
        "artifactName": "reproducible-prod-release-receipt-{sourceSha}",
        "buildCache": False,
        "configurationCache": False,
        "gradleHomes": 2,
        "outputGlob": "app/build/outputs/apk/prod/release/*.apk",
        "outputIdentity": "prod-release-unsigned.apk",
        "receiptPath": "build/reports/build-inputs/reproducible-prod-release-receipt.json",
        "receiptSchemaVersion": 1,
        "requiredCardinality": 1,
        "signingSecretsAllowed": False,
        "sourceCopies": 2,
        "strictDependencyVerification": True,
        "task": ":app:assembleProdRelease",
        "unsigned": True,
    }:
        raise BuildInputError("reproducible unsigned APK policy drift")

    for role in ("compile", "runtime"):
        record = policy["jdks"].get(role) if isinstance(policy["jdks"], dict) else None
        if not isinstance(record, dict):
            raise BuildInputError(f"jdks.{role} is missing")
        for field in ("archiveSha256", "archiveUrl", "checksumUrl"):
            if field.endswith("Sha256"):
                _require_sha256(record.get(field), f"jdks.{role}.{field}")
            else:
                _require_https(record.get(field), f"jdks.{role}.{field}")
        if type(record.get("archiveSize")) is not int or record["archiveSize"] <= 0:
            raise BuildInputError(f"jdks.{role}.archiveSize must be a positive integer")
        if record.get("vendor") != "Eclipse Temurin" or record.get("os") != "Linux" or record.get("architecture") != "x64" or record.get("packageType") != "JDK" or record.get("vm") != "HotSpot":
            raise BuildInputError(f"jdks.{role} identity drift")
        exact = {
            "compile": {
                "archiveRoot": "jdk-17.0.20+8",
                "filename": "OpenJDK17U-jdk_x64_linux_hotspot_17.0.20_8.tar.gz",
                "major": 17,
                "version": "17.0.20+8",
            },
            "runtime": {
                "archiveRoot": "jdk-21.0.12.1+1",
                "filename": "OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz",
                "major": 21,
                "version": "21.0.12.1+1",
            },
        }[role]
        for field, expected in exact.items():
            if record.get(field) != expected:
                raise BuildInputError(f"jdks.{role}.{field} must equal the reviewed exact identity")

    actions = _require_keys(policy["actions"], {"transitiveUses", "workflowUses"}, "actions")
    action_keys = {
        "annotatedTag",
        "commit",
        "kind",
        "manifestPath",
        "manifestSha256",
        "officialRepositoryUrl",
        "owner",
        "parentChains",
        "path",
        "peeledCommit",
        "repository",
        "sourceTag",
    }
    action_identity = lambda row: (
        row.get("owner", ""), row.get("repository", ""), row.get("path", "")
    ) if isinstance(row, dict) else ("", "", "")
    seen_actions: set[tuple[str, str, str]] = set()
    for section in ("workflowUses", "transitiveUses"):
        rows = _require_sorted_unique(actions[section], f"actions.{section}", key=action_identity)
        for index, row in enumerate(rows):
            row = _require_keys(row, action_keys, f"actions.{section}[{index}]")
            identity = action_identity(row)
            if identity in seen_actions:
                raise BuildInputError(f"duplicate action identity across closure: {identity}")
            seen_actions.add(identity)
            if HEX40.fullmatch(str(row["commit"])) is None or row["peeledCommit"] != row["commit"]:
                raise BuildInputError(f"actions.{section}[{index}] commit identity is invalid")
            _require_https(row["officialRepositoryUrl"], f"actions.{section}[{index}].officialRepositoryUrl")
            expected_url = f"https://github.com/{row['owner']}/{row['repository']}"
            if row["officialRepositoryUrl"] != expected_url:
                raise BuildInputError(f"actions.{section}[{index}] official repository URL drift")
            _require_relative_path(row["manifestPath"], f"actions.{section}[{index}].manifestPath")
            _require_sha256(row["manifestSha256"], f"actions.{section}[{index}].manifestSha256")
            if row["kind"] not in {"node24", "composite"}:
                raise BuildInputError(f"actions.{section}[{index}] kind is unsupported")
            if not isinstance(row["parentChains"], list):
                raise BuildInputError(f"actions.{section}[{index}] parentChains must be an array")
    if len(seen_actions) != 6:
        raise BuildInputError("action closure must contain the reviewed six identities")

    entrypoints = _require_sorted_unique(
        policy["evidenceGradleEntrypoints"],
        "evidenceGradleEntrypoints",
        key=lambda row: row.get("id", "") if isinstance(row, dict) else "",
    )
    if not entrypoints:
        raise BuildInputError("evidenceGradleEntrypoints must not be empty")
    for index, row in enumerate(entrypoints):
        row = _require_keys(
            row,
            {"argv", "gradleHomeRole", "id", "owner", "relationship", "sourceSha256"},
            f"evidenceGradleEntrypoints[{index}]",
        )
        owner = _require_relative_path(row["owner"], f"evidenceGradleEntrypoints[{index}].owner")
        _require_sha256(row["sourceSha256"], f"evidenceGradleEntrypoints[{index}].sourceSha256")
        target = root / owner
        if not target.is_file() or sha256_file(target) != row["sourceSha256"]:
            raise BuildInputError(f"evidence entrypoint source hash mismatch: {owner}")
        if row["relationship"] not in {"direct", "nested", "receipt-consumer"}:
            raise BuildInputError(f"evidenceGradleEntrypoints[{index}] relationship is invalid")
        if row["gradleHomeRole"] not in {
            "command-fresh", "job-fresh", "none", "reproduce-a", "reproduce-b",
            "testkit-adversarial-fresh", "testkit-fresh",
        }:
            raise BuildInputError(f"evidenceGradleEntrypoints[{index}] Gradle-home role is invalid")
        if not isinstance(row["argv"], list) or not row["argv"] or any(not isinstance(item, str) or not item for item in row["argv"]):
            raise BuildInputError(f"evidenceGradleEntrypoints[{index}] argv is invalid")

    hashes = _require_sorted_unique(
        policy["staticSourceHashes"],
        "staticSourceHashes",
        key=lambda row: row.get("path") if isinstance(row, dict) else "",
    )
    for index, row in enumerate(hashes):
        row = _require_keys(row, {"path", "sha256"}, f"staticSourceHashes[{index}]")
        relative = _require_relative_path(row["path"], f"staticSourceHashes[{index}].path")
        _require_sha256(row["sha256"], f"staticSourceHashes[{index}].sha256")
        if relative == "scripts/docs/validate.py" or relative.startswith("scripts/docs/extensions/"):
            raise BuildInputError("docs facade/extensions must not appear in staticSourceHashes")
        target = (root / relative).resolve()
        if not target.is_file() or target.is_symlink() or not target.is_relative_to(root.resolve()):
            raise BuildInputError(f"static source is not a tracked regular repository file: {relative}")

    commands = _require_sorted_unique(policy["evidenceSessionCommands"], "evidenceSessionCommands")
    expected_commands = [
        ["scripts/agent/verify-room-schemas.sh"],
        ["scripts/agent/verify.sh", "auto"],
        ["scripts/agent/verify.sh", "docs"],
        ["python3", "scripts/quality/build_inputs/docs_gradle_validation_bridge.py", "--check-gradle-tasks"],
    ]
    if commands != sorted(expected_commands):
        raise BuildInputError("evidenceSessionCommands must contain the exact four governed commands")
    return policy


def verify_wrapper(root: Path, policy: Mapping[str, Any]) -> None:
    wrapper = policy["gradleWrapper"]
    properties_path = root / "gradle/wrapper/gradle-wrapper.properties"
    properties: dict[str, str] = {}
    for line in properties_path.read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            properties[key] = value.replace("\\:", ":")
    expected = {
        "distributionSha256Sum": wrapper["distributionSha256"],
        "distributionUrl": wrapper["distributionUrl"],
        "networkTimeout": str(wrapper["networkTimeout"]),
        "retries": str(wrapper["retries"]),
        "retryBackOffMs": str(wrapper["retryBackOffMs"]),
        "validateDistributionUrl": "true",
    }
    for key, value in expected.items():
        if properties.get(key) != value:
            raise BuildInputError(f"Gradle wrapper property mismatch: {key}")
    jar = root / wrapper["wrapperJarPath"]
    actual = sha256_file(jar)
    if actual != wrapper["wrapperJarSha256"]:
        raise BuildInputError("Gradle wrapper JAR SHA-256 mismatch")


def validate_protected_environment(
    environment: Mapping[str, str],
    *,
    compile_home: str,
    runtime_home: str,
    gradle_home: str,
) -> None:
    for name in PROTECTED_OPTION_ENVIRONMENT:
        if environment.get(name):
            raise BuildInputError(f"protected environment must be empty: {name}")
    for name, value in environment.items():
        if name.startswith("ORG_GRADLE_PROJECT_") and value:
            raise BuildInputError(f"protected environment must be empty: {name}")
    expected = {
        "JAVA_HOME": runtime_home,
        "JAVA_HOME_17_X64": compile_home,
        "JAVA_HOME_21_X64": runtime_home,
        "GRADLE_USER_HOME": gradle_home,
    }
    for name, value in expected.items():
        if environment.get(name) != value:
            raise BuildInputError(f"protected environment mismatch: {name}")
    path_entries = environment.get("PATH", "").split(os.pathsep)
    if not path_entries or Path(path_entries[0]) != Path(runtime_home) / "bin":
        raise BuildInputError("protected environment mismatch: PATH must start with runtime bin")


def _contains_init_script(argv: list[str]) -> bool:
    for token in argv:
        if token in {"-" + "I", "--init-" + "script"} or token.startswith("-" + "I") or token.startswith("--init-" + "script="):
            return True
    return False


def validate_gradle_arguments(argv: Iterable[str], *, allow_metadata_write: bool = False) -> None:
    values = list(argv)
    if not values or Path(values[0]).name != "gradlew":
        raise BuildInputError("governed Gradle argv must start with the checked wrapper")
    if _contains_init_script(values):
        raise BuildInputError("governed Gradle argv may not contain an init script")
    if any(
        "org.gradle.dependency.verification=" + "off" in token
        or "org.gradle.dependency.verification=" + "lenient" in token
        for token in values
    ):
        raise BuildInputError("dependency verification weakening is forbidden")
    strict = any(
        token == "--dependency-verification=strict"
        or (token == "--dependency-verification" and index + 1 < len(values) and values[index + 1] == "strict")
        for index, token in enumerate(values)
    )
    if not strict:
        raise BuildInputError("governed Gradle argv must request strict dependency verification")
    if not allow_metadata_write and any(token == "--write-verification-metadata" or token.startswith("--write-verification-metadata=") for token in values):
        raise BuildInputError("metadata writes are forbidden in ordinary governed Gradle argv")
    required = {
        "-Dorg.gradle.java.installations.auto-detect=false",
        "-Dorg.gradle.java.installations.auto-download=false",
    }
    if not required.issubset(values) or not any(token.startswith("-Dorg.gradle.java.installations.paths=") for token in values):
        raise BuildInputError("governed Gradle argv must seal toolchain discovery")


_ACTIVE_SOURCE_SUFFIXES = {
    ".gradle",
    ".groovy",
    ".java",
    ".kts",
    ".kt",
    ".properties",
    ".py",
    ".sh",
    ".toml",
    ".yaml",
    ".yml",
}
_EXCLUDED_SOURCE_PARTS = {
    ".git",
    ".gradle",
    "build",
    "fixtures",
    "testFixtures",
    "tests",
}
_BYPASS_PATTERNS = (
    re.compile(r"(?<![A-Za-z0-9_])(?:[A-Za-z0-9_.]+\.)?disableDependencyVerification\s*\("),
    re.compile(r"org\.gradle\.dependency\.verification\s*(?:=|:)\s*(?:off|lenient)\b", re.IGNORECASE),
    re.compile(r"--dependency-verification(?:\s+|=)(?:off|lenient)\b", re.IGNORECASE),
    re.compile(r"(?:^|[\s'\"])(?:-I[^\s'\",)]+|--init-script(?:\s+|=)\S+)", re.MULTILINE),
)
_REGISTERED_TESTKIT_CONSTRUCTOR = (
    "build-logic/convention/src/test/kotlin/fixtures/GradlePluginTestProject.kt"
)
_TESTKIT_CONSTRUCTION = re.compile(r"(?:GradleRunner\s*\.\s*create|\.\s*withArguments)\s*\(")


def _active_source_paths(root: Path) -> list[Path]:
    paths: list[Path] = []
    for path in root.rglob("*"):
        if not path.is_file() or path.is_symlink():
            continue
        relative = path.relative_to(root)
        if any(part in _EXCLUDED_SOURCE_PARTS for part in relative.parts[:-1]):
            continue
        if path.name in {"gradlew", "gradlew.bat"} or path.suffix in _ACTIVE_SOURCE_SUFFIXES:
            paths.append(path)
    return sorted(paths)


def _without_comment_only_lines(text: str) -> str:
    output: list[str] = []
    block = False
    for line in text.splitlines(keepends=True):
        stripped = line.lstrip()
        if block:
            if "*/" in stripped:
                block = False
            output.append("\n" if line.endswith("\n") else "")
            continue
        if stripped.startswith("/*"):
            block = "*/" not in stripped
            output.append("\n" if line.endswith("\n") else "")
            continue
        if stripped.startswith(("#", "//", "@rem")):
            output.append("\n" if line.endswith("\n") else "")
            continue
        output.append(line)
    return "".join(output)


def _without_c_like_literals_and_comments(text: str) -> str:
    """Mask Kotlin/Java/Groovy literals while retaining lines and executable code."""

    output = list(text)
    index = 0
    state = "code"
    quote = ""
    while index < len(text):
        if state == "code":
            if text.startswith("//", index):
                output[index:index + 2] = "  "
                index += 2
                state = "line-comment"
                continue
            if text.startswith("/*", index):
                output[index:index + 2] = "  "
                index += 2
                state = "block-comment"
                continue
            if text.startswith('"""', index):
                output[index:index + 3] = "   "
                index += 3
                state = "triple-string"
                continue
            if text[index] in {'"', "'"}:
                quote = text[index]
                output[index] = " "
                index += 1
                state = "string"
                continue
            index += 1
            continue
        if state == "line-comment":
            if text[index] == "\n":
                state = "code"
            else:
                output[index] = " "
            index += 1
            continue
        if state == "block-comment":
            if text.startswith("*/", index):
                output[index:index + 2] = "  "
                index += 2
                state = "code"
            else:
                if text[index] != "\n":
                    output[index] = " "
                index += 1
            continue
        if state == "triple-string":
            if text.startswith('"""', index):
                output[index:index + 3] = "   "
                index += 3
                state = "code"
            else:
                if text[index] != "\n":
                    output[index] = " "
                index += 1
            continue
        if state == "string":
            if text[index] == "\\" and index + 1 < len(text):
                output[index] = " "
                if text[index + 1] != "\n":
                    output[index + 1] = " "
                index += 2
                continue
            if text[index] == quote:
                output[index] = " "
                index += 1
                state = "code"
                continue
            if text[index] != "\n":
                output[index] = " "
            index += 1
    return "".join(output)


def scan_dependency_verification_bypasses(root: Path) -> list[str]:
    issues: list[str] = []
    for path in _active_source_paths(root):
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        if path.suffix in {".gradle", ".groovy", ".java", ".kt", ".kts"}:
            executable = _without_c_like_literals_and_comments(text)
        else:
            executable = _without_comment_only_lines(text)
        relative = path.relative_to(root).as_posix()
        if (
            relative != _REGISTERED_TESTKIT_CONSTRUCTOR
            and (testkit := _TESTKIT_CONSTRUCTION.search(executable)) is not None
        ):
            line = executable.count("\n", 0, testkit.start()) + 1
            issues.append(
                f"{relative}:{line}: unregistered GradleRunner construction is forbidden",
            )
            continue
        matches = [match for pattern in _BYPASS_PATTERNS for match in pattern.finditer(executable)]
        if matches:
            first = min(matches, key=lambda match: match.start())
            line = executable.count("\n", 0, first.start()) + 1
            issues.append(
                f"{path.relative_to(root).as_posix()}:{line}: dependency verification bypass is forbidden",
            )
    return issues


_DYNAMIC_VERSION = re.compile(
    r"(?P<quote>['\"])(?P<value>[^'\"\r\n]*(?:\+|SNAPSHOT|latest|[\[\](,)\s][0-9]+\.[0-9]+)[^'\"\r\n]*)(?P=quote)",
    re.IGNORECASE,
)


def scan_dynamic_dependency_selectors(root: Path) -> list[str]:
    issues: list[str] = []
    candidates = [
        path
        for path in _active_source_paths(root)
        if path.name.endswith((".gradle", ".gradle.kts", ".versions.toml"))
        or path.name == "libs.versions.toml"
    ]
    for path in candidates:
        text = path.read_text(encoding="utf-8")
        for match in _DYNAMIC_VERSION.finditer(_without_comment_only_lines(text)):
            value = match.group("value")
            if value.startswith(("https://", "http://")):
                continue
            line = text.count("\n", 0, match.start()) + 1
            issues.append(
                f"{path.relative_to(root).as_posix()}:{line}: dynamic dependency selector: {value}",
            )
    return issues
