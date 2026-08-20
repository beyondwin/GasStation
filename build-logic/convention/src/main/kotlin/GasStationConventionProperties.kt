import org.gradle.api.GradleException
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

internal fun ProviderFactory.strictBooleanGradleProperty(
    name: String,
    defaultValue: Boolean,
): Provider<Boolean> =
    gradleProperty(name)
        .map { value -> parseStrictBooleanGradlePropertyValue(name, value) }
        .orElse(defaultValue)

internal fun parseStrictBooleanGradlePropertyValue(
    name: String,
    value: String,
): Boolean =
    when (value) {
        "true" -> true
        "false" -> false
        else -> throw GradleException("$name must be exactly true or false")
    }
