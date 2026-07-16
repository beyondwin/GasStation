# Demo Seed Verification Risk Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a deterministic, credential-free verification path for the committed demo seed while preserving authenticated live regeneration.

**Architecture:** A pure verifier in `tools:demo-seed` validates the same `DemoSeedDocument` produced by the generator. A small CLI and Gradle task apply it to the committed asset, while unit tests validate both failure behavior and the real asset used by `demo`.

**Tech Stack:** Kotlin/JVM, Gradle Kotlin DSL, Gson, JUnit, existing GasStation demo-seed models.

## Global Constraints

- Do not commit, print, or synthesize an Opinet API key.
- `generateDemoSeed` remains the only live refresh path and must keep failing fast without `opinet.apikey`.
- `verifyDemoSeedAsset` must make no network request and require no credential.
- Do not add dependencies or move policy into the Android app runtime.
- Preserve the checked-in RTO/ETC demo identities and the approved 15-query matrix.
- Do not push, create a PR, or deploy.

---

### Task 1: Deterministic committed demo-seed verification

**Files:**
- Create: `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedAssetVerifier.kt`
- Create: `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedAssetVerifierMain.kt`
- Create: `tools/demo-seed/src/test/kotlin/com/gasstation/tools/demoseed/DemoSeedAssetVerifierTest.kt`
- Create: `tools/demo-seed/src/test/kotlin/com/gasstation/tools/demoseed/CommittedDemoSeedAssetTest.kt`
- Modify: `tools/demo-seed/build.gradle.kts`
- Modify: `README.md`
- Modify: `docs/verification-matrix.md`

**Interfaces:**
- Consumes: `DemoSeedDocument`, `DemoSeedQueryMatrix.all()`, `DemoPortfolioStations.forQuery()`, and `DemoSeedJsonWriter.gson`.
- Produces: `DemoSeedAssetVerifier.verify(document: DemoSeedDocument): Unit` and Gradle task `:tools:demo-seed:verifyDemoSeedAsset`.

- [ ] **Step 1: Write the failing verifier and committed-asset tests**

Create tests that call the not-yet-existing verifier:

```kotlin
class DemoSeedAssetVerifierTest {
    @Test
    fun `rejects a document with no approved query matrix`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            DemoSeedAssetVerifier.verify(
                DemoSeedDocument(
                    seedVersion = 1,
                    generatedAtEpochMillis = 1_770_000_000_000,
                    origin = DemoSeedOriginJson(
                        label = DemoSeedGenerator.GANGNAM_STATION_EXIT_2_LABEL,
                        latitude = 37.497927,
                        longitude = 127.027583,
                    ),
                    queries = emptyList(),
                    history = emptyList(),
                ),
            )
        }

        assertTrue(failure.message.orEmpty().contains("query matrix"))
    }
}
```

```kotlin
class CommittedDemoSeedAssetTest {
    @Test
    fun `committed demo asset satisfies generator invariants`() {
        val asset = File(requireNotNull(System.getProperty("demo.seed.asset.path")))
        val document = DemoSeedJsonWriter.gson.fromJson(asset.readText(), DemoSeedDocument::class.java)

        DemoSeedAssetVerifier.verify(document)
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew :tools:demo-seed:test --tests '*DemoSeedAssetVerifierTest' --tests '*CommittedDemoSeedAssetTest'
```

Expected: Kotlin compilation fails because `DemoSeedAssetVerifier` does not exist.

- [ ] **Step 3: Implement the pure verifier**

Implement `DemoSeedAssetVerifier.verify` with fail-fast `require` checks:

```kotlin
object DemoSeedAssetVerifier {
    private const val EXPECTED_SEED_VERSION = 1
    private val approvedOrigin = Coordinates(latitude = 37.497927, longitude = 127.027583)

    fun verify(document: DemoSeedDocument) {
        require(document.seedVersion == EXPECTED_SEED_VERSION) { "Unexpected demo seed version." }
        require(document.origin.label == DemoSeedGenerator.GANGNAM_STATION_EXIT_2_LABEL) {
            "Unexpected demo seed origin label."
        }
        require(document.origin.latitude == approvedOrigin.latitude && document.origin.longitude == approvedOrigin.longitude) {
            "Unexpected demo seed origin coordinates."
        }

        val expectedQueryKeys = DemoSeedQueryMatrix.all().map { it.radius.meters to it.fuelType.name }.toSet()
        val actualQueryKeys = document.queries.map { it.radiusMeters to it.fuelType }
        require(actualQueryKeys.size == expectedQueryKeys.size && actualQueryKeys.toSet() == expectedQueryKeys) {
            "Demo seed query matrix must contain every approved combination exactly once."
        }
        document.queries.forEach { snapshot ->
            require(snapshot.stations.map(DemoSeedStation::stationId).distinct().size == snapshot.stations.size) {
                "Duplicate station id in ${snapshot.radiusMeters}/${snapshot.fuelType}."
            }
        }

        val expectedHistory = linkedMapOf<Pair<String, String>, DemoSeedStation>()
        document.queries.forEach { snapshot ->
            snapshot.stations.forEach { station ->
                expectedHistory.putIfAbsent(snapshot.fuelType to station.stationId, station)
            }
        }
        val actualHistoryKeys = document.history.map { it.fuelType to it.stationId }
        require(actualHistoryKeys.size == actualHistoryKeys.distinct().size) { "Duplicate demo seed history key." }
        require(actualHistoryKeys.toSet() == expectedHistory.keys) { "Demo seed history keys do not match query stations." }

        val historyByKey = document.history.associateBy { it.fuelType to it.stationId }
        expectedHistory.forEach { (key, station) ->
            val entries = requireNotNull(historyByKey[key]).entries
            require(entries.size == 3) { "History $key must contain three points." }
            require(entries.last().priceWon == station.priceWon) { "History $key latest price does not match the query projection." }
            require(entries.last().fetchedAtEpochMillis == document.generatedAtEpochMillis) {
                "History $key latest timestamp does not match generatedAtEpochMillis."
            }
        }

        val portfolioSnapshot = document.queries.single {
            it.radiusMeters == SearchRadius.KM_3.meters && it.fuelType == FuelType.GASOLINE.name
        }
        DemoPortfolioStations.forQuery(SearchRadius.KM_3, FuelType.GASOLINE).forEach { expected ->
            val actual = portfolioSnapshot.stations.singleOrNull { it.stationId == expected.stationId }
            require(actual != null && actual.brandCode == expected.brandCode && actual.name == expected.name) {
                "Missing approved portfolio station ${expected.stationId}."
            }
        }
    }
}
```

- [ ] **Step 4: Add the no-key CLI and Gradle wiring**

Add a main function that accepts exactly one asset path, parses it, calls the verifier, and prints only the verified path. Configure tests with `demo.seed.asset.path` and register:

```kotlin
tasks.register<JavaExec>("verifyDemoSeedAsset") {
    group = "verification"
    description = "Verifies the committed demo seed without network access or credentials."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.gasstation.tools.demoseed.DemoSeedAssetVerifierMainKt")
    args(outputFile.asFile.absolutePath)
}
```

- [ ] **Step 5: Run focused GREEN verification**

Run:

```bash
./gradlew \
  :tools:demo-seed:test \
  :tools:demo-seed:verifyDemoSeedAsset \
  :app:testDemoDebugUnitTest --tests '*DemoSeedAssetLoaderTest*'
```

Expected: `BUILD SUCCESSFUL`; the verifier prints the committed asset path and no key is requested.

- [ ] **Step 6: Document the live/deterministic boundary**

Update README and `docs/verification-matrix.md` with both commands:

```bash
# Deterministic, required, no key/network
./gradlew :tools:demo-seed:verifyDemoSeedAsset

# Operator-only live refresh, requires opinet.apikey
./gradlew :tools:demo-seed:generateDemoSeed
```

State that the existing `:tools:demo-seed:test` CI job validates the committed asset.

- [ ] **Step 7: Run the complete merge gate**

Run:

```bash
git diff --check
./gradlew \
  spotlessCheck lint \
  :tools:demo-seed:test \
  :tools:demo-seed:verifyDemoSeedAsset \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  verifyModuleBoundaries \
  verifyNoDeprecatedComposeTestApis \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  :app:assembleProdRelease \
  :benchmark:assemble
```

Expected: all tasks pass and `git status --short` lists only the planned source, test, build-script, and documentation changes.

- [ ] **Step 8: Commit the implementation**

```bash
git add tools/demo-seed README.md docs/verification-matrix.md
git commit -m "test: verify committed demo seed deterministically"
```

After the commit, switch to local `main`, fast-forward merge `codex/close-demo-seed-risk`, rerun the same complete gate, and delete only the temporary local branch. Do not touch `origin/main`.
