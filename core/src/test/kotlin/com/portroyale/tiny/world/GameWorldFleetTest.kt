package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.GdxNativesLoader
import com.portroyale.tiny.diagnostics.GameRandom
import com.portroyale.tiny.economy.Captain
import com.portroyale.tiny.economy.FleetSimulation
import com.portroyale.tiny.economy.MarketState
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.ships.ShipTypes
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Hired captains at sea, run through [GameWorld] on the real map - the fifth block moved out of
 * PortRoyaleGame (review batch 2, step 5): where a captain's ship is drawn, running for shelter
 * and coming out again, and a fight with an AI ship.
 *
 * The game clock is global, so every test starts it from zero and the leg times are read off the
 * captain rather than assumed. Fights are rigged so only one outcome can happen.
 */
class GameWorldFleetTest {

    companion object {
        private lateinit var worldMap: WorldMap
        private lateinit var ports: List<ResolvedPort>

        @JvmStatic
        @BeforeAll
        fun loadRealMap() {
            GdxNativesLoader.load()
            val bytes = File("../assets/maps/caribbean_mask.png").readBytes()
            val pixmap = Pixmap(bytes, 0, bytes.size)
            worldMap = WorldMap(pixmap, MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT, 10f)
            pixmap.dispose()
            ports = resolvePorts(worldMap)
        }

        private const val SEED = 47L
        private const val PORT_A = "Havana"
        private const val PORT_B = "Portobelo"
    }

    private class RecordedEvents : WorldEvents {
        val toasts = mutableListOf<Pair<String, ToastIcon>>()
        val offered = mutableListOf<AiShip>()
        override fun toast(message: String, icon: ToastIcon) {
            toasts += message to icon
        }
        override fun offerEncounter(ship: AiShip) {
            offered += ship
        }
        override fun arrivedAtPort(port: ResolvedPort) = Unit
        override fun startCombat(ship: AiShip) = Unit
    }

    private lateinit var events: RecordedEvents
    private lateinit var player: Player
    private lateinit var world: GameWorld

    @BeforeTest
    fun newWorld() {
        GameClock.reset()
        MarketState.reset()
        events = RecordedEvents()
        player = Player(100_000)
        world = GameWorld(worldMap, ports, player, events)
        // As the game refills it every frame: a fleet manoeuvre spends from it (finding 34).
        world.routeBudget = 6
    }

    @AfterEach
    fun stopTheClock() {
        GameClock.reset()
    }

    /** The player's first Sloop, crewed and sent from [PORT_A] to [PORT_B]; the player sails a
     *  Cutter, since the hull the player is aboard is never free for a captain. */
    private fun hire(caution: Float = 0.5f): Captain {
        val sloopId = player.activeShipId
        player.buyShip(ShipTypes.CUTTER, "Runner", PORT_A)
        val captain = Captain(name = "Test Captain", tradeSense = 0f, caution = caution)
        assertTrue(
            FleetSimulation.hire(player, captain, sloopId, PORT_A, PORT_B, GameClock.totalGameMinutes, world::fleetSeaDistance),
            "the captain could not be hired"
        )
        return captain
    }

    private fun pirate(at: Vector2, damage: Int = 0, hull: Int = 1) = AiShip(
        role = AiShipRole.PIRATE, faction = null, spritePath = "p.png", tint = Color.WHITE,
        speed = 10f, maxHull = hull, minDamage = damage, maxDamage = damage, goldReward = 150, tier = 3,
        position = at.cpy()
    ).also { it.anchor = at.cpy() }

    /** The first point on open water [radius] from [centre], going round the compass. */
    private fun waterAround(centre: Vector2, radius: Float): Vector2 {
        for (degrees in 0 until 360 step 5) {
            val p = Vector2(radius, 0f).rotateDeg(degrees.toFloat()).add(centre)
            if (worldMap.isWater(p.x, p.y)) return p
        }
        error("no water $radius from $centre")
    }

    private fun lengthOf(path: List<Vector2>): Float = (0 until path.size - 1).sumOf { path[it].dst(path[it + 1]).toDouble() }.toFloat()

    /** The point [fraction] of the way along [path] by distance sailed - worked out here, not
     *  borrowed from the code under test. */
    private fun pointAlong(path: List<Vector2>, fraction: Float): Vector2 {
        var left = lengthOf(path) * fraction
        for (i in 0 until path.size - 1) {
            val leg = path[i].dst(path[i + 1])
            if (left <= leg) return path[i].cpy().lerp(path[i + 1], if (leg > 0f) left / leg else 0f)
            left -= leg
        }
        return path.last().cpy()
    }

    // --- under way ---------------------------------------------------------------------------

    @Test
    fun `a captain under way is drawn on the sea route, as far along it as the clock says`() {
        val captain = hire()
        val route = world.pathBetweenPorts(PORT_A, PORT_B)
        val sloop = player.ownedShips.first { it.id == captain.shipId }
        assertEquals(
            GameClock.realSecondsToGameMinutes(lengthOf(route) / sloop.effectiveSpeed), captain.legDurationGameMinutes, 0.5f,
            "the leg is timed by the sea route at the ship's own speed"
        )

        val (start, _) = assertNotNull(world.fleetShipTransform(captain))
        assertTrue(start.dst(route.first()) < 1f, "a captain just hired is still off $PORT_A, not at $start")

        GameClock.restore(captain.legDurationGameMinutes * 0.5f)
        val (halfway, heading) = assertNotNull(world.fleetShipTransform(captain))
        val expected = pointAlong(route, 0.5f)
        assertTrue(halfway.dst(expected) < 2f, "halfway through the leg the ship is at $halfway, not $expected")
        assertTrue(worldMap.isWater(halfway.x, halfway.y), "drawn aground at $halfway")
        val onward = pointAlong(route, 0.6f).sub(expected)
        val bow = Vector2(1f, 0f).rotateDeg(heading)
        assertTrue(bow.dot(onward) > 0f, "the ship faces back the way it came")
    }

    // --- shelter -----------------------------------------------------------------------------

    @Test
    fun `a captain who sights a pirate with a harbour nearer runs for it, waits while the pirate is about, and sails on once it has gone`() {
        GameRandom.withSeed(SEED) {
            val captain = hire()
            // Just out of harbour: clear of a port's safe water, but with some harbour nearer than
            // a ship can see - the only place the run-for-shelter branch can be taken.
            val leg = captain.legDurationGameMinutes
            var at: Vector2? = null
            var minute = 0f
            while (minute < leg && at == null) {
                GameClock.restore(minute)
                val p = world.fleetShipTransform(captain)!!.first
                if (!world.isNearAnyPort(p) && ports.minOf { it.approachPoint.dst(p) } < Encounters.SIGHT_RADIUS - 40f) at = p
                minute += leg / 400f
            }
            val position = assertNotNull(at, "the route never leaves a harbour's safe water within sight of one")
            val haven = ports.minByOrNull { it.approachPoint.dst(position) }!!
            val havenRange = haven.approachPoint.dst(position)
            val pirate = pirate(waterAround(position, (havenRange + Encounters.SIGHT_RADIUS) / 2f))
            world.aiShips += pirate

            world.updateFleetEncounters()
            assertEquals(haven.port.name, captain.shelterPort, "the captain did not run for the nearest harbour")
            assertFalse(captain.sheltering, "in harbour before getting there")
            assertSame(pirate, world.fleetPaths[captain]?.avoidedThreat)
            assertEquals(haven.port.name, world.fleetPaths[captain]?.dest, "drawn sailing somewhere else")

            GameClock.restore(captain.arrivesAtGameMinutes + 1f)
            world.updateFleet()
            assertTrue(captain.sheltering, "arrived, but not waiting in harbour")

            pirate.position.set(waterAround(haven.approachPoint, Encounters.SIGHT_RADIUS / 2f))
            world.updateFleetEncounters()
            assertTrue(captain.sheltering, "sailed out under the guns of the pirate waiting off the harbour")

            world.aiShips.remove(pirate)
            world.updateFleetEncounters()
            assertFalse(captain.sheltering, "still waiting with the sea clear")
            assertEquals("", captain.shelterPort)
            val onward = assertNotNull(world.fleetPaths[captain])
            assertEquals(PORT_B, onward.dest, "set out for somewhere other than the end of the leg")
            assertTrue(onward.path.first().dst(haven.approachPoint) < 1f, "the onward course does not start from the harbour it hid in")
            assertTrue(captain.arrivesAtGameMinutes > GameClock.totalGameMinutes, "arrived before leaving")
        }
    }

    @Test
    fun `a captain who sights a pirate far from any harbour steers around it once and keeps going`() {
        GameRandom.withSeed(SEED) {
            val captain = hire()
            GameClock.restore(captain.legDurationGameMinutes * 0.5f)
            val position = world.fleetShipTransform(captain)!!.first
            assertTrue(ports.minOf { it.approachPoint.dst(position) } > Encounters.SIGHT_RADIUS, "the route's midpoint is near a harbour")
            val pirate = pirate(waterAround(position, Encounters.SIGHT_RADIUS * 0.7f))
            world.aiShips += pirate
            val arrival = captain.arrivesAtGameMinutes

            world.updateFleetEncounters()
            val detour = assertNotNull(world.fleetPaths[captain])
            assertEquals("", captain.shelterPort, "ran for a harbour that is further than the pirate")
            assertSame(pirate, detour.avoidedThreat)
            assertEquals(PORT_B, detour.dest)
            assertTrue(captain.arrivesAtGameMinutes != arrival, "the leg's clock ignores the longer way round")

            world.updateFleetEncounters()
            assertSame(detour, world.fleetPaths[captain], "re-planned the same detour a second time")
        }
    }

    // --- caught ------------------------------------------------------------------------------

    @Test
    fun `a captain caught by a pirate with no guns sinks it, is paid for it, and a new pirate sails`() {
        GameRandom.withSeed(SEED) {
            val captain = hire()
            GameClock.restore(captain.legDurationGameMinutes * 0.5f)
            val position = world.fleetShipTransform(captain)!!.first
            val pirate = pirate(position, damage = 0, hull = 1)
            world.aiShips += pirate
            val gold = player.gold

            world.updateFleetEncounters()
            assertFalse(pirate in world.aiShips, "the beaten pirate is still at sea")
            assertEquals(1, world.aiShips.count { it.role == AiShipRole.PIRATE }, "no pirate took its place")
            assertEquals(gold + pirate.goldReward, player.gold, "the prize was not paid into the purse")
            assertEquals(pirate.goldReward, captain.totalProfit, "the prize is not on the captain's record")
            assertTrue(captain in player.captains)
            assertTrue(events.toasts.isEmpty(), "a won fight interrupts the player")
        }
    }

    @Test
    fun `a captain caught by a pirate that cannot be beaten is lost with the ship, and the player is told`() {
        GameRandom.withSeed(SEED) {
            val captain = hire(caution = 0f)
            val shipId = captain.shipId
            GameClock.restore(captain.legDurationGameMinutes * 0.5f)
            val position = world.fleetShipTransform(captain)!!.first
            world.aiShips += pirate(position, damage = 10_000, hull = 100_000)

            world.updateFleetEncounters()
            assertFalse(captain in player.captains, "the captain survived a fight they could not win")
            assertTrue(player.ownedShips.none { it.id == shipId }, "the ship survived its captain")
            assertEquals(1, events.toasts.size, "the loss was not reported")
            assertEquals(ToastIcon.Castaway, events.toasts.single().second)
            assertTrue(events.toasts.single().first.contains("Test Captain"))
        }
    }

    // --- the player's opponent is the player's (REVIEW.md, finding 7) ---

    @Test
    fun `a captain does not fight the ship the player is fighting`() {
        GameRandom.withSeed(SEED) {
            val captain = hire()
            GameClock.restore(captain.legDurationGameMinutes * 0.5f)
            val position = world.fleetShipTransform(captain)!!.first
            val pirate = pirate(position)
            world.aiShips += pirate
            world.startAiCombat(pirate)

            world.updateFleetEncounters()

            assertTrue(pirate in world.aiShips, "a captain sank the player's opponent in the middle of the fight")
            assertEquals(0, captain.totalProfit, "a captain was paid for the player's prize")
        }
    }

    @Test
    fun `a captain does not fight a ship the player has just been offered`() {
        GameRandom.withSeed(SEED) {
            val captain = hire()
            GameClock.restore(captain.legDurationGameMinutes * 0.5f)
            val position = world.fleetShipTransform(captain)!!.first
            world.shipPosition.set(position)
            val pirate = pirate(position)
            world.aiShips += pirate
            world.checkAiEncounters()
            assertEquals(listOf(pirate), events.offered, "precondition: the player is offered the fight")

            world.updateFleetEncounters()

            assertTrue(pirate in world.aiShips, "a captain sank the ship while the player was choosing")
            assertEquals(0, captain.totalProfit)
        }
    }

    /** REVIEW.md, finding 10. Two basins with a wall of land between them; the route runs along the
     *  south one and the nearest harbour is across the wall. No course can be laid to it, and the
     *  captain used to be drawn sailing a straight line through the land. */
    @Test
    fun `a captain with no way to the nearest harbour keeps to the route rather than sailing through land`() {
        GameRandom.withSeed(SEED) {
            val cell = 10f
            val columns = 40
            val rows = 20
            val pixmap = Pixmap(columns, rows, Pixmap.Format.RGBA8888)
            pixmap.setColor(1f, 1f, 1f, 1f) // water
            pixmap.fill()
            pixmap.setColor(0f, 0f, 0f, 1f) // land: one wall along row 10
            for (gx in 0 until columns) pixmap.drawPixel(gx, rows - 1 - 10)
            val walled = WorldMap(pixmap, columns * cell, rows * cell, cell)
            pixmap.dispose()
            fun at(gx: Int, gy: Int) = Vector2((gx + 0.5f) * cell, (gy + 0.5f) * cell)
            fun harbour(name: String, where: Vector2) = ResolvedPort(Ports.ALL.first { it.name == name }, where, where)
            val acrossTheWall = harbour("Nassau", at(20, 15))
            world = GameWorld(walled, listOf(harbour(PORT_A, at(2, 5)), harbour(PORT_B, at(37, 5)), acrossTheWall), player, events)
            world.routeBudget = 6

            val captain = hire()
            GameClock.restore(captain.legDurationGameMinutes * 0.5f)
            val position = world.fleetShipTransform(captain)!!.first
            val pirate = pirate(Vector2(position.x + 140f, position.y))
            world.aiShips += pirate
            assertTrue(acrossTheWall.approachPoint.dst(position) < pirate.position.dst(position), "precondition: the harbour is the nearer")

            world.updateFleetEncounters()

            assertEquals("", captain.shelterPort, "ran for a harbour there is no way to")
            assertNull(world.fleetPaths[captain]?.avoidedThreat, "the threat is marked as dealt with, so no other way will be tried")
        }
    }

    // --- pachka 10: a captain's loose ends (REVIEW.md, findings 31-34) ---

    /** Finding 31: a ship that never leaves the waters off the shelter port used to keep the
     *  captain there for the rest of the session. */
    @Test
    fun `a captain kept in harbour by a ship that never leaves sails on once the time is up`() {
        GameRandom.withSeed(SEED) {
            val captain = hire()
            val haven = ports.first { it.port.name == "Nassau" }
            FleetSimulation.departForShelter(captain, haven.port.name, 1f, GameClock.totalGameMinutes)
            GameClock.restore(captain.arrivesAtGameMinutes + 1f)
            world.updateFleet()
            assertTrue(captain.sheltering, "precondition: waiting in harbour")
            world.aiShips += pirate(waterAround(haven.approachPoint, Encounters.SIGHT_RADIUS / 2f))

            GameClock.restore(captain.arrivesAtGameMinutes + SHELTER_MAX_GAME_MINUTES - 1f)
            world.updateFleetEncounters()
            assertTrue(captain.sheltering, "left before the time was up, under the pirate's guns")

            GameClock.restore(captain.arrivesAtGameMinutes + SHELTER_MAX_GAME_MINUTES + 1f)
            world.updateFleetEncounters()
            assertFalse(captain.sheltering, "still waiting long past the time limit")
            assertEquals("", captain.shelterPort)
        }
    }

    /** Finding 32. */
    @Test
    fun `a pirate chasing a captain who is lost gives the chase up`() {
        GameRandom.withSeed(SEED) {
            val captain = hire()
            GameClock.restore(captain.legDurationGameMinutes * 0.5f)
            val position = world.fleetShipTransform(captain)!!.first
            val hunter = pirate(waterAround(position, 100f), damage = 20, hull = 200)
            world.aiShips += hunter
            world.updateAiShips(0.1f)
            assertTrue(hunter.isPursuing && hunter.pursuingCaptain === captain, "precondition: the pirate is after the captain")

            FleetSimulation.loseCaptain(player, captain)
            world.updateAiShips(0.1f)

            assertFalse(hunter.isPursuing, "still chasing a captain who is gone")
            assertNull(hunter.pursuingCaptain)
        }
    }

    /** Finding 33. */
    @Test
    fun `a captain recalled or lost leaves no drawn course behind`() {
        GameRandom.withSeed(SEED) {
            val captain = hire()
            val sloopId = captain.shipId!!
            world.fleetShipTransform(captain)
            assertTrue(world.fleetPaths.containsKey(captain), "precondition: a course is drawn")

            FleetSimulation.recall(player, captain)
            world.updateFleetEncounters()
            assertFalse(world.fleetPaths.containsKey(captain), "the recalled captain's course is still kept")

            assertTrue(FleetSimulation.assign(player, captain, sloopId, PORT_A, PORT_B, GameClock.totalGameMinutes, world::fleetSeaDistance))
            world.fleetShipTransform(captain)
            FleetSimulation.loseCaptain(player, captain)
            world.updateFleetEncounters()
            assertFalse(world.fleetPaths.containsKey(captain), "the lost captain's course is still kept")
        }
    }

    /** Finding 34: a manoeuvre is a course laid, and courses are rationed per frame. */
    @Test
    fun `a captain's manoeuvre waits for a frame with a course to spare`() {
        GameRandom.withSeed(SEED) {
            val captain = hire()
            GameClock.restore(captain.legDurationGameMinutes * 0.5f)
            val position = world.fleetShipTransform(captain)!!.first
            val pirate = pirate(waterAround(position, Encounters.SIGHT_RADIUS * 0.7f))
            world.aiShips += pirate

            world.routeBudget = 0
            world.updateFleetEncounters()
            assertNull(world.fleetPaths[captain]?.avoidedThreat, "a course was laid with none to spare")
            assertEquals(0, world.routeBudget)

            world.routeBudget = 2
            world.updateFleetEncounters()
            assertSame(pirate, world.fleetPaths[captain]?.avoidedThreat, "no manoeuvre with courses to spare")
            assertEquals(1, world.routeBudget, "the manoeuvre's course was not counted")
        }
    }
}
