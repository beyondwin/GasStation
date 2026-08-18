#!/usr/bin/env bash
set -euo pipefail

test_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$test_dir/../../.." && pwd)
source "$test_dir/test_helpers.sh"

assert_error_locations() {
  local output=$1
  local error_line
  local found=0
  while IFS= read -r error_line; do
    [[ -n "$error_line" ]] || continue
    found=1
    [[ "$error_line" =~ ^ERROR:\ .+:[0-9]+:\  ]] || fail "error lacks file:line: $error_line"
  done <<EOF
$output
EOF
  [[ "$found" -eq 1 ]] || fail "expected checker error output"
}

fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
make_git_repo "$fixture/repo"
mkdir -p \
  "$fixture/repo/app" \
  "$fixture/repo/docs" \
  "$fixture/repo/docs/release-notes" \
  "$fixture/repo/.codex" \
  "$fixture/repo/.claude" \
  "$fixture/repo/.github/workflows" \
  "$fixture/repo/core/database" \
  "$fixture/repo/benchmark" \
  "$fixture/repo/scripts/agent"
cat > "$fixture/repo/app/build.gradle.kts" <<'EOF'
android {
    compileSdk = 37
    defaultConfig {
        versionCode = 8
        versionName = "1.2.0"
    }
}
EOF
cat > "$fixture/repo/README.md" <<'EOF'
The fixture ships a 1-module setup.
Current version: `1.2.0` (`versionCode` 8).
[Guide](docs/guide.md)
EOF
printf '# Guide\n' > "$fixture/repo/docs/guide.md"
cat > "$fixture/repo/docs/release-notes/2026-06-07-v1.2.0.md" <<'EOF'
# Release Notes - v1.2.0

| `versionName` | `1.2.0` |
| `versionCode` | `8` |
| 릴리즈 태그 | `v1.2.0` |
EOF
printf 'Java 21+, Android SDK 37, Python 3.9+.\n' > "$fixture/repo/CONTRIBUTING.md"
printf '# Changelog\n' > "$fixture/repo/CHANGELOG.md"
printf '# Design contract\n' > "$fixture/repo/.impeccable.md"
cat > "$fixture/repo/AGENTS.md" <<'EOF'
# Agent contract

Run `scripts/agent/preflight.sh` before work and `scripts/agent/verify.sh auto` before completion.
EOF
for live_doc in \
  agent-workflow.md architecture.md build-velocity.md deployment.md \
  offline-strategy.md performance.md project-reading-guide.md \
  security-trade-offs.md state-model.md test-strategy.md verification-matrix.md; do
  printf '# Live contract\n' > "$fixture/repo/docs/$live_doc"
done
cat > "$fixture/repo/docs/module-contracts.md" <<'EOF'
# Module contracts

The active module is `app`.
EOF
cat > "$fixture/repo/docs/AGENTS.md" <<'EOF'
# Documentation contract

Run `scripts/agent/verify.sh docs`; keep `docs/superpowers/` historical.
EOF
cat > "$fixture/repo/core/database/AGENTS.md" <<'EOF'
# Database contract

Preserve `StationSearchResult.hasCachedSnapshot`; never add `fallbackToDestructiveMigration`.
EOF
cat > "$fixture/repo/benchmark/AGENTS.md" <<'EOF'
# Benchmark contract

Use `demoBenchmark` with an explicit `ANDROID_SERIAL`.
EOF
printf '[features]\nhooks = true\n' > "$fixture/repo/.codex/config.toml"
cat > "$fixture/repo/.codex/hooks.json" <<'EOF'
{
  "hooks": {
    "SessionStart": [{"hooks": [{"type": "command", "command": "/bin/bash \"$(git rev-parse --show-toplevel)/scripts/agent/preflight.sh\" --hook"}]}],
    "PreToolUse": [{"hooks": [{"type": "command", "command": "/usr/bin/env python3 \"$(git rev-parse --show-toplevel)/scripts/agent/pre_tool_policy.py\""}]}],
    "Stop": [{"hooks": [{"type": "command", "command": "/usr/bin/env python3 \"$(git rev-parse --show-toplevel)/scripts/agent/stop_check.py\""}]}]
  }
}
EOF
cat > "$fixture/repo/.claude/settings.json" <<'EOF'
{
  "hooks": {
    "PreToolUse": [{"hooks": [{"type": "command", "command": "/usr/bin/env python3 \"$(git rev-parse --show-toplevel)/scripts/agent/pre_tool_policy.py\""}]}],
    "PostToolUse": [{"hooks": [{"type": "command", "command": "/bin/bash \"$(git rev-parse --show-toplevel)/scripts/agent/check-contracts.sh\" --quick"}]}],
    "SubagentStop": [{"hooks": [{"type": "command", "command": "/bin/bash \"$(git rev-parse --show-toplevel)/scripts/agent/check-contracts.sh\" --quick"}]}]
  }
}
EOF
for hook_target in preflight.sh pre_tool_policy.py stop_check.py check-contracts.sh; do
  printf '#!/usr/bin/env bash\nexit 0\n' > "$fixture/repo/scripts/agent/$hook_target"
  chmod +x "$fixture/repo/scripts/agent/$hook_target"
done
printf '#!/usr/bin/env bash\nexit 0\n' > "$fixture/repo/scripts/agent/verify-room-schemas.sh"
chmod +x "$fixture/repo/scripts/agent/verify-room-schemas.sh"
cat > "$fixture/repo/.github/workflows/android.yml" <<'EOF'
name: Android CI
on:
  pull_request:
  push:
    branches:
      - main
    tags:
      - "v*"
jobs:
  agent-contracts:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
        with:
          fetch-depth: 0
      - run: scripts/agent/check-contracts.sh --ci
        env:
          GASSTATION_CI_BASE_REF: fixture-base
      - run: scripts/agent/verify-room-schemas.sh
  static-analysis:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - name: Production lint
        run: |
          ./gradlew \
            spotlessCheck \
            :app:lintDemoDebug \
            :app:lintProdDebug \
            lint \
            verifyModuleBoundaries \
            verifyNoDeprecatedComposeTestApis \
            verifyCiRobolectricRuntime \
            -Pgasstation.lintTestSources=false \
            --warning-mode fail \
            --continue
      - name: Convention plugin tests
        run: ./gradlew :build-logic:convention:test --warning-mode fail
      - name: Upload production lint reports
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: lint-production-reports
          path: "**/build/reports/lint-results-*"
          if-no-files-found: error
          retention-days: 7
  lint-tests:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - name: Test-source lint
        run: |
          ./gradlew \
            :app:lintDemoDebug \
            :app:lintProdDebug \
            lint \
            -Pgasstation.lintTestSources=true \
            --warning-mode fail \
            --continue
      - name: Upload test-source lint reports
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: lint-test-source-reports
          path: "**/build/reports/lint-results-*"
          if-no-files-found: error
          retention-days: 7
  coverage:
    runs-on: ubuntu-latest
    timeout-minutes: 45
    env:
      CODECOV_TOKEN: ${{ secrets.CODECOV_TOKEN }}
      GASSTATION_COVERAGE_EVENT: ${{ github.event_name == 'pull_request' && 'pull-request' || startsWith(github.ref, 'refs/tags/v') && 'tag' || 'main' }}
      GASSTATION_COVERAGE_BASE_REF: ${{ github.event_name == 'pull_request' && github.event.pull_request.base.sha || (github.ref == 'refs/heads/main' && github.event.before) || '' }}
    steps:
      - uses: actions/checkout@v7
        with:
          fetch-depth: 0
      - name: Create coverage attempt envelope
        env:
          COVERAGE_SOURCE_SHA: ${{ github.sha }}
        run: |
          mkdir -p build/reports/coverage
          python3 - <<'PY'
          import json
          from pathlib import Path

          Path("build/reports/coverage/coverage-attempt.json").write_text(
              json.dumps(
                  {
                      "baseRef": "",
                      "baseline": "config/quality/coverage-baseline.json",
                      "event": "local",
                      "expectedTasks": ["coverageXmlReport", "verifyCoverageReport"],
                      "policy": "config/quality/coverage-policy.json",
                      "schemaVersion": 1,
                      "sourceCommit": "fixture",
                  },
                  sort_keys=True,
              ) + "\\n"
          )
          PY
      - name: Verify trustworthy coverage
        run: |
          ./gradlew coverageXmlReport verifyCoverageReport \
            -Pgasstation.coverageSourceCommit="$GITHUB_SHA" \
            -Pgasstation.coverageEvent="$GASSTATION_COVERAGE_EVENT" \
            -Pgasstation.coverageBaseRef="$GASSTATION_COVERAGE_BASE_REF" \
            --warning-mode fail
      - name: Upload coverage evidence
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: coverage-evidence
          path: |
            build/reports/coverage/coverage-attempt.json
            build/reports/coverage/report-manifest.json
            build/reports/coverage/verification-summary.json
            **/build/reports/coverage/*/manifest-entry.json
            **/build/reports/coverage/*/report.xml
          if-no-files-found: error
          retention-days: 7
      - name: Upload to Codecov
        if: ${{ env.CODECOV_TOKEN != '' }}
        continue-on-error: true
        uses: codecov/codecov-action@v7
        with:
          token: ${{ env.CODECOV_TOKEN }}
          files: "**/build/reports/coverage/*/report.xml"
  release-publish:
    if: ${{ startsWith(github.ref, 'refs/tags/v') }}
    needs: [agent-contracts, static-analysis, lint-tests, unit-tests, screenshot-tests, assemble, release-assemble, coverage]
    permissions:
      contents: write
    steps:
      - uses: actions/download-artifact@v8
      - name: Publish release
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          notes_file=$(find docs/release-notes/ -name "*-${GITHUB_REF_NAME}.md")
          (
            cd release-assets
            sha256sum ./*.apk > SHA256SUMS.txt
          )
          gh release create "$GITHUB_REF_NAME" --notes-file "$notes_file" release-assets/*.apk
EOF
mkdir -p "$fixture/repo/docs/onboarding" "$fixture/repo/docs/adr"
mkdir -p "$fixture/repo/docs/superpowers"
printf '# Onboarding\n' > "$fixture/repo/docs/onboarding/developer-onboarding-guide.md"
printf '# Decision\n' > "$fixture/repo/docs/adr/2026-05-18-backend-proxy-escalation.md"
printf '#!/usr/bin/env bash\nexit 0\n' > "$fixture/repo/scripts/agent/verify.sh"
chmod +x "$fixture/repo/scripts/agent/verify.sh"
cp "$repo_root/docs/station-data-policy.json" "$fixture/repo/docs/station-data-policy.json"
cp "$repo_root/docs/station-data-policy-consumers.json" "$fixture/repo/docs/station-data-policy-consumers.json"
cp "$repo_root/docs/station-list-state-contract.json" "$fixture/repo/docs/station-list-state-contract.json"
cp "$repo_root/docs/station-list-state-contract-consumers.json" "$fixture/repo/docs/station-list-state-contract-consumers.json"
FIXTURE_REPO="$fixture/repo" python3 - <<'PY'
import json
import os
from pathlib import Path

root = Path(os.environ["FIXTURE_REPO"])
paths = [
    "AGENTS.md", "README.md", "CONTRIBUTING.md", "CHANGELOG.md", ".impeccable.md",
    "docs/README.md", "docs/AGENTS.md", "docs/onboarding/developer-onboarding-guide.md",
    "docs/agent-workflow.md", "docs/project-reading-guide.md", "docs/architecture.md",
    "docs/module-contracts.md", "docs/state-model.md", "docs/offline-strategy.md",
    "docs/test-strategy.md", "docs/verification-matrix.md", "docs/security-trade-offs.md",
    "docs/deployment.md", "docs/performance.md", "docs/build-velocity.md",
    "core/database/AGENTS.md", "benchmark/AGENTS.md",
    "docs/adr/2026-05-18-backend-proxy-escalation.md",
]
entries = [{
    "path": path,
    "kind": "contract",
    "owner": f"fixture owner for {path}",
    "authoritativeSources": ["settings.gradle.kts"],
    "reviewTriggers": ["fixture changes"],
    "verificationScope": "python3 scripts/docs/validate.py",
} for path in paths]
offline = next(entry for entry in entries if entry["path"] == "docs/offline-strategy.md")
offline["authoritativeSources"].insert(0, "docs/station-data-policy.json")
offline["authoritativeSources"].insert(1, "docs/station-data-policy-consumers.json")
state_model = next(entry for entry in entries if entry["path"] == "docs/state-model.md")
state_model["authoritativeSources"].insert(0, "docs/station-list-state-contract.json")
state_model["authoritativeSources"].insert(1, "docs/station-list-state-contract-consumers.json")
(root / "docs/offline-strategy.md").write_text(
    "# Offline strategy\n\n## 기계 판독 정책 계약\n\n"
    "<!-- station-data-policy:start -->\n"
    "```json\n"
    + (root / "docs/station-data-policy.json").read_text().rstrip()
    + "\n```\n"
    "<!-- station-data-policy:end -->\n"
)
references = {
    "README.md": (
        "<!-- station-data-policy-ref: retry -->"
        "[structured `retry` contract](docs/offline-strategy.md#기계-판독-정책-계약)\n"
        "<!-- station-data-policy-ref: retry -->"
        "[structured `retry` contract](docs/offline-strategy.md#기계-판독-정책-계약)\n"
    ),
    "docs/onboarding/developer-onboarding-guide.md": (
        "<!-- station-data-policy-ref: retry -->"
        "[structured `retry` contract](../offline-strategy.md#기계-판독-정책-계약)\n"
        "<!-- station-data-policy-ref: freshness -->"
        "[structured `freshness` contract](../offline-strategy.md#기계-판독-정책-계약)\n"
    ),
    "docs/agent-workflow.md": (
        "<!-- station-data-policy-ref: retry -->"
        "[structured `retry` contract](offline-strategy.md#기계-판독-정책-계약)\n"
    ),
    "docs/test-strategy.md": (
        "<!-- station-data-policy-ref: retry -->"
        "[structured `retry` contract](offline-strategy.md#기계-판독-정책-계약)\n"
        "<!-- station-data-policy-ref: freshness -->"
        "[structured `freshness` contract](offline-strategy.md#기계-판독-정책-계약)\n"
    ),
}
for path, text in references.items():
    target = root / path
    target.write_text(target.read_text() + text)
(root / "docs/state-model.md").write_text(
    "# State model\n\n## Station-list 결정적 상태 계약\n\n"
    "<!-- station-list-state-contract:start -->\n"
    "```json\n"
    + (root / "docs/station-list-state-contract.json").read_text().rstrip()
    + "\n```\n"
    "<!-- station-list-state-contract:end -->\n"
)
state_references = {
    "README.md": "docs/state-model.md",
    "docs/agent-workflow.md": "state-model.md",
    "docs/architecture.md": "state-model.md",
    "docs/module-contracts.md": "state-model.md",
    "docs/onboarding/developer-onboarding-guide.md": "../state-model.md",
    "docs/project-reading-guide.md": "state-model.md",
    "docs/test-strategy.md": "state-model.md",
    "docs/verification-matrix.md": "state-model.md",
}
for path, target_path in state_references.items():
    target = root / path
    target.write_text(
        target.read_text()
        + "<!-- station-list-state-contract-ref -->"
        "[상태 모델의 구조화된 station-list 계약]"
        f"({target_path}#station-list-결정적-상태-계약)\n"
    )
state_sources = {
    "feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/LocationStateMachine.kt": (
        "class LocationStateMachine {\n"
        "  var permissionGeneration = 0\n  var gpsGeneration = 0\n"
        "  var locationRequestGeneration = 0\n  var addressRequestGeneration = 0\n"
        "  suspend fun resolveAddressLabel() = Unit\n"
        "  val result = LocationAcquisitionResult.Superseded\n}\n"
    ),
    "feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationSearchOrchestrator.kt": (
        "class StationSearchOrchestrator {\n"
        "  val observationFailed = false\n  fun retryObservation() = Unit\n"
        "  data class ObservationSession(val generation: Int)\n}\n"
    ),
    "feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/RefreshCoordinator.kt": (
        "class RefreshCoordinator {\n  data class ActiveRefreshWork(val id: Long)\n"
        "  val onResult: suspend () -> Unit = {}\n"
        "  fun refresh() { scope.launch { resolveAddressLabel() }.invokeOnCompletion {} }\n}\n"
    ),
    "feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListCommandQueue.kt": (
        "class StationListCommandQueue {\n"
        "  val mutableCommands = MutableStateFlow<List<StationListUiCommand>>(emptyList())\n"
        "  fun enqueue(command: StationListUiCommand) { mutableCommands.value = mutableCommands.value + command }\n"
        "  fun acknowledge(commandId: Long) { if (current.firstOrNull()?.id == commandId) Unit }\n}\n"
    ),
    "feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListStateAssembler.kt": (
        "object StationListStateAssembler {\n"
        "  fun assemble(inputs: StationListStateInputs): StationListUiState = TODO()\n}\n"
    ),
    "feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListStateInputs.kt": (
        "data class StationListStateInputs(val value: Int)\nfun projectStationSearchResult() = Unit\n"
    ),
    "feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt": (
        "class StationListViewModel {\n  val state = StationListStateAssembler.assemble(inputs)\n}\n"
    ),
    "data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt": (
        "class DefaultStationRepository(private val latestWatchIntentGate: LatestWatchIntentGate) {\n"
        "  fun updateWatchState() = WatchMutationResult.Committed\n"
        "  fun removeWatchedStation() = WatchMutationResult.Superseded\n}\n"
    ),
    "core/database/src/main/kotlin/com/gasstation/core/database/station/WatchedStationDao.kt": (
        "@Dao\ninterface WatchedStationDao {\n"
        "  @Insert(onConflict = OnConflictStrategy.IGNORE)\n"
        "  fun insertIfAbsent() = Unit\n"
        '  @Query("SELECT stationId FROM watched_station ORDER BY watchedAtEpochMillis DESC, stationId ASC")\n'
        "  fun observeWatchedStationIds(): Flow<List<String>> = TODO()\n"
        '  @Query("SELECT * FROM watched_station ORDER BY watchedAtEpochMillis DESC, stationId ASC")\n'
        "  fun observeWatchedStations(): Flow<List<WatchedStationEntity>> = TODO()\n}\n"
    ),
}
for path, text in state_sources.items():
    target = root / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text)
(root / "docs/documentation-catalog.json").write_text(
    json.dumps({"schemaVersion": 1, "documents": entries}, indent=2) + "\n"
)
links = [f"- [{path}]({os.path.relpath(path, 'docs')})" for path in paths if path != "docs/README.md"]
(root / "docs/README.md").write_text("# Documentation hub\n\n" + "\n".join(links) + "\n")
PY
git -C "$fixture/repo" add .
git -C "$fixture/repo" commit -qm "test: add contract fixture"
ci_base=$(git -C "$fixture/repo" rev-parse HEAD^)

assert_contains "$(cat "$repo_root/.github/workflows/android.yml")" "python3 scripts/docs/validate.py --check-gradle-tasks"

"$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo"
GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(workflow.read_text().replace("  pull_request:\n", "", 1))
PY
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/coverage-pr-trigger.out" 2>&1; then
  fail "CI accepted a coverage workflow without pull-request coverage"
fi
assert_contains "$(cat "$fixture/coverage-pr-trigger.out")" "coverage workflow must run for pull requests, main pushes, and v tags"
assert_error_locations "$(cat "$fixture/coverage-pr-trigger.out")"
git -C "$fixture/repo" restore .github/workflows/android.yml

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(
    workflow.read_text().replace(
        "      - name: Verify trustworthy coverage\n",
        "      - name: Verify trustworthy coverage\n"
        "        continue-on-error: true\n",
        1,
    )
)
PY
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/coverage-report-only.out" 2>&1; then
  fail "CI accepted a report-only coverage verifier"
fi
assert_contains "$(cat "$fixture/coverage-report-only.out")" "coverage verification step must be blocking"
assert_error_locations "$(cat "$fixture/coverage-report-only.out")"
git -C "$fixture/repo" restore .github/workflows/android.yml

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(
    workflow.read_text().replace(
        ":build-logic:convention:test",
        ":build-logic:convention:tests",
        1,
    )
)
PY
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/convention-test-task.out" 2>&1; then
  fail "CI accepted a misspelled static-analysis convention test task"
fi
assert_contains "$(cat "$fixture/convention-test-task.out")" "static-analysis command missing: convention plugin tests"
assert_error_locations "$(cat "$fixture/convention-test-task.out")"
git -C "$fixture/repo" restore .github/workflows/android.yml

for blocking_field in \
  'continue-on-error: true' \
  'continue-on-error: ${{ true }}' \
  'continue-on-error: no' \
  'continue-on-error: off' \
  'continue-on-error: ${{ github.event_name == '\''pull_request'\'' }}' \
  'continue-on-error: "${{ github.event_name == '\''pull_request'\'' }}"' \
  'continue-on-error: '\''${{ github.event_name == '\'''\''pull_request'\'''\'' }}'\'''; do
  python3 - "$fixture/repo/.github/workflows/android.yml" "$blocking_field" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
blocking_field = sys.argv[2]
workflow.write_text(
    workflow.read_text().replace(
        "      - name: Convention plugin tests\n",
        "      - name: Convention plugin tests\n"
        f"        {blocking_field}\n",
        1,
    )
)
PY
  if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/convention-test-nonblocking.out" 2>&1; then
    fail "CI accepted a non-blocking convention test step with $blocking_field"
  fi
  assert_contains "$(cat "$fixture/convention-test-nonblocking.out")" "static-analysis convention plugin tests step must be blocking"
  assert_error_locations "$(cat "$fixture/convention-test-nonblocking.out")"
  git -C "$fixture/repo" restore .github/workflows/android.yml
done

for disabling_field in \
  'if: false' \
  'if: ${{ false }}' \
  'if: ${{ github.event_name == '\''push'\'' }}' \
  'if: "${{ github.event_name == '\''push'\'' }}"' \
  'if: '\''${{ github.event_name == '\'''\''push'\'''\'' }}'\''' \
  'if: true' \
  'if: ${{ true }}' \
  'if: always()'; do
  python3 - "$fixture/repo/.github/workflows/android.yml" "$disabling_field" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
disabling_field = sys.argv[2]
workflow.write_text(
    workflow.read_text().replace(
        "      - name: Convention plugin tests\n",
        "      - name: Convention plugin tests\n"
        f"        {disabling_field}\n",
        1,
    )
)
PY
  if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/convention-test-disabled.out" 2>&1; then
    fail "CI accepted a disabled convention test step with $disabling_field"
  fi
  assert_contains "$(cat "$fixture/convention-test-disabled.out")" "static-analysis convention plugin tests step must not be disabled"
  assert_error_locations "$(cat "$fixture/convention-test-disabled.out")"
  git -C "$fixture/repo" restore .github/workflows/android.yml
done

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(
    workflow.read_text().replace(
        "      - name: Convention plugin tests\n",
        "      - name: Convention plugin tests\n"
        "        continue-on-error: false\n",
        1,
    )
)
PY
GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci
git -C "$fixture/repo" restore .github/workflows/android.yml

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(
    workflow.read_text().replace(
        "      - name: Convention plugin tests\n",
        "      - name: Conditional unrelated step\n"
        "        continue-on-error: ${{ github.event_name == 'pull_request' }}\n"
        "        if: ${{ github.event_name == 'push' }}\n"
        "        run: echo decoy\n"
        "      - name: Convention plugin tests\n",
        1,
    )
)
PY
GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci
git -C "$fixture/repo" restore .github/workflows/android.yml

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(
    workflow.read_text().replace(
        "        run: ./gradlew :build-logic:convention:test --warning-mode fail",
        "        run: ./gradlew help --warning-mode fail\n"
        "        # ./gradlew :build-logic:convention:test --warning-mode fail",
        1,
    )
)
PY
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/convention-test-comment.out" 2>&1; then
  fail "CI accepted a static-analysis convention test task present only in a comment"
fi
assert_contains "$(cat "$fixture/convention-test-comment.out")" "static-analysis command missing: convention plugin tests"
assert_error_locations "$(cat "$fixture/convention-test-comment.out")"
git -C "$fixture/repo" restore .github/workflows/android.yml

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(
    workflow.read_text().replace(
        "  lint-tests:\n    runs-on: ubuntu-latest\n",
        "  lint-tests:\n    runs-on: ubuntu-latest\n    continue-on-error: true\n",
    )
)
PY
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/lint-escape.out" 2>&1; then
  fail "CI accepted a lint-tests continue-on-error escape"
fi
assert_contains "$(cat "$fixture/lint-escape.out")" "lint-tests must be blocking"
assert_error_locations "$(cat "$fixture/lint-escape.out")"
git -C "$fixture/repo" restore .github/workflows/android.yml

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(
    workflow.read_text().replace(
        "-Pgasstation.lintTestSources=true",
        "-Pgasstation.lintTestSources=false",
    )
)
PY
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/lint-property.out" 2>&1; then
  fail "CI accepted a lint-tests false property"
fi
assert_contains "$(cat "$fixture/lint-property.out")" "lint-tests command missing: test-source property"
assert_error_locations "$(cat "$fixture/lint-property.out")"
git -C "$fixture/repo" restore .github/workflows/android.yml

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(
    workflow.read_text().replace("            :app:lintProdDebug \\\n", "", 1)
)
PY
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/lint-prod-task.out" 2>&1; then
  fail "CI accepted production lint without the prod app task"
fi
assert_contains "$(cat "$fixture/lint-prod-task.out")" "static-analysis command missing: prod app lint task"
assert_error_locations "$(cat "$fixture/lint-prod-task.out")"
git -C "$fixture/repo" restore .github/workflows/android.yml

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(
    workflow.read_text().replace("            --continue\n", "            --continue || true\n", 1)
)
PY
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/lint-success-mask.out" 2>&1; then
  fail "CI accepted a production lint command whose failure is masked"
fi
assert_contains "$(cat "$fixture/lint-success-mask.out")" "static-analysis command must be one standalone ./gradlew invocation"
assert_error_locations "$(cat "$fixture/lint-success-mask.out")"
git -C "$fixture/repo" restore .github/workflows/android.yml

for dry_run_option in --dry-run -m; do
  python3 - "$fixture/repo/.github/workflows/android.yml" "$dry_run_option" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
option = sys.argv[2]
workflow.write_text(
    workflow.read_text().replace(
        "            --continue\n",
        f"            {option} \\\n            --continue\n",
        1,
    )
)
PY
  if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/lint-dry-run.out" 2>&1; then
    fail "CI accepted a non-executing production lint command with $dry_run_option"
  fi
  assert_contains "$(cat "$fixture/lint-dry-run.out")" "static-analysis command must execute lint: dry-run option forbidden"
  assert_error_locations "$(cat "$fixture/lint-dry-run.out")"
  git -C "$fixture/repo" restore .github/workflows/android.yml
done

for exclude_option in -x --exclude-task; do
  python3 - "$fixture/repo/.github/workflows/android.yml" "$exclude_option" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
option = sys.argv[2]
workflow.write_text(
    workflow.read_text().replace(
        "            --continue\n",
        f"            {option} :app:lintProdDebug \\\n            --continue\n",
        1,
    )
)
PY
  if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/lint-task-exclusion.out" 2>&1; then
    fail "CI accepted production lint with a required task excluded via $exclude_option"
  fi
  assert_contains "$(cat "$fixture/lint-task-exclusion.out")" "static-analysis command must not exclude lint tasks"
  assert_error_locations "$(cat "$fixture/lint-task-exclusion.out")"
  git -C "$fixture/repo" restore .github/workflows/android.yml
done

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(
    workflow.read_text()
    .replace(
        "        run: |\n          ./gradlew \\\n",
        "        run: |\n          # :app:lintProdDebug\n          ./gradlew \\\n",
        1,
    )
    .replace("            :app:lintProdDebug \\\n", "            help \\\n", 1)
)
PY
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/lint-comment-shadow.out" 2>&1; then
  fail "CI accepted a production lint task present only in a shell comment"
fi
assert_contains "$(cat "$fixture/lint-comment-shadow.out")" "static-analysis command missing: prod app lint task"
assert_error_locations "$(cat "$fixture/lint-comment-shadow.out")"
git -C "$fixture/repo" restore .github/workflows/android.yml

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(
    workflow.read_text()
    .replace(
        "        run: |\n          ./gradlew \\\n",
        "        run: |\n          echo :app:lintProdDebug\n          ./gradlew \\\n",
        1,
    )
    .replace("            :app:lintProdDebug \\\n", "            help \\\n", 1)
)
PY
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/lint-echo-shadow.out" 2>&1; then
  fail "CI accepted a production lint task present only as an echo argument"
fi
assert_contains "$(cat "$fixture/lint-echo-shadow.out")" "static-analysis command missing: prod app lint task"
assert_error_locations "$(cat "$fixture/lint-echo-shadow.out")"
git -C "$fixture/repo" restore .github/workflows/android.yml

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(
    workflow.read_text().replace(
        "        uses: actions/upload-artifact@v7\n",
        "        uses: actions/checkout@v7\n        # uses: actions/upload-artifact@v7\n",
        1,
    )
)
PY
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/lint-artifact-action-comment.out" 2>&1; then
  fail "CI accepted an artifact upload action present only in a YAML comment"
fi
assert_contains "$(cat "$fixture/lint-artifact-action-comment.out")" "static-analysis lint artifact upload missing: lint-production-reports"
assert_error_locations "$(cat "$fixture/lint-artifact-action-comment.out")"
git -C "$fixture/repo" restore .github/workflows/android.yml

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(
    workflow.read_text().replace(
        "        if: always()\n",
        "        if: success()\n        # if: always()\n",
        1,
    )
)
PY
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/lint-artifact-condition-comment.out" 2>&1; then
  fail "CI accepted if always present only in a YAML comment"
fi
assert_contains "$(cat "$fixture/lint-artifact-condition-comment.out")" "static-analysis lint artifact upload missing: always condition"
assert_error_locations "$(cat "$fixture/lint-artifact-condition-comment.out")"
git -C "$fixture/repo" restore .github/workflows/android.yml

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(
    workflow.read_text().replace(
        '          path: "**/build/reports/lint-results-*"\n',
        '          path: "**/build/reports/not-lint-*"\n'
        '          # path: "**/build/reports/lint-results-*"\n',
        1,
    )
)
PY
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/lint-artifact-path-comment.out" 2>&1; then
  fail "CI accepted a report glob present only in a nested YAML comment"
fi
assert_contains "$(cat "$fixture/lint-artifact-path-comment.out")" "static-analysis lint artifact upload missing: report glob"
assert_error_locations "$(cat "$fixture/lint-artifact-path-comment.out")"
git -C "$fixture/repo" restore .github/workflows/android.yml

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(workflow.read_text().replace("        if: always()\n", "", 1))
PY
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/lint-artifact-always.out" 2>&1; then
  fail "CI accepted a production lint artifact without if always"
fi
assert_contains "$(cat "$fixture/lint-artifact-always.out")" "static-analysis lint artifact upload missing: always condition"
assert_error_locations "$(cat "$fixture/lint-artifact-always.out")"
git -C "$fixture/repo" restore .github/workflows/android.yml

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(
    workflow.read_text().replace(
        "needs: [agent-contracts, static-analysis, lint-tests,",
        "needs: [agent-contracts, static-analysis,",
    )
)
PY
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/lint-release-prerequisite.out" 2>&1; then
  fail "CI accepted release publishing without lint-tests"
fi
assert_contains "$(cat "$fixture/lint-release-prerequisite.out")" "tag release publishing prerequisite missing: lint-tests"
assert_error_locations "$(cat "$fixture/lint-release-prerequisite.out")"
git -C "$fixture/repo" restore .github/workflows/android.yml

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(workflow.read_text().replace("      - run: scripts/agent/verify-room-schemas.sh\n", ""))
PY
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/room-schema-workflow.out" 2>&1; then
  fail "CI accepted a workflow without Room schema verification"
fi
assert_contains "$(cat "$fixture/room-schema-workflow.out")" ".github/workflows/android.yml:1: required contract anchor missing"
assert_error_locations "$(cat "$fixture/room-schema-workflow.out")"
git -C "$fixture/repo" restore .github/workflows/android.yml

printf 'bad whitespace   \n' >> "$fixture/repo/README.md"
if "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" > "$fixture/unstaged-diff.out" 2>&1; then
  fail "unstaged whitespace was accepted"
fi
assert_contains "$(cat "$fixture/unstaged-diff.out")" "README.md:7: trailing whitespace"
assert_error_locations "$(cat "$fixture/unstaged-diff.out")"
git -C "$fixture/repo" restore README.md

printf 'staged whitespace   \n' >> "$fixture/repo/docs/guide.md"
git -C "$fixture/repo" add docs/guide.md
if "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" > "$fixture/staged-diff.out" 2>&1; then
  fail "staged whitespace was accepted"
fi
assert_contains "$(cat "$fixture/staged-diff.out")" "docs/guide.md:2: trailing whitespace"
assert_error_locations "$(cat "$fixture/staged-diff.out")"
git -C "$fixture/repo" restore --staged docs/guide.md
git -C "$fixture/repo" restore docs/guide.md

git clone -q "$fixture/repo" "$fixture/ci-diff"
git -C "$fixture/ci-diff" config user.name "Agent Test"
git -C "$fixture/ci-diff" config user.email "agent-test@example.invalid"
ci_diff_base=$(git -C "$fixture/ci-diff" rev-parse HEAD)
printf 'committed whitespace   \n' > "$fixture/ci-diff/committed.txt"
git -C "$fixture/ci-diff" add committed.txt
git -C "$fixture/ci-diff" commit -qm "test: add committed whitespace"
if GASSTATION_CI_BASE_REF="$ci_diff_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/ci-diff" --ci > "$fixture/committed-diff.out" 2>&1; then
  fail "CI committed whitespace was accepted"
fi
assert_contains "$(cat "$fixture/committed-diff.out")" "committed.txt:1: trailing whitespace"
assert_error_locations "$(cat "$fixture/committed-diff.out")"
if GASSTATION_CI_BASE_REF= "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/ci-diff" --ci > "$fixture/fallback-diff.out" 2>&1; then
  fail "CI HEAD^ fallback missed committed whitespace"
fi
assert_contains "$(cat "$fixture/fallback-diff.out")" "committed.txt:1: trailing whitespace"
assert_error_locations "$(cat "$fixture/fallback-diff.out")"

cat > "$fixture/repo/.github/workflows/android.yml" <<'EOF'
name: Android CI
jobs:
  static-analysis:
    runs-on: ubuntu-latest
EOF
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/workflow.out" 2>&1; then
  fail "workflow without agent-contracts job was accepted"
fi
assert_contains "$(cat "$fixture/workflow.out")" ".github/workflows/android.yml:1: agent-contracts job missing"
assert_error_locations "$(cat "$fixture/workflow.out")"
cat > "$fixture/repo/.github/workflows/android.yml" <<'EOF'
name: Android CI
jobs:
  agent-contracts:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
        with:
          fetch-depth: 0
      - run: scripts/agent/check-contracts.sh --ci
        env:
          GASSTATION_CI_BASE_REF: fixture-base
  static-analysis:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - name: Production lint
        run: |
          ./gradlew \
            spotlessCheck \
            :app:lintDemoDebug \
            :app:lintProdDebug \
            lint \
            verifyModuleBoundaries \
            verifyNoDeprecatedComposeTestApis \
            verifyCiRobolectricRuntime \
            -Pgasstation.lintTestSources=false \
            --warning-mode fail \
            --continue
      - name: Upload production lint reports
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: lint-production-reports
          path: "**/build/reports/lint-results-*"
          if-no-files-found: error
          retention-days: 7
  lint-tests:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - name: Test-source lint
        run: |
          ./gradlew \
            :app:lintDemoDebug \
            :app:lintProdDebug \
            lint \
            -Pgasstation.lintTestSources=true \
            --warning-mode fail \
            --continue
      - name: Upload test-source lint reports
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: lint-test-source-reports
          path: "**/build/reports/lint-results-*"
          if-no-files-found: error
          retention-days: 7
  release-publish:
    if: ${{ startsWith(github.ref, 'refs/tags/v') }}
    needs: [agent-contracts, static-analysis, lint-tests, unit-tests, screenshot-tests, assemble, release-assemble, coverage]
    permissions:
      contents: write
    steps:
      - uses: actions/download-artifact@v8
      - name: Publish release
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          notes_file=$(find docs/release-notes/ -name "*-${GITHUB_REF_NAME}.md")
          (
            cd release-assets
            sha256sum ./*.apk > SHA256SUMS.txt
          )
          gh release create "$GITHUB_REF_NAME" --notes-file "$notes_file" release-assets/*.apk
EOF

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import re
import sys

workflow = Path(sys.argv[1])
workflow.write_text(
    re.sub(r"(?ms)^  release-publish:\s*\n.*?(?=^  [A-Za-z0-9_-]+:\s*$|\Z)", "", workflow.read_text())
)
PY
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/release-workflow.out" 2>&1; then
  fail "workflow without tag release publishing was accepted"
fi
assert_contains "$(cat "$fixture/release-workflow.out")" ".github/workflows/android.yml:1: tag release publishing contract missing"
assert_error_locations "$(cat "$fixture/release-workflow.out")"
git -C "$fixture/repo" restore .github/workflows/android.yml

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(workflow.read_text().replace(", coverage]", "]"))
PY
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/release-needs.out" 2>&1; then
  fail "release publishing without the coverage prerequisite was accepted"
fi
assert_contains "$(cat "$fixture/release-needs.out")" ".github/workflows/android.yml:1: tag release publishing prerequisite missing: coverage"
assert_error_locations "$(cat "$fixture/release-needs.out")"
git -C "$fixture/repo" restore .github/workflows/android.yml

python3 - "$fixture/repo/.github/workflows/android.yml" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1])
workflow.write_text(workflow.read_text().replace("          cd release-assets\n", ""))
PY
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/release-checksum.out" 2>&1; then
  fail "release publishing with non-portable checksum paths was accepted"
fi
assert_contains "$(cat "$fixture/release-checksum.out")" ".github/workflows/android.yml:1: tag release publishing contract missing: portable checksum directory"
assert_error_locations "$(cat "$fixture/release-checksum.out")"
git -C "$fixture/repo" restore .github/workflows/android.yml

rm "$fixture/repo/docs/state-model.md"
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/missing-file.out" 2>&1; then
  fail "CI accepted a missing required live file"
fi
assert_contains "$(cat "$fixture/missing-file.out")" "docs/state-model.md:1: required CI contract file missing"
assert_error_locations "$(cat "$fixture/missing-file.out")"
git -C "$fixture/repo" restore docs/state-model.md

printf '# Database contract without durable anchors\n' > "$fixture/repo/core/database/AGENTS.md"
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/missing-anchor.out" 2>&1; then
  fail "CI accepted a missing durable anchor"
fi
assert_contains "$(cat "$fixture/missing-anchor.out")" "core/database/AGENTS.md:1: required contract anchor missing"
assert_error_locations "$(cat "$fixture/missing-anchor.out")"
git -C "$fixture/repo" restore core/database/AGENTS.md

for json_config in .codex/hooks.json .claude/settings.json; do
  printf '{\n' > "$fixture/repo/$json_config"
  if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/malformed-json.out" 2>&1; then
    fail "CI accepted malformed $json_config"
  fi
  assert_contains "$(cat "$fixture/malformed-json.out")" "$json_config:2: malformed JSON hook config"
  assert_error_locations "$(cat "$fixture/malformed-json.out")"
  git -C "$fixture/repo" restore "$json_config"
done

cat > "$fixture/repo/.codex/hooks.json" <<'EOF'
{"hooks": {"SessionStart": [], "PreToolUse": []}}
EOF
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/missing-event.out" 2>&1; then
  fail "CI accepted a missing Codex Stop event"
fi
assert_contains "$(cat "$fixture/missing-event.out")" ".codex/hooks.json:1: required hook event missing: Stop"
assert_error_locations "$(cat "$fixture/missing-event.out")"
git -C "$fixture/repo" restore .codex/hooks.json

cat > "$fixture/repo/.codex/hooks.json" <<'EOF'
{"hooks": {"SessionStart": [], "PreToolUse": [{"hooks": [{"type": "command", "command": "scripts/agent/pre_tool_policy.py"}]}], "Stop": [{"hooks": [{"type": "command", "command": "scripts/agent/stop_check.py"}]}]}}
EOF
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/empty-event.out" 2>&1; then
  fail "CI accepted an empty required hook event"
fi
assert_contains "$(cat "$fixture/empty-event.out")" ".codex/hooks.json:1: required hook event must be a non-empty list: SessionStart"
assert_error_locations "$(cat "$fixture/empty-event.out")"
git -C "$fixture/repo" restore .codex/hooks.json

cat > "$fixture/repo/.codex/hooks.json" <<'EOF'
{"hooks": {"SessionStart": {}, "PreToolUse": [{"hooks": [{"type": "command", "command": "scripts/agent/pre_tool_policy.py"}]}], "Stop": [{"hooks": [{"type": "command", "command": "scripts/agent/stop_check.py"}]}]}}
EOF
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/non-list-event.out" 2>&1; then
  fail "CI accepted a non-list required hook event"
fi
assert_contains "$(cat "$fixture/non-list-event.out")" ".codex/hooks.json:1: required hook event must be a non-empty list: SessionStart"
assert_error_locations "$(cat "$fixture/non-list-event.out")"
git -C "$fixture/repo" restore .codex/hooks.json

cat > "$fixture/repo/.codex/hooks.json" <<'EOF'
{"hooks": {"SessionStart": [{"hooks": [{"type": "command"}]}], "PreToolUse": [{"hooks": [{"type": "command", "command": "scripts/agent/pre_tool_policy.py"}]}], "Stop": [{"hooks": [{"type": "command", "command": "scripts/agent/stop_check.py"}]}]}}
EOF
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/no-command-event.out" 2>&1; then
  fail "CI accepted a required event without a command hook"
fi
assert_contains "$(cat "$fixture/no-command-event.out")" ".codex/hooks.json:1: required hook event has no well-formed command hook: SessionStart"
assert_error_locations "$(cat "$fixture/no-command-event.out")"
git -C "$fixture/repo" restore .codex/hooks.json

cat > "$fixture/repo/.codex/hooks.json" <<'EOF'
{"hooks": {"SessionStart": [{"hooks": [{"type": "command", "command": "scripts/agent/preflight.sh"}]}], "PreToolUse": [{"hooks": [{"type": "command", "command": "scripts/agent/pre_tool_policy.py"}]}], "Stop": [{"hooks": [{"type": "command", "command": "/bin/bash -lc \"echo scripts/agent/stop_check.py\""}, {"type": "command", "command": "/bin/bash -ec \"echo scripts/agent/stop_check.py\""}]}]}}
EOF
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/wrong-event-target.out" 2>&1; then
  fail "CI accepted a wrong or masked Codex hook target"
fi
assert_contains "$(cat "$fixture/wrong-event-target.out")" ".codex/hooks.json:1: required hook target missing for Stop: scripts/agent/stop_check.py"
assert_error_locations "$(cat "$fixture/wrong-event-target.out")"
git -C "$fixture/repo" restore .codex/hooks.json

cat > "$fixture/repo/.codex/hooks.json" <<'EOF'
{"hooks": {"SessionStart": [{"hooks": [{"type": "command", "command": "scripts/agent/preflight.sh"}]}], "PreToolUse": [{"hooks": [{"type": "command", "command": "scripts/agent/pre_tool_policy.py"}]}], "Stop": [{"hooks": [{"type": "command", "command": "/tmp/scripts/agent/stop_check.py"}]}]}}
EOF
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/outside-hook-target.out" 2>&1; then
  fail "CI accepted an outside path that only ended in the expected hook target"
fi
assert_contains "$(cat "$fixture/outside-hook-target.out")" ".codex/hooks.json:1: required hook target missing for Stop: scripts/agent/stop_check.py"
assert_error_locations "$(cat "$fixture/outside-hook-target.out")"
git -C "$fixture/repo" restore .codex/hooks.json

cat > "$fixture/repo/.codex/hooks.json" <<'EOF'
{"hooks": {"SessionStart": [{"hooks": [{"type": "http", "command": "scripts/agent/preflight.sh"}]}], "PreToolUse": [{"hooks": [{"type": "command", "command": "scripts/agent/pre_tool_policy.py"}]}], "Stop": [{"hooks": [{"type": "command", "command": "scripts/agent/stop_check.py"}]}]}}
EOF
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/non-command-hook.out" 2>&1; then
  fail "CI accepted a non-command hook type"
fi
assert_contains "$(cat "$fixture/non-command-hook.out")" ".codex/hooks.json:1: required hook event has no well-formed command hook: SessionStart"
assert_error_locations "$(cat "$fixture/non-command-hook.out")"
git -C "$fixture/repo" restore .codex/hooks.json

cat > "$fixture/repo/.claude/settings.json" <<'EOF'
{"hooks": {"PreToolUse": [], "PostToolUse": []}}
EOF
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/missing-claude-event.out" 2>&1; then
  fail "CI accepted a missing Claude SubagentStop event"
fi
assert_contains "$(cat "$fixture/missing-claude-event.out")" ".claude/settings.json:1: required hook event missing: SubagentStop"
assert_error_locations "$(cat "$fixture/missing-claude-event.out")"
git -C "$fixture/repo" restore .claude/settings.json

cat > "$fixture/repo/.claude/settings.json" <<'EOF'
{"hooks": {"PreToolUse": [{"hooks": [{"type": "command", "command": "scripts/agent/pre_tool_policy.py"}]}], "PostToolUse": [{"hooks": [{"type": "command", "command": "scripts/agent/pre_tool_policy.py # scripts/agent/check-contracts.sh"}]}], "SubagentStop": [{"hooks": [{"type": "command", "command": "scripts/agent/check-contracts.sh"}]}]}}
EOF
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/wrong-claude-target.out" 2>&1; then
  fail "CI accepted a wrong or masked Claude hook target"
fi
assert_contains "$(cat "$fixture/wrong-claude-target.out")" ".claude/settings.json:1: required hook target missing for PostToolUse: scripts/agent/check-contracts.sh"
assert_error_locations "$(cat "$fixture/wrong-claude-target.out")"
git -C "$fixture/repo" restore .claude/settings.json

rm "$fixture/repo/scripts/agent/stop_check.py"
if GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/missing-hook-target.out" 2>&1; then
  fail "CI accepted a missing configured hook target"
fi
assert_contains "$(cat "$fixture/missing-hook-target.out")" "scripts/agent/stop_check.py:1: configured hook target missing"
assert_error_locations "$(cat "$fixture/missing-hook-target.out")"
git -C "$fixture/repo" restore scripts/agent/stop_check.py

printf '[Broken](docs/missing.md)\n' >> "$fixture/repo/README.md"
if "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" > "$fixture/broken.out" 2>&1; then
  fail "broken link was accepted"
fi
assert_contains "$(cat "$fixture/broken.out")" "missing.md"
assert_error_locations "$(cat "$fixture/broken.out")"
git -C "$fixture/repo" checkout -q -- README.md

printf 'command = "/Users/example/private/hook.sh"\n' >> "$fixture/repo/.codex/config.toml"
if "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" > "$fixture/path.out" 2>&1; then
  fail "personal path was accepted"
fi
assert_contains "$(cat "$fixture/path.out")" "personal absolute path"
assert_error_locations "$(cat "$fixture/path.out")"
git -C "$fixture/repo" checkout -q -- .codex/config.toml

cat >> "$fixture/repo/.codex/config.toml" <<'EOF'
command = "C:\Users\example\private\hook.bat"
EOF
if "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" > "$fixture/windows-path.out" 2>&1; then
  fail "Windows personal path was accepted"
fi
assert_contains "$(cat "$fixture/windows-path.out")" "personal absolute path"
assert_error_locations "$(cat "$fixture/windows-path.out")"
git -C "$fixture/repo" checkout -q -- .codex/config.toml

printf '[Quick-only](docs/missing.md)\n' >> "$fixture/repo/README.md"
quick_output=$("$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --quick)
assert_contains "$quick_output" "agent-contracts: PASS"
git -C "$fixture/repo" checkout -q -- README.md

printf 'The fixture ships a 2-module setup.\nCurrent version: `1.2.0` (`versionCode` 8).\n[Guide](docs/guide.md)\n' > "$fixture/repo/README.md"
if "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" > "$fixture/modules.out" 2>&1; then
  fail "module count drift was accepted"
fi
assert_contains "$(cat "$fixture/modules.out")" "active module count is 1"
assert_error_locations "$(cat "$fixture/modules.out")"

printf 'The fixture ships a 1-module setup.\nCurrent version: `1.1.0` (`versionCode` 7).\n[Guide](docs/guide.md)\n' > "$fixture/repo/README.md"
if "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" > "$fixture/version.out" 2>&1; then
  fail "version drift was accepted"
fi
assert_contains "$(cat "$fixture/version.out")" "expected current version"
assert_error_locations "$(cat "$fixture/version.out")"
git -C "$fixture/repo" checkout -q -- README.md

rm "$fixture/repo/docs/release-notes/2026-06-07-v1.2.0.md"
if "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" > "$fixture/release-note.out" 2>&1; then
  fail "current version without a release note was accepted"
fi
assert_contains "$(cat "$fixture/release-note.out")" "current version release note missing"
assert_error_locations "$(cat "$fixture/release-note.out")"
git -C "$fixture/repo" restore docs/release-notes/2026-06-07-v1.2.0.md

printf 'opinet.apikey=real-secret\n' > "$fixture/repo/gradle.properties"
git -C "$fixture/repo" add gradle.properties
if "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" > "$fixture/secret.out" 2>&1; then
  fail "non-empty tracked secret was accepted"
fi
assert_contains "$(cat "$fixture/secret.out")" "non-empty secret assignment"
assert_error_locations "$(cat "$fixture/secret.out")"

git -C "$fixture/repo" reset -q HEAD -- gradle.properties
"$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo"

printf 'fixture artifact\n' > "$fixture/repo/release.apk"
git -C "$fixture/repo" add release.apk
if "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --quick > "$fixture/artifact.out" 2>&1; then
  fail "tracked artifact was accepted by quick check"
fi
assert_contains "$(cat "$fixture/artifact.out")" "tracked local/generated artifact"
assert_error_locations "$(cat "$fixture/artifact.out")"
git -C "$fixture/repo" reset -q HEAD -- release.apk

mkdir -p "$fixture/repo/scripts/agent"
printf 'fi\n' > "$fixture/repo/scripts/agent/broken.sh"
if "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" > "$fixture/shell.out" 2>&1; then
  fail "shell syntax failure was accepted"
fi
assert_contains "$(cat "$fixture/shell.out")" "shell syntax error"
assert_error_locations "$(cat "$fixture/shell.out")"
assert_not_contains "$(cat "$fixture/shell.out")" "$fixture/repo"

printf '{\n' > "$fixture/repo/docs/documentation-catalog.json"
if "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" > "$fixture/docs-validator.out" 2>&1; then
  fail "agent contract check accepted a malformed documentation catalog"
fi
assert_contains "$(cat "$fixture/docs-validator.out")" "malformed catalog JSON"
assert_error_locations "$(cat "$fixture/docs-validator.out")"
rm "$fixture/repo/docs/documentation-catalog.json"

if GASSTATION_TEST_ALLOW_MISSING_DOCS_CATALOG=1 "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" > "$fixture/missing-catalog.out" 2>&1; then
  fail "missing documentation catalog was accepted"
fi
assert_contains "$(cat "$fixture/missing-catalog.out")" "documentation catalog missing"

mkdir -p "$fixture/no-validator/scripts/agent" "$fixture/no-validator/docs"
make_git_repo "$fixture/no-validator"
cp "$repo_root/scripts/agent/check_contracts.py" "$fixture/no-validator/scripts/agent/check_contracts.py"
printf '{}\n' > "$fixture/no-validator/docs/documentation-catalog.json"
git -C "$fixture/no-validator" add .
git -C "$fixture/no-validator" commit -qm "test: missing validator fixture"
if python3 "$fixture/no-validator/scripts/agent/check_contracts.py" --root "$fixture/no-validator" > "$fixture/missing-validator.out" 2>&1; then
  fail "missing documentation validator was accepted"
fi
assert_contains "$(cat "$fixture/missing-validator.out")" "documentation validator missing"

if [[ "${GASSTATION_CHECK_REAL_REPO:-0}" == 1 ]]; then
  for required in docs/AGENTS.md core/database/AGENTS.md benchmark/AGENTS.md; do
    [[ -f "$repo_root/$required" ]] || fail "missing nested contract: $required"
  done
  assert_contract_anchor() {
    local path=$1
    local anchor=$2
    grep -Fq -- "$anchor" "$repo_root/$path" || fail "missing contract anchor in $path: $anchor"
  }
  assert_contract_anchor AGENTS.md 'scripts/agent/preflight.sh'
  assert_contract_anchor AGENTS.md 'scripts/agent/verify.sh auto'
  assert_contract_anchor docs/AGENTS.md 'scripts/agent/verify.sh docs'
  assert_contract_anchor docs/AGENTS.md 'docs/superpowers/'
  assert_contract_anchor core/database/AGENTS.md 'StationSearchResult.hasCachedSnapshot'
  assert_contract_anchor core/database/AGENTS.md 'fallbackToDestructiveMigration'
  assert_contract_anchor benchmark/AGENTS.md 'demoBenchmark'
  assert_contract_anchor benchmark/AGENTS.md 'ANDROID_SERIAL'
  "$repo_root/scripts/agent/check-contracts.sh"
fi

echo "check_contracts_test: PASS"
