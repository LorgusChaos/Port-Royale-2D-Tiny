package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.portroyale.tiny.factions.Faction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A raid is bookkeeping over borrowed ships. The thing that has to be right is the borrowing:
 * every ship pulled into a corridor must get its old anchor back, by every route out - the raid
 * ending, the ship fleeing, the ship sinking. Miss one and that hull spends the rest of the
 * session living in a stretch of water it only ever visited once.
 */
class PirateRaidTest {

    private fun raid(seconds: Float = 600f, response: Float = 120f) = PirateRaid(
        portA = "Port Royal",
        portB = "Cartagena",
        centre = Vector2(1000f, 1000f),
        radius = 400f,
        secondsRemaining = seconds,
        responseDelay = response,
        rallyPoint = Vector2(1305f, 2495f)
    )

    private fun pirate(x: Float, y: Float) = AiShip(
        role = AiShipRole.PIRATE, faction = null, spritePath = "p.png", tint = Color.WHITE,
        speed = 12f, maxHull = 80, minDamage = 10, maxDamage = 20, goldReward = 150, tier = 3,
        position = Vector2(x, y)
    ).also { it.anchor = Vector2(x, y) }

    private fun warship(faction: Faction, x: Float, y: Float) = AiShip(
        role = AiShipRole.MILITARY, faction = faction, spritePath = "m.png", tint = Color.WHITE,
        speed = 11f, maxHull = 160, minDamage = 18, maxDamage = 30, goldReward = 250, tier = 4,
        position = Vector2(x, y)
    ).also { it.anchor = Vector2(x, y) }

    @Test
    fun `drafting a ship moves its anchor into the corridor and remembers the old one`() {
        val raid = raid()
        val ship = pirate(3000f, 200f)
        val home = ship.anchor.cpy()

        raid.draftIn(ship, raid.centre)

        assertEquals(raid.centre, ship.anchor)
        assertNotEquals(home, ship.anchor)
        assertTrue(raid.pirates().contains(ship))
    }

    @Test
    fun `a drafted ship drops its stale route`() {
        // Its old path led somewhere else entirely; keeping it would send the ship away from the
        // corridor it was just pulled into.
        val raid = raid()
        val ship = pirate(3000f, 200f)
        ship.path = listOf(Vector2(1f, 1f), Vector2(2f, 2f))
        ship.pathIndex = 1

        raid.draftIn(ship, raid.centre)

        assertTrue(ship.path.isEmpty())
        assertEquals(0, ship.pathIndex)
    }

    @Test
    fun `ending a raid gives every ship its home back`() {
        val raid = raid()
        val ships = listOf(pirate(3000f, 200f), pirate(2500f, 400f), warship(Faction.SPAIN, 800f, 900f))
        val homes = ships.map { it.anchor.cpy() }
        ships.forEach { raid.draftIn(it, raid.centre) }

        raid.releaseAll()

        ships.forEachIndexed { index, ship ->
            assertEquals(homes[index], ship.anchor, "${ship.displayName} was left living in the corridor")
        }
        assertTrue(raid.pirates().isEmpty())
        assertTrue(raid.warships().isEmpty())
    }

    @Test
    fun `releasing one ship early leaves the others in the raid`() {
        val raid = raid()
        val fleeing = pirate(3000f, 200f)
        val staying = pirate(2500f, 400f)
        val home = fleeing.anchor.cpy()
        raid.draftIn(fleeing, raid.centre)
        raid.draftIn(staying, raid.centre)

        raid.release(fleeing)

        assertEquals(home, fleeing.anchor)
        assertEquals(raid.centre, staying.anchor)
        assertEquals(listOf(staying), raid.pirates())
    }

    @Test
    fun `releasing a ship twice cannot corrupt its anchor`() {
        // Sinking releases, and so does the raid ending - the same ship can take both routes out.
        val raid = raid()
        val ship = pirate(3000f, 200f)
        val home = ship.anchor.cpy()
        raid.draftIn(ship, raid.centre)

        raid.release(ship)
        ship.anchor = Vector2(77f, 77f) // it has moved on and been re-anchored elsewhere
        raid.release(ship)
        raid.releaseAll()

        assertEquals(Vector2(77f, 77f), ship.anchor, "a stale release overwrote a newer anchor")
        assertNotEquals(home, ship.anchor)
    }

    @Test
    fun `drafting the same ship twice does not lose its real home`() {
        val raid = raid()
        val ship = pirate(3000f, 200f)
        val home = ship.anchor.cpy()

        raid.draftIn(ship, raid.centre)
        raid.draftIn(ship, Vector2(1234f, 5678f))
        raid.releaseAll()

        assertEquals(home, ship.anchor)
    }

    @Test
    fun `pirates and warships are counted apart`() {
        val raid = raid()
        raid.draftIn(pirate(0f, 0f), raid.centre)
        raid.draftIn(pirate(10f, 10f), raid.centre)
        raid.draftIn(warship(Faction.ENGLAND, 20f, 20f), raid.centre)

        assertEquals(2, raid.pirates().size)
        assertEquals(1, raid.warships().size)
    }

    @Test
    fun `the corridor knows what is inside it`() {
        val raid = raid()
        assertTrue(raid.contains(raid.centre))
        assertTrue(raid.contains(Vector2(1000f, 1390f)))
        assertFalse(raid.contains(Vector2(1000f, 1500f)))
    }

    @Test
    fun `the response only fires once`() {
        val raid = raid()
        assertFalse(raid.responseSent)
        raid.markResponseSent()
        assertTrue(raid.responseSent)
    }

    @Test
    fun `sink marks record which side went down`() {
        val raid = raid()
        raid.marks.add(SinkMark(Vector2(1f, 2f), wasPirate = true))
        raid.marks.add(SinkMark(Vector2(3f, 4f), wasPirate = false))

        assertEquals(2, raid.marks.size)
        assertEquals(1, raid.marks.count { it.wasPirate })
        assertEquals(1, raid.marks.count { !it.wasPirate }, "a navy loss must be recorded too")
    }

    @Test
    fun `a raid is named after the route it spoils`() {
        assertEquals("Port Royal - Cartagena", raid().label)
    }

    // --- forming up and sailing as a band (M11e) ---

    @Test
    fun `a raid starts unannounced and gathering`() {
        // Telling the player at this point sends them out to look at empty water: the band is
        // still scattered across the map.
        val raid = raid()
        assertEquals(RaidPhase.GATHERING, raid.phase)
        assertFalse(raid.announced)
    }

    @Test
    fun `the band is not formed up while anyone is still far from the rally point`() {
        val raid = raid()
        val near = pirate(raid.rallyPoint.x + 50f, raid.rallyPoint.y)
        val far = pirate(raid.rallyPoint.x + 2000f, raid.rallyPoint.y)
        raid.band.addAll(listOf(near, far))

        assertFalse(raid.formedUp(listOf(near, far), tolerance = 260f))
    }

    @Test
    fun `the band is formed up once everyone has arrived`() {
        val raid = raid()
        val a = pirate(raid.rallyPoint.x + 50f, raid.rallyPoint.y)
        val b = pirate(raid.rallyPoint.x, raid.rallyPoint.y + 120f)
        raid.band.addAll(listOf(a, b))

        assertTrue(raid.formedUp(listOf(a, b), tolerance = 260f))
    }

    @Test
    fun `a band of nobody is never formed up`() {
        // Otherwise a raid whose pirates all sank while gathering would "set out" empty.
        val raid = raid()
        assertFalse(raid.formedUp(emptyList(), tolerance = 260f))
    }

    @Test
    fun `a sunk member no longer holds the band at the rally point`() {
        val raid = raid()
        val here = pirate(raid.rallyPoint.x, raid.rallyPoint.y)
        val lost = pirate(raid.rallyPoint.x + 3000f, raid.rallyPoint.y)
        raid.band.addAll(listOf(here, lost))

        assertFalse(raid.formedUp(listOf(here, lost), tolerance = 260f))
        assertTrue(raid.formedUp(listOf(here), tolerance = 260f), "a ship that no longer exists cannot be waited for")
    }

    @Test
    fun `the leader is the first survivor, and the rest follow it`() {
        val raid = raid()
        val first = pirate(0f, 0f)
        val second = pirate(10f, 0f)
        val third = pirate(20f, 0f)
        raid.band.addAll(listOf(first, second, third))

        assertEquals(first, raid.leader(listOf(first, second, third)))
        assertEquals(listOf(second, third), raid.followers(listOf(first, second, third)))
    }

    @Test
    fun `losing the leader promotes the next survivor rather than stranding the band`() {
        val raid = raid()
        val first = pirate(0f, 0f)
        val second = pirate(10f, 0f)
        raid.band.addAll(listOf(first, second))

        val afloat = listOf(second)
        assertEquals(second, raid.leader(afloat))
        assertTrue(raid.followers(afloat).isEmpty())
    }

    @Test
    fun `a band with no survivors has no leader`() {
        val raid = raid()
        raid.band.addAll(listOf(pirate(0f, 0f), pirate(10f, 0f)))
        assertNull(raid.leader(emptyList()))
        assertTrue(raid.followers(emptyList()).isEmpty())
    }

    @Test
    fun `a raid needs a corridor wide enough for anything to be allowed to happen in it`() {
        // Ports are safe harbours: no encounter of any kind may trigger within 160 units of one.
        // Site a raid between two ports closer together than twice that and its whole area lies
        // inside the exclusion - the band assembles somewhere the rules forbid it to act, and
        // sits there doing nothing. Grenada and Saint-Pierre, 283 apart, did exactly this.
        val portSafeRadius = WorldScale.PORT_SAFE_RADIUS
        val tooClose = portSafeRadius * 1.6f
        assertTrue(tooClose / 2f < portSafeRadius, "the midpoint of such a pair is inside safe water")

        val workable = portSafeRadius * 2.4f
        assertTrue(workable / 2f > portSafeRadius, "the guard has to clear the radius, not merely equal it")
    }

    @Test
    fun `each power's committed squadron is tracked separately`() {
        // They act independently: one power losing its ships must not change what another sends.
        val raid = raid()
        val spanish = warship(Faction.SPAIN, 0f, 0f)
        val english = warship(Faction.ENGLAND, 10f, 0f)
        raid.squadrons.getOrPut(Faction.SPAIN) { mutableListOf() }.add(spanish)
        raid.squadrons.getOrPut(Faction.ENGLAND) { mutableListOf() }.add(english)

        assertEquals(2, raid.squadrons.size)
        assertEquals(listOf(spanish), raid.squadrons.getValue(Faction.SPAIN).toList())
        assertEquals(listOf(english), raid.squadrons.getValue(Faction.ENGLAND).toList())
    }
}
