# State Model

The reference app keeps persisted preferences and volatile session state separate.

## Persisted preference state

`data:settings` writes long-lived user choices into DataStore through `SettingsRepository`.

- Search radius
- Fuel type
- Brand filter
- Sort order
- External map provider

`core:datastore` also performs a one-time migration from the legacy shared-preferences keys into the same `UserPreferences` model, so existing installs converge onto the DataStore-backed state surface.

These values survive process death and feed both the settings screen and the station query pipeline.

## Session state

`feature:station-list` keeps session-only state inside `StationListSessionState` and derives `StationListUiState` from a combination of preferences, session inputs, and cached search results.

- Current coordinates
- Permission state
- GPS availability
- Loading / refreshing flags
- Snackbar and external-map effects
- Stale/fresh rendering derived from cached result freshness

This state is recreated per process and is intentionally not written back to DataStore. That keeps runtime conditions out of persisted settings and preserves the intended ownership split from the design review: preferences are durable inputs, while permissions, location, stale status, and UI effects are session concerns.

## Demo onboarding path

`demo` keeps the same reducer and stale/offline semantics as `prod`, but starts from deterministic seeded cache data and a fake coordinate source. Reviewers can exercise the state model without live API credentials, while `prod` still uses the exact same domain and data contracts with real secrets.
