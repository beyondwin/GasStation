# 성능

숫자는 재현 가능한 `demo` 경로의 물리 기기 결과다. `prod`는 실서버·네트워크·위치가 섞여서 커밋 숫자로 쓰지 않는다.

## 핵심 흐름

| Journey | What It Measures | Metric |
| --- | --- | --- |
| Startup to first content | Cold app launch until the first usable station-list content is visible and `reportFullyDrawn()` is reached | `StartupTimingMetric` |
| List scroll | Frame stability while scrolling the price-first station list | `FrameTimingMetric` |
| Refresh | Frame stability while refreshing seeded nearby station data | `FrameTimingMetric` |
| Open watchlist | Frame stability while saving a station and opening the watchlist comparison screen | `FrameTimingMetric` |

## 최근 물리 기기 측정

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

## 결과

| Hero journey | Primary metric | p50 | p95 | Iterations | Samples |
| --- | --- | --- | --- | --- | --- |
| Startup to first content | `timeToInitialDisplayMs` | 347 ms | 393 ms | 10 | 10 |
| Startup to first content | `timeToFullDisplayMs` | 546 ms | 622 ms | 10 | 10 |
| List scroll | `frameDurationCpuMs` | 3.84 ms/frame | 6.83 ms/frame | 5 | 225 |
| List scroll | `frameOverrunMs` | -3.50 ms/frame | -0.48 ms/frame | 5 | 225 |
| Refresh | `frameDurationCpuMs` | 3.83 ms/frame | 6.05 ms/frame | 5 | 185 |
| Refresh | `frameOverrunMs` | -3.42 ms/frame | -1.15 ms/frame | 5 | 185 |

Negative `frameOverrunMs` values mean the frame finished its work that far ahead of its display deadline; positive values would indicate jank. p95 ≤ 0 across both scroll and refresh on this device means no dropped frames at the 95th percentile of observed samples.

## Baseline profile 흐름

The baseline profile generator covers:

- App startup
- First station-list content
- Seeded refresh
- Station-list scroll
- Watchlist entry after saving a station

The generator and its companion `openWatchlistFrameTiming` benchmark depend on `station-list-watch-toggle`, the persistent `bottom-nav-watchlist` tab, and `watchlist-card` appearing within the benchmark helper timeout. These ASCII test tags are exposed as resource IDs so benchmark selectors stay separate from Korean accessibility copy. See Known Limitations for the current status of those two scenarios.

## 명령

<!-- command-owner: performance.hero -->

```bash
./gradlew :app:assembleDemoBenchmark :benchmark:assembleBenchmark
ANDROID_SERIAL=<device serial> ./gradlew :app:installDemoBenchmark :benchmark:connectedBenchmarkAndroidTest
```

The `:app` `benchmark` build type forks `release` with `isDebuggable=false`, `isProfileable=true`, and the debug signing config so the same minified APK macrobenchmark expects can be installed and traced without a release keystore. The connected command installs `demoBenchmark` before running the test APK because the benchmark module launches the target activity explicitly as `com.gasstation.demo/com.gasstation.MainActivity`.

## 결과 읽기

- Startup numbers are used to replace the README startup metric table. `timeToInitialDisplayMs` is the moment the first frame after Activity launch lands; `timeToFullDisplayMs` is the moment `reportFullyDrawn()` is called (i.e., the first real station content is laid out).
- Frame timing numbers (`frameDurationCpuMs`) describe how long the UI thread spent producing each frame during the journey. p95 below the device's frame budget (~16.6 ms at 60 Hz, ~8.3 ms at 120 Hz) means scroll/refresh are not the bottleneck on this device.
- Perfetto traces are local diagnostic artifacts and are not committed unless a future investigation needs a small excerpt or screenshot.

## 한계

- **Engineering-quality gate timing is not app performance evidence.** The 52-suite/90-test convention run and its later narrow marker fix measure build verification, not a hero journey. The governed Linux invocation stopped at infrastructure preflight before attempt allocation, so Linux 90/90, package seal, recovery, cleanup, and default-Colima noninterference remain `NOT_MEASURED`; no new physical-device, emulator, hosted, or reproducible-APK run was performed. Therefore none of that evidence changes the Macrobenchmark table or APK-size snapshot above.
- **API 24/28/36 에뮬레이터 결과는 성능 측정이 아니다.** 기기 검증 워크플로는 demo UI, Room migration, API 36 Geocoder callback의 정확성을 본다. 에뮬레이터 결과가 나중에 `PASS`여도 물리 기기 Macrobenchmark 표, trace, README 성능 주장을 바꾸지 않는다. 지금 런타임 상태는 `NOT RUN`이다. [기기 검증](runbooks/device-verification.md)을 본다.
- **Baseline profile not installed.** `BaselineProfileGenerator.collectHeroJourney` did not produce a committed physical-device profile in the latest measured run, so compilation mode stays at `verify`. Startup numbers above are realistic for first-install / post-update users and represent a lower-bound improvement target once a baseline profile is generated.
- **`openWatchlistFrameTiming`은 아직 물리 기기에서 다시 측정하지 않았다.** 벤치마크 헬퍼는 `com.gasstation.demo/com.gasstation.MainActivity`를 직접 열고 `station-list-watch-toggle`, `bottom-nav-watchlist`, `watchlist-card`를 기다린다. 기기 검증은 같은 selector 흐름을 Pixel 8 API 37 에뮬레이터에서 확인했고, `:benchmark:assemble`은 제품 벤치마크 계약을 컴파일된 채로 유지한다. 다만 새 물리 기기 JSON/trace는 없다. `ANDROID_SERIAL=<physical device> ./gradlew :app:installDemoBenchmark :benchmark:connectedBenchmarkAndroidTest`가 통과하고 `find benchmark/build/outputs/connected_android_test_additional_output -name '*benchmarkData.json' -print`에 새 JSON이 보이기 전에는 README 성능 숫자를 바꾸지 않는다.
- **Cooling and thermal state not enforced.** macrobenchmark warned about `SUSTAINED_PERFORMANCE_MODE` being unavailable; results above are the median over 10 startup iterations and 5 frame iterations, which mitigates but does not eliminate device-side thermal variance. Re-run on a cooled device before committing future numbers if comparisons span multiple firmware revisions.

## APK 크기 (`demo`)

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
