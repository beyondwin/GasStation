import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class GasStationRoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.github.takahirom.roborazzi")

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            dependencies {
                add("testImplementation", libs.findLibrary("roborazzi-core").get())
                add("testImplementation", libs.findLibrary("roborazzi-compose").get())
                add("testImplementation", libs.findLibrary("roborazzi-junit-rule").get())
                add("testImplementation", libs.findLibrary("robolectric").get())
            }
        }
    }
}
