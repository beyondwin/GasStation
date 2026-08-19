package com.gasstation.buildlogic.testing

import java.io.File
import java.util.Properties

data class CoverageFixture(
    val includeUnownedEmptyModule: Boolean = false,
    val assertNoLocationClassCollection: Boolean = false,
    val jvmOnly: Boolean = false,
)

fun GradlePluginTestProject.writeCoverageFixture(
    fixture: CoverageFixture = CoverageFixture(),
): GradlePluginTestProject {
    val modules =
        buildList {
            add(":sample:jvm")
            add(":benchmark")
            if (!fixture.jvmOnly) {
                add(":android")
                add(":app")
            }
            if (fixture.includeUnownedEmptyModule) add(":empty")
        }
    writeSettings(
        buildString {
            appendLine("pluginManagement {")
            appendLine("    repositories { google(); mavenCentral(); gradlePluginPortal() }")
            appendLine("}")
            appendLine("dependencyResolutionManagement {")
            appendLine("    repositories { google(); mavenCentral(); gradlePluginPortal() }")
            appendLine("}")
            appendLine("rootProject.name = \"coverage-fixture\"")
            appendLine("val activeModulePaths: List<String> = listOf(")
            modules.forEach { appendLine("    \"$it\",") }
            appendLine(")")
            appendLine("include(*activeModulePaths.toTypedArray())")
            appendLine(
                "gradle.extensions.extraProperties.set(\"gasstation.activeModulePaths\", activeModulePaths.toList())",
            )
        },
    )
    writeFile("local.properties", "sdk.dir=${coverageAndroidSdkDirectory().coveragePropertiesValue()}")
    writeFile("gradle/libs.versions.toml", COVERAGE_VERSION_CATALOG)
    writeBuildFile(
        """
        import com.gasstation.buildlogic.quality.coverage.CoverageXmlReportTask

        plugins {
            id("gasstation.root.quality")
        }

        tasks.register("assertCoverageTopology") {
            dependsOn("coverageXmlReport")
            doLast {
                val reports = allprojects.flatMap { project ->
                    project.tasks.matching {
                        it.name != "coverageXmlReport" && it.name.startsWith("coverage") && it.name.endsWith("XmlReport")
                    }.toList()
                }
                check(reports.size == 4) { "expected exactly four coverage XML reports, found ${'$'}{reports.size}" }
                check(reports.all { it is CoverageXmlReportTask }) { "coverage XML report is not typed" }
            }
        }
        """.trimIndent(),
    )
    writeFile("config/robolectric/robolectric.properties", "sdk=36")
    writeFile(
        ".github/workflows/android.yml",
        """
        name: Android CI
        env:
          CI_JAVA_VERSION: "21"
        jobs:
          test:
            steps:
              - uses: actions/setup-java@v5
                with:
                  java-version: ${'$'}{{ env.CI_JAVA_VERSION }}
        """.trimIndent(),
    )
    writeFile(
        "config/quality/coverage-policy.json",
        if (fixture.jvmOnly) COVERAGE_JVM_MEASUREMENT_POLICY else COVERAGE_MEASUREMENT_POLICY,
    )
    writeFile("config/quality/coverage-baseline.json", COVERAGE_STUB_BASELINE)
    writeFile("scripts/quality/verify_coverage.py", COVERAGE_STUB_VERIFIER)
    writeFile("scripts/quality/real_verify_coverage.py", coverageRealVerifierSource().readText())
    writeFile(
        "scripts/quality/check_real_boundary.py",
        """
        import sys
        from pathlib import Path
        import real_verify_coverage as coverage

        root = Path(__file__).resolve().parents[2]
        try:
            manifest = coverage.validate_manifest_schema(
                coverage.read_json(root / "build/reports/coverage/report-manifest.json")
            )
            head = coverage._git(root, "rev-parse", "HEAD").decode().strip()
            if manifest["sourceCommit"] != head:
                raise coverage.CoverageError("manifest sourceCommit differs from fixture HEAD")
            for relative in manifest["entries"]:
                entry = coverage.validate_entry_schema(coverage.read_json(root / relative), relative)
                if entry["sourceCommit"] != head:
                    raise coverage.CoverageError("entry sourceCommit differs from fixture HEAD")
                coverage.validate_entry_evidence(root, entry)
        except coverage.CoverageError as error:
            print(error)
            sys.exit(1)
        """.trimIndent(),
    )

    writeFile(
        "sample/jvm/build.gradle.kts",
        """
        plugins { id("gasstation.jvm.library") }
        """.trimIndent(),
    )
    writeFile(
        "sample/jvm/src/main/kotlin/fixture/JvmLogic.kt",
        """
        package fixture

        class JvmLogic {
            fun covered(value: Int): Int = if (value > 0) value + 1 else 0
        }
        """.trimIndent(),
    )
    writeFile(
        "sample/jvm/src/test/kotlin/fixture/JvmLogicTest.kt",
        """
        package fixture

        import kotlin.test.Test
        import kotlin.test.assertEquals

        class JvmLogicTest {
            @Test fun coversPositiveBranch() { assertEquals(2, JvmLogic().covered(1)) }
        }
        """.trimIndent(),
    )

    if (!fixture.jvmOnly) {
        writeFile(
            "android/build.gradle.kts",
            buildString {
            if (fixture.assertNoLocationClassCollection) {
                appendLine("import org.gradle.testing.jacoco.plugins.JacocoTaskExtension")
                appendLine("import org.gradle.api.tasks.testing.Test")
                appendLine()
            }
            appendLine("plugins { id(\"gasstation.android.library\") }")
            appendLine("android { namespace = \"fixture.android\" }")
            if (fixture.assertNoLocationClassCollection) {
                appendLine()
                appendLine(noLocationAssertion(setOf("testDebugUnitTest")))
            }
            },
        )
        writeFile("android/src/main/AndroidManifest.xml", "<manifest />")
        writeFile(
            "android/src/main/java/fixture/android/AndroidLogic.java",
            javaLogic("AndroidLogic"),
        )
        writeFile(
            "android/src/test/java/fixture/android/AndroidLogicTest.java",
            javaTest("AndroidLogic", "AndroidLogicTest"),
        )

    writeFile(
        "app/build.gradle.kts",
        buildString {
            if (fixture.assertNoLocationClassCollection) {
                appendLine("import org.gradle.testing.jacoco.plugins.JacocoTaskExtension")
                appendLine("import org.gradle.api.tasks.testing.Test")
                appendLine()
            }
            appendLine("""
        plugins { id("gasstation.android.application.compose") }
        android {
            namespace = "fixture.app"
            defaultConfig { applicationId = "fixture.app" }
            flavorDimensions += "environment"
            productFlavors {
                create("demo") { dimension = "environment" }
                create("prod") { dimension = "environment" }
            }
        }
        """.trimIndent())
            if (fixture.assertNoLocationClassCollection) {
                appendLine()
                appendLine(noLocationAssertion(setOf("testDemoDebugUnitTest", "testProdDebugUnitTest")))
            }
        },
    )
        writeFile("app/src/main/AndroidManifest.xml", "<manifest />")
        writeFile("app/src/main/java/fixture/app/SharedLogic.java", javaLogic("SharedLogic"))
        writeFile("app/src/demo/java/fixture/app/DemoLogic.java", javaLogic("DemoLogic"))
        writeFile("app/src/prod/java/fixture/app/ProdLogic.java", javaLogic("ProdLogic"))
        writeFile(
            "app/src/test/java/fixture/app/SharedLogicTest.java",
            javaTest("SharedLogic", "SharedLogicTest"),
        )
        writeFile(
            "app/src/testDemo/java/fixture/app/DemoLogicTest.java",
            javaTest("DemoLogic", "DemoLogicTest"),
        )
        writeFile(
            "app/src/testProd/java/fixture/app/ProdLogicTest.java",
            javaTest("ProdLogic", "ProdLogicTest"),
        )
    }

    writeFile("benchmark/build.gradle.kts", "plugins { `java-library` }")
    writeFile(
        "benchmark/src/main/java/fixture/benchmark/BenchmarkOnly.java",
        "package fixture.benchmark; public final class BenchmarkOnly {}",
    )
    if (fixture.includeUnownedEmptyModule) {
        writeFile("empty/.gitkeep", "")
    }
    runFixtureGit("init", "-q")
    runFixtureGit("config", "user.name", "Coverage Fixture")
    runFixtureGit("config", "user.email", "coverage-fixture@example.invalid")
    runFixtureGit("add", ".")
    runFixtureGit("commit", "-qm", "fixture")
    return this
}

private fun GradlePluginTestProject.runFixtureGit(vararg arguments: String) {
    val process = ProcessBuilder(listOf("git") + arguments).directory(projectDir).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "git ${arguments.joinToString(" ")} failed: $output" }
}

private fun noLocationAssertion(expected: Set<String>): String =
    """
    tasks.register("assertCoverageNoLocationScope") {
        dependsOn(${expected.sorted().joinToString { "tasks.named(\"$it\")" }})
        doLast {
            tasks.withType<Test>().forEach { candidate ->
                val actual = candidate.extensions.getByType(JacocoTaskExtension::class.java).isIncludeNoLocationClasses
                check(actual == (candidate.name in ${expected.sorted().joinToString(prefix = "setOf(", postfix = ")") { "\"$it\"" }})) {
                    "unexpected no-location scope for ${'$'}{candidate.path}: ${'$'}actual"
                }
            }
        }
    }
    """.trimIndent()

private fun javaLogic(name: String): String =
    """
    package fixture.${if (name == "AndroidLogic") "android" else "app"};

    public final class $name {
        public int covered(int value) { return value > 0 ? value + 1 : 0; }
    }
    """.trimIndent()

private fun javaTest(
    subject: String,
    testName: String,
): String =
    """
    package fixture.${if (subject == "AndroidLogic") "android" else "app"};

    import static org.junit.Assert.assertEquals;
    import org.junit.Test;

    public final class $testName {
        @Test public void coversPositiveBranch() { assertEquals(2, new $subject().covered(1)); }
    }
    """.trimIndent()

private fun coverageAndroidSdkDirectory(): String {
    val environmentSdk =
        sequenceOf("ANDROID_HOME", "ANDROID_SDK_ROOT")
            .mapNotNull(System::getenv)
            .firstOrNull(String::isNotBlank)
    if (environmentSdk != null) return File(environmentSdk).canonicalPath
    val localProperties =
        generateSequence(File(System.getProperty("user.dir")).canonicalFile, File::getParentFile)
            .map { directory -> directory.resolve("local.properties") }
            .firstOrNull(File::isFile)
    require(localProperties != null) { "Coverage fixture requires an Android SDK" }
    val properties = Properties().apply { localProperties.inputStream().use(::load) }
    return File(properties.getProperty("sdk.dir").orEmpty()).canonicalPath.also { sdk ->
        require(File(sdk).isDirectory) { "Android SDK directory is missing: $sdk" }
    }
}

private fun coverageRealVerifierSource(): File =
    generateSequence(File(System.getProperty("user.dir")).canonicalFile, File::getParentFile)
        .map { directory -> directory.resolve("scripts/quality/verify_coverage.py") }
        .firstOrNull(File::isFile)
        ?: error("Coverage fixture requires the production coverage verifier")

private fun String.coveragePropertiesValue(): String = replace("\\", "\\\\").replace(":", "\\:")

private val COVERAGE_VERSION_CATALOG =
    """
    [versions]
    compileSdk = "37"
    minSdk = "24"
    targetSdk = "36"
    desugarJdkLibs = "2.1.5"
    coreKtx = "1.19.0"
    lifecycle = "2.11.0"
    activityCompose = "1.13.0"
    composeBom = "2026.06.01"
    coroutines = "1.11.0"
    junit4 = "4.13.2"
    androidxJunit = "1.3.0"
    espresso = "3.7.0"
    androidxTestCore = "1.7.0"
    robolectric = "4.16.1"
    kotlin = "2.4.10"

    [libraries]
    android-desugarJdkLibs = { module = "com.android.tools:desugar_jdk_libs", version.ref = "desugarJdkLibs" }
    androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
    androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
    androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
    androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
    androidx-ui = { module = "androidx.compose.ui:ui" }
    androidx-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
    androidx-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
    androidx-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
    androidx-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
    androidx-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
    androidx-material3 = { module = "androidx.compose.material3:material3" }
    junit = { module = "junit:junit", version.ref = "junit4" }
    kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test-junit", version.ref = "kotlin" }
    androidx-junit = { module = "androidx.test.ext:junit", version.ref = "androidxJunit" }
    androidx-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }
    kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
    androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTestCore" }
    robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
    """.trimIndent()

private val COVERAGE_MEASUREMENT_POLICY =
    """
    {
      "schemaVersion": 1,
      "enforcementMode": "measurement",
      "activeModules": [":android", ":app", ":benchmark", ":sample:jvm"],
      "excludedModules": [{"module": ":benchmark", "reason": "connected macrobenchmark and device performance evidence owns this module"}],
      "reports": [],
      "units": [],
      "changedThresholds": {"lineBasisPoints": 8000, "branchBasisPoints": 7000},
      "maximumBaselineDropBasisPoints": 50,
      "maximumFloorRaiseBasisPoints": 200,
      "nonExecutableExceptions": []
    }
    """.trimIndent()

private val COVERAGE_JVM_MEASUREMENT_POLICY =
    COVERAGE_MEASUREMENT_POLICY.replace(
        "[\":android\", \":app\", \":benchmark\", \":sample:jvm\"]",
        "[\":benchmark\", \":sample:jvm\"]",
    )

private val COVERAGE_STUB_BASELINE =
    """
    {"schemaVersion":1,"sourceCommit":"1111111111111111111111111111111111111111","policySha256":"${"0".repeat(64)}","manifestSchemaVersion":1,"predecessor":null,"reports":[],"units":[]}
    """.trimIndent()

private val COVERAGE_STUB_VERIFIER =
    """
    #!/usr/bin/env python3
    import json
    import pathlib
    import subprocess
    import sys

    args = sys.argv[1:]
    source = args[args.index("--source-commit") + 1]
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    if source != head:
        print("stale source commit", file=sys.stderr)
        raise SystemExit(1)
    summary = pathlib.Path(args[args.index("--output") + 1])
    marker = summary.with_name("stub-invocations.txt")
    count = int(marker.read_text() if marker.exists() else "0") + 1
    marker.parent.mkdir(parents=True, exist_ok=True)
    marker.write_text(str(count))
    summary.with_name("stub-arguments.txt").write_text(" ".join(args))
    summary.write_text(json.dumps({"schemaVersion": 1, "status": "pass", "invocation": count}, sort_keys=True) + "\n")
    """.trimIndent()
