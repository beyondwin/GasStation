import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class GasStationSpotlessConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.diffplug.spotless")

        extensions.configure<SpotlessExtension> {
            val ktlintVersion = "1.5.0"

            kotlin {
                target("src/**/*.kt")
                targetExclude("**/build/**", "**/generated/**")
                ktlint(ktlintVersion)
                    .editorConfigOverride(
                        mapOf(
                            "android" to "true",
                            "ktlint_standard_filename" to "disabled",
                        ),
                    )
                trimTrailingWhitespace()
                endWithNewline()
            }

            kotlinGradle {
                target("*.gradle.kts", "src/**/*.gradle.kts")
                ktlint(ktlintVersion)
            }

            format("misc") {
                target("*.md", ".gitignore")
                trimTrailingWhitespace()
                endWithNewline()
            }
        }
    }
}
