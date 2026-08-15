import com.android.build.api.dsl.Lint
import org.gradle.api.Project

internal fun Project.configureGasStationAndroidLint(
    lint: Lint,
    checkDependencies: Boolean,
) {
    val lintTestSourcesEnabled =
        providers.strictBooleanGradleProperty(
            name = "gasstation.lintTestSources",
            defaultValue = false,
        )

    lint.apply {
        warningsAsErrors = true
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
