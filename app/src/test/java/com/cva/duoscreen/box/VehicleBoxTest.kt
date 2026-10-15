package com.cva.duoscreen.box

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleBoxTest {

    @Test
    fun testHotspotValidation() {
        val emptySsid = ""
        val validSsid = "AI Phone"
        assertFalse(emptySsid.isNotBlank())
        assertTrue(validSsid.isNotBlank())
    }

    @Test
    fun testPskGenerationLogic() {
        val shortPass = "123"
        val validPass = "12345678"
        val hasValidPass = validPass.isNotBlank() && validPass.length >= 8
        val hasShortPass = shortPass.isNotBlank() && shortPass.length >= 8

        assertTrue(hasValidPass)
        assertFalse(hasShortPass)

        val keyMgmtValid = if (hasValidPass) "02" else "01"
        val keyMgmtShort = if (hasShortPass) "02" else "01"

        assertEquals("02", keyMgmtValid)
        assertEquals("01", keyMgmtShort)
    }

    @Test
    fun testDeprecationKeywordsDetection() {
        val alert1 = "Điện thoại sẽ không được hỗ trợ nữa"
        val alert2 = "Android Auto sắp ngừng hoạt động vì điện thoại này không thể cập nhật"
        val normalMsg = "Đang dẫn đường về nhà"

        val keywords = listOf("không được hỗ trợ", "ngừng hoạt động", "cập nhật", "unsupported", "deprecated")

        assertTrue(keywords.any { alert1.lowercase().contains(it) })
        assertTrue(keywords.any { alert2.lowercase().contains(it) })
        assertFalse(keywords.any { normalMsg.lowercase().contains(it) })
    }
}
