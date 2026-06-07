package com.gasstation.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MoneyWonTest {

    @Test
    fun `ofOrNull returns instance for positive value`() {
        assertEquals(MoneyWon(1_680), MoneyWon.ofOrNull(1_680))
    }

    @Test
    fun `ofOrNull returns instance for zero`() {
        assertEquals(MoneyWon(0), MoneyWon.ofOrNull(0))
    }

    @Test
    fun `ofOrNull returns null for negative value`() {
        assertNull(MoneyWon.ofOrNull(-1))
    }

    @Test
    fun `constructor still fails fast for negative value`() {
        assertFailsWith<IllegalArgumentException> {
            MoneyWon(-1)
        }
    }
}
