package com.gasstation.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrandFilterTest {

    @Test
    fun `all filter matches every station brand`() {
        assertTrue(BrandFilter.ALL.matches(Brand.GSC))
    }

    @Test
    fun `specific filter matches only the same station brand`() {
        assertTrue(BrandFilter.GSC.matches(Brand.GSC))
        assertFalse(BrandFilter.SKE.matches(Brand.GSC))
    }

    @Test
    fun `brand filter entries expose grouped alteul and private label last`() {
        assertEquals(
            listOf(
                BrandFilter.ALL,
                BrandFilter.SKE,
                BrandFilter.GSC,
                BrandFilter.HDO,
                BrandFilter.SOL,
                BrandFilter.ALTEUL,
                BrandFilter.E1G,
                BrandFilter.SKG,
                BrandFilter.ETC,
            ),
            BrandFilter.entries,
        )
    }

    @Test
    fun `alteul matches every alteul source brand only`() {
        assertTrue(BrandFilter.ALTEUL.matches(Brand.RTO))
        assertTrue(BrandFilter.ALTEUL.matches(Brand.RTX))
        assertTrue(BrandFilter.ALTEUL.matches(Brand.NHO))
        assertFalse(BrandFilter.ALTEUL.matches(Brand.SKE))
        assertFalse(BrandFilter.ALTEUL.matches(Brand.ETC))
    }
}
