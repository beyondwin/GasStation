"""Guard the discoverability of committed Phase 1 station-data contracts."""

from __future__ import annotations

import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
OFFLINE = ROOT / "docs" / "offline-strategy.md"


class DataCorrectnessContractDocsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.offline = OFFLINE.read_text(encoding="utf-8")

    def assert_offline_contains(self, *terms: str) -> None:
        for term in terms:
            self.assertIn(term, self.offline)

    def test_offline_owner_documents_transport_retry_contract(self) -> None:
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


if __name__ == "__main__":
    unittest.main()
