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
assert_contains "$clean_output" "ledger: none detected"

mkdir -p "$fixture/repo/.superpowers/sdd"
cat > "$fixture/repo/.superpowers/sdd/progress.md" <<'EOF'
# Subagent-Driven Development Progress

Task 1: complete (commits base..head, review clean)
private ledger detail that must not be printed
EOF
completed_ledger_output=$(cd "$fixture/repo" && "$repo_root/scripts/agent/preflight.sh" --hook)
assert_contains "$completed_ledger_output" "ledger: .superpowers/sdd/progress.md exists completed=1 pending=no"
assert_not_contains "$completed_ledger_output" "ledger: none detected"
assert_not_contains "$completed_ledger_output" "private ledger detail"

printf '%s\n' '- [ ] Task 2: pending review' >> "$fixture/repo/.superpowers/sdd/progress.md"
pending_ledger_output=$(cd "$fixture/repo" && "$repo_root/scripts/agent/preflight.sh" --hook)
assert_contains "$pending_ledger_output" "ledger: .superpowers/sdd/progress.md exists completed=1 pending=yes"

cat > "$fixture/repo/.superpowers/sdd/progress.md" <<'EOF'
Task 1: complete (commits base..head, review clean)
Task 2: in_progress (private in-progress detail)
EOF
in_progress_ledger_output=$(cd "$fixture/repo" && "$repo_root/scripts/agent/preflight.sh" --hook)
assert_contains "$in_progress_ledger_output" "ledger: .superpowers/sdd/progress.md exists completed=1 pending=yes"
assert_not_contains "$in_progress_ledger_output" "private in-progress detail"

cat > "$fixture/repo/.superpowers/sdd/progress.md" <<'EOF'
Task 1: complete (commits base..head, review clean)
Task 2: blocked (private blocked detail)
EOF
blocked_ledger_output=$(cd "$fixture/repo" && "$repo_root/scripts/agent/preflight.sh" --hook)
assert_contains "$blocked_ledger_output" "ledger: .superpowers/sdd/progress.md exists completed=1 pending=yes"
assert_not_contains "$blocked_ledger_output" "private blocked detail"

printf 'user change\n' > "$fixture/repo/user-change.txt"
dirty_output=$(cd "$fixture/repo" && "$repo_root/scripts/agent/preflight.sh" --hook)
assert_contains "$dirty_output" "dirty: changes present"
assert_contains "$dirty_output" "user-change.txt"

git -C "$fixture/repo" checkout -q --detach
detached_output=$(cd "$fixture/repo" && "$repo_root/scripts/agent/preflight.sh" --hook)
assert_contains "$detached_output" "branch: detached"

echo "preflight_test: PASS"
