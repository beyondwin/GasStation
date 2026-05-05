package com.gasstation.core.designsystem

import com.gasstation.core.model.Brand
import com.gasstation.core.model.BrandFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class BrandLabelsTest {
    @Test
    fun `brand and brand filter labels use one canonical rtx meaning`() {
        assertEquals("고속도로알뜰", Brand.RTX.gasStationBrandLabel())
        assertEquals("고속도로알뜰", BrandFilter.RTX.gasStationBrandFilterLabel())
        assertEquals("자가상표", Brand.ETC.gasStationBrandLabel())
        assertEquals("자가상표", BrandFilter.ETC.gasStationBrandFilterLabel())
    }

    @Test
    fun `brand filter labels mirror brand labels except all option`() {
        BrandFilter.entries
            .filterNot { it == BrandFilter.ALL }
            .forEach { filter ->
                assertEquals(filter.brand?.gasStationBrandLabel(), filter.gasStationBrandFilterLabel())
            }
    }
}
