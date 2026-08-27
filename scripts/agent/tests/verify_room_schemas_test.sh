#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

assert_contains() {
  case "$1" in
    *"$2"*) ;;
    *) fail "expected output to contain: $2" ;;
  esac
}

write_schema() {
  local target=$1
  local version=$2
  local identity="identity-$version"
  cat > "$target/$version.json" <<EOF
{"formatVersion":1,"database":{"version":$version,"identityHash":"$identity","entities":[{"tableName":"station_$version","createSql":"CREATE TABLE station_$version (id INTEGER NOT NULL, PRIMARY KEY(id))","fields":[{"fieldPath":"id","columnName":"id","affinity":"INTEGER","notNull":true}],"primaryKey":{"autoGenerate":false,"columnNames":["id"]},"indices":[]}],"setupQueries":["CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)","INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$identity')"]}}
EOF
}

assert_rejected() {
  local target=$1
  local expected=$2
  local label=$3
  local output
  if output=$("$target/scripts/agent/verify-room-schemas.sh" --root "$target" 2>&1); then
    printf 'REPRODUCED: %s was accepted\n' "$label" >&2
    rejected_failures=$((rejected_failures + 1))
    return
  fi
  assert_contains "$output" "$expected"
}

setup_fixture() {
  local target=$1
  mkdir -p \
    "$target/scripts/agent" \
    "$target/core/database/schemas/com.gasstation.core.database.GasStationDatabase"
  cp "$repo_root/scripts/agent/verify-room-schemas.sh" "$target/scripts/agent/verify-room-schemas.sh"
  cp "$repo_root/scripts/agent/validate_room_schemas.py" "$target/scripts/agent/validate_room_schemas.py"
  chmod +x "$target/scripts/agent/verify-room-schemas.sh"
  for version in 1 2 3 4 5; do
    write_schema \
      "$target/core/database/schemas/com.gasstation.core.database.GasStationDatabase" \
      "$version"
  done
  cat > "$target/gradlew" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" > gradle-invocation.txt
schema_output=
for argument in "$@"; do
  case "$argument" in
    -Pgasstation.roomSchemaOutput=*) schema_output=${argument#*=} ;;
  esac
done
[[ -n "$schema_output" ]] || exit 0
database_schema="$schema_output/com.gasstation.core.database.GasStationDatabase"
mkdir -p "$database_schema"
cp core/database/schemas/com.gasstation.core.database.GasStationDatabase/5.json \
  "$database_schema/5.json"
EOF
  chmod +x "$target/gradlew"
  git -C "$target" init -q
  git -C "$target" config user.email agent-test@example.invalid
  git -C "$target" config user.name 'Agent Test'
  git -C "$target" add .
  git -C "$target" commit -qm 'test: add schema fixture'
}

setup_fixture "$fixture/clean"
"$fixture/clean/scripts/agent/verify-room-schemas.sh" --root "$fixture/clean"
GASSTATION_BUILD_INPUT_EVIDENCE=sealed-v1 \
  "$fixture/clean/scripts/agent/verify-room-schemas.sh" --root "$fixture/clean"
assert_contains "$(cat "$fixture/clean/gradle-invocation.txt")" ":core:database:kspDebugKotlin"
assert_contains "$(cat "$fixture/clean/gradle-invocation.txt")" "--rerun-tasks"
assert_contains "$(cat "$fixture/clean/gradle-invocation.txt")" "--no-build-cache"

rejected_failures=0

setup_fixture "$fixture/no-op"
printf '#!/usr/bin/env bash\nexit 0\n' > "$fixture/no-op/gradlew"
chmod +x "$fixture/no-op/gradlew"
assert_rejected "$fixture/no-op" "did not produce current schema" \
  "successful Gradle no-op with stale v5"

setup_fixture "$fixture/tracked-extra"
write_schema \
  "$fixture/tracked-extra/core/database/schemas/com.gasstation.core.database.GasStationDatabase" \
  6
git -C "$fixture/tracked-extra" add core/database/schemas
git -C "$fixture/tracked-extra" commit -qm 'test: commit unexpected schema'
assert_rejected "$fixture/tracked-extra" "unexpected canonical schema artifacts" \
  "committed unexpected 6.json"

setup_fixture "$fixture/invalid-json"
printf '{not-json\n' > \
  "$fixture/invalid-json/core/database/schemas/com.gasstation.core.database.GasStationDatabase/3.json"
git -C "$fixture/invalid-json" commit -qam 'test: commit invalid schema JSON'
assert_rejected "$fixture/invalid-json" "invalid Room schema JSON" \
  "committed invalid JSON"

setup_fixture "$fixture/version-mismatch"
python3 - "$fixture/version-mismatch/core/database/schemas/com.gasstation.core.database.GasStationDatabase/3.json" <<'PY'
import json
from pathlib import Path
import sys

path = Path(sys.argv[1])
schema = json.loads(path.read_text())
schema["database"]["version"] = 99
path.write_text(json.dumps(schema))
PY
git -C "$fixture/version-mismatch" commit -qam 'test: commit mismatched schema version'
assert_rejected "$fixture/version-mismatch" "does not match internal version" \
  "committed filename/internal-version mismatch"

setup_fixture "$fixture/empty-identity"
python3 - "$fixture/empty-identity/core/database/schemas/com.gasstation.core.database.GasStationDatabase/4.json" <<'PY'
import json
from pathlib import Path
import sys

path = Path(sys.argv[1])
schema = json.loads(path.read_text())
schema["database"]["identityHash"] = ""
schema["database"]["setupQueries"][-1] = (
    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '')"
)
path.write_text(json.dumps(schema))
PY
git -C "$fixture/empty-identity" commit -qam 'test: commit empty schema identity'
assert_rejected "$fixture/empty-identity" "non-empty identityHash" \
  "committed empty identity"

setup_fixture "$fixture/setup-mismatch"
python3 - "$fixture/setup-mismatch/core/database/schemas/com.gasstation.core.database.GasStationDatabase/2.json" <<'PY'
import json
from pathlib import Path
import sys

path = Path(sys.argv[1])
schema = json.loads(path.read_text())
schema["database"]["setupQueries"][-1] = (
    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'different-identity')"
)
path.write_text(json.dumps(schema))
PY
git -C "$fixture/setup-mismatch" commit -qam 'test: commit mismatched setup identity'
assert_rejected "$fixture/setup-mismatch" "room-master setup identity" \
  "committed setup identity mismatch"

setup_fixture "$fixture/modified"
printf '\n' >> \
  "$fixture/modified/core/database/schemas/com.gasstation.core.database.GasStationDatabase/5.json"
if output=$("$fixture/modified/scripts/agent/verify-room-schemas.sh" --root "$fixture/modified" 2>&1); then
  fail 'modified tracked schema was accepted'
fi
assert_contains "$output" "M core/database/schemas/com.gasstation.core.database.GasStationDatabase/5.json"

setup_fixture "$fixture/untracked"
printf '{"database":{"version":6}}\n' > \
  "$fixture/untracked/core/database/schemas/com.gasstation.core.database.GasStationDatabase/6.json"
if output=$("$fixture/untracked/scripts/agent/verify-room-schemas.sh" --root "$fixture/untracked" 2>&1); then
  fail 'untracked next-version schema was accepted'
fi
assert_contains "$output" "unexpected canonical schema artifacts"

setup_fixture "$fixture/deleted"
rm "$fixture/deleted/core/database/schemas/com.gasstation.core.database.GasStationDatabase/4.json"
if output=$("$fixture/deleted/scripts/agent/verify-room-schemas.sh" --root "$fixture/deleted" 2>&1); then
  fail 'deleted historical schema was accepted'
fi
assert_contains "$output" "unexpected canonical schema artifacts"

setup_fixture "$fixture/missing"
rm -rf "$fixture/missing/core/database/schemas"
if output=$("$fixture/missing/scripts/agent/verify-room-schemas.sh" --root "$fixture/missing" 2>&1); then
  fail 'missing schema directory was accepted'
fi
assert_contains "$output" "Room schema directory is unavailable"

setup_fixture "$fixture/gradle-failure"
printf '#!/usr/bin/env bash\nexit 7\n' > "$fixture/gradle-failure/gradlew"
chmod +x "$fixture/gradle-failure/gradlew"
if "$fixture/gradle-failure/scripts/agent/verify-room-schemas.sh" \
  --root "$fixture/gradle-failure" >/dev/null 2>&1; then
  fail 'Gradle schema generation failure was accepted'
fi

if ((rejected_failures > 0)); then
  fail "$rejected_failures invalid Room schema fixture(s) were accepted"
fi

printf 'verify-room-schemas tests: PASS\n'
