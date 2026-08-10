package com.portroyale.tiny.diagnostics

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The log has one job that matters: when something goes wrong, produce a report a person can act
 * on without having to reproduce the problem first. These tests hold it to that - and to the
 * rule that a diagnostic must never become the failure it was meant to explain.
 */
class GameLogTest {

    private val captured = mutableListOf<String>()

    @BeforeTest
    fun startSession() {
        captured.clear()
        GameLog.start(
            environment = listOf("game version: test", "os: TestOS"),
            sink = { captured.add(it) },
            contextProvider = { listOf("game mode: SAILING", "player gold: 1234") }
        )
    }

    @AfterTest
    fun stopSession() {
        GameLog.reset()
    }

    @Test
    fun `a logged event reaches both the file sink and the in-memory ring`() {
        GameLog.log(GameLog.Category.AI, "pirate gave up a chase")

        assertTrue(captured.any { it.contains("pirate gave up a chase") })
        assertTrue(GameLog.recent().any { it.contains("pirate gave up a chase") })
    }

    @Test
    fun `every line is timestamped and says which system it came from`() {
        GameLog.log(GameLog.Category.FLEET, "captain lost")
        val line = GameLog.recent().last()
        assertContains(line, "[FLEET]")
        assertTrue(line.matches(Regex("""^\d{2}:\d{2}:\d{2}\.\d{3} \[FLEET] captain lost$""")), line)
    }

    @Test
    fun `the ring keeps the most recent events and drops the oldest`() {
        // What matters before a crash is what happened just before it, not what happened first.
        repeat(500) { GameLog.log(GameLog.Category.SYSTEM, "event $it") }
        val recent = GameLog.recent()

        assertTrue(recent.size <= 200, "the ring grew to ${recent.size}")
        assertTrue(recent.last().contains("event 499"))
        assertFalse(recent.any { it.contains("event 0 ") }, "the oldest events should have been dropped")
    }

    @Test
    fun `a crash report carries the machine, the world and the events leading up to it`() {
        GameLog.log(GameLog.Category.COMBAT, "boarding action began")
        val report = GameLog.buildCrashReport(IllegalStateException("hull below zero"))

        assertContains(report, "os: TestOS")
        assertContains(report, "player gold: 1234")
        assertContains(report, "IllegalStateException")
        assertContains(report, "hull below zero")
        assertContains(report, "boarding action began")
    }

    @Test
    fun `a crash report keeps the cause chain, not just the outermost failure`() {
        val root = ArithmeticException("divide by zero")
        val wrapper = RuntimeException("could not update the fleet", root)

        val report = GameLog.buildCrashReport(wrapper)

        assertContains(report, "could not update the fleet")
        assertContains(report, "divide by zero")
        assertContains(report, "Caused by")
    }

    @Test
    fun `a broken context snapshot cannot replace the crash it was meant to explain`() {
        // The snapshot runs while things are already going wrong - half-initialised state is
        // exactly when it gets called. It must degrade, not throw.
        GameLog.start(
            environment = listOf("os: TestOS"),
            contextProvider = { throw UninitializedPropertyAccessException("worldMap not initialized") }
        )

        val report = GameLog.buildCrashReport(IllegalStateException("the real problem"))

        assertContains(report, "the real problem")
        assertContains(report, "context unavailable")
        assertContains(report, "UninitializedPropertyAccessException")
    }

    @Test
    fun `a report is still useful with no context registered and nothing logged`() {
        GameLog.reset()
        val report = GameLog.buildCrashReport(IllegalStateException("very early failure"))

        assertContains(report, "very early failure")
        assertContains(report, "no context registered")
        assertContains(report, "nothing logged")
    }

    @Test
    fun `starting a session clears whatever the last one left behind`() {
        GameLog.log(GameLog.Category.SYSTEM, "from the previous session")
        GameLog.start(environment = emptyList())

        assertFalse(GameLog.recent().any { it.contains("from the previous session") })
    }

    @Test
    fun `a session announces itself so an empty log is distinguishable from a missing one`() {
        assertTrue(GameLog.recent().any { it.contains("session started") })
        assertEquals(GameLog.Category.SYSTEM.name, "SYSTEM")
    }

    @Test
    fun `logging without a sink still fills the ring`() {
        // The file may be unopenable (read-only install directory, full disk). The in-memory
        // history has to survive that, since it is what a crash report is built from.
        GameLog.start(environment = emptyList(), sink = null)
        GameLog.log(GameLog.Category.SAVE, "autosave written")
        assertTrue(GameLog.recent().any { it.contains("autosave written") })
    }
}
