#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
if [[ ${1:-} == "--root" ]]; then
  if [[ -z ${2:-} || -n ${3:-} ]]; then
    printf 'usage: %s [--root REPOSITORY]\n' "$0" >&2
    exit 2
  fi
  repo_root=$(cd "$2" && pwd -P)
elif [[ $# -ne 0 ]]; then
  printf 'usage: %s [--root REPOSITORY]\n' "$0" >&2
  exit 2
fi

schema_root="$repo_root/core/database/schemas"
database_schema="$schema_root/com.gasstation.core.database.GasStationDatabase"

if [[ ! -x "$repo_root/gradlew" ]]; then
  printf 'Room schema verification requires executable %s/gradlew\n' "$repo_root" >&2
  exit 1
fi

(
  cd "$repo_root"
  ./gradlew \
    :core:database:kspDebugKotlin \
    --rerun-tasks \
    --no-build-cache \
    --warning-mode fail
)

for version in 1 2 3 4 5; do
  if [[ ! -f "$database_schema/$version.json" ]]; then
    printf 'Room schema history is missing canonical version %s at %s\n' \
      "$version" "$database_schema/$version.json" >&2
    exit 1
  fi
done

schema_status=$(git -C "$repo_root" status \
  --porcelain \
  --untracked-files=all \
  -- core/database/schemas)
if [[ -n "$schema_status" ]]; then
  printf 'Room schema regeneration changed checked-in evidence:\n%s\n' \
    "$schema_status" >&2
  exit 1
fi

printf 'room-schema-verification: PASS\n'
