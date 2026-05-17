# Changelog

이 문서는 사용자와 리뷰어가 버전별로 무엇이 바뀌었는지 빠르게 확인할 수 있도록 유지합니다.

## Unreleased

### 개발자 영향

- Hero benchmark evidence: station-list first usable content 기준으로 `reportFullyDrawn()`을 연결하고, startup/list scroll/refresh/watchlist macrobenchmark 경로를 분리합니다.
- Baseline profile: 앱 시작, 목록 표시, refresh, watchlist 진입을 포함하는 baseline profile journey를 문서화합니다.
- Security operations: Opinet API key를 backend proxy로 승격해야 하는 조건과 Android 영향 범위를 ADR로 기록합니다.

### 문서와 검증

- README "Performance Snapshot"과 `docs/performance.md`는 Samsung Galaxy S20+ 5G (API 33, `demoBenchmark` variant) 측정값(`timeToInitialDisplayMs` p50 347 ms / p95 393 ms 등)을 게시합니다.
- `docs/verification-matrix.md`는 physical-device benchmark를 PR gate가 아닌 opt-in evidence collection으로 분리하고 "Hero Benchmark Evidence" 섹션을 추가합니다.
- `:app`에 `benchmark` build type(`isProfileable=true`, `isDebuggable=false`, debug 키 서명)을 추가해 minified APK를 macrobenchmark가 추적할 수 있게 하고, `:benchmark` 매크로벤치마크 소스를 `com.android.test` 표준 위치인 `src/main/kotlin`으로 이동했습니다(이전 `src/androidTest/`는 컴파일 대상이 아님).
- 알려진 제약: `BaselineProfileGenerator`와 `openWatchlistFrameTiming`은 현재 실기기에서 watchlist 카드 selector 5초 타임아웃으로 실패하므로 baseline profile은 아직 설치하지 않은 상태로 측정합니다. 자세한 내용은 `docs/performance.md` "Known Limitations"를 참고하세요.

## 1.1.2 - 2026-05-14

### 개발자 영향

- Build/test fast path: Roborazzi screenshot 검증을 일반 unit test 경로에서 제외하고, screenshot 회귀는 `verifyRoborazziDebug`가 전담하도록 분리했습니다.
- CI 안정화: `assemble` job의 demo debug, prod debug, benchmark assemble을 별도 Gradle 호출로 나눠 GitHub runner에서 Hilt compile 메모리 피크가 겹치지 않게 했습니다.
- Lint/Compose metrics: 기본 lint는 production source 중심으로 유지하고, test source lint와 Compose compiler report/metric은 명시적 opt-in으로 정리했습니다.
- Test ownership: app resource smoke test는 demo unit test source set으로 옮기고, station route 정책은 빠른 JVM 테스트로 보호합니다.

### 문서와 검증

- README의 현재 버전과 릴리즈 인덱스를 v1.1.2 기준으로 갱신했습니다.
- 검증 매트릭스에 CI assemble job의 메모리 안정화 의도를 반영했습니다.
- 상세 릴리즈 노트는 [docs/release-notes/2026-05-14-v1.1.2.md](docs/release-notes/2026-05-14-v1.1.2.md)를 봅니다.

## 1.1.1 - 2026-05-13

### 개발자 영향

- Clean Architecture 경계: `CrashReporter` 계약을 `domain:station`에서 `core:observability`로 이동해 `core:location`이 station domain을 참조하지 않게 했습니다. 앱은 여전히 flavor별 NoOp/Logcat 구현을 Hilt로 바인딩합니다.
- 주소 라벨 정규화: 행정동 라벨 파싱을 `domain:location/AddressLabelNormalizer.kt`로 올리고, `core:location`과 station list가 같은 정규화 규칙을 쓰도록 정리했습니다.
- 공유 값 객체: 좌표 거리 계산은 `Coordinates.distanceTo`, 브랜드 코드 fallback은 `Brand.fromCode`로 중앙화했습니다.
- Station list UI: `StationListScreen.kt`의 카드, 상태 화면, query context, body state 책임을 별도 파일로 분리하면서 가격 우선 card hierarchy와 semantics 계약은 유지했습니다.
- Station repository: 검색 결과와 watchlist 요약 조립을 `StationSearchResultAssembler.kt`, `WatchlistSummaryAssembler.kt`로 분리하고 repository/test double 파일 크기를 줄였습니다.
- CI: PR에서는 static analysis, unit tests, screenshot tests, debug assemble만 돌리고, `prodRelease` assemble과 coverage는 `main`/`v*` tag push에서 실행하도록 scope를 나눴습니다.

### 문서와 검증

- README, 아키텍처, 모듈 계약, 상태 모델, 테스트 전략, 검증 매트릭스를 `core:observability`, 주소 정규화, station-list/data 분리 구조에 맞췄습니다.
- 검증 기준에 `:core:model:test`, `:domain:location:test`, `:core:observability:test`를 포함해 새 경계와 값 객체 계약을 확인합니다.
- 상세 릴리즈 노트는 [docs/release-notes/2026-05-13-v1.1.1.md](docs/release-notes/2026-05-13-v1.1.1.md)를 봅니다.

## 1.1.0 - 2026-05-11

### 사용자 영향

- 시스템 locale이 영어일 때 주요 UI 문자열이 올바르게 표시됩니다 (i18n: StringResource + en strings 추가).
- 시작 속도 향상은 후속 작업으로 표기합니다. Baseline profile 수집은 AGP 9.1.1과 `androidx.baselineprofile` 1.4.1 인프라 호환성 문제로 이번 릴리즈에서 제외됩니다. 수집 후 적용 시 startup metric이 README의 placeholder 표에 반영됩니다.

### 개발자 영향

- Spotless + ktlint: 전 모듈 코드 스타일 일관성 강제 (convention plugin 적용).
- Lint strict: `warningsAsErrors` 대신 `abortOnError = true`, `checkDependencies = true` 기준으로 전환.
- Roborazzi: 화면 회귀 골든 테스트 9개 추가 (designsystem 5개, station-list 상태 4개). Stale 상태 스냅샷은 CI와 로컬이 같은 기준으로 비교되도록 timezone을 고정합니다.
- CrashReporter: `domain:station` 모듈에 추상화 인터페이스 도입, `app` 모듈이 flavor별 구현(NoOp/Logcat)을 Hilt 바인딩. feature/data/core는 구현에 직접 의존하지 않음.
- Kover 0.9.1: 전 모듈 코드 커버리지 수집 활성화, Hilt/Compose 생성 코드 제외.
- Compose stability metrics: `compose-reports` / `compose-metrics` 출력 4개 모듈 설정.
- Baseline profile: AGP 9.1.1 인프라 호환성 대기 중 (deferred). 준비되면 이 항목을 갱신합니다.
- CI: GitHub Actions workflow를 5개 job으로 분리 — `static-analysis`, `unit-tests`, `screenshot-tests`, `assemble`, `coverage`. Codecov 업로드는 `CODECOV_TOKEN`이 있을 때만 실행되도록 env gate를 사용해 secret 미설정 상태에서도 workflow 파일이 유효합니다.
- i18n: `StringResource` 래퍼 + `en/strings.xml` 추가.
- Repository hygiene: 로컬 `.orchestrator` 실행 산출물을 추적 대상에서 제거하고 `.gitignore`에 추가했습니다.

### 문서

- `docs/security-trade-offs.md` 신설: API key, cleartext HTTP, Android backup, 인증서 피닝, CrashReporter 결정 단일 출처.
- README / AGENTS.md: 인라인 보안 단락을 `docs/security-trade-offs.md` 링크로 대체.
- README: 영문 elevator pitch + 5분 투어 구조로 정돈하고, 현재 버전/릴리즈 노트/CI badge를 v1.1.0 발행 기준으로 맞췄습니다.
- 설계/계획 문서 `docs/history/`로 이동 (단일 계획 참조 구조).
- 제품 정의 단일화 및 작업 지시 문구를 운영 계약 문서로 이동.

### 검증

```bash
./gradlew \
  spotlessCheck lint \
  :app:testDemoDebugUnitTest :app:testProdDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  verifyRoborazziDebug \
  koverXmlReport \
  :app:assembleProdRelease
```

상세 릴리즈 노트는 [docs/release-notes/2026-05-11-v1.1.0.md](docs/release-notes/2026-05-11-v1.1.0.md)를 봅니다.

## 1.0.2 - 2026-05-05

### 개발자 영향

- `core:network`의 `proj4j` 의존성을 Gradle version catalog로 이동해 dependency 선언 방식을 다른 라이브러리와 맞췄습니다.
- GitHub Actions `Verification Matrix`를 `docs/verification-matrix.md`의 머지 전 권장 회귀 세트에 맞춰 `:domain:location:test`, `:app:testProdDebugUnitTest`, `:tools:demo-seed:test`를 포함하도록 보강했습니다. release assemble은 CI 시간과 R8 회귀 필요성에 따라 조건부로 남깁니다.
- `feature:station-list` ViewModel 테스트의 `Dispatchers.Main` 설정을 `MainDispatcherRule`로 중앙화했습니다.
- watchlist Compose 테스트 selector를 ASCII `testTag`로 분리하고, 한글 접근성 문구는 `contentDescription`으로 유지했습니다.

### 문서와 검증

- deep analysis 결과 문서와 개선 backlog를 실제 구현 상태에 맞춰 갱신했습니다.
- `README`, 테스트 전략, 검증 매트릭스, 작업 절차 문서에 이번 pass 이후의 테스트/문서 계약을 반영했습니다.
- 상세 릴리즈 노트는 [docs/release-notes/2026-05-05-v1.0.2.md](docs/release-notes/2026-05-05-v1.0.2.md)를 봅니다.

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

- 현재 위치 기반 주유소 탐색, stale cache fallback, watchlist 비교, 외부 지도 handoff, demo/prod flavor 경로를 갖춘 1.0 기준선입니다.
