# 모듈 계약

어디에 무엇을 두고, 어디에 두지 말지 판단한다. 흐름은 `docs/architecture.md`, 파일은 `docs/project-reading-guide.md`다.

## 공통

- `app`은 조립만 한다. 정책을 두지 않는다.
- `feature:*`는 화면 상태를 만든다. Room, Retrofit, DataStore를 직접 다루지 않는다. 설정 쓰기는 명시적 use case다.
- `domain:*`는 계약과 모델만 둔다. Android/UI 타입을 노출하지 않는다.
- `data:*`는 저장과 조합을 한다. Compose 상태를 만들지 않는다.
- `core:*`는 여러 모듈이 쓰는 인프라와 값 객체만 둔다.
- 경계는 `verifyModuleBoundaries`(CI `static-analysis`)가 막는다. 18개 활성 모듈의 직접 의존은 `config/quality/production-dependency-policy.txt`의 exact row다. wildcard는 없다. 의도된 예외는 `core:location → domain:location` 한 줄이다.
- 공개 ABI owner는 `core:model`, `core:observability`, `domain:location`, `domain:settings`, `domain:station`이다. `verifyPublicApiBoundaries`가 Android/Compose/Room/Retrofit/DataStore 타입 누수를 막는다.

## Exact public ABI mappings

```text
:core:model|core/model/api/model.api
:core:observability|core/observability/api/observability.api
:domain:location|domain/location/api/location.api
:domain:settings|domain/settings/api/settings.api
:domain:station|domain/station/api/station.api
```

## 인벤토리

| 모듈 | 소유 | 직접 의존 | 두지 말 것 |
| --- | --- | --- | --- |
| `app` | 조립, startup, navigation, flavor, 외부 앱, endpoint 모드 선택 | `feature:*`, `data:*`, 필요한 `core:*`/`domain:*` | 캐시 정책, 비즈니스 규칙 |
| `feature:station-list` | 위치 generation, 관찰, refresh, FIFO, 순수 투영, 얇은 ViewModel | `domain:location`, `domain:station`, `domain:settings`, `core:designsystem`, `core:model` | Room/Retrofit, `core:location` 직접 호출, ViewModel에 동시성 재집중 |
| `feature:settings` | 설정 요약/상세 UI | `core:model`, `domain:settings`, `core:designsystem` | 저장 구현, 네트워크 |
| `feature:watchlist` | 관심 비교 UI | `domain:station`, `domain:settings`, `core:model`, `core:designsystem` | 위치 조회, refresh session |
| `domain:location` | 위치 계약, use case | `core:model` | Android 위치 API |
| `domain:settings` | `UserPreferences`, use case | `core:model` as public API | DataStore, Android 타입 |
| `domain:station` | 검색/비교, `WatchMutationResult`, `StationEvent` | `core:model` | Room entity, Retrofit DTO, SDK |
| `data:settings` | `SettingsRepository` 구현 | `domain:settings`, `core:datastore` | Compose 상태 |
| `data:station` | 저장소 구현, 캐시/히스토리/관심, retry, latest-watch-intent | `domain:station`, `core:observability`, `core:database`, `core:network`, `core:model` | 화면 command, 위치 구현 |
| `core:model` | 값 객체, 공유 enum | 없음 | 앱 정책 |
| `core:observability` | CrashReporter 계약 | 없음 | 화면 상태, SDK 구현 |
| `core:designsystem` | 테마, 토큰, 공통 primitive, 브랜드 아이콘 | Compose/Material3, `core:model` | 화면 문구, 검색 정책 |
| `core:location` | 위치 구현, 지오코더, demo override | `domain:location`, `core:observability`, `core:model` | 카드 배치, flavor 바인딩 |
| `core:network` | Opinet/proxy fetcher, 좌표 변환 | `core:model` | 캐시 조합, endpoint 선택 |
| `core:database` | Room, DAO, migration, `INSERT IGNORE` | Room | latest 의도, analytics |
| `core:datastore` | DataStore DTO | Android DataStore | 화면 상태, domain model |
| `tools:demo-seed` | seed CLI | `core:network`, `domain:station`, `core:model` | 앱 런타임 의존 |
| `benchmark` | demo hero benchmark, baseline profile | `app` | 기능 구현 |

## 헷갈릴 때

- 새 설정: `UserPreferences` → datastore DTO → settings repository → `feature:settings`
- 설정 쓰기: `domain/settings/usecase/*` → feature
- 정렬/필터: `domain/station/model/*` → `DefaultStationRepository` → 필요 시 station-list
- 위치: `domain:location` → `core:location` → station-list
- 주소를 목록에 연결: station-list가 `StationQuery`를 만든다. `data:station`에 위치 타입을 넣지 않는다
- 주소 표시: 정규화는 `AddressLabelNormalizer`, Android 변환은 `core:location`, 배치는 station-list
- 목록 상태: generation은 `LocationStateMachine`, 관찰은 orchestrator, refresh는 coordinator, FIFO는 queue, 투영은 assembler, 연결만 ViewModel
- 브랜드 아이콘: `Brand`/`BrandFilter`, `BrandIcon.kt`, `BrandLabels.kt`. 노출 정책은 각 feature
- 캐시/stale: `StationCachePolicy`, `core:database`
- 재시도: `StationRetryPolicy`, `DefaultStationRepository`, 이벤트는 `StationEvent`
- endpoint: `NetworkRuntimeConfig`, `NetworkModule`, `ProxyStationFetcher`, 선택은 `AppConfigModule`
- 이벤트: `StationEvent`, `CrashReporter`, 앱 flavor analytics
- 관심 비교: `DefaultStationRepository`, `WatchlistSummaryAssembler`, `feature:watchlist`
- watch 순서: `WatchMutationResult`, `LatestWatchIntentGate`, `WatchedStationDao`
- demo seed: `tools/demo-seed`, `demo-station-seed.json`, `app/src/demo`

<!-- station-list-state-contract-ref -->[상태 모델의 구조화된 station-list 계약](state-model.md#station-list-결정적-상태-계약)

## 전제

- 공식 런타임은 `demo`와 `prod`뿐이다.
- `demo`는 예외 경로가 아니다.
- 과거 직렬화나 폐기된 provider 호환은 목표가 아니다.
