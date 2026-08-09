package com.portroyale.tiny.world

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** GameClock is a process-wide singleton, so each test rewinds it first. */
class GameClockTest {

    @BeforeTest
    fun rewind() {
        GameClock.reset()
    }

    @Test
    fun `a new game starts on the first day`() {
        assertEquals(0f, GameClock.totalGameMinutes)
        assertEquals(1, GameClock.day)
        assertEquals("1 Jan 1600", GameClock.calendarLabel)
    }

    @Test
    fun `game time runs ten times faster than real time`() {
        GameClock.update(60f) // one real minute
        assertEquals(10f, GameClock.totalGameMinutes, 0.0001f)
    }

    @Test
    fun `time accumulates across frames`() {
        repeat(60) { GameClock.update(1f) }
        assertEquals(10f, GameClock.totalGameMinutes, 0.001f)
    }

    @Test
    fun `a real-world span converts to game minutes and back again`() {
        val gameMinutes = GameClock.realSecondsToGameMinutes(144f)
        assertEquals(144f / 60f, GameClock.gameMinutesToRealMinutes(gameMinutes), 0.0001f)
    }

    @Test
    fun `the day rolls over exactly on the day boundary`() {
        GameClock.restore(24 * 60f - 0.1f)
        assertEquals(1, GameClock.day)
        GameClock.restore(24 * 60f)
        assertEquals(2, GameClock.day)
    }

    @Test
    fun `the calendar rolls into the next month after thirty days`() {
        GameClock.restore(29 * 24 * 60f)
        assertEquals("30 Jan 1600", GameClock.calendarLabel)
        GameClock.restore(30 * 24 * 60f)
        assertEquals("1 Feb 1600", GameClock.calendarLabel)
    }

    @Test
    fun `the calendar rolls into the next year after twelve months`() {
        GameClock.restore(359 * 24 * 60f)
        assertEquals("30 Dec 1600", GameClock.calendarLabel)
        GameClock.restore(360 * 24 * 60f)
        assertEquals("1 Jan 1601", GameClock.calendarLabel)
    }

    @Test
    fun `the calendar never runs off the end of the month name table`() {
        // A month index escaping 0..11 would crash the HUD, so walk a few simulated years.
        for (day in 0 until 365 * 3) {
            GameClock.restore(day * 24 * 60f)
            assertTrue(GameClock.calendarLabel.isNotBlank())
        }
    }

    @Test
    fun `a snapshot survives a round trip through restore`() {
        GameClock.update(123.5f)
        val saved = GameClock.snapshot()
        val label = GameClock.calendarLabel

        GameClock.reset()
        assertEquals(0f, GameClock.snapshot())

        GameClock.restore(saved)
        assertEquals(saved, GameClock.snapshot())
        assertEquals(label, GameClock.calendarLabel)
    }

    @Test
    fun `resetting rewinds a long-running game back to day one`() {
        GameClock.restore(500 * 24 * 60f)
        GameClock.reset()
        assertEquals(1, GameClock.day)
    }

    @Test
    fun `a loan term measured in game days converts to a sane real-world wait`() {
        // Five in-game days should not mean five real-world days of waiting.
        val fiveGameDays = 5 * 24 * 60f
        assertEquals(5 * 24 * 6f, GameClock.gameMinutesToRealMinutes(fiveGameDays), 0.001f)
    }
}

class GameSpeedTest {

    @BeforeTest
    fun rewind() {
        GameSpeed.reset()
    }

    @Test
    fun `the world starts running at normal speed`() {
        assertEquals(0, GameSpeed.stepIndex)
        assertEquals(1f, GameSpeed.multiplier)
    }

    @Test
    fun `cycling walks the full ladder and wraps back to normal`() {
        val seen = mutableListOf(GameSpeed.multiplier)
        repeat(4) {
            GameSpeed.cycle()
            seen.add(GameSpeed.multiplier)
        }
        assertEquals(listOf(1f, 2f, 5f, 10f, 20f), seen)

        GameSpeed.cycle()
        assertEquals(1f, GameSpeed.multiplier, "the ladder wraps around")
    }

    @Test
    fun `the multiplier never stalls the world or runs backwards`() {
        repeat(20) {
            assertTrue(GameSpeed.multiplier >= 1f)
            GameSpeed.cycle()
        }
    }

    @Test
    fun `resetting drops back to normal speed from anywhere on the ladder`() {
        repeat(3) { GameSpeed.cycle() }
        assertEquals(10f, GameSpeed.multiplier)
        GameSpeed.reset()
        assertEquals(1f, GameSpeed.multiplier)
    }
}
