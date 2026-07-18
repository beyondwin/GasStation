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
mkdir -p "$fixture/repo/app" "$fixture/repo/docs" "$fixture/repo/.codex" "$fixture/repo/.github/workflows"
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
printf 'Java 21+, Android SDK 37, Python 3.9+.\n' > "$fixture/repo/CONTRIBUTING.md"
printf '[features]\nhooks = true\n' > "$fixture/repo/.codex/config.toml"
cat > "$fixture/repo/.github/workflows/android.yml" <<'EOF'
name: Android CI
jobs:
  agent-contracts:
    runs-on: ubuntu-latest
  static-analysis:
    runs-on: ubuntu-latest
EOF
git -C "$fixture/repo" add .
git -C "$fixture/repo" commit -qm "test: add contract fixture"

"$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo"

cat > "$fixture/repo/.github/workflows/android.yml" <<'EOF'
name: Android CI
jobs:
  static-analysis:
    runs-on: ubuntu-latest
EOF
if "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" --ci > "$fixture/workflow.out" 2>&1; then
  fail "workflow without agent-contracts job was accepted"
fi
assert_contains "$(cat "$fixture/workflow.out")" ".github/workflows/android.yml:1: agent-contracts job missing"
assert_error_locations "$(cat "$fixture/workflow.out")"
cat > "$fixture/repo/.github/workflows/android.yml" <<'EOF'
name: Android CI
jobs:
  agent-contracts:
    runs-on: ubuntu-latest
  static-analysis:
    runs-on: ubuntu-latest
EOF

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
