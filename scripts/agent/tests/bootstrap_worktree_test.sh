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

(cd "$fixture/linked" && "$repo_root/scripts/agent/bootstrap-worktree.sh")
[[ -L "$fixture/linked/local.properties" ]] || fail "local.properties was not linked"
[[ $(cat "$fixture/linked/local.properties") == 'sdk.dir=/safe/test/sdk' ]] || fail "linked content mismatch"

rm "$fixture/linked/local.properties"
printf 'keep-me\n' > "$fixture/linked/local.properties"
if (cd "$fixture/linked" && "$repo_root/scripts/agent/bootstrap-worktree.sh"); then
  fail "bootstrap overwrote an occupied target"
fi
[[ $(cat "$fixture/linked/local.properties") == 'keep-me' ]] || fail "occupied target changed"

if (cd "$fixture/main" && "$repo_root/scripts/agent/bootstrap-worktree.sh"); then
  fail "bootstrap accepted the primary worktree"
fi

echo "bootstrap_worktree_test: PASS"
