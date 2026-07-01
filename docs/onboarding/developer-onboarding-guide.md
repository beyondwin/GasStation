# GasStation 개발자 온보딩 가이드

이 문서는 GasStation을 처음 맡는 Android 개발자가 제품 목적, 프로젝트 구조, 기술 선택 이유, 실제 로직 흐름, 수정 위치, 검증 방법을 순서대로 이해하도록 돕는 학습용 핸드북입니다.

이 문서는 기존 단일 출처를 대체하지 않습니다. 현재 계약 판단은 `docs/project-reading-guide.md`가 안내하는 live 문서와 실제 코드를 우선합니다. 세부 계약은 `docs/module-contracts.md`, 구조와 런타임 흐름은 `docs/architecture.md`, 상태는 `docs/state-model.md`, 오프라인 정책은 `docs/offline-strategy.md`, 테스트와 명령은 `docs/test-strategy.md`와 `docs/verification-matrix.md`를 우선합니다.

처음 읽을 때는 이 문서를 위에서 아래로 따라가도 됩니다. 실제 변경을 시작할 때는 아래 live 문서 표로 돌아가 현재 계약과 검증 범위를 다시 확인합니다.

| 상황 | 먼저 확인할 live 문서 |
| --- | --- |
| 작업 원칙과 금지선 확인 | `AGENTS.md` |
| 무엇을 읽을지 고르기 | `docs/project-reading-guide.md` |
| 작업 순서와 체크리스트 확인 | `docs/agent-workflow.md` |
| 모듈 위치 판단 | `docs/module-contracts.md` |
| 구조와 런타임 흐름 판단 | `docs/architecture.md` |
| 상태 ownership 판단 | `docs/state-model.md` |
| cache/stale/failure/watchlist fallback 판단 | `docs/offline-strategy.md` |
| 테스트 의미와 실행 명령 판단 | `docs/test-strategy.md`, `docs/verification-matrix.md` |

## 1. 이 프로젝트를 한 문장으로 이해하기

GasStation은 한국 운전자가 현재 위치 근처의 주유소를 가격, 거리, 브랜드, 유종, 북마크 상태, 외부 지도 연결 기준으로 빠르게 비교하도록 돕는 Android 앱입니다.

핵심은 "가까운 주유소 목록을 보여준다"가 아니라 "운전자가 지금 어디로 갈지 결정하는 시간을 줄인다"입니다. 그래서 station card에서 가격이 첫 번째 읽기 대상이고, 거리/역명/브랜드/유종/freshness/watch 상태는 가격 판단을 돕는 context입니다.

처음 코드를 읽을 때는 이 제품 기준을 계속 들고 있어야 합니다. UI가 예쁘더라도 가격보다 장식이 먼저 보이면 제품 의도와 어긋납니다. 캐시가 오래됐더라도 마지막 성공 결과를 버리면 운전자가 비교할 근거를 잃습니다. `demo`가 실제 네트워크를 쓰지 않더라도 문서, 테스트, benchmark가 기대는 정식 재현 경로이므로 임시 mock처럼 다루면 안 됩니다.

## 2. 제품 관점: 사용자가 실제로 무엇을 하는 앱인가

대표 사용자 흐름은 아래 순서입니다.

1. 앱을 열면 가까운 주유소 목록 화면으로 진입합니다.
2. 위치 권한과 GPS 상태에 따라 현재 위치를 얻거나, 권한/GPS 안내를 봅니다.
3. 현재 위치 기준으로 주유소를 가격과 거리 중심으로 비교합니다.
4. 유종, 반경, 브랜드 필터, 정렬 기준을 바꿔 조건을 좁힙니다.
5. 관심 있는 주유소를 북마크로 저장합니다.
6. watchlist 화면에서 저장한 주유소를 다시 비교합니다.
7. 선택한 외부 지도 앱으로 길 안내를 넘깁니다.

이 흐름에서 앱이 보장해야 하는 사용성은 세 가지입니다.

- 비교 속도: 가격과 거리가 빨리 보여야 합니다.
- 신뢰성: 네트워크 실패나 stale 상태에서도 마지막으로 성공한 비교 근거를 유지해야 합니다.
- 재현성: `demo` 경로에서 같은 seed와 고정 위치로 문서, 테스트, benchmark를 반복할 수 있어야 합니다.

따라서 기능을 추가할 때는 "화면에 뭔가를 더 넣을 수 있는가"보다 "비교 속도, 신뢰성, 재현성을 깨지 않는가"를 먼저 확인합니다.

## 3. 전체 구조: 멀티모듈 Clean Architecture

GasStation은 `app / feature / domain / data / core / tools / benchmark`로 나뉜 멀티모듈 Android 프로젝트입니다. 이 구조의 목적은 "파일을 많이 나누기"가 아니라, 변경 이유가 다른 코드를 서로 다른 모듈에 둬서 회귀 범위를 줄이는 것입니다.

큰 방향은 아래와 같습니다.

- `app`은 앱을 조립합니다. Hilt 그래프, startup hook, navigation, flavor 연결, 외부 지도 handoff를 맡습니다.
- `feature:*`는 화면을 만듭니다. Route, ViewModel, UI state, action, effect, Compose screen이 여기에 있습니다.
- `domain:*`는 앱의 계약과 순수 모델을 둡니다. repository interface, use case, domain model이 여기에 있습니다.
- `data:*`는 repository 구현을 둡니다. Room, remote source, cache, history, watchlist 조합이 여기에 있습니다.
- `core:*`는 여러 모듈이 공유하는 값 객체, 플랫폼 구현, 디자인 primitive, 저장/네트워크/DB 인프라를 둡니다.
- `tools:demo-seed`는 demo seed를 재생성하는 JVM CLI입니다.
- `benchmark`는 macrobenchmark와 baseline profile 경로입니다.

이 구조를 읽는 기준은 단순합니다. 화면의 표시/interaction은 `feature`, 정책과 계약은 `domain`, 저장/원격/캐시 조합은 `data`, 플랫폼 구현은 `core`, 최종 연결은 `app`입니다. 새 기능을 화면 파일에서 바로 시작하면 빠르게 보일 수는 있지만, 금방 Room/Retrofit/DataStore가 feature로 새어 들어와 모듈 경계가 무너집니다.

더 깊게 볼 문서:

- `docs/architecture.md`: 현재 모듈 그래프와 런타임 흐름의 단일 출처입니다.
- `docs/module-contracts.md`: 어떤 변경을 어느 모듈에 둬야 하는지 판단하는 단일 출처입니다.
- `docs/project-reading-guide.md`: 질문별로 어떤 문서와 코드를 먼저 열지 안내하는 라우터입니다.

## 4. Gradle 모듈과 레이어별 책임

활성 모듈은 파일시스템에 남아 있는 디렉터리가 아니라 `settings.gradle.kts`의 `include(...)` 기준으로 판단합니다. 현재 포함된 모듈은 아래와 같습니다.

```text
:app
:core:model
:core:observability
:core:designsystem
:core:location
:core:network
:core:database
:core:datastore
:domain:location
:domain:settings
:domain:station
:data:settings
:data:station
:feature:settings
:feature:station-list
:feature:watchlist
:tools:demo-seed
:benchmark
```

각 레이어를 신규 개발자 관점에서 풀면 다음과 같습니다.

| 레이어 | 쉽게 말하면 | 대표 모듈 | 여기에 두면 좋은 것 | 여기에 두면 안 되는 것 |
| --- | --- | --- | --- | --- |
| `app` | 최종 조립자 | `:app` | Hilt binding, flavor별 startup hook, navigation, 외부 지도 실행 | 캐시 정책, 정렬 규칙, 화면 전용 상태 |
| `feature` | 화면과 사용자 interaction | `:feature:station-list`, `:feature:settings`, `:feature:watchlist` | UI state, action, effect, Compose screen, 화면별 표시 정책 | Room DAO, Retrofit service, DataStore 구현 직접 호출 |
| `domain` | 계약과 순수 규칙 | `:domain:station`, `:domain:settings`, `:domain:location` | repository interface, use case, domain model, event contract | Android/Compose/Room/Retrofit/DataStore 타입 |
| `data` | 계약의 실제 구현 | `:data:station`, `:data:settings` | repository 구현, DB/remote/cache/history 조합 | Compose UI state, 화면 문구 |
| `core` | 공유 기반 | `:core:model`, `:core:network`, `:core:database`, `:core:location`, `:core:datastore`, `:core:designsystem`, `:core:observability` | 값 객체, Android provider 구현, Room DB, Retrofit fetcher, DataStore source, 디자인 primitive | feature 전용 비즈니스 정책 |
| `tools` | 앱 밖 보조 도구 | `:tools:demo-seed` | demo seed 생성 CLI | 앱 런타임 우회 로직 |
| `benchmark` | 성능 증거 | `:benchmark` | macrobenchmark, baseline profile journey | 기능 구현 |

중요한 예시는 위치 흐름입니다. `feature:station-list`가 Android 위치 API를 직접 호출하지 않습니다. 화면은 `domain:location`의 use case를 호출하고, 실제 Android 구현은 `core:location`에 있습니다. 이렇게 해야 위치 provider를 바꾸거나 demo override를 다룰 때 화면 코드가 흔들리지 않습니다.

또 다른 예시는 설정 쓰기입니다. feature가 `SettingsRepository` 구현이나 DataStore를 직접 만지지 않고, `domain:settings`의 명시적 update use case를 통합니다. 설정 저장 방식이 바뀌어도 화면의 action 의미는 유지됩니다.

## 5. 기술 스택 요약표

| 기술 | 이 프로젝트에서 하는 일 | 선택 이유 | 장점 | 단점/주의점 | 대표 파일 |
| --- | --- | --- | --- | --- | --- |
| Kotlin | 앱, 도메인, 빌드 로직의 주 언어 | Android 공식 생태계와 coroutine/Flow 지원 | null-safety, data class, sealed type로 상태 표현이 좋음 | 언어 기능이 많아 과하게 추상화하기 쉬움 | `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationSearchResult.kt` |
| Gradle Kotlin DSL + convention plugins | 모듈별 빌드 설정과 공통 Android/JVM 규칙 관리 | 멀티모듈에서 반복 설정을 줄이기 위해 | 플러그인 하나로 test/lint/Compose/Hilt 설정을 재사용 | build-logic 변경은 전체 빌드에 영향이 큼 | `build-logic/convention/src/main/kotlin/*`, `gradle/libs.versions.toml` |
| 멀티모듈 Clean Architecture | 화면, 계약, 구현, 공유 인프라 분리 | 변경 범위와 의존 방향을 통제하기 위해 | 회귀 범위가 좁고 테스트 단위가 명확함 | 작은 기능도 여러 파일을 확인해야 함 | `settings.gradle.kts`, `docs/module-contracts.md` |
| Jetpack Compose | station list, settings, watchlist UI 구현 | 상태 기반 UI와 테스트 가능한 화면 계약을 위해 | UI가 state의 함수처럼 읽힘 | state ownership과 effect 처리가 흐리면 복잡해짐 | `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt` |
| Material 3 + `core:designsystem` | 공통 색상, 타이포, metric/row/guidance primitive | yellow/black/white 정체성과 가격 우선 위계를 반복하기 위해 | 화면 간 정보 위계가 일관됨 | feature 전용 문구/정책을 designsystem에 넣으면 안 됨 | `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/*` |
| AndroidX Lifecycle ViewModel | 화면 세션 상태와 action 처리 | configuration change와 lifecycle에 맞는 상태 보존 | UI state 조합 위치가 명확함 | ViewModel이 모든 정책을 흡수하면 비대해짐 | `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt` |
| Coroutines + Flow | preferences, Room 관찰, 위치 availability, UI state stream | 비동기와 observable state를 자연스럽게 표현하기 위해 | combine/flatMapLatest로 state 조합이 명확함 | cancellation, lifecycle collection을 잘못 다루면 누수/중복 실행 위험 | `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationSearchOrchestrator.kt` |
| Hilt | app/data/core 구현 조립 | 구현체 wiring을 app과 각 module에서 안정적으로 관리하기 위해 | 생성자 주입으로 테스트와 교체가 쉬움 | 잘못 쓰면 모듈 경계 위반을 숨길 수 있음 | `app/src/main/java/com/gasstation/di/*`, `data/station/src/main/kotlin/com/gasstation/data/station/StationDataModule.kt` |
| Room | 주유소 캐시, 스냅샷, 가격 이력, watchlist 저장 | 오프라인 fallback과 observable DAO가 필요해서 | SQL/Flow/migration 테스트로 저장 계약을 보호 | schema 변경 시 migration 테스트가 필요함 | `core/database/src/main/kotlin/com/gasstation/core/database/station/*` |
| DataStore | 사용자 설정 저장 | 작은 key-value preference 상태에 적합 | Flow 기반 관찰과 atomic update가 쉬움 | storage DTO와 domain model을 섞으면 경계가 흐려짐 | `core/datastore/src/main/kotlin/com/gasstation/core/datastore/*` |
| Retrofit + OkHttp + Gson converter | Opinet/proxy HTTP 호출 | typed service와 테스트 가능한 fetcher 구성을 위해 | 네트워크 경계가 명확함 | API key/endpoint 보안 한계를 별도로 관리해야 함 | `core/network/src/main/kotlin/com/gasstation/core/network/service/OpinetService.kt` |
| Navigation Compose | station list, settings, settings detail, watchlist route 연결 | Compose UI 안에서 route 그래프를 관리하기 위해 | route와 argument 흐름이 코드로 명확함 | navigation에 business policy가 들어가면 안 됨 | `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt` |
| Play Services Location + Android Geocoder | 현재 위치와 주소 라벨 획득 | 실제 기기 위치와 행정동 표시가 필요해서 | Android platform 기능을 활용함 | permission/GPS/provider/geocoder 실패 분기가 많음 | `core/location/src/main/kotlin/com/gasstation/core/location/*` |
| proj4j | WGS84 좌표를 KATEC으로 변환 | Opinet 입력 좌표계를 앱 안에서 맞추기 위해 | 별도 좌표 변환 API가 필요 없음 | 좌표 변환 테스트가 깨지면 검색 결과가 틀어짐 | `core/network/src/main/kotlin/com/gasstation/core/network/station/LocalKoreanCoordinateTransform.kt` |
| Robolectric | Android/Compose 관련 unit test | 기기 없이 빠르게 Android resource/UI state를 검증하기 위해 | 로컬 피드백이 빠름 | 실제 device/provider 차이는 connected test가 필요함 | `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/*Test.kt` |
| Roborazzi | screenshot regression | 디자인 시스템과 주요 화면의 시각 회귀를 잡기 위해 | UI 깨짐을 이미지로 확인 가능 | 의도적 디자인 변경 시 snapshot 갱신 책임이 생김 | `core/designsystem/src/test/snapshots/*`, `feature/station-list/src/test/snapshots/*` |
| Macrobenchmark + baseline profile | startup/list/refresh/watchlist 성능 증거 | portfolio와 release evidence를 물리 기기 기준으로 남기기 위해 | 실제 사용자 흐름 기반 성능을 측정 | emulator 수치를 committed 성능으로 쓰면 안 됨 | `benchmark/src/main/kotlin/com/gasstation/benchmark/*` |
| Kover + Pitest | coverage report와 mutation testing | 테스트가 단순 실행이 아니라 결함을 잡는지 보기 위해 | 테스트 품질 신호를 얻음 | Pitest는 느리고 일부 equivalent mutant를 해석해야 함 | `docs/test-strategy.md`, `domain/station/build.gradle.kts` |
| Spotless + ktlint | formatting/lint gate | 코드 스타일과 review noise를 줄이기 위해 | 자동화된 일관성 | formatting만 통과한다고 설계가 좋은 것은 아님 | `build-logic/convention/src/main/kotlin/GasStationSpotlessConventionPlugin.kt` |

## 6. 기술별 선정 이유, 장점, 단점, 주의점

### Kotlin

Kotlin은 Android 공식 생태계에서 가장 자연스러운 선택이고, 이 프로젝트는 sealed interface, data class, value object, coroutine을 적극적으로 사용합니다. 예를 들어 `StationSearchResult`는 단순 DTO가 아니라 "현재 쿼리 버킷에 캐시 스냅샷이 있는가"라는 의미를 `hasCachedSnapshot`으로 드러냅니다.

장점은 상태와 불변식을 타입으로 표현하기 쉽다는 점입니다. 단점은 편의 기능이 많아 "멋진 추상화"를 만들기 쉽다는 점입니다. 이 프로젝트에서는 추상화를 추가하기 전에 `docs/module-contracts.md`의 소유 범위에 맞는지 먼저 확인합니다.

### Gradle Kotlin DSL과 convention plugin

모듈이 많기 때문에 각 `build.gradle.kts`에 Android/Kotlin/test 설정을 반복하지 않습니다. `build-logic/convention/src/main/kotlin/*`에 `gasstation.android.library`, `gasstation.android.library.compose`, `gasstation.jvm.library`, `gasstation.android.hilt`, `gasstation.android.room` 같은 규칙을 둡니다.

장점은 새 모듈이나 기존 모듈 설정이 일관된다는 점입니다. 단점은 build-logic이 깨지면 여러 모듈이 동시에 영향을 받는다는 점입니다. 빌드 설정을 바꿀 때는 제품 코드보다 더 보수적으로 검증해야 합니다.

### 멀티모듈 Clean Architecture

이 구조는 "feature가 infra를 모르게 한다"는 목표가 핵심입니다. `feature:station-list`는 위치 provider, Room, Retrofit, DataStore를 직접 알지 않고 domain use case와 UI state만 다룹니다. `data:station`은 Room/remote/cache를 조합하지만 Compose 상태나 화면 문구를 만들지 않습니다.

장점은 변경 이유가 분리된다는 점입니다. 캐시 정책을 바꿀 때는 data/core/database 중심으로, 화면 위계를 바꿀 때는 feature/designsystem 중심으로 보면 됩니다. 단점은 처음에는 파일 이동이 많아 보인다는 점입니다. 그래서 이 문서는 "작업 유형별 수정 위치"를 뒤에서 따로 제공합니다.

### Jetpack Compose

Compose는 `StationListUiState`를 화면으로 투영하는 데 잘 맞습니다. 상태가 바뀌면 UI가 다시 그려지고, 테스트는 semantics/testTag/contentDescription을 통해 계약을 확인합니다.

주의점은 side effect입니다. snackbar, 위치 설정 열기, 외부 지도 열기는 영속 상태가 아니라 한 번 소비할 `StationListEffect`입니다. 이런 effect를 UI state에 섞으면 화면 재구성 때 중복 실행될 수 있습니다.

### Hilt

Hilt는 `app`, `data`, `core`의 구현체를 생성자 주입으로 연결합니다. 예를 들어 `DefaultStationRepository`는 DAO, remote data source, retry policy, event logger, crash reporter, transaction runner, clock을 주입받습니다.

장점은 wiring 코드가 줄고 테스트 대체가 쉬워진다는 점입니다. 단점은 의존성 주입이 된다고 해서 의존 방향이 맞는 것은 아니라는 점입니다. feature가 infra 구현을 주입받기 시작하면 Hilt는 오히려 잘못된 구조를 쉽게 숨깁니다.

### Room

Room은 이 앱의 오프라인 전략 중심입니다. `station_cache`만 있으면 "성공했지만 결과 0건"과 "아직 캐시 없음"을 구분하기 어렵기 때문에 `station_cache_snapshot` 마커를 따로 둡니다.

장점은 Flow 기반 관찰, SQL index, migration test를 통해 저장 계약을 보호할 수 있다는 점입니다. 단점은 schema 변경 비용이 있다는 점입니다. DB 변경은 DAO 테스트와 migration 테스트까지 같이 봐야 합니다.

### DataStore

DataStore는 반경, 유종, 브랜드 필터, 정렬, 지도 앱 같은 `UserPreferences`를 저장합니다. `core:datastore`는 storage-local DTO만 알고, domain model 변환은 `data:settings`가 담당합니다.

장점은 작은 설정 상태에 적합하고 Flow로 관찰하기 쉽다는 점입니다. 주의점은 저장 포맷과 domain model을 섞지 않는 것입니다. enum 이름 fallback 같은 domain 의미는 data/domain 경계에서 처리해야 합니다.

### Retrofit, OkHttp, Gson converter

`core:network`는 direct Opinet 호출과 proxy 호출을 `StationNetworkSource`로 추상화합니다. `NetworkStationFetcher`는 WGS84 좌표를 KATEC으로 변환하고 Opinet API를 호출합니다.

장점은 네트워크 경계가 테스트 가능한 fetcher로 모인다는 점입니다. 단점은 Android 클라이언트에 들어간 API key가 완전한 secret boundary가 아니라는 점입니다. 공개 서비스 배포 전에는 backend proxy, quota, key restriction을 별도로 고려해야 하며 관련 기준은 `docs/security-trade-offs.md`와 `docs/adr/2026-05-18-backend-proxy-escalation.md`에 있습니다.

### demo flavor

`demo`는 "가짜 화면"이 아니라 반복 가능한 정식 실행 경로입니다. 앱 시작 시 seed DB를 적재하고 preferences를 초기화하며, 고정 좌표로 목록과 benchmark를 재현합니다.

장점은 문서, screenshot, UI test, benchmark가 같은 시작 상태를 공유한다는 점입니다. 단점은 demo seed와 실제 규칙이 어긋나면 신뢰가 크게 떨어진다는 점입니다. demo 전용 우회 로직으로 제품 규칙을 피하면 안 됩니다.

### 테스트 도구

Robolectric은 빠른 로컬 Android 테스트를, Roborazzi는 screenshot 회귀를, Macrobenchmark는 물리 기기 성능 증거를 담당합니다. Kover와 Pitest는 커버리지와 변이 테스트 신호를 보조로 제공합니다.

장점은 계층별로 빠른 테스트와 깊은 테스트를 나눌 수 있다는 점입니다. 단점은 모든 테스트를 항상 돌리면 피드백이 느려진다는 점입니다. 그래서 `docs/verification-matrix.md`에서 변경 유형별 명령을 고릅니다.
## 7. 앱 시작 흐름

앱 시작 흐름은 `app` 모듈이 소유합니다. 비즈니스 정책을 만들기보다 이미 정의된 feature/data/core/domain 구현을 조립하는 역할입니다.

먼저 볼 파일:

- `app/src/main/java/com/gasstation/App.kt`
- `app/src/main/java/com/gasstation/startup/AppStartupRunner.kt`
- `app/src/main/java/com/gasstation/MainActivity.kt`
- `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`

읽는 순서는 아래와 같습니다.

1. `App.kt`에서 Hilt application이 시작됩니다.
2. `AppStartupRunner`가 flavor별 `AppStartupHook`을 실행합니다.
3. `MainActivity`가 Compose host를 띄우고 system bar/startup reporting bridge를 연결합니다.
4. `GasStationNavHost`가 시작 destination으로 station list를 엽니다.

`GasStationNavHost`는 네 route를 연결합니다.

- station list: 앱의 시작 화면입니다.
- settings: 설정 요약 화면입니다.
- settings detail: 설정 항목별 선택 화면입니다.
- watchlist: 저장한 주유소 비교 화면입니다.

여기서 중요한 점은 navigation이 화면 이동만 담당한다는 것입니다. 예를 들어 station list에서 주유소를 눌렀을 때 외부 지도 앱을 여는 handoff는 `app`의 `ExternalMapLauncher`가 실행하지만, "어떤 provider를 쓸지" 같은 사용자 선택 상태는 settings/domain/data 경로에서 옵니다.

`reportFullyDrawn()`도 app이 연결하지만, "first usable content가 무엇인가"라는 판단은 feature 쪽의 policy가 맡습니다. app은 Android platform bridge이고, 화면 의미는 feature가 소유합니다.

## 8. 목록 화면 흐름

목록 화면은 이 프로젝트의 중심입니다. 위치, 권한, GPS, 설정, 검색 query, 캐시, stale, refresh 실패, watch toggle, 외부 지도 effect가 모두 이 화면에서 만납니다.

먼저 볼 파일:

- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/LocationStateMachine.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationSearchOrchestrator.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListEffect.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListCards.kt`

책임은 네 덩어리로 나눠 봅니다.

| 구성요소 | 책임 |
| --- | --- |
| `StationListRoute` | Compose route, 권한 상태 전달, lifecycle-bound availability 수집, effect 소비 |
| `LocationStateMachine` | 권한, GPS availability, 현재 좌표, 주소 라벨, denied/recovery 같은 세션 위치 상태 |
| `StationSearchOrchestrator` | active query, cache snapshot state, observed search result, pending/blocking failure |
| `StationListViewModel` | preferences, location state, search projection, loading flag, action dispatch, one-shot effect, 최종 `StationListUiState` 조합 |

처음 읽을 때 `StationListViewModel`만 계속 보면 복잡해 보입니다. 의도는 반대입니다. ViewModel이 모든 정책을 직접 소유하지 않도록 위치 상태는 `LocationStateMachine`, query/cache/failure 판단은 `StationSearchOrchestrator`, 저장/원격/캐시 조합은 `data:station`으로 내려 보낸 구조입니다.

흐름을 따라가면 아래와 같습니다.

1. Route가 화면 foreground 동안 GPS availability를 수집하고 ViewModel action으로 넘깁니다.
2. ViewModel은 `ObserveUserPreferencesUseCase`로 설정을 구독합니다.
3. 위치가 준비되면 설정값과 좌표를 합쳐 `StationQuery`를 만듭니다.
4. `StationSearchOrchestrator`가 해당 query로 `ObserveNearbyStationsUseCase`를 구독합니다.
5. refresh가 필요하면 `RefreshNearbyStationsUseCase`를 호출합니다.
6. 저장소에서 온 `StationSearchResult`는 UI projection으로 바뀌고, 최종 `StationListUiState`가 만들어집니다.
7. 화면은 `StationListUiState`를 보고 목록, stale 배너, 권한/GPS/loading/empty/failure 상태를 렌더링합니다.

`StationListEffect`는 한 번만 소비할 반응입니다. snackbar, 위치 설정 열기, 외부 지도 열기는 UI state에 넣어 오래 보존할 값이 아닙니다. 화면 재구성 때 중복 실행되지 않게 effect stream으로 분리합니다.

상태 의미를 더 깊게 보려면 `docs/state-model.md`를 먼저 읽습니다.

## 9. 데이터 흐름: Opinet, proxy, 좌표 변환, Room snapshot

데이터 흐름은 domain 계약에서 시작해 data 구현, core network/database로 내려갑니다.

먼저 볼 파일:

- `domain/station/src/main/kotlin/com/gasstation/domain/station/StationRepository.kt`
- `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQuery.kt`
- `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQueryCacheKey.kt`
- `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationSearchResult.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/StationSearchResultAssembler.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/StationCachePolicy.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/StationRetryPolicy.kt`
- `core/database/src/main/kotlin/com/gasstation/core/database/station/StationCacheDao.kt`
- `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationFetcher.kt`
- `core/network/src/main/kotlin/com/gasstation/core/network/station/ProxyStationFetcher.kt`
- `core/network/src/main/kotlin/com/gasstation/core/network/station/LocalKoreanCoordinateTransform.kt`
- `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfig.kt`

`StationQuery`는 현재 검색 조건을 표현합니다.

- 현재 좌표
- 검색 반경
- 유종
- 브랜드 필터
- 정렬 순서

하지만 cache key에는 모든 값이 들어가지 않습니다. `StationQueryCacheKey`는 위치 버킷, 반경, 유종만 포함합니다. 브랜드 필터와 정렬은 캐시 키가 아니라 읽기 모델 단계에서 적용합니다. 이렇게 해야 같은 위치/반경/유종 스냅샷을 재사용하면서도 UI 조건을 빠르게 바꿀 수 있습니다.

Room 저장 모델의 핵심은 `station_cache_snapshot`입니다. `station_cache` 행만 있으면 아래 두 상태를 구분하기 어렵습니다.

- 성공적으로 조회했지만 결과가 0건인 상태
- 아직 성공한 조회가 없어 캐시 자체가 없는 상태

그래서 `StationSearchResult.hasCachedSnapshot`이 중요합니다. 문서나 코드에서 "캐시가 있다"를 판단할 때 `fetchedAt != null`보다 `hasCachedSnapshot` 의미를 우선해야 합니다.

refresh 성공 흐름은 다음과 같습니다.

1. `DefaultStationRepository.refreshNearbyStations()`가 remote data source를 호출합니다.
2. `StationRetryPolicy`가 `Timeout`과 `Network` 실패에 한해 500ms 뒤 한 번 재시도합니다.
3. remote 결과를 cache entity와 price history entity로 바꿉니다.
4. transaction 안에서 snapshot을 교체하고 가격 이력을 추가합니다.
5. 오래된 cache/snapshot을 정리합니다.
6. `StationEvent.SearchRefreshed`를 기록합니다.

refresh 실패 흐름은 더 중요합니다. 실패했다고 기존 스냅샷을 지우지 않습니다. 기존 캐시가 있으면 UI는 마지막 성공 결과를 계속 보여주고, snackbar나 stale/failure context로 실패를 알립니다. 캐시가 없을 때만 blocking failure가 됩니다.

네트워크 흐름은 `core:network`가 담당합니다. direct Opinet 모드에서는 `NetworkStationFetcher`가 현재 WGS84 좌표를 `LocalKoreanCoordinateTransform`으로 KATEC 좌표로 바꾼 뒤 Opinet API를 호출합니다. proxy 모드에서는 `ProxyStationFetcher`가 proxy endpoint를 사용합니다. 어떤 endpoint mode를 쓸지는 `app`의 config/Hilt wiring이 결정하고, fetcher 자체는 선택 정책을 소유하지 않습니다.

API key는 Android client `BuildConfig`에 들어갈 수 있으므로 완전한 secret boundary가 아닙니다. 현재 범위에서는 direct Opinet 경로를 지원하지만, 공개 배포 전 backend proxy 승격 조건은 `docs/security-trade-offs.md`와 `docs/adr/2026-05-18-backend-proxy-escalation.md`를 따릅니다.

오프라인과 stale 정책은 `docs/offline-strategy.md`가 단일 출처입니다.

## 10. demo/prod flavor 차이

`demo`와 `prod`는 둘 다 정식 실행 경로입니다.

먼저 볼 파일:

- `app/build.gradle.kts`
- `app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt`
- `app/src/demo/kotlin/com/gasstation/DemoLocationModule.kt`
- `app/src/demo/kotlin/com/gasstation/di/DemoStationRemoteDataSourceModule.kt`
- `app/src/prod/kotlin/com/gasstation/startup/ProdSecretsStartupHook.kt`

`demo`는 앱 시작 때 아래를 수행합니다.

1. seed asset을 읽습니다.
2. DB의 station cache, snapshot, price history, watched station을 초기화합니다.
3. seed query와 history를 DB에 적재합니다.
4. `UserPreferences.default()`로 설정을 되돌립니다.
5. 강남역 2번 출구 기준 고정 좌표를 사용합니다.

따라서 `demo`는 "서버 없이 그럴듯한 화면을 보여주는 mock"이 아닙니다. 실제 cache/stale/watchlist 규칙을 seed 데이터로 재현하는 경로입니다. README screenshot, UI test, benchmark가 이 안정성에 기대고 있습니다.

`prod`는 실제 사용 경로입니다.

- `opinet.apikey`가 필요합니다.
- 실제 위치 provider를 사용합니다.
- direct Opinet 또는 proxy endpoint 모드를 사용합니다.
- 앱 시작 시 `ProdSecretsStartupHook`가 키 누락을 빠르게 실패시킵니다.

새 동작이 사용자 흐름에 보이면 `demo`에서 재현 가능한지도 확인합니다. `demo`에서만 돌아가는 우회 구현을 넣으면 안 되고, `prod`에서만 의미 있는 로직이라도 demo/test/benchmark 전제를 깨지 않는지 봐야 합니다.

## 11. 설정 화면 흐름

설정 화면은 `UserPreferences`를 편집하는 얇은 UI 계층에 가깝습니다.

먼저 볼 파일:

- `feature/settings/src/main/kotlin/com/gasstation/feature/settings/*`
- `domain/settings/src/main/kotlin/com/gasstation/domain/settings/*`
- `data/settings/src/main/kotlin/com/gasstation/data/settings/DefaultSettingsRepository.kt`
- `core/datastore/src/main/kotlin/com/gasstation/core/datastore/*`

흐름은 아래와 같습니다.

1. `SettingsRoute`가 설정 요약 화면을 보여줍니다.
2. 사용자가 항목을 누르면 `SettingsDetailRoute`로 이동합니다.
3. detail route는 별도 ViewModel이 아니라 settings back stack owner를 통해 같은 `SettingsViewModel`을 공유합니다.
4. 사용자가 값을 선택하면 `UpdateFuelTypeUseCase`, `UpdateSearchRadiusUseCase`, `UpdateBrandFilterUseCase`, `UpdateMapProviderUseCase`, `UpdatePreferredSortOrderUseCase` 같은 명시적 use case를 호출합니다.
5. `data:settings`가 DataStore storage DTO와 domain `UserPreferences`를 매핑합니다.
6. 목록 화면은 같은 preferences stream을 보고 검색 조건을 갱신합니다.

주의할 점은 설정 쓰기 경로입니다. feature에서 DataStore를 직접 호출하지 않습니다. "설정 하나 추가"는 대개 아래 순서를 따릅니다.

1. `domain/settings/model/UserPreferences.kt`
2. 필요한 update use case
3. `core/datastore` storage DTO/serializer/data source
4. `data/settings/DefaultSettingsRepository.kt`
5. `feature/settings` 화면과 ViewModel
6. 목록 검색에 영향이 있으면 `feature:station-list` query 흐름

## 12. watchlist 흐름

watchlist는 현재 목록의 복제 화면이 아니라 저장 항목 비교 화면입니다.

먼저 볼 파일:

- `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/*`
- `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveWatchlistUseCase.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/WatchlistSummaryAssembler.kt`

목록 화면에서 사용자가 watch toggle을 누르면 `UpdateWatchStateUseCase`를 통해 저장 상태가 바뀝니다. watchlist 화면으로 이동할 때는 기준 좌표가 navigation argument로 넘어가고, `WatchlistViewModel`은 `SavedStateHandle`에서 이 좌표를 읽습니다.

watchlist는 별도 위치 조회나 refresh 세션 상태를 들고 있지 않습니다. 저장된 주유소를 비교할 기준 좌표와 저장소에서 관찰한 `WatchedStationSummary`가 핵심입니다.

저장소는 watchlist 항목을 만들 때 아래 순서로 복원합니다.

1. `watched_station`에서 저장 항목을 읽습니다.
2. 같은 station id의 최신 cache가 있으면 우선 사용합니다.
3. 최신 cache가 없거나 무효하면 price history와 저장된 이름/브랜드/좌표로 가능한 만큼 복원합니다.
4. 둘 다 없으면 요약에서 제외합니다.

그래서 watchlist는 현재 검색 결과에 없는 주유소라도 바로 사라지지 않습니다. 사용자가 저장한 비교 대상이라는 제품 의미를 최대한 유지합니다.

## 13. 오프라인, stale, failure 처리

이 앱의 오프라인 전략은 "마지막 성공 스냅샷을 버리지 않고, 실패와 빈 결과를 구분한 채 계속 보여준다"입니다.

핵심 용어는 아래와 같습니다.

| 용어 | 의미 |
| --- | --- |
| snapshot | 특정 query bucket에 대해 마지막으로 성공한 조회 결과 |
| snapshot marker | `station_cache_snapshot` 행. 결과가 0건이어도 성공한 조회였음을 남김 |
| stale | `StationCachePolicy` 기준 5분을 넘긴 저장 결과 |
| blocking failure | 캐시가 없어 화면 전체가 실패 상태로 가야 하는 실패 |

실패 처리 기준은 `StationSearchOrchestrator`와 `StationSearchResult.hasCachedSnapshot`이 함께 만듭니다.

- 캐시 스냅샷이 있으면 refresh/location 실패 후에도 기존 결과를 유지합니다.
- 캐시 스냅샷이 없으면 timeout/failure가 blocking failure가 됩니다.
- 성공한 빈 결과는 실패가 아닙니다.
- `fetchedAt != null`만으로 캐시 존재를 판단하지 않습니다.

이 정책은 사용자 관점에서 중요합니다. 운전자는 네트워크가 잠깐 실패해도 마지막으로 성공한 가격 비교 화면을 보고 판단할 수 있어야 합니다. 반대로 아직 한 번도 성공한 조회가 없다면 빈 화면을 정상 결과처럼 보여주면 안 됩니다.

세부 정책과 테이블 의미는 `docs/offline-strategy.md`, 상태 lifecycle은 `docs/state-model.md`를 우선합니다.

## 14. 디자인 시스템과 UI 정보 위계

디자인 시스템은 `core:designsystem`이 소유합니다.

먼저 볼 파일:

- `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/*`
- `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/*`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListCards.kt`
- `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`

GasStation UI의 기본 정체성은 yellow, black, white입니다. 하지만 색상보다 더 중요한 것은 정보 위계입니다.

- 가격이 첫 번째 읽기 대상입니다.
- 거리는 두 번째 판단 기준입니다.
- 역명, 브랜드, 유종, freshness, watch 상태는 보조 context입니다.
- 상태 안내는 permission, GPS, loading, empty, failure가 같은 guidance system처럼 읽혀야 합니다.

`core:designsystem`의 공통 primitive는 시각 리듬과 재사용 가능한 chrome을 제공합니다. 예를 들어 metric, supporting info, row, guidance, status banner 같은 구성요소입니다. 하지만 feature별 정책은 designsystem에 넣지 않습니다.

예시는 브랜드 표시입니다.

- station list에서는 가격/거리 비교 속도를 위해 브랜드 텍스트보다 브랜드 아이콘 중심입니다.
- watchlist에서는 저장 항목 식별이 중요하므로 브랜드 아이콘과 visible brand label을 함께 보여줍니다.

즉 "브랜드를 어떻게 표시할지"의 최종 화면 정책은 feature가 소유하고, 브랜드 아이콘/라벨 primitive는 designsystem이 제공합니다.

접근성 semantics와 test tag도 UI 계약입니다. 테스트 selector를 정리한다는 이유로 semantics를 제거하면 안 됩니다. 제거가 필요하면 대체 테스트와 접근성 설명을 함께 마련해야 합니다.
## 15. 테스트 전략과 검증 명령

테스트는 "이 앱이 `demo`와 `prod` 정식 경로에서 계속 성립하는가"를 확인하는 장치입니다. 어떤 테스트가 어떤 계층을 보호하는지는 `docs/test-strategy.md`, 실제 명령 조합은 `docs/verification-matrix.md`가 단일 출처입니다.

계층별로 보면 아래처럼 생각하면 됩니다.

| 변경 영역 | 주로 보는 테스트 |
| --- | --- |
| 값 객체, enum, 순수 규칙 | `:core:model:test`, `:domain:*:test` |
| 위치 계약과 Android 위치 구현 | `:domain:location:test`, `:core:location:testDebugUnitTest` |
| 캐시, retry, watchlist 저장소 | `:data:station:testDebugUnitTest`, `:core:database:testDebugUnitTest` |
| 설정 저장 | `:domain:settings:test`, `:data:settings:testDebugUnitTest`, `:core:datastore:testDebugUnitTest` |
| 화면 상태와 Compose 계약 | `:feature:*:testDebugUnitTest`, `verifyRoborazziDebug` |
| app 조립, flavor, startup | `:app:testDemoDebugUnitTest`, `:app:testProdDebugUnitTest` |
| 실제 demo 사용자 흐름 | `:app:connectedDemoDebugAndroidTest` |
| 성능 증거 | `:benchmark:connectedBenchmarkAndroidTest` on physical device |

문서/가벼운 변경 후 빠른 로컬 확인은 아래 조합을 기준으로 봅니다.

```bash
./gradlew \
  :core:model:test \
  :core:network:test \
  :domain:location:test \
  :core:observability:test \
  :core:designsystem:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :app:assembleDemoDebug \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :benchmark:assemble
```

문서만 바꿨다면 우선 아래 명령으로 공백/패치 오류를 확인합니다.

```bash
git diff --check -- README.md docs/project-reading-guide.md docs/onboarding/developer-onboarding-guide.md
```

Gradle 테스트는 무조건 많이 돌리는 것이 답이 아닙니다. 변경 계층에 맞는 테스트를 먼저 고르고, 공통 계약이나 release 전에는 `docs/verification-matrix.md`의 더 넓은 조합으로 확장합니다.

## 16. 작업 유형별 수정 위치

처음 맡은 작업은 "어느 파일을 고치면 되지?"보다 "어느 모듈이 이 정책을 소유하지?"로 접근합니다.

| 변경 유형 | 먼저 읽을 파일 | 주로 수정할 모듈 | 검증 |
| --- | --- | --- | --- |
| 목록 UI 변경 | `StationListScreen.kt`, `StationListCards.kt`, `StationListStates.kt`, `core/designsystem/*` | `feature:station-list`, 필요 시 `core:designsystem` | `:feature:station-list:testDebugUnitTest`, 필요 시 `verifyRoborazziDebug` |
| 가격/거리/카드 정보 위계 변경 | `StationListItemUiModel.kt`, `StationListCards.kt`, `core/designsystem/component/*` | `feature:station-list`, `core:designsystem` | station-list UI test, screenshot test |
| 새 설정 추가 | `UserPreferences.kt`, `domain/settings/usecase/*`, `core/datastore/*`, `DefaultSettingsRepository.kt`, `feature/settings/*` | `domain:settings`, `core:datastore`, `data:settings`, `feature:settings` | `:domain:settings:test`, `:data:settings:testDebugUnitTest`, `:feature:settings:testDebugUnitTest` |
| 캐시/stale 정책 변경 | `StationCachePolicy.kt`, `DefaultStationRepository.kt`, `StationSearchResultAssembler.kt`, `core/database/station/*` | `data:station`, `core:database`, 필요 시 `domain:station` | `:data:station:testDebugUnitTest`, `:core:database:testDebugUnitTest` |
| refresh retry 변경 | `StationRetryPolicy.kt`, `DefaultStationRepository.kt`, `StationEvent.kt` | `data:station`, 필요 시 `domain:station` | `:data:station:testDebugUnitTest`, `:domain:station:test` |
| 위치 동작 변경 | `domain/location/*`, `core/location/*`, `LocationStateMachine.kt` | `domain:location`, `core:location`, `feature:station-list` | `:domain:location:test`, `:core:location:testDebugUnitTest`, station-list tests |
| 주소 라벨 표시 변경 | `AddressLabelNormalizer.kt`, `AddressLabelFormatter.kt`, `LocationStateMachine.kt` | `domain:location`, `core:location`, `feature:station-list` | location/domain/core tests |
| network/proxy 변경 | `NetworkRuntimeConfig.kt`, `NetworkStationFetcher.kt`, `ProxyStationFetcher.kt`, `AppConfigModule.kt` | `core:network`, `app` | `:core:network:test`, `:app:testProdDebugUnitTest` |
| watchlist 변경 | `WatchlistViewModel.kt`, `WatchlistScreen.kt`, `WatchlistSummaryAssembler.kt` | `feature:watchlist`, `data:station` | `:feature:watchlist:testDebugUnitTest`, `:data:station:testDebugUnitTest` |
| demo seed 변경 | `tools/demo-seed/*`, `DemoSeedStartupHook.kt`, `app/src/demo/assets/demo-station-seed.json` | `tools:demo-seed`, `app` demo source set | `:tools:demo-seed:test`, `:app:testDemoDebugUnitTest`, 필요 시 connected demo |
| 외부 지도 handoff 변경 | `ExternalMapLauncher.kt`, `StationListEffect.OpenExternalMap`, `GasStationNavHost.kt` | `app`, `feature:station-list` | `:app:testDemoDebugUnitTest`, station-list tests |
| 문서-only 변경 | 바꿀 문서와 실제 코드 앵커 | `docs/*`, 필요 시 `README.md` | `git diff --check`, 링크/파일 존재 확인 |

수정 위치가 애매하면 `docs/module-contracts.md`를 먼저 봅니다. 구조 설명이 필요하면 `docs/architecture.md`, 상태가 헷갈리면 `docs/state-model.md`, 캐시/failure가 헷갈리면 `docs/offline-strategy.md`를 봅니다.

여기부터는 학습을 실제 변경으로 연결하는 구간입니다. 코드 수정 전에는 `docs/project-reading-guide.md`의 목적별 경로와 `docs/agent-workflow.md`의 절차를 다시 확인하고, 변경하려는 계층의 테스트를 먼저 읽습니다.

## 17. 처음 맡은 개발자의 3일 온보딩 루트

### Day 1: 앱을 실행 가능한 구조로 이해하기

1. `AGENTS.md`를 읽고 작업 원칙을 확인합니다.
2. `README.md`로 제품 목적과 대표 사용자 흐름을 봅니다.
3. 이 가이드를 한 번 끝까지 읽습니다.
4. `settings.gradle.kts`와 `app/build.gradle.kts`로 활성 모듈과 flavor를 확인합니다.
5. `docs/architecture.md`와 `docs/module-contracts.md`로 구조와 금지 의존을 확인합니다.
6. `./gradlew :app:assembleDemoDebug`를 실행해 demo build가 되는지 확인합니다.
7. `GasStationNavHost.kt`에서 route 구조를 따라갑니다.

### Day 2: station list를 끝까지 추적하기

1. `StationListRoute.kt`에서 화면 진입을 봅니다.
2. `StationListViewModel.kt`에서 action, state, effect가 어떻게 나뉘는지 봅니다.
3. `LocationStateMachine.kt`로 위치 상태를 봅니다.
4. `StationSearchOrchestrator.kt`로 query/cache/failure 판단을 봅니다.
5. `DefaultStationRepository.kt`로 observe/refresh/watchlist 조합을 봅니다.
6. `StationSearchResultAssembler.kt`, `StationCachePolicy.kt`, `StationRetryPolicy.kt`를 읽습니다.
7. `docs/state-model.md`와 `docs/offline-strategy.md`를 같이 읽습니다.

### Day 3: 작은 변경 하나를 안전하게 해보기

1. 화면 문구, 테스트 정리, 문서 링크 같은 작은 작업을 고릅니다.
2. 관련 테스트를 먼저 읽습니다.
3. 변경 소유 모듈을 정합니다.
4. 최소 수정만 합니다.
5. targeted verification을 실행합니다.
6. 문서가 약속한 흐름이 바뀌었는지 확인합니다.

처음부터 cache policy, location provider, build-logic, benchmark를 크게 바꾸지 않는 편이 좋습니다. 이 영역들은 영향 범위가 넓고, 한 번에 여러 단일 출처 문서와 테스트를 함께 봐야 합니다.

## 18. 첫 버그 수정 절차

첫 버그 수정은 아래 순서로 진행합니다.

```text
재현 -> 소유 모듈 찾기 -> 관련 테스트 읽기 -> 실패 테스트 또는 문서 검증 기준 추가 -> 최소 수정 -> targeted verification -> 문서 영향 확인
```

각 단계를 풀면 다음과 같습니다.

1. 재현합니다. 화면 버그라면 demo에서 재현되는지 먼저 봅니다.
2. 소유 모듈을 찾습니다. 화면 표시 문제인지, domain 규칙인지, data/cache 문제인지 나눕니다.
3. 관련 테스트를 먼저 읽습니다. 현재 계약이 무엇인지 모르면 수정 방향이 흔들립니다.
4. 가능한 경우 실패 테스트를 추가합니다. 문서-only라면 `git diff --check`와 파일 존재 확인처럼 검증 기준을 명확히 합니다.
5. 최소 수정합니다. 주변 리팩터링을 같이 하지 않습니다.
6. targeted verification을 돌립니다.
7. 사용자 흐름이나 모듈 책임 설명이 바뀌었으면 문서를 갱신합니다.

예를 들어 "네트워크 실패 후 목록이 비어 보인다"는 버그가 있다면 UI만 보지 않습니다. `StationSearchOrchestrator`, `StationSearchResult.hasCachedSnapshot`, `DefaultStationRepository.observeNearbyStations()`, `StationCacheDao` snapshot 관찰을 함께 봅니다. 실패해도 기존 snapshot을 유지해야 하기 때문입니다.

## 19. 첫 기능 추가 절차

첫 기능 추가는 화면에서 바로 시작하지 않습니다.

```text
제품 흐름 확인 -> domain 계약 확인 -> data/core 필요성 판단 -> feature state/action/effect 작성 -> app navigation wiring -> demo/prod 영향 확인 -> 테스트와 문서 갱신
```

각 단계의 질문은 아래와 같습니다.

1. 제품 흐름 확인: 사용자가 왜 이 기능을 쓰는가? 가격 비교 속도를 늦추지 않는가?
2. domain 계약 확인: 새 domain model, use case, repository method가 필요한가?
3. data/core 필요성 판단: 저장, 네트워크, 위치, DataStore, Room schema가 바뀌는가?
4. feature 작성: 어떤 UI state, action, effect가 필요한가?
5. app wiring: 새 route, Hilt binding, flavor 연결이 필요한가?
6. demo/prod 영향: demo seed나 prod key/network 경로가 영향을 받는가?
7. 테스트와 문서: 어떤 계층 테스트와 어떤 단일 출처 문서가 바뀌어야 하는가?

설정 항목 추가처럼 단순해 보이는 기능도 domain/settings -> core/datastore -> data/settings -> feature/settings -> station-list query 영향 순서로 봅니다. 화면에 radio row 하나 추가하는 문제가 아니라, 사용자 선택 상태의 저장/관찰/반영 경로를 유지하는 문제입니다.

## 20. 면접/포트폴리오 설명 가이드

이 섹션은 실무 문서와 분리해서 봅니다. 면접에서는 "썼다"보다 "왜 그렇게 나눴고, 어떤 trade-off를 알고 있는가"가 중요합니다.

### 왜 멀티모듈 Clean Architecture를 썼나요?

답변 예시:

> 현재 위치 기반 주유소 비교 앱은 위치, 설정, 캐시, 네트워크, 오프라인 fallback, UI 상태가 한 화면에서 만납니다. 이를 한 모듈이나 한 ViewModel에 몰면 변경 이유가 섞입니다. 그래서 화면은 `feature`, 계약은 `domain`, 저장/원격 조합은 `data`, Android/Room/Retrofit/DataStore 구현은 `core`, 최종 조립은 `app`으로 나눴습니다. 특히 feature가 Room/Retrofit/DataStore를 직접 알지 않게 한 것이 핵심입니다.

### 왜 Compose를 썼나요?

답변 예시:

> station list는 권한, GPS, loading, stale, failure, 목록, watch 상태가 자주 바뀌는 화면입니다. Compose는 `StationListUiState`를 화면으로 투영하기 좋아서 상태 기반 UI에 맞습니다. 대신 side effect를 UI state에 섞지 않도록 snackbar, 외부 지도 열기 같은 반응은 `StationListEffect`로 분리했습니다.

### 왜 Hilt를 썼나요?

답변 예시:

> repository, DAO, remote source, retry policy, event logger 같은 의존성이 많습니다. Hilt를 쓰면 생성자 주입으로 조립을 명확히 하고 flavor별 구현도 app에서 연결할 수 있습니다. 다만 Hilt가 의존 방향을 자동으로 지켜주는 것은 아니므로 `docs/module-contracts.md`와 module boundary test로 feature가 infra를 직접 의존하지 않게 관리합니다.

### 왜 Room에 snapshot table을 따로 뒀나요?

답변 예시:

> `station_cache` 행만으로는 "성공적으로 조회했지만 결과가 0건"과 "아직 캐시가 없음"을 구분하기 어렵습니다. 그래서 `station_cache_snapshot` 마커를 둬서 성공한 빈 결과도 성공으로 기록합니다. UI는 `fetchedAt != null`보다 `StationSearchResult.hasCachedSnapshot`을 기준으로 blocking failure 여부를 판단합니다.

### 왜 DataStore를 썼나요?

답변 예시:

> 반경, 유종, 브랜드 필터, 정렬, 지도 앱 선택은 작은 사용자 설정 상태이고 Flow로 관찰되어야 합니다. DataStore가 이 요구에 맞습니다. 단, storage DTO를 domain model로 노출하지 않고 `data:settings`에서 `UserPreferences`로 매핑해 저장 포맷과 domain 의미를 분리했습니다.

### 왜 demo flavor를 중요하게 보나요?

답변 예시:

> demo는 mock 예외 경로가 아니라 재현 가능한 정식 실행 경로입니다. seed DB, 기본 preferences, 고정 좌표로 항상 같은 시작 상태를 만들기 때문에 README screenshot, UI test, macrobenchmark가 같은 기준을 공유합니다. 그래서 demo가 깨지면 단순 샘플이 깨진 것이 아니라 프로젝트의 검증 기반이 흔들린 것입니다.

### 오프라인 동작은 어떻게 설명하나요?

답변 예시:

> refresh가 실패해도 기존 snapshot은 지우지 않습니다. 캐시가 있으면 stale이나 snackbar로 실패를 알리고 마지막 성공 결과를 유지합니다. 캐시가 없을 때만 blocking failure로 전환합니다. 이때 핵심 기준은 `hasCachedSnapshot`이고, 성공한 빈 결과와 캐시 없음은 다른 상태입니다.

### secret/API key는 어떻게 다루나요?

답변 예시:

> 현재 `prod` direct Opinet 경로에서는 API key가 Android client `BuildConfig`에 들어가므로 완전한 secret boundary는 아닙니다. 이를 문서에서 명시하고, 공개 배포 전에는 backend proxy, key restriction, quota monitoring으로 승격해야 하는 조건을 ADR과 security trade-off 문서에 남겼습니다.

### 성능은 어떻게 측정하나요?

답변 예시:

> demoBenchmark variant와 macrobenchmark로 startup to first content, list scroll, refresh, watchlist 진입을 측정합니다. committed 성능 수치는 emulator가 아니라 physical device 기준으로만 기록합니다. demo flavor가 deterministic하기 때문에 성능 비교가 흔들리지 않습니다.

### 공개 production 전에 다시 볼 trade-off는 무엇인가요?

답변 예시:

> 가장 큰 것은 Opinet API key boundary입니다. Android client에 키가 들어가는 direct mode는 포트폴리오/제한된 실행에는 수용 가능하지만 공개 서비스라면 backend proxy, quota, abuse monitoring, key restriction을 먼저 승격해야 합니다. 그 다음은 실제 사용자 규모에서 cache retention과 benchmark 기준을 다시 검증해야 합니다.

## 21. 자주 실수하는 지점

- 파일시스템에 디렉터리가 있다고 활성 모듈로 판단합니다. 활성 모듈은 `settings.gradle.kts` 기준입니다.
- `app`에 비즈니스 정책을 넣습니다. app은 조립, startup, navigation, handoff를 맡습니다.
- feature에서 Room/Retrofit/DataStore를 직접 호출합니다. feature는 domain use case와 UI state/effect를 중심으로 유지합니다.
- `demo`를 fake path로 취급합니다. demo는 문서, 테스트, benchmark가 기대는 정식 재현 경로입니다.
- 캐시 존재 여부를 `fetchedAt != null`로 판단합니다. 우선 기준은 `StationSearchResult.hasCachedSnapshot`입니다.
- 성공한 빈 결과와 캐시 없음 상태를 같은 empty로 처리합니다. 두 상태는 UI 실패 의미가 다릅니다.
- settings write를 domain use case 없이 repository/DataStore로 직접 연결합니다.
- semantics나 test tag를 정리하면서 대체 접근성/테스트 계약을 만들지 않습니다.
- designsystem에 feature 전용 문구나 화면 상태 분기를 넣습니다.
- 문서를 바꿀 때 실제 코드 앵커를 확인하지 않습니다.
- benchmark 수치를 emulator smoke 결과로 문서에 남깁니다. committed 성능 수치는 physical device 기준입니다.
- 오래된 `docs/superpowers/specs/`나 `docs/superpowers/plans/`의 당시 기준을 현재 계약으로 착각합니다. 현재 기준은 live 문서와 실제 코드입니다.

## 22. 머지 전 체크리스트

- [ ] `git status --short`로 기존 변경을 확인했다.
- [ ] 변경 모듈의 소유 범위가 `docs/module-contracts.md`와 맞다.
- [ ] 활성 모듈 판단을 `settings.gradle.kts` 기준으로 했다.
- [ ] `demo`와 `prod` 영향이 모두 확인됐다.
- [ ] 변경 계층에 맞는 테스트를 골랐다.
- [ ] 문서 변경은 `git diff --check`를 통과했다.
- [ ] 현재 단일 출처 문서가 바뀌어야 하는지 확인했다.
- [ ] UI 변경이라면 가격 우선 정보 위계, 접근성 semantics, test tag 계약을 확인했다.
- [ ] 캐시/failure 변경이라면 성공한 빈 결과와 캐시 없음이 구분되는지 확인했다.
- [ ] benchmark나 성능 수치를 바꿨다면 physical device evidence와 `docs/performance.md`를 확인했다.
