# Agent Workflow

이 문서는 GasStation에서 새 기능을 추가하거나 기존 기능을 수정할 때 에이전트가 따라야 할 실전 절차를 정리한다. 짧은 원칙은 `AGENTS.md`, 구조 설명은 `docs/architecture.md`, 모듈 경계는 `docs/module-contracts.md`를 기준으로 한다.

## Working Model

GasStation은 clean architecture에 가까운 멀티모듈 Android 앱이다. 작업은 화면 파일 하나를 고치는 방식보다 "계약 -> 구현 -> 상태 -> 화면 -> 검증" 순서로 보는 편이 안전하다.

기본 흐름:

1. 변경 목적을 한 문장으로 정한다.
2. 관련 문서와 테스트를 먼저 찾는다.
3. 어떤 모듈이 정책을 소유하는지 결정한다.
4. domain 계약이나 모델 변경이 필요한지 확인한다.
5. data/core 구현 변경이 필요한지 확인한다.
6. feature의 UI state, action, effect, screen을 조정한다.
7. demo/prod 경로와 테스트 범위를 확인한다.
8. 문서가 약속한 동작과 달라졌다면 문서를 갱신한다.

## Before Any Change

작업 시작 전에 아래를 확인한다.

- `git status --short`로 기존 사용자 변경을 확인한다.
- 실제 활성 모듈은 `settings.gradle.kts` 기준으로 판단한다.
- 관련 테스트 파일을 먼저 읽고 현재 계약을 파악한다.
- 새 dependency를 추가하기 전에 같은 계층의 기존 패턴을 찾는다.
- UI 작업이면 `core:designsystem` 토큰과 공통 component를 먼저 확인한다.
- 상태/캐시 작업이면 `docs/state-model.md`와 `docs/offline-strategy.md`를 먼저 읽는다.

## Module Placement

### app

여기에 둔다:

- `App`, `MainActivity`
- top-level navigation
- Hilt composition root
- startup hook 연결
- flavor별 binding
- 외부 지도 launcher

여기에 두지 않는다:

- 캐시 정책
- 정렬/필터 비즈니스 규칙
- 화면별 UI state reducer
- Room, Retrofit, DataStore 조합 로직

### feature

여기에 둔다:

- Route, Screen, ViewModel
- UI state, action, effect
- 화면 전용 UI model
- permission/GPS/refresh 같은 presentation flow
- accessibility semantics와 test tag 계약

여기에 두지 않는다:

- Room DAO 직접 호출
- Retrofit service 직접 호출
- DataStore 직접 호출
- Android 위치 provider 직접 호출
- repository 구현 세부사항

### domain

여기에 둔다:

- repository interface
- use case
- 도메인 모델
- 순수 정책과 계산 규칙
- 실패 reason이나 event 계약

여기에 두지 않는다:

- Android framework 타입
- Compose 타입
- Room entity
- Retrofit DTO
- DataStore serializer

### data

여기에 둔다:

- repository 구현
- local/remote source 조합
- mapper
- cache/stale/watchlist fallback 구현
- 저장소 단위 오류 매핑

여기에 두지 않는다:

- 화면 문구
- Compose 상태
- navigation effect
- permission UI 분기

### core

`core:*`는 여러 모듈이 공유하는 기반이다.

- `core:model`: 값 객체와 불변식
- `core:designsystem`: 테마, 토큰, 공통 UI primitive
- `core:database`: Room DB, DAO, migration
- `core:network`: Opinet service, DTO, coordinate transform, network fetcher
- `core:datastore`: preferences data source와 serializer
- `core:location`: `domain:location` 구현, Android 위치 provider, 주소 라벨 정규화

`core`가 앱 정책을 흡수하기 시작하면 먼저 `domain` 또는 `data` 소유가 맞는지 확인한다.

## Adding A Feature

새 기능은 다음 순서로 설계한다.

1. 사용자 흐름이 기존 route 안에 들어가는지, 새 route가 필요한지 결정한다.
2. 새 도메인 개념이 있으면 `domain:*` 모델과 use case부터 정의한다.
3. 저장, 원격 조회, 캐시 조합이 필요하면 `data:*` 또는 `core:*` 구현 위치를 정한다.
4. feature에는 action, state, effect, UI model을 만든다.
5. navigation 연결은 마지막에 `app`에서 조립한다.
6. demo 경로가 필요한 기능이면 seed, startup hook, UI test 영향을 확인한다.
7. 테스트는 domain/data/core 단위 계약부터 막고 feature test로 사용자 흐름을 확인한다.

새 기능이 단순 UI 표시처럼 보여도, 값의 기준 원천이 domain/data인지 feature 파생 상태인지 먼저 구분한다.

## Modifying Existing Behavior

기존 동작을 바꿀 때는 먼저 현재 소유자를 찾는다.

- 정렬/필터: `domain:station` 모델과 `data:station/DefaultStationRepository.kt`
- 위치 조회: `domain:location` 계약과 `core:location` 구현
- 주소 표시: `core:location` 정규화와 `feature:station-list` 표시 정책
- 설정 저장: `domain:settings` use case, `data:settings`, `core:datastore`
- cache/stale: `data:station/StationCachePolicy.kt`, `core:database`
- watchlist 비교: `data:station/DefaultStationRepository.kt`, `feature:watchlist`
- 외부 지도: `app/map/ExternalMapLauncher.kt`, `StationListEffect.OpenExternalMap`

소유자가 둘 이상이면 "정책은 domain/data, 표시와 interaction은 feature" 기준으로 나눈다.

## UI And Design Work

UI 작업은 station list를 기준 화면으로 본다. 이 화면이 가격, 거리, freshness, permission, GPS, refresh, watch, external map을 모두 포함하기 때문이다.

순서:

1. `core:designsystem`의 color, typography, spacing, component를 먼저 확인한다.
2. feature 내부에 중복된 metric, supporting block, status surface가 있으면 shared primitive 후보인지 판단한다.
3. 가격은 첫 번째 시선, 거리는 두 번째 판단 기준으로 유지한다.
4. station list card에서는 브랜드 텍스트보다 브랜드 아이콘 중심 계약을 유지한다.
5. watchlist에서는 저장 항목 식별을 위해 브랜드 label 표시 계약을 유지한다.
6. 상태 화면은 permission, GPS, loading, empty, blocking failure가 같은 guidance system처럼 읽히게 한다.
7. semantics, content description, test tag를 제거할 때는 대체 테스트를 함께 만든다.

UI 변경 후에는 screenshot/readme story가 portfolio-review speed에서 여전히 읽히는지 확인한다.

## Settings Changes

새 설정 항목이나 설정 동작 변경은 다음 경로를 따른다.

1. `domain/settings/model/UserPreferences.kt`
2. 필요한 `domain/settings/usecase/*`
3. `core/datastore/*` serializer와 data source
4. `data/settings/DefaultSettingsRepository.kt`
5. `feature/settings/*`
6. 설정이 목록 조회에 영향을 주면 `feature/station-list/*`
7. 테스트와 문서 갱신

feature가 `SettingsRepository`를 직접 호출하지 않게 유지한다. 설정 쓰기는 명시적 update use case를 통한다.

## Location Changes

위치 경계는 아래 방향을 유지한다.

`feature:station-list -> domain:location -> core:location`

규칙:

- feature는 Android provider나 `core:location` 구현 타입을 알지 않는다.
- OS permission 상태는 route에서 domain 타입으로 변환한다.
- 현재 위치 조회는 refresh 시점에 `GetCurrentLocationUseCase`로 호출한다.
- availability는 foreground 구간에서 `ObserveLocationAvailabilityUseCase`로 관찰한다.
- demo override는 `core:location` 내부 구현 세부사항이어야 한다.

주소 라벨은 검색 입력이 아니라 표시용 context다. 지오코더 정규화는 `core:location`, 목록 상단 배치는 `feature:station-list`가 담당한다.

## Station Search And Cache Changes

목록 검색과 cache/stale 정책은 가장 회귀 위험이 높다.

핵심 기준:

- 캐시 키는 위치 버킷, 반경, 유종 중심이다.
- 브랜드 필터와 정렬은 읽기 모델 단계에서 적용한다.
- 실패해도 기존 스냅샷은 유지한다.
- 성공한 빈 결과와 캐시 없음은 다르다.
- UI 전면 실패 판단은 `hasCachedSnapshot` 의미를 기준으로 한다.

변경 전 확인 파일:

- `domain/station/model/StationQuery.kt`
- `domain/station/model/StationQueryCacheKey.kt`
- `domain/station/model/StationSearchResult.kt`
- `data/station/DefaultStationRepository.kt`
- `data/station/StationCachePolicy.kt`
- `core/database/src/main/kotlin/com/gasstation/core/database/station/*`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`

테스트는 repository, cache policy, ViewModel을 함께 본다.

## Watchlist Changes

watchlist는 현재 목록의 복제 화면이 아니라 저장 항목 비교 화면이다.

규칙:

- 기준 좌표는 navigation argument와 `SavedStateHandle`에서 온다.
- 별도 위치 조회나 refresh 세션 상태를 들고 있지 않는다.
- 저장 항목은 최신 캐시가 없어도 가격 히스토리와 저장된 좌표/브랜드/이름으로 가능한 만큼 복원한다.
- watchlist card는 브랜드 icon과 label을 함께 보여주는 현재 계약을 유지한다.

변경 전 확인 파일:

- `feature/watchlist/WatchlistViewModel.kt`
- `feature/watchlist/WatchlistScreen.kt`
- `domain/station/usecase/ObserveWatchlistUseCase.kt`
- `data/station/DefaultStationRepository.kt`
- `data/station/*Watchlist*Test.kt`

## Demo And Prod

`demo`와 `prod`는 둘 다 정식 경로다.

- `demo`: seed DB 적재, preferences reset, 고정 좌표, 반복 가능한 UI/test/benchmark 경로
- `prod`: 실제 Opinet API key, 실제 위치, 실제 네트워크 경로

새 동작이 사용자 플로우에 보이면 demo에서 재현 가능한지 확인한다. demo seed를 바꾸면 README screenshot, UI test, benchmark 전제도 함께 점검한다.

`prod` 실행에는 `opinet.apikey`가 필요하지만, 자동화는 실서버 상태에 의존하지 않는 방향을 우선한다.

## Testing Selection

작은 변경도 관련 계층의 테스트를 고른다.

- 값 객체/도메인 규칙: `domain:*:test`, `core:model:test`
- 저장소/캐시/watchlist: `data:station:testDebugUnitTest`
- 설정 저장: `domain:settings:test`, `data:settings:testDebugUnitTest`, `core:datastore:testDebugUnitTest`
- 위치: `domain:location:test`, `core:location:testDebugUnitTest`
- UI state와 Compose 계약: 해당 `feature:*:testDebugUnitTest`
- app 조립/flavor/startup: `app:testDemoDebugUnitTest`, `app:testProdDebugUnitTest`
- demo 실제 플로우: `app:connectedDemoDebugAndroidTest`
- benchmark: `benchmark:assemble` 또는 `benchmark:connectedDebugAndroidTest`

정확한 명령 조합은 `docs/verification-matrix.md`를 따른다.

## Documentation Updates

문서 업데이트 기준:

- 모듈 책임이나 의존 방향이 바뀌면 `docs/architecture.md`와 `docs/module-contracts.md`
- 상태 원천이나 lifecycle이 바뀌면 `docs/state-model.md`
- 캐시, stale, refresh 실패, watchlist fallback이 바뀌면 `docs/offline-strategy.md`
- 테스트 의미나 명령이 바뀌면 `docs/test-strategy.md`와 `docs/verification-matrix.md`
- README가 설명하는 대표 사용자 흐름이 바뀌면 `README.md`
- 일회성 기능 설계나 구현 계획은 `docs/superpowers/specs/`와 `docs/superpowers/plans/`

AGENTS.md에는 모든 작업자가 항상 알아야 하는 원칙만 추가한다. 특정 변경 유형에서만 필요한 긴 설명은 이 문서나 전문 문서로 보낸다.

## Final Review Checklist

작업을 마치기 전에 확인한다.

- 새 코드나 문서가 현재 활성 모듈 기준과 맞는가?
- feature가 infra 구현을 직접 알게 되지 않았는가?
- domain이 Android/UI/storage DTO를 노출하지 않는가?
- data가 화면 상태나 문구를 소유하지 않는가?
- demo와 prod 중 하나만 우연히 동작하는 구조가 아닌가?
- 문서에 쓴 사용자 흐름이 테스트로 보호되는가?
- AGENTS.md에 들어간 내용이 정말 모든 작업자에게 필요한가?
