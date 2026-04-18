package com.gasstation.core.datastore

import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder
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

        assertEquals(UserPreferences.default(), decoded)
    }

    @Test
    fun `readFrom ignores unknown fields in evolvable key value format`() = runBlocking {
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
            UserPreferences.default().copy(
                searchRadius = SearchRadius.KM_5,
                sortOrder = SortOrder.PRICE,
            ),
            decoded,
        )
    }

    @Test
    fun `writeTo emits evolvable key value format`() = runBlocking {
        val output = ByteArrayOutputStream()

        UserPreferencesSerializer.writeTo(
            UserPreferences.default().copy(
                searchRadius = SearchRadius.KM_4,
                fuelType = FuelType.DIESEL,
                brandFilter = BrandFilter.GSC,
                sortOrder = SortOrder.PRICE,
                mapProvider = MapProvider.NAVER_MAP,
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
