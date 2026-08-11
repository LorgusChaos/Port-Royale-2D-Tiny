package com.portroyale.tiny.world

import com.badlogic.gdx.math.Vector2
import com.portroyale.tiny.factions.Faction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A ship's name in the diagnostics has to outlive the list it happens to be sitting in.
 *
 * The watchdog identified ships by their index in the fleet, which shifts every time anything
 * sinks or respawns - so its "report each problem once" rule quietly stopped working: one stuck
 * pirate was reported under a dozen different names and forty honest lines became fourteen
 * hundred. The tool built to cut through noise was manufacturing it.
 */
class AiShipIdentityTest {

    private fun trader() = AiShipFactory.trade(Faction.SPAIN, Vector2.Zero.cpy())

    @Test
    fun `no two ships share an identity`() {
        val ids = List(50) { trader().id }
        assertEquals(ids.size, ids.distinct().size, "identities were reused")
    }

    @Test
    fun `identity does not follow position in the fleet`() {
        val fleet = mutableListOf(trader(), trader(), trader())
        val survivor = fleet[2]
        val before = survivor.id

        fleet.removeAt(0)
        fleet.add(0, trader())

        assertEquals(before, survivor.id, "a sinking elsewhere renamed an untouched ship")
        assertNotEquals(fleet[0].id, survivor.id)
    }

    @Test
    fun `a fresh hull starts whole, chasing nobody and running from nobody`() {
        val ship = trader()
        assertEquals(ship.maxHull, ship.currentHull)
        assertTrue(ship.quarry == null)
        assertTrue(ship.fleeingFrom == null)
        assertEquals(0f, ship.chaseSeconds)
    }
}
