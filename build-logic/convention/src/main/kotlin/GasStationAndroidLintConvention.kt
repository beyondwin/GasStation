import com.android.build.api.dsl.Lint
import org.gradle.api.GradleException
import org.gradle.api.Project

internal fun Project.configureGasStationAndroidLint(
    lint: Lint,
    checkDependencies: Boolean,
) {
    val lintTestSourcesEnabled =
        providers.gradleProperty("gasstation.lintTestSources")
            .map(::parseLintTestSources)
            .orElse(false)

    lint.apply {
        warningsAsErrors = false
        abortOnError = true
        this.checkDependencies = checkDependencies
        checkTestSources = lintTestSourcesEnabled.get()
        ignoreTestSources = !lintTestSourcesEnabled.get()
        xmlReport = true
        textReport = true
        htmlReport = true
        sarifReport = true
    }
}

private fun parseLintTestSources(value: String): Boolean =
    when (value) {
        "true" -> true
        "false" -> false
        else -> throw GradleException("gasstation.lintTestSources must be exactly true or false")
    }
