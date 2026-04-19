package com.gasstation.core.designsystem.component

import com.gasstation.core.designsystem.R
import com.gasstation.domain.station.model.Brand
import org.junit.Assert.assertEquals
import org.junit.Test

class BrandIconTest {
    @Test
    fun `brand icon resources match legacy gas station type mapping`() {
        assertEquals(R.drawable.ic_ske, Brand.SKE.gasStationBrandIconResource())
        assertEquals(R.drawable.ic_gsc, Brand.GSC.gasStationBrandIconResource())
        assertEquals(R.drawable.ic_hdo, Brand.HDO.gasStationBrandIconResource())
        assertEquals(R.drawable.ic_sol, Brand.SOL.gasStationBrandIconResource())
        assertEquals(R.drawable.ic_rtx, Brand.RTO.gasStationBrandIconResource())
        assertEquals(R.drawable.ic_rtx, Brand.RTX.gasStationBrandIconResource())
        assertEquals(R.drawable.ic_rtx, Brand.NHO.gasStationBrandIconResource())
        assertEquals(R.drawable.ic_etc, Brand.ETC.gasStationBrandIconResource())
        assertEquals(R.drawable.ic_e1g, Brand.E1G.gasStationBrandIconResource())
        assertEquals(R.drawable.ic_skg, Brand.SKG.gasStationBrandIconResource())
    }
}
