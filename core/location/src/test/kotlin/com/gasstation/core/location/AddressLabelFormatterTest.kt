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
    fun `formatter trims complete address line to dong level`() {
        val address = Address(Locale.KOREA).apply {
            setAddressLine(0, "서울 영등포구 당산동 194-32")
            adminArea = "서울"
            locality = "영등포구"
            thoroughfare = "당산동"
        }

        assertEquals("서울 영등포구 당산동", address.toDisplayLabel())
    }

    @Test
    fun `formatter prefers administrative dong fields over road address building dong`() {
        val address = Address(Locale.KOREA).apply {
            setAddressLine(0, "서울특별시 강남구 테헤란로 152 강남파이낸스센터 E동")
            adminArea = "서울특별시"
            locality = "강남구"
            subLocality = "역삼동"
            thoroughfare = "테헤란로"
            subThoroughfare = "152"
            featureName = "강남파이낸스센터 E동"
        }

        assertEquals("서울특별시 강남구 역삼동", address.toDisplayLabel())
    }

    @Test
    fun `formatter extracts final administrative address when address line has country and building noise`() {
        val address = Address(Locale.KOREA).apply {
            setAddressLine(0, "대한민국 서울 특별시 강남구 지하 번지 동 상가 27호 KR 서울특별시 강남구 역삼동")
        }

        assertEquals("서울특별시 강남구 역삼동", address.toDisplayLabel())
    }

    @Test
    fun `formatter builds dong level label from road parts when address line is blank`() {
        val address = Address(Locale.KOREA).apply {
            adminArea = "서울"
            locality = "영등포구"
            subLocality = "당산동"
            thoroughfare = "당산로"
            subThoroughfare = "123"
        }

        assertEquals("서울 영등포구 당산동", address.toDisplayLabel())
    }

    @Test
    fun `formatter returns null when address has no displayable parts`() {
        assertNull(Address(Locale.KOREA).toDisplayLabel())
    }
}
