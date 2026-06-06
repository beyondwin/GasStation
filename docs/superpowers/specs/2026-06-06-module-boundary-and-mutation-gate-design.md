# GasStation 모듈 경계 가드 + 변이 게이트 (검증 깊이 II) 설계

> 작성일: 2026-06-06
> 기준 커밋: `fcdcaba`
> 범위: 모듈 경계 코드 가드, domain 계층 변이 테스트 확장, domain:station 변이 회귀 floor 게이트
> 사용자 플로우 영향: 없음 (주유소 비교/watchlist/설정/외부 지도 handoff 동작 불변)
> 짝 구현 plan: `docs/superpowers/plans/2026-06-06-module-boundary-and-mutation-gate.md`

## 목표

직전 `2026-06-06-verification-depth-hardening`(pitest report-only + 의존성 신선도 스캔)을 이어, 검증을 "문서로만 약속된 상태"에서 "코드로 강제되고 회귀를 막는 상태"로 한 단계 더 끌어올린다. 세 가지 독립 트랙으로 구성하며, 각 트랙은 독립적으로 commit 가능한 단위다. **어떤 트랙도 모듈 그래프나 `feature:*`/`domain:*`/`data:*`의 사용자 대면 동작을 바꾸지 않는다.** 이 작업은 기존 설계를 "바꾸는" 것이 아니라 "지키는" 검증만 추가한다.

## 배경: 탐색에서 확인한 사실

2026-06-06 실제 파일 확인 결과:

1. **모듈 경계는 문서에만 존재하고 코드 강제가 없다.** `docs/module-contracts.md`가 "어떤 모듈이 무엇을 소유하면 안 되는가"를 단일 출처로 규정하지만, 위반을 자동으로 잡는 가드가 없다. 경계는 리뷰어의 주의에만 의존한다.
2. **`core:location → domain:location`은 의도된 예외다.** `docs/module-contracts.md:29`·`docs/architecture.md`가 `core:location`을 "위치를 플랫폼 인프라로 둔" 데이터 역할(F1)로 명시한다. `domain:settings`가 `core:model`을 `api`로 노출하는 것(F3, `module-contracts.md:22` "core:model as public API")도 의도된 결정이다. **이 둘은 "고칠 결함"이 아니라 가드가 보존해야 할 규칙이다.**
3. **`gradle.properties`에 `org.gradle.configuration-cache=true`.** 따라서 경계 가드 태스크는 실행 시점에 `Project` 그래프에 접근하면 안 되고, **설정 시점에 의존성 엣지를 String으로 캡처**해야 config-cache 안전하다.
4. **`domain:station` 변이 베이스라인이 안정됐다.** 직전 플랜에서 `docs/test-strategy.md:94` 기준 보강 후 `Killed 28/60 (47%)`, **test strength 97%**, SURVIVED 1(= `StationPriceDelta.from`의 `<` 경계 동등 변이, 추가 테스트로 못 잡는 equivalent mutant). overall %가 낮은 이유는 `no-coverage` 변이 31건 때문이다. → 게이트 승격에 충분한 근거.
5. **`domain:settings`/`domain:location`은 변이 미측정이다.** 둘 다 `gasstation.jvm.library` 순수 JVM 모듈이라 pitest 적용이 가능한데 아직 베이스라인이 없다. 컨벤션 플러그인(`GasStationJvmLibraryConventionPlugin.kt:36`)이 `testImplementation(kotlin-test)`를 주입하고, `domain:station`이 이 설정만으로 pitest를 이미 돌리므로 두 모듈도 동일하게 동작한다.
6. **변이 표면의 비대칭.** `domain:settings`의 use case는 `updateUserPreferences { current.copy(필드 = 인자) }` 형태의 얇은 위임이고 기존 테스트가 도달값·기본값을 이미 단언한다 → SURVIVED 0 기대. `domain:location`은 `AddressLabelNormalizer.kt`에 분기·경계(시/도 토큰 결합, `indexOfLast`, districtIndex 유무 분기)가 몰려 있어 SURVIVED 후보가 집중된다.
7. **현재 그래프는 가드 규칙을 모두 통과한다(GREEN).** 18개 모듈의 `implementation`/`api` 의존을 확인한 결과, 아래 denylist 어떤 규칙도 위반하지 않는다. 즉 가드 도입은 즉시 GREEN이며, 회귀가 생길 때만 RED가 된다.

## 비목표 (Out of Scope)

- **F1/F3를 "고치는" 것.** `core:location`의 데이터 역할, `domain:settings`의 `api` 노출은 의도된 설계다. 가드는 이 둘을 깨지 않도록 규칙에서 제외/보존한다.
- 모듈 그래프 재배선이나 사용자 대면 동작 변경.
- 변이 테스트를 CI PR 게이트에 넣는 것(느리므로 온디맨드 유지).
- `domain:settings`/`domain:location`을 변이 점수 게이트로 강제하는 것(베이스라인 안정화 전까지 report-only).
- Pitest를 Android 모듈로 확장하는 것(불안정).
- 경계 가드를 외부 플러그인(예: Konsist/ArchUnit)으로 구현하는 것(이번엔 의존성 추가 없이 root build의 경량 Gradle 태스크로).

---

## Track C: 모듈 경계 가드 (CI 차단)

**소유:** 루트 `build.gradle.kts`, `.github/workflows/android.yml`, `docs/module-contracts.md`

**문제:** 경계 위반(예: `feature`가 `core:network`를 직접 호출, `data`가 `core:location`에 의존, `domain`이 `data`를 앎)을 자동으로 막는 장치가 없다. 의도된 `core:location → domain:location` 예외는 보존하면서 나머지 위반만 잡아야 한다.

### 설계

- **denylist(금지 엣지) 방식**을 택한다. allowlist(허용 엣지 전수 나열)가 아니라, `(소비 모듈 prefix, 금지 대상 prefix, 사유)` Triple 목록으로 "있어선 안 되는 의존"만 규정한다.
  - **이유:** allowlist는 새 모듈/새 정상 의존이 생길 때마다 갱신해야 해 마찰이 크고, 누락 시 false-positive(정상인데 RED)를 낸다. denylist는 의도된 예외(`core:location → domain:location`)를 소비자 prefix 목록에서 단순 제외하는 것으로 보존할 수 있어 **오탐 0**을 보장한다. 트레이드오프는 "새로운 종류의 나쁜 엣지"를 규칙에 추가하지 않으면 못 잡는다는 점인데, 경계는 천천히 바뀌므로 수용 가능하다.
- **config-cache 안전:** `evaluationDependsOnChildren()`로 자식 프로젝트를 먼저 평가한 뒤, 각 subproject의 `implementation`/`api` 의존을 `ProjectDependency.path`(String)로만 캡처해 immutable `Map<String, List<String>>`에 담는다. 태스크 `doLast`는 이 String 맵만 읽고 `Project` 그래프에 접근하지 않는다.
- **빠르므로 CI에 넣는다.** 의존성 그래프만 보는 태스크라 거의 즉시 끝난다 → `static-analysis` job(`spotlessCheck lint`)에 `verifyModuleBoundaries`를 추가해 PR마다 강제한다.
- 위반 시 위반 엣지와 사유를 모두 모아 `GradleException`으로 실패시킨다(부분 실패가 아니라 전체 목록 제공).

### 산출물

- 루트 `build.gradle.kts`의 `verifyModuleBoundaries` 태스크 + `forbiddenModuleEdges` 규칙.
- CI `static-analysis` job 배선.
- `docs/module-contracts.md`에 가드 명령과 의도된 예외 한 줄.

**완료 기준:** `./gradlew verifyModuleBoundaries`가 현재 그래프(18개 모듈)에서 통과하고, 임시로 금지된 엣지(예: `data:station → core:location`)를 주입하면 사유와 함께 실패한다. config-cache 저장/재사용이 경고 없이 동작한다.

**Track C 검증:**
```bash
./gradlew verifyModuleBoundaries
```

---

## Track A: domain 계층 변이 테스트 확장 (report-only)

**소유:** `domain:settings`, `domain:location`, `docs/test-strategy.md`

**목적:** 변이 테스트를 `domain:station` 한 모듈에서 domain 계층 전체로 넓혀, 각 모듈 테스트의 결함 탐지력 베이스라인을 측정·기록한다. 게이트화 전 단계.

### 설계

- 두 모듈에 `info.solidsoft.pitest`(직전 플랜에서 카탈로그 등록 완료, `libs.plugins.pitest`)를 alias로 적용한다. **카탈로그 변경 없음.**
- **report-only/온디맨드, CI 미포함.** 베이스라인이 안정화되기 전에는 점수로 빌드를 깨지 않는다.
- **살아남은 mutant 보강은 조건부.** 리포트가 SURVIVED를 가리킬 때만, 그 라인에 한정해 기존 동작 계약을 바꾸지 않는 신규 입력 케이스를 추가한다.
  - `domain:settings`: 변이 표면이 좁고(얇은 `copy()` 위임 + 이미 단언된 도달값/기본값) **SURVIVED 0이 정상 경로**라 보강이 거의 불필요할 것으로 예상.
  - `domain:location`: `AddressLabelNormalizer`의 시/도 토큰 set 멤버(광역시/특별자치시/특별자치도)·`indexOfLast`·districtIndex 유무 분기가 SURVIVED 후보. 기존 테스트가 안 덮는 입력만 추가.

### 산출물

- 두 모듈 `build.gradle.kts`의 pitest alias + `pitest {}` 설정.
- `docs/test-strategy.md`에 두 모듈의 변이 점수·test strength·SURVIVED 기록(report-only 명시).

**완료 기준:** `./gradlew :domain:settings:pitest :domain:location:pitest`가 성공하고 리포트가 생성되며, 점수가 문서에 기록된다. 보강 테스트가 있으면 각 모듈 `:test`가 통과한다.

**Track A 검증:**
```bash
./gradlew :domain:settings:pitest :domain:location:pitest
./gradlew :domain:settings:test :domain:location:test
```

---

## Track B: domain:station 변이 회귀 floor 게이트 승격 (온디맨드)

**소유:** `domain:station`, `docs/test-strategy.md`

**목적:** 베이스라인이 확보된 `domain:station`을 report-only에서 보수적 회귀 floor 게이트로 올려, 테스트가 약해지는 회귀를 pass/fail로 판정한다.

### 설계

- `pitest {}`에 `mutationThreshold.set(40)`을 추가한다.
- **40% floor 근거:** overall 47%(test strength 97%)가 베이스라인. 단순히 베이스라인(47%)을 floor로 잡으면 `no-coverage` 변이 31건의 미세 변동이나 동등 변이 1건 때문에 noise로 깨질 수 있다. floor를 **40%로 보수적으로** 잡으면 7%p 마진이 생겨 "테스트가 의미 있게 약해지는 회귀"만 차단하고 noise에는 안 깨진다. floor는 "100% 강제"가 아니라 "회귀 차단"이 목적이다(no-coverage 31 + 동등 변이 1로 100%는 애초에 불가).
- **CI에 넣지 않는다.** 변이 테스트는 느리므로 PR 시간을 해치지 않도록 온디맨드 유지. 게이트는 로컬/온디맨드 실행 시 작동한다.
- `domain:settings`/`domain:location`은 Track A의 report-only를 유지하며, 베이스라인이 안정화된 뒤 동일 패턴으로 별도 승격을 검토한다.

### 산출물

- `domain/station/build.gradle.kts`의 `mutationThreshold` floor.
- `docs/test-strategy.md`의 report-only 문구 → 회귀 floor 게이트 결정으로 갱신.

**완료 기준:** `./gradlew :domain:station:pitest`가 현재 점수(47%)에서 통과하고, floor를 베이스라인 위(예: 60%)로 올리면 "below threshold"로 실패한다.

**Track B 검증:**
```bash
./gradlew :domain:station:pitest
```

---

## 트랙 간 독립성

Track C(경계 가드) / Track A(변이 확장) / Track B(변이 게이트)는 서로 독립적으로 commit 가능하다. 한 묶음으로 batch하지 않는다. 직전 verification-depth-hardening과 동일한 "엄브렐러 스펙, 독립 commit" 패턴을 따른다. 문서 마무리는 별도 commit으로 분리한다.

## 최종 검증 (전체)

```bash
# Track C
./gradlew verifyModuleBoundaries
# Track A
./gradlew :domain:settings:pitest :domain:location:pitest :domain:settings:test :domain:location:test
# Track B
./gradlew :domain:station:pitest
# 회귀 안전망 (기존 fast 경로 + config-cache 호환)
./gradlew spotlessCheck lint verifyModuleBoundaries --continue
./gradlew \
  :domain:location:test :domain:settings:test :domain:station:test :core:model:test \
  :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest \
  :app:testDemoDebugUnitTest :app:testProdDebugUnitTest
```

## 문서 갱신 후보

- `docs/module-contracts.md`: `verifyModuleBoundaries` 강제 + 의도된 `core:location → domain:location` 예외 한 줄.
- `docs/test-strategy.md`: `domain:settings`/`domain:location` 변이 베이스라인, `domain:station` report-only → 회귀 floor 게이트 결정.
- `docs/verification-matrix.md`: `verifyModuleBoundaries`(CI static-analysis), domain 계층 pitest(온디맨드), station floor 게이트 명령.
- `CHANGELOG.md` Unreleased: 경계 가드 + 변이 확장 + station 게이트 승격.
