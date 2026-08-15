import java.time.Duration
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal fun Project.configureGasStationKotlinAndTestConventions() {
    val strictByModule =
        path.startsWith(":domain:") ||
            path == ":core:model" ||
            path == ":core:observability"
    val kotlinWarningsOptIn =
        providers.strictBooleanGradleProperty(
            name = "gasstation.kotlinWarningsAsErrors",
            defaultValue = false,
        )

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        compilerOptions.allWarningsAsErrors.set(
            kotlinWarningsOptIn.map { optedIn -> strictByModule || optedIn },
        )
    }
    tasks.withType<Test>().configureEach {
        timeout.set(Duration.ofMinutes(15))
    }
}
