# GasStation Information Hierarchy Redesign Plan

> **For agentic workers:** Do not start implementation from this document without first confirming the current branch state and visual baseline. This plan is intentionally written before code changes so the design work can proceed screen-by-screen without breaking existing UI contracts.

**Goal:** GasStation의 세 화면군을 `리뷰어 우선 + 실사용성 유지` 기준으로 재정비한다. 전체 리디자인보다 정보 위계를 먼저 다루며, 가격이 가장 먼저 읽히고 상태/설정/북마크가 같은 안내 시스템 안에 있는 것처럼 보이게 만든다.

**Design Context:** `AGENTS.md`와 `.impeccable.md`의 Design Context를 기준으로 한다. 대상은 포트폴리오 리뷰어/면접관이 우선이고, 실제 운전자의 빠른 가격/거리 판단을 제품 기준으로 유지한다. 톤은 `신뢰감 있는 / 빠른 / 도시적`, 시각 방향은 현대적인 도시형 공공 안내판이다.

**Architecture:** `core:designsystem`에서 토큰과 공통 컴포넌트 계약을 먼저 고정한 뒤, `feature:station-list`를 기준 화면으로 삼아 station card, 상태 안내, refresh/empty/failure 패턴을 정리한다. 그 다음 `feature:watchlist`와 `feature:settings`에 같은 정보 문법을 확장한다. ViewModel, domain, data 계약은 바꾸지 않고 presentation layer와 테스트 계약 중심으로 진행한다.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, Hilt, Robolectric Compose UI tests, Android instrumented demo flow, Gradle module tests.

---

## Current Screen And Structure Inventory

### App Shell

- `app/src/main/java/com/gasstation/MainActivity.kt`
  - `GasStationTheme` 아래 `GasStationNavHost`를 렌더링한다.
  - 외부 지도 실행은 `ExternalMapLauncher`를 주입받아 navigation effect에서 처리한다.
- `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`
  - 시작 화면은 `station-list`.
  - route는 `station-list`, `settings`, `settings/{section}`, `watchlist/{latitude}/{longitude}` 네 그룹이다.
  - 설정 상세는 settings back stack entry의 `SettingsViewModel`을 공유한다.

### Station List

- Main files:
  - `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
  - `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
  - `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
  - `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
  - `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt`
  - `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListBannerModel.kt`
- Current body states:
  - permission required
  - GPS required
  - initial loading
  - blocking failure
  - results
- Current results structure:
  - top bar sort segmented control plus bookmark/refresh/settings actions
  - stale/approximate-location banners
  - lightweight query context summary
  - station cards with price, distance, station name, fuel chip, brand icon, price delta, watch toggle
  - pull-to-refresh with top loading rail
  - empty state card
- Existing locked behavior:
  - price row appears above station title
  - distance metric aligns with price metric
  - brand label is hidden on list cards while icon remains accessible
  - cached results stay visible during refresh/failure
  - permission and GPS states override stale content when they should

### Settings

- Main files:
  - `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsRoute.kt`
  - `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailRoute.kt`
  - `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsViewModel.kt`
  - `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt`
  - `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt`
  - `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsSection.kt`
  - `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsUiState.kt`
- Current structure:
  - settings main groups: `탐색 설정`, `표시 설정`, `연결 설정`
  - each group is a `GasStationCard` with internal flat rows and dividers
  - detail screen uses the same card grammar with parent group heading and option rows
  - brand detail rows reserve a leading icon slot, including the all-brand option
- Existing locked behavior:
  - rows stack vertically inside group containers
  - setting title and selected value are one line on main rows
  - detail selected state uses check icon and semantics, not visible "현재 선택" text
  - detail screen keeps parent hierarchy inside one card

### Watchlist

- Main files:
  - `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistRoute.kt`
  - `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistViewModel.kt`
  - `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistUiState.kt`
  - `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`
  - `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistItemUiModel.kt`
- Current structure:
  - list of saved station comparison cards
  - price/distance top metric row
  - station name plus brand icon and visible brand label
  - supporting blocks for price change and last seen timestamp
  - empty state card with next-step copy
- Existing locked behavior:
  - brand icon and brand label both render on watchlist
  - comparison metric columns align across cards
  - delta indicator sits to the right of the change value
  - empty card sits in the upper portion of the screen

### Design System

- Main files:
  - `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Color.kt`
  - `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Typo.kt`
  - `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Theme.kt`
  - `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/GasStationThemeDefaults.kt`
  - `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Chrome.kt`
  - `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/BrandIcon.kt`
- Current reusable primitives:
  - `GasStationBackground`
  - `GasStationTopBar`
  - `GasStationCard`
  - `GasStationSectionHeading`
  - `GasStationStatusBanner`
  - `GasStationBrandIcon`
- Current tokens:
  - typography roles: topBarTitle, sectionTitle, cardTitle, priceHero, metricValue, body, meta, chip, bannerTitle, bannerBody
  - spacing: 4, 8, 12, 16, 24
  - corner: small, medium, large
  - stroke: default, emphasis
  - icon size: topBarAction, trailingAction, status
- Known design debt:
  - `FontFamily.Default` is still the only font family.
  - `ColorWhite` is pure white and neutrals are not brand-tinted.
  - some typography roles use negative letter spacing.
  - `GasStationStatusBanner` uses a leading vertical accent bar; the new direction should replace this with a less generic status grammar.
  - `MetricBlock`, supporting blocks, action state cards, and top-bar action icon drawing are duplicated inside feature screens.

---

## Design Strategy

Use `StationListScreen` as the reference screen because it exercises every important presentation rule: price hierarchy, distance comparison, status banners, pull-to-refresh, empty/failure states, watch toggle, settings entry, and watchlist entry.

The target is not a brand-new look. The current yellow/black/white identity remains. The redesign should make the existing identity more disciplined:

- keep high contrast, but reduce visual competition between top bar, cards, chips, and banners
- make numeric hierarchy explicit and reusable
- move repeated UI patterns from feature files into `core:designsystem`
- keep the station list readable at screenshot-review speed
- extend the same hierarchy to watchlist and settings only after the station list proves the system

---

## Non-Goals

- Do not change repository, cache, DataStore, location, external map, or domain contracts.
- Do not redesign navigation structure.
- Do not replace the yellow/black/white brand identity.
- Do not add unrelated new features.
- Do not update README screenshots until the UI implementation is complete and visually verified.
- Do not remove existing semantics/test tags unless replacement tests cover the same contract.

---

## Proposed Implementation Phases

### Phase 0: Baseline Capture And Contract Audit

**Purpose:** Freeze what the current UI already promises before changing components.

**Files to inspect or update:**
- `docs/readme-assets/playstore_11.png`
- `docs/readme-assets/playstore_22.png`
- `docs/readme-assets/playstore_33.png`
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`
- `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt`
- `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistScreenTest.kt`
- `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/GasStationThemeTokensTest.kt`
- `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/ChromeContractsTest.kt`

- [ ] Capture current screenshots for station list, settings main, settings detail, watchlist populated, watchlist empty, permission required, GPS required, blocking failure, stale banner, and refreshing rail.
- [ ] Write a short baseline note listing which visual behaviors are intentionally preserved.
- [ ] Add or confirm UI tests for any visual contract that will be touched during the redesign.
- [ ] Confirm the implementation branch starts from a clean worktree or intentionally documented local changes.

**Exit criteria:**
- Current visual state is available for comparison.
- Existing test contracts are mapped to the UI areas they protect.

### Phase 1: Design System Token Cleanup

**Purpose:** Make the design language stronger before moving pixels in feature screens.

**Files likely to change:**
- `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Color.kt`
- `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Typo.kt`
- `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/GasStationThemeDefaults.kt`
- `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/GasStationThemeTokensTest.kt`
- `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/GasStationThemeDefaultsTest.kt`

- [ ] Keep `ColorYellow` and `ColorBlack` as the brand anchors, but introduce tinted surface/neutral/support names instead of relying on pure white and generic gray values everywhere.
- [ ] Replace negative letter spacing with neutral spacing unless a specific numeric role needs optical tightening.
- [ ] Decide whether Android system font remains acceptable for Korean readability or whether a bundled Korean-friendly display/body pair is worth the dependency and asset cost.
- [ ] Preserve `priceHero > metricValue > cardTitle/body/meta` hierarchy.
- [ ] Keep tabular number settings only on numeric emphasis roles.
- [ ] Extend tests so large number styling cannot leak into banners, chips, settings subtitles, or status copy.

**Exit criteria:**
- Token tests explain why each role exists.
- Feature screens can consume tokens without choosing arbitrary Material typography slots.

### Phase 2: Shared Presentation Components

**Purpose:** Move duplicated information hierarchy patterns into `core:designsystem` without over-abstracting product-specific behavior.

**Files likely to change:**
- `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Chrome.kt`
- Create `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Metric.kt` if metric primitives would make `Chrome.kt` too broad.
- Create `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Rows.kt` if row/supporting primitives would make `Chrome.kt` too broad.
- `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/ChromeContractsTest.kt`

- [ ] Add a reusable metric component for `label + number + unit`, with explicit primary and secondary modes.
- [ ] Add a reusable supporting-information component for `label + value + optional trailing`.
- [ ] Add a reusable action-state card for permission, GPS, loading, empty, and blocking failure surfaces.
- [ ] Replace the current status banner side-accent layout with a status grammar based on full border, tone-specific surface, optional compact symbol, and title/body hierarchy.
- [ ] Keep `GasStationCard` as the default frame but clarify when a screen should render unframed context.
- [ ] Move top-bar action sizing into shared primitives if custom Canvas icons remain necessary in settings.

**Exit criteria:**
- Feature screens can express their layout through shared primitives instead of copy-pasted MetricBlock/SupportingBlock variants.
- Component contracts define section order and text roles.

### Phase 3: Station List Reference Screen

**Purpose:** Apply the new hierarchy to the most important screen first.

**Files likely to change:**
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListBannerModel.kt`
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListBannerModelTest.kt`

- [ ] Keep the top-bar sort toggle and action entry points, but verify it does not visually overpower station cards.
- [ ] Keep `QueryContextSummary` unframed so current address and search condition read as list context, not a card competing with station cards.
- [ ] Rebuild station cards around the shared metric component.
- [ ] Keep price first, distance second, station name third, then fuel/brand/delta/watch state.
- [ ] Ensure long station names, large prices, and long fuel labels do not push watch actions or deltas into awkward overlap.
- [ ] Make stale and approximate-location banners feel like guidance, not a second card type.
- [ ] Keep cached results visible during loading/refresh/failure.
- [ ] Verify pull-to-refresh rail reads as transient progress, not another card.

**Exit criteria:**
- Existing station-list UI tests pass or are intentionally updated to the same behavioral contracts.
- New tests cover any new shared metric or status component behavior used by the list.

### Phase 4: Watchlist Parity

**Purpose:** Make watchlist feel like a comparison mode of the same app, not a separate layout language.

**Files likely to change:**
- `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`
- `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistScreenTest.kt`

- [ ] Replace local `MetricBlock` and `SupportingBlock` with shared components.
- [ ] Keep watchlist-specific brand label visibility; unlike the station list, watchlist benefits from explicit saved-station identity.
- [ ] Preserve metric column alignment across cards.
- [ ] Re-evaluate whether change value should display current price, delta amount, or both with clearer labels.
- [ ] Keep empty state in the upper portion and align its copy with the same action-state card grammar used by station list.

**Exit criteria:**
- Watchlist still reads as saved-station comparison.
- Tests continue to protect brand label visibility, column alignment, delta positioning, and empty state placement.

### Phase 5: Settings Main And Detail Polish

**Purpose:** Bring settings into the same information system while preserving the existing two-level hierarchy.

**Files likely to change:**
- `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt`
- `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt`
- `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsSection.kt`
- `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsUiState.kt`
- `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt`

- [ ] Replace duplicated row/divider/icon patterns with shared row primitives where they improve consistency.
- [ ] Keep grouped card hierarchy: explore, display, connection.
- [ ] Keep detail screens as one card with parent group title and flat rows.
- [ ] Preserve selected-state semantics and check icon behavior.
- [ ] Review Korean labels for scan speed and consistent terminology, especially `오일 타입`, `정렬기준`, and `연동지도 서비스`.
- [ ] Keep all options visible through scroll without hiding critical choices on smaller screens.

**Exit criteria:**
- Settings still proves DataStore-backed preferences clearly.
- Settings main and detail feel like configuration surfaces for the same station-list hierarchy.

### Phase 6: Visual QA, Demo Flow, And Documentation

**Purpose:** Confirm the redesigned UI still works as a portfolio artifact and as a demo app.

**Files likely to change after implementation only:**
- `docs/readme-assets/playstore_11.png`
- `docs/readme-assets/playstore_22.png`
- `docs/readme-assets/playstore_33.png`
- `README.md`
- `docs/test-strategy.md`
- `docs/verification-matrix.md`

- [ ] Run focused module tests:
  - `./gradlew :core:designsystem:testDebugUnitTest`
  - `./gradlew :feature:station-list:testDebugUnitTest`
  - `./gradlew :feature:watchlist:testDebugUnitTest`
  - `./gradlew :feature:settings:testDebugUnitTest`
- [ ] Run app-level demo checks:
  - `./gradlew :app:testDemoDebugUnitTest`
  - `./gradlew :app:assembleDemoDebug`
- [ ] If an emulator/device is available, run `./gradlew :app:connectedDemoDebugAndroidTest`.
- [ ] Capture after screenshots for the same baseline states captured in Phase 0.
- [ ] Update README screenshots only after the visual QA pass.
- [ ] Document any intentional visual contract changes in `docs/test-strategy.md` or a follow-up design note.

**Exit criteria:**
- Screenshot set supports the portfolio story.
- Tests and docs describe the new UI contracts.

---

## Recommended First Implementation Slice

Start with a small vertical slice, not the whole redesign:

1. Update token tests and token names if needed.
2. Add shared `MetricBlock` and `SupportingBlock` primitives in `core:designsystem`.
3. Migrate only `StationListScreen.StationCard` to those primitives.
4. Verify station-list tests.
5. Capture one before/after screenshot.

This slice proves the new hierarchy without touching settings, watchlist, navigation, or data flow.

---

## Risk Register

- **Risk: over-abstracting shared components.** Keep product-specific decisions, such as station-list brand label hiding, inside feature screens unless multiple screens need the exact same behavior.
- **Risk: visual polish breaks existing UI tests.** Update tests only when the user-visible contract changes intentionally.
- **Risk: typography changes hurt Korean readability.** Any font change must be tested with Korean station names, long settings descriptions, and large numeric labels.
- **Risk: stronger status styling competes with price.** Status surfaces should guide, not dominate the station list.
- **Risk: README screenshots drift from demo flow.** Screenshot updates must wait until demo data and UI tests are stable.

---

## Approval Gate Before Code

Before implementation begins, confirm these decisions:

- Keep information hierarchy as the first scope, not a full visual restyle.
- Use station list as the reference screen.
- Preserve existing navigation and state contracts.
- Allow `core:designsystem` component extraction before feature-screen polish.
- Update README screenshots only after implementation and visual QA.
