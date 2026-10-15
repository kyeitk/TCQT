package com.owo233.tcqt.features.menu.settingmecards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NativeBalanceTextTest {
    @Test fun `joins the native split fraction instead of truncating to zero`() {
        assertEquals("0.81", NativeBalanceText.parse(listOf("0", ".81", "余额")))
        assertEquals("1234.56", NativeBalanceText.parse(listOf("1,234", ".56", "账户余额")))
    }

    @Test fun `accepts real zero and a complete currency display`() {
        assertEquals("0.00", NativeBalanceText.parse(listOf("余额", "0.00")))
        assertEquals("1234.56", NativeBalanceText.parse(listOf("余额", "￥ 1,234.56")))
    }

    @Test fun `rejects other assets masked data and ambiguous displays`() {
        assertNull(NativeBalanceText.parse(listOf("Q币", "10.00")))
        assertNull(NativeBalanceText.parse(listOf("余额", "****")))
        assertNull(NativeBalanceText.parse(listOf("余额", "0.00", ".81")))
        assertNull(NativeBalanceText.parse(listOf("余额", "0", "12")))
    }
}
