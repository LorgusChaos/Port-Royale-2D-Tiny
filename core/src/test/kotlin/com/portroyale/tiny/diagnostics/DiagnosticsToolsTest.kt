package com.portroyale.tiny.diagnostics

import com.portroyale.tiny.combat.CombatOutcome
import com.portroyale.tiny.combat.CombatState
import com.portroyale.tiny.combat.ShipCombatant
import com.portroyale.tiny.economy.Captain
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.ships.ShipTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The seed is only worth having if it actually reproduces a session's randomness - and only
 *  safe if reproducing one does not disturb the sequence the game is running on. */
class GameRandomTest {

    @Test
    fun `the same seed replays the same sequence`() {
        val first = GameRandom.withSeed(1234L) { List(20) { GameRandom.nextInt(0, 1000) } }
        val second = GameRandom.withSeed(1234L) { List(20) { GameRandom.nextInt(0, 1000) } }
        assertEquals(first, second)
    }

    @Test
    fun `different seeds give different sequences`() {
        val a = GameRandom.withSeed(1L) { List(20) { GameRandom.nextInt(0, 1000) } }
        val b = GameRandom.withSeed(2L) { List(20) { GameRandom.nextInt(0, 1000) } }
        assertNotEquals(a, b)
    }

    @Test
    fun `a seeded replay leaves the running sequence untouched`() {
        // Replaying a reported seed must not perturb the session doing the replaying, or
        // investigating a bug would change the very thing being investigated.
        GameRandom.reseed(999L)
        val expected = List(5) { GameRandom.nextInt(0, 1000) }

        GameRandom.reseed(999L)
        val before = GameRandom.nextInt(0, 1000)
        GameRandom.withSeed(1L) { repeat(50) { GameRandom.nextFloat() } }
        val after = List(4) { GameRandom.nextInt(0, 1000) }

        assertEquals(expected, listOf(before) + after)
        assertEquals(999L, GameRandom.seed, "the session's own seed should be restored")
    }

    @Test
    fun `a whole fight replays identically from one seed`() {
        // The real payoff: "seed 4242, the fight went wrong" becomes something re-runnable.
        fun fightLog(): List<String> = GameRandom.withSeed(4242L) {
            val player = Player()
            val combat = CombatState(
                player,
                ShipCombatant("Sloop", 100, 15, 25, "p.png"),
                ShipCombatant("Pirate", 60, 8, 16, "e.png", goldReward = 120)
            )
            val entries = mutableListOf<String>()
            repeat(40) {
                if (!combat.isOver) {
                    combat.playerAttack()
                    entries.add(combat.log)
                }
            }
            entries.add(combat.outcome.name)
            entries
        }

        val first = fightLog()
        assertEquals(first, fightLog())
        assertNotEquals(CombatOutcome.ONGOING.name, first.last())
    }

    @Test
    fun `rolled captains replay from a seed too`() {
        fun roll() = GameRandom.withSeed(77L) {
            List(5) { Captain.rollCandidate() }.map { "${it.name}/${it.tradeSense}/${it.caution}" }
        }
        assertEquals(roll(), roll())
    }

    @Test
    fun `inclusive helpers cover both ends of their range`() {
        val values = GameRandom.withSeed(5L) { List(400) { GameRandom.nextIntInclusive(1, 5) } }
        assertEquals(setOf(1, 2, 3, 4, 5), values.toSet())
    }

    @Test
    fun `a session always has a seed to report`() {
        assertNotEquals(0L, GameRandom.seed, "an unseeded session could not be reproduced")
        assertTrue(ShipTypes.ALL.isNotEmpty()) // sanity: the game data loaded at all
    }
}

/** The watchdog's whole value is saying something once. A check that fires every frame is noise,
 *  and noise is indistinguishable from no logging at all. */
class WorldWatchdogTest {

    @Test
    fun `nothing is checked until the interval has elapsed`() {
        val watchdog = WorldWatchdog(intervalSeconds = 5f) {}
        var checks = 0
        repeat(4) { watchdog.update(1f) { checks++ } }
        assertEquals(0, checks)

        watchdog.update(1f) { checks++ }
        assertEquals(1, checks)
    }

    @Test
    fun `a lasting problem is reported once, not every sweep`() {
        val reports = mutableListOf<String>()
        val watchdog = WorldWatchdog(intervalSeconds = 1f) { reports.add(it) }

        repeat(10) {
            watchdog.update(1f) { reporter ->
                reporter.problem("pirate-3/stuck") { "pirate-3 is stuck" }
            }
        }

        assertEquals(1, reports.size, "reported ${reports.size} times: $reports")
        assertEquals("pirate-3 is stuck", reports.single())
    }

    @Test
    fun `a problem that clears and returns is reported again`() {
        val reports = mutableListOf<String>()
        val watchdog = WorldWatchdog(intervalSeconds = 1f) { reports.add(it) }
        var broken = true

        repeat(6) {
            watchdog.update(1f) { reporter ->
                if (broken) reporter.problem("ship/aground") { "ship aground" }
            }
            if (it == 2) broken = false
            if (it == 4) broken = true
        }

        assertEquals(2, reports.size, "a recurrence should not be swallowed: $reports")
    }

    @Test
    fun `separate subjects with the same fault are separate reports`() {
        val reports = mutableListOf<String>()
        val watchdog = WorldWatchdog(intervalSeconds = 1f) { reports.add(it) }

        watchdog.update(1f) { reporter ->
            reporter.problem("pirate-1/stuck") { "pirate-1 stuck" }
            reporter.problem("pirate-2/stuck") { "pirate-2 stuck" }
        }

        assertEquals(2, reports.size)
    }

    @Test
    fun `describing a problem costs nothing while nothing is wrong`() {
        // The message builder must stay lazy: these run against every ship on every sweep.
        var described = 0
        val watchdog = WorldWatchdog(intervalSeconds = 1f) {}
        repeat(5) {
            watchdog.update(1f) { reporter ->
                reporter.problem("same/key") { described++; "problem" }
            }
        }
        assertEquals(1, described, "the description was built $described times")
    }

    @Test
    fun `forgetting lets everything be reported afresh`() {
        val reports = mutableListOf<String>()
        val watchdog = WorldWatchdog(intervalSeconds = 1f) { reports.add(it) }
        watchdog.update(1f) { it.problem("a/b") { "trouble" } }
        watchdog.forget()
        watchdog.update(1f) { it.problem("a/b") { "trouble" } }
        assertEquals(2, reports.size)
    }

    @Test
    fun `a sweep with no problems reports nothing`() {
        val reports = mutableListOf<String>()
        val watchdog = WorldWatchdog(intervalSeconds = 1f) { reports.add(it) }
        repeat(5) { watchdog.update(1f) { } }
        assertTrue(reports.isEmpty())
        assertFalse(reports.isNotEmpty())
    }
}
