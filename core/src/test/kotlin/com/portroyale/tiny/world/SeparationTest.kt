package com.portroyale.tiny.world

import com.portroyale.tiny.ships.ShipTypes
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Keeping hulls from sharing a pixel must never become a force that sails ships around.
 *
 * This is a real regression, not a hypothetical: the drift rate was set at nearly twice the
 * speed of the slowest ship in the game and applied once per crowding neighbour, so anything in
 * company was pushed harder than it could sail and stopped dead. It survived unnoticed because a
 * separate bug had switched separation off for every moving ship - the day that was fixed, a
 * third of the fleet stopped moving and the watchdog lit up.
 */
class SeparationTest {

    private val slowestHull = ShipTypes.ALL.minOf { it.speed }

    @Test
    fun `crowding drifts a hull slower than it can sail`() {
        assertTrue(
            WorldScale.SEPARATION_DRIFT < slowestHull,
            "drift ${WorldScale.SEPARATION_DRIFT} outruns the slowest hull at $slowestHull"
        )
    }

    @Test
    fun `no crowd, however thick, can out-shove a ship's own sails`() {
        // The per-neighbour rate alone is not enough of a guarantee - the pushes add up, so the
        // total is clamped to a fraction of the ship's own speed.
        assertTrue(WorldScale.SEPARATION_SPEED_FRACTION > 0f, "separation has to do something")
        assertTrue(
            WorldScale.SEPARATION_SPEED_FRACTION < 1f,
            "a ship may not be displaced faster than it sails"
        )
    }

    @Test
    fun `hulls are held far enough apart not to overlap on screen`() {
        assertTrue(WorldScale.HULL_CLEARANCE >= WorldScale.GALLEON_LENGTH)
    }

    @Test
    fun `a chase can still end in a fight despite the clearance`() {
        assertTrue(Encounters.CONTACT_RADIUS < WorldScale.HULL_CLEARANCE)
    }
}
