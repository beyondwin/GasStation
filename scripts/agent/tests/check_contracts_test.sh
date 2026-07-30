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
  release-publish:
    if: ${{ startsWith(github.ref, 'refs/tags/v') }}
    needs: [agent-contracts, static-analysis, unit-tests, screenshot-tests, assemble, release-assemble, coverage]
    permissions:
      contents: write
    steps:
      - uses: actions/download-artifact@v8
      - name: Publish release
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          notes_file=$(find docs/release-notes/ -name "*-${GITHUB_REF_NAME}.md")
          gh release create "$GITHUB_REF_NAME" --notes-file "$notes_file" release-assets/*.apk
EOF
git -C "$fixture/repo" add .
git -C "$fixture/repo" commit -qm "test: add contract fixture"
ci_base=$(git -C "$fixture/repo" rev-parse HEAD^)

"$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo"
GASSTATION_CI_BASE_REF="$ci_base" "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci

printf 'bad whitespace   \n' >> "$fixture/repo/README.md"
if "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" > "$fixture/unstaged-diff.out" 2>&1; then
  fail "unstaged whitespace was accepted"
fi
assert_contains "$(cat "$fixture/unstaged-diff.out")" "README.md:4: trailing whitespace"
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
  release-publish:
    if: ${{ startsWith(github.ref, 'refs/tags/v') }}
    needs: [agent-contracts, static-analysis, unit-tests, screenshot-tests, assemble, release-assemble, coverage]
    permissions:
      contents: write
    steps:
      - uses: actions/download-artifact@v8
      - name: Publish release
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          notes_file=$(find docs/release-notes/ -name "*-${GITHUB_REF_NAME}.md")
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
