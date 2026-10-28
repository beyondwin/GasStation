#!/usr/bin/env bash
set -euo pipefail

test_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$test_dir/../../.." && pwd)
source "$test_dir/test_helpers.sh"

docs=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file docs/architecture.md)
assert_contains "$docs" "scopes: docs"
assert_not_contains "$docs" "verifyRoborazziDebug"

for changed_path in scripts/agent/verify.sh scripts/docs/validate.py .codex/hooks.json .claude/settings.json; do
  tooling=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file "$changed_path")
  assert_contains "$tooling" "scopes: tooling"
  assert_contains "$tooling" "command: scripts/agent/test.sh"
  assert_not_contains "$tooling" "command: ./gradlew"
done

tooling_and_data=$($repo_root/scripts/agent/verify.sh auto --dry-run \
  --changed-file scripts/agent/verify.sh \
  --changed-file core/database/src/main/kotlin/Db.kt)
assert_contains "$tooling_and_data" "scopes: tooling data"
assert_contains "$tooling_and_data" "command: scripts/agent/test.sh"
assert_contains "$tooling_and_data" ":core:database:testDebugUnitTest"

ui=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file feature/station-list/src/main/kotlin/Screen.kt)
assert_contains "$ui" "scopes: ui"
assert_contains "$ui" "verifyRoborazziDebug"

data=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file core/database/src/main/kotlin/Db.kt)
assert_contains "$data" "scopes: data"
assert_contains "$data" ":core:database:testDebugUnitTest"
assert_not_contains "$data" "verifyPitestConfiguration"
assert_not_contains "$data" "pitestVerified"

app=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file app/build.gradle.kts)
assert_contains "$app" "scopes: app release"
assert_contains "$app" ":app:assembleProdDebug"
assert_contains "$app" ":app:assembleProdRelease"
assert_not_contains "$app" "verifyPitestConfiguration"
assert_not_contains "$app" "pitestVerified"

release_source=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file app/src/release/kotlin/ReleaseConfig.kt)
assert_contains "$release_source" "scopes: app release"

release_workflow=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file .github/workflows/release.yml)
assert_contains "$release_workflow" "scopes: app release"

publish_workflow=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file .github/workflows/publish-play.yml)
assert_contains "$publish_workflow" "scopes: app release"

ordinary_workflow=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file .github/workflows/android.yml)
assert_contains "$ordinary_workflow" "scopes: app"
assert_not_contains "$ordinary_workflow" "scopes: app release"

release=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file docs/deployment.md)
assert_contains "$release" "scopes: docs release"
assert_contains "$release" ":app:assembleProdRelease"
assert_contains "$release" "coverageXmlReport verifyCoverageReport"
assert_not_contains "$release" "verifyPitestConfiguration"
assert_not_contains "$release" "pitestVerified"
assert_contains "$release" "-Pgasstation.coverageSourceCommit=$(git -C "$repo_root" rev-parse HEAD)"
assert_contains "$release" "-Pgasstation.coverageEvent=local"
coverage_source_commit=$(git -C "$repo_root" rev-parse HEAD)
coverage_base_commit=
for candidate in refs/remotes/origin/main refs/heads/main; do
  if git -C "$repo_root" show-ref --verify --quiet "$candidate"; then
    if coverage_base_commit=$(git -C "$repo_root" merge-base "$candidate" HEAD 2>/dev/null); then
      break
    fi
  fi
done
if [[ -n "$coverage_base_commit" && "$coverage_base_commit" != "$coverage_source_commit" ]]; then
  assert_contains "$release" "-Pgasstation.coverageBaseRef=$coverage_base_commit"
else
  assert_not_contains "$release" "-Pgasstation.coverageBaseRef="
fi
assert_not_contains "$release" "--dry-run"

mutation_policy=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file config/quality/mutation-policy.json)
assert_contains "$mutation_policy" "scopes: tooling data app release"
assert_not_contains "$mutation_policy" "verifyPitestConfiguration"
assert_not_contains "$mutation_policy" "pitestVerified"

for changed_path in scripts/quality/verify_build_inputs.py config/quality/build-inputs.json config/quality/device-evidence-policy.json .github/actions/setup-build-inputs/action.yml; do
  quality=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file "$changed_path")
  assert_contains "$quality" "scopes: tooling data app release"
  assert_contains "$quality" "command: scripts/agent/test.sh"
done

for changed_path in build-logic/convention/build.gradle.kts build-logic/convention/src/test/kotlin/ExampleTest.kt; do
  convention=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file "$changed_path")
  assert_contains "$convention" "scopes: convention app"
  assert_contains "$convention" "command: ./gradlew :build-logic:convention:test --no-configuration-cache --warning-mode fail"
  assert_contains "$convention" ":app:assembleProdDebug"
done

mutation_owner=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file build-logic/convention/src/main/kotlin/quality/mutation/Policy.kt)
assert_contains "$mutation_owner" "scopes: convention data app release"

for changed_path in tools/demo-seed/src/main/kotlin/Main.kt app/src/demo/assets/demo-station-seed.json; do
  seed=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file "$changed_path")
  assert_contains "$seed" "scopes: seed"
  assert_contains "$seed" ":tools:demo-seed:test :tools:demo-seed:verifyDemoSeedAsset"
  assert_not_contains "$seed" "generateDemoSeed"
  if [[ "$changed_path" == app/* ]]; then
    assert_contains "$seed" "scopes: seed app"
    assert_contains "$seed" ":app:testDemoDebugUnitTest"
    assert_contains "$seed" ":app:assembleDemoDebug"
  else
    assert_not_contains "$seed" ":app:assembleDemoDebug"
  fi
done

unknown=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file tools/new-path/file.kt)
assert_contains "$unknown" "scopes: fast"

fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT

make_verify_repo() {
  local target=$1
  make_git_repo "$target"
  git -C "$target" branch -M main
  mkdir -p "$target/scripts/agent" "$target/docs" "$target/core/database"
  cp "$repo_root/scripts/agent/verify.sh" "$target/scripts/agent/verify.sh"
  printf 'initial docs\n' > "$target/docs/architecture.md"
  printf 'initial database\n' > "$target/core/database/Contract.kt"
  git -C "$target" add scripts/agent/verify.sh docs/architecture.md core/database/Contract.kt
  git -C "$target" commit -qm "test: add verifier fixtures"
}

commit_app_change() {
  local target=$1
  git -C "$target" switch -qc agent-setup
  mkdir -p "$target/app"
  printf 'plugins {}\n' > "$target/app/build.gradle.kts"
  git -C "$target" add app/build.gradle.kts
  git -C "$target" commit -qm "test: add committed app change"
}

make_verify_repo "$fixture/local-main"
commit_app_change "$fixture/local-main"
local_main=$("$fixture/local-main/scripts/agent/verify.sh" auto --dry-run)
assert_contains "$local_main" "scopes: app"

make_verify_repo "$fixture/no-base"
git -C "$fixture/no-base" switch -qc agent-setup
git -C "$fixture/no-base" branch -D main >/dev/null
release_without_base=$("$fixture/no-base/scripts/agent/verify.sh" release --dry-run)
assert_contains "$release_without_base" "coverageXmlReport verifyCoverageReport"
assert_contains "$release_without_base" "-Pgasstation.coverageSourceCommit=$(git -C "$fixture/no-base" rev-parse HEAD)"
assert_contains "$release_without_base" "-Pgasstation.coverageEvent=local"
assert_not_contains "$release_without_base" "-Pgasstation.coverageBaseRef="
if no_base_output=$("$fixture/no-base/scripts/agent/verify.sh" auto --dry-run 2>&1); then
  fail "auto accepted a repository without a usable main base"
else
  no_base_status=$?
fi
[[ "$no_base_status" -ne 0 ]] || fail "auto without a usable base returned zero"
assert_contains "$no_base_output" "pass an explicit scope"
assert_not_contains "$no_base_output" "scopes:"

make_verify_repo "$fixture/unrelated-remote"
commit_app_change "$fixture/unrelated-remote"
empty_tree=$(git -C "$fixture/unrelated-remote" mktree </dev/null)
unrelated_commit=$(printf 'unrelated remote main\n' | git -C "$fixture/unrelated-remote" commit-tree "$empty_tree")
git -C "$fixture/unrelated-remote" update-ref refs/remotes/origin/main "$unrelated_commit"
unrelated_remote=$("$fixture/unrelated-remote/scripts/agent/verify.sh" auto --dry-run)
assert_contains "$unrelated_remote" "scopes: app"

make_verify_repo "$fixture/union"
commit_app_change "$fixture/union"
printf 'staged docs\n' > "$fixture/union/docs/architecture.md"
git -C "$fixture/union" add docs/architecture.md
printf 'unstaged database\n' > "$fixture/union/core/database/Contract.kt"
mkdir -p "$fixture/union/feature/station-list"
printf 'untracked UI\n' > "$fixture/union/feature/station-list/Screen.kt"
union=$("$fixture/union/scripts/agent/verify.sh" auto --dry-run)
assert_contains "$union" "scopes: app release data docs ui"
assert_not_contains "$union" "scopes: fast"

if unknown_error=$("$repo_root/scripts/agent/verify.sh" unknown --dry-run 2>&1); then
  fail "unknown scope was accepted"
else
  unknown_status=$?
fi
[[ "$unknown_status" -eq 64 ]] || fail "unknown scope returned $unknown_status instead of 64"
assert_contains "$unknown_error" "unknown scope: unknown"

if missing_value_error=$("$repo_root/scripts/agent/verify.sh" auto --changed-file 2>&1); then
  fail "missing --changed-file value was accepted"
else
  missing_value_status=$?
fi
[[ "$missing_value_status" -eq 64 ]] || fail "missing --changed-file value returned $missing_value_status instead of 64"
assert_contains "$missing_value_error" "missing value for --changed-file"

mkdir -p "$fixture/docs-scope/scripts/agent" "$fixture/docs-scope/scripts/quality/build_inputs"
cp "$repo_root/scripts/agent/verify.sh" "$fixture/docs-scope/scripts/agent/verify.sh"
cat > "$fixture/docs-scope/scripts/agent/check-contracts.sh" <<'EOF'
#!/usr/bin/env bash
printf 'contracts:%s\n' "$*" >> "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/calls.log"
EOF
cat > "$fixture/docs-scope/scripts/quality/build_inputs/docs_gradle_validation_bridge.py" <<'EOF'
#!/usr/bin/env python3
import pathlib
import sys
root = pathlib.Path(__file__).resolve().parents[3]
with (root / "calls.log").open("a") as output:
    output.write("docs:" + " ".join(sys.argv[1:]) + "\n")
EOF
cat > "$fixture/docs-scope/gradlew" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$fixture/docs-scope/scripts/agent/check-contracts.sh" "$fixture/docs-scope/gradlew"
"$fixture/docs-scope/scripts/agent/verify.sh" docs
docs_calls=$(cat "$fixture/docs-scope/calls.log")
assert_contains "$docs_calls" "contracts:"
assert_not_contains "$docs_calls" "docs:--check-gradle-tasks"

cat > "$fixture/docs-scope/scripts/agent/test.sh" <<'EOF'
#!/usr/bin/env bash
printf 'tooling-tests\n' >> "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/calls.log"
exit "${TOOLING_TEST_EXIT:-0}"
EOF
chmod +x "$fixture/docs-scope/scripts/agent/test.sh"
"$fixture/docs-scope/scripts/agent/verify.sh" tooling
tooling_calls=$(cat "$fixture/docs-scope/calls.log")
assert_contains "$tooling_calls" "tooling-tests"
if TOOLING_TEST_EXIT=7 "$fixture/docs-scope/scripts/agent/verify.sh" tooling; then
  fail "tooling test failure was ignored"
else
  tooling_status=$?
fi
[[ "$tooling_status" -eq 7 ]] || fail "tooling scope returned $tooling_status instead of 7"

cat > "$fixture/docs-scope/scripts/agent/preflight.sh" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
cat > "$fixture/docs-scope/gradlew" <<'EOF'
#!/usr/bin/env bash
printf 'gradle:%s\n' "$*" >> "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/calls.log"
exit "${CONVENTION_TEST_EXIT:-0}"
EOF
chmod +x "$fixture/docs-scope/scripts/agent/preflight.sh" "$fixture/docs-scope/gradlew"
"$fixture/docs-scope/scripts/agent/verify.sh" convention
convention_calls=$(cat "$fixture/docs-scope/calls.log")
assert_contains "$convention_calls" "gradle::build-logic:convention:test --no-configuration-cache --warning-mode fail"
if CONVENTION_TEST_EXIT=9 "$fixture/docs-scope/scripts/agent/verify.sh" convention; then
  fail "convention test failure was ignored"
else
  convention_status=$?
fi
[[ "$convention_status" -eq 9 ]] || fail "convention scope returned $convention_status instead of 9"

echo "verify_test: PASS"
