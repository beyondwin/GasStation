# Developer Onboarding Guide Design

## Summary

Create a Korean developer onboarding guide for GasStation. The guide will be a single onboarding handbook that explains the product, project structure, technology choices, trade-offs, actual runtime/data flows, testing strategy, and interview/portfolio talking points.

The guide is not a replacement for the existing source-of-truth documents. It is a high-level, practical entry document that routes readers to the existing architecture, module contract, state, offline, test, verification, security, deployment, and performance documents when deeper detail is needed.

## Output

Primary output:

- `docs/onboarding/developer-onboarding-guide.md`

Navigation updates:

- Add a short link to the new guide in `README.md` under the documentation map.
- Add the guide to `docs/project-reading-guide.md` as a recommended entry for first-time maintainers.

No changes should be made to `AGENTS.md`.

## Audience

The primary reader is an Android developer who is joining or inheriting this project for the first time.

The guide should help that reader answer four questions:

- What does this app do for the user?
- Why did the project choose these technologies and boundaries?
- How does the main app logic actually flow through the code?
- Where should I change code, and how should I verify the change?

A secondary reader is an interviewer or reviewer. The document should include a dedicated interview/portfolio section that helps explain the project as a multi-module Clean Architecture Android app without mixing pitch copy into every technical section.

## Placement Rationale

Use `docs/onboarding/developer-onboarding-guide.md` instead of expanding `README.md` or scattering content across existing documents.

Reasons:

- `README.md` already serves as the project overview and portfolio-facing landing document.
- `docs/project-reading-guide.md` is a router, not a long-form teaching document.
- `docs/architecture.md`, `docs/module-contracts.md`, `docs/state-model.md`, `docs/offline-strategy.md`, `docs/test-strategy.md`, and `docs/verification-matrix.md` already own specific source-of-truth details.
- A developer onboarding guide needs a narrative reading path, practical code anchors, and explanation of trade-offs in one place.

## Scope

The guide will cover:

- Product purpose and user workflow.
- Active Gradle modules from `settings.gradle.kts`.
- Layer responsibilities across `app`, `feature:*`, `domain:*`, `data:*`, `core:*`, `tools:demo-seed`, and `benchmark`.
- Technology choices and trade-offs.
- Main runtime flows:
  - app startup
  - navigation
  - station list
  - location lookup
  - station search
  - cache snapshot and stale handling
  - refresh failure handling
  - settings
  - watchlist
  - external map handoff
  - demo/prod flavor differences
- Testing and verification commands.
- Change-by-change modification entry points.
- First three days onboarding route.
- First bug-fix procedure.
- First feature-addition procedure.
- Interview/portfolio explanation guide.
- Common mistakes and merge checklist.

The guide will not:

- Redefine module contracts in full table form.
- Duplicate complete architecture diagrams from `docs/architecture.md`.
- Change any app behavior.
- Add new dependencies.
- Update `AGENTS.md`.

## Proposed Document Structure

### 1. Start Here

Explain GasStation in one paragraph:

- Korean Android app for comparing nearby gas stations.
- Current location, price, distance, brand, fuel type, watchlist state, and external map handoff are the main product axes.
- Price is the hero; the rest is context for faster price decisions.
- `demo` and `prod` are both official runtime paths.

### 2. Product Mental Model

Explain the user journey:

1. Open station list.
2. Grant/deny location and handle GPS state.
3. Compare stations by price and distance.
4. Change fuel/radius/brand/sort settings.
5. Save stations to watchlist.
6. Compare saved stations.
7. Open preferred external map.

### 3. Repository And Module Shape

Use the active Gradle modules from `settings.gradle.kts` as the only module inventory.

Explain the layers:

- `app`: composition, flavor wiring, startup, navigation, external app handoff.
- `feature:*`: UI state, route, screen, effect.
- `domain:*`: repository contracts, use cases, domain models, pure policy surfaces.
- `data:*`: repository implementations and storage/remote/cache composition.
- `core:*`: shared infrastructure, values, design primitives, platform implementations.
- `tools:demo-seed`: repeatable demo seed generation.
- `benchmark`: performance and baseline profile evidence.

Link to:

- `docs/module-contracts.md`
- `docs/architecture.md`

### 4. Technology Stack Overview

Provide a short table for each technology:

- Kotlin
- Gradle Kotlin DSL and convention plugins
- Multi-module Clean Architecture
- Jetpack Compose
- Material 3 and `core:designsystem`
- AndroidX Lifecycle ViewModel
- Coroutines and Flow
- Hilt
- Room
- DataStore
- Retrofit, OkHttp, Gson converter
- Navigation Compose
- Play Services Location and Android Geocoder
- proj4j coordinate transform
- Robolectric
- Roborazzi
- Macrobenchmark and baseline profile
- Kover and Pitest
- Spotless and ktlint

Each row should include:

- What it does in this project.
- Why it was chosen.
- Main advantage.
- Main downside or risk.
- What a new maintainer must watch.
- Representative files.

### 5. Technology Trade-Off Details

Add deeper explanations for the major choices.

Examples:

- Compose was chosen for state-driven UI and testable screen contracts, but it requires clear state ownership and careful effect handling.
- Hilt reduces manual graph wiring, but can hide architecture drift if feature modules start depending on infra directly.
- Room gives reliable local cache and observable DAO flows, but schema changes need migration tests.
- DataStore fits small preference state, but storage DTOs must not become domain models.
- Flow matches observable cache and preference streams, but cancellation and lifecycle collection must be explicit.
- Demo flavor provides repeatability, but it must stay aligned with the real data path and not become a fake app mode.

### 6. App Startup Flow

Explain the flow through:

- `app/src/main/java/com/gasstation/App.kt`
- `app/src/main/java/com/gasstation/startup/AppStartupRunner.kt`
- `app/src/main/java/com/gasstation/MainActivity.kt`
- `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`

Cover:

- Hilt app graph.
- Startup hook abstraction.
- `reportFullyDrawn()` bridge.
- Start destination as station list.
- Navigation to settings, settings detail, and watchlist.

### 7. Station List Flow

Explain why station list is the central feature.

Representative files:

- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/LocationStateMachine.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationSearchOrchestrator.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListEffect.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListCards.kt`

Explain responsibilities:

- Route observes permission and lifecycle-bound availability.
- `LocationStateMachine` owns session location state.
- `StationSearchOrchestrator` owns active query, cache snapshot state, observed result, and blocking failure.
- `StationListViewModel` composes preferences, location state, search projection, loading flags, actions, and effects.
- Screen files render state and emit actions.

### 8. Data And Cache Flow

Representative files:

- `domain/station/src/main/kotlin/com/gasstation/domain/station/StationRepository.kt`
- `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQuery.kt`
- `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQueryCacheKey.kt`
- `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationSearchResult.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/StationSearchResultAssembler.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/StationCachePolicy.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/StationRetryPolicy.kt`
- `core/database/src/main/kotlin/com/gasstation/core/database/station/StationCacheDao.kt`
- `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationFetcher.kt`

Explain:

- `StationQuery` contains current coordinates, radius, fuel, brand filter, and sort order.
- Cache key includes location bucket, radius, and fuel type.
- Brand filter and sort are applied at read-model level.
- `station_cache_snapshot` distinguishes successful empty result from no cache.
- `StationSearchResult.hasCachedSnapshot` is the semantic source for cache existence.
- `fetchedAt != null` is not the preferred cache-existence test.
- Timeout and network failures retry once in data layer.
- Refresh failure preserves existing snapshot.
- Successful refresh replaces snapshot, appends price history, prunes old rows, and logs `SearchRefreshed`.

### 9. Location Flow

Representative files:

- `domain/location/src/main/kotlin/com/gasstation/domain/location/*`
- `core/location/src/main/kotlin/com/gasstation/core/location/*`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/LocationStateMachine.kt`

Explain:

- Feature depends on domain location contracts, not Android location implementation.
- `core:location` implements Android provider, availability, geocoder, address formatting, and demo override support.
- Address label is display context, not search input.
- Domain normalization prevents raw geocoder noise from leaking into station list.

### 10. Network Flow

Representative files:

- `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationFetcher.kt`
- `core/network/src/main/kotlin/com/gasstation/core/network/station/ProxyStationFetcher.kt`
- `core/network/src/main/kotlin/com/gasstation/core/network/station/LocalKoreanCoordinateTransform.kt`
- `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfig.kt`
- `app/src/main/java/com/gasstation/di/AppConfigModule.kt`

Explain:

- WGS84 is converted to KATEC locally.
- Direct Opinet and proxy modes are abstracted by `StationNetworkSource`.
- App owns endpoint mode selection.
- API key in Android client is not a strong secret boundary.
- Proxy escalation criteria live in the ADR and security docs.

### 11. Settings Flow

Representative files:

- `feature/settings/src/main/kotlin/com/gasstation/feature/settings/*`
- `domain/settings/src/main/kotlin/com/gasstation/domain/settings/*`
- `data/settings/src/main/kotlin/com/gasstation/data/settings/DefaultSettingsRepository.kt`
- `core/datastore/src/main/kotlin/com/gasstation/core/datastore/*`

Explain:

- `UserPreferences` is the durable source.
- Writes go through explicit domain use cases.
- DataStore stores storage-local DTOs.
- Settings main/detail routes share the same `SettingsViewModel`.
- Preference changes update station list query behavior.

### 12. Watchlist Flow

Representative files:

- `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/*`
- `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveWatchlistUseCase.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/WatchlistSummaryAssembler.kt`

Explain:

- Watchlist is a saved-station comparison screen, not a clone of the current list.
- Origin coordinates come from navigation argument and `SavedStateHandle`.
- It restores from latest cache when possible, then price history and saved metadata.
- It has no separate refresh/session location state.

### 13. Demo And Prod

Representative files:

- `app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt`
- `app/src/demo/kotlin/com/gasstation/DemoLocationModule.kt`
- `app/src/demo/kotlin/com/gasstation/di/DemoStationRemoteDataSourceModule.kt`
- `app/src/prod/kotlin/com/gasstation/startup/ProdSecretsStartupHook.kt`
- `app/build.gradle.kts`

Explain:

- `demo` is a deterministic official runtime path, not a mock exception path.
- `demo` resets DB and preferences and uses fixed coordinates and seed data.
- `prod` uses the real Opinet key, actual location, and direct/proxy network path.
- Tests, screenshots, benchmark evidence, and README stories depend on demo stability.

### 14. UI And Design System

Representative files:

- `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/*`
- `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/*`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListCards.kt`
- `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`

Explain:

- Yellow, black, white identity.
- Price-first hierarchy.
- Shared primitives own layout rhythm, not feature policy.
- Station list uses brand icon without visible brand label.
- Watchlist uses icon plus visible brand label for saved item identification.
- Accessibility semantics and test tags are part of the contract.

### 15. Testing And Verification

Link to:

- `docs/test-strategy.md`
- `docs/verification-matrix.md`

Explain:

- Which tests protect each layer.
- Why demo and prod are both tested.
- When to use quick local checks.
- When to use connected demo tests.
- When physical-device benchmark evidence is required.
- Why mutation tests are on-demand.

### 16. Change Entry Points

Provide practical entry points:

- UI change.
- New setting.
- Cache/stale policy change.
- Network/proxy change.
- Location behavior change.
- Watchlist behavior change.
- Demo seed change.
- Test/verification change.
- Documentation-only change.

Each entry should list:

- First files to read.
- Likely modules to edit.
- Tests to run.
- Documents to update.

### 17. First Three Days

Day 1:

- Read `AGENTS.md`, `README.md`, this guide, `docs/architecture.md`, and `docs/module-contracts.md`.
- Build demo.
- Follow station list flow in code.

Day 2:

- Read state, offline, and test strategy docs.
- Run a fast verification subset.
- Trace one UI action from screen to repository.

Day 3:

- Pick a small bug or copy/test cleanup.
- Make a scoped change.
- Run targeted tests.
- Update docs only if behavior changed.

### 18. First Bug Fix Procedure

Explain:

- Reproduce.
- Locate owning module.
- Read nearby tests first.
- Add or update a focused test.
- Fix at the owning layer.
- Run targeted verification.
- Avoid broad unrelated refactors.

### 19. First Feature Procedure

Explain:

- Start with product flow and domain contract.
- Decide whether storage/network/settings/location are involved.
- Add domain/data/core before feature UI when needed.
- Add feature state/action/effect and screen changes.
- Wire app navigation last.
- Check demo/prod impact.

### 20. Interview And Portfolio Explanation

Add answer templates for:

- Why multi-module Clean Architecture?
- Why Compose?
- Why Hilt?
- Why Room plus snapshot table?
- Why DataStore?
- Why demo flavor?
- How does offline behavior work?
- How are secrets handled?
- How is performance measured?
- What trade-off would you revisit before public production launch?

Tone should be candid and precise, not marketing-heavy.

### 21. Common Mistakes

Include mistakes such as:

- Treating filesystem directories as active modules instead of `settings.gradle.kts`.
- Putting business policy in `app`.
- Calling Room/Retrofit/DataStore directly from feature modules.
- Treating demo as a fake path.
- Using `fetchedAt != null` as cache existence instead of `hasCachedSnapshot`.
- Removing semantics or test tags without replacement.
- Adding settings writes without domain use cases.
- Updating docs without checking code anchors.

### 22. Merge Checklist

Include:

- `git status --short` was checked before work.
- Module ownership is correct.
- Demo/prod implications are considered.
- Tests were selected based on changed layers.
- `git diff --check` passes for documentation changes.
- Existing source-of-truth docs were updated only when their owned facts changed.

## Writing Guidelines

- Write in Korean.
- Use direct, practical explanations.
- Keep code paths explicit.
- Prefer "this project does X because Y" over generic Android definitions.
- Link to existing source-of-truth docs instead of copying complete sections.
- Do not invent benchmark numbers, module counts, APIs, or behavior.
- Keep the interview section separate from the operational onboarding sections.

## Verification Plan

For the spec document:

- Run `git diff --check -- docs/superpowers/specs/2026-06-10-developer-onboarding-guide-design.md`.

For the eventual guide implementation:

- Run `git diff --check -- README.md docs/project-reading-guide.md docs/onboarding/developer-onboarding-guide.md`.
- Confirm referenced source files exist.
- If only documentation links and the guide are changed, Gradle tests are not required.
- If the implementation discovers stale current-contract documentation and updates architecture/state/offline/test docs, run the documentation verification command from `docs/verification-matrix.md`.

## Approval State

Approved decisions:

- Use one long guide rather than scattered edits.
- Use `docs/onboarding/developer-onboarding-guide.md`.
- Include both practical developer onboarding and interview/portfolio explanation.
- Add short navigation links in `README.md` and `docs/project-reading-guide.md`.
- Do not edit `AGENTS.md`.
