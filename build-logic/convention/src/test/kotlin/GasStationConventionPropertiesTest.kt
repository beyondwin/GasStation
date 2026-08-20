import org.gradle.api.GradleException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GasStationConventionPropertiesTest {
    @Test
    fun exactBooleanValuesAreAccepted() {
        assertTrue(parseStrictBooleanGradlePropertyValue("fixture", "true"))
        assertEquals(false, parseStrictBooleanGradlePropertyValue("fixture", "false"))
    }

    @Test
    fun everyInvalidBooleanSpellingIsRejectedWithStableDiagnostic() {
        listOf("TRUE", "False", " true", "false ", "yes", "").forEach { invalid ->
            val error =
                assertThrows(GradleException::class.java) {
                    parseStrictBooleanGradlePropertyValue("fixture.property", invalid)
                }

            assertEquals(
                "fixture.property must be exactly true or false",
                error.message,
            )
        }
    }
}
