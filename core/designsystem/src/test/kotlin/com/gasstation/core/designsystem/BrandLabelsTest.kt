package com.gasstation.core.designsystem

import com.gasstation.core.model.Brand
import com.gasstation.core.model.BrandFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrandLabelsTest {
    @Test
    fun `brand labels keep each alteul source meaning`() {
        assertEquals("고속도로알뜰", Brand.RTX.gasStationBrandLabel())
        assertEquals("자가상표", Brand.ETC.gasStationBrandLabel())
    }

    @Test
    fun `grouped alteul filter uses one label and one representative logo`() {
        assertEquals("알뜰", BrandFilter.ALTEUL.gasStationBrandFilterLabel())
        assertEquals(Brand.RTO, BrandFilter.ALTEUL.gasStationBrandFilterIconBrand())
        assertNull(BrandFilter.ALL.gasStationBrandFilterIconBrand())
    }
}
