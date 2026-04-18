# Clean Architecture Boundary Refactor Design

**Date:** 2026-04-19

## Goal

현재 구조에서 눈에 띄는 계층 누수를 줄이되, 사용자 동작은 바꾸지 않는다.

이번 리팩터의 목표는 아래 세 가지다.

- `feature` 계층에서 `SettingsRepository` 직접 접근을 제거한다.
- `StationQuery`에서 검색과 무관한 `mapProvider`를 제거한다.
- `feature:station-list`가 `core:location` 인프라 구현에 직접 묶이지 않도록 `domain:location` 계약 계층을 도입한다.

이 작업은 화면 기능 추가가 아니라 구조 정리다. 권한 요청, GPS 비활성 처리, demo 고정 좌표, stale 캐시 fallback, watchlist 흐름은 현재 동작을 그대로 유지한다.

## Scope

이번 설계는 다음 변경만 포함한다.

- 새 `:domain:location` 모듈 추가
- `:core:location`의 Android/demo 세부사항을 `domain:location` 계약 뒤로 숨기기
- `feature:station-list`에서 `:core:location` 직접 의존 제거
- `domain:settings`에 명시적 update use case 추가
- `feature:settings`, `feature:station-list`에서 repository 직접 접근 제거
- `domain:station.model.StationQuery`에서 `mapProvider` 제거
- 관련 테스트와 문서 갱신

이번 설계는 다음을 포함하지 않는다.

- 권한/GPS UI 흐름 변경
- settings 화면 UX 변경
- watchlist 기능 확장
- Room, Retrofit, DataStore 저장 포맷 변경
- 전체 모듈 구조 재편

## Current Problems

### 1. Feature layer reaches repository directly

`feature:settings`와 `feature:station-list`는 읽기에는 use case를 쓰지만, 쓰기에는 `SettingsRepository`를 직접 호출한다. 이 구조는 화면 계층이 설정 변경 규칙을 직접 소유하게 만든다.

### 2. Search request model contains UI concern

`StationQuery`는 저장소 조회 조건을 표현하는 모델인데 `mapProvider`까지 들고 있다. 하지만 `mapProvider`는 캐시 키나 조회 파이프라인에 쓰이지 않고, 외부 지도 effect를 만들 때만 필요하다.

### 3. Feature depends on infrastructure location module

현재 `feature:station-list`는 `core:location`의 `ForegroundLocationProvider`, `LocationLookupResult`, `LocationPermissionState`, `DemoLocationOverride`를 직접 참조한다. 결과적으로 화면 계층이 위치 인프라와 demo 특수처리를 안다.

## Chosen Approach

추천안으로 합의한 방식은 "외부 동작은 유지하고, 내부 경계만 정리"다.

핵심 방향은 다음과 같다.

- `domain:location`을 새로 만들어 위치 관련 계약과 use case를 둔다.
- `core:location`은 Android 위치 조회, GPS availability 감시, demo override를 감춘 구현 모듈이 된다.
- `feature:station-list`는 더 이상 `core:location`을 보지 않고 `domain:location`만 의존한다.
- 설정 변경은 repository 직접 호출 대신 명시적 use case로 통일한다.
- `StationQuery`는 검색 조건만 담고, 외부 지도 provider는 `UserPreferences`에서 별도로 읽는다.

## Module Changes

### New module: `domain:location`

이 모듈은 위치 관련 application contract를 소유한다.

책임:

- `LocationPermissionState`
- `LocationLookupResult`
- `LocationRepository`
- `GetCurrentLocationUseCase`
- `ObserveLocationAvailabilityUseCase`

의존:

- `core:model`
- coroutine flow

금지:

- Android 타입
- Google Play Services 타입
- demo asset / flavor 구현 세부사항

### Updated module: `core:location`

이 모듈은 위치 인프라 구현을 제공한다.

책임:

- Android 현재 위치 조회
- GPS/Network provider availability 관찰
- demo override 흡수
- `LocationRepository` 구현 바인딩

이 모듈은 `domain:location`에 의존한다. 기존 `ForegroundLocationProvider`, `DemoLocationOverride`, `gpsAvailabilityFlow()` 같은 구성은 외부에 흩어지지 않고 여기서 정리된다.

### Updated module: `feature:station-list`

이 모듈은 화면 상태와 effect만 유지한다.

변경점:

- `core:location` 의존 제거
- `domain:location` 의존 추가
- `Optional<DemoLocationOverride>` 제거
- `effectivePermissionState()` 제거
- 위치 availability 구독은 `ObserveLocationAvailabilityUseCase` 사용
- 현재 위치 조회는 `GetCurrentLocationUseCase` 사용

### Updated module: `domain:settings`

설정 변경 규칙을 화면 밖으로 이동시킨다.

추가 대상:

- `UpdateFuelTypeUseCase`
- `UpdateSearchRadiusUseCase`
- `UpdateBrandFilterUseCase`
- `UpdateMapProviderUseCase`

기존 `UpdatePreferredSortOrderUseCase`는 유지하고, 목록 화면의 sort toggle은 이 use case를 통해 처리한다.

## API Design

### `LocationRepository`

최소 API만 둔다.

- `fun observeAvailability(): Flow<Boolean>`
- `suspend fun getCurrentLocation(permissionState: LocationPermissionState): LocationLookupResult`

이 인터페이스는 "위치 사용 가능 여부"와 "현재 위치 획득 결과"만 노출한다. Android 브로드캐스트, timeout 처리, demo override는 구현 세부사항으로 숨긴다.

### `LocationPermissionState`

권한 상태는 기존 의미를 유지한다.

- `Denied`
- `ApproximateGranted`
- `PreciseGranted`

Route는 OS permission 상태를 이 domain 타입으로 변환만 한다.

### `LocationLookupResult`

현재 결과 의미를 유지한다.

- `Success(Coordinates)`
- `PermissionDenied`
- `Unavailable`
- `TimedOut`
- `Error(Throwable)`

ViewModel의 실패 매핑 로직은 유지하되, 의존 대상만 `domain:location`으로 바뀐다.

### `StationQuery`

`StationQuery`는 검색 조건만 가진다.

포함 필드:

- `coordinates`
- `radius`
- `fuelType`
- `brandFilter`
- `sortOrder`

제거 필드:

- `mapProvider`

외부 지도 provider는 `UserPreferences`에서 직접 읽어 `StationListEffect.OpenExternalMap` 생성 시점에만 사용한다.

## Runtime Flow After Refactor

### Station list screen

1. `StationListRoute`는 OS permission 상태를 `domain:location.LocationPermissionState`로 변환해 ViewModel에 전달한다.
2. ViewModel은 `ObserveLocationAvailabilityUseCase`를 구독해 `isGpsEnabled`를 갱신한다.
3. 새로고침 시 ViewModel은 `GetCurrentLocationUseCase`를 호출한다.
4. `core:location` 구현체는 demo override가 있으면 내부에서 우선 적용하고, 없으면 Android 위치 조회를 사용한다.
5. 성공 시 ViewModel은 `UserPreferences`와 좌표로 `StationQuery`를 만들고 기존 검색/캐시 흐름을 수행한다.
6. 외부 지도 열기 effect는 `preferences.mapProvider`를 직접 사용해 만든다.

### Settings updates

1. `SettingsViewModel`은 관찰에 `ObserveUserPreferencesUseCase`를 유지한다.
2. 액션 처리 시에는 repository 대신 명시적 update use case를 호출한다.
3. `StationListViewModel`의 sort toggle도 repository가 아니라 update use case를 사용한다.

## Error Handling

외부 동작은 바꾸지 않는다.

- 위치 권한이 없으면 기존과 동일하게 snackbar/권한 요청 흐름을 유지한다.
- GPS가 비활성 상태면 기존과 동일하게 위치 설정 열기 effect를 보낸다.
- 위치 timeout, unavailable, generic error는 기존과 같은 `StationListFailureReason`으로 매핑한다.
- refresh 실패와 stale 캐시 fallback 규칙은 바꾸지 않는다.

중요한 점은 오류 의미를 바꾸지 않는다는 것이다. 이번 리팩터는 "누가 그 규칙을 소유하는가"를 바꾸는 작업이다.

## Testing Strategy

### Domain location tests

새 `domain:location` use case는 간단한 위임 테스트만 둔다.

- availability use case가 repository flow를 그대로 노출하는지
- current location use case가 repository 결과를 그대로 전달하는지

### Core location tests

구현 테스트는 현재 behavior를 유지하는 데 집중한다.

- demo override가 있으면 Android 조회보다 우선되는지
- permission state에 따라 적절한 결과를 돌려주는지
- availability 관찰이 GPS/Network provider 변경을 반영하는지

### Feature station list tests

ViewModel 테스트는 다음을 보장해야 한다.

- availability use case를 통해 `isGpsEnabled`가 갱신된다.
- current location use case 성공/실패에 따라 기존과 같은 UI 상태와 effect가 나온다.
- sort toggle은 repository가 아니라 update use case를 사용한다.
- 외부 지도 effect는 여전히 현재 `mapProvider`를 사용한다.
- `StationQuery` 생성 시 더 이상 `mapProvider`를 요구하지 않는다.

### Feature settings tests

- 각 settings action이 대응하는 update use case를 호출하는지
- 관찰된 `UserPreferences`가 기존처럼 `SettingsUiState`로 투영되는지

## File Ownership

예상 수정 범위는 아래와 같다.

- `settings.gradle.kts`
- `docs/architecture.md`
- `docs/module-contracts.md`
- `domain/location/build.gradle.kts`
- `domain/location/src/main/kotlin/...`
- `domain/location/src/test/kotlin/...`
- `core/location/src/main/kotlin/...`
- `core/location/src/test/kotlin/...`
- `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/...`
- `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsViewModel.kt`
- `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsViewModelTest.kt`
- `feature/station-list/build.gradle.kts`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/...`
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/...`
- `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQuery.kt`
- 관련 domain/data 테스트

## Risks and Mitigations

### Risk: behavior regression in demo mode

demo override가 `feature`에서 `core`로 내려가면서 refresh 초기 분기가 달라질 수 있다.

대응:

- demo 우선 처리 테스트를 유지한다.
- 기존 demo 관련 ViewModel 테스트 시나리오를 새 포트 기준으로 다시 고정한다.

### Risk: station-list tests become harder to read

위치 관련 의존이 use case 두 개로 쪼개지면서 테스트 더블이 늘어날 수 있다.

대응:

- feature 테스트용 fake location use case/helper를 도입한다.
- repository 직접 더블 대신 use case 수준 더블로 정리한다.

### Risk: over-modeling location layer

위치 계층을 과하게 추상화하면 단순한 동작이 복잡해질 수 있다.

대응:

- `LocationRepository`는 최소 API 두 개만 유지한다.
- policy 객체를 추가로 만들지 않는다.

## Success Criteria

리팩터 완료 후 아래 조건을 만족해야 한다.

- `feature:settings`와 `feature:station-list`에 `SettingsRepository` 직접 주입이 없다.
- `feature:station-list`가 `core:location`을 직접 의존하지 않는다.
- `StationQuery`에 `mapProvider`가 없다.
- demo/prod의 사용자 동작이 기존과 동일하다.
- 관련 단위 테스트가 갱신되고 통과한다.
