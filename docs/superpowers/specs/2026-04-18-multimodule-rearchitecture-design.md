# GasStation Multi-Module Rearchitecture Design

**Date:** 2026-04-18

**Goal**

GasStation의 현재 멀티모듈 전환 상태를 정리해 `app`에 남아 있는 레거시 단일모듈 구조를 제거하고, 각 모듈이 실제 책임과 의존 방향을 일관되게 갖도록 재편한다.

## Current Problems

- `app` 아래에 과거 단일모듈 구조가 대량으로 남아 있다.
  - `ui/*`
  - `viewmodel/*`
  - `domain/*`
  - `data/*`
  - `common/*`
  - `extensions/*`
  - 구 `di/*`
- 새 feature/data/domain/core 구조와 레거시 app 구조가 동시에 컴파일되어, 실제 진입 경로와 죽은 코드가 공존한다.
- `core:network`가 앱 `BuildConfig`를 리플렉션으로 읽고 있어 모듈 경계를 런타임 의존으로 우회한다.
- `core:designsystem`와 `core:ui`는 선언된 모듈 역할에 비해 실체가 약하고, 실제 테마 구현은 여전히 `app`에 남아 있다.
- `core:location`은 계약만 가진 것처럼 보이지만 Android/Google Play Services 의존을 이미 가지고 있고, 실제 구현은 `app`에 있다.
- `domain:settings`가 `domain:station`을 `api`로 재노출하고 있어 도메인 경계가 흐려져 있다.

## Target Architecture

### app

`app`은 composition root와 애플리케이션 진입점만 담당한다.

유지 대상:

- `App`
- 런처 `MainActivity`
- 최상위 navigation
- flavor hook
- 앱 전용 config 제공 DI

삭제 대상:

- `app/ui/*`
- `app/viewmodel/*`
- `app/domain/*`
- `app/data/*`
- `app/common/*`
- `app/extensions/*`
- 구 `app/di/*`

### core:designsystem

`core:designsystem`는 앱 전역 테마와 디자인 토큰의 홈이 된다.

이동 대상:

- `app/ui/theme/*`

역할:

- `GasStationTheme`
- color
- typography
- 공통 Compose 토큰/스타일

결과:

- `app`과 `feature:*`는 앱 내부 theme 패키지를 더 이상 참조하지 않는다.

### core:location

`core:location`은 위치 관련 계약과 Android 구현을 함께 가진다.

유지 및 이동 대상:

- `ForegroundLocationProvider`
- `LocationPermissionState`
- `AndroidForegroundLocationProvider`
- 위치 Hilt 바인딩

결과:

- `app`은 위치 구현 세부사항을 모른다.
- 위치 구현은 feature가 아닌 core 인프라에 귀속된다.

### core:network

`core:network`는 순수 네트워크 클라이언트 계층만 담당한다.

유지 대상:

- Retrofit service
- DTO
- network DI

변경:

- API 키와 환경값은 직접 읽지 않는다.
- 앱이 `BuildConfig` 기반으로 값을 제공하고, `core:network`는 DI로 주입받는다.

삭제 대상:

- `app/data/network/*`
- `app/di/NetworkModule.kt`

### domain

#### domain:station

역할:

- 주유소 탐색 모델
- 정렬/반경/유종/브랜드/지도 선택 모델
- repository contract
- use case

#### domain:settings

역할:

- 설정 조회/수정 contract
- 설정 관련 use case
- settings aggregate인 `UserPreferences`

규칙:

- `domain:settings -> domain:station api` 재노출을 제거한다.
- `settings`가 `station` 모델 일부를 참조하는 것은 허용하지만, 소비자에게 재노출하지 않는다.

### data

#### data:station

역할:

- `StationRepository` 구현
- remote/local data source
- mapper
- cache policy

의존 방향:

- `domain:station`
- 필요한 `core:*`

정리:

- 현재 불필요한 `domain:settings` 의존은 제거한다.

#### data:settings

역할:

- `SettingsRepository` 구현
- datastore 기반 설정 영속화

## Dependency Rules

- `app -> feature`, `data`, 필요한 `core`
- `feature -> domain`, 필요한 `core`
- `data -> domain`, 필요한 `core`
- `domain -> core:model`, `core:common` 정도의 순수 공통 모듈만 허용
- `core`는 `app`을 참조하거나 앱 구현에 기대지 않는다.
- 런타임 리플렉션으로 앱 클래스를 읽어 모듈 경계를 우회하지 않는다.

## File-Level Move Plan

### Move to core:designsystem

- `app/src/main/java/com/gasstation/ui/theme/Color.kt`
- `app/src/main/java/com/gasstation/ui/theme/Theme.kt`
- `app/src/main/java/com/gasstation/ui/theme/Typo.kt`

### Move to core:location

- `app/src/main/java/com/gasstation/location/AndroidForegroundLocationProvider.kt`
- `app/src/main/java/com/gasstation/di/LocationModule.kt`의 위치 provider 바인딩 책임

### Keep in app

- `app/src/main/java/com/gasstation/App.kt`
- `app/src/main/java/com/gasstation/MainActivity.kt`
- `app/src/main/java/com/gasstation/navigation/*`
- `app/src/main/java/com/gasstation/map/ExternalMapLauncher.kt`
- flavor hook:
  - `app/src/demo/kotlin/com/gasstation/DemoLocationModule.kt`
  - `app/src/demo/kotlin/com/gasstation/DemoSeedData.kt`
  - `app/src/prod/kotlin/com/gasstation/ProdSecretsModule.kt`

### Delete from app

- 레거시 `ui/*`
- 레거시 `viewmodel/*`
- 레거시 `domain/*`
- 레거시 `data/*`
- 레거시 `common/*`
- 레거시 `extensions/*`
- 구 `AppModule`
- 구 `PreferenceModule`
- 구 `NetworkModule`

## Implementation Sequence

1. `core:designsystem`에 실제 theme 구현을 옮기고 import를 정리한다.
2. `core:location`에 Android 위치 구현과 바인딩을 옮기고 `app`의 위치 구현을 제거한다.
3. `core:network`에서 앱 `BuildConfig` 리플렉션을 제거하고, 앱 config 주입 방식으로 바꾼다.
4. `domain:settings`의 `domain:station` 재노출을 제거하고, `data:station`의 불필요한 settings 의존을 제거한다.
5. `app/build.gradle.kts`에서 직접 들고 있는 레거시 라이브러리 의존을 줄여 composition root에 맞게 정리한다.
6. `app`의 레거시 소스 트리를 한 번에 제거한다.
7. 최종적으로 전체 DI와 빌드 경로를 점검한다.

## Verification Strategy

각 단계마다 관련 검증을 수행한다.

- 관련 모듈 unit test
- 앱 compile/build
- Hilt binding 중복 또는 누락 확인
- feature 모듈이 `app` 내부 패키지에 더 이상 기대지 않는지 검색

최종 검증:

- 전체 Gradle 테스트
- 앱 빌드 성공
- 새 엔트리포인트만으로 런처 경로가 살아 있는지 확인

## Non-Goals

- 신규 기능 추가
- UI 재디자인
- `ExternalMapLauncher`를 위한 신규 모듈 추가
- 도메인 모델 전체 재설계

## Risks and Mitigations

- 대량 삭제 과정에서 살아 있는 참조를 함께 지울 수 있다.
  - 삭제 전 import/reference 검색으로 실제 참조 여부를 확인한다.
- DI 이동 과정에서 중복 provider 또는 누락이 생길 수 있다.
  - 각 단계 후 compile로 검증한다.
- 도메인 의존 축소 과정에서 테스트 픽스가 필요할 수 있다.
  - 의존 제거는 작은 단계로 나누고 관련 테스트를 바로 돌린다.

## Success Criteria

- `app`은 진입점과 composition root만 남는다.
- `core:designsystem`가 실제 앱 테마 구현을 가진다.
- `core:location`가 위치 구현까지 책임진다.
- `core:network`는 앱 `BuildConfig`를 직접 읽지 않는다.
- 레거시 `app` 단일모듈 구조 파일이 제거된다.
- `domain:settings`의 `domain:station` 재노출이 제거된다.
- 전체 프로젝트가 새 구조로 빌드되고 테스트된다.
