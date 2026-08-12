# Data Correctness Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent stale network completions, mixed Room reads, time-stuck freshness, proxy fuel contamination, ambiguous HTTP retry, reporter failures, and unverified migration fixtures from corrupting or misrepresenting station data.

**Architecture:** Keep policy in `data:station`, transport details in `core:network`, storage details in `core:database`, and SDK-neutral failure isolation in `core:observability`. Remote work may overlap, but only the latest cache-key generation may enter the transaction; observation reads marker and rows from one transaction and freshness advances through a cancellable data-layer timer.

**Tech Stack:** Kotlin 2.4.10, Coroutines/Flow, Hilt, Retrofit, Room 2.x/KSP, JUnit4, Robolectric, Turbine-style Flow assertions where already available

## Global Constraints

- Preserve the 18-module graph and the `feature -> domain -> data/core` dependency direction.
- `age <= 5 minutes` is Fresh; `age > 5 minutes` is Stale.
- Cancellation and superseded generations are silent and are never retried or reported as failures.
- A successful empty snapshot remains distinct from no cache.
- A failed fetch or persistence operation preserves the previous snapshot.
- Assign `fetchedAt` at the validated latest write boundary, not request start.
- Update `docs/offline-strategy.md`, `docs/architecture.md`, and test contracts in the same phase.
- Each behavioral task follows RED, observed failure, minimal GREEN, focused regression, then a narrow commit.

---

### Task 1: Isolate `CrashReporter` failures

**Files:**
- Modify: `core/observability/src/main/kotlin/com/gasstation/core/observability/CrashReporter.kt`
- Modify: `core/observability/src/test/kotlin/com/gasstation/core/observability/CrashReporterContractTest.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt`
- Modify: `core/location/src/main/kotlin/com/gasstation/core/location/AndroidAddressResolver.kt`
- Modify: `core/location/src/test/kotlin/com/gasstation/core/location/AndroidAddressResolverCrashReporterTest.kt`

**Interfaces:**
- Produces: `fun CrashReporter.recordNonFatalSafely(throwable: Throwable, metadata: Map<String, String> = emptyMap())`
- Preserves: original cancellation, fatal `Error`, and recoverable application failure meaning

- [ ] **Step 1: Add failing helper contract tests**

Add tests that use reporters throwing `IllegalStateException`, `CancellationException`, and `AssertionError`:

```kotlin
@Test
fun `recordNonFatalSafely swallows ordinary reporter exception`() {
    val reporter = throwingReporter(IllegalStateException("reporter failed"))
    reporter.recordNonFatalSafely(IllegalArgumentException("original"))
}

@Test(expected = CancellationException::class)
fun `recordNonFatalSafely preserves reporter cancellation`() {
    throwingReporter(CancellationException("cancelled"))
        .recordNonFatalSafely(IllegalArgumentException("original"))
}

@Test(expected = AssertionError::class)
fun `recordNonFatalSafely does not swallow fatal error`() {
    throwingReporter(AssertionError("fatal"))
        .recordNonFatalSafely(IllegalArgumentException("original"))
}
```

Use `java.util.concurrent.CancellationException` so `core:observability` does not acquire a new coroutines dependency.

- [ ] **Step 2: Run the focused test and observe RED**

Run: `./gradlew :core:observability:test --tests '*CrashReporterContractTest'`

Expected: compilation fails because `recordNonFatalSafely` does not exist.

- [ ] **Step 3: Add the safe extension**

```kotlin
fun CrashReporter.recordNonFatalSafely(
    throwable: Throwable,
    metadata: Map<String, String> = emptyMap(),
) {
    try {
        recordNonFatal(throwable, metadata)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        // Diagnostics must not replace the recoverable failure being recorded.
    }
}
```

- [ ] **Step 4: Replace direct calls and add caller regressions**

Use `recordNonFatalSafely` in `DefaultStationRepository` and `AndroidAddressResolver`. Add one test per caller proving that a throwing reporter does not replace the original `StationRefreshException(Unknown)` or `LocationAddressLookupResult.Error` cause.

- [ ] **Step 5: Run focused GREEN verification**

Run: `./gradlew :core:observability:test :data:station:testDebugUnitTest :core:location:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add core/observability core/location data/station
git commit -m "fix: isolate crash reporter failures"
```

### Task 2: Type network and HTTP failures at the owning boundary

**Files:**
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationFailure.kt`
- Modify: `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationFetchResult.kt`
- Modify: `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationFetcher.kt`
- Modify: `core/network/src/main/kotlin/com/gasstation/core/network/station/ProxyStationFetcher.kt`
- Modify: `core/network/src/main/kotlin/com/gasstation/core/network/model/ProxyStationDtos.kt`
- Modify: `core/network/src/test/kotlin/com/gasstation/core/network/station/NetworkStationFetcherTest.kt`
- Modify: `core/network/src/test/kotlin/com/gasstation/core/network/station/ProxyStationFetcherTest.kt`
- Modify: `domain/station/src/main/kotlin/com/gasstation/domain/station/StationRefreshFailureReason.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/StationRemoteDataSource.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/StationRetryPolicy.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/StationRemoteDataSourceTest.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/StationRetryPolicyTest.kt`
- Modify: `domain/station/src/test/kotlin/com/gasstation/domain/station/DomainContractSurfaceTest.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationSearchOrchestrator.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`

**Interfaces:**
- Produces: `sealed interface NetworkStationFailure { InvalidPayload; Timeout; Network; data class Http(val statusCode: Int); Unknown }`
- Changes: `NetworkStationFetchResult.Failure(reason, cause)` replaces the untyped singleton
- Produces: `StationRefreshFailureReason.Http(statusCode: Int)`; retryable only for 408, 429, and 500..599

- [ ] **Step 1: Add RED transport classification tests**

For both direct and proxy `MockWebServer` paths, add 408, 429, 500, 404, malformed-body, and cancellation tests. Assert the exact `NetworkStationFailure` and preserved cause.

- [ ] **Step 2: Add RED retry tests**

```kotlin
@Test
fun `http 429 retries once`() = runTest {
    assertRetriesOnce(StationRefreshFailureReason.Http(429))
}

@Test
fun `http 404 does not retry`() = runTest {
    assertDoesNotRetry(StationRefreshFailureReason.Http(404))
}
```

Run: `./gradlew :core:network:test :data:station:testDebugUnitTest :domain:station:test`

Expected: RED because failures have no payload and the HTTP domain subtype is absent.

- [ ] **Step 3: Add the transport-owned failure model**

```kotlin
sealed interface NetworkStationFailure {
    data object InvalidPayload : NetworkStationFailure
    data object Timeout : NetworkStationFailure
    data object Network : NetworkStationFailure
    data class Http(val statusCode: Int) : NetworkStationFailure
    data object Unknown : NetworkStationFailure
}

sealed interface NetworkStationFetchResult {
    data class Success(val stations: List<NetworkRemoteStation>) : NetworkStationFetchResult
    data class Failure(
        val reason: NetworkStationFailure,
        val cause: Throwable? = null,
    ) : NetworkStationFetchResult
}
```

Catch Retrofit `HttpException` only in `core:network`; `data:station` must not gain a Retrofit dependency. Rethrow `CancellationException`, map `InterruptedIOException`, `IOException`, parsing failures, HTTP status, and unknown exceptions in that order.

- [ ] **Step 4: Enforce proxy fuel parity and remove unused timestamps**

Change the mapper to require the request fuel:

```kotlin
private fun ProxyStationDto.toNetworkRemoteStation(
    expectedFuelType: FuelType,
): NetworkRemoteStation? {
    if (fuelType != expectedFuelType.name) return null
    // Existing identity, price, and coordinate validation follows.
}
```

Remove `fetchedAtEpochMillis` from `ProxyStationSearchResponseDto` and `ProxyStationDto`. Add tests proving mixed valid/mismatched rows retain only matching rows, all mismatched rows fail as `InvalidPayload`, and a raw empty list remains `Success(emptyList())`.

- [ ] **Step 5: Map transport reasons without leaking Retrofit**

Add `StationRefreshFailureReason.Http(val statusCode: Int)`. Make `DefaultStationRemoteDataSource` a pure mapping boundary and update `StationRetryPolicy.isRetryable()`:

```kotlin
is StationRefreshFailureReason.Http ->
    statusCode == 408 || statusCode == 429 || statusCode in 500..599
```

Map the new domain subtype to the existing generic refresh-failed copy in `StationSearchOrchestrator` and `StationListViewModel`; do not add a new UI message.

- [ ] **Step 6: Run GREEN regression**

Run: `./gradlew :core:network:test :domain:station:test :data:station:testDebugUnitTest :feature:station-list:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`, including all 408/429/5xx and 4xx cases.

- [ ] **Step 7: Commit**

```bash
git add core/network domain/station data/station feature/station-list
git commit -m "fix: type station transport failures"
```

### Task 3: Observe one atomic Room bucket snapshot

**Files:**
- Create: `core/database/src/main/kotlin/com/gasstation/core/database/station/StationBucketSnapshot.kt`
- Create: `core/database/src/main/kotlin/com/gasstation/core/database/station/StationBucketSnapshotObserver.kt`
- Create: `core/database/src/test/kotlin/com/gasstation/core/database/station/StationBucketSnapshotObserverTest.kt`
- Modify: `core/database/src/main/kotlin/com/gasstation/core/database/station/StationCacheDao.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/RepositoryDoubles.kt`
- Modify: repository and DAO tests

**Interfaces:**
- Produces: `StationBucketSnapshot(marker, rows)`
- Produces: `StationBucketSnapshotObserver.observe(latitudeBucket, longitudeBucket, radiusMeters, fuelType)`
- Removes from repository: `combine(observeSnapshot(), observeStations())`

- [ ] **Step 1: Write real-Room RED tests**

Cover non-empty replacement, empty replacement, repeated replacement, and pruning. Every emission with rows must satisfy:

```kotlin
assertTrue(snapshot.rows.all {
    it.fetchedAtEpochMillis == snapshot.marker?.fetchedAtEpochMillis
})
```

Run: `./gradlew :core:database:testDebugUnitTest --tests '*StationBucketSnapshotObserverTest'`

Expected: RED because the observer is absent.

- [ ] **Step 2: Add synchronous transactional DAO reads**

Add `suspend fun readSnapshot(...)` and `suspend fun readStations(...)` queries alongside the existing observation methods. Add:

```kotlin
data class StationBucketSnapshot(
    val marker: StationCacheSnapshotEntity?,
    val rows: List<StationCacheEntity>,
)
```

- [ ] **Step 3: Implement invalidation-triggered transactional observation**

`StationBucketSnapshotObserver` uses `GasStationDatabase.invalidationTracker.createFlow` for both tables. Each invalidation executes both synchronous reads inside `database.withTransaction`. Normalize `marker == null` to empty rows and require every non-empty row timestamp to match the marker before emitting.

Do not wrap `combine(two DAO flows)` in `@Transaction`; that does not make emissions atomic.

- [ ] **Step 4: Switch repository and doubles to the observer**

Inject `StationBucketSnapshotObserver` into `DefaultStationRepository`, remove the marker/row `combine`, and update `RecordingStationCacheDao` plus repository construction fixtures.

- [ ] **Step 5: Run GREEN regression**

Run: `./gradlew :core:database:testDebugUnitTest :data:station:testDebugUnitTest`

Expected: atomic observer tests and existing empty-snapshot semantics pass.

- [ ] **Step 6: Commit**

```bash
git add core/database data/station
git commit -m "fix: observe atomic station bucket snapshots"
```

### Task 4: Re-emit freshness when time crosses the boundary

**Files:**
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/StationFreshnessTicker.kt`
- Create: `data/station/src/test/kotlin/com/gasstation/data/station/StationFreshnessTickerTest.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/StationCachePolicy.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/StationCachePolicyTest.kt`
- Modify: repository and repository tests

**Interfaces:**
- Produces: `StationCachePolicy.staleBoundaryDelay(fetchedAt, now): Duration?`
- Produces: `StationFreshnessTicker.observe(fetchedAt): Flow<StationFreshness>`

- [ ] **Step 1: Add RED boundary tests**

Assert Fresh at exactly five minutes, Stale at five minutes plus one millisecond, rescheduling on a new snapshot, and cancellation when collection ends. Include an empty cached snapshot.

- [ ] **Step 2: Run RED**

Run: `./gradlew :data:station:testDebugUnitTest --tests '*StationCachePolicyTest' --tests '*StationFreshnessTickerTest'`

Expected: missing ticker/delay API failure.

- [ ] **Step 3: Implement pure boundary math and ticker**

```kotlin
fun staleBoundaryDelay(fetchedAt: Instant, now: Instant): Duration? {
    val staleAt = fetchedAt.plus(staleAfter).plusMillis(1)
    return Duration.between(now, staleAt).takeIf { !it.isNegative && !it.isZero }
}
```

The ticker emits current freshness immediately, delays only while Fresh, then emits Stale once. It accepts the injected `Clock`; tests use virtual coroutine time and a mutable fake clock.

- [ ] **Step 4: Integrate beneath repository projection**

For each atomic bucket snapshot, `flatMapLatest` to a new ticker. A new marker cancels the old boundary. Combine the ticker with watch/history metadata; do not mutate Room to signal staleness.

- [ ] **Step 5: Run GREEN and commit**

Run: `./gradlew :data:station:testDebugUnitTest`

```bash
git add data/station
git commit -m "fix: advance station freshness with time"
```

### Task 5: Persist only the latest refresh intent

**Files:**
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/LatestRefreshGate.kt`
- Create: `data/station/src/test/kotlin/com/gasstation/data/station/LatestRefreshGateTest.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/StationDataModule.kt`
- Modify: repository doubles/tests

**Interfaces:**
- Produces: `begin(key): RefreshTicket`
- Produces: `commitIfLatest(ticket, block): LatestCommitResult<T>`
- Produces: `complete(ticket)`; entry removal waits for all registered generations

- [ ] **Step 1: Write gate RED tests**

Use `CompletableDeferred` barriers to prove older-after-newer rejection, different-key independence, cancellation, empty-result latest intent, and tombstone retention.

- [ ] **Step 2: Run RED**

Run: `./gradlew :data:station:testDebugUnitTest --tests '*LatestRefreshGateTest'`

Expected: missing gate types.

- [ ] **Step 3: Implement the key-scoped gate**

Use a registry mutex plus one mutex per cache key. Registration and latest-generation checks use the key mutex. `commitIfLatest` holds it through the supplied transaction block. `complete` decrements the in-flight count and removes the entry only at zero.

```kotlin
internal data class RefreshTicket(
    val key: StationQueryCacheKey,
    val generation: Long,
)

internal sealed interface LatestCommitResult<out T> {
    data class Committed<T>(val value: T) : LatestCommitResult<T>
    data object Superseded : LatestCommitResult<Nothing>
}
```

- [ ] **Step 4: Integrate the full refresh lifecycle**

Register before remote work. In `finally`, complete the ticket. After validated remote success, enter `commitIfLatest`, capture `clock.instant()`, build entities, and execute the existing transaction. Only `Committed` logs `SearchRefreshed`; `Superseded` returns normally without history, prune, or analytics writes.

- [ ] **Step 5: Add repository-level inversion tests**

Prove B-then-A completion stores B, A cancellation cannot overwrite B, different buckets do not block, `fetchedAt` is write-time, and superseded success emits no success event.

- [ ] **Step 6: Run GREEN and commit**

Run: `./gradlew :data:station:testDebugUnitTest`

```bash
git add data/station
git commit -m "fix: persist only latest station refresh"
```

### Task 6: Export and test Room schema history

**Files:**
- Modify: `core/database/src/main/kotlin/com/gasstation/core/database/GasStationDatabase.kt`
- Modify: `core/database/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `core/database/schemas/com.gasstation.core.database.GasStationDatabase/1.json` through `5.json`
- Create: `core/database/src/androidTest/kotlin/com/gasstation/core/database/GasStationDatabaseMigrationInstrumentedTest.kt`
- Modify: existing migration tests and CI/device plan integration

**Interfaces:**
- Produces: checked-in Room schema evidence for every supported database version
- Preserves: intentional v2-to-v3 history reset as an explicit tested contract

- [ ] **Step 1: Enable deterministic schema export**

Set `exportSchema = true`. Configure the KSP `room.schemaLocation` argument to `core/database/schemas` and add `androidx.room:room-testing` through the version catalog.

- [ ] **Step 2: Generate historical schemas from source evidence**

Use temporary isolated worktrees at the commits that introduced each schema: v1 `e64634f`, v2 `a705fdb`, v3 `9b070ab`, v4 `014127f`, and v5 `da96a5f`. Enable schema export there without committing, generate each JSON, and copy only the database schema files. Verify entity SQL and identity hashes against the source at that commit; never hand-edit generated hashes.

- [ ] **Step 3: Add exported-schema migration RED tests**

Use `MigrationTestHelper` to create versions 1, 2, 3, and 4 and run to v5. Seed representative cache/watch/history rows. Name and assert the v2-to-v3 disposable history reset explicitly.

- [ ] **Step 4: Keep production-builder wiring coverage**

Retain the current production builder 3-to-5 test and current-schema index test. Remove duplicated hand-written schema construction only after all exported-schema starts pass.

- [ ] **Step 5: Run database verification**

Run: `./gradlew :core:database:testDebugUnitTest :core:database:compileDebugAndroidTestKotlin`

On an available device, run: `./gradlew :core:database:connectedDebugAndroidTest`

Expected: all supported migrations pass; device unavailability is recorded, not inferred.

- [ ] **Step 6: Commit**

```bash
git add core/database gradle/libs.versions.toml
git commit -m "test: verify exported Room migrations"
```

### Task 7: Synchronize live contracts and close Phase 1

**Files:**
- Modify: `docs/offline-strategy.md`
- Modify: `docs/architecture.md`
- Modify: `docs/test-strategy.md`
- Modify: `docs/verification-matrix.md`

- [ ] **Step 1: Document the exact contracts**

Document latest-started intent, atomic marker/rows reads, strict five-minute boundary, proxy fuel parity, HTTP retry table, reporter isolation, schema evidence, and intentional v2-to-v3 history reset. Keep exact commands in `verification-matrix.md` only.

- [ ] **Step 2: Run focused phase regression**

Run: `./gradlew :domain:station:test :core:network:test :core:observability:test :core:database:testDebugUnitTest :core:location:testDebugUnitTest :data:station:testDebugUnitTest :feature:station-list:testDebugUnitTest verifyModuleBoundaries`

- [ ] **Step 3: Verify docs and diff**

Run: `scripts/agent/verify.sh docs`

Run: `git diff --check`

- [ ] **Step 4: Commit live documentation**

```bash
git add docs/architecture.md docs/offline-strategy.md docs/test-strategy.md docs/verification-matrix.md
git commit -m "docs: define hardened station data contracts"
```

- [ ] **Step 5: Record Phase 1 evidence**

Run: `scripts/agent/verify.sh auto`

Expected: all automatically selected scopes pass at the same HEAD. Record any unavailable connected-device evidence separately before starting the state plan.
