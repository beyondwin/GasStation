#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
"$repo_root/scripts/agent/tests/preflight_test.sh"
"$repo_root/scripts/agent/tests/bootstrap_worktree_test.sh"
"$repo_root/scripts/agent/tests/check_contracts_test.sh"
"$repo_root/scripts/agent/tests/verify_test.sh"
"$repo_root/scripts/agent/tests/verify_room_schemas_test.sh"
python3 "$repo_root/scripts/agent/tests/test_pre_tool_policy.py"
python3 -m unittest discover -s "$repo_root/scripts/docs/tests"
