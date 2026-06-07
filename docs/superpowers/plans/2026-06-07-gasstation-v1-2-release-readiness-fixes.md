# GasStation v1.2 Release Readiness Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the three remaining v1.2 release blockers: metadata still on v1.1.3, watchlist invalid-cache fallback crash risk, and unclear proxy endpoint configuration failure.

**Architecture:** Keep the three concerns independent. First fix the runtime bug in `data:station`, then harden proxy configuration validation in `core:network`, then do a release-prep documentation/version commit. Direct Opinet remains the default runtime path, and performance numbers remain unchanged unless a physical-device benchmark is actually collected.

**Tech Stack:** Kotlin, Gradle Kotlin DSL, Android/Hilt, Room-backed repository tests, Retrofit/OkHttp, JUnit4, existing GasStation docs under `docs/superpowers`, `CHANGELOG.md`, `docs/release-notes`.

---

## File Structure

- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/WatchlistRepositoryTest.kt`
  - Adds the failing regression test for invalid cached row + valid history fallback.
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/WatchlistSummaryAssembler.kt`
  - Separates raw cached row presence from valid cached snapshot semantics.
- Modify: `core/network/src/test/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfigTest.kt`
  - Adds proxy base URL validation tests.
- Modify: `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt`
  - Validates proxy base URL before constructing Retrofit service.
- Modify: `app/build.gradle.kts`
  - Bumps version to `1.2.0` / `versionCode 8`.
- Modify: `CHANGELOG.md`
  - Moves current `Unreleased` content to `1.2.0 - 2026-06-07`.
- Create: `docs/release-notes/2026-06-07-v1.2.0.md`
  - Records the actual release contents and known benchmark limits.
- Modify: `README.md`
  - Updates current app version and release index.

## Task 1: Watchlist Invalid Cache Fallback

**Files:**
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/WatchlistRepositoryTest.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/WatchlistSummaryAssembler.kt`

- [ ] **Step 1: Add the failing regression test**

In `WatchlistRepositoryTest`, insert this test before `observeWatchlist drops watched entries with no last known snapshot or history`.

```kotlin
    @Test
    fun `observeWatchlist ignores invalid cached row when computing history fallback delta`() = runBlocking {
        val origin = Coordinates(37.498095, 127.027610)
        val repository = repository(
            stationCacheDao = RecordingWatchlistStationCacheDao(
                cachedStations = listOf(
                    cachedStation(
                        stationId = "station-invalid",
                        name = "Invalid Cached Snapshot",
                        brandCode = "GSC",
                        priceWon = -1,
                        latitude = 37.500095,
                        longitude = 127.025610,
                        fetchedAt = now.minusSeconds(10),
                    ),
                ),
            ),
            stationPriceHistoryDao = RecordingStationPriceHistoryDao(
                history = listOf(
                    history(
                        stationId = "station-invalid",
                        priceWon = 1_680,
                        fetchedAt = now.minusSeconds(30),
                    ),
                    history(
                        stationId = "station-invalid",
                        priceWon = 1_660,
                        fetchedAt = now.minusSeconds(330),
                    ),
                ),
            ),
            watchedStationDao = RecordingWatchedStationDao(
                watchedStations = listOf(
                    watched(
                        stationId = "station-invalid",
                        name = "Watched Fallback",
                        brandCode = "GSC",
                        latitude = 37.497095,
                        longitude = 127.026610,
                        watchedAt = now.minusSeconds(5),
                    ),
                ),
            ),
        )

        val item = repository.observeWatchlist(origin).first().single()

        assertEquals("Watched Fallback", item.station.name)
        assertEquals(1_680, item.station.price.value)
        assertEquals(StationPriceDelta.Increased(20), item.priceDelta)
        assertEquals(now.minusSeconds(30), item.lastSeenAt)
    }
```

- [ ] **Step 2: Run the targeted test and confirm it fails**

Run:

```bash
./gradlew :data:station:testDebugUnitTest --tests '*WatchlistRepositoryTest.observeWatchlist ignores invalid cached row when computing history fallback delta' --console=plain
```

Expected: fail with `Current price must be non-negative.` from `StationPriceDelta.from`.

- [ ] **Step 3: Update `WatchlistSummaryAssembler` to pass only valid cached rows into delta and lastSeen logic**

Replace the top of `toWatchedSummary` and the private helpers with this shape.

```kotlin
internal fun WatchedStationEntity.toWatchedSummary(
    origin: Coordinates,
    cachedStation: StationCacheEntity?,
    history: List<StationPriceHistoryEntity>,
): WatchedStationSummary? {
    val cachedSnapshot = cachedStation?.toDomainStation(origin)
    val validCachedStation = cachedStation?.takeIf { cachedSnapshot != null }
    val historyForContext = history.historyForWatchlistContext(cachedStation?.fuelType)
    val station = resolveStation(origin, cachedSnapshot, historyForContext) ?: return null
    val priceDelta = resolvePriceDelta(validCachedStation, historyForContext)
    return WatchedStationSummary(
        station = station,
        priceDelta = priceDelta,
        lastSeenAt = resolveLastSeenAt(validCachedStation, historyForContext),
    )
}

private fun WatchedStationEntity.resolveStation(
    origin: Coordinates,
    cachedSnapshot: Station?,
    historyForContext: List<StationPriceHistoryEntity>,
): Station? {
    val latestPrice = historyForContext.firstOrNull()
    return when {
        cachedSnapshot != null -> cachedSnapshot
        latestPrice != null -> {
            val stationCoordinates = Coordinates.ofOrNull(latitude, longitude) ?: return null
            val price = MoneyWon.ofOrNull(latestPrice.priceWon) ?: return null
            Station(
                id = stationId,
                name = name,
                brand = Brand.fromCode(brandCode),
                price = price,
                distance = origin.distanceTo(stationCoordinates),
                coordinates = stationCoordinates,
            )
        }
        else -> null
    }
}

private fun resolvePriceDelta(cachedStation: StationCacheEntity?, historyForContext: List<StationPriceHistoryEntity>): StationPriceDelta =
    when {
        cachedStation != null -> StationPriceDelta.from(
            previousPriceWon = historyRowsBefore(
                fetchedAtEpochMillis = cachedStation.fetchedAtEpochMillis,
                history = historyForContext,
            ).firstOrNull()?.priceWon,
            currentPriceWon = cachedStation.priceWon,
        )
        historyForContext.isNotEmpty() -> StationPriceDelta.from(
            previousPriceWon = historyForContext.drop(1).firstOrNull()?.priceWon,
            currentPriceWon = historyForContext.first().priceWon,
        )
        else -> StationPriceDelta.Unavailable
    }

private fun resolveLastSeenAt(cachedStation: StationCacheEntity?, historyForContext: List<StationPriceHistoryEntity>): Instant? =
    cachedStation?.fetchedAtEpochMillis?.let(Instant::ofEpochMilli)
        ?: historyForContext.firstOrNull()?.fetchedAtEpochMillis?.let(Instant::ofEpochMilli)
```

Do not change `historyForWatchlistContext`. It should still receive `cachedStation?.fuelType` so an invalid cached row can keep the latest known fuel-type context.

- [ ] **Step 4: Run the watchlist repository tests**

Run:

```bash
./gradlew :data:station:testDebugUnitTest --tests '*WatchlistRepositoryTest' --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit Task 1**

```bash
git add data/station/src/main/kotlin/com/gasstation/data/station/WatchlistSummaryAssembler.kt \
  data/station/src/test/kotlin/com/gasstation/data/station/WatchlistRepositoryTest.kt
git commit -m "fix(station): ignore invalid cached rows in watchlist fallback"
```

## Task 2: Proxy Endpoint Base URL Validation

**Files:**
- Modify: `core/network/src/test/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfigTest.kt`
- Modify: `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt`

- [ ] **Step 1: Add failing validation tests**

In `NetworkRuntimeConfigTest`, add `assertNotNull` and `assertThrows` imports:

```kotlin
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
```

Then add these tests after `runtime config supports proxy endpoint mode`.

```kotlin
    @Test
    fun `proxy station service rejects blank base url before Retrofit construction`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            NetworkModule.provideProxyStationService(" ")
        }

        assertEquals(
            "Proxy station base URL must be a non-blank absolute http(s) URL ending with '/'.",
            error.message,
        )
    }

    @Test
    fun `proxy station service rejects invalid base url before Retrofit construction`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            NetworkModule.provideProxyStationService("not-a-url")
        }

        assertEquals(
            "Proxy station base URL must be a non-blank absolute http(s) URL ending with '/'.",
            error.message,
        )
    }

    @Test
    fun `proxy station service rejects path base url without trailing slash`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            NetworkModule.provideProxyStationService("https://gasstation-proxy.example/api")
        }

        assertEquals(
            "Proxy station base URL must be a non-blank absolute http(s) URL ending with '/'.",
            error.message,
        )
    }

    @Test
    fun `proxy station service accepts host-only and trailing-slash base urls`() {
        assertNotNull(NetworkModule.provideProxyStationService("https://gasstation-proxy.example"))
        assertNotNull(NetworkModule.provideProxyStationService("https://gasstation-proxy.example/api/"))
    }
```

- [ ] **Step 2: Run tests and confirm the new validation tests fail**

Run:

```bash
./gradlew :core:network:test --tests '*NetworkRuntimeConfigTest' --console=plain
```

Expected: at least the blank/invalid/path validation tests fail because Retrofit currently owns the exception and message.

- [ ] **Step 3: Implement explicit base URL validation**

In `NetworkModule.kt`, add this import:

```kotlin
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
```

Replace `provideProxyStationService` and add the private helper below it.

```kotlin
    fun provideProxyStationService(baseUrl: String): ProxyStationService {
        val validatedBaseUrl = requireValidProxyBaseUrl(baseUrl)
        return Retrofit.Builder()
            .baseUrl(validatedBaseUrl)
            .client(defaultOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ProxyStationService::class.java)
    }

    private fun requireValidProxyBaseUrl(baseUrl: String): String {
        val parsedUrl = baseUrl.trim().toHttpUrlOrNull()
        require(parsedUrl != null && parsedUrl.encodedPath.endsWith("/")) {
            "Proxy station base URL must be a non-blank absolute http(s) URL ending with '/'."
        }
        return parsedUrl.toString()
    }
```

Leave `provideStationNetworkSource` unchanged. It will call `provideProxyStationService` only for `StationEndpointMode.Proxy`, so direct mode still allows a blank proxy URL.

- [ ] **Step 4: Run network tests**

Run:

```bash
./gradlew :core:network:test --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run demo BuildConfig default test**

Run:

```bash
./gradlew :app:testDemoDebugUnitTest --tests '*NetworkConfigResourceTest' --console=plain
```

Expected: `BUILD SUCCESSFUL`, proving default direct mode still has blank `PROXY_BASE_URL`.

- [ ] **Step 6: Commit Task 2**

```bash
git add core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt \
  core/network/src/test/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfigTest.kt
git commit -m "fix(network): validate proxy station base url"
```

## Task 3: v1.2.0 Release Metadata And Notes

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `CHANGELOG.md`
- Create: `docs/release-notes/2026-06-07-v1.2.0.md`
- Modify: `README.md`

- [ ] **Step 1: Bump app version**

In `app/build.gradle.kts`, change:

```kotlin
        versionCode = 7
        versionName = "1.1.3"
```

to:

```kotlin
        versionCode = 8
        versionName = "1.2.0"
```

- [ ] **Step 2: Move CHANGELOG Unreleased content to `1.2.0`**

At the top of `CHANGELOG.md`, keep this empty Unreleased section:

```markdown
## Unreleased

릴리스 후 다음 변경 사항을 기록합니다.

## 1.2.0 - 2026-06-07

### 개발자 영향

- v1.2 hardening planning: benchmark selector contracts now use stable Compose test tags exposed as resource IDs, keeping watchlist macrobenchmark selectors separate from Korean accessibility copy.
- Backend proxy readiness: `core:network` now has a proxy endpoint contract and endpoint-mode boundary while keeping direct Opinet as the default Android path.
- Refresh persistence hardening: `data:station`의 `refreshNearbyStations`는 snapshot 교체, 가격 히스토리 insert/trim, cache prune을 `core:database`의 새 `DatabaseTransactionRunner` 계약으로 단일 트랜잭션 안에서 수행합니다. 부분 실패 시 일관성 깨짐을 막고, 주유소별 `keepLatestTen` 호출을 stationId 기준으로 중복 제거합니다. 출력/동작은 변하지 않습니다.
- Verification depth: `domain:station`에 변이 테스트(pitest, report-only)를 도입하고 `StationPriceDelta.from`/`StationQuery.toCacheKey` 경계 테스트를 보강해 mutation test strength를 70%→97%로 끌어올렸습니다. 의존성 신선도 스캔(ben-manes versions, 비차단 CI job `dependency-freshness`)도 추가했습니다. 둘 다 빌드를 깨지 않는 신호 수집용입니다. 커버리지 진실성 게이트(Track 1, `koverVerify`)는 Kover 0.9.1↔AGP 9.1.1 호환성 한계로 보류합니다.
- Module boundary guard: `docs/module-contracts.md`의 의도된 모듈 경계를 config-cache-safe한 `verifyModuleBoundaries` Gradle 태스크(denylist)로 고정했습니다. 금지된 production 의존성 엣지(feature→core:location/network/database/datastore, feature/domain→data 등)가 생기면 빌드를 깨고, 의도된 `core:location → domain:location` 예외는 가드에서 제외합니다. CI `static-analysis` job에 포함됩니다.
- Mutation gate promotion: `domain:station` pitest를 report-only에서 `mutationThreshold` 40 floor 게이트로 승격해 점수 하락(현재 47%)을 막습니다. 변이 테스트를 `domain:settings`(report-only baseline)와 `domain:location`로 확장하고, `domain:location`은 `AddressLabelNormalizer`의 fallback 지역/district 선택 로직 갭을 보강해 test strength를 78%→85%로 올렸습니다. `domain:settings` SURVIVED는 전부 coroutine-suspend 등가 변이라 baseline만 기록합니다.
- Release readiness fixes: watchlist fallback now ignores invalid cached rows when calculating history-based deltas, and proxy endpoint mode now validates blank or malformed base URLs before Retrofit construction.

### 문서와 검증

- Build velocity evidence: `docs/build-velocity.md` records timing and current decisions for Gradle parallel/cache/configuration-cache and release assemble gate placement.
- Verification depth measurement: `docs/test-strategy.md`에 변이 테스트 섹션을, `docs/verification-matrix.md`에 온디맨드/report-only 검증 깊이 측정 섹션을 추가해 pitest와 dependency 스캔 실행법, Track 1 보류 배경을 단일 출처로 기록합니다.
- Module boundary + mutation gate docs: `docs/module-contracts.md`에 `verifyModuleBoundaries` 강제 규칙을, `docs/test-strategy.md`에 `domain:station`(floor 40)/`domain:settings`/`domain:location` 변이 점수와 SURVIVED 분석을, `docs/verification-matrix.md`에 모듈 경계 가드 명령과 세 모듈 pitest 명령, CI static-analysis 범위를 갱신했습니다.
- 상세 릴리즈 노트는 [docs/release-notes/2026-06-07-v1.2.0.md](docs/release-notes/2026-06-07-v1.2.0.md)를 봅니다.
```

Keep all existing `1.1.3` and older sections below this new `1.2.0` section.

- [ ] **Step 3: Create the v1.2.0 release note**

Create `docs/release-notes/2026-06-07-v1.2.0.md` with this content.

```markdown
# Release Notes - v1.2.0 (2026-06-07)

이 문서는 v1.2.0에서 실제로 적용된 변경을 기록합니다.

## 요약

v1.2.0은 가까운 주유소 비교, stale cache fallback, watchlist 비교, 외부 지도 handoff 흐름을 유지하면서 release confidence를 높인 hardening 릴리스입니다. 핵심은 proxy-ready network boundary, refresh write transaction, module boundary guard, mutation testing floor, 그리고 릴리스 직전 발견한 watchlist/proxy 설정 결함 마감입니다.

## 사용자 영향

- 기본 `demo`/`prod` 흐름은 v1.1.3과 같습니다.
- watchlist는 invalid cached row가 있어도 가능한 경우 저장 항목과 가격 히스토리로 비교 화면을 복원합니다.
- 가격, 거리, 브랜드, 유종, freshness, watch 상태의 정보 위계는 유지됩니다.

## 개발자 영향

- `core:network`는 direct Opinet과 proxy endpoint mode를 같은 `StationNetworkSource` 계약으로 제공합니다. 기본값은 direct Opinet입니다.
- proxy endpoint mode는 blank 또는 malformed base URL을 Retrofit construction 전에 명확한 설정 오류로 거부합니다.
- `data:station` refresh writes는 snapshot replace, history insert/trim, cache prune을 `DatabaseTransactionRunner` 트랜잭션 안에서 수행합니다.
- root `verifyModuleBoundaries` 태스크가 production dependency edge를 검사해 documented module boundary를 CI static-analysis job에서 강제합니다.
- `domain:station` pitest는 `mutationThreshold` 40 floor gate로 승격됐고, `domain:settings`/`domain:location` pitest는 report-only baseline으로 기록됩니다.
- ben-manes dependency freshness scan은 report-only CI job으로 추가됐습니다.

## 문서와 검증

- `CHANGELOG.md`, `README.md`, `docs/test-strategy.md`, `docs/verification-matrix.md`, `docs/module-contracts.md`, `docs/security-trade-offs.md`, `docs/adr/2026-05-18-backend-proxy-escalation.md`, `docs/build-velocity.md`가 v1.2 상태를 설명합니다.
- `docs/performance.md`의 committed physical-device numbers는 2026-05-18 Samsung Galaxy S20+ 5G run이 최신입니다. 이번 릴리스에서는 물리 기기 benchmark를 새로 수집하지 않았으므로 performance numbers는 갱신하지 않습니다.
- `openWatchlistFrameTiming`과 baseline profile physical-device evidence는 계속 Known Limitations로 남습니다.

## 버전

| 항목 | 값 |
| --- | --- |
| `versionName` | `1.2.0` |
| `versionCode` | `8` |
| 릴리즈 태그 | `v1.2.0` |

## 검증

```bash
./gradlew :core:network:test :data:station:testDebugUnitTest :app:testDemoDebugUnitTest --console=plain
git diff --check -- README.md CHANGELOG.md CONTRIBUTING.md app/build.gradle.kts docs/deployment.md docs/verification-matrix.md docs/release-notes/*.md
./gradlew :app:assembleDemoDebug :app:assembleProdDebug :benchmark:assemble
./gradlew :app:assembleProdRelease
```
```

- [ ] **Step 4: Update README current version and release index**

In `README.md`, change the current app version row to:

```markdown
| 현재 앱 버전 | `1.2.0` (`versionCode` 8) |
```

In the release section, replace the Unreleased line and insert v1.2.0 above v1.1.3:

```markdown
- [Unreleased](CHANGELOG.md#unreleased): v1.2.0 이후 변경 사항을 추적합니다.
- [1.2.0 릴리즈 노트](docs/release-notes/2026-06-07-v1.2.0.md): proxy readiness, refresh transaction, module boundary guard, mutation gate, release-readiness fixes를 정리합니다.
- [1.1.3 릴리즈 노트](docs/release-notes/2026-05-18-v1.1.3.md): hero benchmark evidence, first usable content startup reporting, backend proxy ADR, physical-device performance snapshot, 배포 절차 문서화를 정리합니다.
```

Do not change the Performance Snapshot table or `docs/performance.md`.

- [ ] **Step 5: Run release metadata text checks**

Run:

```bash
rg -n "versionCode =|versionName =|현재 앱 버전|2026-06-07-v1.2.0|## 1.2.0|v1.1.3 이후|v1.2.0 이후" app/build.gradle.kts README.md CHANGELOG.md docs/release-notes/2026-06-07-v1.2.0.md
```

Expected:

- `app/build.gradle.kts` shows `versionCode = 8`, `versionName = "1.2.0"`.
- `README.md` shows current app version `1.2.0` / `versionCode 8`.
- `CHANGELOG.md` has `## 1.2.0 - 2026-06-07`.
- README says `v1.2.0 이후`, not `v1.1.3 이후`.

- [ ] **Step 6: Commit Task 3**

```bash
git add app/build.gradle.kts CHANGELOG.md README.md docs/release-notes/2026-06-07-v1.2.0.md
git commit -m "chore: prepare v1.2.0 release metadata"
```

## Task 4: Final Verification

**Files:**
- No source edits unless verification exposes a real issue.

- [ ] **Step 1: Run targeted tests for the two code fixes**

```bash
./gradlew :core:network:test :data:station:testDebugUnitTest :app:testDemoDebugUnitTest --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run release document whitespace check**

```bash
git diff --check -- README.md CHANGELOG.md CONTRIBUTING.md app/build.gradle.kts docs/deployment.md docs/verification-matrix.md docs/release-notes/*.md
```

Expected: no output and exit code 0.

- [ ] **Step 3: Run release assemble minimum**

```bash
./gradlew :app:assembleDemoDebug :app:assembleProdDebug :benchmark:assemble
./gradlew :app:assembleProdRelease
```

Expected: both commands finish with `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run broader pre-tag confidence set**

```bash
./gradlew spotlessCheck lint verifyModuleBoundaries --continue
./gradlew \
  :core:network:test \
  :data:station:testDebugUnitTest \
  :domain:station:test \
  :feature:watchlist:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  verifyRoborazziDebug \
  :app:assembleProdRelease \
  --console=plain
```

Expected: both commands finish with `BUILD SUCCESSFUL`.

- [ ] **Step 5: Confirm the release tree is coherent**

```bash
git status --short
git log --oneline -5
rg -n 'versionCode = 8|versionName = "1.2.0"|현재 앱 버전 \| `1.2.0`|## 1.2.0 - 2026-06-07|2026-06-07-v1.2.0' app/build.gradle.kts README.md CHANGELOG.md docs/release-notes/2026-06-07-v1.2.0.md
```

Expected:

- `git status --short` is clean after commits.
- Latest commits include the two fix commits and the release metadata commit.
- The `rg` output only confirms v1.2.0 release metadata and does not show stale current-version text.

## Self-Review

- Spec coverage: P1 maps to Task 3, P2 maps to Task 1, P3 maps to Task 2, final release confidence maps to Task 4.
- Type consistency: `validCachedStation` remains `StationCacheEntity?`; `cachedSnapshot` remains `Station?`; `provideProxyStationService` continues returning `ProxyStationService`.
- Scope control: no proxy service deployment, no default endpoint flip, no performance number update, no UI redesign.
