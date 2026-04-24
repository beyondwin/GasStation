package com.gasstation.core.model

import kotlin.test.Test
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
}
