# GasStation Hero Benchmark Evidence Design

> 작성일: 2026-05-17
> 목적: GasStation을 멀티모듈 Clean Architecture Android 앱에서 한 단계 더 나아가, 성능 측정과 운영 판단의 증거까지 갖춘 대표 프로젝트로 정리한다.

## 1. 배경

GasStation은 현재 위치 기반 주유소 비교, demo/prod 정식 경로, Room cache/stale fallback, watchlist, 외부 지도 handoff, CI, screenshot regression, coverage, 운영 문서를 이미 갖추고 있다. 기존 포트폴리오 업그레이드 설계의 핵심 기능도 상당 부분 구현되어 있으므로, 이번 패스의 중심은 새 기능 확장이 아니라 "실무형 Android 설계와 시니어 운영 감각을 어떤 증거로 보여줄 것인가"다.

이번 설계는 GitHub README 첫인상과 시니어 코드 리뷰 대응력을 동시에 겨냥한다. README는 멀티모듈 Clean Architecture, demo/prod, offline/cache/watchlist, CI/검증, 성능 측정 결과를 짧게 보여주고, 깊게 들어오는 사람에게는 benchmark, baseline profile, verification matrix, backend proxy ADR이 근거가 되게 한다.

참고한 공식 Android 문서는 다음이다.

- [Jetpack Compose Hero benchmarks](https://developer.android.com/develop/ui/compose/performance/herobenchmark)
- [Macrobenchmark overview](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
- [Baseline Profiles overview](https://developer.android.com/topic/performance/baselineprofiles/overview)
- [App startup time and reportFullyDrawn](https://developer.android.com/topic/performance/vitals/launch-time)

## 2. 목표

1. README 첫 화면에서 멀티모듈 Clean Architecture, demo/prod 경로, offline/cache/watchlist, CI/검증, hero benchmark 결과가 바로 보이게 한다.
2. GasStation 핵심 사용자 여정을 hero benchmark로 정의하고 실기기 측정값을 남긴다.
3. 첫 usable content 기준으로 `reportFullyDrawn()`을 연결해 startup metric의 의미를 명확히 한다.
4. Baseline Profile 생성 경로를 앱 시작, 목록 표시, 새로고침, watchlist 진입까지 확장한다.
5. backend proxy는 구현하지 않고 ADR로 승격 설계를 남긴다.
6. 필요한 범위에서 station-list와 benchmark helper의 코드 품질을 정리하되, 제품 기능이나 모듈 구조는 흔들지 않는다.

## 3. 비목표

- 새 사용자 기능 대량 추가
- backend proxy 실제 구현
- Firebase Crashlytics, remote analytics 같은 외부 SDK 실제 연동
- 전체 UI 리디자인
- 모듈 재편
- PR CI에 실기기 benchmark를 필수 gate로 추가

## 4. 접근안 비교

### 4.1 추천안: Architecture + Performance Evidence Pack

README, hero benchmark, baseline profile, `reportFullyDrawn()`, proxy ADR을 하나의 증거 패키지로 묶는다. 면접관은 README에서 핵심을 빠르게 보고, 시니어 리뷰어는 benchmark 코드와 검증 문서에서 근거를 확인할 수 있다.

장점:

- 외부 첫인상과 코드 깊이를 동시에 강화한다.
- 기존 benchmark 모듈과 demo flavor를 그대로 활용한다.
- Android 앱 포트폴리오 초점을 유지하면서 운영 판단까지 보여준다.

단점:

- 실기기 측정 환경과 결과 관리 절차를 문서화해야 한다.
- benchmark 안정성을 위해 selector/helper 정리가 필요하다.

### 4.2 Code Quality Deepening Pack

`StationListViewModel` reducer 분리, benchmark DSL, 테스트 fixture 정리처럼 코드 내부 품질을 깊게 다듬는다.

장점:

- 시니어 코드 리뷰에서 구조적 설득력이 커진다.
- 향후 상태 추가와 테스트 작성 비용이 줄어든다.

단점:

- README 첫인상은 상대적으로 약하다.
- 성능과 운영 증거가 부족하면 "잘 정리된 코드"에서 멈출 수 있다.

### 4.3 Security/Operations Pack

backend proxy ADR, release checklist, 보안 trade-off, 관측 이벤트 맵에 집중한다.

장점:

- 공개 배포와 운영 승격 조건을 판단할 줄 안다는 신호가 강하다.
- 현재 client API key 구조의 한계를 더 명확히 설명할 수 있다.

단점:

- 문서 중심으로 보이면 Android 구현 역량보다 계획만 앞서 보일 수 있다.
- proxy 구현까지 가면 Android 프로젝트 초점이 분산된다.

결정: 4.1을 메인으로 채택한다. 4.2는 station-list usable content 판단과 benchmark helper 정리에 필요한 만큼만 포함하고, 4.3은 backend proxy ADR과 release/verification 문서 갱신까지만 포함한다.

## 5. 아키텍처 설계

현재 모듈 경계는 유지한다.

### 5.1 `benchmark`

Hero benchmark의 소유자가 된다. 기존 `coldStartAndOpenWatchlist` 단일 시나리오를 다음 시나리오로 분리한다.

- `startup`: cold start 후 첫 usable content까지 측정
- `listScroll`: 주유소 목록 스크롤 또는 fling 중 frame 안정성 측정
- `refresh`: 새로고침 action 후 화면이 안정화될 때까지 측정
- `openWatchlist`: 목록에서 watchlist 진입과 카드 렌더링 frame 안정성 측정

사용 metric:

- `StartupTimingMetric`: startup TTID/TTFD 계열 측정
- `FrameTimingMetric`: scroll, refresh, navigation frame 안정성 측정
- `TraceSectionMetric`: 앱 내부 trace section을 추가할 때만 사용

`demo` flavor를 기준으로 측정한다. `prod`는 실서버, 실제 위치, 네트워크 상태가 결과에 섞이므로 README 성능 수치의 기준으로 쓰지 않는다.

### 5.2 `app`

Startup 측정 신호의 마지막 연결만 담당한다. `MainActivity` 또는 Compose host가 first usable content 신호를 받아 `reportFullyDrawn()`을 한 번 호출한다. 이 계층에는 검색 정책, 캐시 정책, 화면 상태 판단을 넣지 않는다.

### 5.3 `feature:station-list`

첫 usable content 판단을 제공한다. 기준은 다음 중 하나가 처음 만족되는 시점이다.

- 주유소 카드가 1개 이상 표시됨
- 성공한 empty state가 표시됨
- 캐시가 없는 blocking failure guidance가 표시됨

Loading 상태, 위치 권한 대기, GPS 대기, refresh in progress만으로는 fully drawn으로 보지 않는다. 이 판단은 UI state에서 파생 가능한 작은 policy로 두고, 테스트로 고정한다.

ViewModel이 복잡해질 경우에는 full reducer 추출보다 `StationListFirstContentPolicy` 같은 좁은 pure policy를 우선한다. 이 패스의 목적은 대규모 리팩터링이 아니라 성능 측정 기준을 명확히 하는 것이다.

### 5.4 문서 계층

새 문서 위치:

- `docs/performance.md`: hero benchmark 정의, 실기기 측정 환경, 실행 명령, 결과 표, baseline profile 생성 절차
- `docs/adr/2026-05-18-backend-proxy-escalation.md`: client API key 한계와 공개 배포 시 proxy 승격 설계

기존 문서 갱신:

- `README.md`: 성능 요약 표와 링크
- `docs/verification-matrix.md`: 실기기 opt-in benchmark 명령
- `docs/test-strategy.md`: first usable content와 benchmark 신뢰성 테스트 설명
- `docs/architecture.md`: 필요한 경우 `reportFullyDrawn()` 연결 책임만 짧게 추가

## 6. 성능 측정 흐름

### 6.1 Startup hero

1. Macrobenchmark가 `com.gasstation.demo`를 cold start로 실행한다.
2. demo startup hook이 seed DB와 preferences를 고정한다.
3. demo location이 동일 좌표를 제공한다.
4. 목록 화면이 첫 usable content 상태에 도달한다.
5. app host가 `reportFullyDrawn()`을 호출한다.
6. benchmark 결과에서 startup metric을 수집한다.

측정 결과는 실기기 모델, Android 버전, build variant, 반복 횟수, 측정일과 함께 기록한다.

### 6.2 List scroll hero

Seed 데이터가 표시된 목록에서 실제 card list를 스크롤한다. 목적은 Compose lazy list, metric block, brand icon, price delta 표시가 함께 있는 화면의 frame 안정성을 보는 것이다.

### 6.3 Refresh hero

새로고침 버튼을 누르고 seed 기반 refresh가 완료되어 화면이 다시 안정화되는 시간을 본다. 이 시나리오는 네트워크 성능이 아니라 location/search/cache orchestration과 UI update 비용을 보는 용도다.

### 6.4 Watchlist hero

목록에서 watchlist 화면으로 진입하고 저장 항목 비교 카드가 렌더링되는 흐름을 측정한다. watchlist가 비어 있으면 의미가 약하므로 benchmark setup은 demo seed 또는 UI action을 통해 저장 항목이 존재하는 상태를 만든다.

### 6.5 Baseline Profile

Baseline Profile 생성 경로는 넓게 커버한다.

- 앱 시작
- 새로고침
- 목록 스크롤
- watchlist 진입

측정 benchmark는 좁고 안정적인 시나리오로 유지하고, baseline profile 생성은 실제 사용 경로를 넓게 포함한다. 두 역할을 섞지 않는다.

## 7. 오류 처리와 측정 신뢰성

- 실기기 측정값만 README 성능 수치로 사용한다.
- 에뮬레이터 실행은 smoke 용도로만 문서화한다.
- UI selector를 찾지 못하면 benchmark는 실패해야 한다.
- 실패 메시지는 어떤 selector와 어떤 화면 단계에서 실패했는지 드러내야 한다.
- `reportFullyDrawn()`은 첫 frame이 아니라 first usable content 기준으로 호출한다.
- 단일 숫자만 문서화하지 않고 p50/p95 또는 median/min/max처럼 반복 측정 맥락을 함께 둔다.
- connected benchmark는 PR CI gate로 넣지 않고 실기기 opt-in 검증으로 둔다.
- CI는 기존 static analysis, unit test, screenshot, assemble, coverage 중심을 유지한다.

## 8. 테스트 전략

새 테스트는 이번 설계가 만든 리스크만 막는다.

### 8.1 `feature:station-list`

- 위치 권한 대기는 first usable content가 아님
- GPS 비활성화 대기는 first usable content가 아님
- 캐시 없는 초기 loading은 first usable content가 아님
- empty 결과는 refresh가 끝나야 first usable content임
- station card가 있으면 first usable content임
- 성공한 settled empty state는 first usable content임
- 캐시 없는 blocking failure guidance는 first usable content임
- stale cache with visible stations는 first usable content임 (`stations.isNotEmpty()` 분기로 자동 충족)

### 8.2 `app`

- first usable content 신호가 Activity의 fully drawn reporter로 연결됨
- `reportFullyDrawn()` 또는 equivalent reporter release가 한 번만 발생함

### 8.3 `benchmark`

Hero benchmark helper는 `MacrobenchmarkScope`/UiAutomator에 강하게 의존하므로 이번 패스 범위에서 pure JVM 테스트로 분리하지 않는다. 대신 코드 수준에서 다음 신뢰성 기준을 helper 자체 구조로 보장한다.

- selector 실패 시 메시지에 어떤 selector와 단계인지 포함되도록 helper 안에 `check(...)` 메시지를 둔다.
- demo package name은 `TARGET_PACKAGE` 단일 상수로 노출해 scenario마다 중복하지 않는다.
- scenario helper는 startup, scroll, refresh, watchlist를 별도 함수로 두고, 함수 이름이 측정 대상을 그대로 나타내게 한다.
- frame timing scenario는 `launchStationList()`가 cold start 자동 refresh의 rail이 사라질 때까지 기다린 뒤 반환하도록 하여, refresh/watchlist hero 측정이 잔여 refresh 활동과 섞이지 않게 한다.

Macrobenchmark 자체는 실기기에서 실행한다. Helper의 JVM 단위 추출은 UiAutomator 의존성을 줄이는 별도 follow-up으로 남긴다.

## 9. Backend Proxy ADR 범위

ADR은 구현 계획이 아니라 승격 판단 문서다.

포함할 내용:

- 현재 Android client `BuildConfig.OPINET_API_KEY` 구조의 한계
- 왜 현재 포트폴리오 범위에서는 proxy를 구현하지 않는지
- 공개 배포, quota 비용, abuse risk, key rotation 요구가 생기면 proxy가 필요한 이유
- proxy가 소유할 책임: secret 보관, Opinet 호출, rate limiting, response normalization, cache, monitoring
- Android 앱에 남을 책임: 위치 권한, local cache, settings, UI state, external map handoff
- cleartext Opinet HTTP 제약을 proxy가 HTTPS edge로 감싸는 방향
- Android 모듈 경계 변경 예상: `core:network` runtime config와 repository remote source만 endpoint를 바꾸고 feature/domain 계약은 유지

## 10. 실행 순서

### Phase 1. Performance foundation

- first usable content policy 추가
- `reportFullyDrawn()` 연결
- 관련 unit test 추가

### Phase 2. Hero benchmark expansion

- benchmark scenario 분리
- startup, list scroll, refresh, watchlist 측정 추가
- benchmark helper 실패 메시지 정리
- baseline profile journey 확장

### Phase 3. Evidence docs

- 실기기 benchmark 실행
- `docs/performance.md` 작성
- README startup metric과 performance evidence 섹션 갱신

### Phase 4. Senior judgment docs

- backend proxy ADR 작성
- verification matrix 갱신
- test strategy와 architecture 문서 필요한 부분만 갱신

## 11. 완료 기준

- README startup metric 표가 실제 실기기 측정값으로 대체된다.
- Hero benchmark가 GasStation 핵심 여정 4개 이상을 측정한다.
- Baseline Profile 생성 경로가 앱 시작, 리스트, refresh, watchlist를 포함한다.
- `reportFullyDrawn()`이 first usable content 기준으로 호출된다.
- 성능 측정은 실기기 opt-in 명령으로 문서화되고 PR CI gate와 분리된다.
- Backend proxy ADR이 현재 trade-off와 승격 조건을 명확히 설명한다.
- 관련 unit test, benchmark assemble, 빠른 로컬 verification이 통과한다.

## 12. 리스크와 완화

| 리스크 | 영향 | 완화 |
| --- | --- | --- |
| 실기기 benchmark 결과가 환경에 따라 흔들림 | README 숫자 신뢰도 하락 | 기기 모델, Android 버전, 반복 횟수, 측정일을 함께 기록하고 단일 run 결과를 쓰지 않음 |
| `reportFullyDrawn()`이 너무 일찍 호출됨 | TTFD가 실제 usable state를 반영하지 못함 | first usable content policy를 feature 테스트로 고정 |
| Benchmark selector가 UI copy 변경에 취약함 | 성능 테스트 flake | content description과 test tag 계약을 확인하고 helper 실패 메시지를 명확히 함 |
| Baseline Profile 생성과 benchmark 측정이 섞임 | 결과 해석이 불분명함 | profile generation은 넓은 journey, benchmark는 좁은 scenario로 분리 |
| Proxy ADR이 구현 부재의 변명처럼 보임 | 운영 신호 약화 | 현재 범위에서 구현하지 않는 이유와 승격 조건을 구체적으로 명시 |

## 13. 검증 명령 초안

문서/코드 변경 후 빠른 확인:

```bash
./gradlew \
  :feature:station-list:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :benchmark:assemble
```

실기기 성능 측정:

```bash
./gradlew :benchmark:connectedDebugAndroidTest
```

기존 빠른 로컬 확인:

```bash
./gradlew \
  :core:model:test \
  :domain:location:test \
  :core:observability:test \
  :core:designsystem:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :app:assembleDemoDebug \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :benchmark:assemble
```
