package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.portroyale.tiny.factions.Faction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rules that decide who attacks whom. What matters here is not any single number but the
 * shape: nothing certain, nothing impossible, and the answer always derived from the hulls the
 * two ships actually have rather than from which of them happens to be inside an event.
 */
class EncountersTest {

    private fun trader() = AiShipFactory.trade(Faction.SPAIN, Vector2.Zero.cpy())
    private fun warship() = AiShipFactory.military(Faction.ENGLAND, Vector2.Zero.cpy())

    /** The factory rolls a random tier, and a tier-1 pirate and a tier-5 pirate are different
     *  animals - so every pirate here is built by hand. */
    private fun pirate(tier: Int) = AiShip(
        role = AiShipRole.PIRATE, faction = null, spritePath = "p.png", tint = Color.WHITE,
        speed = 10f, maxHull = 25 + tier * 25, minDamage = 3 + tier * 4, maxDamage = 6 + tier * 7,
        goldReward = 40 + tier * 45, tier = tier, position = Vector2.Zero.cpy()
    )

    // --- contact ---

    @Test
    fun `hulls have to be nearly touching before shots are exchanged`() {
        // The old 52 units was wider than the mark left on the water where a ship went down:
        // merchantmen died with a clear gap of sea between them and their attacker.
        assertTrue(
            Encounters.CONTACT_RADIUS < WorldScale.TYPICAL_SHIP_LENGTH,
            "contact at ${Encounters.CONTACT_RADIUS} is further apart than a ship is long"
        )
    }

    @Test
    fun `a chase can actually reach contact through the anti-stacking rule`() {
        // Hulls are kept a full ship length apart so they never overlap on screen, and that is
        // wider than the range at which they may fire - so an attacker and its target have to be
        // exempt from the clearance rule, or no chase on the map could ever end in a fight.
        assertTrue(
            Encounters.CONTACT_RADIUS < WorldScale.HULL_CLEARANCE,
            "contact is outside the clearance radius, so the exemption is pointless"
        )
    }

    @Test
    fun `clearance covers the longest hull afloat`() {
        // At the old 34 units two Galleons held exactly at the limit still overlapped bow to
        // stern: the biggest ships were the ones seen sailing through each other.
        assertTrue(WorldScale.HULL_CLEARANCE >= WorldScale.GALLEON_LENGTH)
    }

    @Test
    fun `everybody sees exactly as far as everybody else`() {
        // There were three sight ranges, invented per role: hunters saw 360, merchantmen 420,
        // and a pirate noticed the player at 140. A lookout sees what there is to see; he does
        // not see further because of what his ship does for a living. One number, symmetrical,
        // so an escape is decided by speed and handling rather than by who noticed first.
        assertTrue(Encounters.SIGHT_RADIUS > WorldScale.HULL_CLEARANCE, "ships would meet unseen")
        assertTrue(
            Encounters.CHASE_CLOSE_RANGE <= Encounters.SIGHT_RADIUS,
            "a chase cannot be closer-quarters than the range at which it can start"
        )
    }

    @Test
    fun `a chase is long enough to close the distance sight allows`() {
        // Spotting at SIGHT_RADIUS and touching at CONTACT_RADIUS leaves this much to make up.
        // If the clock ran out before a modestly faster hull could cover it, no chase would ever
        // succeed and pirates would be ornamental.
        val toClose = Encounters.SIGHT_RADIUS - Encounters.CONTACT_RADIUS
        val modestEdge = 3f // units per second in hand, out of hull speeds around 10 to 16
        assertTrue(
            toClose / modestEdge <= Encounters.CHASE_MAX_SECONDS,
            "closing $toClose units at $modestEdge/s takes longer than a chase is allowed to last"
        )
    }

    // --- who bothers whom ---

    @Test
    fun `pirates prey on merchantmen and will fight the navy sent after them`() {
        assertTrue(Encounters.isHostile(AiShipRole.PIRATE, AiShipRole.TRADE))
        assertTrue(Encounters.isHostile(AiShipRole.PIRATE, AiShipRole.MILITARY))
    }

    @Test
    fun `navies hunt pirates and nobody else`() {
        assertTrue(Encounters.isHostile(AiShipRole.MILITARY, AiShipRole.PIRATE))
        // There is no war system yet; a navy sinking neutral shipping would be violence the
        // player can neither see the reason for nor do anything about.
        assertFalse(Encounters.isHostile(AiShipRole.MILITARY, AiShipRole.TRADE))
        assertFalse(Encounters.isHostile(AiShipRole.MILITARY, AiShipRole.MILITARY))
    }

    @Test
    fun `merchantmen start nothing`() {
        for (role in AiShipRole.entries) {
            assertFalse(Encounters.isHostile(AiShipRole.TRADE, role), "a trader went hunting $role")
        }
    }

    // --- strength is read from the hull as it stands ---

    @Test
    fun `a damaged ship is genuinely a softer target`() {
        val whole = warship()
        val battered = warship().also { it.currentHull = it.maxHull / 4 }
        assertTrue(Encounters.strength(battered) < Encounters.strength(whole))

        // And that has to show up in the decision, not merely in the arithmetic: a mid-weight
        // pirate that would think twice about a fresh frigate should fancy a crippled one.
        val hunter = pirate(3)
        assertTrue(
            Encounters.odds(hunter, battered) > Encounters.odds(hunter, whole),
            "a wounded warship did not read as easier prey"
        )
    }

    @Test
    fun `a hulk with nothing left to fire back with is simply taken`() {
        val helpless = trader().also { it.currentHull = 0 }
        assertEquals(1f, Encounters.odds(pirate(1), helpless))
    }

    // --- the odds curve ---

    @Test
    fun `nothing is ever certain and nothing is ever barred`() {
        val featherweight = pirate(1)
        val fresh = warship()
        assertTrue(Encounters.odds(featherweight, fresh) > 0f, "a long shot must stay possible")
        assertTrue(Encounters.odds(pirate(5), trader()) < 1f, "no fight may be a foregone conclusion")
    }

    @Test
    fun `the odds rise with the strength behind them`() {
        val target = warship()
        val odds = (1..5).map { Encounters.odds(pirate(it), target) }
        assertEquals(odds.sorted(), odds, "a heavier pirate was not more willing: $odds")
    }

    @Test
    fun `an even match at even temperament is a coin toss`() {
        // Traders are the one role with no thumb on the scale, so they are what the base curve
        // can be read off.
        val a = trader()
        val b = trader()
        assertEquals(0.5f, Encounters.odds(a, b), 0.001f)
    }

    @Test
    fun `pirates press harder than navies do`() {
        // Same two hulls, opposite roles: the one after cargo takes the fight more often than the
        // one keeping order.
        val bold = pirate(3)
        val professional = warship().also { it.currentHull = Encounters.strength(bold).toInt() / 24 }
        assertTrue(
            Encounters.odds(bold, professional) > Encounters.odds(professional, bold),
            "the navy was keener than the pirates"
        )
    }

    // --- and what the weaker party does about it ---

    @Test
    fun `a light pirate keeps out of a frigate's way`() {
        assertTrue(Encounters.shouldAvoid(pirate(1), warship()))
    }

    @Test
    fun `a heavy pirate does not run from a patrol`() {
        assertFalse(Encounters.shouldAvoid(pirate(5), warship()))
    }

    @Test
    fun `a pirate has no reason to avoid a merchantman`() {
        assertFalse(Encounters.shouldAvoid(pirate(1), trader()))
    }

    @Test
    fun `a navy loses its edge as it takes damage`() {
        // The point of persisting damage: a squadron that has been fighting all afternoon is a
        // squadron pirates start fancying their chances against.
        val worn = warship().also { it.currentHull = 30 }
        assertTrue(
            Encounters.shouldAvoid(pirate(2), warship()) && !Encounters.shouldAvoid(pirate(2), worn),
            "a badly damaged warship still frightened off a pirate"
        )
    }
}
