import org.gradle.api.Plugin
import org.gradle.api.Project

class GasStationKoverConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlinx.kover")
        }
    }
}
