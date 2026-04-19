package com.gasstation.core.location

import android.location.Address
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AddressLabelFormatterTest {
    @Test
    fun `formatter prefers complete address line`() {
        val address = Address(Locale.KOREA).apply {
            setAddressLine(0, "서울 영등포구 당산동 194-32")
            adminArea = "서울"
            locality = "영등포구"
            thoroughfare = "당산동"
        }

        assertEquals("서울 영등포구 당산동 194-32", address.toDisplayLabel())
    }

    @Test
    fun `formatter builds label from road parts when address line is blank`() {
        val address = Address(Locale.KOREA).apply {
            adminArea = "서울"
            locality = "영등포구"
            subLocality = "당산동"
            thoroughfare = "당산로"
            subThoroughfare = "123"
        }

        assertEquals("서울 영등포구 당산동 당산로 123", address.toDisplayLabel())
    }

    @Test
    fun `formatter returns null when address has no displayable parts`() {
        assertNull(Address(Locale.KOREA).toDisplayLabel())
    }
}
