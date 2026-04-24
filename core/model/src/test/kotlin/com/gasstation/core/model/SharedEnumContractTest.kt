package com.gasstation.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedEnumContractTest {

    @Test
    fun `fuel type identities stay transport free`() {
        assertEquals(
            listOf("GASOLINE", "DIESEL", "PREMIUM_GASOLINE", "KEROSENE", "LPG"),
            FuelType.entries.map { it.name },
        )
        assertTrue(FuelType::class.java.declaredFields.none { it.name.contains("code", ignoreCase = true) })
        assertFalse(FuelType::class.java.declaredMethods.any { it.name == "getDisplayName" })
        assertFalse(FuelType::class.java.declaredMethods.any { it.name == "getProductCode" })
    }

    @Test
    fun `brand identities stay display and transport free`() {
        assertEquals(
            listOf("SKE", "GSC", "HDO", "SOL", "RTO", "RTX", "NHO", "ETC", "E1G", "SKG"),
            Brand.entries.map { it.name },
        )
        assertTrue(Brand::class.java.declaredFields.none { it.name.contains("code", ignoreCase = true) })
        assertFalse(Brand::class.java.declaredMethods.any { it.name == "getCode" })
        assertFalse(Brand::class.java.declaredMethods.any { it.name == "getDisplayName" })
        assertFalse(Brand::class.java.declaredMethods.any { it.name == "getBrandCode" })
    }

    @Test
    fun `brand filters keep stable identities and match by brand`() {
        assertEquals(
            listOf("ALL", "SKE", "GSC", "HDO", "SOL", "RTO", "RTX", "NHO", "ETC", "E1G", "SKG"),
            BrandFilter.entries.map { it.name },
        )

        assertTrue(BrandFilter.ALL.matches(Brand.GSC))
        assertTrue(BrandFilter.GSC.matches(Brand.GSC))
        assertFalse(BrandFilter.GSC.matches(Brand.SKE))
    }

    @Test
    fun `preference enum identities stay stable`() {
        assertEquals(listOf("DISTANCE", "PRICE"), SortOrder.entries.map { it.name })
        assertEquals(3_000, SearchRadius.KM_3.meters)
        assertEquals(4_000, SearchRadius.KM_4.meters)
        assertEquals(5_000, SearchRadius.KM_5.meters)
        assertEquals(listOf("TMAP", "KAKAO_NAVI", "NAVER_MAP"), MapProvider.entries.map { it.name })
    }
}
