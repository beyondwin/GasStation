package com.gasstation.buildlogic.testing

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties
import org.gradle.testkit.runner.GradleRunner

internal enum class GradlePluginTestRunnerMode {
    NORMAL,
    CONFIGURATION_CACHE,
    ADVERSARIAL,
}

internal data class DecodedTestKitPropertyArgument(
    val option: String,
    val key: String,
    val value: String,
    val sourceArgumentIndex: Int,
    val consumedArgumentCount: Int,
)

internal data class PreparedGradleRunnerArguments(
    val arguments: List<String>,
    val callerProperties: List<DecodedTestKitPropertyArgument>,
)

class GradlePluginTestProject private constructor(
    val projectDir: File,
    val testKitDir: File,
    val gradleUserHomeDir: File,
) {
    fun writeSettings(content: String = DEFAULT_SETTINGS): GradlePluginTestProject =
        writeFile("settings.gradle.kts", content)

    fun writeBuildFile(content: String): GradlePluginTestProject =
        writeFile("build.gradle.kts", content)

    fun writeFile(relativePath: String, content: String): GradlePluginTestProject = apply {
        require(relativePath.isNotBlank()) { "Fixture path must not be blank" }
        val requestedPath = Path.of(relativePath)
        require(!requestedPath.isAbsolute) { "Fixture path must be relative: $relativePath" }
        require(requestedPath.none { it.toString() == ".." }) {
            "Fixture path must not contain '..': $relativePath"
        }

        val canonicalProjectDir = projectDir.canonicalFile
        val target = canonicalProjectDir.resolve(relativePath).canonicalFile
        require(target != canonicalProjectDir && target.toPath().startsWith(canonicalProjectDir.toPath())) {
            "Fixture path resolves outside project directory: $relativePath"
        }
        require(target.parentFile.mkdirs() || target.parentFile.isDirectory) {
            "Unable to create fixture parent directory: ${target.parentFile}"
        }
        target.writeText(content.trimEnd('\r', '\n') + "\n", UTF_8)
    }

    fun runner(vararg arguments: String): GradleRunner {
        val prepared =
            prepareArguments(
                arguments = arguments.toList(),
                mode = GradlePluginTestRunnerMode.NORMAL,
                selectedGradleUserHome = gradleUserHomeDir,
            )
        return createRunner(prepared.arguments)
    }

    fun configurationCacheRunner(vararg arguments: String): GradleRunner {
        val prepared =
            prepareArguments(
                arguments = arguments.toList(),
                mode = GradlePluginTestRunnerMode.CONFIGURATION_CACHE,
                selectedGradleUserHome = gradleUserHomeDir,
            )
        return createRunner(prepared.arguments)
    }

    private fun createRunner(arguments: List<String>): GradleRunner {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withTestKitDir(testKitDir)
            .withGradleVersion(EXACT_GRADLE_VERSION)
            .withPluginClasspath()
            .withEnvironment(sanitizedEnvironment())
            .withArguments(arguments)
    }

    fun adversarialRunner(gradleUserHome: File, vararg arguments: String): GradleRunner {
        val prepared =
            prepareArguments(
                arguments = arguments.toList(),
                mode = GradlePluginTestRunnerMode.ADVERSARIAL,
                selectedGradleUserHome = gradleUserHome,
            )
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withTestKitDir(testKitDir)
            .withGradleVersion(EXACT_GRADLE_VERSION)
            .withPluginClasspath()
            .withEnvironment(sanitizedEnvironment())
            .withArguments(prepared.arguments)
    }

    internal fun prepareArgumentsForTesting(
        mode: GradlePluginTestRunnerMode,
        selectedGradleUserHome: File = gradleUserHomeDir,
        vararg arguments: String,
    ): PreparedGradleRunnerArguments =
        prepareArguments(arguments.toList(), mode, selectedGradleUserHome)

    private fun prepareArguments(
        arguments: List<String>,
        mode: GradlePluginTestRunnerMode,
        selectedGradleUserHome: File,
    ): PreparedGradleRunnerArguments {
        require(System.getProperty(WORKER_PROPERTY) == null) {
            "Runner executor JVM property conflicts with harness policy: $WORKER_PROPERTY"
        }
        val callerProperties = validateWorkerControls(arguments, allowAuthoritativeCap = false)
        arguments.forEach(::requireNonConflictingArgument)
        validateGradleProperties(projectDir.resolve("gradle.properties"), "fixture project")
        validateGradleProperties(selectedGradleUserHome.resolve("gradle.properties"), "fixture Gradle user home")

        val finalArguments = arguments + deterministicArguments(mode, selectedGradleUserHome)
        val finalProperties = validateWorkerControls(finalArguments, allowAuthoritativeCap = true)
        require(finalArguments.lastOrNull() == MAX_WORKERS_ARGUMENT) {
            "Fixture-owned worker cap must be the final deterministic argument"
        }
        require(finalArguments.count { it == MAX_WORKERS_ARGUMENT } == 1) {
            "Fixture-owned worker cap must appear exactly once"
        }
        require(finalProperties.none { it.key == WORKER_PROPERTY }) {
            "Final runner arguments contain a worker-property override"
        }
        return PreparedGradleRunnerArguments(finalArguments, callerProperties)
    }

    private fun deterministicArguments(
        mode: GradlePluginTestRunnerMode,
        selectedGradleUserHome: File,
    ): List<String> {
        val configurationCacheArguments =
            when (mode) {
                GradlePluginTestRunnerMode.CONFIGURATION_CACHE ->
                    listOf("--configuration-cache", "--configuration-cache-problems=fail")
                GradlePluginTestRunnerMode.NORMAL,
                GradlePluginTestRunnerMode.ADVERSARIAL,
                -> listOf("--no-configuration-cache")
            }
        return configurationCacheArguments +
            listOf(
                "--no-build-cache",
                "--warning-mode=fail",
                "--stacktrace",
                "--gradle-user-home=${selectedGradleUserHome.canonicalPath}",
                "-Dorg.gradle.java.installations.auto-detect=false",
                "-Dorg.gradle.java.installations.auto-download=false",
                "-Dorg.gradle.java.installations.paths=${compileJavaHome.absolutePath},${runtimeJavaHome.absolutePath}",
                MAX_WORKERS_ARGUMENT,
            )
    }

    private val compileJavaHome: File
        get() = resolveJavaHome("JAVA_HOME_17_X64", 17)

    private val runtimeJavaHome: File
        get() = resolveJavaHome("JAVA_HOME_21_X64", 21)

    private fun resolveJavaHome(environmentName: String, major: Int): File {
        System.getenv(environmentName)?.takeIf(String::isNotBlank)?.let { return File(it).canonicalFile }
        val candidates =
            sequenceOf(
                File(System.getProperty("java.home")),
                File("/Library/Java/JavaVirtualMachines"),
                File(System.getProperty("user.home"), ".gradle/jdks"),
            ).flatMap { root ->
                if (!root.exists()) emptySequence() else root.walkTopDown().maxDepth(5)
            }.filter { it.isDirectory && it.resolve("bin/java").isFile && it.resolve("release").isFile }
        return candidates.firstOrNull { home ->
            Regex("(?m)^JAVA_VERSION=\"$major(?:[.\"].*)?\"")
                .containsMatchIn(home.resolve("release").readText())
        }?.canonicalFile ?: error("TestKit requires a Java $major home or $environmentName")
    }

    private fun sanitizedEnvironment(): Map<String, String> {
        val readOnlyDependencyCache =
            System.getenv("GRADLE_RO_DEP_CACHE")
                ?.takeIf(String::isNotBlank)
                ?.let(::File)
                ?.canonicalFile
                ?: error("TestKit requires the prepared read-only dependency cache")
        require(readOnlyDependencyCache.isDirectory && !Files.isSymbolicLink(readOnlyDependencyCache.toPath())) {
            "TestKit read-only dependency cache is missing or unsafe: $readOnlyDependencyCache"
        }
        val seedManifest = readOnlyDependencyCache.resolve("seed-manifest.tsv")
        require(seedManifest.isFile && !Files.isSymbolicLink(seedManifest.toPath())) {
            "TestKit read-only dependency-cache manifest is missing or unsafe: $seedManifest"
        }
        val allowed = mutableMapOf(
            "GRADLE_RO_DEP_CACHE" to readOnlyDependencyCache.canonicalPath,
            "HOME" to projectDir.parentFile.resolve("home").also(File::mkdirs).canonicalPath,
            "JAVA_HOME" to runtimeJavaHome.canonicalPath,
            "JAVA_HOME_17_X64" to compileJavaHome.canonicalPath,
            "JAVA_HOME_21_X64" to runtimeJavaHome.canonicalPath,
            "LANG" to "C.UTF-8",
            "LC_ALL" to "C.UTF-8",
            "PATH" to runtimeJavaHome.resolve("bin").canonicalPath + File.pathSeparator + "/usr/bin:/bin:/usr/sbin:/sbin",
            "TZ" to "UTC",
        )
        listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").forEach { name ->
            System.getenv(name)?.takeIf(String::isNotBlank)?.let { allowed[name] = it }
        }
        require(FORBIDDEN_WORKER_ENVIRONMENT_KEYS.none(allowed::containsKey)) {
            "Nested TestKit environment contains a forbidden worker-control channel"
        }
        require(allowed.values.none { it.contains(WORKER_PROPERTY) }) {
            "Nested TestKit environment value contains a worker-property override"
        }
        return allowed.toMap()
    }

    private fun validateWorkerControls(
        arguments: List<String>,
        allowAuthoritativeCap: Boolean,
    ): List<DecodedTestKitPropertyArgument> {
        val decoded = mutableListOf<DecodedTestKitPropertyArgument>()
        var authoritativeCapCount = 0
        var index = 0
        while (index < arguments.size) {
            val argument = arguments[index]
            when {
                argument == "--max-workers" || argument == "-max-workers" ->
                    throw IllegalArgumentException(
                        "Runner argument conflicts with harness worker policy: $argument",
                    )
                argument.startsWith("--max-workers=") -> {
                    if (allowAuthoritativeCap && argument == MAX_WORKERS_ARGUMENT) {
                        authoritativeCapCount += 1
                    } else {
                        throw IllegalArgumentException(
                            "Runner argument conflicts with harness worker policy: $argument",
                        )
                    }
                    index += 1
                    continue
                }
                argument == "--" ->
                    throw IllegalArgumentException(
                        "Runner argument uses the fixture-prohibited Gradle option terminator: --",
                    )
            }

            val property = decodePropertyArgument(arguments, index)
            if (property != null) {
                require(property.key != WORKER_PROPERTY) {
                    "Runner property argument conflicts with harness worker policy: ${property.option} $WORKER_PROPERTY"
                }
                decoded += property
                index += property.consumedArgumentCount
            } else {
                index += 1
            }
        }
        if (allowAuthoritativeCap) {
            require(authoritativeCapCount == 1) {
                "Final runner arguments must contain exactly one fixture-owned $MAX_WORKERS_ARGUMENT"
            }
        } else {
            require(authoritativeCapCount == 0) {
                "Caller arguments must not contain the fixture-owned worker cap"
            }
        }
        return decoded
    }

    private fun decodePropertyArgument(
        arguments: List<String>,
        index: Int,
    ): DecodedTestKitPropertyArgument? {
        val argument = arguments[index]
        val equalsPrefix =
            PROPERTY_EQUALS_PREFIXES.firstOrNull(argument::startsWith)
        if (equalsPrefix != null) {
            return decodePropertyPayload(
                option = equalsPrefix.dropLast(1),
                payload = argument.substring(equalsPrefix.length),
                sourceArgumentIndex = index,
                consumedArgumentCount = 1,
            )
        }

        val separatedOption = PROPERTY_SEPARATED_OPTIONS.firstOrNull(argument::equals)
        if (separatedOption != null) {
            val next = arguments.getOrNull(index + 1)
            val state = classifySeparatedPropertyToken(next)
            require(state !in FIXTURE_REJECTED_SEPARATED_STATES) {
                when (state) {
                    SeparatedPropertyTokenState.MISSING_TOKEN ->
                        "Runner property argument is missing its required value: $separatedOption"
                    SeparatedPropertyTokenState.EMPTY_TOKEN ->
                        "Runner property argument has an empty required value: $separatedOption"
                    SeparatedPropertyTokenState.TERMINATOR_TOKEN ->
                        "Runner property argument is followed by the fixture-prohibited Gradle option terminator: $separatedOption --"
                    SeparatedPropertyTokenState.OPTION_TOKEN ->
                        "Runner property argument is missing its value before option token: $separatedOption ${next.orEmpty()}"
                    else -> error("unexpected preserved separated property state")
                }
            }
            check(next != null)
            return decodePropertyPayload(
                option = separatedOption,
                payload = next,
                sourceArgumentIndex = index,
                consumedArgumentCount = 2,
            )
        }

        val joinedPrefix = PROPERTY_JOINED_PREFIXES.firstOrNull { prefix ->
            argument.startsWith(prefix) && argument.length > prefix.length
        }
        return joinedPrefix?.let { prefix ->
            decodePropertyPayload(
                option = prefix,
                payload = argument.substring(prefix.length),
                sourceArgumentIndex = index,
                consumedArgumentCount = 1,
            )
        }
    }

    private fun classifySeparatedPropertyToken(token: String?): SeparatedPropertyTokenState =
        when {
            token == null -> SeparatedPropertyTokenState.MISSING_TOKEN
            token.isEmpty() -> SeparatedPropertyTokenState.EMPTY_TOKEN
            token == "-" -> SeparatedPropertyTokenState.LONE_DASH_PAYLOAD
            token == "--" -> SeparatedPropertyTokenState.TERMINATOR_TOKEN
            OPTION_TOKEN_PATTERN.matches(token) -> SeparatedPropertyTokenState.OPTION_TOKEN
            else -> SeparatedPropertyTokenState.PAYLOAD
        }

    private fun decodePropertyPayload(
        option: String,
        payload: String,
        sourceArgumentIndex: Int,
        consumedArgumentCount: Int,
    ): DecodedTestKitPropertyArgument {
        require(payload.isNotEmpty()) {
            "Runner property argument has an empty payload: $option"
        }
        val separator = payload.indexOf('=')
        val key = if (separator < 0) payload else payload.substring(0, separator)
        val value = if (separator < 0) "" else payload.substring(separator + 1)
        require(key.isNotEmpty()) {
            "Runner property argument has an empty key: $option"
        }
        return DecodedTestKitPropertyArgument(
            option = option,
            key = key,
            value = value,
            sourceArgumentIndex = sourceArgumentIndex,
            consumedArgumentCount = consumedArgumentCount,
        )
    }

    private fun validateGradleProperties(path: File, owner: String) {
        val candidate = path.toPath()
        if (!Files.exists(candidate, NOFOLLOW_LINKS)) return
        require(!Files.isSymbolicLink(candidate) && Files.isRegularFile(candidate, NOFOLLOW_LINKS)) {
            "$owner gradle.properties is not a regular nonsymlink file: $path"
        }
        val hasReadPermission =
            try {
                Files.getPosixFilePermissions(candidate, NOFOLLOW_LINKS).any(READ_PERMISSIONS::contains)
            } catch (error: Exception) {
                throw IllegalArgumentException("$owner gradle.properties permissions are unreadable: $path", error)
            }
        require(hasReadPermission && Files.isReadable(candidate)) {
            "$owner gradle.properties is unreadable: $path"
        }
        val properties = Properties()
        try {
            Files.newInputStream(candidate).use(properties::load)
        } catch (error: Exception) {
            throw IllegalArgumentException("$owner gradle.properties is malformed: $path", error)
        }
        listOf(WORKER_PROPERTY, "systemProp.$WORKER_PROPERTY").forEach { key ->
            require(properties.getProperty(key) == null) {
                "$owner gradle.properties contains a worker override: $key"
            }
        }
        properties.getProperty("org.gradle.jvmargs")?.let { value ->
            require(!value.contains(WORKER_PROPERTY)) {
                "$owner gradle.properties org.gradle.jvmargs contains a worker override"
            }
        }
    }

    private fun requireNonConflictingArgument(argument: String) {
        val conflicts =
            argument == "-g" ||
                argument.startsWith("-g") ||
                argument == "--gradle-user-home" ||
                argument.startsWith("--gradle-user-home=") ||
                argument == "--configuration-cache" ||
                argument.startsWith("--configuration-cache=") ||
                argument == "--no-configuration-cache" ||
                argument.startsWith("--no-configuration-cache=") ||
                argument == "--configuration-cache-problems" ||
                argument.startsWith("--configuration-cache-problems=") ||
                argument == "--build-cache" ||
                argument.startsWith("--build-cache=") ||
                argument == "--no-build-cache" ||
                argument.startsWith("--no-build-cache=") ||
                argument == "--warning-mode" ||
                argument.startsWith("--warning-mode=") ||
                argument == "-I" ||
                argument.startsWith("-I") ||
                argument == "--init-script" ||
                argument.startsWith("--init-script=") ||
                argument.startsWith("-Dorg.gradle.java.installations.")
        require(!conflicts) { "Runner argument conflicts with harness policy: $argument" }
    }

    companion object {
        fun create(
            root: File,
            sharedGradleUserHomeDir: File? = null,
        ): GradlePluginTestProject {
            val canonicalRoot = root.canonicalFile
            require(canonicalRoot.isDirectory) { "Fixture root must be an existing directory: $root" }

            val projectDir = canonicalRoot.resolve(PROJECT_DIRECTORY)
            val testKitDir = canonicalRoot.resolve(TEST_KIT_DIRECTORY)
            listOf(projectDir, testKitDir).forEach { directory ->
                require(directory.mkdir()) {
                    "Fixture directory must be newly created and empty: $directory"
                }
            }
            val gradleUserHomeDir =
                sharedGradleUserHomeDir?.canonicalFile
                    ?: canonicalRoot.resolve(GRADLE_USER_HOME_DIRECTORY).also { directory ->
                        require(directory.mkdir()) {
                            "Fixture directory must be newly created and empty: $directory"
                        }
                    }
            require(gradleUserHomeDir.isDirectory) {
                "Shared Gradle user home must be an existing directory: $gradleUserHomeDir"
            }
            return GradlePluginTestProject(projectDir, testKitDir, gradleUserHomeDir)
        }

        private const val PROJECT_DIRECTORY = "project"
        private const val TEST_KIT_DIRECTORY = "test-kit"
        private const val GRADLE_USER_HOME_DIRECTORY = "gradle-user-home"
        private const val EXACT_GRADLE_VERSION = "9.6.1"
        private const val MAX_WORKERS_ARGUMENT = "--max-workers=2"
        private const val WORKER_PROPERTY = "org.gradle.workers.max"
        private val OPTION_TOKEN_PATTERN = Regex("(?s)-.+")
        private val PROPERTY_JOINED_PREFIXES = listOf("-D", "-P")
        private val PROPERTY_EQUALS_PREFIXES =
            listOf("-D=", "-P=", "--D=", "--P=", "--system-prop=", "--project-prop=")
        private val PROPERTY_SEPARATED_OPTIONS =
            listOf(
                "-D",
                "-P",
                "--D",
                "--P",
                "--system-prop",
                "--project-prop",
                "-system-prop",
                "-project-prop",
            )
        private val FIXTURE_REJECTED_SEPARATED_STATES =
            setOf(
                SeparatedPropertyTokenState.MISSING_TOKEN,
                SeparatedPropertyTokenState.EMPTY_TOKEN,
                SeparatedPropertyTokenState.TERMINATOR_TOKEN,
                SeparatedPropertyTokenState.OPTION_TOKEN,
            )
        private val FORBIDDEN_WORKER_ENVIRONMENT_KEYS =
            setOf(
                "GRADLE_OPTS",
                "JAVA_OPTS",
                "JAVA_TOOL_OPTIONS",
                "JDK_JAVA_OPTIONS",
                "_JAVA_OPTIONS",
                "ORG_GRADLE_PROJECT_org.gradle.workers.max",
                "GRADLE_USER_HOME",
            )
        private val READ_PERMISSIONS =
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.OTHERS_READ,
            )

        private val DEFAULT_SETTINGS =
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            dependencyResolutionManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            rootProject.name = "gasstation-convention-plugin-test-fixture"
            """.trimIndent()
    }
}

private enum class SeparatedPropertyTokenState {
    MISSING_TOKEN,
    EMPTY_TOKEN,
    LONE_DASH_PAYLOAD,
    TERMINATOR_TOKEN,
    OPTION_TOKEN,
    PAYLOAD,
}
