# GasStation 아키텍처 둘러보기

이 문서는 새 기여자가 제품 기준을 놓치지 않고 레이어와 주요 runtime 흐름을 읽도록 돕는 방향 지도입니다. 정확한 활성 모듈 그래프는 [아키텍처](../architecture.md), 파일 배치는 [모듈 계약](../module-contracts.md)이 소유합니다.

## 제품을 먼저 이해하기

GasStation은 한국 운전자가 현재 위치 근처의 주유소를 가격, 거리, 브랜드, 유종, watch 상태와 외부 지도 연결 기준으로 빠르게 비교하도록 돕는 Android 앱입니다. 목표는 목록을 많이 보여주는 것이 아니라 사용자가 지금 어디로 갈지 결정하는 시간을 줄이는 것입니다.

- 가격은 station row의 첫 번째 읽기 대상입니다.
- 거리, 역명, 브랜드, 유종, freshness와 watch 상태는 가격 결정을 돕는 context입니다.
- 네트워크가 실패해도 마지막 성공 snapshot을 함부로 버리지 않습니다.
- `demo`와 `prod`는 모두 정식 실행 경로입니다.
- yellow, black, white 정체성과 가격 우선 위계를 유지하며 generic 장식으로 정보 속도를 늦추지 않습니다.

대표 흐름은 `현재 위치 -> 주변 비교 -> 조건 조정 -> 관심 저장 -> 관심 목록 비교 -> 외부 지도 handoff`입니다. UI나 데이터 정책을 바꿀 때 비교 속도, 실패 시 신뢰성, demo 재현성을 함께 확인합니다.

## 레이어 방향

프로젝트는 `app / feature / domain / data / core / tools / benchmark` 레이어로 나뉩니다.

| 레이어 | 소유하는 것 | 두지 않는 것 |
| --- | --- | --- |
| `app` | Hilt 조립, startup hook, navigation, flavor 연결, 외부 앱 handoff | 캐시·정렬 정책, 화면 전용 상태 |
| `feature:*` | Route, ViewModel, UI state/action/command, Compose 화면과 표시 정책 | Room, Retrofit, DataStore, Android 위치 구현 직접 호출 |
| `domain:*` | repository 계약, use case, 순수 domain model과 event 계약 | Android, Compose, storage DTO |
| `data:*` | repository 구현, remote/DB/cache/history/watchlist 조합 | Compose 상태와 화면 문구 |
| `core:*` | 값 객체, 공통 UI primitive, 플랫폼·DB·network·DataStore 구현, SDK-agnostic 관찰 계약 | feature 전용 제품 정책 |
| `tools:demo-seed` | deterministic demo seed 생성 CLI | 앱 runtime 우회 로직 |
| `benchmark` | macrobenchmark와 baseline profile journey | 앱 기능 구현 |

활성 모듈과 직접 의존 edge를 외우거나 이 문서에 복제하지 않습니다. `settings.gradle.kts`와 [정확한 모듈 그래프](../architecture.md#모듈-그래프)를 함께 봅니다.

## 앱 시작과 flavor

`MainActivity`는 Compose root를 열고 app은 Hilt graph, startup hook과 navigation을 조립합니다. `demo`는 seed asset을 DB에 적재하고 설정을 기본값으로 초기화합니다. 권한을 허용한 뒤에만 고정 좌표를 공급하므로 권한 거부를 기존 위치나 cache로 우회하지 않습니다.

`prod`는 실제 위치와 network를 사용하며 `opinet.apikey` 누락을 startup에서 실패시킵니다. direct Opinet과 proxy endpoint 선택은 app config/Hilt wiring이 맡고 fetcher가 선택 정책을 소유하지 않습니다. Android client key는 완전한 secret boundary가 아니므로 공개 배포 전 승격 조건은 [보안 트레이드오프](../security-trade-offs.md)와 [현재 proxy ADR](../adr/2026-05-18-backend-proxy-escalation.md)을 따릅니다.

## Nearby 상태와 data 흐름

Nearby 화면은 하나의 ViewModel에 모든 책임을 모으지 않습니다.

- `LocationStateMachine`: permission, GPS, location, address generation과 obsolete 결과 차단.
- `StationSearchOrchestrator`: 현재 query 관찰 session과 동일 query 재시작.
- `RefreshCoordinator`: latest eligible query 재검증, refresh와 address lookup 경계.
- `StationListCommandQueue`: ViewModel lifetime의 immutable FIFO와 exact-head acknowledgement.
- `StationListStateInputs`와 `StationListStateAssembler`: 한 시점의 입력을 최종 UI field/body로 순수 투영.
- `StationListViewModel`: action routing, preferences와 collaborator 수집, 결과 번역과 게시.

설정 첫 emission, permission, GPS와 좌표가 준비되어야 `StationQuery`가 생깁니다. 현재 좌표를 유지한 채 반경·유종·브랜드·정렬이 바뀌면 active query를 갱신하고 refresh를 요청합니다. snackbar와 외부 지도 같은 command는 collector 사이의 일회성 stream이 아니라 session FIFO에 남지만, 외부 side effect의 exactly-once나 process-death 복원까지 보장하지는 않습니다.

domain의 `StationQuery`는 현재 검색 조건을 표현하고, cache key는 위치 bucket·반경·유종을 중심으로 합니다. 브랜드와 정렬은 같은 snapshot을 재사용하는 읽기 모델 단계에서 적용합니다. `station_cache_snapshot` marker와 `StationSearchResult.hasCachedSnapshot`은 성공한 빈 결과와 캐시 없음 상태를 구분합니다. refresh 실패는 기존 snapshot을 삭제하지 않으며, cache가 없을 때만 blocking failure가 됩니다.

정확한 상태 lifecycle은 [상태 모델](../state-model.md), retry·freshness·snapshot 정책은 [오프라인 전략](../offline-strategy.md)을 읽습니다.

## Settings, watchlist와 UI 시스템

설정 화면은 `domain:settings`의 명시적 update use case를 통해서만 씁니다. `data:settings`가 DataStore DTO와 `UserPreferences`를 매핑하고, detail route는 committed value를 받은 뒤에만 돌아갑니다. 설정이 목록 조건에 영향을 주면 같은 preferences stream을 통해 active query가 갱신됩니다.

watchlist는 현재 목록의 복제 화면이 아니라 저장 항목 비교 화면입니다. 별도 위치 조회나 refresh session을 소유하지 않고 navigation/SavedStateHandle의 기준 좌표, 선택 유종과 저장소의 watched summary를 사용합니다. 최신 cache가 없으면 price history와 저장 identity를 결합하고, 선택 유종 가격이 없어도 행을 제거하지 않습니다. station ID별 watch 변경은 최신 intent만 반영하며 superseded mutation은 DAO나 analytics side effect를 남기지 않습니다.

`core:designsystem`은 `#FFFCF2` canvas, `#222222` black chrome, `#FFDC00` yellow signal과 재사용 가능한 metric, row, guidance primitive를 제공합니다. feature는 최종 표시 정책을 소유합니다. station list와 watchlist는 실제 브랜드 아이콘을 쓰고 visible label을 반복하지 않으며, 접근성 semantics, ASCII test tag와 48dp touch target을 UI 계약으로 유지합니다.

## 기술 선택을 설명하는 기준

- Kotlin의 sealed type, data class와 coroutine/Flow로 순수 모델과 observable state를 표현합니다.
- Compose는 assembler가 만든 state를 화면으로 투영하고, Hilt는 app/data/core 구현을 조립합니다. Hilt 자체가 모듈 경계를 보장하지는 않습니다.
- Room은 cache, snapshot marker, price history와 watchlist를 저장하며 schema 변경은 migration 증거가 필요합니다.
- DataStore는 작은 사용자 설정을 Flow로 관찰하되 storage DTO를 domain에 노출하지 않습니다.
- Retrofit/OkHttp와 좌표 변환은 network 경계를 테스트 가능한 fetcher로 모읍니다.
- Robolectric과 Roborazzi는 빠른 Android/UI 회귀, connected test는 실제 provider 차이, Macrobenchmark는 물리 기기 성능을 담당합니다. emulator 성능을 committed physical-device 수치로 승격하지 않습니다.

## 다음 읽기

질문별 실제 파일은 [프로젝트 읽기 가이드](../project-reading-guide.md), 변경 순서는 [변경 플레이북](change-playbook.md), 현재 architecture의 전체 설명은 [아키텍처](../architecture.md)로 이어집니다. 문서 전체로 돌아가려면 [문서 허브](../README.md)를 사용합니다.
