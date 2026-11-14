package com.portroyale.tiny.ui

import kotlin.test.Test
import kotlin.test.assertEquals

private data class Mode(val w: Int, val h: Int)

class DisplayModeChoiceTest {

    private val desktop = Mode(2560, 1440)
    private val size = { m: Mode -> m.w to m.h }

    @Test
    fun `the exact size is chosen when the monitor has it`() {
        val modes = listOf(Mode(1280, 720), Mode(1920, 1080), Mode(2560, 1440))
        assertEquals(Mode(1920, 1080), chooseDisplayMode(modes, 1920, 1080, desktop, size))
    }

    @Test
    fun `without the size the desktop's own mode is kept`() {
        val modes = listOf(Mode(1280, 720), Mode(1920, 1080))
        assertEquals(desktop, chooseDisplayMode(modes, 1366, 768, desktop, size))
    }

    @Test
    fun `an empty list falls back to the desktop's mode`() {
        assertEquals(desktop, chooseDisplayMode(emptyList(), 1920, 1080, desktop, size))
    }
}
