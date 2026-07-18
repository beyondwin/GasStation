#!/usr/bin/env bash
set -euo pipefail

test_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$test_dir/../../.." && pwd)
source "$test_dir/test_helpers.sh"

fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
make_git_repo "$fixture/main"
printf 'sdk.dir=/safe/test/sdk\n' > "$fixture/main/local.properties"
git -C "$fixture/main" worktree add -qb agent-test "$fixture/linked"

bootstrap_output=$(cd "$fixture/linked" && "$repo_root/scripts/agent/bootstrap-worktree.sh")
linked_cwd=$(cd "$fixture/linked" && pwd -P)
[[ -L "$fixture/linked/local.properties" ]] || fail "local.properties was not linked"
[[ $(cat "$fixture/linked/local.properties") == 'sdk.dir=/safe/test/sdk' ]] || fail "linked content mismatch"
assert_contains "$bootstrap_output" "worktree: linked"
assert_contains "$bootstrap_output" "invocation-cwd: $linked_cwd"
assert_contains "$bootstrap_output" "gradle-wrapper: present version=9.6.1"

rm "$fixture/linked/local.properties"
rm "$fixture/main/local.properties"
if missing_output=$(cd "$fixture/linked" && "$repo_root/scripts/agent/bootstrap-worktree.sh" 2>&1); then
  fail "bootstrap accepted a missing primary local.properties"
fi
assert_contains "$missing_output" "primary worktree has no local.properties"
printf 'sdk.dir=/safe/test/sdk\n' > "$fixture/main/local.properties"

printf 'keep-me\n' > "$fixture/linked/local.properties"
if (cd "$fixture/linked" && "$repo_root/scripts/agent/bootstrap-worktree.sh"); then
  fail "bootstrap overwrote an occupied target"
fi
[[ $(cat "$fixture/linked/local.properties") == 'keep-me' ]] || fail "occupied target changed"

if (cd "$fixture/main" && "$repo_root/scripts/agent/bootstrap-worktree.sh"); then
  fail "bootstrap accepted the primary worktree"
fi

echo "bootstrap_worktree_test: PASS"
