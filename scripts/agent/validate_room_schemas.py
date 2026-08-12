#!/usr/bin/env python3
"""Validate the checked-in Room schema history without third-party packages."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


DATABASE_CLASS = "com.gasstation.core.database.GasStationDatabase"
EXPECTED_VERSIONS = tuple(range(1, 6))
SETUP_IDENTITY_PATTERN = re.compile(
    r"INSERT\s+OR\s+REPLACE\s+INTO\s+room_master_table\s*"
    r"\(id,identity_hash\)\s+VALUES\s*\(42,\s*'([^']+)'\s*\)",
    re.IGNORECASE,
)


def fail(message: str) -> None:
    raise ValueError(message)


def validate_schema(path: Path, expected_version: int) -> None:
    try:
        schema = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"invalid Room schema JSON at {path}: {error}")

    if not isinstance(schema, dict) or schema.get("formatVersion") != 1:
        fail(f"Room schema {path} has an invalid formatVersion")
    database = schema.get("database")
    if not isinstance(database, dict):
        fail(f"Room schema {path} has no database object")
    internal_version = database.get("version")
    if internal_version != expected_version:
        fail(
            f"Room schema filename version {expected_version} does not match "
            f"internal version {internal_version!r} at {path}"
        )

    identity = database.get("identityHash")
    if not isinstance(identity, str) or not identity.strip():
        fail(f"Room schema {path} must have a non-empty identityHash")

    entities = database.get("entities")
    if not isinstance(entities, list) or not entities:
        fail(f"Room schema {path} must have a non-empty entities list")
    for entity in entities:
        if not isinstance(entity, dict):
            fail(f"Room schema {path} contains an invalid entity")
        if not isinstance(entity.get("tableName"), str) or not entity["tableName"]:
            fail(f"Room schema {path} contains an entity without tableName")
        if not isinstance(entity.get("createSql"), str) or not entity["createSql"]:
            fail(f"Room schema {path} contains an entity without createSql")
        fields = entity.get("fields")
        if not isinstance(fields, list) or not fields:
            fail(f"Room schema {path} contains an entity without fields")
        primary_key = entity.get("primaryKey")
        if not isinstance(primary_key, dict) or not isinstance(
            primary_key.get("columnNames"), list
        ):
            fail(f"Room schema {path} contains an entity without primaryKey")

    setup_queries = database.get("setupQueries")
    if not isinstance(setup_queries, list) or not setup_queries:
        fail(f"Room schema {path} must have non-empty setupQueries")
    setup_identities = [
        match.group(1)
        for query in setup_queries
        if isinstance(query, str)
        for match in [SETUP_IDENTITY_PATTERN.search(query)]
        if match is not None
    ]
    if setup_identities != [identity]:
        fail(
            f"Room schema {path} room-master setup identity does not match "
            "identityHash"
        )


def validate_schema_root(schema_root: Path) -> None:
    database_schema = schema_root / DATABASE_CLASS
    expected_names = {f"{version}.json" for version in EXPECTED_VERSIONS}
    try:
        actual_paths = list(database_schema.iterdir())
    except OSError as error:
        fail(f"Room schema directory is unavailable at {database_schema}: {error}")
    actual_names = {path.name for path in actual_paths}
    if actual_names != expected_names:
        unexpected = sorted(actual_names - expected_names)
        missing = sorted(expected_names - actual_names)
        fail(
            "unexpected canonical schema artifacts: "
            f"missing={missing or 'none'}, unexpected={unexpected or 'none'}"
        )
    for version in EXPECTED_VERSIONS:
        validate_schema(database_schema / f"{version}.json", version)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--schema-root", required=True, type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        validate_schema_root(args.schema_root)
    except ValueError as error:
        print(f"room-schema-validation: {error}", file=sys.stderr)
        return 1
    print("room-schema-validation: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
