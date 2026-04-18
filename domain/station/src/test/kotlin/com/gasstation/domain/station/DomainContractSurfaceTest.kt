package com.gasstation.domain.station

import com.gasstation.core.common.dispatchers.DispatcherProvider
import com.gasstation.domain.station.model.Brand
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DomainContractSurfaceTest {

    @Test
    fun `dispatcher provider exposes coroutine dispatcher contracts`() {
        val returnTypes = DispatcherProvider::class.java.declaredMethods.associate { method ->
            method.name to method.returnType
        }

        assertEquals(CoroutineDispatcher::class.java, returnTypes.getValue("getDefault"))
        assertEquals(CoroutineDispatcher::class.java, returnTypes.getValue("getIo"))
        assertEquals(CoroutineDispatcher::class.java, returnTypes.getValue("getMain"))
    }

    @Test
    fun `station domain enums keep stable identities without ui or transport fields`() {
        assertEquals(
            listOf("GASOLINE", "DIESEL", "PREMIUM_GASOLINE", "KEROSENE", "LPG"),
            FuelType.entries.map { it.name },
        )
        assertTrue(FuelType::class.java.declaredFields.none { it.name.contains("code", ignoreCase = true) })
        assertFalse(FuelType::class.java.declaredMethods.any { it.name == "getDisplayName" })
        assertFalse(FuelType::class.java.declaredMethods.any { it.name == "getProductCode" })

        assertEquals(
            listOf("SKE", "GSC", "HDO", "SOL", "RTO", "RTX", "NHO", "ETC", "E1G", "SKG"),
            Brand.entries.map { it.name },
        )
        assertTrue(Brand::class.java.declaredFields.none { it.name.contains("code", ignoreCase = true) })
        assertFalse(Brand::class.java.declaredMethods.any { it.name == "getDisplayName" })
        assertFalse(Brand::class.java.declaredMethods.any { it.name == "getBrandCode" })
        assertEquals(
            listOf("ALL", "SKE", "GSC", "HDO", "SOL", "RTO", "RTX", "NHO", "ETC", "E1G", "SKG"),
            BrandFilter.entries.map { it.name },
        )
        assertTrue(BrandFilter::class.java.declaredFields.none { it.name.contains("code", ignoreCase = true) })
        assertFalse(BrandFilter::class.java.declaredMethods.any { it.name == "getDisplayName" })
        assertFalse(BrandFilter::class.java.declaredMethods.any { it.name == "getBrandCode" })

        assertEquals(listOf("DISTANCE", "PRICE"), SortOrder.entries.map { it.name })
        assertTrue(SortOrder::class.java.declaredFields.none { it.name.contains("code", ignoreCase = true) })
        assertFalse(SortOrder::class.java.declaredMethods.any { it.name == "getDisplayName" })
        assertFalse(SortOrder::class.java.declaredMethods.any { it.name == "getApiCode" })

        assertEquals(3_000, SearchRadius.KM_3.meters)
        assertEquals(4_000, SearchRadius.KM_4.meters)
        assertTrue(SearchRadius::class.java.declaredFields.none { it.name.contains("code", ignoreCase = true) })
        assertFalse(SearchRadius::class.java.declaredMethods.any { it.name == "getDisplayName" })

        assertEquals(listOf("TMAP", "KAKAO_NAVI", "NAVER_MAP"), MapProvider.entries.map { it.name })
        assertTrue(MapProvider::class.java.declaredFields.none { it.name.contains("code", ignoreCase = true) })
        assertFalse(MapProvider::class.java.declaredMethods.any { it.name == "getDisplayName" })
    }
}
