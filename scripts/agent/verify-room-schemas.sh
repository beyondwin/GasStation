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
schema_validator="$repo_root/scripts/agent/validate_room_schemas.py"

if [[ ! -x "$repo_root/gradlew" ]]; then
  printf 'Room schema verification requires executable %s/gradlew\n' "$repo_root" >&2
  exit 1
fi

if [[ ! -f "$schema_validator" ]]; then
  printf 'Room schema verification requires %s\n' "$schema_validator" >&2
  exit 1
fi

python3 "$schema_validator" --schema-root "$schema_root"

temporary_parent=$(mktemp -d)
cleanup() {
  rm -rf -- "$temporary_parent"
}
trap cleanup EXIT
generated_schema_root="$temporary_parent/schemas"
generated_database_schema="$generated_schema_root/com.gasstation.core.database.GasStationDatabase"
mkdir -p "$generated_schema_root"

(
  cd "$repo_root"
  gradle_launcher=(./gradlew)
  if [[ ${GASSTATION_BUILD_INPUT_EVIDENCE:-} == sealed-v1 ]]; then
    gradle_launcher=(scripts/quality/build_inputs/run_gradle.sh)
  fi
  "${gradle_launcher[@]}" \
    :core:database:kspDebugKotlin \
    "-Pgasstation.roomSchemaOutput=$generated_schema_root" \
    --rerun-tasks \
    --no-build-cache \
    --warning-mode fail
)

if [[ ! -f "$generated_database_schema/5.json" ]]; then
  printf 'Room schema generation did not produce current schema at %s\n' \
    "$generated_database_schema/5.json" >&2
  exit 1
fi

python3 - "$generated_database_schema/5.json" <<'PY'
import json
from pathlib import Path
import sys

path = Path(sys.argv[1])
try:
    schema = json.loads(path.read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError) as error:
    print(f"generated Room schema is invalid at {path}: {error}", file=sys.stderr)
    raise SystemExit(1)
if schema.get("database", {}).get("version") != 5:
    print(f"generated Room schema at {path} is not version 5", file=sys.stderr)
    raise SystemExit(1)
PY

if ! cmp -s "$generated_database_schema/5.json" "$database_schema/5.json"; then
  printf 'Generated Room schema does not match checked-in current schema: %s\n' \
    "$database_schema/5.json" >&2
  exit 1
fi

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
