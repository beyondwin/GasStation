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
            val includeInUnitTests = providers
                .gradleProperty("gasstation.includeRoborazziInUnitTests")
                .map(String::toBoolean)
                .orElse(false)
            val roborazziTaskRequested = gradle.startParameter.taskNames.any {
                it.contains("Roborazzi", ignoreCase = true)
            }

            dependencies {
                add("testImplementation", libs.findLibrary("roborazzi-core").get())
                add("testImplementation", libs.findLibrary("roborazzi-compose").get())
                add("testImplementation", libs.findLibrary("roborazzi-junit-rule").get())
                add("testImplementation", libs.findLibrary("robolectric").get())
            }

            tasks.withType<Test>().configureEach {
                if (!roborazziTaskRequested && !includeInUnitTests.get()) {
                    exclude("**/Roborazzi*Test.class")
                }
            }
        }
    }
}
