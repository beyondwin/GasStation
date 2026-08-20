from __future__ import annotations

import re
from pathlib import Path
from typing import Any, Mapping

from .contracts import BuildInputError, HEX40


USES = re.compile(r"^(?P<indent>\s*)-?\s*uses:\s*(?P<value>[^\s#]+)(?:\s+#\s*(?P<label>v\d+))?\s*$")


def _remote_uses(root: Path) -> list[tuple[str, int, str, str | None]]:
    rows: list[tuple[str, int, str, str | None]] = []
    paths = [
        *sorted((root / ".github/workflows").glob("*.yml")),
        *sorted((root / ".github/workflows").glob("*.yaml")),
        *sorted((root / ".github/actions").glob("**/*.yml")),
        *sorted((root / ".github/actions").glob("**/*.yaml")),
    ]
    for path in paths:
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            match = USES.match(line)
            if match is None:
                continue
            value = match.group("value")
            if value.startswith("./"):
                continue
            rows.append((path.relative_to(root).as_posix(), line_number, value, match.group("label")))
    return rows


def verify_repository_workflows(root: Path, policy: Mapping[str, Any], *, promoted: bool | None) -> None:
    action_rows = policy["actions"]["workflowUses"]
    expected = {f"{row['owner']}/{row['repository']}{('/' + row['path']) if row['path'] else ''}@{row['commit']}": row for row in action_rows}
    actual_rows = _remote_uses(root)
    actual = {value for _, _, value, _ in actual_rows}
    if actual != set(expected):
        raise BuildInputError(f"workflow action inventory mismatch: missing={sorted(set(expected)-actual)} extra={sorted(actual-set(expected))}")
    for path, line, value, label in actual_rows:
        reference = value.rsplit("@", 1)[-1]
        if HEX40.fullmatch(reference) is None:
            raise BuildInputError(f"{path}:{line}: remote action must use a lowercase full SHA")
        expected_label = expected[value]["sourceTag"].split(".", 1)[0]
        if label != expected_label:
            raise BuildInputError(f"{path}:{line}: action pin requires # {expected_label}")

    for workflow in sorted((root / ".github/workflows").glob("*.yml")):
        text = workflow.read_text(encoding="utf-8")
        if "runs-on: ubuntu-latest" in text:
            raise BuildInputError(f"{workflow.name}: ubuntu-latest is forbidden")
        if "actions/setup-java@" in text:
            raise BuildInputError(f"{workflow.name}: setup-java is forbidden")
        for forbidden in ("JAVA_OPTS:", "GRADLE_OPTS:", "JAVA_TOOL_OPTIONS:", "JDK_JAVA_OPTIONS:", "_JAVA_OPTIONS:"):
            if forbidden in text:
                raise BuildInputError(f"{workflow.name}: protected environment shadow: {forbidden[:-1]}")
        checkout_blocks = re.finditer(r"(?m)^\s*-\s+uses:\s+actions/checkout@[^\n]+\n(?P<body>(?:\s{8,}[^\n]*\n){0,8})", text)
        for match in checkout_blocks:
            if "persist-credentials: false" not in match.group("body"):
                raise BuildInputError(f"{workflow.name}: checkout must disable persisted credentials")
        for match in re.finditer(r"(?m)^\s*-\s+uses:\s+gradle/actions/setup-gradle@[^\n]+\n(?P<body>(?:\s{8,}[^\n]*\n){0,10})", text):
            body = match.group("body")
            required = (
                "cache-provider: basic",
                "validate-wrappers: true",
                "allow-snapshot-wrappers: false",
                "dependency-graph: disabled",
            )
            if any(value not in body for value in required):
                raise BuildInputError(f"{workflow.name}: setup-gradle inputs are incomplete")
        if "./gradlew" in _without_comments(text):
            raise BuildInputError(f"{workflow.name}: governed workflow must use the sealed Gradle launcher")
    action = (root / ".github/actions/setup-build-inputs/action.yml").read_text(encoding="utf-8")
    if "python3 scripts/quality/verify_build_inputs.py install-jdks" not in action:
        raise BuildInputError("verified build-input action must invoke the closed JDK installer")
    required_action_inputs = (
        "cache-provider: basic",
        "validate-wrappers: true",
        "allow-snapshot-wrappers: false",
        "dependency-graph: disabled",
    )
    if any(value not in action for value in required_action_inputs):
        raise BuildInputError("verified build-input action has incomplete setup-gradle inputs")
    android = (root / ".github/workflows/android.yml").read_text(encoding="utf-8")
    observation = "  build-inputs:\n" in android and "    continue-on-error: true\n" in android
    if promoted is True and observation:
        raise BuildInputError("blocking build-inputs job may not continue on error")
    if promoted is False and not observation:
        raise BuildInputError("observation build-inputs job must be present and report-only")


def _without_comments(text: str) -> str:
    return "\n".join(line.split("#", 1)[0] for line in text.splitlines())
