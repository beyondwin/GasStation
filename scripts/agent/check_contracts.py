#!/usr/bin/env python3
"""Check repository contracts used by the agent workflow.

Secret and artifact checks intentionally inspect Git-tracked files only. This
avoids reading ignored or untracked local credentials while still detecting an
accidental attempt to commit them.
"""

from __future__ import annotations

import argparse
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
        "docs/AGENTS.md",
        "core/database/AGENTS.md",
        "benchmark/AGENTS.md",
        "scripts/agent/verify-room-schemas.sh",
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
    "unit-tests",
    "screenshot-tests",
    "assemble",
    "release-assemble",
    "coverage",
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
    validator = Path(__file__).resolve().parents[1] / "docs" / "validate.py"
    if not catalog.is_file():
        return [issue("docs/documentation-catalog.json", 1, "documentation catalog missing")]
    if not validator.is_file():
        return [issue("scripts/docs/validate.py", 1, "documentation validator missing")]
    result = subprocess.run(
        [sys.executable, str(validator), "--root", str(root)],
        text=True,
        capture_output=True,
    )
    if result.returncode == 0:
        return []
    issues = []
    for line in result.stderr.splitlines():
        if line.startswith("ERROR: "):
            issues.append(line.removeprefix("ERROR: "))
    return issues or [issue("docs/documentation-catalog.json", 1, "documentation validation failed")]


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

    workflow_path = root / ".github" / "workflows" / "android.yml"
    if workflow_path.is_file():
        workflow = workflow_path.read_text(errors="replace")
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
    return issues


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
