# Performance

GasStation measures performance through the deterministic `demo` flavor. The `prod` flavor is not used for committed performance numbers because real server, network, and live location state would make results environment-dependent.

## Hero Journeys

| Journey | What It Measures | Metric |
| --- | --- | --- |
| Startup to first content | Cold app launch until the first usable station-list content is visible and `reportFullyDrawn()` is reached | `StartupTimingMetric` |
| List scroll | Frame stability while scrolling the price-first station list | `FrameTimingMetric` |
| Refresh | Frame stability while refreshing seeded nearby station data | `FrameTimingMetric` |
| Open watchlist | Frame stability while saving a station and opening the watchlist comparison screen | `FrameTimingMetric` |

## Latest Physical Device Run

- **Device:** Samsung Galaxy S20+ 5G (`SM-G986N`)
- **Android version:** 13 (API 33, build `TP1A.220624.014`)
- **Hardware:** 8 cores, 2.84 GHz max, 11.1 GB RAM, CPU not locked
- **Build variant:** `demoBenchmark` (`isDebuggable=false`, `isProfileable=true`, `isMinifyEnabled=true` via R8)
- **Compilation mode:** `verify` (no baseline profile installed for this run — see Known Limitations)
- **Measurement date:** 2026-05-18 (KST)
- **Source data:** `benchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/SM-G986N - 13/com.gasstation.benchmark-benchmarkData.json`

Reproduce with:

```bash
find benchmark/build/outputs/connected_android_test_additional_output -name '*benchmarkData.json' -print
```

## Results

| Hero journey | Primary metric | p50 | p95 | Iterations | Samples |
| --- | --- | --- | --- | --- | --- |
| Startup to first content | `timeToInitialDisplayMs` | 347 ms | 393 ms | 10 | 10 |
| Startup to first content | `timeToFullDisplayMs` | 546 ms | 622 ms | 10 | 10 |
| List scroll | `frameDurationCpuMs` | 3.84 ms/frame | 6.83 ms/frame | 5 | 225 |
| List scroll | `frameOverrunMs` | -3.50 ms/frame | -0.48 ms/frame | 5 | 225 |
| Refresh | `frameDurationCpuMs` | 3.83 ms/frame | 6.05 ms/frame | 5 | 185 |
| Refresh | `frameOverrunMs` | -3.42 ms/frame | -1.15 ms/frame | 5 | 185 |

Negative `frameOverrunMs` values mean the frame finished its work that far ahead of its display deadline; positive values would indicate jank. p95 ≤ 0 across both scroll and refresh on this device means no dropped frames at the 95th percentile of observed samples.

## Baseline Profile Journey

The baseline profile generator covers:

- App startup
- First station-list content
- Seeded refresh
- Station-list scroll
- Watchlist entry after saving a station

The generator and its companion `openWatchlistFrameTiming` benchmark depend on a station being saved and the watchlist card (`content-description = "관심 주유소 카드"`) appearing within 5 seconds. See Known Limitations for the current status of those two scenarios.

## Commands

```bash
./gradlew :app:assembleDemoBenchmark :benchmark:assembleBenchmark
ANDROID_SERIAL=<device serial> ./gradlew :benchmark:connectedBenchmarkAndroidTest
```

The `:app` `benchmark` build type forks `release` with `isDebuggable=false`, `isProfileable=true`, and the debug signing config so the same minified APK macrobenchmark expects can be installed and traced without a release keystore.

## Result Interpretation

- Startup numbers are used to replace the README startup metric table. `timeToInitialDisplayMs` is the moment the first frame after Activity launch lands; `timeToFullDisplayMs` is the moment `reportFullyDrawn()` is called (i.e., the first real station content is laid out).
- Frame timing numbers (`frameDurationCpuMs`) describe how long the UI thread spent producing each frame during the journey. p95 below the device's frame budget (~16.6 ms at 60 Hz, ~8.3 ms at 120 Hz) means scroll/refresh are not the bottleneck on this device.
- Perfetto traces are local diagnostic artifacts and are not committed unless a future investigation needs a small excerpt or screenshot.

## Known Limitations

- **Baseline profile not installed.** `BaselineProfileGenerator.collectHeroJourney` failed during this run because the watchlist card selector timed out on this device after both the original 5 s budget and a follow-up 10 s budget. Compilation mode therefore stayed at `verify`. Startup numbers above are realistic for first-install / post-update users and represent a lower-bound improvement target once a baseline profile is generated.
- **`openWatchlistFrameTiming` skipped.** The watchlist-card flow times out on the very first selector (`By.desc("저장")`) on this device. A follow-up run with `WAIT_TIMEOUT_MS = 10_000` and an added `scrollStationList()` setup step still timed out at the same selector, so the failure is not a timing or list-position issue. `BaselineProfileGenerator.collectHeroJourney`, which runs `refreshStationList()` before the same `openWatchlistWithSavedStation()` call, gets past `"저장"` and only fails at the final watchlist card. Remaining investigation candidates: (a) confirm whether the `refresh` interaction is what makes the `IconButton` semantics in `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListCards.kt` discoverable to UiAutomator (e.g., by adding `setupBlock = { launchStationList(); refreshStationList() }` to `openWatchlistFrameTiming`), (b) attach a stable test tag / contentDescription directly to the `WatchToggleButton` semantics node so it does not depend on Compose `mergeDescendants` behavior, (c) confirm the seeded demo data exposes a card the bookmark action can save on this device.
- **Cooling and thermal state not enforced.** macrobenchmark warned about `SUSTAINED_PERFORMANCE_MODE` being unavailable; results below are the median over 10 startup iterations and 5 frame iterations, which mitigates but does not eliminate device-side thermal variance. Re-run on a cooled device before committing future numbers if comparisons span multiple firmware revisions.

## APK Size (demo flavor)

R8 minification on the `benchmark` build type produces a usable size baseline for what production-shaped users would download. Measured from the same build that produced the numbers above:

| Variant | APK | Size |
| --- | --- | --- |
| `demoBenchmark` (R8 minify=true, profileable, debug-signed) | `app/build/outputs/apk/demo/benchmark/app-demo-benchmark.apk` | **2.51 MB** |
| `demoDebug` (minify=false, debuggable) | `app/build/outputs/apk/demo/debug/app-demo-debug.apk` | 22.70 MB |

The roughly 9× difference confirms R8 + resource shrinking is doing the work expected of it for the production-shaped APK; it does not represent a separate optimization opportunity, just the build-type cost on this codebase. Reproduce with:

```bash
./gradlew :app:assembleDemoBenchmark :app:assembleDemoDebug
ls -l app/build/outputs/apk/demo/{benchmark,debug}/*.apk
```
