#!/usr/bin/env python3
"""Check repository contracts used by the agent workflow.

Secret and artifact checks intentionally inspect Git-tracked files only. This
avoids reading ignored or untracked local credentials while still detecting an
accidental attempt to commit them.
"""

from __future__ import annotations

import argparse
import re
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


def check_diff(root: Path) -> list[str]:
    result = subprocess.run(
        ["git", "-C", str(root), "diff", "--check"], text=True, capture_output=True
    )
    if result.returncode:
        issues: list[str] = []
        for line in result.stdout.splitlines():
            if not line:
                continue
            match = DIFF_CHECK_ISSUE.match(line)
            if match:
                path, line_number, message = match.groups()
                issues.append(issue(path, int(line_number or 1), message))
            else:
                issues.append(issue("git-diff", 1, line.strip()))
        return issues or [issue("git-diff", 1, "git diff --check failed")]
    return []


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path)
    parser.add_argument("--quick", action="store_true")
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
        issues += check_live_links(root)
        issues += check_build_contract(root)
        issues += check_shell_syntax(root)
        issues += check_diff(root)
    if issues:
        for issue in sorted(set(issues)):
            print(f"ERROR: {issue}", file=sys.stderr)
        return 1
    print("agent-contracts: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
