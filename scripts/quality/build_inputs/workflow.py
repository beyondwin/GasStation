from __future__ import annotations

import re
from pathlib import Path
from typing import Any, Mapping

from .contracts import BuildInputError, HEX40


USES = re.compile(r"^(?P<indent>\s*)-?\s*uses:\s*(?P<value>[^\s#]+)(?:\s+#\s*(?P<label>v\d+))?\s*$")


def _job_block(workflow: str, name: str) -> str:
    match = re.search(
        rf"(?ms)^  {re.escape(name)}:\n.*?(?=^  [A-Za-z0-9_-]+:\n|\Z)",
        workflow,
    )
    if match is None:
        raise BuildInputError(f"android workflow is missing {name} job")
    return match.group(0)


def _require_fragments(block: str, fragments: tuple[str, ...], *, owner: str) -> None:
    missing = [fragment for fragment in fragments if fragment not in block]
    if missing:
        raise BuildInputError(f"{owner} contract is incomplete: missing={missing}")


def build_inputs_is_promoted(workflow: str) -> bool:
    return "    continue-on-error: true\n" not in _job_block(workflow, "build-inputs")


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
    if promoted is not None:
        build_inputs = _job_block(android, "build-inputs")
        _require_fragments(
            build_inputs,
            (
                "    runs-on: ubuntu-24.04\n",
                "    timeout-minutes: 60\n",
                "          fetch-depth: 0\n",
                "          persist-credentials: false\n",
                "      - uses: ./.github/actions/setup-build-inputs\n",
                "verify_build_inputs.py verify --policy config/quality/build-inputs.json",
                "verify_build_inputs.py strict-matrix",
                "--policy config/quality/build-inputs.json --group complete",
                "--policy config/quality/build-inputs.json --group product-regressions",
                "verify_build_inputs.py configuration-cache",
                "verify_build_inputs.py reproduce",
                "--source-commit \"$GITHUB_SHA\"",
                "--output build/reports/build-inputs/reproducible-build.json",
                "verify_build_inputs.py capture",
                "--evidence build/reports/build-inputs/reproducible-build.json",
                "--output build/reports/build-inputs/build-input-receipt.json",
                "      - name: Upload build-input evidence\n        if: always()\n",
                "name: build-input-evidence-${{ github.sha }}-${{ github.run_attempt }}",
                "path: build/reports/build-inputs/**",
                "if-no-files-found: warn",
                "      - name: Upload source-bound reproducibility receipt\n        if: success()\n",
                "name: reproducible-prod-release-receipt-${{ github.sha }}",
                "path: build/reports/build-inputs/reproducible-prod-release-receipt.json",
            ),
            owner="build-inputs job",
        )
        report_only = "    continue-on-error: true\n" in build_inputs
        if build_inputs.count("continue-on-error:") != (1 if report_only else 0):
            raise BuildInputError("build-inputs may only use the job-level report-only allowance")
        if promoted is True and report_only:
            raise BuildInputError("blocking build-inputs job may not continue on error")
        if promoted is False and not report_only:
            raise BuildInputError("observation build-inputs job must be present and report-only")
        if promoted is True:
            release_assemble = _job_block(android, "release-assemble")
            release_publish = _job_block(android, "release-publish")
            receipt_name = "name: reproducible-prod-release-receipt-${{ github.sha }}"
            receipt_path = "path: build/reports/build-inputs/probe"
            binding_fragments = (
                "verify_build_inputs.py release-bind",
                "--receipt build/reports/build-inputs/probe/reproducible-prod-release-receipt.json",
                "--source-commit \"$GITHUB_SHA\"",
                "--artifact-name \"reproducible-prod-release-receipt-${GITHUB_SHA}\"",
            )
            _require_fragments(
                release_assemble,
                (
                    "    needs: build-inputs\n",
                    receipt_name,
                    receipt_path,
                    "      - uses: ./.github/actions/setup-build-inputs\n",
                    *binding_fragments,
                    "--output build/reports/build-inputs/release-assemble-binding.json",
                ),
                owner="release-assemble job",
            )
            _require_fragments(
                release_publish,
                (
                    "needs: [agent-contracts, build-inputs, static-analysis, lint-tests, unit-tests, screenshot-tests, assemble, release-assemble, coverage, mutation]",
                    receipt_name,
                    receipt_path,
                    "      - uses: ./.github/actions/setup-build-inputs\n",
                    *binding_fragments,
                    "--output build/reports/build-inputs/release-publish-binding.json",
                ),
                owner="release-publish job",
            )
            if release_assemble.index("verify_build_inputs.py release-bind") > release_assemble.index(
                "      - name: Upload unsigned prod APK",
            ):
                raise BuildInputError("release-assemble binding must precede APK upload")
            publish_binding = release_publish.index("verify_build_inputs.py release-bind")
            if publish_binding > release_publish.index("sha256sum") or publish_binding > release_publish.index("gh release"):
                raise BuildInputError("release-publish binding must precede checksum and release mutation")


def _without_comments(text: str) -> str:
    return "\n".join(line.split("#", 1)[0] for line in text.splitlines())
