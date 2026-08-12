"""Guard the structured Phase 1 station-data documentation contract."""

from __future__ import annotations

import copy
import json
import unittest
from pathlib import Path

from scripts.docs.validate import (
    EXPECTED_STATION_DATA_POLICY,
    STATION_DATA_POLICY_END,
    STATION_DATA_POLICY_OWNER,
    STATION_DATA_POLICY_PATH,
    STATION_DATA_POLICY_START,
    load_catalog,
    parse_station_data_policy_block,
    station_data_policy_issues,
    strict_json_loads,
    validate_station_data_policy,
)


ROOT = Path(__file__).resolve().parents[3]
OFFLINE = ROOT / STATION_DATA_POLICY_OWNER
POLICY = ROOT / STATION_DATA_POLICY_PATH


def replace_policy_block(text: str, policy: object) -> str:
    start = text.index(STATION_DATA_POLICY_START) + len(STATION_DATA_POLICY_START)
    end = text.index(STATION_DATA_POLICY_END, start)
    rendered = "\n```json\n" + json.dumps(policy, indent=2) + "\n```\n"
    return text[:start] + rendered + text[end:]


def changed(policy: dict[str, object], path: tuple[object, ...], value: object) -> dict[str, object]:
    result = copy.deepcopy(policy)
    target: object = result
    for component in path[:-1]:
        target = target[component]  # type: ignore[index]
    target[path[-1]] = value  # type: ignore[index]
    return result


class DataCorrectnessContractDocsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.offline = OFFLINE.read_text(encoding="utf-8")
        self.policy = strict_json_loads(POLICY.read_text(encoding="utf-8"))
        if not isinstance(self.policy, dict):
            raise AssertionError("station data policy fixture must be an object")

    def test_structured_source_and_designated_block_are_exact(self) -> None:
        validate_station_data_policy(self.policy)
        self.assertEqual(EXPECTED_STATION_DATA_POLICY, self.policy)
        self.assertEqual(self.policy, parse_station_data_policy_block(self.offline))
        self.assertFalse(self.policy["retry"]["unlistedHttpRetryable"])

    def test_structured_field_mutations_are_rejected(self) -> None:
        mutations = (
            ("HTTP 404 retry", ("retry", "retryableHttpStatuses"), [404, 408, 429]),
            ("extra retry", ("retry", "maxRetries"), 2),
            ("different delay", ("retry", "delayMs"), 0),
            ("Fresh inversion", ("freshness", "fresh", "operator"), ">"),
            ("wrong first stale", ("freshness", "firstStaleAgeMs"), 300000),
            ("Room mutation", ("freshness", "timeCrossingWithoutRoomMutation"), False),
            ("device execution", ("schema", "migrationEvidence", "connectedDeviceExecuted"), True),
            ("device available", ("schema", "migrationEvidence", "connectedDeviceAvailable"), True),
            ("incomplete silence", ("superseded", "forbiddenSideEffects"), ["Retry"]),
            ("duplicate status", ("retry", "retryableHttpStatuses"), [408, 408, 429]),
            ("unlisted HTTP retry", ("retry", "unlistedHttpRetryable"), True),
            (
                "overlapping range",
                ("retry", "nonRetryableHttpRanges"),
                [{"minInclusive": 400, "maxInclusive": 499}],
            ),
            ("wrong scalar type", ("retry", "maxRetries"), True),
        )
        for label, path, value in mutations:
            with self.subTest(label=label):
                with self.assertRaisesRegex(AssertionError, "approved contract"):
                    validate_station_data_policy(changed(self.policy, path, value))

    def test_missing_and_extra_fields_are_rejected(self) -> None:
        missing = copy.deepcopy(self.policy)
        del missing["schema"]["migrationEvidence"]["status"]
        extra = copy.deepcopy(self.policy)
        extra["retry"]["allowAll4xx"] = True
        for label, candidate in (("missing", missing), ("extra", extra)):
            with self.subTest(label=label):
                with self.assertRaisesRegex(AssertionError, "approved contract"):
                    validate_station_data_policy(candidate)

    def test_http_600_falls_under_the_nonretry_default(self) -> None:
        retry = self.policy["retry"]
        self.assertNotIn(600, retry["retryableHttpStatuses"])
        self.assertFalse(
            any(
                item["minInclusive"] <= 600 <= item["maxInclusive"]
                for item in retry["retryableHttpRanges"]
            )
        )
        self.assertFalse(retry["unlistedHttpRetryable"])

    def test_mutated_declarative_block_rows_are_rejected(self) -> None:
        mutations = (
            changed(self.policy, ("retry", "retryableHttpStatuses"), [404, 408, 429]),
            changed(self.policy, ("freshness", "fresh", "operator"), ">"),
            changed(self.policy, ("schema", "migrationEvidence", "connectedDeviceExecuted"), True),
            changed(self.policy, ("superseded", "forbiddenSideEffects"), ["Retry"]),
        )
        for candidate in mutations:
            with self.subTest(candidate=candidate):
                parsed = parse_station_data_policy_block(replace_policy_block(self.offline, candidate))
                with self.assertRaisesRegex(AssertionError, "approved contract"):
                    validate_station_data_policy(parsed)

    def test_duplicate_json_keys_are_rejected_in_source_and_block(self) -> None:
        duplicate = '{"schemaVersion": 1, "schemaVersion": 1}'
        with self.assertRaisesRegex(ValueError, "duplicate JSON key"):
            strict_json_loads(duplicate)

        duplicate_block = (
            self.offline[: self.offline.index(STATION_DATA_POLICY_START)]
            + STATION_DATA_POLICY_START
            + "\n```json\n"
            + duplicate
            + "\n```\n"
            + STATION_DATA_POLICY_END
        )
        with self.assertRaisesRegex(AssertionError, "duplicate JSON key"):
            parse_station_data_policy_block(duplicate_block)

    def test_owner_and_catalog_source_are_unique(self) -> None:
        entries, catalog_issues = load_catalog(ROOT)
        self.assertEqual([], catalog_issues)
        texts = {
            entry["path"]: (ROOT / entry["path"]).read_text(encoding="utf-8")
            for entry in entries
            if isinstance(entry.get("path"), str) and entry["path"].endswith(".md")
        }
        self.assertEqual([], station_data_policy_issues(ROOT, entries, texts))

        duplicate_texts = dict(texts)
        duplicate_texts["docs/architecture.md"] += (
            "\n" + STATION_DATA_POLICY_START + "\n" + STATION_DATA_POLICY_END + "\n"
        )
        duplicate_issues = station_data_policy_issues(ROOT, entries, duplicate_texts)
        self.assertTrue(any("block owner must be exactly" in issue for issue in duplicate_issues))

        duplicate_entries = copy.deepcopy(entries)
        architecture = next(entry for entry in duplicate_entries if entry["path"] == "docs/architecture.md")
        architecture["authoritativeSources"].append(STATION_DATA_POLICY_PATH)
        catalog_owner_issues = station_data_policy_issues(ROOT, duplicate_entries, texts)
        self.assertTrue(any("catalog owner must be exactly" in issue for issue in catalog_owner_issues))

        duplicate_in_owner = copy.deepcopy(entries)
        offline = next(entry for entry in duplicate_in_owner if entry["path"] == STATION_DATA_POLICY_OWNER)
        offline["authoritativeSources"].append(STATION_DATA_POLICY_PATH)
        duplicate_source_issues = station_data_policy_issues(ROOT, duplicate_in_owner, texts)
        self.assertTrue(any("catalog owner must be exactly" in issue for issue in duplicate_source_issues))

    def test_surrounding_prose_keeps_non_structured_semantics_discoverable(self) -> None:
        for term in (
            "하나의 Room transaction",
            "`stationId ASC`",
            "관찰자는 행을 빈 목록으로 정규화",
            "요청 유종과 같지 않은 proxy 행은 거부",
            "`fetchedAt`은 guarded write 안에서",
            "tombstone",
            "ABA",
            "CrashReporter.recordNonFatalSafely",
        ):
            self.assertIn(term, self.offline)


if __name__ == "__main__":
    unittest.main()
