import org.gradle.api.Plugin
import org.gradle.api.Project

class GasStationAndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("gasstation.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    }
}
