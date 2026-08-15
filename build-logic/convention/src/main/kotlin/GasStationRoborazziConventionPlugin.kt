import java.io.File
import javax.imageio.ImageIO
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

class GasStationRoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.github.takahirom.roborazzi")

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            val includeInUnitTests =
                providers.strictBooleanGradleProperty(
                    name = "gasstation.includeRoborazziInUnitTests",
                    defaultValue = false,
                )
            val roborazziTaskRequested = providers.provider { isRoborazziLifecycleTaskRequested() }

            dependencies {
                add("testImplementation", libs.findLibrary("roborazzi-core").get())
                add("testImplementation", libs.findLibrary("roborazzi-compose").get())
                add("testImplementation", libs.findLibrary("roborazzi-junit-rule").get())
                add("testImplementation", libs.findLibrary("robolectric").get())
            }

            tasks.withType<Test>().configureEach {
                if (!roborazziTaskRequested.get() && !includeInUnitTests.get()) {
                    exclude("**/Roborazzi*Test.class")
                }
            }

            val snapshotsDirectory = projectDir.resolve("src/test/snapshots")
            tasks.matching {
                isRoborazziLifecycleTaskName(it.name)
            }.configureEach {
                doLast {
                    verifyNoRoborazziStagingFrames(snapshotsDirectory)
                }
            }
        }
    }

    private fun Project.isRoborazziLifecycleTaskRequested(): Boolean {
        val registeredTasks = tasks.names
        return gradle.startParameter.taskNames.any { requestedTask ->
            val leafTask = requestedTask.substringAfterLast(':')
            if (
                !isRoborazziLifecycleTaskName(leafTask) ||
                leafTask !in registeredTasks
            ) {
                return@any false
            }
            when {
                ':' !in requestedTask -> true
                requestedTask.startsWith(":") ->
                    requestedTask.removeSuffix(":$leafTask").ifEmpty { ":" } == path
                else -> false
            }
        }
    }
}

private fun isRoborazziLifecycleTaskName(taskName: String): Boolean =
    ROBORAZZI_LIFECYCLE_TASK_PREFIXES.any { prefix ->
        taskName == prefix ||
            taskName.removePrefix(prefix).let { suffix ->
                taskName.startsWith(prefix) && suffix.firstOrNull()?.isUpperCase() == true
            }
    }

internal fun verifyNoRoborazziStagingFrames(snapshotsDirectory: File) {
    val pngFiles = snapshotsDirectory.listFiles { file ->
        file.isFile && file.extension.equals("png", ignoreCase = true)
    }.orEmpty().sortedBy(File::getName)
    var pollutedPixelCount = 0
    var firstPollutedPixel: String? = null

    pngFiles.forEach { snapshot ->
        val image = ImageIO.read(snapshot)
            ?: throw GradleException("Unable to decode Roborazzi snapshot: ${snapshot.path}")
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) == ROBORAZZI_STAGING_FRAME_ARGB) {
                    pollutedPixelCount += 1
                    if (firstPollutedPixel == null) {
                        firstPollutedPixel = "${snapshot.path}:$x,$y"
                    }
                }
            }
        }
    }

    if (pollutedPixelCount > 0) {
        throw GradleException(
            "Roborazzi snapshots contain $pollutedPixelCount exact staging magenta pixel(s); " +
                "first found at $firstPollutedPixel",
        )
    }
}

private const val ROBORAZZI_STAGING_FRAME_ARGB = -65281
private val ROBORAZZI_LIFECYCLE_TASK_PREFIXES =
    listOf(
        "recordRoborazzi",
        "verifyRoborazzi",
        "compareRoborazzi",
        "verifyAndRecordRoborazzi",
    )
