# 주니어 개발자를 위한 GasStation 인수인계 가이드

이 문서는 GasStation을 처음 인수인계받는 주니어 Android 개발자가 제품 목적, 프로젝트 구조, 기술 선택 이유, 실제 로직 흐름, 수정 위치, 검증 방법을 한 번에 따라갈 수 있도록 돕는 온보딩 핸드북입니다.

기존 문서의 단일 출처를 대체하지 않습니다. 세부 계약은 `docs/module-contracts.md`, 구조와 런타임 흐름은 `docs/architecture.md`, 상태는 `docs/state-model.md`, 오프라인 정책은 `docs/offline-strategy.md`, 테스트와 명령은 `docs/test-strategy.md`와 `docs/verification-matrix.md`를 우선합니다.

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

각 레이어를 주니어 관점에서 풀면 다음과 같습니다.

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

## 8. 목록 화면 흐름

## 9. 데이터 흐름: Opinet, proxy, 좌표 변환, Room snapshot

## 10. demo/prod flavor 차이

## 11. 설정 화면 흐름

## 12. watchlist 흐름

## 13. 오프라인, stale, failure 처리

## 14. 디자인 시스템과 UI 정보 위계

## 15. 테스트 전략과 검증 명령

## 16. 작업 유형별 수정 위치

## 17. 처음 맡은 개발자의 3일 온보딩 루트

## 18. 첫 버그 수정 절차

## 19. 첫 기능 추가 절차

## 20. 면접/포트폴리오 설명 가이드

## 21. 자주 실수하는 지점

## 22. 머지 전 체크리스트
