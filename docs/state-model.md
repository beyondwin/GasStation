# 상태 모델

상태가 어디서 생기고 얼마나 사는지 설명한다.

## 층

| 층 | 대표 타입 | 유지 | 역할 |
| --- | --- | --- | --- |
| 영속 선호 | `UserPreferences` | 재시작 후에도 | 반경, 유종, 브랜드, 정렬, 지도 앱 |
| 목록 위치 | `LocationStateMachine` | ViewModel 동안 | 권한, GPS, 좌표, 주소 |
| navigation 좌표 | `GasStationNavHost` | navigation graph 동안 | 관심 탭, 거리 기준 |
| 목록 검색 | `StationSearchOrchestrator` | ViewModel 동안 | query, 캐시, 관찰 결과, 전면 실패 |
| 목록 refresh | `RefreshCoordinator` | ViewModel 동안 | 한 번에 하나의 refresh |
| 목록 UI 조합 | `StationListStateAssembler` | 조합 결과는 ViewModel 동안 | 최종 `StationListUiState` |
| 목록 연결 | `StationListViewModel` | ViewModel 동안 | 수집, action, 게시 |
| 저장소 읽기 | `StationSearchResult`, `WatchedStationSummary` | 다시 계산 가능 | 화면에 그릴 데이터 |
| 설정 화면 | `SettingsUiState` | 선호값에서 재생성 | 라벨과 옵션 |
| UI command | `StationListCommandQueue` | ViewModel 동안 | snackbar, 위치 설정, 외부 지도 FIFO |
| 이벤트 | `StationEvent` | 저장하지 않음 | 관찰/진단 |
| 비치명 보고 | `CrashReporter` | 저장하지 않음 | nonfatal 예외 |

## 영속 선호

원천은 `UserPreferences` 하나다. 저장은 `core:datastore`, 매핑은 `data:settings`. Nearby와 Settings는 DataStore 첫 값이 오기 전에 `UserPreferences.default()`를 쓰지 않는다. default는 새 저장소 fallback과 demo 초기화에만 쓴다.

포함하는 값: `searchRadius`, `fuelType`, `brandFilter`, `sortOrder`, `mapProvider`.

`demo`는 시작 때 default로 덮어 같은 검토 상태를 만든다. Kakao 이름은 `KAKAO_MAP`이다. 옛 `KAKAO_NAVI`는 읽을 때 복원한다.

## 목록 런타임

한 reducer가 아니다.

- `LocationStateMachine` — 권한, GPS, 좌표, 주소, 네 generation
- `StationSearchOrchestrator` — query, 캐시, 관찰, 전면 실패
- `RefreshCoordinator` — 위치 획득과 단일 refresh job
- `StationListCommandQueue` — FIFO와 exact-head 승인
- assembler — 한 시점 입력을 UI로 투영. I/O 없음
- ViewModel — 연결만. generation, refresh identity, retry, 투영, FIFO 정책을 다시 구현하지 않는다

이 값은 저장되지 않는다. 화면을 떠나면 사라진다.

권한 dialog는 CTA에서만 연다. 거부는 demo 좌표나 캐시보다 먼저다. 정밀 권한이 대략으로 낮아지면 좌표와 주소를 지운다. GPS 꺼짐은 권한 거부와 다른 안내다.

저장소 예외는 현재 관찰 session 안에서 failure가 된다. 같은 query retry는 관찰만 다시 구독하고 remote refresh를 시작하지 않는다. 캐시 의미는 `hasCachedSnapshot`만 쓴다.

Nearby는 권한, GPS, 좌표, 설정이 준비된 뒤에만 `StationQuery`를 만든다. 좌표가 있는 상태에서 반경·유종·브랜드·정렬이 바뀌면 refresh를 다시 요청한다. 브랜드·정렬은 캐시 키가 아니지만 읽기 모델에는 들어간다.

`StationSearchResult`:

| 필드 | 의미 |
| --- | --- |
| `stations` | 목록에 그릴 항목 |
| `freshness` | `Fresh` 또는 `Stale` |
| `fetchedAt` | 마지막 성공 시각 |
| `hasCachedSnapshot` | 이 버킷에 스냅샷 마커가 있는지 |

빈 성공과 캐시 없음은 다르다. body 순서는 permission → GPS → 설정 실패/로딩 → 스냅샷 없는 실패/로딩 → 결과다.

## 전면 실패

캐시가 있으면 실패해도 목록을 유지하고 snackbar만 보여준다. 캐시가 없을 때만 `LocationTimedOut`, `LocationFailed`, `RefreshTimedOut`, `RefreshFailed`가 전면 실패가 된다. orchestrator는 캐시가 정말 없는지 확인한 뒤에만 확정한다.

## 관심

별도 세션 리듀서가 없다. 좌표는 `SavedStateHandle`, 유종은 설정 첫 값 이후다. 가격이 없어도 행을 지우지 않고 `선택 유종 가격 없음`을 보여준다. 반경·브랜드·Nearby 정렬은 포함 여부와 순서를 바꾸지 않는다.

watch 변경은 station ID별 마지막 의도만 Room에 들어간다. 반복 ON은 처음 `watchedAt`을 지킨다. `WatchToggled`는 `Committed`만 기록한다.

## 설정

요약과 상세가 같은 ViewModel을 쓴다. 첫 설정값 전에는 Loading만 보여준다. 상세는 저장 성공 뒤에만 돌아간다. 실패하면 이전 값을 유지한다.

## UI command

프로세스 재시작 후 복원하지 않지만, collector가 잠시 없어도 잃으면 안 되는 반응은 ViewModel FIFO에 둔다.

- `ShowSnackbar`
- `OpenLocationSettings`
- `OpenExternalMap`

head의 handler가 정상 끝나고 coroutine이 살아 있을 때만 그 ID를 승인한다. 실패·취소한 head는 다음 START에서 다시 시도한다. 외부 실행은 at-least-once다. 정확히 한 번이나 process-death 복원은 약속하지 않는다.

## 이벤트

화면 복원용이 아니다.

- `WatchToggled` — 저장/해제 성공
- `SearchRefreshed` — 스냅샷 저장 성공
- `CompareViewed` — 관심 비교가 처음 보일 때 한 번
- `ExternalMapOpened` — 지도 앱 요청. 실제 실행 성공이 아님
- `RefreshFailed`, `LocationFailed`, `RetryAttempted`

## Station-list 결정적 상태 계약

아래 JSON은 구조화 source와 같아야 한다. 테스트 수나 기기 상태는 넣지 않는다.

<!-- station-list-state-contract:start -->
```json
{
  "schemaVersion": 1,
  "contractId": "station-list-state-concurrency-v1",
  "location": {
    "owner": "LocationStateMachine",
    "generations": ["permission", "gps", "locationRequest", "addressRequest"],
    "providerBoundary": "suspend_outside_lock_then_active_check_and_atomic_commit",
    "precisionDowngrade": "clear_coordinates_address_and_recovery",
    "superseded": "normal_silent"
  },
  "observation": {
    "owner": "StationSearchOrchestrator",
    "failureBoundary": "inside_active_query_session",
    "normalCompletion": "observation_failed",
    "cancellation": "rethrow",
    "sameQueryRetry": "resubscribe_without_remote_refresh",
    "queryChange": "clear_old_unkeyed_result_and_failures",
    "cacheEvidence": "hasCachedSnapshot"
  },
  "watch": {
    "owner": "LatestWatchIntentGate",
    "domainResult": "WatchMutationResult",
    "key": "stationId",
    "sharedOperations": ["updateWatchState", "removeWatchedStation"],
    "onConflict": "insert_ignore_preserves_original_watchedAt",
    "ordering": ["watchedAtEpochMillis DESC", "stationId ASC"],
    "superseded": "normal_silent"
  },
  "command": {
    "owner": "StationListCommandQueue",
    "stateField": "StationListUiState.pendingCommands",
    "delivery": "viewmodel_lifetime_fifo",
    "acknowledgement": "exact_head_after_normal_handler_completion_and_active_check",
    "handlerFailure": "retain_head_for_next_start_or_attachment",
    "externalSideEffect": "at_least_once",
    "processDeathPersistence": "not_promised"
  },
  "refresh": {
    "owner": "RefreshCoordinator",
    "work": "single_exact_identity_job",
    "completion": "registered_before_start_and_identity_guarded",
    "query": "revalidate_before_refresh_and_terminal_delivery",
    "address": "caller_scope_nonblocking_after_successful_acquisition",
    "resultDelivery": "inline_suspending_callback",
    "superseded": "normal_silent"
  },
  "projection": {
    "owner": "StationListStateAssembler",
    "input": "StationListStateInputs",
    "purity": ["no_io", "no_coroutines", "no_clock", "no_logging", "no_mutation"],
    "cacheMarker": "hasCachedSnapshot",
    "listIdentityOwner": "projectStationSearchResult"
  },
  "viewModel": {
    "owner": "StationListViewModel",
    "responsibilities": [
      "viewmodel_lifetime_collection",
      "preference_read_write_admission",
      "action_routing",
      "typed_result_translation",
      "assembler_publication",
      "foreground_gps_suspend_bridge"
    ],
    "forbiddenResponsibilities": [
      "location_or_address_generation",
      "refresh_job_or_work_identity",
      "search_session_retry_or_cache_failure_policy",
      "ui_field_projection_or_body_precedence",
      "command_retention_or_acknowledgement_policy",
      "watch_latest_intent_serialization",
      "one_shot_effect_stream"
    ]
  },
  "verification": {
    "primary": "host_coroutine_room_robolectric_and_app_graph",
    "connectedDeviceRequired": false,
    "connectedDeviceEvidence": "not_claimed"
  }
}
```
<!-- station-list-state-contract:end -->

이 계약을 바꾸면 JSON source와 이 block을 같이 고친다. 여덟 consumer는 정책을 다시 쓰지 말고 이 heading만 가리킨다.

## 한 줄

- 오래 남는 선택: `UserPreferences`
- 실행 중 위치: `LocationStateMachine`
- 검색/캐시/실패: `StationSearchOrchestrator`
- 새로고침: `RefreshCoordinator`
- FIFO: `StationListCommandQueue`
- 연결: `StationListViewModel`
- 최종 UI: `StationListStateAssembler`
