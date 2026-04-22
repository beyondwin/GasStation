# GasStation Information Hierarchy Remaining Tasks

> **For agentic workers:** This is a remaining-work implementation plan. The first vertical slice is already merged: `GasStationMetricBlock` / `GasStationMetricEmphasis` exist in `core:designsystem`, and the station-list card migration uses them. Do not reimplement that slice.

## Goal

Finish the GasStation information hierarchy redesign in safe sequential vertical slices while preserving the existing Android module boundaries, demo/prod paths, UI state contracts, semantics, and tests. The result should make station list, watchlist, and settings feel like one fast, high-contrast public-signage system where price remains the primary read and status/settings/watchlist context supports that decision.

## Scope Guard

- [x] Verified current branch is `codex/information-hierarchy-redesign-remaining`.
- [x] Verified worktree was clean before creating this plan.
- [x] Read required source docs: `AGENTS.md`, `.impeccable.md`, module contracts, agent workflow, test strategy, verification matrix, broad redesign plan, and first station-list slice plan.
- [x] Verified first slice is present in code: `core/designsystem/.../Metric.kt` defines `GasStationMetricBlock` and `GasStationMetricEmphasis`; `feature:station-list` imports and uses them.
- [x] Do not redo the merged station-list metric card migration.
- [x] Keep this remaining scope limited to watchlist metric/supporting migration, settings row primitive extraction, status/banner guidance redesign, token color/font restyle, station-list state guidance cleanup, parity in watchlist/settings, screenshot QA, README screenshot refresh if warranted, tests, and docs.
- [x] Do not change repository, cache, DataStore, location, external map, navigation, domain, or data behavior unless a test reveals an existing UI contract cannot compile without a minimal supporting adjustment.
- [x] Preserve module boundaries: `app` only composition/navigation/handoff; `feature:*` owns UI state/models/screens/effects; `domain:*` exposes no Android/Compose/Room/Retrofit; `data:*` owns storage/remote/cache implementation without screen state or Compose; `core:designsystem` owns shared UI primitives.

## Model Routing

- [x] Use `gpt-5.4 high` for implementation planning, production code edits, spec compliance review, code quality review, and final judgment.
- [x] Use `gpt-5.4 high` for design decisions involving hierarchy, typography, token naming, accessibility, or README screenshot replacement.
- [x] Use `gpt-5.3-codex-spark medium` only for bounded smoke execution: running Gradle commands, collecting screenshots, checking device/emulator state, or reporting deterministic command output.
- [x] Escalate any smoke/test/screenshot failure from `gpt-5.3-codex-spark medium` back to `gpt-5.4 high` before changing code or deciding scope.

## Task 0: Baseline And Contract Lock

**Purpose:** Establish the visual/test baseline for the remaining slices without changing production Kotlin.

**Files likely touched:**
- `docs/superpowers/plans/2026-04-23-gasstation-information-hierarchy-remaining-tasks.md`
- Optional baseline note under `docs/superpowers/plans/` if screenshots uncover non-obvious existing behavior
- No production Kotlin files

**Implementation checklist:**
- [x] Locate current screenshot baseline assets. The implementation-before demo runtime baseline is under `docs/superpowers/plans/2026-04-23-gasstation-information-hierarchy-baseline/` and contains PNG captures for reachable states: `01-station-list.png`, `02-settings-main.png`, `03-settings-detail.png`, `04-watchlist-empty.png`, `05-watchlist-populated.png`, `06-refresh-rail-stale.png`, `07-loading-state.png`, and `08-approximate-banner.png`.
- [x] Verify baseline capture format with `ls` and `file`: all eight implementation-before screenshots exist and are 1080x2424 PNG image data. Capture conditions were Pixel_9 AVD, demo flavor installed, SDK `adb` screencap output retained in the baseline directory; the README trio `docs/readme-assets/playstore_11.png`, `docs/readme-assets/playstore_22.png`, and `docs/readme-assets/playstore_33.png` is also retained as the portfolio baseline.
- [x] Confirm existing tests that protect station-list card hierarchy, watchlist brand label visibility, settings selected-state semantics, status banner content validation, and design-system typography role contracts.
- [x] Record local visual baseline notes: permission-required, GPS-required, station-list empty-results, and blocking-failure full-screen states were not reachable through the current demo runtime without test-only state injection. Permission-required and blocking-failure were already locked by existing Compose tests; GPS-required and empty-results are now locked by the focused `StationListScreenTest` cases `gps required state renders location settings guidance and opens settings` and `empty results state renders empty guidance and retry action`.
- [x] Keep branch/worktree status documented before starting Task 1: current branch is `codex/information-hierarchy-redesign-remaining`; `git status --short` shows this plan file and the implementation-before baseline screenshot directory as untracked during Task 0 baseline work.

**Task 0 contract notes:**
- First slice is present and should not be redone: `GasStationMetricBlock` and `GasStationMetricEmphasis` exist in `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Metric.kt`, with station-list imports/usages in `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`.
- Station-list contracts are protected by `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`: price above station title, distance label alignment, brand icon without visible brand label, cached loading/refresh behavior, blocking failure copy, cached results under failure, permission override, GPS-required settings guidance, empty-results retry guidance, empty refresh, and pull-to-refresh behavior.
- Watchlist contracts are protected by `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistScreenTest.kt` and `WatchlistItemUiModelTest.kt`: brand icon plus visible brand label, stable card semantics hook, aligned comparison metric columns, delta indicator to the right of change value, empty card upper placement, and split price metric labels.
- Settings contracts are protected by `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt` and `SettingsSectionTest.kt`: vertical grouped rows, stable group containers, flat rows inside groups, one-line title/current value rows, detail parent group card, selected check icon without visible `현재 선택` copy, and stable section route/title ordering.
- Design-system contracts are protected by `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/ChromeContractsTest.kt` and `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/GasStationThemeTokensTest.kt`: metric emphasis roles, primary/secondary metric unit padding, status banner title/body order and title validation, approved text-role material fallbacks, and large-number typography isolation to `priceHero`/`metricValue`.

**Verification commands:**

```bash
git status --short
ls -l docs/superpowers/plans/2026-04-23-gasstation-information-hierarchy-baseline/*.png
file docs/superpowers/plans/2026-04-23-gasstation-information-hierarchy-baseline/*.png
./gradlew :app:installDemoDebug
"$ANDROID_HOME/platform-tools/adb" shell monkey -p com.gasstation.demo 1
"$ANDROID_HOME/platform-tools/adb" exec-out screencap -p > docs/superpowers/plans/2026-04-23-gasstation-information-hierarchy-baseline/<state>.png
rg -n "GasStationMetricBlock|GasStationMetricEmphasis" core/designsystem feature/station-list
rg -n "brand label|selected|StatusBanner|empty|failure|loading|price" feature/*/src/test core/designsystem/src/test
```

**Review gates:**
- [x] Spec compliance review (`gpt-5.4 high`): first slice is treated as complete; reachable implementation-before screenshots are captured/located; unreachable demo states are covered by named Compose tests rather than deferred without coverage; remaining work stays scoped to watchlist, settings, status/guidance, tokens, cross-screen cleanup, screenshot QA, tests, and docs.
- [x] Code quality review (`gpt-5.4 high`): no production Kotlin changed during baseline capture; Task 0 changes are limited to the remaining-task plan, implementation-before screenshot assets, and focused station-list UI contract tests.

## Task 1: Watchlist Metric And Supporting Migration

**Purpose:** Make watchlist cards use the shared information hierarchy while preserving their saved-station comparison behavior.

**Files likely touched:**
- `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Metric.kt`
- Optional: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Rows.kt`
- `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/ChromeContractsTest.kt`
- `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`
- `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistScreenTest.kt`
- `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistItemUiModelTest.kt`

**Implementation checklist:**
- [x] Add or extend a design-system supporting-info primitive for `label + value + optional trailing` only if the existing metric primitive does not cover watchlist supporting rows cleanly.
- [x] Migrate watchlist local `MetricBlock` calls to `GasStationMetricBlock`.
- [x] Migrate watchlist local `SupportingBlock` calls to the shared supporting primitive.
- [x] Keep brand icon and visible brand label on watchlist cards.
- [x] Preserve comparison metric column alignment across cards.
- [x] Preserve price delta indicator placement and semantics.
- [x] Keep watchlist empty state in the upper portion of the screen and align its copy with the emerging guidance language.
- [x] Avoid adding location lookup, refresh session state, repository behavior, or data-layer fallback changes to `feature:watchlist`.

**Verification commands:**

```bash
./gradlew :core:designsystem:testDebugUnitTest --tests com.gasstation.core.designsystem.component.ChromeContractsTest
./gradlew :feature:watchlist:testDebugUnitTest
rg -n "private fun MetricBlock|private fun SupportingBlock|GasStationMetricBlock" feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt
```

**Review gates:**
- [x] Spec compliance review (`gpt-5.4 high`): verify watchlist still reads as saved-station comparison and has not copied station-list-only brand hiding.
- [x] Code quality review (`gpt-5.4 high`): verify shared primitives are generic design-system UI, not watchlist business logic, and no domain/data/app files were touched.

## Task 2: Settings Row Primitive Extraction

**Purpose:** Bring settings main/detail rows into the same hierarchy without changing settings contracts or persistence.

**Files likely touched:**
- Optional: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Rows.kt`
- `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/ChromeContractsTest.kt`
- `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt`
- `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt`
- `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsSection.kt`
- `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt`
- `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsSectionTest.kt`

**Implementation checklist:**
- [x] Extract a shared row primitive only for reusable presentation shape: leading slot, title, selected/current value, trailing slot, divider placement, and selected-state affordance.
- [x] Keep settings main groups as `탐색 설정`, `표시 설정`, and `연결 설정` inside grouped cards.
- [x] Keep detail screens as one parent group/card with flat option rows.
- [x] Preserve selected-state semantics and check icon behavior; do not add visible "현재 선택" copy.
- [x] Preserve main-row one-line title/value scan behavior unless tests prove it fails on small screens.
- [x] Review Korean labels for scan speed and consistency, but avoid changing domain setting values.
- [x] Keep settings writes routed through existing `domain:settings` use cases and existing ViewModel actions.

**Verification commands:**

```bash
./gradlew :core:designsystem:testDebugUnitTest --tests com.gasstation.core.designsystem.component.ChromeContractsTest
./gradlew :feature:settings:testDebugUnitTest
rg -n "SettingsRow|GasStation.*Row|selected|check" feature/settings/src/main/kotlin feature/settings/src/test/kotlin core/designsystem/src/main/kotlin
```

**Review gates:**
- [x] Spec compliance review (`gpt-5.4 high`): verify settings still demonstrates DataStore-backed preferences through the existing two-level hierarchy.
- [x] Code quality review (`gpt-5.4 high`): verify row extraction removes meaningful duplication without absorbing settings-specific policy into `core:designsystem`.

## Task 3: Status Banner And Guidance Surface Redesign

**Purpose:** Replace generic status styling with a consistent guidance system for stale, approximate-location, permission, GPS, loading, empty, and blocking-failure states.

**Files likely touched:**
- `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Chrome.kt`
- Optional: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Guidance.kt`
- `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/ChromeContractsTest.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListBannerModel.kt`
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListBannerModelTest.kt`
- Optional follow-through: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`

**Implementation checklist:**
- [x] Redesign `GasStationStatusBanner` around full border, tone-specific surface, optional compact symbol, and clear title/body hierarchy.
- [x] Remove the old side-accent-bar feel from status banners.
- [x] Add or migrate to a shared guidance/action-state card only if station-list permission, GPS, loading, empty, and blocking failure surfaces share enough structure.
- [x] Keep stale and approximate-location banners as guidance above list content, not station-card competitors.
- [x] Preserve cached results during loading/refresh/failure.
- [x] Preserve permission and GPS override behavior where those states must take precedence.
- [x] Keep pull-to-refresh rail visually transient and separate from cards.
- [x] Update tests for behavior/semantics, not pixel snapshots.

**Verification commands:**

```bash
./gradlew :core:designsystem:testDebugUnitTest
./gradlew :feature:station-list:testDebugUnitTest
./gradlew :app:testDemoDebugUnitTest
rg -n "GasStationStatusBanner|StationListActionStateCard|permission|GPS|stale|approximate|failure|empty|loading" core/designsystem/src/main/kotlin feature/station-list/src/main/kotlin feature/station-list/src/test/kotlin
```

**Review gates:**
- [x] Spec compliance review (`gpt-5.4 high`): verify guidance supports price-first scanning and preserves cache/fallback state contracts.
- [x] Code quality review (`gpt-5.4 high`): verify status/guidance primitives are reusable presentation components and feature state branching remains inside `feature:station-list`.

## Task 4: Token Color And Typography Restyle

**Purpose:** Strengthen the yellow/black/white identity into a modern public-signage token system after shared primitives are stable.

**Files likely touched:**
- `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Color.kt`
- `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Typo.kt`
- `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Theme.kt`
- `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/GasStationThemeDefaults.kt`
- `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/GasStationThemeTokensTest.kt`
- `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/GasStationThemeDefaultsTest.kt`
- All feature screen tests only as needed for token-driven semantic changes

**Implementation checklist:**
- [x] Keep `ColorYellow` and `ColorBlack` as brand anchors.
- [x] Introduce or refine tinted neutral/surface/support tokens so the UI does not rely on pure white and generic gray everywhere.
- [x] Remove negative letter spacing unless a tested numeric role needs explicit optical treatment.
- [x] Preserve hierarchy: `priceHero` > `metricValue` > `cardTitle` > `body/meta/chip/banner`.
- [x] Keep tabular numeric settings limited to numeric emphasis roles.
- [x] Decide whether the Android system font remains best for Korean readability; do not add bundled fonts unless the tradeoff is explicitly justified.
- [x] Verify banners, chips, settings subtitles, and status copy do not inherit numeric display styling.

**Verification commands:**

```bash
./gradlew :core:designsystem:testDebugUnitTest
./gradlew :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :feature:settings:testDebugUnitTest
rg -n "letterSpacing|FontFamily|ColorWhite|ColorGray|priceHero|metricValue|tabular" core/designsystem/src/main/kotlin core/designsystem/src/test/kotlin
```

**Review gates:**
- [x] Spec compliance review (`gpt-5.4 high`): verify token changes keep the city public-information-sign direction and do not create generic Material sameness.
- [x] Code quality review (`gpt-5.4 high`): verify feature screens consume tokens rather than hardcoding new color/type choices.

## Task 5: Cross-Screen Hierarchy And Copy Cleanup

**Purpose:** Sweep station list, watchlist, and settings for consistency after primitives and tokens land.

**Files likely touched:**
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListBannerModel.kt`
- `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`
- `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt`
- `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt`
- Related feature tests under `feature/*/src/test`

**Implementation checklist:**
- [x] Confirm station list still reads price first, distance second, station name third, then fuel/brand/delta/watch state.
- [x] Confirm watchlist uses the same metric hierarchy while keeping explicit saved-station identity.
- [x] Confirm settings uses the same title/value/support rhythm without pretending it is a station card.
- [x] Clean up station-list banner, empty, loading, and failure copy so states are guidance, not decorative cards.
- [x] Check long Korean station names, large prices, long fuel labels, and long settings values for overlap risk.
- [x] Preserve accessibility semantics and test tags, or update tests with equivalent coverage.
- [x] Do not alter README screenshots in this task; only decide whether Task 6 should refresh them.

Task 5 note: README screenshots were not changed. Task 6 should refresh them if device screenshot QA confirms the hierarchy/copy changes are visually representative in the demo flow.

**Verification commands:**

```bash
./gradlew :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :feature:settings:testDebugUnitTest
./gradlew :app:assembleDemoDebug
rg -n "testTag|contentDescription|semantics|Price|가격|거리|선택|watch|empty|failure" feature/station-list/src feature/watchlist/src feature/settings/src
```

**Review gates:**
- [x] Spec compliance review (`gpt-5.4 high`): verify all three screen groups share the intended hierarchy and no deferred first-slice work was repeated.
- [x] Code quality review (`gpt-5.4 high`): verify cleanup is narrow, tests explain changed contracts, and unrelated behavior stays untouched.

## Task 6: Visual And Device Screenshot QA

**Purpose:** Prove the redesigned UI works at portfolio-review speed and on real demo runtime paths before changing README assets.

**Files likely touched:**
- Screenshot outputs under a temporary or documented local QA path
- `docs/readme-assets/playstore_11.png`
- `docs/readme-assets/playstore_22.png`
- `docs/readme-assets/playstore_33.png`
- `README.md`

**Implementation checklist:**
- [x] Build and run the demo app on an emulator/device.
- [x] Capture screenshots for station list, watchlist populated, watchlist empty, settings main, settings detail, stale/approximate guidance, permission/GPS states, loading, empty, and blocking failure where reachable.
- [x] Check phone-sized and wider screenshots for text overlap, clipped buttons, unstable metric alignment, and status surfaces competing with station cards.
- [x] Compare screenshots against the baseline from Task 0.
- [x] Refresh README screenshots only if the new UI is materially different and the captured flow is visually verified.
- [x] Keep README copy changes limited to screenshot references or flow wording made outdated by the redesign.

**Task 6 QA notes:**
- Pixel_9 AVD was not running before Task 6; it was started headlessly for this task at 1080x2424 / 420 dpi and restored to that size/density after the temporary README and wide-layout captures.
- After screenshots are under `docs/superpowers/plans/2026-04-23-gasstation-information-hierarchy-after/`: `01-station-list.png`, `01b-station-list-saved.png`, `02-settings-main.png`, `03-settings-detail.png`, `04-watchlist-empty.png`, `05-watchlist-populated.png`, `06-refresh-rail.png`, `08-approximate-banner.png`, `08b-approximate-settled.png`, and `10-wide-station-list.png`.
- Phone captures are 1080x2424 PNGs; the wider emulator override capture is 1600x2424. README assets were recaptured directly at 1080x1920 by temporarily overriding the emulator display size, preserving `docs/readme-assets/playstore_11.png`, `playstore_22.png`, and `playstore_33.png`.
- Compared against the Task 0 baseline: station cards now read price/distance first with stronger black/yellow contrast; watchlist and settings share the same stricter rhythm; approximate guidance uses the redesigned full-border guidance surface. No blocking overlap, clipped primary controls, or unstable metric alignment was observed in the retained phone or wide captures.
- `06-refresh-rail.png` captures the reachable refresh rail during a manual refresh. The rail is transient, keeps station cards in place, and can temporarily sit above the approximate-location banner during refresh; this was noted as non-blocking because content remains readable and the rail exits after refresh.
- Approximate guidance was reachable by granting only coarse location. Independent stale guidance was not retained as a runtime screenshot because the demo startup seed is immediately refreshed through the local seed remote data source before a stable stale-only frame can be captured.
- Permission-required, empty-results, and blocking-failure states were not reachable through the normal demo runtime: the demo location override allows denied-permission demo refreshes, the demo seed contains non-empty results for all available settings combinations, and no runtime failure toggle exists. GPS-disabled was attempted with `adb shell cmd location set-location-enabled false`, then restored, but the running demo did not transition to the GPS-required surface. These non-runtime states remain covered by the Task 0/Task 5 Compose tests named in the baseline notes.
- README refresh was warranted by the verified visual change. README copy changes were limited to the preview caption and alt text so the refreshed demo screenshots are described accurately.

**Verification commands:**

```bash
./gradlew :app:assembleDemoDebug
./gradlew :app:connectedDemoDebugAndroidTest
./gradlew :benchmark:assemble
git diff -- docs/readme-assets README.md
```

**Task 6 verification results:**
- [x] `./gradlew :app:assembleDemoDebug` completed with `BUILD SUCCESSFUL`.
- [x] `./gradlew :app:connectedDemoDebugAndroidTest` completed with `BUILD SUCCESSFUL` on the Pixel_9 AVD; 2 connected demo instrumentation tests ran.
- [x] `./gradlew :benchmark:assemble` completed with `BUILD SUCCESSFUL`.
- [x] `git diff -- docs/readme-assets README.md` was inspected; it contains only the preview caption/alt text update plus the three refreshed README PNG binaries.

**Review gates:**
- [x] Spec compliance review (`gpt-5.4 high`): verify screenshots support the GasStation portfolio story and the demo path remains a first-class path.
- [x] Code quality review (`gpt-5.4 high`): verify generated image updates are intentional, reasonably sized, and paired with any required README changes.

## Task 7: Final Tests And Documentation Sync

**Purpose:** Close the remaining redesign with module-level confidence and accurate docs.

**Files likely touched:**
- `README.md`
- `docs/test-strategy.md`
- `docs/verification-matrix.md`
- Optional follow-up design note under `docs/superpowers/plans/`
- No `AGENTS.md` update unless a principle applies to every future worker

**Implementation checklist:**
- [x] Update docs only when shipped behavior, screenshot story, test commands, or UI contracts changed.
- [x] Confirm `docs/module-contracts.md` still matches actual changed ownership.
- [x] Confirm `docs/test-strategy.md` names any new or newly important UI contract tests.
- [x] Confirm `docs/verification-matrix.md` commands remain accurate.
- [x] Run focused tests first, then the merge-ready regression set.
- [x] Inspect final diff for narrow scope and no production Kotlin outside planned modules.

**Task 7 documentation notes:**
- `docs/test-strategy.md` now names the newly important `core:designsystem` contract tests and the station-list, settings, and watchlist UI contracts for guidance/status surfaces, shared rows/metrics, selected-state affordance, and long-content clipping prevention.
- `docs/module-contracts.md` now explicitly lists shared metric, supporting-info, row, and guidance primitives under `core:designsystem`, while keeping screen state and copy branching in `feature:*`.
- `docs/verification-matrix.md` was inspected and left unchanged because the quick, merge-ready, connected UI, and benchmark commands still match the current Gradle task surface and Task 7 verification needs.
- `README.md` had already been updated by Task 6 for the refreshed demo screenshot story; Task 7 did not need additional README changes.

**Verification commands:**

```bash
./gradlew \
  :core:designsystem:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:assembleDemoDebug \
  :benchmark:assemble
```

Recommended merge-ready regression set:

```bash
./gradlew \
  :domain:location:test \
  :core:model:test \
  :domain:station:test \
  :domain:settings:test \
  :core:database:testDebugUnitTest \
  :core:datastore:testDebugUnitTest \
  :core:designsystem:testDebugUnitTest \
  :core:location:testDebugUnitTest \
  :core:network:test \
  :data:settings:testDebugUnitTest \
  :data:station:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :tools:demo-seed:test \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  :benchmark:assemble
```

**Task 7 verification results:**
- [x] Focused command completed with `BUILD SUCCESSFUL` in 24s:
  `./gradlew :core:designsystem:testDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :feature:settings:testDebugUnitTest :app:testDemoDebugUnitTest :app:assembleDemoDebug :benchmark:assemble`.
- [x] Merge-ready regression command completed with `BUILD SUCCESSFUL` in 25s:
  `./gradlew :domain:location:test :core:model:test :domain:station:test :domain:settings:test :core:database:testDebugUnitTest :core:datastore:testDebugUnitTest :core:designsystem:testDebugUnitTest :core:location:testDebugUnitTest :core:network:test :data:settings:testDebugUnitTest :data:station:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :app:testDemoDebugUnitTest :app:testProdDebugUnitTest :tools:demo-seed:test :app:assembleDemoDebug :app:assembleProdDebug :benchmark:assemble`.
- [x] Connected instrumentation was not rerun for Task 7 because Task 6 already completed `./gradlew :app:connectedDemoDebugAndroidTest` with `BUILD SUCCESSFUL` on Pixel_9 AVD and then shut the emulator down; `docs/verification-matrix.md` does not require duplicating that run for a docs-only final sync.
- [x] Diff scope inspection found production Kotlin changes only in planned UI/design-system modules: `core:designsystem`, `feature:station-list`, `feature:watchlist`, and `feature:settings`. No `app`, `domain:*`, `data:*`, location, repository, cache, navigation, or external-map production Kotlin files are changed.

**Review gates:**
- [x] Spec compliance review (`gpt-5.4 high`): verify the completed work matches this remaining plan and the source redesign docs.
- [x] Code quality review (`gpt-5.4 high`): verify final diff is cohesive, tests passed or failures are documented, and docs do not overstate implementation.

## Final Verification

- [x] `git status --short` contains only intentional files: README screenshot/copy updates, design-system token/primitive/test updates, station-list/watchlist/settings UI and test updates, plan screenshots, and docs/plan updates.
- [x] No first-slice reimplementation appears in the diff; the existing station-list `GasStationMetricBlock`/`GasStationMetricEmphasis` slice remains reused rather than recreated.
- [x] `core:designsystem` owns shared primitives and tokens.
- [x] `feature:station-list`, `feature:watchlist`, and `feature:settings` own only screen-specific state, models, effects, copy, and composition.
- [x] `app`, `domain:*`, and `data:*` remain untouched unless a documented, reviewed reason exists.
- [x] Focused Gradle commands passed.
- [x] Merge-ready regression set passed or any skipped/failing command is documented with reason.
- [x] Device/emulator screenshot QA completed where available; Task 6 captured the retained screenshots and ran connected demo instrumentation successfully on Pixel_9 AVD.
- [x] README screenshots refreshed only if warranted by verified visual change.

## Progress Checklist

- [x] Task 0 initial branch/context/source-doc verification started.
- [x] Task 0 baseline and contract lock complete.
- [x] Task 1 watchlist metric/supporting migration complete.
- [x] Task 2 settings row primitive extraction complete.
- [x] Task 3 status banner and guidance surface redesign complete.
- [x] Task 4 token color and typography restyle complete.
- [x] Task 5 cross-screen hierarchy and copy cleanup complete.
- [x] Task 6 visual/device screenshot QA complete.
- [x] Task 7 final tests and documentation sync complete.
