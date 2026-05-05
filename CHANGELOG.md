# Changelog

이 문서는 사용자와 리뷰어가 버전별로 무엇이 바뀌었는지 빠르게 확인할 수 있도록 유지합니다.

## 1.0.1 - 2026-05-05

### 사용자 영향

- `prod` 실행 경로의 API key 안내와 실패 조건을 정리해, 키 누락 상태가 더 명확하게 드러나도록 했습니다.
- 상태 표시줄과 내비게이션 바가 GasStation 테마 색상과 맞게 적용되도록 앱 chrome을 정리했습니다.
- 설정 저장 경로를 정리해 저장소와 DataStore 사이의 책임을 분리하고, 알 수 없는 저장 enum 값은 기본 설정으로 안전하게 fallback합니다.

### 개발자 영향

- Android library와 Compose library Gradle convention이 공통 unit/UI test 의존성을 소유하도록 정리해 모듈별 build file 중복을 줄였습니다.
- `core:datastore`가 `domain:settings`에 의존하던 예외를 제거하고 storage-local DTO를 도입했습니다.
- API 33+ Geocoder callback 경로를 실제 기기/에뮬레이터에서 확인하는 `AndroidAddressResolverDeviceTest` smoke test를 추가했습니다.
- app system bar 정책, DataStore serializer, settings repository mapper, feature settings 경로에 대한 targeted test coverage를 보강했습니다.

### 문서

- README, 아키텍처, 모듈 계약, 상태 모델, 테스트 전략, 검증 매트릭스, 개선 backlog 문서를 현재 구현 기준으로 갱신했습니다.
- 상세 릴리즈 노트는 [docs/release-notes/2026-05-05-v1.0.1.md](docs/release-notes/2026-05-05-v1.0.1.md)를 봅니다.

### 검증

- `git diff --check`
- secret assignment scan
- `./gradlew :domain:settings:test :core:datastore:testDebugUnitTest :data:settings:testDebugUnitTest :feature:settings:testDebugUnitTest :app:testDemoDebugUnitTest --tests com.gasstation.SystemBarPolicyTest`
- `./gradlew :app:assembleDemoDebug`

## 1.0.0 - 2026-04-18

- 현재 위치 기반 주유소 탐색, stale cache fallback, watchlist 비교, 외부 지도 handoff, demo/prod flavor 경로를 갖춘 초기 reference 앱 기준선입니다.
