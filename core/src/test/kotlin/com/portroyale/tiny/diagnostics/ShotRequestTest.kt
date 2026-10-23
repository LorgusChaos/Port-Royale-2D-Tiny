package com.portroyale.tiny.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The shot mode's whole value is that a given command line gives a given picture. These pin the
 * parsing down, and above all that a mistyped argument stops the launch rather than quietly
 * producing a screenshot of the wrong thing - the failure that would cost the most to notice.
 */
class ShotRequestTest {

    @Test
    fun `an ordinary launch is not a shot`() {
        assertNull(ShotRequest.parse(arrayOf()))
        assertNull(ShotRequest.parse(arrayOf("--something-else")))
    }

    @Test
    fun `a bare screen gets every default`() {
        val shot = ShotRequest.parse(arrayOf("--shot=Market"))!!
        assertEquals(ShotScreen.MARKET, shot.screen)
        assertEquals("build/shots/market.png", shot.outPath)
        assertEquals(30, shot.frames)
        assertNull(shot.seed)
        assertEquals("Port Royal", shot.port)
        assertEquals(1280 to 720, shot.width to shot.height)
    }

    @Test
    fun `every option is read`() {
        val shot = ShotRequest.parse(
            arrayOf("--shot=sea", "--shot-out=C:/tmp/a.png", "--shot-frames=5", "--shot-seed=42", "--shot-port=Havana", "--shot-size=1920x1080")
        )!!
        assertEquals(ShotRequest(ShotScreen.SEA, "C:/tmp/a.png", 5, 42L, "Havana", 1920, 1080), shot)
    }

    @Test
    fun `mistakes stop the launch instead of shooting the wrong screen`() {
        assertFailsWith<IllegalArgumentException> { ShotRequest.parse(arrayOf("--shot=markt")) }
        assertFailsWith<IllegalArgumentException> { ShotRequest.parse(arrayOf("--shot=hub", "--shot-fames=5")) }
        assertFailsWith<IllegalArgumentException> { ShotRequest.parse(arrayOf("--shot-port=Havana")) }
        assertFailsWith<IllegalArgumentException> { ShotRequest.parse(arrayOf("--shot=hub", "--shot-frames=0")) }
        assertFailsWith<IllegalArgumentException> { ShotRequest.parse(arrayOf("--shot=hub", "--shot-size=big")) }
        assertFailsWith<IllegalArgumentException> { ShotRequest.parse(arrayOf("--shot")) }
    }
}
