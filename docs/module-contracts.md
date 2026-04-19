# 모듈 계약

이 문서는 "어떤 변경을 어디에 둬야 하는가"를 빠르게 판단하기 위한 경계 문서입니다. 세부 데이터 흐름은 `docs/architecture.md`, 읽기 순서는 `docs/project-reading-guide.md`를 봅니다.

## 공통 규칙

- `app`은 조립과 연결만 담당하고 정책을 소유하지 않습니다.
- `feature:*`는 화면 상태를 만들지만 Room, Retrofit, DataStore를 직접 다루지 않습니다. 읽기와 이벤트는 도메인 계약을 통해 연결하고, 설정 변경처럼 정책이 있는 쓰기는 명시적 도메인 유스케이스를 통해 수행합니다.
- `domain:*`는 계약과 모델을 소유하지만 Android/UI 타입을 노출하지 않습니다.
- `data:*`는 저장과 조합을 담당하지만 화면 상태나 Compose 타입을 만들지 않습니다.
- `core:*`는 여러 모듈이 공유하는 인프라와 값 객체만 둡니다.

## 모듈 인벤토리

| 모듈 | 소유 범위 | 직접 의존 | 이 모듈에 두지 말 것 |
| --- | --- | --- | --- |
| `app` | Hilt 조립, startup hook, navigation, flavor 연결 | `feature:*`, `data:*`, 필요한 `core:*`, `domain:*` | 캐시 정책, 비즈니스 규칙 |
| `feature:station-list` | 목록 화면 상태, 새로고침/권한/GPS 흐름, 주소 라벨 보정, effect | `domain:location`, `domain:station`, `domain:settings`, `core:designsystem`, `core:model` | Room/Retrofit 접근, `core:location` 직접 호출 |
| `feature:settings` | 설정 요약/상세 UI, 항목 선택 액션 | `domain:settings`, `domain:station`, `core:designsystem` | 저장 구현, 네트워크 설정 |
| `feature:watchlist` | watchlist(북마크) 비교 UI | `domain:station`, `core:model`, `core:designsystem` | 현재 위치 조회, refresh 세션 상태 |
| `domain:location` | `LocationRepository`, 위치 permission/result 모델, 위치 조회/availability use case | `core:model` | Android 위치 API, Play services 타입 |
| `domain:settings` | `SettingsRepository`, `UserPreferences`, 관련 use case | `domain:station`, `core:model` | DataStore 구현, Android 타입 |
| `domain:station` | `StationRepository`, 검색/비교 use case, 이벤트 계약, 도메인 모델 | `core:model` | Room entity, Retrofit DTO |
| `data:settings` | `SettingsRepository` 구현 | `domain:settings`, `core:datastore` | Compose 상태 |
| `data:station` | `StationRepository` 구현, 캐시/히스토리/watchlist 조합 | `domain:station`, `core:database`, `core:network`, `core:model` | 화면 전용 UI 모델, 위치 조회 구현 |
| `core:model` | 값 객체와 불변식 | 없음 | 앱 정책 |
| `core:designsystem` | 테마, 색상, 타이포, 카드/배너/탑바 | Compose/Material3 | feature 전용 비즈니스 문구 |
| `core:location` | `domain:location` 구현체, Android 위치 provider, availability flow, 주소 표시 라벨 정규화, `DemoLocationOverride` 계약, repository/provider Hilt 바인딩 | `domain:location`, `core:model` | 목록 카드 배치 정책, flavor별 demo override 바인딩, 위치 도메인 계약 |
| `core:network` | Opinet 서비스, 좌표 변환, fetcher | `core:model`, `domain:station` | 캐시/Room 조합 |
| `core:database` | Room DB, DAO, migration | Room | 도메인 정책 |
| `core:datastore` | DataStore data source, serializer | `domain:settings`, `domain:station` | 화면 상태, 설정 정책 |
| `tools:demo-seed` | demo seed 재생성 CLI | `core:network`, `domain:station`, `core:model` | 앱 런타임 의존 |
| `benchmark` | 매크로벤치마크와 baseline profile | `app` | 기능 구현 |

## 경계가 헷갈릴 때 보는 기준

- 새 설정 항목 추가:
  `domain/settings/model/UserPreferences.kt` -> `core/datastore/*` -> `feature/settings/*`
- 설정 변경 호출 경로 변경:
  `domain/settings/usecase/*` -> `feature/settings/*` 또는 `feature/station-list/*`
- 목록 정렬/필터 규칙 변경:
  `domain/station/model/*` -> `data/station/DefaultStationRepository.kt` -> 필요 시 `feature/station-list/*`
- 위치 조회 계약/구현 변경:
  `domain/location/*` -> `core/location/*` -> 필요 시 `feature/station-list/*`
- 위치 결과를 목록 검색에 연결:
  `feature/station-list/*`에서 `domain:location` 결과로 `StationQuery`를 만들고, `data:station`에는 위치 provider나 `core:location` 타입을 넣지 않음
- 현재 주소 표시 변경:
  지오코더 결과를 행정동 단위로 정규화하는 규칙은 `core/location/*`, 목록 상단에 어떻게 보일지는 `feature/station-list/*`
- 캐시/stale 정책 변경:
  `data/station/StationCachePolicy.kt`와 `core/database/*`
- watchlist 비교 규칙 변경:
  `data/station/DefaultStationRepository.kt`와 `feature/watchlist/*`
- demo 재현 경로 변경:
  `tools/demo-seed/*`, `app/src/demo/assets/demo-station-seed.json`, `app/src/demo/kotlin/*`

## 현재 프로젝트 전제

- 공식 지원 런타임은 `demo`와 `prod` 두 경로뿐입니다.
- `demo`는 예외 경로가 아니라 문서와 테스트가 전제로 삼는 정식 경로입니다.
- 과거 직렬화 포맷이나 폐기된 네트워크 provider 호환은 현재 설계 목표가 아닙니다.
