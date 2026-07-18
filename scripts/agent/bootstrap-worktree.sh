#!/usr/bin/env bash
set -euo pipefail

repo_root=$(git rev-parse --show-toplevel 2>/dev/null) || {
  echo "bootstrap: not inside a Git repository" >&2
  exit 2
}
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
git_dir=$(cd "$(git rev-parse --git-dir)" && pwd -P)
git_common=$(cd "$(git rev-parse --git-common-dir)" && pwd -P)
if [[ "$git_dir" == "$git_common" ]]; then
  echo "bootstrap: current checkout is the primary worktree" >&2
  exit 3
fi

primary=$(git worktree list --porcelain | awk '/^worktree / { print substr($0, 10); exit }')
source_file="$primary/local.properties"
target_file="$repo_root/local.properties"

if [[ ! -f "$source_file" ]]; then
  echo "bootstrap: primary worktree has no local.properties" >&2
  exit 4
fi
if [[ -e "$target_file" || -L "$target_file" ]]; then
  echo "bootstrap: refusing to overwrite $target_file" >&2
  exit 5
fi

ln -s "$source_file" "$target_file"
echo "bootstrap: linked local.properties for this worktree"
"$script_dir/preflight.sh" --hook
