# Offline Strategy

`data:station` treats the Room cache as the read model for station search results.

## Cache key

- Cache key = `locationBucket + searchRadius + fuelType`
- `sortOrder` is excluded from the key because distance/price sorting is applied on the cached result set
- Brand filtering is applied client-side after the cached snapshot is loaded
- `mapProvider` is also excluded because it only changes the external handoff target, not the fetched station dataset

## Freshness and stale behavior

- Freshness is determined by `StationCachePolicy`
- The stale threshold is 5 minutes
- Cached data is still rendered when it is stale
- When refresh fails, `DefaultStationRepository.refreshNearbyStations()` throws without replacing the Room snapshot
- Because the observe path still reads the existing snapshot, the last successful result remains visible and the UI stays in a stale state instead of clearing the list

## Demo flavor

- `demo` preloads a deterministic station snapshot for the default search radius and gasoline fuel type
- `demo` also uses a fixed Seoul coordinate so the seeded cache bucket lines up with reviewer onboarding
- No API keys are required for `demo`; the fake coordinate plus seeded snapshot demonstrate the same stale/offline semantics that `prod` uses with live network refreshes
