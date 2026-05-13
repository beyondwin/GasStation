package com.gasstation.domain.location

import kotlin.test.Test
import kotlin.test.assertEquals

class AddressLabelNormalizerTest {
    @Test
    fun `normalizes road address through administrative dong`() {
        assertEquals(
            "서울 영등포구 당산동",
            normalizeCurrentAddressLabel("서울 영등포구 당산동 194-32"),
        )
    }

    @Test
    fun `ignores country code and building dong before administrative dong`() {
        assertEquals(
            "서울특별시 강남구 역삼동",
            normalizeCurrentAddressLabel("대한민국 서울 특별시 강남구 지하 번지 동 상가 27호 KR 서울특별시 강남구 역삼동"),
        )
    }

    @Test
    fun `joins split administrative region tokens`() {
        assertEquals(
            "서울특별시 강남구 역삼동",
            normalizeCurrentAddressLabel("서울 특별시 강남구 역삼동"),
        )
    }

    @Test
    fun `returns original label when administrative dong is unavailable`() {
        assertEquals(
            "서울특별시 강남구 테헤란로 152",
            normalizeCurrentAddressLabel("서울특별시 강남구 테헤란로 152"),
        )
    }
}
