#!/usr/bin/env bash
set -euo pipefail

test_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$test_dir/../../.." && pwd)
source "$test_dir/test_helpers.sh"

docs=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file docs/architecture.md)
assert_contains "$docs" "scopes: docs"
assert_not_contains "$docs" "verifyRoborazziDebug"

ui=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file feature/station-list/src/main/kotlin/Screen.kt)
assert_contains "$ui" "scopes: ui"
assert_contains "$ui" "verifyRoborazziDebug"

data=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file core/database/src/main/kotlin/Db.kt)
assert_contains "$data" "scopes: data"
assert_contains "$data" ":core:database:testDebugUnitTest"

app=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file app/build.gradle.kts)
assert_contains "$app" "scopes: app"
assert_contains "$app" ":app:assembleProdDebug"

release=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file docs/deployment.md)
assert_contains "$release" "scopes: docs release"
assert_contains "$release" ":app:assembleProdRelease"

unknown=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file tools/new-path/file.kt)
assert_contains "$unknown" "scopes: fast"

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

echo "verify_test: PASS"
