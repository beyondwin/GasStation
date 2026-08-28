#!/usr/bin/env python3
"""Check repository contracts used by the agent workflow.

Secret and artifact checks intentionally inspect Git-tracked files only. This
avoids reading ignored or untracked local credentials while still detecting an
accidental attempt to commit them.
"""

from __future__ import annotations

import argparse
import ast
import json
import os
import re
import shlex
import subprocess
import sys
from pathlib import Path


ROOT_LIVE_MARKDOWN = [
    "AGENTS.md", "README.md", "CONTRIBUTING.md", "CHANGELOG.md", ".impeccable.md",
]
DOC_LIVE_MARKDOWN = [
    "agent-workflow.md", "architecture.md", "build-velocity.md", "deployment.md",
    "module-contracts.md", "offline-strategy.md", "performance.md",
    "project-reading-guide.md", "security-trade-offs.md", "state-model.md",
    "test-strategy.md", "verification-matrix.md",
]
PERSONAL_PATH = re.compile(
    r"(?:/Users/[^/$\s]+/|/home/[^/$\s]+/|[A-Za-z]:\\Users\\[^\\\s]+\\)"
)
MARKDOWN_LINK = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
DIFF_CHECK_ISSUE = re.compile(r"^(.*?):(?:(\d+):)?\s*(.*)$")
REPO_HOOK_TARGET = re.compile(r"^(scripts/agent/[A-Za-z0-9_.-]+)$")
ROOT_HOOK_TARGET = re.compile(
    r"^\$\(git rev-parse --show-toplevel\)/(scripts/agent/[A-Za-z0-9_.-]+)$"
)
CI_REQUIRED_FILES = (
    ROOT_LIVE_MARKDOWN
    + [f"docs/{name}" for name in DOC_LIVE_MARKDOWN]
    + [
        ".codex/config.toml",
        ".codex/hooks.json",
        ".claude/settings.json",
        ".github/workflows/android.yml",
        ".github/workflows/device-evidence.yml",
        "config/quality/device-evidence-policy.json",
        "config/quality/device-evidence-quarantine.json",
        "docs/runbooks/device-verification.md",
        "docs/runbooks/build-input-provenance.md",
        "docs/AGENTS.md",
        "core/database/AGENTS.md",
        "benchmark/AGENTS.md",
        "scripts/agent/verify-room-schemas.sh",
        "config/quality/production-dependency-policy.txt",
        "build-logic/convention/src/main/kotlin/GasStationContractApiConvention.kt",
    ]
)
CI_REQUIRED_ANCHORS = {
    "AGENTS.md": ["scripts/agent/preflight.sh", "scripts/agent/verify.sh auto"],
    "docs/AGENTS.md": ["scripts/agent/verify.sh docs", "docs/superpowers/"],
    "core/database/AGENTS.md": [
        "StationSearchResult.hasCachedSnapshot",
        "fallbackToDestructiveMigration",
    ],
    "benchmark/AGENTS.md": ["demoBenchmark", "ANDROID_SERIAL"],
    ".codex/config.toml": ["hooks = true"],
    ".github/workflows/android.yml": [
        "fetch-depth: 0",
        "GASSTATION_CI_BASE_REF",
        "scripts/agent/verify-room-schemas.sh",
    ],
}
HOOK_CONFIG_EVENTS = {
    ".codex/hooks.json": {
        "SessionStart": "scripts/agent/preflight.sh",
        "PreToolUse": "scripts/agent/pre_tool_policy.py",
        "Stop": "scripts/agent/stop_check.py",
    },
    ".claude/settings.json": {
        "PreToolUse": "scripts/agent/pre_tool_policy.py",
        "PostToolUse": "scripts/agent/check-contracts.sh",
        "SubagentStop": "scripts/agent/check-contracts.sh",
    },
}
RELEASE_JOB = re.compile(
    r"(?ms)^  release-publish:\s*\n(?P<body>.*?)(?=^  [A-Za-z0-9_-]+:\s*$|\Z)"
)
RELEASE_JOB_ANCHORS = {
    "tag-only condition": "startsWith(github.ref, 'refs/tags/v')",
    "upstream verification dependencies": "needs:",
    "job-scoped release permission": "contents: write",
    "built artifact download": "actions/download-artifact@",
    "release-note source": "docs/release-notes/",
    "GitHub CLI authentication": "GH_TOKEN:",
    "GitHub Release creation": "gh release create",
    "APK release assets": "release-assets/*.apk",
    "portable checksum directory": "cd release-assets",
    "SHA-256 checksum creation": "sha256sum",
}
RELEASE_JOB_PREREQUISITES = {
    "agent-contracts",
    "static-analysis",
    "lint-tests",
    "unit-tests",
    "screenshot-tests",
    "assemble",
    "release-assemble",
    "coverage",
}
WORKFLOW_JOB_TEMPLATE = (
    r"(?ms)^  {job}:\s*\n(?P<body>.*?)(?=^  [A-Za-z0-9_-]+:\s*$|\Z)"
)
QUALITY_ABI_TASKS = [
    ":core:model:checkKotlinAbi",
    ":core:observability:checkKotlinAbi",
    ":domain:location:checkKotlinAbi",
    ":domain:settings:checkKotlinAbi",
    ":domain:station:checkKotlinAbi",
]
QUALITY_REPORT_PATHS = [
    "build/reports/quality/module-boundaries.json",
    "build/reports/quality/production-dependency-graph.json",
    "build/reports/quality/public-api-boundaries.json",
]
ABI_DUMP_MAPPINGS = [
    (":core:model", "core/model/api/model.api"),
    (":core:observability", "core/observability/api/observability.api"),
    (":domain:location", "domain/location/api/location.api"),
    (":domain:settings", "domain/settings/api/settings.api"),
    (":domain:station", "domain/station/api/station.api"),
]
FORBIDDEN_PUBLIC_API_FAMILIES = [
    "android.*",
    "androidx.*",
    "com.google.android.gms.*",
    "retrofit2.*",
    "okhttp3.*",
    "com.google.gson.*",
]
ABI_MAPPING_BLOCK = [f"{module}|{dump}" for module, dump in ABI_DUMP_MAPPINGS]
QUALITY_VERIFICATION_BLOCK = [
    *QUALITY_ABI_TASKS,
    "verifyPublicApiBoundaries",
    "verifyModuleBoundaries",
    "productionDependencyInventory",
]
ABI_UPDATE_OPERATOR_BLOCK = [task.replace("checkKotlinAbi", "updateKotlinAbi") for task in QUALITY_ABI_TASKS]
LINT_JOB_CONTRACTS = {
    "static-analysis": {
        "property": "-Pgasstation.lintTestSources=false",
        "artifact": "lint-production-reports",
        "arguments": [
            "spotlessCheck",
            ":app:lintDemoDebug",
            ":app:lintProdDebug",
            "lint",
            *QUALITY_ABI_TASKS,
            "verifyPublicApiBoundaries",
            "verifyModuleBoundaries",
            "productionDependencyInventory",
            "verifyNoDeprecatedComposeTestApis",
            "verifyCiRobolectricRuntime",
            "-Pgasstation.lintTestSources=false",
            "--warning-mode=fail",
            "--continue",
        ],
        "commands": {
            "Spotless": "spotlessCheck",
            "demo app lint task": ":app:lintDemoDebug",
            "prod app lint task": ":app:lintProdDebug",
            "five exact ABI checks": QUALITY_ABI_TASKS[0],
            "public API boundary guard": "verifyPublicApiBoundaries",
            "module boundary guard": "verifyModuleBoundaries",
            "resolved production dependency inventory": "productionDependencyInventory",
            "Compose test API guard": "verifyNoDeprecatedComposeTestApis",
            "Robolectric runtime guard": "verifyCiRobolectricRuntime",
        },
    },
    "lint-tests": {
        "property": "-Pgasstation.lintTestSources=true",
        "artifact": "lint-test-source-reports",
        "arguments": [
            ":app:lintDemoDebug",
            ":app:lintProdDebug",
            "lint",
            "-Pgasstation.lintTestSources=true",
            "--warning-mode=fail",
            "--continue",
        ],
        "commands": {
            "demo app lint task": ":app:lintDemoDebug",
            "prod app lint task": ":app:lintProdDebug",
        },
    },
}
CONVENTION_TEST_ARGUMENTS = [
    ":build-logic:convention:test",
    "--no-configuration-cache",
    "--warning-mode=fail",
]
COVERAGE_GRADLE_ARGUMENTS = [
    "coverageXmlReport",
    "verifyCoverageReport",
    "-Pgasstation.coverageSourceCommit=$GITHUB_SHA",
    "-Pgasstation.coverageEvent=$GASSTATION_COVERAGE_EVENT",
    "-Pgasstation.coverageBaseRef=$GASSTATION_COVERAGE_BASE_REF",
    "--warning-mode=fail",
]
COVERAGE_EVENT_EXPRESSION = (
    "${{ github.event_name == 'pull_request' && 'pull-request' || "
    "startsWith(github.ref, 'refs/tags/v') && 'tag' || 'main' }}"
)
COVERAGE_BASE_EXPRESSION = (
    "${{ github.event_name == 'pull_request' && github.event.pull_request.base.sha "
    "|| (github.ref == 'refs/heads/main' && github.event.before) || '' }}"
)
MUTATION_STAGE_SHELL = "/bin/bash --noprofile --norc -euo pipefail {0}"
MUTATION_RUN_SHELL = "/bin/bash --noprofile --norc -euo pipefail {0}"
MUTATION_STAGE_BODY = """umask 077
/bin/mkdir -p build/quality/pitest-runtime/bootstrap
set -C
/usr/bin/printf '%s\\n' "$JAVA_HOME_21_X64" > build/quality/pitest-runtime/bootstrap/java-home.selector"""
MUTATION_UPLOAD_PATHS = """build/reports/pitest/**
domain/*/build/reports/quality/pitest-configuration.json
domain/*/build/reports/pitest/**"""
MUTATION_CI_ENVIRONMENT = [
    ("CI_JAVA_TOOLCHAIN_VERSION", '"17.0.20+8"'),
    ("CI_JAVA_VERSION", '"21.0.12.1+1"'),
]
MUTATION_RUN_PREFIX = (
    "exec /usr/bin/env -i GASSTATION_PITEST_BOOTSTRAP=sealed-v1 \\\n"
    "  JAVA_HOME=\"$JAVA_HOME\" JAVA_HOME_17_X64=\"$JAVA_HOME_17_X64\" JAVA_HOME_21_X64=\"$JAVA_HOME_21_X64\" \\\n"
    "  PATH=\"$JAVA_HOME_21_X64/bin:/usr/bin:/bin:/usr/sbin:/sbin\" \\\n"
    "  LANG=C LC_ALL=C TZ=UTC TERM=dumb CI=true \\\n"
    "  "
)
MUTATION_PRIMARY_RUNS = {
    "Mutation (pull request)": (
        "github.event_name == pull_request",
        MUTATION_RUN_PREFIX + "scripts/quality/run_pitest.sh --event pull-request --base "
        "${{ github.event.pull_request.base.sha }} --java-home-file "
        "build/quality/pitest-runtime/bootstrap/java-home.selector",
    ),
    "Mutation (main)": (
        "github.event_name == push && github.ref == refs/heads/main",
        MUTATION_RUN_PREFIX + "scripts/quality/run_pitest.sh --event main --java-home-file "
        "build/quality/pitest-runtime/bootstrap/java-home.selector",
    ),
    "Mutation (tag)": (
        "github.event_name == push && startsWith(github.ref, refs/tags/v)",
        MUTATION_RUN_PREFIX + "scripts/quality/run_pitest.sh --event tag --java-home-file "
        "build/quality/pitest-runtime/bootstrap/java-home.selector",
    ),
}


def issue(path, line: int, message: str) -> str:
    """Format every checker failure with a stable path-and-line location."""
    return f"{path}:{line}: {message}"


def tracked_files(root: Path) -> list[Path]:
    """Return Git-indexed paths without inspecting ignored or untracked files."""
    output = subprocess.check_output(
        ["git", "-C", str(root), "ls-files", "-z"], text=True
    )
    return [root / line for line in output.split("\0") if line]


def check_live_links(root: Path) -> list[str]:
    issues: list[str] = []
    files = [root / name for name in ROOT_LIVE_MARKDOWN]
    files += [root / "docs" / name for name in DOC_LIVE_MARKDOWN]
    for directory in (root / "docs" / "adr", root / "docs" / "onboarding"):
        if directory.exists():
            files += sorted(directory.glob("*.md"))

    for file in files:
        if not file.exists():
            continue
        for line_number, line in enumerate(
            file.read_text(errors="replace").splitlines(), 1
        ):
            for raw in MARKDOWN_LINK.findall(line):
                target = raw.split("#", 1)[0].strip().strip("<>")
                if not target or target.startswith(("http://", "https://", "mailto:")):
                    continue
                if not (file.parent / target).resolve().exists():
                    issues.append(
                        issue(
                            file.relative_to(root),
                            line_number,
                            f"missing link target {target}",
                        )
                    )
    return issues


def check_documentation_contracts(root: Path) -> list[str]:
    """Delegate cataloged live-document checks to the focused validator."""
    catalog = root / "docs" / "documentation-catalog.json"
    if not catalog.is_file():
        return [issue("docs/documentation-catalog.json", 1, "documentation catalog missing")]
    governed = os.environ.get("GASSTATION_BUILD_INPUT_EVIDENCE") == "sealed-v1"
    bridge = root / "scripts/quality/build_inputs/docs_gradle_validation_bridge.py"
    validator = Path(__file__).resolve().parents[1] / "docs" / "validate.py"
    if governed and bridge.is_file():
        command = [sys.executable, str(bridge), "--check-gradle-tasks"]
    else:
        if not validator.is_file():
            return [issue("scripts/docs/validate.py", 1, "documentation validator missing")]
        command = [sys.executable, str(validator), "--root", str(root)]
    result = subprocess.run(
        command,
        cwd=root,
        text=True,
        capture_output=True,
    )
    if result.returncode == 0:
        return []
    issues = []
    for line in result.stderr.splitlines():
        if line.startswith("ERROR: "):
            issues.append(line.removeprefix("ERROR: "))
    if issues:
        return issues
    detail = (result.stderr or result.stdout).strip().splitlines()
    summary = detail[0][:200] if detail else "documentation validation failed"
    return [issue("docs/documentation-catalog.json", 1, summary)]


def check_build_contract(root: Path) -> list[str]:
    issues: list[str] = []
    settings = (root / "settings.gradle.kts").read_text()
    modules = re.findall(r'"(:[^"]+)"', settings)
    readme_file = root / "README.md"
    readme = readme_file.read_text() if readme_file.exists() else ""
    app_build_file = root / "app" / "build.gradle.kts"
    app_build = app_build_file.read_text() if app_build_file.exists() else ""
    version_name = re.search(r'versionName\s*=\s*"([^"]+)"', app_build)
    version_code = re.search(r"versionCode\s*=\s*(\d+)", app_build)
    compile_sdk = re.search(r"compileSdk\s*=\s*(\d+)", app_build)
    versions_file = root / "gradle" / "libs.versions.toml"
    if not compile_sdk and versions_file.exists():
        compile_sdk = re.search(
            r'(?m)^compileSdk\s*=\s*"(\d+)"', versions_file.read_text()
        )

    if readme and not re.search(rf"\b{len(modules)}-module\b", readme):
        issues.append(issue("README.md", 1, f"active module count is {len(modules)}"))

    module_contract_file = root / "docs" / "module-contracts.md"
    if module_contract_file.exists():
        module_contract = module_contract_file.read_text()
        for module in modules:
            documented = module[1:] if module.startswith(":") else module
            if f"`{documented}`" not in module_contract:
                issues.append(
                    issue(
                        "docs/module-contracts.md",
                        1,
                        f"active module missing: {module}",
                    )
                )

    if version_name and version_code and readme:
        expected = f"`{version_name.group(1)}` (`versionCode` {version_code.group(1)})"
        if expected not in readme:
            issues.append(issue("README.md", 1, f"expected current version {expected}"))

    if version_name and version_code:
        current_version = version_name.group(1)
        release_notes = sorted(
            (root / "docs" / "release-notes").glob(f"*-v{current_version}.md")
        )
        if len(release_notes) != 1:
            issues.append(
                issue(
                    "docs/release-notes",
                    1,
                    f"current version release note missing or ambiguous: v{current_version}",
                )
            )
        else:
            release_note = release_notes[0]
            release_note_text = release_note.read_text(errors="replace")
            expected_release_anchors = (
                f"`versionName` | `{current_version}`",
                f"`versionCode` | `{version_code.group(1)}`",
                f"릴리즈 태그 | `v{current_version}`",
            )
            for anchor in expected_release_anchors:
                if anchor not in release_note_text:
                    issues.append(
                        issue(
                            release_note.relative_to(root),
                            1,
                            f"current version release note contract missing: {anchor}",
                        )
                    )

    contributing_file = root / "CONTRIBUTING.md"
    contributing = contributing_file.read_text() if contributing_file.exists() else ""
    if contributing and "Java 21" not in contributing:
        issues.append(issue("CONTRIBUTING.md", 1, "Java 21+ contract missing"))
    if contributing and "Python 3.9" not in contributing:
        issues.append(
            issue("CONTRIBUTING.md", 1, "Python 3.9+ agent-tool contract missing")
        )
    if contributing and compile_sdk and f"SDK {compile_sdk.group(1)}" not in contributing:
        issues.append(
            issue(
                "CONTRIBUTING.md",
                1,
                f"Android SDK {compile_sdk.group(1)} contract missing",
            )
        )

    workflow = root / ".github" / "workflows" / "android.yml"
    if workflow.exists() and not re.search(
        r"(?m)^  agent-contracts:\s*$", workflow.read_text()
    ):
        issues.append(
            issue(
                ".github/workflows/android.yml",
                1,
                "agent-contracts job missing",
            )
        )
    return issues


def check_portable_agent_paths(root: Path) -> list[str]:
    issues: list[str] = []
    candidates = [
        path
        for path in tracked_files(root)
        if path.is_file()
        and path.name != "check_contracts.py"
        and "tests" not in path.parts
        and (
            ".codex" in path.parts
            or ".claude" in path.parts
            or ("scripts" in path.parts and "agent" in path.parts)
        )
    ]
    for file in candidates:
        for line_number, line in enumerate(
            file.read_text(errors="replace").splitlines(), 1
        ):
            if PERSONAL_PATH.search(line):
                issues.append(
                    issue(file.relative_to(root), line_number, "personal absolute path")
                )
    return issues


def check_secrets_and_artifacts(root: Path) -> list[str]:
    issues: list[str] = []
    artifact_names = {"local.properties", "keystore.properties"}
    artifact_suffixes = {
        ".jks", ".keystore", ".p12", ".pem", ".hprof", ".log", ".apk", ".aab",
    }
    for file in tracked_files(root):
        relative = file.relative_to(root)
        if (
            file.name in artifact_names
            or file.suffix in artifact_suffixes
            or any(part in {".worktrees", ".superpowers", ".gstack"} for part in relative.parts)
        ):
            issues.append(issue(relative, 1, "tracked local/generated artifact"))
        if file.name in {"gradle.properties", "keystore.properties"} or file.name.startswith(".env"):
            for line_number, line in enumerate(
                file.read_text(errors="replace").splitlines(), 1
            ):
                if re.match(
                    r"\s*(?:[A-Za-z0-9_.-]*(?:api.?key|password|secret|token)[A-Za-z0-9_.-]*)\s*=\s*[^\s#<]+",
                    line,
                    re.IGNORECASE,
                ):
                    issues.append(
                        issue(relative, line_number, "non-empty secret assignment")
                    )
    return issues


def check_shell_syntax(root: Path) -> list[str]:
    issues: list[str] = []
    for script in sorted((root / "scripts" / "agent").glob("*.sh")):
        relative_script = script.relative_to(root)
        result = subprocess.run(
            ["bash", "-n", str(relative_script)],
            cwd=root,
            text=True,
            capture_output=True,
        )
        if result.returncode:
            syntax_message = " ".join(result.stderr.split()) or "bash -n failed"
            issues.append(
                issue(
                    relative_script,
                    1,
                    f"shell syntax error: {syntax_message}",
                )
            )
    return issues


def hook_commands(value):
    if isinstance(value, dict):
        for key, nested in value.items():
            if key == "command" and isinstance(nested, str):
                yield nested
            else:
                yield from hook_commands(nested)
    elif isinstance(value, list):
        for nested in value:
            yield from hook_commands(nested)


def event_command_hooks(value):
    if not isinstance(value, list):
        return
    for group in value:
        if not isinstance(group, dict) or not isinstance(group.get("hooks"), list):
            continue
        for hook in group["hooks"]:
            if not isinstance(hook, dict):
                continue
            command = hook.get("command")
            if (
                hook.get("type") == "command"
                and isinstance(command, str)
                and command.strip()
            ):
                yield command


def exact_hook_target(token: str):
    for pattern in (REPO_HOOK_TARGET, ROOT_HOOK_TARGET):
        match = pattern.fullmatch(token)
        if match:
            return match.group(1)
    return None


def command_hook_targets(command: str) -> list[str]:
    try:
        words = shlex.split(command, comments=True, posix=True)
    except ValueError:
        return []
    index = 0
    while index < len(words) and re.match(r"^[A-Za-z_][A-Za-z0-9_]*=", words[index]):
        index += 1
    if index < len(words) and Path(words[index]).name == "command":
        index += 1
    if index < len(words) and Path(words[index]).name == "env":
        index += 1
        while index < len(words) and (
            re.match(r"^[A-Za-z_][A-Za-z0-9_]*=", words[index])
            or words[index].startswith("-")
        ):
            index += 1
    if index >= len(words):
        return []

    executable = Path(words[index]).name
    executable_target = exact_hook_target(words[index])
    if executable_target:
        return [executable_target]
    if executable not in {"bash", "sh", "python", "python3"}:
        return []
    for token in words[index + 1 :]:
        if token == "--command" or (
            token.startswith("-")
            and not token.startswith("--")
            and "c" in token[1:]
        ):
            return []
        if token == "--" or token.startswith(("-", "+")):
            continue
        target = exact_hook_target(token)
        return [target] if target else []
    return []


def command_named_targets(command: str) -> list[str]:
    try:
        words = shlex.split(command, comments=True, posix=True)
    except ValueError:
        return []
    return [target for word in words if (target := exact_hook_target(word))]


def source_line(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def yaml_scalar(value: str) -> str:
    """Return an active scalar value without treating comments as data."""
    try:
        words = shlex.split(value, comments=True, posix=True)
    except ValueError:
        return ""
    return " ".join(words)


def yaml_mapping_entry(line: str, indent: int) -> tuple[str, str] | None:
    """Parse one exact-indentation YAML mapping entry used by CI contracts."""
    if len(line) - len(line.lstrip(" ")) != indent:
        return None
    content = line[indent:]
    if not content or content.startswith("#") or ":" not in content:
        return None
    key, value = content.split(":", 1)
    if not re.fullmatch(r"[A-Za-z0-9_-]+", key):
        return None
    return key, yaml_scalar(value.strip())


def workflow_job_fields(body: str) -> dict[str, str]:
    return {
        key: value
        for line in body.splitlines()
        if (entry := yaml_mapping_entry(line, 4)) is not None
        for key, value in [entry]
    }


def workflow_job_raw_fields(body: str) -> dict[str, str]:
    """Retain scalar spelling where a contract permits one literal form only."""
    fields: dict[str, str] = {}
    for line in body.splitlines():
        if len(line) - len(line.lstrip(" ")) != 4:
            continue
        content = line[4:]
        if not content or content.startswith("#") or ":" not in content:
            continue
        key, value = content.split(":", 1)
        if re.fullmatch(r"[A-Za-z0-9_-]+", key):
            fields[key] = value.strip()
    return fields


def workflow_job_environment(body: str) -> dict[str, str]:
    lines = body.splitlines()
    for index, line in enumerate(lines):
        if yaml_mapping_entry(line, 4) == ("env", ""):
            environment: dict[str, str] = {}
            for child in lines[index + 1:]:
                entry = yaml_mapping_entry(child, 6)
                if entry is None:
                    break
                environment[entry[0]] = entry[1]
            return environment
    return {}


def workflow_top_level_environment(text: str) -> list[tuple[str, str]] | None:
    """Return the one exact workflow-level env mapping, preserving scalar spelling."""
    lines = text.splitlines()
    headers = [index for index, line in enumerate(lines) if line == "env:"]
    if len(headers) != 1:
        return None
    entries: list[tuple[str, str]] = []
    for line in lines[headers[0] + 1:]:
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if len(line) - len(line.lstrip(" ")) < 2:
            break
        if len(line) - len(line.lstrip(" ")) != 2 or ":" not in line:
            return None
        key, value = line[2:].split(":", 1)
        if not re.fullmatch(r"[A-Za-z0-9_-]+", key):
            return None
        entries.append((key, value.strip()))
    return entries


def workflow_setup_java_versions(text: str) -> tuple[int, list[str]]:
    """Return every parsed setup-java version plus the raw action count."""
    raw_count = len(
        re.findall(r"(?m)^\s+(?:-\s+)?uses:\s*actions/setup-java@v5\s*$", text)
    )
    versions: list[str] = []
    for match in re.finditer(
        WORKFLOW_JOB_TEMPLATE.format(job=r"[A-Za-z0-9_-]+"),
        text,
    ):
        for step in workflow_steps(match.group("body")):
            if step["fields"].get("uses") == "actions/setup-java@v5":
                versions.append(step["nested"].get("with", {}).get("java-version", ""))
    return raw_count, versions


def coverage_attempt_script_is_exact(script: str) -> bool:
    lines = script.splitlines()
    if len(lines) < 4 or lines[0] != "mkdir -p build/reports/coverage":
        return False
    if lines[1] != "python3 - <<'PY'" or lines[-1] != "PY":
        return False
    actual_python = "\n".join(lines[2:-1]).strip()
    expected_python = '''
import json
import os
from pathlib import Path

payload = {
    "baseRef": os.environ["GASSTATION_COVERAGE_BASE_REF"],
    "event": os.environ["GASSTATION_COVERAGE_EVENT"],
    "expectedTasks": ["coverageXmlReport", "verifyCoverageReport"],
    "policy": "config/quality/coverage-policy.json",
    "baseline": "config/quality/coverage-baseline.json",
    "schemaVersion": 1,
    "sourceCommit": os.environ["COVERAGE_SOURCE_SHA"],
}
Path("build/reports/coverage/coverage-attempt.json").write_text(
    json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\\n",
    encoding="utf-8",
)
'''.strip()
    try:
        actual = ast.dump(ast.parse(actual_python), include_attributes=False)
        expected = ast.dump(ast.parse(expected_python), include_attributes=False)
    except SyntaxError:
        return False
    return actual == expected


def workflow_steps(body: str) -> list[dict[str, object]]:
    """Parse the active fields needed from conventional GitHub Actions steps."""
    steps: list[dict[str, object]] = []
    lines = body.splitlines()
    index = 0
    while index < len(lines):
        line = lines[index]
        if not line.startswith("      - ") or line[8:].startswith("#"):
            index += 1
            continue

        fields: dict[str, str] = {}
        nested: dict[str, dict[str, str]] = {}
        first_entry = yaml_mapping_entry("        " + line[8:], 8)
        if first_entry is not None:
            fields[first_entry[0]] = first_entry[1]
        index += 1

        while index < len(lines) and not lines[index].startswith("      - "):
            current = lines[index]
            if current.startswith("  ") and not current.startswith("        "):
                break
            entry = yaml_mapping_entry(current, 8)
            if entry is None:
                index += 1
                continue
            key, value = entry
            fields[key] = value
            index += 1

            if key == "run" and value in {"|", "|-", "|+", ">", ">-", ">+"}:
                block_lines = []
                while index < len(lines):
                    block_line = lines[index]
                    if block_line.strip() and not block_line.startswith("          "):
                        break
                    block_lines.append(block_line[10:] if block_line.startswith("          ") else "")
                    index += 1
                fields[key] = "\n".join(block_lines)
                continue

            if not value:
                children: dict[str, str] = {}
                while index < len(lines):
                    child = yaml_mapping_entry(lines[index], 10)
                    if child is None:
                        break
                    child_key, child_value = child
                    children[child_key] = child_value
                    index += 1
                    if child_value in {"|", "|-", "|+", ">", ">-", ">+"}:
                        block_lines = []
                        while index < len(lines):
                            block_line = lines[index]
                            if block_line.strip() and not block_line.startswith("            "):
                                break
                            block_lines.append(
                                block_line[12:] if block_line.startswith("            ") else ""
                            )
                            index += 1
                        children[child_key] = "\n".join(block_lines)
                nested[key] = children

        steps.append({"fields": fields, "nested": nested})
    return steps


def shell_gradle_arguments(script: str) -> tuple[list[list[str]], bool]:
    """Return active Gradle arguments and whether the script is only that command."""
    logical_lines: list[str] = []
    pending = ""
    for raw_line in script.splitlines():
        stripped = raw_line.strip()
        if not pending and (not stripped or stripped.startswith("#")):
            continue
        if stripped.endswith("\\"):
            pending += stripped[:-1] + " "
            continue
        logical_lines.append(pending + stripped)
        pending = ""
    if pending:
        logical_lines.append(pending)

    invocations: list[list[str]] = []
    standalone = len(logical_lines) == 1
    for command in logical_lines:
        try:
            lexer = shlex.shlex(command, posix=True, punctuation_chars=";&|")
            lexer.whitespace_split = True
            lexer.commenters = "#"
            words = list(lexer)
        except ValueError:
            standalone = False
            continue
        launchers = {"./gradlew", "scripts/quality/build_inputs/run_gradle.sh"}
        if not words or words[0] not in launchers or any(
            word in {";", "&", "&&", "|", "||"} for word in words
        ):
            standalone = False
        segment: list[str] = []
        for word in words + [";"]:
            if word in {";", "&", "&&", "|", "||"}:
                if segment and segment[0] in launchers:
                    invocations.append(segment[1:])
                segment = []
            else:
                segment.append(word)
    return invocations, standalone and len(invocations) == 1


def normalize_gradle_arguments(arguments: list[str]) -> list[str]:
    """Normalize the one supported two-token Gradle option spelling."""
    normalized: list[str] = []
    index = 0
    while index < len(arguments):
        argument = arguments[index]
        if argument == "--warning-mode" and index + 1 < len(arguments):
            normalized.append(f"--warning-mode={arguments[index + 1]}")
            index += 2
            continue
        normalized.append(argument)
        index += 1
    return normalized


def check_mutation_workflow_contracts(root: Path) -> list[str]:
    """Validate the closed primary/scheduled mutation workflow transport."""
    policy_path = root / "config/quality/mutation-policy.json"
    primary_path = root / ".github/workflows/android.yml"
    scheduled_path = root / ".github/workflows/mutation-schedule.yml"
    issues: list[str] = []
    if not policy_path.is_file():
        return issues
    for path in (primary_path, scheduled_path):
        if not path.is_file():
            issues.append(issue(path.relative_to(root), 1, "mutation workflow missing"))
    if issues:
        return issues
    try:
        policy = json.loads(policy_path.read_text())
    except (json.JSONDecodeError, OSError) as error:
        return [issue("config/quality/mutation-policy.json", 1, f"invalid mutation policy: {error}")]

    phase = policy.get("enforcementPhase")
    if phase not in {"observe", "blocking"}:
        issues.append(issue("config/quality/mutation-policy.json", 1, "mutation enforcement phase is invalid"))
    linux = policy.get("bootstrapProfiles", {}).get("linux-x86_64", {})
    expected_linux = {
        "platform": "Linux",
        "architecture": "x86_64",
        "runnerLabel": "ubuntu-24.04",
        "kind": "github-hosted-image-observed-v1",
    }
    for key, expected in expected_linux.items():
        if linux.get(key) != expected:
            issues.append(issue("config/quality/mutation-policy.json", 1, f"Linux mutation profile {key} drifted"))
    expected_image = {
        "ImageOS": "ubuntu24",
        "ImageVersion": "20260816.277.1",
        "runnerImagesTag": "ubuntu24/20260816.277",
        "runnerImagesTagCommit": "3b5f596ffecb076aa5f3c3ded95b145f6daeb016",
        "inventoryAsset": "internal.ubuntu24.json",
        "inventoryAssetDigest": "sha256:35b3696018cc49cc1b307943091be1578a18771ee3e375632495d3a027216f19",
    }
    if linux.get("image") != expected_image:
        issues.append(issue("config/quality/mutation-policy.json", 1, "Linux mutation image identity drifted"))

    primary = primary_path.read_text(errors="replace")
    scheduled = scheduled_path.read_text(errors="replace")
    for text, path in (
        (primary, ".github/workflows/android.yml"),
        (scheduled, ".github/workflows/mutation-schedule.yml"),
    ):
        top_level_environment = workflow_top_level_environment(text)
        if top_level_environment != MUTATION_CI_ENVIRONMENT:
            issues.append(issue(path, 1, "workflow must declare the exact closed JDK role versions"))
        for name, _ in MUTATION_CI_ENVIRONMENT:
            if len(re.findall(rf"(?m)^\s*{name}:\s*", text)) != 1:
                issues.append(issue(path, 1, f"{name} must not be duplicated or shadowed"))
        if "actions/setup-java@" in text:
            issues.append(issue(path, 1, "setup-java is forbidden by the closed JDK contract"))
    if not all(anchor in primary for anchor in ("pull_request:", "branches:\n      - main", "tags:\n      - \"v*\"")):
        issues.append(issue(".github/workflows/android.yml", 1, "mutation primary trigger matrix drifted"))
    if 'cron: "17 3 * * 0"' not in scheduled:
        issues.append(issue(".github/workflows/mutation-schedule.yml", 1, "weekly mutation schedule drifted"))

    def job(text: str, path: str, job_name: str) -> tuple[str, int] | None:
        match = re.search(WORKFLOW_JOB_TEMPLATE.format(job=job_name), text)
        if match is None:
            issues.append(issue(path, 1, f"workflow job missing: {job_name}"))
            return None
        return match.group("body"), source_line(text, match.start())

    def common_steps(body: str, path: str, line: int, *, scheduled_job: bool) -> None:
        fields = workflow_job_fields(body)
        if fields.get("runs-on") != "ubuntu-24.04":
            issues.append(issue(path, line, "mutation runner must be exact ubuntu-24.04"))
        if fields.get("timeout-minutes") != "60":
            issues.append(issue(path, line, "mutation timeout must be 60 minutes"))
        if "if" in fields or "container" in fields:
            issues.append(issue(path, line, "mutation job must be unconditional and non-containerized"))
        if "continue-on-error" in fields and static_workflow_boolean(fields["continue-on-error"]) is not False:
            issues.append(issue(path, line, "mutation job must be blocking"))
        if workflow_job_environment(body):
            issues.append(issue(path, line, "mutation job must not transport values through env"))

        steps = workflow_steps(body)
        expected_count = 5 if scheduled_job else 7
        if len(steps) != expected_count:
            issues.append(issue(path, line, f"mutation job must contain exactly {expected_count} ordered steps"))
            return
        for step in steps:
            step_fields = step["fields"]
            step_nested = step["nested"]
            if "continue-on-error" in step_fields:
                issues.append(issue(path, line, "mutation steps must not declare continue-on-error"))
            if step_nested.get("env"):
                issues.append(issue(path, line, "mutation steps must not transport values through env"))

        checkout = steps[0]
        if (
            checkout["fields"].get("uses") != "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1"
            or checkout["nested"].get("with") != {"fetch-depth": "0", "persist-credentials": "false"}
        ):
            issues.append(issue(path, line, "mutation checkout must fetch full history"))
        setup = steps[1]
        if (
            setup["fields"].get("uses") != "./.github/actions/setup-build-inputs"
        ):
            issues.append(issue(path, line, "mutation closed JDK setup contract drifted"))
        stage = steps[2]["fields"]
        if (
            stage.get("name") != "Stage mutation Java selector"
            or stage.get("shell") != MUTATION_STAGE_SHELL
            or stage.get("run", "").strip() != MUTATION_STAGE_BODY
        ):
            issues.append(issue(path, line, "mutation selector staging contract drifted"))

        run_steps = steps[3:-1]
        expected_runs = (
            {"Mutation (schedule)": (None, MUTATION_RUN_PREFIX + "scripts/quality/run_pitest.sh --event schedule --java-home-file build/quality/pitest-runtime/bootstrap/java-home.selector")}
            if scheduled_job
            else MUTATION_PRIMARY_RUNS
        )
        if [step["fields"].get("name") for step in run_steps] != list(expected_runs):
            issues.append(issue(path, line, "mutation run-step order or names drifted"))
        else:
            for step in run_steps:
                run_fields = step["fields"]
                condition, command = expected_runs[run_fields["name"]]
                if run_fields.get("shell") != MUTATION_RUN_SHELL or run_fields.get("run", "").strip() != command:
                    issues.append(issue(path, line, f"{run_fields['name']} sealed command drifted"))
                if condition is None:
                    if "if" in run_fields:
                        issues.append(issue(path, line, "scheduled mutation run must be unconditional"))
                elif run_fields.get("if") != condition:
                    issues.append(issue(path, line, f"{run_fields['name']} routing condition drifted"))

        upload = steps[-1]
        expected_name = "pitest-scheduled-evidence" if scheduled_job else "pitest-evidence"
        if (
            upload["fields"].get("name") != "Upload mutation evidence"
            or upload["fields"].get("if") != "always()"
            or upload["fields"].get("uses") != "actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a"
            or upload["nested"].get("with")
            != {
                "name": expected_name,
                "path": MUTATION_UPLOAD_PATHS,
                "if-no-files-found": "error",
                "retention-days": "7",
            }
        ):
            issues.append(issue(path, line, "mutation evidence upload contract drifted"))

    primary_job = job(primary, ".github/workflows/android.yml", "mutation")
    if primary_job is not None:
        common_steps(primary_job[0], ".github/workflows/android.yml", primary_job[1], scheduled_job=False)
    scheduled_job = job(scheduled, ".github/workflows/mutation-schedule.yml", "mutation-scheduled")
    if scheduled_job is not None:
        common_steps(scheduled_job[0], ".github/workflows/mutation-schedule.yml", scheduled_job[1], scheduled_job=True)

    release = RELEASE_JOB.search(primary)
    if release is None:
        issues.append(issue(".github/workflows/android.yml", 1, "tag release publishing contract missing"))
    else:
        needs = re.search(r"(?m)^\s+needs:\s*\[([^\]]+)\]\s*$", release.group("body"))
        prerequisites = {value.strip() for value in needs.group(1).split(",")} if needs else set()
        if phase == "observe" and "mutation" in prerequisites:
            issues.append(issue(".github/workflows/android.yml", 1, "report-only mutation must not gate tag release"))
        if phase == "blocking" and "mutation" not in prerequisites:
            issues.append(issue(".github/workflows/android.yml", 1, "blocking mutation must gate tag release"))

    agent_test = root / "scripts/agent/test.sh"
    expected_quality_suite = (
        'PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover '
        '-s "$repo_root/scripts/quality/tests" -v'
    )
    if not agent_test.is_file() or agent_test.read_text().count(expected_quality_suite) != 1:
        issues.append(issue("scripts/agent/test.sh", 1, "quality Python suite must run exactly once"))
    verifier = root / "scripts/agent/verify.sh"
    verifier_text = verifier.read_text() if verifier.is_file() else ""
    if "verifyPitestConfiguration" in verifier_text or "pitestVerified" in verifier_text:
        issues.append(issue("scripts/agent/verify.sh", 1, "agent scopes must not bypass the routed PIT runner"))
    return issues


def static_workflow_boolean(value: str) -> bool | None:
    """Resolve only literal booleans and literal GitHub expression booleans."""
    normalized = value.strip().lower()
    expression = re.fullmatch(r"\$\{\{\s*(true|false)\s*\}\}", normalized)
    if expression is not None:
        normalized = expression.group(1)
    if normalized == "true":
        return True
    if normalized == "false":
        return False
    return None


def check_lint_workflow_contracts(workflow: str) -> list[str]:
    issues: list[str] = []
    workflow_path = ".github/workflows/android.yml"
    for job_name, contract in LINT_JOB_CONTRACTS.items():
        match = re.search(
            WORKFLOW_JOB_TEMPLATE.format(job=re.escape(job_name)),
            workflow,
        )
        if match is None:
            issues.append(issue(workflow_path, 1, f"workflow job missing: {job_name}"))
            continue

        body = match.group("body")
        job_line = source_line(workflow, match.start())
        job_fields = workflow_job_fields(body)
        job_raw_fields = workflow_job_raw_fields(body)
        expected_timeout = "60" if job_name == "static-analysis" else "30"
        if job_fields.get("timeout-minutes") != expected_timeout:
            issues.append(
                issue(workflow_path, job_line, f"{job_name} timeout must be {expected_timeout} minutes")
            )
        if "if" in job_fields:
            issues.append(
                issue(workflow_path, job_line, f"{job_name} job must not declare if")
            )
        if (
            "continue-on-error" in job_fields
            and job_raw_fields.get("continue-on-error") != "false"
        ):
            issues.append(issue(workflow_path, job_line, f"{job_name} must be blocking"))

        steps = workflow_steps(body)
        required_argument_values = {
            **{name: value for name, value in contract["commands"].items()},
            "root lint task": "lint",
            "test-source property": contract["property"],
            "Gradle warning policy": "--warning-mode=fail",
            "complete failure collection": "--continue",
        }
        parsed_runs = [
            shell_gradle_arguments(step["fields"].get("run", "")) for step in steps
        ]
        gradle_invocations = [
            (arguments, standalone)
            for invocations, standalone in parsed_runs
            for arguments in invocations
        ]
        convention_test_invocations = [
            (step, arguments, standalone)
            for step, (invocations, standalone) in zip(steps, parsed_runs)
            for arguments in invocations
            if normalize_gradle_arguments(arguments) == CONVENTION_TEST_ARGUMENTS
        ]
        lint_arguments = max(
            gradle_invocations,
            key=lambda invocation: sum(
                value in normalize_gradle_arguments(invocation[0])
                for value in required_argument_values.values()
            ),
            default=([], False),
        )
        normalized_arguments = normalize_gradle_arguments(lint_arguments[0])
        for name, argument in required_argument_values.items():
            if argument not in normalized_arguments:
                issues.append(
                    issue(workflow_path, job_line, f"{job_name} command missing: {name}")
                )
        if any(argument in {"--dry-run", "-m"} for argument in normalized_arguments):
            issues.append(
                issue(
                    workflow_path,
                    job_line,
                    f"{job_name} command must execute lint: dry-run option forbidden",
                )
            )
        if any(
            argument in {"-x", "--exclude-task"}
            or argument.startswith("--exclude-task=")
            for argument in normalized_arguments
        ):
            issues.append(
                issue(
                    workflow_path,
                    job_line,
                    f"{job_name} command must not exclude lint tasks",
                )
            )
        expected_invocation_count = 2 if job_name == "static-analysis" else 1
        if gradle_invocations and (
            len(gradle_invocations) != expected_invocation_count or not lint_arguments[1]
        ):
            issues.append(
                issue(
                    workflow_path,
                    job_line,
                    f"{job_name} command must be one standalone ./gradlew invocation",
                )
            )
        if job_name == "static-analysis":
            lint_step = max(
                (
                    step
                    for step, (invocations, _) in zip(steps, parsed_runs)
                    if any(
                        normalize_gradle_arguments(arguments) == contract["arguments"]
                        for arguments in invocations
                    )
                ),
                default=None,
                key=lambda _: 1,
            )
            if lint_step is not None:
                lint_fields = lint_step["fields"]
                if "if" in lint_fields or "continue-on-error" in lint_fields:
                    issues.append(
                        issue(
                            workflow_path,
                            job_line,
                            "static-analysis dependency and ABI gate step must be unconditional and blocking",
                        )
                    )
            convention_test_steps = [
                step
                for step, _, standalone in convention_test_invocations
                if standalone
            ]
            if not convention_test_steps:
                issues.append(
                    issue(
                        workflow_path,
                        job_line,
                        "static-analysis command missing: convention plugin tests",
                    )
                )
            else:
                convention_fields = convention_test_steps[0]["fields"]
                if (
                    "continue-on-error" in convention_fields
                    and static_workflow_boolean(
                        convention_fields["continue-on-error"]
                    )
                    is not False
                ):
                    issues.append(
                        issue(
                            workflow_path,
                            job_line,
                            "static-analysis convention plugin tests step must be blocking",
                        )
                    )
                if "if" in convention_fields:
                    issues.append(
                        issue(
                            workflow_path,
                            job_line,
                            "static-analysis convention plugin tests step must not be disabled",
                        )
                    )
        if normalized_arguments and normalized_arguments != contract["arguments"]:
            issues.append(
                issue(
                    workflow_path,
                    job_line,
                    f"{job_name} command arguments must exactly match the lint contract",
                )
            )

        artifact_step = next(
            (
                step
                for step in steps
                if step["fields"].get("uses", "").startswith("actions/upload-artifact@")
                and step["nested"].get("with", {}).get("name") == contract["artifact"]
            ),
            None,
        )
        if artifact_step is None:
            issues.append(
                issue(
                    workflow_path,
                    job_line,
                    f"{job_name} lint artifact upload missing: {contract['artifact']}",
                )
            )
            continue
        artifact_fields = artifact_step["fields"]
        artifact_with = artifact_step["nested"].get("with", {})
        artifact_requirements = {
            "always condition": (artifact_fields, "if", "always()"),
            "report glob": (artifact_with, "path", "**/build/reports/lint-results-*"),
            "missing-report failure": (artifact_with, "if-no-files-found", "error"),
            "bounded retention": (artifact_with, "retention-days", "7"),
        }
        for name, (mapping, key, expected) in artifact_requirements.items():
            if mapping.get(key) != expected:
                issues.append(
                    issue(
                        workflow_path,
                        job_line,
                        f"{job_name} lint artifact upload missing: {name}",
                    )
                )
        if job_name == "static-analysis":
            quality_artifact = next(
                (
                    step
                    for step in steps
                    if step["fields"].get("uses", "").startswith("actions/upload-artifact@")
                    and step["nested"].get("with", {}).get("name")
                    == "dependency-public-api-reports"
                ),
                None,
            )
            if quality_artifact is None:
                issues.append(
                    issue(workflow_path, job_line, "dependency and public API report upload missing")
                )
            else:
                quality_fields = quality_artifact["fields"]
                quality_with = quality_artifact["nested"].get("with", {})
                actual_paths = quality_with.get("path", "").splitlines()
                if (
                    quality_fields.get("if") != "always()"
                    or quality_with.get("if-no-files-found") != "error"
                    or quality_with.get("retention-days") != "7"
                    or actual_paths != QUALITY_REPORT_PATHS
                ):
                    issues.append(
                        issue(
                            workflow_path,
                            job_line,
                            "dependency and public API report upload must use exact blocking paths",
                        )
                    )
    return issues


def check_forbidden_abi_automation(root: Path, workflow: str) -> list[str]:
    """Keep reviewed ABI baseline updates and deprecated aliases out of automation."""
    issues: list[str] = []
    forbidden = ("updateKotlinAbi", "checkLegacyAbi", "updateLegacyAbi")
    for step in workflow_steps(workflow):
        run = str(step["fields"].get("run", ""))
        for task in forbidden:
            if task in run:
                issues.append(
                    issue(
                        ".github/workflows/android.yml",
                        1,
                        f"forbidden ABI automation task: {task}",
                    )
                )
    for script in sorted((root / "scripts" / "agent").glob("*.sh")):
        text = script.read_text(errors="replace")
        for task in forbidden:
            if task in text:
                issues.append(
                    issue(
                        script.relative_to(root),
                        1,
                        f"forbidden ABI automation task: {task}",
                    )
                )
    return issues


def check_dependency_and_abi_repository_contracts(root: Path) -> list[str]:
    issues: list[str] = []
    policy_path = root / "config" / "quality" / "production-dependency-policy.txt"
    if policy_path.is_file():
        policy = policy_path.read_text(errors="replace")
        if "\nenforcement=blocking\n" not in f"\n{policy}":
            issues.append(
                issue(
                    "config/quality/production-dependency-policy.txt",
                    1,
                    "production dependency policy must be blocking",
                )
            )
    helper_path = (
        root
        / "build-logic"
        / "convention"
        / "src"
        / "main"
        / "kotlin"
        / "GasStationContractApiConvention.kt"
    )
    if helper_path.is_file():
        helper = helper_path.read_text(errors="replace")
        if "explicitApi()" not in helper or "explicitApiWarning()" in helper:
            issues.append(
                issue(
                    helper_path.relative_to(root),
                    1,
                    "contract API convention must use strict explicitApi",
                )
            )
    module_doc_path = root / "docs" / "module-contracts.md"
    module_doc = module_doc_path.read_text(errors="replace") if module_doc_path.is_file() else ""
    mapping_block = markdown_fenced_block(module_doc, "Exact public ABI mappings")
    if mapping_block != ABI_MAPPING_BLOCK:
        issues.append(
            issue(
                "docs/module-contracts.md",
                1,
                "docs must contain the exact ordered ABI mapping block",
            )
        )
    for family in FORBIDDEN_PUBLIC_API_FAMILIES:
        if family not in module_doc:
            issues.append(
                issue(
                    "docs/module-contracts.md",
                    1,
                    f"forbidden public API family missing: {family}",
                )
            )
    verification_path = root / "docs" / "verification-matrix.md"
    verification = verification_path.read_text(errors="replace") if verification_path.is_file() else ""
    verification_block = markdown_fenced_block(
        verification,
        "Production dependency and public ABI verification",
    )
    if verification_block != QUALITY_VERIFICATION_BLOCK:
        issues.append(
            issue(
                "docs/verification-matrix.md",
                1,
                "docs must contain the exact ordered verification block",
            )
        )
    report_block = markdown_fenced_block(verification, "Quality report upload paths")
    if report_block != QUALITY_REPORT_PATHS:
        issues.append(
            issue(
                "docs/verification-matrix.md",
                1,
                "docs must contain the exact ordered quality report block",
            )
        )
    operator_block = markdown_fenced_block(
        verification,
        "ABI baseline operator mutation, not verification",
    )
    if operator_block != ABI_UPDATE_OPERATOR_BLOCK:
        issues.append(
            issue(
                "docs/verification-matrix.md",
                1,
                "docs must contain the exact ordered ABI operator block",
            )
        )
    return issues


def markdown_fenced_block(document: str, heading: str) -> list[str] | None:
    """Return the one exact text fence owned by a level-two heading."""
    headings = list(re.finditer(rf"(?m)^## {re.escape(heading)}\s*$", document))
    if len(headings) != 1:
        return None
    owned = headings[0]
    next_heading = re.search(r"(?m)^## .+$", document[owned.end() :])
    section_end = owned.end() + next_heading.start() if next_heading else len(document)
    section = document[owned.end() : section_end]
    fences = list(
        re.finditer(
            r"(?ms)^```text\s*$\n(?P<body>.*?)^```\s*$",
            section,
        )
    )
    if len(fences) != 1 or section[: fences[0].start()].strip():
        return None
    match = fences[0]
    body = match.group("body")
    if not body.endswith("\n") or "\r" in body:
        return None
    lines = body[:-1].split("\n")
    if not lines or any(not line or line != line.strip() for line in lines):
        return None
    return lines


def check_coverage_workflow_contract(workflow: str) -> list[str]:
    """Require the one blocking, evidence-producing coverage invocation."""
    issues: list[str] = []
    workflow_path = ".github/workflows/android.yml"
    if not re.search(
        r'(?m)^on:\n  pull_request:\n  push:\n    branches:\n      - main\n    tags:\n      - "v\*"$',
        workflow,
    ):
        issues.append(
            issue(
                workflow_path,
                1,
                "coverage workflow must run for pull requests, main pushes, and v tags",
            )
        )
    match = re.search(WORKFLOW_JOB_TEMPLATE.format(job="coverage"), workflow)
    if match is None:
        return [issue(workflow_path, 1, "workflow job missing: coverage")]

    body = match.group("body")
    job_line = source_line(workflow, match.start())
    job_fields = workflow_job_fields(body)
    if job_fields.get("runs-on") != "ubuntu-24.04":
        issues.append(issue(workflow_path, job_line, "coverage runner must be ubuntu-24.04"))
    if job_fields.get("timeout-minutes") != "45":
        issues.append(issue(workflow_path, job_line, "coverage timeout must be 45 minutes"))
    if "if" in job_fields:
        issues.append(issue(workflow_path, job_line, "coverage job must not be disabled"))
    if (
        "continue-on-error" in job_fields
        and static_workflow_boolean(job_fields["continue-on-error"]) is not False
    ):
        issues.append(issue(workflow_path, job_line, "coverage job must be blocking"))

    job_environment = workflow_job_environment(body)
    if "CODECOV_TOKEN" in job_environment:
        issues.append(issue(workflow_path, job_line, "Codecov token must not be job-scoped"))
    if job_environment.get("GASSTATION_COVERAGE_EVENT") != yaml_scalar(COVERAGE_EVENT_EXPRESSION):
        issues.append(issue(workflow_path, job_line, "coverage event routing must use the immutable contract"))
    if job_environment.get("GASSTATION_COVERAGE_BASE_REF") != yaml_scalar(COVERAGE_BASE_EXPRESSION):
        issues.append(issue(workflow_path, job_line, "coverage base routing must use the immutable contract"))

    steps = workflow_steps(body)
    checkout_steps = [
        step
        for step in steps
        if str(step["fields"].get("uses", "")).startswith("actions/checkout@")
        and step["nested"].get("with", {}).get("fetch-depth") == "0"
    ]
    if len(checkout_steps) != 1:
        issues.append(issue(workflow_path, job_line, "coverage checkout must use fetch-depth: 0"))

    attempt_steps = [
        step for step in steps
        if step["fields"].get("name") == "Create coverage attempt envelope"
    ]
    if len(attempt_steps) != 1:
        issues.append(issue(workflow_path, job_line, "coverage attempt envelope step missing"))
    else:
        attempt = attempt_steps[0]
        attempt_run = str(attempt["fields"].get("run", ""))
        attempt_env = attempt["nested"].get("env", {})
        if (
            attempt_env != {"COVERAGE_SOURCE_SHA": "${{ github.sha }}"}
            or not coverage_attempt_script_is_exact(attempt_run)
        ):
            issues.append(issue(workflow_path, job_line, "coverage attempt envelope must be deterministic and complete"))

    parsed_runs = [shell_gradle_arguments(str(step["fields"].get("run", ""))) for step in steps]
    gradle_invocations = [
        (step, arguments, standalone)
        for step, (invocations, standalone) in zip(steps, parsed_runs)
        for arguments in invocations
    ]
    valid_invocations = [
        (step, standalone)
        for step, arguments, standalone in gradle_invocations
        if normalize_gradle_arguments(arguments) == COVERAGE_GRADLE_ARGUMENTS
    ]
    if len(gradle_invocations) != 1 or len(valid_invocations) != 1:
        issues.append(issue(workflow_path, job_line, "coverage command arguments must exactly match the blocking contract"))
    elif not valid_invocations[0][1]:
        issues.append(issue(workflow_path, job_line, "coverage command must be one standalone ./gradlew invocation"))
    else:
        verification_step = valid_invocations[0][0]
        verification_fields = verification_step["fields"]
        if "if" in verification_fields:
            issues.append(issue(workflow_path, job_line, "coverage verification step must not be disabled"))
        if (
            "continue-on-error" in verification_fields
            and static_workflow_boolean(verification_fields["continue-on-error"]) is not False
        ):
            issues.append(issue(workflow_path, job_line, "coverage verification step must be blocking"))

    evidence_steps = [
        step
        for step in steps
        if str(step["fields"].get("uses", "")).startswith("actions/upload-artifact@")
        and step["nested"].get("with", {}).get("name") == "coverage-evidence"
    ]
    required_evidence_paths = (
        "build/reports/coverage/coverage-attempt.json",
        "build/reports/coverage/report-manifest.json",
        "build/reports/coverage/verification-summary.json",
        "**/build/reports/coverage/*/manifest-entry.json",
        "**/build/reports/coverage/*/report.xml",
    )
    if len(evidence_steps) != 1:
        issues.append(issue(workflow_path, job_line, "coverage evidence upload missing"))
    else:
        evidence = evidence_steps[0]
        evidence_with = evidence["nested"].get("with", {})
        if (
            evidence["fields"].get("if") != "always()"
            or evidence_with.get("if-no-files-found") != "error"
            or evidence_with.get("retention-days") != "7"
            or tuple(line.strip() for line in evidence_with.get("path", "").splitlines() if line.strip())
            != required_evidence_paths
        ):
            issues.append(issue(workflow_path, job_line, "coverage evidence upload must retain every produced artifact"))

    codecov_steps = [
        step
        for step in steps
        if str(step["fields"].get("uses", "")).startswith("codecov/codecov-action@")
    ]
    if len(codecov_steps) != 1:
        issues.append(issue(workflow_path, job_line, "optional Codecov upload missing"))
    else:
        codecov = codecov_steps[0]
        codecov_with = codecov["nested"].get("with", {})
        if (
            "if" in codecov["fields"]
            or static_workflow_boolean(codecov["fields"].get("continue-on-error", "")) is not True
            or codecov_with.get("token") != yaml_scalar("${{ secrets.CODECOV_TOKEN }}")
            or codecov_with.get("files")
            != "**/build/reports/coverage/*/report.xml"
        ):
            issues.append(issue(workflow_path, job_line, "Codecov secret must be action-scoped and nonblocking"))
    return issues


def check_ci_contracts(root: Path) -> list[str]:
    issues: list[str] = []
    for relative in CI_REQUIRED_FILES:
        if not (root / relative).is_file():
            issues.append(issue(relative, 1, "required CI contract file missing"))

    for relative, anchors in CI_REQUIRED_ANCHORS.items():
        contract = root / relative
        if not contract.is_file():
            continue
        text = contract.read_text(errors="replace")
        for anchor in anchors:
            if anchor not in text:
                issues.append(issue(relative, 1, "required contract anchor missing"))

    configured_targets = set()
    for relative, required_events in HOOK_CONFIG_EVENTS.items():
        config = root / relative
        if not config.is_file():
            continue
        try:
            payload = json.loads(config.read_text())
        except json.JSONDecodeError as error:
            issues.append(issue(relative, error.lineno, "malformed JSON hook config"))
            continue
        hooks = payload.get("hooks") if isinstance(payload, dict) else None
        if not isinstance(hooks, dict):
            issues.append(issue(relative, 1, "hook config must contain a hooks object"))
            continue
        for event, expected_target in required_events.items():
            if event not in hooks:
                issues.append(issue(relative, 1, f"required hook event missing: {event}"))
                continue
            event_value = hooks[event]
            if not isinstance(event_value, list) or not event_value:
                issues.append(
                    issue(
                        relative,
                        1,
                        f"required hook event must be a non-empty list: {event}",
                    )
                )
                continue
            event_commands = list(event_command_hooks(event_value))
            event_targets = {
                target
                for command in event_commands
                for target in command_hook_targets(command)
            }
            if not event_commands:
                issues.append(
                    issue(
                        relative,
                        1,
                        f"required hook event has no well-formed command hook: {event}",
                    )
                )
            elif expected_target not in event_targets:
                issues.append(
                    issue(
                        relative,
                        1,
                        f"required hook target missing for {event}: {expected_target}",
                    )
                )
        for command in hook_commands(hooks):
            configured_targets.update(command_named_targets(command))

    for target in sorted(configured_targets):
        if not (root / target).is_file():
            issues.append(issue(target, 1, "configured hook target missing"))

    issues += check_dependency_and_abi_repository_contracts(root)

    workflow_path = root / ".github" / "workflows" / "android.yml"
    if workflow_path.is_file():
        workflow = workflow_path.read_text(errors="replace")
        issues += check_lint_workflow_contracts(workflow)
        issues += check_forbidden_abi_automation(root, workflow)
        issues += check_coverage_workflow_contract(workflow)
        release_job = RELEASE_JOB.search(workflow)
        if release_job is None:
            issues.append(
                issue(
                    ".github/workflows/android.yml",
                    1,
                    "tag release publishing contract missing",
                )
            )
        else:
            body = release_job.group("body")
            for name, anchor in RELEASE_JOB_ANCHORS.items():
                if anchor not in body:
                    issues.append(
                        issue(
                            ".github/workflows/android.yml",
                            1,
                            f"tag release publishing contract missing: {name}",
                        )
                    )
            needs_match = re.search(r"(?m)^\s+needs:\s*\[([^\]]+)\]\s*$", body)
            prerequisites = (
                {
                    prerequisite.strip()
                    for prerequisite in needs_match.group(1).split(",")
                    if prerequisite.strip()
                }
                if needs_match
                else set()
            )
            for prerequisite in sorted(RELEASE_JOB_PREREQUISITES - prerequisites):
                issues.append(
                    issue(
                        ".github/workflows/android.yml",
                        1,
                        f"tag release publishing prerequisite missing: {prerequisite}",
                    )
                    )
    issues += check_mutation_workflow_contracts(root)
    return issues


def check_device_evidence_contracts(root: Path) -> list[str]:
    quality_dir = Path(__file__).resolve().parents[1] / "quality"
    sys.path.insert(0, str(quality_dir))
    try:
        try:
            from device_workflow import check_device_contracts
        except ModuleNotFoundError:
            return [issue("scripts/quality/device_workflow.py", 1, "device workflow checker missing")]
    finally:
        sys.path.pop(0)
    return check_device_contracts(root)


def parse_diff_issues(output: str) -> list[str]:
    issues: list[str] = []
    for line in output.splitlines():
        if not line:
            continue
        match = DIFF_CHECK_ISSUE.match(line)
        if match:
            path, line_number, message = match.groups()
            issues.append(issue(path, int(line_number or 1), message))
        else:
            issues.append(issue("git-diff", 1, line.strip()))
    return issues


def run_diff_check(root: Path, arguments: list[str]) -> list[str]:
    result = subprocess.run(
        ["git", "-C", str(root), "diff", "--check"] + arguments,
        text=True,
        capture_output=True,
    )
    if result.returncode:
        issues = parse_diff_issues(result.stdout)
        return issues or [issue("git-diff", 1, "git diff --check failed")]
    return []


def commit_exists(root: Path, reference: str) -> bool:
    result = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "--verify", "--quiet", f"{reference}^{{commit}}"],
        text=True,
        capture_output=True,
    )
    return result.returncode == 0


def check_diff(root: Path, ci: bool = False) -> list[str]:
    issues = run_diff_check(root, [])
    issues += run_diff_check(root, ["--cached"])
    if not ci:
        return issues

    configured_base = os.environ.get("GASSTATION_CI_BASE_REF", "").strip()
    all_zero_sha = configured_base and set(configured_base) == {"0"}
    if configured_base and not all_zero_sha:
        if not commit_exists(root, configured_base):
            issues.append(issue("git-diff", 1, "configured CI diff base is not a commit"))
            return issues
        base = configured_base
    elif commit_exists(root, "HEAD^"):
        base = "HEAD^"
    else:
        return issues
    issues += run_diff_check(root, [f"{base}...HEAD"])
    return issues


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path)
    parser.add_argument("--quick", action="store_true")
    parser.add_argument(
        "--ci",
        action="store_true",
        help="run the full contract check for CI",
    )
    args = parser.parse_args()
    root = (
        args.root
        or Path(
            subprocess.check_output(
                ["git", "rev-parse", "--show-toplevel"], text=True
            ).strip()
        )
    ).resolve()

    issues = check_portable_agent_paths(root) + check_secrets_and_artifacts(root)
    if not args.quick:
        issues += check_documentation_contracts(root)
        issues += check_build_contract(root)
        issues += check_shell_syntax(root)
        issues += check_device_evidence_contracts(root)
        issues += check_diff(root, ci=args.ci)
        if args.ci:
            issues += check_ci_contracts(root)
    if issues:
        for issue in sorted(set(issues)):
            print(f"ERROR: {issue}", file=sys.stderr)
        return 1
    print("agent-contracts: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
