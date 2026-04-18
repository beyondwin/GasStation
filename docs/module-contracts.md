# 모듈 계약

GasStation은 현재 `app / feature / domain / data / core / tools / benchmark` 구조를 실제 빌드 그래프로 사용한다. 이 문서는 각 모듈의 소유 범위와 의존 경계를 고정한다.

## 핵심 규칙

- `app`은 조립과 연결만 담당하고 정책을 소유하지 않는다.
- `feature`는 화면 상태를 만들지만 Room/Retrofit을 직접 만지지 않는다.
- `domain`은 계약과 모델을 소유하지만 Android 타입을 노출하지 않는다.
- `data`는 저장과 조합을 담당하지만 화면 상태를 만들지 않는다.
- `core`는 공유 인프라만 두고 앱 전용 규칙을 넣지 않는다.

## 모듈 지도

| 모듈 | 책임 | 허용 의존 | 하지 말아야 할 일 |
| --- | --- | --- | --- |
| `app` | 앱 시작점, DI 조립, 내비게이션, flavor 연결 | `feature:*`, `data:*`, 필요한 `core:*` | 비즈니스 규칙, 캐시 정책, 화면 상태 조합 |
| `feature:*` | 화면 상태와 사용자 액션 처리 | `domain:*`, 필요한 `core:*` | Room/Retrofit 직접 접근 |
| `domain:*` | 유스케이스, 저장소 계약, 순수 모델 | `core:model` | Android/UI/DB 타입 노출 |
| `data:*` | 저장소 구현, 캐시 조합, 영속화 | `domain:*`, 필요한 `core:*` | 화면 상태 생성 |
| `core:*` | 공유 인프라와 값 객체 | 필요한 최소 공통 의존 | 앱 전용 정책 소유 |
| `tools:demo-seed` | demo seed 생성 | `core:model`, `core:network`, `domain:station` | 앱 런타임 코드 경유 |
| `benchmark` | 성능 측정 | `app` | 기능 구현 |

## 대표 진입 파일

- `app/src/main/java/com/gasstation/MainActivity.kt`
- `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationFetcher.kt`

## 검토 포인트

- 현재 지원 경로는 `demo`와 `prod`뿐이다.
- 레거시 유저/레거시 앱 호환을 위한 직렬화나 네트워크 분기는 유지하지 않는다.
- demo는 고정된 재현 경로이므로 예외가 아니라 공식 지원 경로다.
