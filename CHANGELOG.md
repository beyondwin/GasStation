# Changelog

이 문서는 사용자와 리뷰어가 버전별로 무엇이 바뀌었는지 빠르게 확인할 수 있도록 유지합니다.

## Unreleased

## 1.5.0 - 2026-08-29

### 사용자 영향

- 주변 목록 신선도는 DB 변경 없이도 시간이 지나면 다시 계산됩니다.
- 연속 새로고침은 나중에 시작한 결과가 이깁니다. 늦게 도착한 이전 요청이 최신 가격을 덮지 않습니다.
- 위치 권한 변경 중 이전 좌표가 뒤늦게 적용되지 않습니다.
- 관심 저장/해제는 마지막 탭만 반영합니다.

### 개발자 영향

- station 데이터 계약(최신 refresh, freshness, atomic snapshot, typed retry, Room schema)과 주변 목록 상태 분리(위치·관찰·refresh·command·projection)를 테스트와 live 문서로 고정했습니다.
- CI는 production/test lint, coverage ratchet, public ABI, sealed mutation, unsigned prod 재현을 차단합니다. tag Release는 이 경로가 모두 성공한 뒤에만 게시됩니다.
- 문서 허브, catalog, 온보딩 4경로를 현재 계약 입구로 묶었습니다.
- PreToolUse hook이 Claude/Codex `tool_input`과 Grok `toolInput`을 같은 정책으로 검사합니다.
- Gradle dependency verification metadata를 제거하고, Ubuntu 24.04 image 후속 `20260823.283.1`을 recapture 없이 허용합니다.

### 문서와 검증

- README, 배포, 테스트 전략, 검증 매트릭스를 v1.5.0과 현재 CI 표면에 맞췄습니다.
- 상세 릴리즈 노트는 [docs/release-notes/2026-08-29-v1.5.0.md](docs/release-notes/2026-08-29-v1.5.0.md)를 봅니다.

## 1.4.0 - 2026-07-31

### 사용자 영향

- Launcher, themed monochrome, AndroidX splash가 같은 refined-droplet silhouette를 사용하도록 선명도와 mask 여백을 정리했습니다. 모든 지원 Android 버전에서 정적 droplet을 사용해 시작 지연을 피하고, 기존 reduced-motion-safe 종료 전환을 유지합니다.
- 앱이 첫 frame을 준비하면 검정 물방울이 180ms signal pulse로 콘텐츠에 연결됩니다. 시스템 애니메이션을 끈 환경에서는 custom exit를 즉시 제거하고, API 31+의 불필요한 settle 대기를 없애 startup 회귀를 막았습니다.
- edge-to-edge navigation inset을 한 계층에서만 소비하도록 정리해 관심 화면처럼 스크롤되는 root destination에서 하단 여백이 중복되는 문제를 고쳤습니다.

### 개발자 영향

- app icon과 splash의 승인 SVG/PNG provenance, 리소스 계약, API 30/API 37 cold-launch evidence를 저장소 문서와 테스트로 고정하고 benchmark selector를 현재 Compose tag와 맞췄습니다.
- `v*` tag CI는 모든 계약·정적 분석·단위·screenshot·assemble·coverage job이 성공한 뒤 installable demo debug APK, unsigned prod release APK, SHA-256 checksum을 GitHub Release에 자동 게시합니다.
- `scripts/agent/check-contracts.sh --ci`가 release job의 검증 의존성, tag-only 조건, job-scoped `contents: write`, release note와 APK 게시 경로를 보호합니다.

### 문서와 검증

- README, 기여 가이드, 배포 절차, 테스트 전략, 검증 매트릭스를 v1.4.0과 자동 GitHub Release 경로에 맞췄습니다.
- 상세 릴리즈 노트는 [docs/release-notes/2026-07-31-v1.4.0.md](docs/release-notes/2026-07-31-v1.4.0.md)를 봅니다.

## 1.3.0 - 2026-07-25

### 사용자 영향

- 전체 앱을 Urban Signal UI로 전환했습니다. 주변 화면은 가격 우선 flat row, 최저가·건수/평균가·절약액의 2줄 요약, 공통 anchored filter menu, 명시적인 가격 이력 상태를 사용합니다. 관심 화면은 실제 브랜드 로고와 5행 고밀도 비교를, 설정은 flat overview/detail과 단일 `알뜰` 그룹을 사용하며 `자가상표`를 마지막에 둡니다. icon-only `주변·관심·설정` bottom navigation, 200% 글꼴 확장, RTO/RTX/NHO 및 ETC demo seed, Roborazzi와 최신 README 스크린샷을 함께 고정했습니다.
- 주변 필터의 검정 surface를 40dp 높이와 14dp 모서리의 슬림형으로 정리하면서 48dp 터치 영역, 200% 글꼴 확장, 320dp 화면 menu containment, 마지막 chip 끝 여백을 유지했습니다.
- 설정은 DataStore의 첫 값이 준비되기 전 기본값을 화면이나 검색에 사용하지 않고, 저장 성공 뒤에만 선택과 화면 이동을 확정합니다. 주변 목록은 같은 committed 설정으로 query와 refresh를 전환해 화면 간 값 불일치를 막습니다.
- `demo`와 `prod`가 같은 위치 권한 gate를 사용합니다. 앱 진입만으로 권한 dialog를 열거나 demo 고정 좌표로 거부 상태를 우회하지 않으며, 명시적 요청·거부·앱 설정 복구·GPS 안내를 분리합니다.
- 관심 화면은 설정에서 선택한 유종만으로 가격과 변동을 비교하고, 해당 유종 가격이 없을 때도 저장한 주유소 identity를 유지한 채 가격 없음 상태를 표시합니다.
- 외부 지도 설정의 label, 저장 enum, package, URI를 TMAP·카카오맵·네이버 지도로 일치시켰습니다. route 실행 실패 시 Play Store app URI와 HTTPS Store로 순차 fallback하고 최종 실패는 앱이 사용자 feedback으로 처리합니다.

### 개발자 영향

- Gradle 9.6.1, AGP 9.3.0, Kotlin 2.4.10, Compose BOM 2026.06.01, Spotless 8.8.0/ktlint 1.8.0, PIT 1.25.7과 안정 AndroidX/빌드·테스트 의존성을 최신화했습니다. 최신 AndroidX의 compile API 37 요구를 수용하면서 target/Robolectric unit test SDK는 안정 지원 범위인 API 36으로 유지합니다.
- Compose UI 테스트를 공식 v2 테스트 환경 API로 전환하고, deprecated v1 import를 막는 `verifyNoDeprecatedComposeTestApis` 가드와 demo instrumentation test 컴파일을 CI에 추가했습니다.
- Kover 0.9.8의 미해결 Gradle 10 deprecation을 제거하기 위해 커버리지 수집을 최신 안정 JaCoCo 0.8.15로 교체했습니다. `coverageXmlReport`는 전체 JVM/Android unit-test matrix를 실행해 `build/reports/coverage/report.xml`에 통합 결과를 만들며, CI Gradle 경로는 `--warning-mode fail`로 새 deprecation을 차단합니다.
- 최신 ben-manes versions 0.54.0의 Gradle 10 비호환 실행 경로를 제거하고, Gradle 의존성과 GitHub Actions를 매주 확인하는 Dependabot 설정으로 신선도 모니터링을 이전했습니다.
- 멀티모듈 Spotless 검증은 프로젝트 전체 병렬 빌드를 유지하면서 ktlint 실행만 직렬화해 클래스 로더 경쟁으로 인한 간헐 실패를 제거했습니다.
- `StationSearchResult`는 cache snapshot 존재 여부를 명시적으로 받고, flavor별 remote source 선택을 repository 밖의 `FlavorAwareStationRemoteDataSource`로 분리해 data source 경계를 선명하게 했습니다.
- 저장소 작업자는 `scripts/agent/preflight.sh`, `scripts/agent/check-contracts.sh`, `scripts/agent/verify.sh`와 portable hook을 공통 진입점으로 사용합니다. GitHub Actions의 `agent-contracts` job이 같은 계약을 검증합니다.
- 체크인된 demo seed는 키와 네트워크 없이 query matrix, origin/version, 가격 히스토리, portfolio station을 검증하는 `verifyDemoSeedAsset` 경로를 갖습니다.

### 문서와 검증

- README와 architecture, state, offline, module contract, test strategy, verification matrix, deployment 문서를 Urban Signal UI와 설정·권한·watchlist·외부 지도·agent/CI 계약에 맞췄습니다.
- 상세 릴리즈 노트는 [docs/release-notes/2026-07-25-v1.3.0.md](docs/release-notes/2026-07-25-v1.3.0.md)를 봅니다.

## 1.2.0 - 2026-06-07

### 개발자 영향

- v1.2 hardening planning: benchmark selector contracts now use stable Compose test tags exposed as resource IDs, keeping watchlist macrobenchmark selectors separate from Korean accessibility copy.
- Backend proxy readiness: `core:network` now has a proxy endpoint contract and endpoint-mode boundary, selected by `app` via `BuildConfig.STATION_ENDPOINT_MODE`/`PROXY_BASE_URL` (Gradle property `gasstation.stationEndpointMode`/`gasstation.proxyBaseUrl`), while keeping direct Opinet (`direct`) as the default Android path.
- Refresh persistence hardening: `data:station`의 `refreshNearbyStations`는 snapshot 교체, 가격 히스토리 insert/trim, cache prune을 `core:database`의 새 `DatabaseTransactionRunner` 계약으로 단일 트랜잭션 안에서 수행합니다. 부분 실패 시 일관성 깨짐을 막고, 주유소별 `keepLatestTen` 호출을 stationId 기준으로 중복 제거합니다. 출력/동작은 변하지 않습니다.
- Verification depth: `domain:station`에 변이 테스트(pitest, report-only)를 도입하고 `StationPriceDelta.from`/`StationQuery.toCacheKey` 경계 테스트를 보강해 mutation test strength를 70%→97%로 끌어올렸습니다. 당시 의존성 신선도 스캔(ben-manes versions, 비차단 CI job)도 추가했지만 현재는 제거됐습니다. 둘 다 빌드를 깨지 않는 신호 수집용이었습니다. 커버리지 진실성 게이트(Track 1, `koverVerify`)는 Kover 0.9.1↔AGP 9.1.1 호환성 한계로 보류합니다.
- Module boundary guard: `docs/module-contracts.md`의 의도된 모듈 경계를 config-cache-safe한 `verifyModuleBoundaries` Gradle 태스크(denylist)로 고정했습니다. 금지된 production 의존성 엣지(feature→core:location/network/database/datastore, feature/domain→data 등)가 생기면 빌드를 깨고, 의도된 `core:location → domain:location` 예외는 가드에서 제외합니다. CI `static-analysis` job에 포함됩니다.
- Mutation gate promotion: `domain:station` pitest를 report-only에서 `mutationThreshold` 40 floor 게이트로 승격해 점수 하락(현재 47%)을 막습니다. 변이 테스트를 `domain:settings`(report-only baseline)와 `domain:location`로 확장하고, `domain:location`은 `AddressLabelNormalizer`의 fallback 지역/district 선택 로직 갭을 보강해 test strength를 78%→85%로 올렸습니다. `domain:settings` SURVIVED는 전부 coroutine-suspend 등가 변이라 baseline만 기록합니다.
- Boundary validation cleanup: 신뢰할 수 없는 DB/remote 입력은 읽기 경계에서 안전 생성으로 걸러지도록 `MoneyWon.ofOrNull`, nullable station mapping, Opinet 양수 가격 검증을 추가했습니다. 잘못 저장된 캐시 행이나 음수/0 원격 가격이 정상 행 전체를 깨뜨리지 않도록 단위 테스트를 보강했습니다.
- Readability cleanup: `WatchlistSummaryAssembler`는 station 선택, price delta, last-seen 계산을 의도별 helper로 분리했고, `StationPriceDelta`는 variant가 `direction`/`amountWonOrNull`을 직접 소유하도록 정리했습니다. 공개 API와 UI 동작은 유지합니다.
- Release readiness fixes: watchlist fallback now ignores invalid cached rows when calculating history-based deltas, and proxy endpoint mode now validates blank or malformed base URLs before Retrofit construction.

### 문서와 검증

- Build velocity evidence: `docs/build-velocity.md` records timing and current decisions for Gradle parallel/cache/configuration-cache and release assemble gate placement.
- Clean-code round 2 evidence: `docs/improvements/clean-code-improvements-round2-spec.md`와 `docs/improvements/clean-code-improvements-round2-implementation.md`에 DB→domain 읽기 경계, Opinet 가격 검증, watchlist 조립 분리, `StationPriceDelta` 다형성 전환의 근거와 TDD 검증 경로를 기록했습니다.
- Verification depth measurement: `docs/test-strategy.md`에 변이 테스트 섹션을, `docs/verification-matrix.md`에 온디맨드/report-only 검증 깊이 측정 섹션을 추가해 pitest와 dependency 스캔 실행법, Track 1 보류 배경을 단일 출처로 기록합니다.
- Module boundary + mutation gate docs: `docs/module-contracts.md`에 `verifyModuleBoundaries` 강제 규칙을, `docs/test-strategy.md`에 `domain:station`(floor 40)/`domain:settings`/`domain:location` 변이 점수와 SURVIVED 분석을, `docs/verification-matrix.md`에 모듈 경계 가드 명령과 세 모듈 pitest 명령, CI static-analysis 범위를 갱신했습니다.
- 상세 릴리즈 노트는 [docs/release-notes/2026-06-07-v1.2.0.md](docs/release-notes/2026-06-07-v1.2.0.md)를 봅니다.

## 1.1.3 - 2026-05-18

### 개발자 영향

- Hero benchmark evidence: station-list first usable content 기준으로 `reportFullyDrawn()`을 연결하고, startup/list scroll/refresh/watchlist macrobenchmark 경로를 분리합니다. 판단은 `feature:station-list`의 순수 정책 `StationListFirstContentPolicy`로, 보고는 `app`의 `StartupDrawReporter` Compose 훅으로 책임을 나눠 측정 기준이 feature 경계 안에서 단일 출처를 갖도록 했습니다.
- Baseline profile 런타임: `:app`에 `androidx.profileinstaller`를 포함시켜 생성된 baseline profile이 설치된 APK에서 실제로 적용될 수 있게 합니다(생성기와 measurement 경로는 `:benchmark` 모듈에 분리).
- Baseline profile: 앱 시작, 목록 표시, refresh, watchlist 진입을 포함하는 baseline profile journey를 문서화합니다.
- Security operations: Opinet API key를 backend proxy로 승격해야 하는 조건과 Android 영향 범위를 ADR로 기록합니다.

### 문서와 검증

- README "Performance Snapshot"과 `docs/performance.md`는 Samsung Galaxy S20+ 5G (API 33, `demoBenchmark` variant) 측정값(`timeToInitialDisplayMs` p50 347 ms / p95 393 ms 등)을 게시합니다.
- `docs/verification-matrix.md`는 physical-device benchmark를 PR gate가 아닌 opt-in evidence collection으로 분리하고 "Hero Benchmark Evidence" 섹션을 추가합니다.
- `:app`에 `benchmark` build type(`isProfileable=true`, `isDebuggable=false`, debug 키 서명)을 추가해 minified APK를 macrobenchmark가 추적할 수 있게 하고, `:benchmark` 매크로벤치마크 소스를 `com.android.test` 표준 위치인 `src/main/kotlin`으로 이동했습니다(이전 `src/androidTest/`는 컴파일 대상이 아님).
- `benchmark` macrobenchmark의 UiAutomator 대기 한도(`WAIT_TIMEOUT_MS`)를 5초에서 10초로 늘려 실기기 selector 안정성을 확보합니다.
- `docs/performance.md`에 `demoBenchmark` vs `demoDebug` APK 사이즈(2.51 MB / 22.70 MB) 비교를 추가해 R8 minify 효과를 단일 출처로 기록합니다.
- `docs/deployment.md`를 추가해 release branch, 버전 bump, PR/CI, tag push, prodRelease 산출물, signing/secret 경계를 한 곳에서 확인할 수 있게 했습니다.
- `feature:station-list`의 `WatchToggleButton`(`StationListCards.kt`)과 station-list 새로고침 `IconButton`(`StationListScreen.kt`)이 자식 `Icon` 대신 부모 `Modifier.semantics`에 직접 `contentDescription`을 부여하도록 정리했습니다. UX/시각 변화는 없으며 접근성 트리에서 단일 노드로 노출되어 기존 bookmark `IconButton`과 동일한 패턴이 됩니다.
- `benchmark` 매크로벤치마크의 `openWatchlistWithSavedStation`이 `waitForObject` → `click` 사이 Compose recomposition으로 `UiObject2`가 stale이 될 때 selector를 재해석하도록 `clickStable` retry 헬퍼를 도입했습니다.
- 알려진 제약: 위 변경 후에도 `BaselineProfileGenerator`와 `openWatchlistFrameTiming`은 macrobenchmark phase / 디바이스 상태 상호작용으로 인해 실기기에서 일관되지 않은 selector 매치를 보이며 측정 표본을 수집하지 못합니다. baseline profile은 아직 설치하지 않은 상태로 측정합니다. 시도한 3가지 변경과 남은 조사 후보는 `docs/performance.md` "Known Limitations"를 참고하세요.
- 상세 릴리즈 노트는 [docs/release-notes/2026-05-18-v1.1.3.md](docs/release-notes/2026-05-18-v1.1.3.md)를 봅니다.

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
