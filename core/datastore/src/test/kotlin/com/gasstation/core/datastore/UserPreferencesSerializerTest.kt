package com.gasstation.core.datastore

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class UserPreferencesSerializerTest {

    @Test
    fun `readFrom falls back to default when stored data is not key value format`() = runBlocking {
        val decoded = UserPreferencesSerializer.readFrom(
            ByteArrayInputStream("KM_4|DIESEL|GSC".encodeToByteArray()),
        )

        assertEquals(StoredUserPreferences.Default, decoded)
    }

    @Test
    fun `readFrom returns stored enum names from evolvable key value format`() = runBlocking {
        val decoded = UserPreferencesSerializer.readFrom(
            ByteArrayInputStream(
                """
                version=2
                searchRadius=KM_5
                futureField=ignored
                sortOrder=PRICE
                """.trimIndent().encodeToByteArray(),
            ),
        )

        assertEquals(
            StoredUserPreferences.Default.copy(
                searchRadiusName = "KM_5",
                sortOrderName = "PRICE",
            ),
            decoded,
        )
    }

    @Test
    fun `writeTo emits evolvable key value format`() = runBlocking {
        val output = ByteArrayOutputStream()

        UserPreferencesSerializer.writeTo(
            StoredUserPreferences.Default.copy(
                searchRadiusName = "KM_4",
                fuelTypeName = "DIESEL",
                brandFilterName = "GSC",
                sortOrderName = "PRICE",
                mapProviderName = "NAVER_MAP",
            ),
            output,
        )

        val encoded = output.toString(Charsets.UTF_8.name())

        assertTrue(encoded.contains("version="))
        assertTrue(encoded.contains("searchRadius=KM_4"))
        assertTrue(encoded.contains("fuelType=DIESEL"))
        assertTrue(encoded.contains("brandFilter=GSC"))
        assertTrue(encoded.contains("sortOrder=PRICE"))
        assertTrue(encoded.contains("mapProvider=NAVER_MAP"))
    }
}
