package com.portroyale.tiny.save

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsTest {

    @Test
    fun `a volume that is not a number comes back as the default`() {
        val data = SettingsData().apply { masterVolume = Float.NaN }
        assertEquals(SettingsData().masterVolume, data.sanitized().masterVolume)
    }

    @Test
    fun `an infinite volume comes back within bounds`() {
        for (bad in listOf(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val volume = SettingsData().apply { masterVolume = bad }.sanitized().masterVolume
            assertTrue(volume in 0f..1f, "volume $volume out of bounds")
        }
    }

    @Test
    fun `volumes outside 0 to 1 are brought within it`() {
        assertEquals(1f, SettingsData().apply { masterVolume = 100f }.sanitized().masterVolume)
        assertEquals(0f, SettingsData().apply { masterVolume = -3f }.sanitized().masterVolume)
    }

    @Test
    fun `ordinary settings pass through unchanged`() {
        val data = SettingsData().apply {
            masterVolume = 0.4f
            muted = true
            fullscreen = true
            windowWidth = 1920
            windowHeight = 1080
        }
        val result = data.sanitized()
        assertEquals(0.4f, result.masterVolume)
        assertEquals(true, result.muted)
        assertEquals(true, result.fullscreen)
        assertEquals(1920, result.windowWidth)
        assertEquals(1080, result.windowHeight)
    }
}
