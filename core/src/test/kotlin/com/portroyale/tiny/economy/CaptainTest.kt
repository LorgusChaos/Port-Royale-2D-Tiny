package com.portroyale.tiny.economy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptainTest {

    @Test
    fun `a freshly rolled candidate has no ship and no route`() {
        val candidate = Captain.rollCandidate()
        assertTrue(candidate.isIdle)
        assertNull(candidate.shipId)
        assertEquals("", candidate.portA)
        assertEquals("", candidate.portB)
        assertEquals(0, candidate.totalProfit)
        assertNull(candidate.cargoGood)
    }

    @Test
    fun `a candidate always has a name to show in the tavern`() {
        repeat(50) {
            val candidate = Captain.rollCandidate()
            assertTrue(candidate.name.isNotBlank())
            assertTrue(candidate.name.contains(" "), "expected a first and last name: ${candidate.name}")
        }
    }

    @Test
    fun `rolled stats stay inside their designed ranges`() {
        repeat(500) {
            val candidate = Captain.rollCandidate()
            assertTrue(candidate.tradeSense in -0.10f..0.10f, "tradeSense ${candidate.tradeSense}")
            assertTrue(candidate.caution in 0.2f..0.9f, "caution ${candidate.caution}")
        }
    }

    @Test
    fun `candidates are not all the same person`() {
        val rolled = (1..100).map { Captain.rollCandidate() }
        assertTrue(rolled.map { it.name }.distinct().size > 1)
        assertTrue(rolled.map { it.caution }.distinct().size > 1)
    }

    @Test
    fun `a captain stops being idle once given a ship`() {
        val captain = Captain.rollCandidate()
        captain.shipId = 3
        assertFalse(captain.isIdle)
    }

    @Test
    fun `hiring the first captain costs the base rate`() {
        assertEquals(2000, Captain.hireCost(0))
    }

    @Test
    fun `every additional captain costs sharply more than the last`() {
        val costs = (0..4).map { Captain.hireCost(it) }
        for (i in 1 until costs.size) {
            assertTrue(costs[i] > costs[i - 1] * 2, "captain $i at ${costs[i]} is not a real step up")
        }
    }

    @Test
    fun `leg progress runs from zero at departure to one on arrival`() {
        val captain = Captain.rollCandidate().apply {
            legDurationGameMinutes = 100f
            arrivesAtGameMinutes = 100f
        }
        assertEquals(0f, captain.legProgress(0f), 0.0001f)
        assertEquals(0.25f, captain.legProgress(25f), 0.0001f)
        assertEquals(0.5f, captain.legProgress(50f), 0.0001f)
        assertEquals(1f, captain.legProgress(100f), 0.0001f)
    }

    @Test
    fun `leg progress is clamped outside the leg`() {
        val captain = Captain.rollCandidate().apply {
            legDurationGameMinutes = 100f
            arrivesAtGameMinutes = 100f
        }
        assertEquals(0f, captain.legProgress(-500f), "before departure the ship sits at the origin")
        assertEquals(1f, captain.legProgress(9999f), "an overdue ship parks at the destination")
    }

    @Test
    fun `an idle captain still has a usable placeholder leg duration`() {
        // legProgress divides by it, so a zero here would put a NaN into the ship's map position.
        assertTrue(Captain.rollCandidate().legDurationGameMinutes > 0f)
        assertEquals(DEFAULT_LEG_MINUTES, Captain.rollCandidate().legDurationGameMinutes)
    }

    @Test
    fun `a captain starts out heading for the far port`() {
        assertTrue(Captain.rollCandidate().headingToB)
    }

    @Test
    fun `a captain is not sheltering until told to`() {
        val captain = Captain.rollCandidate()
        assertFalse(captain.sheltering)
        assertEquals("", captain.shelterPort)
    }
}
