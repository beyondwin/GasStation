# Junior Handoff Guide Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Korean junior-developer handoff guide that explains GasStation's product purpose, architecture, technology choices, trade-offs, runtime flows, testing strategy, and interview/portfolio talking points.

**Architecture:** This is a documentation-only change. The new long-form guide lives in `docs/onboarding/junior-handoff-guide.md`; existing source-of-truth docs remain authoritative and are linked rather than duplicated. `README.md` and `docs/project-reading-guide.md` get short navigation links only.

**Tech Stack:** Markdown documentation, existing GasStation Android/Kotlin/Gradle project references, `git diff --check` verification.

---

## File Structure

- Create: `docs/onboarding/junior-handoff-guide.md`
  - Responsibility: Long-form Korean onboarding handbook for junior maintainers.
  - It explains technology choices, code flows, modification entry points, verification, and interview explanations.
- Modify: `README.md`
  - Responsibility: Add one short documentation-map link to the new guide.
- Modify: `docs/project-reading-guide.md`
  - Responsibility: Add the guide as a first-time maintainer entry point.
- Do not modify: `AGENTS.md`
  - Reason: The approved spec says this guide should not expand the operating contract.

## Source Documents To Keep Open

- `docs/superpowers/specs/2026-06-10-junior-handoff-guide-design.md`
- `AGENTS.md`
- `README.md`
- `docs/project-reading-guide.md`
- `docs/architecture.md`
- `docs/module-contracts.md`
- `docs/state-model.md`
- `docs/offline-strategy.md`
- `docs/test-strategy.md`
- `docs/verification-matrix.md`
- `docs/security-trade-offs.md`
- `docs/performance.md`
- `docs/adr/2026-05-18-backend-proxy-escalation.md`
- `settings.gradle.kts`
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`

## Task 1: Create The Onboarding Guide Skeleton

**Files:**
- Create: `docs/onboarding/junior-handoff-guide.md`

- [ ] **Step 1: Check the worktree**

Run:

```bash
git status --short
```

Expected: no output, or only unrelated user changes that must not be touched.

- [ ] **Step 2: Create the onboarding directory and guide skeleton**

Create `docs/onboarding/junior-handoff-guide.md` with this exact top-level structure:

```markdown
# 주니어 개발자를 위한 GasStation 인수인계 가이드

이 문서는 GasStation을 처음 인수인계받는 주니어 Android 개발자가 제품 목적, 프로젝트 구조, 기술 선택 이유, 실제 로직 흐름, 수정 위치, 검증 방법을 한 번에 따라갈 수 있도록 돕는 온보딩 핸드북입니다.

기존 문서의 단일 출처를 대체하지 않습니다. 세부 계약은 `docs/module-contracts.md`, 구조와 런타임 흐름은 `docs/architecture.md`, 상태는 `docs/state-model.md`, 오프라인 정책은 `docs/offline-strategy.md`, 테스트와 명령은 `docs/test-strategy.md`와 `docs/verification-matrix.md`를 우선합니다.

## 1. 이 프로젝트를 한 문장으로 이해하기

## 2. 제품 관점: 사용자가 실제로 무엇을 하는 앱인가

## 3. 전체 구조: 멀티모듈 Clean Architecture

## 4. Gradle 모듈과 레이어별 책임

## 5. 기술 스택 요약표

## 6. 기술별 선정 이유, 장점, 단점, 주의점

## 7. 앱 시작 흐름

## 8. 목록 화면 흐름

## 9. 데이터 흐름: Opinet, proxy, 좌표 변환, Room snapshot

## 10. demo/prod flavor 차이

## 11. 설정 화면 흐름

## 12. watchlist 흐름

## 13. 오프라인, stale, failure 처리

## 14. 디자인 시스템과 UI 정보 위계

## 15. 테스트 전략과 검증 명령

## 16. 작업 유형별 수정 위치

## 17. 처음 맡은 개발자의 3일 온보딩 루트

## 18. 첫 버그 수정 절차

## 19. 첫 기능 추가 절차

## 20. 면접/포트폴리오 설명 가이드

## 21. 자주 실수하는 지점

## 22. 머지 전 체크리스트
```

- [ ] **Step 3: Verify skeleton file exists**

Run:

```bash
test -f docs/onboarding/junior-handoff-guide.md && sed -n '1,80p' docs/onboarding/junior-handoff-guide.md
```

Expected: the file exists and prints the title plus all section headings.

- [ ] **Step 4: Commit the skeleton**

Run:

```bash
git add docs/onboarding/junior-handoff-guide.md
git commit -m "docs: scaffold junior handoff guide"
```

Expected: commit succeeds with only the new guide file.

## Task 2: Write Product, Structure, And Technology Sections

**Files:**
- Modify: `docs/onboarding/junior-handoff-guide.md`

- [ ] **Step 1: Replace sections 1-6 with concrete content**

Write Korean prose for sections 1-6 using the following required facts:

- GasStation is a Korean Android app for comparing nearby gas stations by current location, price, distance, brand, fuel type, watchlist state, and external map handoff.
- Price is the primary information hierarchy.
- `demo` and `prod` are both official runtime paths.
- Active modules must be listed from `settings.gradle.kts`, not from loose directories.
- Layers must be explained as:
  - `app`: Hilt composition, startup hook, navigation, flavor wiring, external map handoff.
  - `feature:*`: route, screen, UI state, UI effect.
  - `domain:*`: repository contracts, use cases, domain models.
  - `data:*`: repository implementation and storage/remote/cache composition.
  - `core:*`: shared infrastructure, values, design primitives, Android implementations.
  - `tools:demo-seed`: repeatable demo seed generation.
  - `benchmark`: macrobenchmark and baseline profile evidence.
- The technology table must include Kotlin, Gradle convention plugins, multi-module Clean Architecture, Compose, Material 3/design system, ViewModel, Coroutines/Flow, Hilt, Room, DataStore, Retrofit/OkHttp/Gson, Navigation Compose, Play Services Location/Geocoder, proj4j, Robolectric, Roborazzi, Macrobenchmark/baseline profile, Kover/Pitest, Spotless/ktlint.

Use this table shape in section 5:

```markdown
| 기술 | 이 프로젝트에서 하는 일 | 선택 이유 | 장점 | 단점/주의점 | 대표 파일 |
| --- | --- | --- | --- | --- | --- |
```

For representative files, use real paths such as:

```markdown
`gradle/libs.versions.toml`, `build-logic/convention/src/main/kotlin/*`, `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`, `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
```

- [ ] **Step 2: Include source-of-truth links**

Add a short "더 깊게 볼 문서" paragraph in section 3 or 4 linking to:

```markdown
- `docs/architecture.md`
- `docs/module-contracts.md`
- `docs/project-reading-guide.md`
```

- [ ] **Step 3: Verify sections 1-6 are present**

Run:

```bash
rg -n "## [1-6]\\. |기술 스택 요약표|멀티모듈 Clean Architecture|settings.gradle.kts|Price" docs/onboarding/junior-handoff-guide.md
```

Expected: output includes section headings, `settings.gradle.kts`, `Price`, and technology stack references.

- [ ] **Step 4: Commit sections 1-6**

Run:

```bash
git add docs/onboarding/junior-handoff-guide.md
git commit -m "docs: explain gasstation onboarding context"
```

Expected: commit succeeds with only `docs/onboarding/junior-handoff-guide.md`.

## Task 3: Write Runtime Flow Sections

**Files:**
- Modify: `docs/onboarding/junior-handoff-guide.md`

- [ ] **Step 1: Replace sections 7-14 with concrete runtime explanations**

Write Korean prose for sections 7-14 using these exact code anchors and facts.

App startup anchors:

```markdown
- `app/src/main/java/com/gasstation/App.kt`
- `app/src/main/java/com/gasstation/startup/AppStartupRunner.kt`
- `app/src/main/java/com/gasstation/MainActivity.kt`
- `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`
```

Station list anchors:

```markdown
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/LocationStateMachine.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationSearchOrchestrator.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListEffect.kt`
```

Data/cache anchors:

```markdown
- `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQuery.kt`
- `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQueryCacheKey.kt`
- `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationSearchResult.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/StationSearchResultAssembler.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/StationCachePolicy.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/StationRetryPolicy.kt`
- `core/database/src/main/kotlin/com/gasstation/core/database/station/StationCacheDao.kt`
```

Network/location anchors:

```markdown
- `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationFetcher.kt`
- `core/network/src/main/kotlin/com/gasstation/core/network/station/ProxyStationFetcher.kt`
- `core/network/src/main/kotlin/com/gasstation/core/network/station/LocalKoreanCoordinateTransform.kt`
- `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfig.kt`
- `domain/location/src/main/kotlin/com/gasstation/domain/location/*`
- `core/location/src/main/kotlin/com/gasstation/core/location/*`
```

Settings/watchlist/demo anchors:

```markdown
- `feature/settings/src/main/kotlin/com/gasstation/feature/settings/*`
- `domain/settings/src/main/kotlin/com/gasstation/domain/settings/*`
- `data/settings/src/main/kotlin/com/gasstation/data/settings/DefaultSettingsRepository.kt`
- `core/datastore/src/main/kotlin/com/gasstation/core/datastore/*`
- `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/*`
- `data/station/src/main/kotlin/com/gasstation/data/station/WatchlistSummaryAssembler.kt`
- `app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt`
- `app/src/prod/kotlin/com/gasstation/startup/ProdSecretsStartupHook.kt`
```

Required behavior explanations:

- `StationListViewModel` composes preferences, location state, search result projection, loading flags, actions, and effects.
- `LocationStateMachine` owns session location state.
- `StationSearchOrchestrator` owns active query, cache snapshot state, observed result, and blocking failure.
- `StationQuery` carries coordinates, radius, fuel type, brand filter, and sort order.
- Cache key includes location bucket, radius, and fuel type.
- Brand filter and sort are read-model concerns.
- `station_cache_snapshot` separates successful empty result from no cache.
- `StationSearchResult.hasCachedSnapshot` is the preferred cache-existence semantic.
- Timeout and network failures retry once in data layer.
- Existing snapshots are preserved on refresh failure.
- `demo` resets DB/preferences and uses fixed coordinates plus seed data.
- `prod` uses the real Opinet key and actual location/network path.
- Watchlist is a saved-station comparison screen, not a copy of the current list.
- Shared design primitives own visual rhythm, while feature modules own screen policy.

- [ ] **Step 2: Link to deep-dive docs**

Add links in sections 7-14 to:

```markdown
- `docs/state-model.md`
- `docs/offline-strategy.md`
- `docs/security-trade-offs.md`
- `docs/adr/2026-05-18-backend-proxy-escalation.md`
```

- [ ] **Step 3: Verify key runtime terms**

Run:

```bash
rg -n "StationListViewModel|LocationStateMachine|StationSearchOrchestrator|hasCachedSnapshot|station_cache_snapshot|DemoSeedStartupHook|ProdSecretsStartupHook|WatchlistSummaryAssembler" docs/onboarding/junior-handoff-guide.md
```

Expected: all key terms appear in the guide.

- [ ] **Step 4: Commit runtime sections**

Run:

```bash
git add docs/onboarding/junior-handoff-guide.md
git commit -m "docs: document gasstation runtime flows"
```

Expected: commit succeeds with only `docs/onboarding/junior-handoff-guide.md`.

## Task 4: Write Workflow, Interview, Mistake, And Checklist Sections

**Files:**
- Modify: `docs/onboarding/junior-handoff-guide.md`

- [ ] **Step 1: Replace sections 15-22 with concrete maintainer guidance**

Write Korean prose for sections 15-22.

Section 15 must link to:

```markdown
- `docs/test-strategy.md`
- `docs/verification-matrix.md`
```

Section 15 must include these commands:

```bash
./gradlew \
  :core:model:test \
  :core:network:test \
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

```bash
git diff --check -- README.md docs/project-reading-guide.md docs/onboarding/junior-handoff-guide.md
```

Section 16 must include a table with this shape:

```markdown
| 변경 유형 | 먼저 읽을 파일 | 주로 수정할 모듈 | 검증 |
| --- | --- | --- | --- |
```

Section 17 must include Day 1, Day 2, Day 3 onboarding routes.

Section 18 must include the sequence:

```markdown
재현 -> 소유 모듈 찾기 -> 관련 테스트 읽기 -> 실패 테스트 또는 문서 검증 기준 추가 -> 최소 수정 -> targeted verification -> 문서 영향 확인
```

Section 19 must include the sequence:

```markdown
제품 흐름 확인 -> domain 계약 확인 -> data/core 필요성 판단 -> feature state/action/effect 작성 -> app navigation wiring -> demo/prod 영향 확인 -> 테스트와 문서 갱신
```

Section 20 must include answer templates for:

- Why multi-module Clean Architecture?
- Why Compose?
- Why Hilt?
- Why Room plus snapshot table?
- Why DataStore?
- Why demo flavor?
- How does offline behavior work?
- How are secrets handled?
- How is performance measured?
- What trade-off should be revisited before public production launch?

Section 21 must include these mistakes:

- Active modules judged from directories instead of `settings.gradle.kts`.
- Business policy added to `app`.
- Room/Retrofit/DataStore called directly from feature modules.
- `demo` treated as a fake path.
- `fetchedAt != null` used as cache existence.
- Semantics or test tags removed without replacement.
- Settings writes bypass domain use cases.
- Documentation updated without checking actual code anchors.

Section 22 must include this checklist:

```markdown
- [ ] `git status --short`로 기존 변경을 확인했다.
- [ ] 변경 모듈의 소유 범위가 `docs/module-contracts.md`와 맞다.
- [ ] `demo`와 `prod` 영향이 모두 확인됐다.
- [ ] 변경 계층에 맞는 테스트를 골랐다.
- [ ] 문서 변경은 `git diff --check`를 통과했다.
- [ ] 현재 단일 출처 문서가 바뀌어야 하는지 확인했다.
```

- [ ] **Step 2: Verify maintainer guidance terms**

Run:

```bash
rg -n "3일 온보딩|첫 버그 수정|첫 기능 추가|면접|자주 실수|머지 전 체크리스트|git diff --check|:app:testDemoDebugUnitTest" docs/onboarding/junior-handoff-guide.md
```

Expected: all workflow sections and verification commands appear.

- [ ] **Step 3: Commit workflow sections**

Run:

```bash
git add docs/onboarding/junior-handoff-guide.md
git commit -m "docs: add handoff workflows and interview guide"
```

Expected: commit succeeds with only `docs/onboarding/junior-handoff-guide.md`.

## Task 5: Add Navigation Links

**Files:**
- Modify: `README.md`
- Modify: `docs/project-reading-guide.md`

- [ ] **Step 1: Add README documentation-map link**

In `README.md`, under `## 문서 지도`, add this bullet immediately after `프로젝트 읽기 가이드`:

```markdown
- [주니어 인수인계 가이드](docs/onboarding/junior-handoff-guide.md): 처음 인수인계받는 개발자를 위해 제품 목적, 기술 선택 이유, 실제 로직 흐름, 수정 위치, 검증 방법을 한 번에 설명합니다.
```

- [ ] **Step 2: Add first-reading entry**

In `docs/project-reading-guide.md`, update the "먼저 볼 문서" list so the new guide appears after `README.md`:

```markdown
1. `AGENTS.md`
2. `README.md`
3. `docs/onboarding/junior-handoff-guide.md`
4. `docs/architecture.md`
5. `docs/module-contracts.md`
6. `docs/agent-workflow.md`
7. `docs/state-model.md`
8. `docs/offline-strategy.md`
9. `docs/test-strategy.md`
10. `docs/verification-matrix.md`
```

Then update the explanatory sentence below it to:

```markdown
이 순서는 "운영 계약 -> 큰 그림 -> 주니어 인수인계 -> 구조 -> 경계 -> 작업 절차 -> 상태 -> 캐시/오프라인 -> 테스트 -> 실행 명령" 순서입니다.
```

- [ ] **Step 3: Add question-router row**

In `docs/project-reading-guide.md`, under "질문별 가장 빠른 진입점", add this row after the operating-principles row:

```markdown
| 처음 인수인계받는 개발자는 무엇부터 보면 되나 | `docs/onboarding/junior-handoff-guide.md`, `README.md`, `docs/architecture.md` |
```

- [ ] **Step 4: Verify links**

Run:

```bash
rg -n "주니어 인수인계 가이드|docs/onboarding/junior-handoff-guide.md" README.md docs/project-reading-guide.md
```

Expected: output includes one README link, one first-reading list item, and one question-router row.

- [ ] **Step 5: Commit navigation links**

Run:

```bash
git add README.md docs/project-reading-guide.md
git commit -m "docs: link junior handoff guide"
```

Expected: commit succeeds with only `README.md` and `docs/project-reading-guide.md`.

## Task 6: Final Verification And Cleanup

**Files:**
- Verify: `docs/onboarding/junior-handoff-guide.md`
- Verify: `README.md`
- Verify: `docs/project-reading-guide.md`

- [ ] **Step 1: Run documentation whitespace verification**

Run:

```bash
git diff --check -- README.md docs/project-reading-guide.md docs/onboarding/junior-handoff-guide.md
```

Expected: no output and exit code 0.

- [ ] **Step 2: Confirm guide has no unfinished markers**

Run:

```bash
rg -n "T[B]D|T[O]DO|F[I]XME|place[h]older|작성 예정|나중에" docs/onboarding/junior-handoff-guide.md
```

Expected: no output and exit code 1.

- [ ] **Step 3: Confirm referenced source files exist**

Run:

```bash
test -f settings.gradle.kts
test -f gradle/libs.versions.toml
test -f app/build.gradle.kts
test -f app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt
test -f feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt
test -f feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationSearchOrchestrator.kt
test -f data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt
test -f core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationFetcher.kt
test -f app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt
test -f app/src/prod/kotlin/com/gasstation/startup/ProdSecretsStartupHook.kt
```

Expected: no output and exit code 0.

- [ ] **Step 4: Confirm no AGENTS.md change**

Run:

```bash
git diff --name-only HEAD~5..HEAD | rg '^AGENTS\.md$'
```

Expected: no output and exit code 1. If the number of implementation commits differs, compare against the branch base or inspect `git status --short` and `git log --name-only`.

- [ ] **Step 5: Inspect final status**

Run:

```bash
git status --short
```

Expected: no output.

- [ ] **Step 6: Report final result**

Final report should include:

- Created guide path: `docs/onboarding/junior-handoff-guide.md`
- Updated links: `README.md`, `docs/project-reading-guide.md`
- Verification commands run and results
- Any skipped Gradle tests with the reason: documentation-only change

## Self-Review

Spec coverage:

- `docs/onboarding/junior-handoff-guide.md` creation is covered by Tasks 1-4.
- README link update is covered by Task 5.
- `docs/project-reading-guide.md` link update is covered by Task 5.
- No `AGENTS.md` edits are enforced by Task 6.
- Technology choices, trade-offs, runtime flows, testing, change entry points, onboarding route, bug/feature procedures, interview guide, mistakes, and checklist are covered by Tasks 2-4.

Placeholder scan:

- This plan contains no unfinished section markers or deferred decisions.
- The final guide verification explicitly scans for unfinished markers.

Type and path consistency:

- All referenced paths match the approved spec and current repository layout.
- The implementation plan is documentation-only and does not introduce code types or APIs.
