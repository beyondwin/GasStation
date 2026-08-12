"""Guard the discoverability of committed Phase 1 station-data contracts."""

from __future__ import annotations

import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
OFFLINE = ROOT / "docs" / "offline-strategy.md"


def section(text: str, heading: str) -> str:
    match = re.search(
        rf"(?ms)^## {re.escape(heading)}\s*$\n(?P<body>.*?)(?=^## |\Z)",
        text,
    )
    if match is None:
        raise AssertionError(f"missing section: {heading}")
    return match.group("body")


def require_statement(text: str, anchor: str, terms: tuple[str, ...], label: str) -> None:
    statements = [line.strip() for line in text.splitlines() if anchor in line]
    if len(statements) != 1 or any(term not in statements[0] for term in terms):
        raise AssertionError(f"{label}: expected one complete statement")


def validate_offline_contract(text: str) -> None:
    retry = section(text, "새로고침 실패 시")
    freshness = section(text, "stale 판정")
    operations = section(text, "운영 메모")

    require_statement(
        retry,
        "`StationRetryPolicy`는 `Timeout`",
        (
            "`Timeout`",
            "`Network`",
            "HTTP 408",
            "HTTP 429",
            "HTTP 500–599",
            "500ms",
            "한 번 재시도",
            "다른 HTTP 4xx",
            "`InvalidPayload`",
            "`Unknown`",
            "cancellation",
            "superseded",
            "재시도하지 않습니다",
        ),
        "bounded retry",
    )
    if re.search(
        r"(?:모든 HTTP 4xx|superseded)[^.\n]*(?:횟수 제한 없이|무제한)[^.\n]*재시도",
        text,
        re.IGNORECASE,
    ):
        raise AssertionError("bounded retry: contradictory unbounded category")

    for boundary in ("- age `<= 5분`: `Fresh`", "- age `> 5분`: `Stale`", "`5분 + 1ms`"):
        if freshness.count(boundary) != 1:
            raise AssertionError("freshness boundary: missing or duplicated boundary")
    require_statement(
        freshness,
        "각 atomic snapshot은",
        ("cancellable", "새 마커", "취소", "새로 예약"),
        "freshness boundary",
    )
    require_statement(
        freshness,
        "이 시간 경과 emission은",
        ("Room mutation", "database invalidation 없이"),
        "freshness boundary",
    )
    if re.search(r"age[^.\n]*(?:5분 이하|<=\s*5분)[^.\n]*Stale", text, re.IGNORECASE):
        raise AssertionError("freshness boundary: contradictory Stale boundary")

    require_statement(
        retry,
        "새 generation에 superseded된 성공이나 실패는",
        ("side effect를 남기지 않는 정상 종료",),
        "superseded silence",
    )
    require_statement(
        retry,
        "즉 retry",
        ("failure report", "snapshot/history/prune", "`SearchRefreshed`", "`RetryAttempted`"),
        "superseded silence",
    )

    require_statement(
        operations,
        "각 historical JSON은",
        (
            "v1 `e64634f`",
            "v2 `a705fdb`",
            "v3 `9b070ab`",
            "v4 `014127f`",
            "v5 `da96a5f`",
            "Room 2.6.1/KSP 1.9.23-1.0.20",
            "Room 2.8.4/KSP 2.3.6",
            "Room 2.8.4/KSP 2.3.7",
        ),
        "schema provenance",
    )
    require_statement(
        operations,
        "Task 6 당시 connected device가 없었으므로",
        ("compile/assets 검증만", "device-executed라고 주장하지 않습니다"),
        "connected device",
    )
    if re.search(
        r"(?:instrumented migration|MigrationTestHelper)[^.\n]*connected device[^.\n]*(?:실제 실행 완료|실행했습니다|device-executed)",
        text,
        re.IGNORECASE,
    ):
        raise AssertionError("connected device: contradictory positive execution claim")


class DataCorrectnessContractDocsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.offline = OFFLINE.read_text(encoding="utf-8")

    def assert_offline_contains(self, *terms: str) -> None:
        for term in terms:
            self.assertIn(term, self.offline)

    def test_offline_owner_documents_transport_retry_contract(self) -> None:
        validate_offline_contract(self.offline)
        self.assert_offline_contains(
            "`InvalidPayload`, `Timeout`, `Network`, `Http(statusCode)`, `Unknown`",
            "HTTP 408",
            "HTTP 429",
            "HTTP 500–599",
            "500ms",
            "OkHttp",
            "요청 유종과 같지 않은 proxy 행은 거부",
        )

    def test_offline_owner_documents_atomic_snapshot_and_timer_contract(self) -> None:
        self.assert_offline_contains(
            "하나의 Room transaction",
            "`stationId ASC`",
            "관찰자는 행을 빈 목록으로 정규화",
            "age `<= 5분`",
            "age `> 5분`",
            "5분 + 1ms",
            "Room mutation이나 database invalidation 없이",
        )

    def test_offline_owner_documents_latest_refresh_and_schema_contract(self) -> None:
        self.assert_offline_contains(
            "최신 등록 generation",
            "`fetchedAt`은 guarded write 안에서",
            "side effect를 남기지 않는 정상 종료",
            "tombstone",
            "ABA",
            "versions 1–5",
            "v2→v3",
            "connected device가 없었으므로",
        )

    def test_live_docs_do_not_restore_superseded_transport_or_device_claims(self) -> None:
        catalog = json.loads((ROOT / "docs" / "documentation-catalog.json").read_text(encoding="utf-8"))
        live_docs = [
            ROOT / entry["path"]
            for entry in catalog["documents"]
            if entry["path"].endswith(".md")
        ]
        text = "\n".join(path.read_text(encoding="utf-8") for path in live_docs)
        self.assertIsNone(
            re.search(r"`Timeout`\s*/\s*`Network`[^.\n]*(?:만|only)[^.\n]*(?:재시도|retry)", text),
        )
        self.assertIsNone(
            re.search(r"MigrationTestHelper[^.\n]*(?:device-executed|기기 실행)", text, re.IGNORECASE),
        )

        exact_owner_markers = (
            "age `<= 5분`",
            "age `> 5분`",
            "`5분 + 1ms`",
            "Room mutation이나 database invalidation 없이",
        )
        for marker in exact_owner_markers:
            owners = [path for path in live_docs if marker in path.read_text(encoding="utf-8")]
            self.assertEqual([OFFLINE], owners, f"exact freshness contract owner for {marker}")

    def test_rejects_unbounded_all_4xx_and_superseded_retry_claim(self) -> None:
        mutated = self.offline + "\n실제 정책은 위 설명과 달리 모든 HTTP 4xx와 superseded 작업을 횟수 제한 없이 재시도합니다.\n"
        self.assert_contract_rejected(mutated, "bounded retry")

    def test_rejects_opposite_freshness_boundary(self) -> None:
        mutated = self.offline + "\n실제 판정은 위 표와 반대로 age가 5분 이하일 때 Stale입니다.\n"
        self.assert_contract_rejected(mutated, "freshness boundary")

    def test_rejects_positive_connected_device_execution_claim(self) -> None:
        mutated = self.offline + "\n- instrumented migration tests는 connected device에서 실제 실행 완료되었습니다.\n"
        self.assert_contract_rejected(mutated, "connected device")

    def test_rejects_incomplete_superseded_silence_statement(self) -> None:
        mutated = self.offline.replace(
            "즉 retry, failure report, snapshot/history/prune, `SearchRefreshed`, `RetryAttempted`를 남기지 않습니다.",
            "즉 retry, snapshot/history/prune, `SearchRefreshed`를 남기지 않습니다.",
        )
        self.assert_contract_rejected(mutated, "superseded silence")

    def assert_contract_rejected(self, offline: str, expected: str) -> None:
        with self.assertRaisesRegex(AssertionError, expected):
            validate_offline_contract(offline)


if __name__ == "__main__":
    unittest.main()
