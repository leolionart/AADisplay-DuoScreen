package com.cva.duoscreen.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class VietmapSourceTest {
    @Before
    fun setUp() {
        SpeedAlertRepository.reset()
    }


    @Test
    fun testProcessParsedTextsFiltersPureSpeedReadings() {
        // Given parsed texts containing pure speed readings like "0 km/h" or "0"
        val texts = listOf("0 km/h", "0", "Pin 85%")
        VietmapSource.processParsedTexts(texts)

        val alert = SpeedAlertRepository.current
        // Alert text should NOT be "0 km/h"
        assertEquals("", alert.alertText)
        assertNull(alert.speedLimit)
    }

    @Test
    fun testProcessParsedTextsCapturesActualAlerts() {
        val texts = listOf("0 km/h", "60", "Camera phạt nguội 300m")
        VietmapSource.processParsedTexts(texts)

        val alert = SpeedAlertRepository.current
        assertEquals(60, alert.speedLimit)
        assertEquals("Camera phạt nguội 300m", alert.alertText)
    }
}
