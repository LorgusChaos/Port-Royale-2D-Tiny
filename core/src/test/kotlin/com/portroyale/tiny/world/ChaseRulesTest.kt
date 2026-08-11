package com.portroyale.tiny.world

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every chase has to be able to end, and a pirate has to be able to finish a voyage.
 *
 * Neither was true. There was no limit on a chase at all - a pirate that could not catch a faster
 * merchantman followed it indefinitely - and nothing stopped a pirate attacking on its way out, so
 * it never reached anywhere. Between them those two omissions produced the whole complaint: the
 * water around the lair was a slaughterhouse, the rest of the map was empty, and every ship
 * involved moved as though it could not make up its mind.
 */
class ChaseRulesTest {

    @Test
    fun `a chase gives up before it can consume a whole voyage`() {
        // A cruise is measured in minutes; if a single chase could outlast one, a pirate would
        // spend its entire working life following one merchantman.
        assertTrue(
            Encounters.CHASE_MAX_SECONDS < 4 * 60f,
            "a chase may not outlast the shortest cruise"
        )
    }

    @Test
    fun `a hunter that stops gaining quits long before the hard limit`() {
        // The honest ending: the range has stopped shrinking, so the ship ahead is faster and
        // persistence will not change that. It should fire well before the clock does.
        assertTrue(Encounters.CHASE_STALL_SECONDS < Encounters.CHASE_MAX_SECONDS)
    }

    @Test
    fun `gaining ground means actually gaining, not yawing about`() {
        assertTrue(Encounters.CHASE_PROGRESS_MARGIN > 0f)
        // A margin as wide as the fighting range would let a hunter close to touching distance
        // while still counting as "not gaining".
        assertTrue(Encounters.CHASE_PROGRESS_MARGIN < Encounters.CONTACT_RADIUS)
    }

    @Test
    fun `a break-off outlasts the pause between target checks`() {
        // Otherwise giving up means nothing: the hunter drops one target and seizes the next a
        // couple of seconds later, which is exactly what it used to do.
        assertTrue(Encounters.CHASE_BREAK_OFF_SECONDS > Encounters.CHASE_MAX_SECONDS)
    }

    @Test
    fun `a chase re-lays its course often enough to stay pointed at a moving ship`() {
        assertTrue(Encounters.CHASE_CLOSE_REPATH_SECONDS < Encounters.CHASE_REPATH_SECONDS)
        assertTrue(Encounters.CHASE_REPATH_SECONDS <= 2f, "aim any staler than this visibly trails")
    }

    @Test
    fun `prey keeps running after the pursuer loses interest`() {
        assertTrue(Encounters.FLIGHT_COMMITMENT_SECONDS > 0f)
    }

    // --- who hunts shipping, and when ---

    @Test
    fun `only a pirate on station preys on merchantmen`() {
        assertTrue(Encounters.huntsShipping(AiShipRole.PIRATE, PirateVoyage.CRUISING))
        assertFalse(Encounters.huntsShipping(AiShipRole.PIRATE, PirateVoyage.TRANSIT))
        assertFalse(Encounters.huntsShipping(AiShipRole.PIRATE, PirateVoyage.HOMEBOUND))
    }

    @Test
    fun `navies never prey on merchantmen whatever they are doing`() {
        for (leg in PirateVoyage.entries) {
            assertFalse(Encounters.huntsShipping(AiShipRole.MILITARY, leg))
            assertFalse(Encounters.huntsShipping(AiShipRole.TRADE, leg))
        }
    }

    @Test
    fun `a pirate in transit is still a pirate as far as everyone else is concerned`() {
        // The leg governs only whether it *starts* on shipping. Navies still hunt it and it still
        // deals with them - a pirate that merchantmen and patrols alike ignored while it crossed
        // the map would look ridiculous.
        assertTrue(Encounters.isHostile(AiShipRole.MILITARY, AiShipRole.PIRATE))
        assertTrue(Encounters.isHostile(AiShipRole.PIRATE, AiShipRole.MILITARY))
    }

    @Test
    fun `a pirate starts out heading somewhere rather than already hunting`() {
        val fresh = AiShipFactory.pirate(com.badlogic.gdx.math.Vector2.Zero.cpy())
        assertTrue(fresh.voyage == PirateVoyage.TRANSIT)
        assertFalse(Encounters.huntsShipping(fresh.role, fresh.voyage))
    }
}
