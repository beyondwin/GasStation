#!/usr/bin/env bash
set -euo pipefail

test_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$test_dir/../../.." && pwd)
source "$test_dir/test_helpers.sh"

fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
make_git_repo "$fixture/repo"

clean_output=$(cd "$fixture/repo" && "$repo_root/scripts/agent/preflight.sh" --hook)
assert_contains "$clean_output" "branch:"
assert_contains "$clean_output" "worktree: primary"
assert_contains "$clean_output" "dirty: clean"
assert_contains "$clean_output" "modules: 1"
assert_contains "$clean_output" "python:"

printf 'user change\n' > "$fixture/repo/user-change.txt"
dirty_output=$(cd "$fixture/repo" && "$repo_root/scripts/agent/preflight.sh" --hook)
assert_contains "$dirty_output" "dirty: changes present"
assert_contains "$dirty_output" "user-change.txt"

git -C "$fixture/repo" checkout -q --detach
detached_output=$(cd "$fixture/repo" && "$repo_root/scripts/agent/preflight.sh" --hook)
assert_contains "$detached_output" "branch: detached"

echo "preflight_test: PASS"
