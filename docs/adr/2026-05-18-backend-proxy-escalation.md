# ADR: Backend Proxy Escalation for Opinet API Access

Date: 2026-05-18

## Status

Accepted as a future escalation path. The Android app has a proxy-ready endpoint boundary, but no backend proxy service is deployed by default.

## Context

GasStation currently injects the Opinet API key into the Android client through Gradle property `opinet.apikey` and `BuildConfig.OPINET_API_KEY`. This keeps the Android app simple and supports the current portfolio scope, but it is not a server-side secret boundary. A released APK can be inspected and the key can be extracted.

Opinet returns public gas station price data. The current risk is quota exhaustion and key abuse, not exposure of private user data. The app also whitelists cleartext HTTP only for `www.opinet.co.kr` because the upstream API endpoint does not provide HTTPS.

## Decision

Do not implement a backend proxy in the current Android-focused scope. Document the conditions that require escalation and keep Android module contracts ready for an endpoint swap.

## Escalation Conditions

Move Opinet access behind a backend proxy when any of these becomes true:

- The app is publicly distributed beyond portfolio or controlled demo use.
- API quota cost or abuse risk becomes material.
- Key rotation or revocation must happen without shipping a new Android build.
- Monitoring must alert on unusual traffic patterns.
- The upstream API begins carrying data with higher sensitivity than public station prices.
- The product needs server-side caching, normalization, or policy enforcement.

## Target Proxy Responsibilities

The proxy owns:

- Opinet API key storage and rotation
- HTTPS edge exposed to Android clients
- Request rate limiting
- Opinet HTTP cleartext interaction inside the server boundary
- Response normalization for app-ready station payloads
- Optional short-lived server-side cache
- Metrics and alerting for quota and upstream errors

The Android app keeps:

- Location permission and current-coordinate acquisition
- Local Room cache and stale fallback behavior
- User settings and watchlist state
- UI state, retry presentation, and external map handoff
- Domain contracts for station search and refresh

## Android Code Impact

The Android app keeps the direct Opinet path as the default. v1.2 adds a proxy-ready network boundary:

- `core:network` can select direct Opinet or proxy endpoint mode from runtime config.
- `ProxyStationService` owns the Android-facing proxy contract.
- `ProxyStationFetcher` maps proxy payloads into the existing `NetworkRemoteStation` model.
- `data:station`, `domain:station`, `feature:*`, cache policy, stale fallback, and watchlist comparison contracts remain unchanged.

The endpoint swap preserves:

- `StationQuery`
- `StationRepository`
- `StationRefreshException`
- `StationSearchResult`
- demo seed behavior

## Consequences

Current app remains focused on Android architecture and performance evidence. Future public deployment has a documented security path that does not require reworking feature or domain contracts.
