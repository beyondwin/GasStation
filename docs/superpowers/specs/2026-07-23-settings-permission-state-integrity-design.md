# Settings And Permission State Integrity Design

**Date:** 2026-07-23

**Status:** Approved design

**Scope:** Settings readiness and persistence, nearby-list synchronization, watchlist fuel context, demo/prod location permission parity, external-map provider integrity, and cross-screen regression coverage

## Goal

Make every settings consumer use the same persisted value at the same time, and make `demo` follow the same location-permission gate as `prod`.

The change must eliminate:

- a nearby list temporarily or persistently using a different radius, fuel type, brand filter, or sort order than Settings shows;
- a default preference being treated as real before DataStore emits its first value;
- a settings detail screen navigating back before a write succeeds;
- watchlist prices silently using a fuel type other than the selected preference;
- `demo` showing permission guidance briefly and then bypassing it with its fixed coordinates;
- a selected external-map label, package, and URI describing different providers.

## Current-State Findings

The reported `demo` permission transition is reproducible. `AndroidForegroundLocationProvider` evaluates the demo location override before denied permission, and station-list state explicitly allows denied access when demo coordinates exist. This produces a permission-guidance-to-list transition without a grant.

The reported steady-state distance/price disagreement was not reproducible at the current commit. In-session manual checks showed home and Settings converging on the same DataStore value. The code nevertheless has a concrete transient and interaction race:

- `StationListViewModel` initializes its preference flow with `UserPreferences.default()`;
- `SettingsViewModel` initializes a fully selected UI state from `UserPreferences.default()`;
- search query construction, sort toggling, and map-provider selection can consume that fabricated value before the first persisted emission;
- Settings detail starts an asynchronous update and immediately navigates back.

The wider audit found additional integrity gaps:

1. Settings promises that fuel type applies to the list and bookmarks, but watchlist does not observe preferences. It resolves each saved station against its latest cached or historical fuel context, so one comparison screen can mix fuel types.
2. Search radius, brand, and sort are nearby-list policies, but Settings copy does not clearly scope all of them.
3. The `KAKAO_NAVI` preference is rendered as Kakao Navi while `ExternalMapLauncher` targets the KakaoMap package and `kakaomap://` URI.
4. NAVER Maps URLs omit the required `appname` parameter.
5. Provider intent tests cover only part of the KakaoMap route and do not protect all packages, URIs, coordinate contracts, or fallback paths.
6. Connected tests grant permission before launch and do not protect denied first entry, bidirectional home/Settings synchronization, recreation, or watchlist fuel consistency.

`prod` was manually checked with a non-secret placeholder build property because no live `opinet.apikey` was available. Denied first entry remained on permission guidance, the Android permission dialog opened only after the explicit action, denial did not reveal the list, and persisted sort selection survived process restart. A live Opinet response remains outside the evidence collected during design.

## Decisions

### Keep Settings as the single source of truth

The ownership path remains:

```text
feature consumer
  -> domain:settings use case
  -> SettingsRepository
  -> data:settings
  -> core:datastore
```

No app-scoped settings coordinator is introduced. `app` continues to own composition, navigation, startup wiring, and external handoff only.

Features represent readiness locally instead of inventing a default selection:

```text
Loading -> Ready(preferences) -> Saving(key) -> Ready(updated preferences)
                                      |
                                      +-> Error(previous preferences, failure)
```

- Before the first DataStore emission, Settings does not expose selected options.
- Station list does not form a `StationQuery`, accept preference mutations, or open an external map until preferences are ready.
- UI selection is derived from observed persisted preferences, not from an optimistic duplicate.
- A failed write leaves the previously observed value authoritative.

### Make sort toggling atomic

The nearby quick-toggle must not calculate its next value from a fabricated or stale feature snapshot. A domain settings use case performs the toggle through the repository transform, so the current persisted value is read and updated atomically.

Explicit selections for radius, fuel, brand, sort, and map provider continue to use explicit domain use cases.

### Define consumer scope per setting

| Preference | Consumer scope |
| --- | --- |
| Search radius | Nearby station search only |
| Fuel type | Nearby station search and watchlist comparison |
| Brand filter | Nearby station search only |
| Sort order | Nearby station search only |
| Map provider | External handoff from a selected station |

Settings section labels and descriptions make these boundaries visible. Watchlist remains a saved-item comparison screen rather than a filtered copy of the current nearby list:

- radius and brand do not remove saved items;
- nearby sort does not reorder the saved comparison;
- the selected fuel type defines every watchlist price and price delta;
- the screen exposes the active fuel context.

### Query watchlist by selected fuel

The domain station boundary receives an explicit watchlist comparison input containing origin and fuel type. Data selects cache and history rows for that fuel only.

For a saved station without a valid price in the selected fuel context:

- retain the saved station identity, coordinates, and brand;
- expose price as unavailable for the selected fuel;
- do not substitute a price from another fuel type;
- render an explicit `selected fuel price unavailable` state.

This preserves offline/watchlist fallback without misrepresenting the preference. The domain/UI model must represent unavailable price deliberately rather than forcing a fake `MoneyWon`.

### Gate demo coordinates behind real permission

Permission is evaluated before any flavor-specific location override:

```text
Denied -> PermissionDenied
Granted + demo override -> fixed demo coordinates
Granted + no override -> Android foreground location
```

Station-list query eligibility requires all of:

- preferences ready;
- permission granted;
- location availability known and GPS enabled;
- valid current coordinates.

Denied permission always wins over retained coordinates, cache, and demo override. If permission is revoked while the app is running, search subscription becomes inactive and permission guidance replaces the list. Cached data may remain stored but is not rendered as current nearby content without permission.

The denied-coordinate bypass state and its rendering branches are removed if no remaining production transition requires them.

### Preserve deterministic demo reset

`demo` continues to reset seed data, watchlist, and preferences on process startup. This is an intentional documentation, screenshot, connected-test, and benchmark contract.

The reset completes synchronously before Activity creation. Feature readiness still waits for the first post-startup DataStore emission, so no previous-session value or fabricated default is rendered. Live documentation must state that a demo process restart restores default preferences.

## UX State Design

### First entry

1. Keep the launcher surface while permission and preference readiness are being established.
2. If permission is denied, render stable permission guidance without a list frame.
3. Open the Android permission dialog only after `Permission request`.
4. On grant, acquire location and transition through the existing loading state to content.
5. On denial or cancellation, remain on permission guidance.
6. After a prior request, when the OS will no longer present a useful prompt, replace the action with an app-settings action.
7. Keep GPS-disabled guidance separate from permission guidance.

`demo` and `prod` use the same visible transition. Only the successful coordinate source differs.

### Settings loading and saving

- Settings main and detail show a non-interactive loading state before preferences are ready.
- Selecting an option marks that setting as saving and disables duplicate selection.
- Navigation back occurs only after the update use case succeeds.
- A failure keeps the detail screen open, preserves the prior selected value, and shows retryable feedback.
- During a nearby quick-filter write, the affected control is temporarily disabled. The list and chip change only when the persisted preference is observed.
- Settings collection failure is an explicit retryable state, not a fallback to defaults.

### Watchlist fuel context

The watchlist summary includes the active fuel label, such as `휘발유 기준`. Each row either shows a price/delta from that same fuel context or a clear unavailable label. Accessibility semantics include the fuel context and unavailable state.

### External map handoff

Rename the model value and UI option from Kakao Navi to KakaoMap:

```text
KAKAO_NAVI (legacy stored name) -> KAKAO_MAP (domain value)
```

The storage mapper accepts the legacy name and emits `KAKAO_MAP`; future writes store the new name.

For each provider:

- set the selected provider package explicitly on the route intent;
- URL-encode the station name;
- pass WGS84 destination coordinates using the provider's supported parameter contract;
- include the runtime application ID as `appname` in NAVER Maps URLs (`com.gasstation` for prod and `com.gasstation.demo` for demo);
- open the matching Play Store page when the package is unavailable;
- fall back to the HTTPS Play Store page if the market URI is unavailable;
- return a structured failure instead of crashing if neither route nor fallback can be opened.

KakaoMap remains a URI-based integration. Adding the Kakao Navi SDK, native app key, or a new navigation provider is outside this change. The distinction follows the official [Kakao Navi Android integration](https://developers.kakao.com/docs/en/kakaonavi/android), which uses the Kakao Navi SDK and `navigateIntent()`.

NAVER Maps construction follows the official [NAVER Maps URL Scheme](https://guide.ncloud-docs.com/docs/en/maps-url-scheme), including the required `appname`.

TMAP's checked-in URI is not treated as verified merely because a matching package is installed. Implementation must establish the current supported TMAP route and coordinate contract through focused tests and device evidence; if authoritative confirmation is unavailable, the implementation report must retain that as an explicit unverified boundary.

## Shared Labels

Short, context-free labels reused by multiple features may move to `core:designsystem`, following the existing shared brand-label pattern:

- search radius labels;
- fuel type labels;
- sort labels.

Feature-specific descriptions, scope explanations, saving/error copy, and screen semantics remain in their feature modules. `core:designsystem` does not own settings policy or screen state.

## Error Handling

| Failure | Required behavior |
| --- | --- |
| Preference first read fails | Show retryable settings/search readiness failure; do not use defaults |
| Preference write fails | Keep previous observed value, keep detail open or nearby control unchanged, show feedback |
| Permission denied | Stable guidance, no location acquisition, query, or list |
| Permission permanently denied | App-settings action |
| GPS disabled | Existing location-settings guidance |
| Selected watchlist fuel unavailable | Keep saved identity and show unavailable price |
| Map package missing | Matching Play Store fallback |
| Route and store fallback unavailable | No crash; visible failure feedback |
| Stale in-flight query after preference change | Ignore result unless it matches the active query |

Cancellation remains cancellation and is not converted into user-visible failure.

## Test Strategy

### Storage and domain settings

- Round-trip every value of all five preferences.
- Verify partial and invalid stored values use the documented fallback without corrupting valid sibling fields.
- Verify legacy `KAKAO_NAVI` reads as `KAKAO_MAP` and the next write stores the new value.
- Verify concurrent field updates preserve unrelated fields.
- Verify atomic sort toggle starts from the current repository value.
- Verify read and write failures are surfaced.

### Location

- Denied permission beats demo override.
- Granted permission returns demo fixed coordinates.
- Denied permission does not invoke Android location acquisition.
- Revocation invalidates retained coordinates for query eligibility.
- Existing prod success, timeout, unavailable, and error paths remain protected.

Tests that currently codify denied-permission demo success are inverted or removed.

### Nearby list

- No query, automatic refresh, filter mutation, sort toggle, or map effect before preferences are ready.
- The first query uses the first persisted preference emission.
- Preference changes create an active query with matching radius, fuel, brand, and sort.
- Write failure preserves the prior chip and query.
- Denied permission produces guidance even if coordinates or cached rows exist.
- Permission grant produces one valid transition to loading/content without a permission-to-list flash.

### Settings

- Loading state contains no selected default option.
- The selected row follows observed preferences.
- Successful selection emits completion and only then navigates back.
- Failed selection keeps the detail route open and exposes feedback.
- Repeated input is blocked while the same setting is saving.

### Watchlist

- Repository queries one explicit fuel context for every saved station.
- Cache and history from other fuel types are never substituted.
- Saved identity survives when selected-fuel price is unavailable.
- UI summary and rows expose a consistent fuel label.
- Radius, brand, and nearby sort changes do not remove or reorder saved comparisons.

### External maps

For TMAP, KakaoMap, and NAVER Maps, verify:

- explicit package;
- scheme and route path;
- encoded destination name;
- latitude/longitude placement and coordinate contract;
- NAVER `appname`;
- installed-app route;
- market fallback;
- HTTPS fallback;
- final non-crashing failure.

### Connected flavor flows

Add connected coverage that uses the real navigation graph and shared settings repository:

1. denied `demo` first launch remains on guidance after waiting;
2. denial/cancellation remains on guidance;
3. grant transitions to fixed-location demo content;
4. home changes radius, fuel, brand, and sort, then Settings shows the same values;
5. Settings changes them, then home chips and query output match;
6. Activity recreation preserves the in-process selection;
7. map-provider change produces the expected provider intent;
8. watchlist uses the selected fuel consistently;
9. demo process restart restores documented defaults;
10. runtime permission revocation hides content again.

`prod` host/instrumentation tests use fake location and station boundaries so CI does not depend on a live API key or service. A credentialed manual prod check is separate evidence.

## Verification Contract

Implementation completion requires, at minimum:

1. focused RED/GREEN tests in each changed module;
2. settings, datastore, location, station data, nearby, settings, watchlist, and app flavor unit suites;
3. connected demo permission and cross-screen settings flow;
4. demo/prod debug assembly;
5. `scripts/agent/verify.sh auto`;
6. emulator checks for permission denial, grant, revocation, bidirectional settings synchronization, watchlist fuel, and external-map fallback;
7. live documentation synchronization for state, test strategy, demo behavior, and README demo story.

A live Opinet data verification is reported separately and must not be claimed without an actual credentialed run.

## Non-Goals

- Adding a Kakao Navi SDK or API key.
- Adding an in-app map.
- Applying nearby radius or brand filters to watchlist.
- Making watchlist a second live refresh session.
- Removing deterministic demo startup reset.
- Changing cache-key or stale-result policy beyond the selected-fuel watchlist query.
- Committing credentials, pushing, opening a PR, releasing, or deploying.

## Success Criteria

- No feature consumes `UserPreferences.default()` as if it were a loaded preference.
- Home, Settings, and watchlist cannot represent contradictory preference meanings at the same time.
- A preference mutation is not presented as complete until persistence succeeds.
- Denied permission prevents location success and nearby content in both flavors.
- Demo content begins only after grant and uses its fixed coordinate only then.
- Watchlist never mixes or silently substitutes fuel contexts.
- Selected map label, enum, package, and URI refer to the same provider.
- Cross-screen, recreation, restart, and flavor-specific regression tests fail if any of these contracts drift.
