"""Guard the deterministic station-list state documentation contract."""

from __future__ import annotations

import copy
import shutil
import tempfile
import unittest
from pathlib import Path

from scripts.docs import validate as validator


ROOT = Path(__file__).resolve().parents[3]
CONTRACT = ROOT / "docs/station-list-state-contract.json"

EXPECTED_CONTRACT = {
    "schemaVersion": 1,
    "contractId": "station-list-state-concurrency-v1",
    "location": {
        "owner": "LocationStateMachine",
        "generations": ["permission", "gps", "locationRequest", "addressRequest"],
        "providerBoundary": "suspend_outside_lock_then_active_check_and_atomic_commit",
        "precisionDowngrade": "clear_coordinates_address_and_recovery",
        "superseded": "normal_silent",
    },
    "observation": {
        "owner": "StationSearchOrchestrator",
        "failureBoundary": "inside_active_query_session",
        "normalCompletion": "observation_failed",
        "cancellation": "rethrow",
        "sameQueryRetry": "resubscribe_without_remote_refresh",
        "queryChange": "clear_old_unkeyed_result_and_failures",
        "cacheEvidence": "hasCachedSnapshot",
    },
    "watch": {
        "owner": "LatestWatchIntentGate",
        "domainResult": "WatchMutationResult",
        "key": "stationId",
        "sharedOperations": ["updateWatchState", "removeWatchedStation"],
        "onConflict": "insert_ignore_preserves_original_watchedAt",
        "ordering": ["watchedAtEpochMillis DESC", "stationId ASC"],
        "superseded": "normal_silent",
    },
    "command": {
        "owner": "StationListCommandQueue",
        "stateField": "StationListUiState.pendingCommands",
        "delivery": "viewmodel_lifetime_fifo",
        "acknowledgement": "exact_head_after_normal_handler_completion_and_active_check",
        "handlerFailure": "retain_head_for_next_start_or_attachment",
        "externalSideEffect": "at_least_once",
        "processDeathPersistence": "not_promised",
    },
    "refresh": {
        "owner": "RefreshCoordinator",
        "work": "single_exact_identity_job",
        "completion": "registered_before_start_and_identity_guarded",
        "query": "revalidate_before_refresh_and_terminal_delivery",
        "address": "caller_scope_nonblocking_after_successful_acquisition",
        "resultDelivery": "inline_suspending_callback",
        "superseded": "normal_silent",
    },
    "projection": {
        "owner": "StationListStateAssembler",
        "input": "StationListStateInputs",
        "purity": ["no_io", "no_coroutines", "no_clock", "no_logging", "no_mutation"],
        "cacheMarker": "hasCachedSnapshot",
        "listIdentityOwner": "projectStationSearchResult",
    },
    "viewModel": {
        "owner": "StationListViewModel",
        "responsibilities": [
            "viewmodel_lifetime_collection",
            "preference_read_write_admission",
            "action_routing",
            "typed_result_translation",
            "assembler_publication",
            "foreground_gps_suspend_bridge",
        ],
        "forbiddenResponsibilities": [
            "location_or_address_generation",
            "refresh_job_or_work_identity",
            "search_session_retry_or_cache_failure_policy",
            "ui_field_projection_or_body_precedence",
            "command_retention_or_acknowledgement_policy",
            "watch_latest_intent_serialization",
            "one_shot_effect_stream",
        ],
    },
    "verification": {
        "primary": "host_coroutine_room_robolectric_and_app_graph",
        "connectedDeviceRequired": False,
        "connectedDeviceEvidence": "not_claimed",
    },
}

EXPECTED_CONSUMERS = {
    "schemaVersion": 1,
    "canonicalOwner": "docs/state-model.md",
    "canonicalAnchor": "station-list-결정적-상태-계약",
    "statementMode": "reference_only",
    "consumers": {
        "README.md": 1,
        "docs/agent-workflow.md": 1,
        "docs/architecture.md": 1,
        "docs/module-contracts.md": 1,
        "docs/onboarding/developer-onboarding-guide.md": 1,
        "docs/project-reading-guide.md": 1,
        "docs/test-strategy.md": 1,
        "docs/verification-matrix.md": 1,
    },
}

SOURCE_PATHS = (
    "feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/LocationStateMachine.kt",
    "feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationSearchOrchestrator.kt",
    "feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/RefreshCoordinator.kt",
    "feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListCommandQueue.kt",
    "feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListStateAssembler.kt",
    "feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListStateInputs.kt",
    "feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt",
    "data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt",
    "core/database/src/main/kotlin/com/gasstation/core/database/station/WatchedStationDao.kt",
)


def changed(payload: dict[str, object], path: tuple[object, ...], value: object) -> dict[str, object]:
    result = copy.deepcopy(payload)
    target: object = result
    for component in path[:-1]:
        target = target[component]  # type: ignore[index]
    target[path[-1]] = value  # type: ignore[index]
    return result


class StateConcurrencyContractDocsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.contract = validator.strict_json_loads(CONTRACT.read_text(encoding="utf-8"))
        self.state_model = (ROOT / "docs/state-model.md").read_text(encoding="utf-8")

    def test_structured_source_and_canonical_block_are_exact(self) -> None:
        expected = self.require_attribute("EXPECTED_STATION_LIST_STATE_CONTRACT")
        validate_contract = self.require_callable("validate_station_list_state_contract")
        parse_block = self.require_callable("parse_station_list_state_contract_block")
        self.assertEqual(EXPECTED_CONTRACT, expected)
        validate_contract(self.contract)
        self.assertEqual(EXPECTED_CONTRACT, self.contract)
        self.assertEqual(self.contract, parse_block(self.state_model))

        source_payload = CONTRACT.read_text(encoding="utf-8").removesuffix("\n")
        start_marker = self.require_attribute("STATION_LIST_STATE_CONTRACT_START")
        end_marker = self.require_attribute("STATION_LIST_STATE_CONTRACT_END")
        self.assertIn(start_marker, self.state_model)
        self.assertIn(end_marker, self.state_model)
        start = self.state_model.index(start_marker)
        start += len(start_marker) + len("\n```json\n")
        end = self.state_model.index("\n```\n" + end_marker, start)
        self.assertEqual(source_payload, self.state_model[start:end])

    def test_structured_semantic_mutations_are_rejected(self) -> None:
        validate_contract = self.require_callable("validate_station_list_state_contract")
        mutations = (
            ("location generation", ("location", "generations"), ["permission", "gps", "addressRequest"]),
            ("observation retry", ("observation", "sameQueryRetry"), "remote_refresh"),
            ("watch order", ("watch", "ordering"), ["stationId ASC", "watchedAtEpochMillis DESC"]),
            ("command ack", ("command", "acknowledgement"), "finally"),
            ("refresh delivery", ("refresh", "resultDelivery"), "shared_flow"),
            ("projection purity", ("projection", "purity"), ["no_io", "no_clock"]),
            ("viewmodel owner", ("viewModel", "owner"), "StateStore"),
            ("device required", ("verification", "connectedDeviceRequired"), True),
            ("device claim", ("verification", "connectedDeviceEvidence"), "passed"),
            ("wrong scalar type", ("schemaVersion",), True),
        )
        for label, path, value in mutations:
            with self.subTest(label=label):
                with self.assertRaisesRegex(AssertionError, "approved contract"):
                    validate_contract(changed(EXPECTED_CONTRACT, path, value))

    def test_missing_extra_and_duplicate_fields_are_rejected(self) -> None:
        validate_contract = self.require_callable("validate_station_list_state_contract")
        missing = copy.deepcopy(EXPECTED_CONTRACT)
        del missing["refresh"]["completion"]
        extra = copy.deepcopy(EXPECTED_CONTRACT)
        extra["command"]["exactlyOnce"] = True
        for candidate in (missing, extra):
            with self.assertRaisesRegex(AssertionError, "approved contract"):
                validate_contract(candidate)

        with self.assertRaisesRegex(ValueError, "duplicate JSON key"):
            validator.strict_json_loads('{"schemaVersion": 1, "schemaVersion": 1}')

    def test_manifest_owner_and_registered_references_are_exact(self) -> None:
        load_consumers = self.require_callable("load_station_list_state_consumers")
        contract_issues = self.require_callable("station_list_state_contract_issues")
        reference_issues = self.require_callable("station_list_state_reference_issues")
        manifest, manifest_issues = load_consumers(ROOT)
        self.assertEqual([], manifest_issues)
        self.assertEqual(EXPECTED_CONSUMERS, manifest)

        entries, catalog_issues = validator.load_catalog(ROOT)
        self.assertEqual([], catalog_issues)
        texts = self.live_texts(entries)
        self.assertEqual([], contract_issues(ROOT, entries, texts))
        self.assertEqual([], reference_issues(ROOT, entries, texts))

    def test_reference_decoys_and_manifest_drift_are_rejected(self) -> None:
        reference_issues = self.require_callable("station_list_state_reference_issues")
        entries, catalog_issues = validator.load_catalog(ROOT)
        self.assertEqual([], catalog_issues)
        original = self.live_texts(entries)
        marker = "<!-- station-list-state-contract-ref -->"
        valid_link = "[\uc0c1\ud0dc \ubaa8\ub378\uc758 \uad6c\uc870\ud654\ub41c station-list \uacc4\uc57d]"
        cases = (
            ("external", valid_link + "(https://example.invalid/state-model.md#station-list-\uacb0정\uc801-\uc0c1태-계약)"),
            ("suffix", valid_link + "(state-model.md.evil#station-list-결정적-상태-계약)"),
            ("fragment", valid_link + "(state-model.md#state-model)"),
            ("prefix", "정책: " + valid_link + "(state-model.md#station-list-결정적-상태-계약)"),
            ("claim label", "[FIFO로 정확히 한 번 처리]" + "(state-model.md#station-list-결정적-상태-계약)"),
        )
        path = "docs/architecture.md"
        marker_lines = [line for line in original[path].splitlines() if marker in line]
        self.assertEqual(1, len(marker_lines), "architecture state-contract marker is missing")
        line = marker_lines[0]
        for label, replacement_link in cases:
            with self.subTest(label=label):
                texts = dict(original)
                texts[path] = texts[path].replace(line, marker + replacement_link, 1)
                self.assertTrue(reference_issues(ROOT, entries, texts))

        missing = dict(original)
        missing[path] = missing[path].replace(line + "\n", "", 1)
        self.assertTrue(reference_issues(ROOT, entries, missing))

        unregistered = dict(original)
        unregistered["docs/security-trade-offs.md"] += "\n" + line + "\n"
        self.assertTrue(reference_issues(ROOT, entries, unregistered))

    def test_source_surface_mutations_are_rejected(self) -> None:
        source_issues = self.require_callable("station_list_state_source_surface_issues")
        self.assertEqual([], source_issues(ROOT))
        mutations = (
            ("assembler call", SOURCE_PATHS[6], "StationListStateAssembler.assemble", "assembleState", "exactly one StationListStateAssembler.assemble call"),
            ("viewmodel shared flow", SOURCE_PATHS[6], "class StationListViewModel", "MutableSharedFlow<Unit>()\nclass StationListViewModel", "forbidden ViewModel ownership token"),
            ("viewmodel address", SOURCE_PATHS[6], "class StationListViewModel", "resolveAddressLabel()\nclass StationListViewModel", "forbidden ViewModel ownership token"),
            ("watch ignore", SOURCE_PATHS[8], "OnConflictStrategy.IGNORE", "OnConflictStrategy.REPLACE", "OnConflictStrategy.IGNORE"),
            ("watch ordering", SOURCE_PATHS[8], "watchedAtEpochMillis DESC, stationId ASC", "watchedAtEpochMillis DESC", "deterministic watched ordering"),
            ("exact head acknowledgement", SOURCE_PATHS[3], "current.firstOrNull()?.id == commandId", "current.any { it.id == commandId }", "exact-head acknowledgement"),
        )
        for label, relative, old, new, expected in mutations:
            with self.subTest(label=label):
                with tempfile.TemporaryDirectory(prefix="state-contract-source-") as directory:
                    root = self.copy_source_surface(Path(directory))
                    target = root / relative
                    text = target.read_text(encoding="utf-8")
                    self.assertIn(old, text)
                    target.write_text(text.replace(old, new, 1), encoding="utf-8")
                    issues = source_issues(root)
                    self.assertTrue(any(expected in issue for issue in issues), issues)

        with tempfile.TemporaryDirectory(prefix="state-contract-source-") as directory:
            root = self.copy_source_surface(Path(directory))
            revived = root / "feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListEffect.kt"
            revived.write_text("sealed interface StationListEffect\n", encoding="utf-8")
            monolith = root / "feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt"
            monolith.parent.mkdir(parents=True, exist_ok=True)
            monolith.write_text("class StationListViewModelTest\n", encoding="utf-8")
            issues = source_issues(root)
            self.assertTrue(any("StationListEffect.kt must remain absent" in issue for issue in issues))
            self.assertTrue(any("StationListViewModelTest.kt must remain absent" in issue for issue in issues))

    def test_source_surface_comment_and_string_decoys_are_rejected(self) -> None:
        source_issues = self.require_callable("station_list_state_source_surface_issues")
        mutations = (
            (
                "ack comment decoy",
                SOURCE_PATHS[3],
                "current.firstOrNull()?.id == commandId",
                "current.any { it.id == commandId } // current.firstOrNull()?.id == commandId",
                "exact-head acknowledgement",
            ),
            (
                "assembler string decoy",
                SOURCE_PATHS[6],
                "StationListStateAssembler.assemble",
                'assembleState /* StationListStateAssembler.assemble */',
                "exactly one StationListStateAssembler.assemble call",
            ),
            (
                "refresh completion comment decoy",
                SOURCE_PATHS[2],
                "job.invokeOnCompletion { finishIfActive(work) }",
                "job.onCompletion { finishIfActive(work) } // invokeOnCompletion",
                "state contract source token missing: invokeOnCompletion",
            ),
            (
                "refresh address string decoy",
                SOURCE_PATHS[2],
                "locationStateMachine.resolveAddressLabel(coordinates)",
                'locationStateMachine.resolveAddress(coordinates)\n'
                '            val decoy = "resolveAddressLabel"',
                "state contract source token missing: resolveAddressLabel",
            ),
            (
                "ignore comment decoy",
                SOURCE_PATHS[8],
                "OnConflictStrategy.IGNORE",
                "OnConflictStrategy.REPLACE // OnConflictStrategy.IGNORE",
                "OnConflictStrategy.IGNORE",
            ),
            (
                "first ordering arbitrary-string decoy",
                SOURCE_PATHS[8],
                'ORDER BY watchedAtEpochMillis DESC, stationId ASC")',
                'ORDER BY watchedAtEpochMillis DESC")\n'
                '    val decoy = "watchedAtEpochMillis DESC, stationId ASC"',
                "deterministic watched ordering",
            ),
            (
                "second ordering block-comment decoy",
                SOURCE_PATHS[8],
                '@Query("SELECT * FROM watched_station ORDER BY watchedAtEpochMillis DESC, stationId ASC")',
                '@Query("SELECT * FROM watched_station ORDER BY watchedAtEpochMillis DESC") '
                '/* @Query("SELECT * FROM watched_station ORDER BY watchedAtEpochMillis DESC, stationId ASC") */',
                "deterministic watched ordering",
            ),
        )
        for label, relative, old, new, expected in mutations:
            with self.subTest(label=label):
                with tempfile.TemporaryDirectory(prefix="state-contract-decoy-") as directory:
                    root = self.copy_source_surface(Path(directory))
                    target = root / relative
                    text = target.read_text(encoding="utf-8")
                    self.assertIn(old, text)
                    target.write_text(text.replace(old, new, 1), encoding="utf-8")
                    issues = source_issues(root)
                    self.assertTrue(any(expected in issue for issue in issues), issues)

    def test_stale_claim_detection_rejects_deleted_models_and_device_overclaim(self) -> None:
        claim_issues = self.require_callable("station_list_state_claim_issues")
        invalid = (
            "Station-list currently exposes StationListEffect.OpenExternalMap.",
            "Station-list commands use a MutableSharedFlow effect stream.",
            "Station-list commands are a one-shot effect.",
            "StationListViewModel owns loading/effect/action dispatch와 최종 UI 조합.",
            "Current test ownership is StationListViewModelTest.",
            "Phase 2 state-concurrency passed on a connected device.",
        )
        for claim in invalid:
            with self.subTest(claim=claim):
                self.assertTrue(claim_issues({"README.md": claim}))

        valid = {
            "docs/test-strategy.md": (
                "StationListCommandEffectTest protects lifecycle handling.\n"
                "Settings emits a failure effect.\n"
                "If a connected target is available, run the separate station-list smoke command.\n"
                "Phase 2 state-concurrency was not executed on a connected device.\n"
            )
        }
        self.assertEqual([], claim_issues(valid))

    def test_real_cataloged_repository_has_no_state_contract_issue(self) -> None:
        self.assertEqual([], validator.validate(ROOT))

    @staticmethod
    def live_texts(entries: list[dict[str, object]]) -> dict[str, str]:
        return {
            entry["path"]: (ROOT / entry["path"]).read_text(encoding="utf-8")
            for entry in entries
            if isinstance(entry.get("path"), str) and str(entry["path"]).endswith(".md")
        }

    @staticmethod
    def copy_source_surface(root: Path) -> Path:
        for relative in SOURCE_PATHS:
            source = ROOT / relative
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
        return root

    def require_attribute(self, name: str) -> object:
        self.assertTrue(hasattr(validator, name), f"state contract validator attribute is missing: {name}")
        return getattr(validator, name)

    def require_callable(self, name: str):
        candidate = self.require_attribute(name)
        self.assertTrue(callable(candidate), f"state contract validator member is not callable: {name}")
        return candidate


if __name__ == "__main__":
    unittest.main()
