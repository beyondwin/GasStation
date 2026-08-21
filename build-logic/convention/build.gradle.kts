import groovy.json.JsonSlurper
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.api.tasks.testing.TestResult

plugins {
    `kotlin-dsl`
}

group = "com.gasstation.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(libs.findLibrary("android-gradlePlugin").get())
    implementation(libs.findLibrary("kotlin-gradlePlugin").get())
    implementation(libs.findLibrary("kotlin-compose-gradlePlugin").get())
    implementation(libs.findLibrary("ksp-gradlePlugin").get())
    implementation(libs.findLibrary("hilt-gradlePlugin").get())
    implementation(libs.findLibrary("spotless-gradlePlugin").get())
    implementation(libs.findLibrary("roborazzi-gradlePlugin").get())
    implementation("org.jacoco:org.jacoco.core:0.8.15")
    implementation(libs.findLibrary("asm").get())
    implementation(libs.findLibrary("pitest-gradlePlugin").get())
    testImplementation(gradleTestKit())
    testImplementation(libs.findLibrary("junit").get())
}

// TestKit projects intentionally exercise dependency versions which are not selected by the
// production graph. Keep each seed isolated so Gradle conflict resolution cannot hide an older
// fixture version from dependency-verification metadata generation.
val testKitVerificationSeeds =
    listOf(
        "com.google.android:android:4.1.1.4",
        "androidx.annotation:annotation-experimental:1.1.0",
        "androidx.annotation:annotation-jvm:1.7.0",
        "androidx.core:core-ktx:1.8.0",
        "com.google.guava:guava:33.4.0-jre",
        "com.google.guava:guava:33.4.8-jre",
        "org.junit:junit-bom:5.10.2",
        "org.junit:junit-bom:5.11.0-M2",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2",
        "org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.9.0",
    ).mapIndexed { index, coordinate ->
        configurations.create("testKitVerificationSeed$index") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }.also { configuration -> dependencies.add(configuration.name, coordinate) }
    }

tasks.register("captureTestKitDependencyVerificationMetadata") {
    group = "verification"
    description = "Captures the reviewed TestKit-only dependency graph for checksum generation."
    doLast {
        testKitVerificationSeeds.forEach { configuration -> configuration.files.forEach(File::getName) }
    }
}

val finalizeTask9TestKitFailureEvidence =
    tasks.register("finalizeTask9TestKitFailureEvidence") {
        val workerTracePath = providers.environmentVariable("GASSTATION_TESTKIT_WORKER_TRACE")
        val failureOutputPath = providers.environmentVariable("GASSTATION_TESTKIT_FAILURE_OUTPUT")
        onlyIf { workerTracePath.isPresent && failureOutputPath.isPresent }
        doLast {
            val workerTrace = file(workerTracePath.get()).toPath()
            val liveStage = file(failureOutputPath.get()).toPath()
            check(Files.isDirectory(liveStage) && !Files.isSymbolicLink(liveStage)) {
                "TestKit live failure stage is missing or unsafe"
            }
            check(Files.isRegularFile(workerTrace) && !Files.isSymbolicLink(workerTrace)) {
                "TestKit worker trace is missing or unsafe"
            }
            val stagedWorker = liveStage.resolve("worker-events.tsv")
            Files.copy(workerTrace, stagedWorker)
            val artifacts =
                Files.list(liveStage).use { paths ->
                    paths
                        .filter { path -> path.fileName.toString() != "live-stage-manifest.json" }
                        .sorted()
                        .map { path ->
                            check(Files.isRegularFile(path) && !Files.isSymbolicLink(path)) {
                                "TestKit live failure artifact is unsafe"
                            }
                            val name = path.fileName.toString()
                            check(name == "worker-events.tsv" || Regex("TEST-[0-9a-f]{64}\\.xml").matches(name)) {
                                "TestKit live failure artifact identity differs"
                            }
                            val body = Files.readAllBytes(path)
                            val sha =
                                MessageDigest
                                    .getInstance("SHA-256")
                                    .digest(body)
                                    .joinToString("") { byte -> "%02x".format(byte) }
                            "{\"path\":\"$name\",\"sha256\":\"$sha\",\"size\":${body.size}}"
                        }.toList()
                }
            check(artifacts.any { row -> row.contains("TEST-") } && artifacts.any { row -> row.contains("worker-events.tsv") }) {
                "TestKit live failure inventory is incomplete"
            }
            val manifest =
                "{\"artifacts\":[${artifacts.joinToString(",")}],\"schemaVersion\":1,\"status\":\"SEALED\"}\n"
            val temporary = liveStage.resolve(".live-stage-manifest.json.tmp")
            Files.writeString(temporary, manifest, Charsets.UTF_8)
            Files.move(
                temporary,
                liveStage.resolve("live-stage-manifest.json"),
                StandardCopyOption.ATOMIC_MOVE,
            )
        }
    }

tasks.withType<Test>().configureEach {
    val outerTimeoutProperty = providers.gradleProperty("gasstation.task9LocalLinuxConventionTestTimeoutMinutes").orNull
    val outerTimeoutMarker = providers.environmentVariable("GASSTATION_TASK9_LOCAL_LINUX_OWNERSHIP_MARKER").orNull
    require((outerTimeoutProperty == null) == (outerTimeoutMarker == null)) {
        "Task-9 local Linux outer timeout property and ownership marker must be configured together"
    }
    var timeoutMinutes = 15L
    if (outerTimeoutProperty != null) {
        require(outerTimeoutProperty == "30") { "Task-9 local Linux outer timeout must be exact 30" }
        require(
            outerTimeoutMarker == "/evidence-work/task9-local-linux-ownership-marker.json" &&
                name == "test" && project.name == "convention" && project.path == ":",
        ) {
            "Task-9 local Linux outer timeout task or marker path differs"
        }
        val markerPath = file(outerTimeoutMarker).toPath()
        val governedFailureOutput = providers.environmentVariable("GASSTATION_TESTKIT_FAILURE_OUTPUT").orNull
        require(Files.isRegularFile(markerPath) && !Files.isSymbolicLink(markerPath)) {
            "Task-9 local Linux outer timeout marker is missing or unsafe"
        }
        require(
            Files.getPosixFilePermissions(markerPath) ==
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        ) {
            "Task-9 local Linux outer timeout marker must be exact mode 0600"
        }
        val markerBytes = Files.readString(markerPath, Charsets.UTF_8)
        val marker = JsonSlurper().parseText(markerBytes) as? Map<*, *>
            ?: throw GradleException("Task-9 local Linux outer timeout marker is malformed")
        val keys =
            listOf(
                "attemptId", "container", "context", "governedCommand",
                "outerConventionTestTimeoutMinutes", "ownershipMarkerSha256",
                "policySha256", "profile", "schemaVersion", "sourceCommit", "taskId", "taskPath",
            )
        require(marker.keys == keys.toSet()) { "Task-9 local Linux outer timeout marker fields differ" }
        require(
            marker["outerConventionTestTimeoutMinutes"] == 30 &&
                marker["schemaVersion"] == 1 &&
                marker["taskId"] == "quality-task-9-local-linux-evidence" &&
                marker["taskPath"] == ":build-logic:convention:test" &&
                marker["profile"] == "gasstation-task9-linux-amd64" &&
                marker["context"] == "colima-gasstation-task9-linux-amd64" &&
                marker["container"] == "gasstation-task9-evidence" &&
                governedFailureOutput != null &&
                file(governedFailureOutput).parentFile.path == "/evidence-work/testkit-failures" &&
                marker["governedCommand"] == file(governedFailureOutput).name &&
                marker["governedCommand"] in setOf("metadata-capture-1", "metadata-capture-2") &&
                Regex("attempt-[0-9]{6}").matches(marker["attemptId"].toString()) &&
                Regex("[0-9a-f]{40}").matches(marker["sourceCommit"].toString()) &&
                Regex("[0-9a-f]{64}").matches(marker["policySha256"].toString()) &&
                Regex("[0-9a-f]{64}").matches(marker["ownershipMarkerSha256"].toString()),
        ) {
            "Task-9 local Linux outer timeout marker identity differs"
        }
        fun jsonValue(value: Any?): String =
            when (value) {
                is Number -> value.toString()
                is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
                else -> throw GradleException("Task-9 local Linux outer timeout marker value type differs")
            }
        val canonical = keys.joinToString(prefix = "{", postfix = "}\n") { key -> "\"$key\":${jsonValue(marker[key])}" }
        require(markerBytes == canonical) { "Task-9 local Linux outer timeout marker is noncanonical" }
        timeoutMinutes = 30L
    }
    timeout.set(Duration.ofMinutes(timeoutMinutes))
    maxParallelForks = 5
    systemProperty("gasstation.convention.test.maxParallelForks", maxParallelForks)

    val workerTracePath = providers.environmentVariable("GASSTATION_TESTKIT_WORKER_TRACE").orNull
    val failureOutputPath = providers.environmentVariable("GASSTATION_TESTKIT_FAILURE_OUTPUT").orNull
    require((workerTracePath == null) == (failureOutputPath == null)) {
        "TestKit worker trace and failure output must be configured together"
    }
    if (workerTracePath != null) {
        check(failureOutputPath != null)
        val workerTrace = file(workerTracePath)
        val traceSession = workerTrace.parentFile
        val traceSessionId = traceSession.name.removePrefix("gasstation-metadata-capture-")
        require(
            workerTrace.name == "testkit-worker-events.tsv" &&
                traceSession.parentFile.path == "/tmp" &&
                traceSession.name.startsWith("gasstation-metadata-capture-") &&
                traceSessionId.isNotEmpty() &&
                traceSessionId.all { it.isLetterOrDigit() || it in "._-" },
        ) {
            "TestKit worker trace path is outside the sealed metadata session"
        }
        val failureOutput = file(failureOutputPath)
        require(
            failureOutput.parentFile.path == "/evidence-work/testkit-failures" &&
                failureOutput.name in setOf("metadata-capture-1", "metadata-capture-2"),
        ) {
            "TestKit failure output path is outside the sealed evidence volume"
        }
        val liveJunit = failureOutput
        val workerTraceLock = Any()
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val capturedOutput = mutableMapOf<Pair<String, String>, MutableMap<String, StringBuilder>>()
        fun encoded(value: String): String = encoder.encodeToString(value.toByteArray(Charsets.UTF_8))
        fun boundedLive(value: String): String {
            val bytes = value.toByteArray(Charsets.UTF_8)
            val limit = 128 * 1024
            if (bytes.size <= limit) return value
            val prefix = "[truncated-live-prefix]\n"
            val prefixBytes = prefix.toByteArray(Charsets.UTF_8)
            return prefix + bytes.copyOfRange(bytes.size - (limit - prefixBytes.size), bytes.size).toString(Charsets.UTF_8)
        }
        fun xmlEscaped(value: String): String =
            buildString {
                value.forEach { character ->
                    when (character) {
                        '&' -> append("&amp;")
                        '<' -> append("&lt;")
                        '>' -> append("&gt;")
                        '"' -> append("&quot;")
                        '\'' -> append("&apos;")
                        '\t', '\n', '\r' -> append(character)
                        else -> if (character.code in 0x20..0xD7FF || character.code in 0xE000..0xFFFD) {
                            append(character)
                        } else {
                            append('\uFFFD')
                        }
                    }
                }
            }
        fun workerName(descriptor: TestDescriptor): String {
            var current = descriptor.parent
            while (current != null) {
                if (Regex("Gradle Test Executor [1-9][0-9]*").matches(current.name)) return current.name
                current = current.parent
            }
            throw GradleException("TestKit worker identity is unavailable")
        }
        fun appendTrace(line: String) {
            synchronized(workerTraceLock) {
                workerTrace.appendText(line + "\n", Charsets.UTF_8)
            }
        }

        doFirst {
            workerTrace.parentFile.mkdirs()
            check(workerTrace.createNewFile()) { "TestKit worker trace already exists" }
            check(liveJunit.mkdir()) { "TestKit live JUnit stage already exists" }
        }
        finalizedBy(finalizeTask9TestKitFailureEvidence)
        addTestListener(
            object : TestListener {
                override fun beforeSuite(suite: TestDescriptor) = Unit

                override fun afterSuite(suite: TestDescriptor, result: TestResult) = Unit

                override fun beforeTest(testDescriptor: TestDescriptor) {
                    val className = testDescriptor.className ?: throw GradleException("TestKit class identity is unavailable")
                    appendTrace(
                        listOf(
                            "START",
                            encoded(workerName(testDescriptor)),
                            encoded(className),
                            encoded(testDescriptor.name),
                            System.currentTimeMillis().toString(),
                        ).joinToString("\t"),
                    )
                }

                override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
                    val className = testDescriptor.className ?: throw GradleException("TestKit class identity is unavailable")
                    val name = testDescriptor.name
                    val durationMillis = result.endTime - result.startTime
                    val durationSeconds =
                        "${durationMillis / 1000}.${(durationMillis % 1000).toString().padStart(3, '0')}"
                    synchronized(workerTraceLock) {
                        workerTrace.appendText(
                            listOf(
                                "END",
                                encoded(workerName(testDescriptor)),
                                encoded(className),
                                encoded(name),
                                result.resultType.name,
                                durationMillis.toString(),
                            ).joinToString("\t") + "\n",
                            Charsets.UTF_8,
                        )
                        val output = capturedOutput.remove(className to name).orEmpty()
                        val failure = result.resultType == TestResult.ResultType.FAILURE
                        val skipped = result.resultType == TestResult.ResultType.SKIPPED
                        val exceptions = result.exceptions
                        val exception = exceptions.firstOrNull()
                        val exceptionMessage =
                            boundedLive(exceptions.joinToString("\n") { throwable -> throwable.message.orEmpty() })
                        val exceptionBody =
                            boundedLive(
                                exceptions.joinToString("\nCaused by recorded exception:\n") { throwable ->
                                    throwable.stackTraceToString()
                                },
                            )
                        val suiteName = "$className#$name"
                        val xml =
                            buildString {
                                append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                                append(
                                    "<testsuite name=\"${xmlEscaped(suiteName)}\" tests=\"1\" " +
                                        "failures=\"${if (failure) 1 else 0}\" errors=\"0\" " +
                                        "skipped=\"${if (skipped) 1 else 0}\" time=\"$durationSeconds\">\n",
                                )
                                append(
                                    "  <testcase classname=\"${xmlEscaped(className)}\" " +
                                        "name=\"${xmlEscaped(name)}\" time=\"$durationSeconds\">",
                                )
                                when {
                                    failure -> {
                                        val type = exception?.javaClass?.name ?: "org.gradle.api.tasks.testing.TestFailure"
                                        val message = exceptionMessage.ifEmpty { "Test failed without a surfaced exception" }
                                        append(
                                            "<failure type=\"${xmlEscaped(type)}\" message=\"${xmlEscaped(message)}\">" +
                                                xmlEscaped(exceptionBody) + "</failure>",
                                        )
                                    }
                                    skipped -> append("<skipped/>")
                                }
                                append("</testcase>\n")
                                output["StdOut"]?.let { append("  <system-out>${xmlEscaped(it.toString())}</system-out>\n") }
                                output["StdErr"]?.let { append("  <system-err>${xmlEscaped(it.toString())}</system-err>\n") }
                                append("</testsuite>\n")
                            }
                        val identity = "$className\u0000$name"
                        val digest =
                            MessageDigest
                                .getInstance("SHA-256")
                                .digest(identity.toByteArray(Charsets.UTF_8))
                                .joinToString("") { byte -> "%02x".format(byte) }
                        val destination = liveJunit.toPath().resolve("TEST-$digest.xml")
                        val temporary = liveJunit.toPath().resolve(".TEST-$digest.xml.tmp")
                        Files.writeString(temporary, xml, Charsets.UTF_8)
                        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE)
                    }
                }
            },
        )
        addTestOutputListener(
            object : TestOutputListener {
                override fun onOutput(testDescriptor: TestDescriptor, outputEvent: TestOutputEvent) {
                    val className = testDescriptor.className ?: throw GradleException("TestKit output class identity is unavailable")
                    synchronized(workerTraceLock) {
                        val name = testDescriptor.name
                        workerTrace.appendText(
                            listOf(
                                "OUTPUT",
                                encoded(workerName(testDescriptor)),
                                encoded(className),
                                encoded(name),
                                outputEvent.destination.name,
                                encoded(outputEvent.message),
                            ).joinToString("\t") + "\n",
                            Charsets.UTF_8,
                        )
                        val destinationOutput =
                            capturedOutput
                            .getOrPut(className to name) { mutableMapOf() }
                            .getOrPut(outputEvent.destination.name) { StringBuilder() }
                        check(
                            (destinationOutput.toString() + outputEvent.message).toByteArray(Charsets.UTF_8).size <= 64 * 1024,
                        ) {
                            "TestKit owned output exceeds the live evidence limit"
                        }
                        destinationOutput.append(outputEvent.message)
                    }
                }
            },
        )
    }
}

gradlePlugin {
    plugins {
        register("gasStationAndroidApplicationCompose") {
            id = "gasstation.android.application.compose"
            implementationClass = "GasStationAndroidApplicationComposeConventionPlugin"
        }
        register("gasStationAndroidLibrary") {
            id = "gasstation.android.library"
            implementationClass = "GasStationAndroidLibraryConventionPlugin"
        }
        register("gasStationAndroidLibraryCompose") {
            id = "gasstation.android.library.compose"
            implementationClass = "GasStationAndroidLibraryComposeConventionPlugin"
        }
        register("gasStationJvmLibrary") {
            id = "gasstation.jvm.library"
            implementationClass = "GasStationJvmLibraryConventionPlugin"
        }
        register("gasStationAndroidHilt") {
            id = "gasstation.android.hilt"
            implementationClass = "GasStationAndroidHiltConventionPlugin"
        }
        register("gasStationAndroidRoom") {
            id = "gasstation.android.room"
            implementationClass = "GasStationAndroidRoomConventionPlugin"
        }
        register("androidSpotless") {
            id = "gasstation.spotless"
            implementationClass = "GasStationSpotlessConventionPlugin"
        }
        register("roborazzi") {
            id = "gasstation.roborazzi"
            implementationClass = "GasStationRoborazziConventionPlugin"
        }
        register("gasStationRootQuality") {
            id = "gasstation.root.quality"
            implementationClass =
                "com.gasstation.buildlogic.quality.GasStationRootQualityConventionPlugin"
        }
        register("gasStationJvmMutation") {
            id = "gasstation.jvm.mutation"
            implementationClass =
                "com.gasstation.buildlogic.quality.mutation.GasStationJvmMutationConventionPlugin"
        }
    }
}
