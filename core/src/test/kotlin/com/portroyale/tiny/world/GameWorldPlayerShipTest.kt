package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.GdxNativesLoader
import com.portroyale.tiny.diagnostics.GameRandom
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.factions.Faction
import org.junit.jupiter.api.BeforeAll
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The player's own ship, run through [GameWorld] on the real map - the last block moved out of
 * PortRoyaleGame (review batch 2, step 6): the autopilot, the helm, and the hunt. The game still
 * reads the keys and works out what a click landed on; what the ship then does is the world's.
 */
class GameWorldPlayerShipTest {

    companion object {
        private lateinit var worldMap: WorldMap
        private lateinit var ports: List<ResolvedPort>
        /** Open water as far from every harbour as the map allows, with clear sea to the east. */
        private lateinit var openSea: Vector2

        @JvmStatic
        @BeforeAll
        fun loadRealMap() {
            GdxNativesLoader.load()
            val bytes = File("../assets/maps/caribbean_mask.png").readBytes()
            val pixmap = Pixmap(bytes, 0, bytes.size)
            worldMap = WorldMap(pixmap, MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT, 10f)
            pixmap.dispose()
            ports = resolvePorts(worldMap)
            openSea = findOpenSea()
        }

        private fun findOpenSea(): Vector2 {
            var best: Vector2? = null
            var bestClearance = 0f
            var y = 100f
            while (y < MapProjection.MAP_HEIGHT - 100f) {
                var x = 100f
                while (x < MapProjection.MAP_WIDTH - 400f) {
                    val clear = (0..30).all { worldMap.isWater(x + it * 10f, y) && worldMap.isWater(x + it * 10f, y + 40f) }
                    if (clear) {
                        val clearance = ports.minOf { it.worldPos.dst(x, y) }
                        if (clearance > bestClearance) {
                            bestClearance = clearance
                            best = Vector2(x, y)
                        }
                    }
                    x += 40f
                }
                y += 40f
            }
            return best!!
        }

        private const val SEED = 53L
        private const val TICK = 0.1f
    }

    private class RecordedEvents : WorldEvents {
        val toasts = mutableListOf<Pair<String, ToastIcon>>()
        val arrivals = mutableListOf<ResolvedPort>()
        override fun toast(message: String, icon: ToastIcon) {
            toasts += message to icon
        }
        override fun offerEncounter(ship: AiShip) = Unit
        override fun startCombat(ship: AiShip) = Unit
        override fun arrivedAtPort(port: ResolvedPort) {
            arrivals += port
        }
    }

    private lateinit var events: RecordedEvents
    private lateinit var player: Player
    private lateinit var world: GameWorld

    @BeforeTest
    fun newWorld() {
        events = RecordedEvents()
        player = Player()
        world = GameWorld(worldMap, ports, player, events)
    }

    private fun port(name: String) = ports.first { it.port.name == name }

    /** The first point on open water [radius] from [centre], going round the compass. */
    private fun waterAround(centre: Vector2, radius: Float): Vector2 {
        for (degrees in 0 until 360 step 5) {
            val p = Vector2(radius, 0f).rotateDeg(degrees.toFloat()).add(centre)
            if (worldMap.isWater(p.x, p.y)) return p
        }
        error("no water $radius from $centre")
    }

    /** Runs the autopilot until the course is spent or [seconds] pass, checking the hull is
     *  afloat on every tick. */
    private fun autopilot(seconds: Float) {
        var t = 0f
        while (t < seconds && world.navMode == NavMode.AUTOPILOT) {
            world.updateAutopilot(TICK)
            val p = world.shipPosition
            assertTrue(worldMap.isWater(p.x, p.y), "the autopilot ran the ship ashore at $p after ${t}s")
            t += TICK
        }
    }

    // --- the autopilot -----------------------------------------------------------------------

    @Test
    fun `the autopilot sails a laid course into harbour, makes port once, and pays off a passenger bound there`() {
        GameRandom.withSeed(SEED) {
            val haven = port("Port Royal")
            world.shipPosition.set(waterAround(haven.approachPoint, 400f))
            world.passengerDestination = haven

            assertTrue(world.setCourse(haven.approachPoint, haven), "no course to ${haven.port.name}")
            assertEquals(NavMode.AUTOPILOT, world.navMode)
            autopilot(300f)

            assertEquals(listOf(haven), events.arrivals, "the arrival was not reported exactly once")
            assertEquals(NavMode.MANUAL, world.navMode)
            assertTrue(world.path.isEmpty(), "a spent course is kept")
            assertNull(world.destinationPort)
            assertTrue(world.shipPosition.dst(haven.approachPoint) < 20f, "made port at ${world.shipPosition}, off the approach")
            assertNull(world.passengerDestination, "the passenger is still aboard in their own harbour")
            assertTrue(events.toasts.isNotEmpty(), "the passenger left without a word")

            repeat(20) { world.updateAutopilot(TICK) }
            assertEquals(1, events.arrivals.size, "made port again without sailing")
        }
    }

    @Test
    fun `a course to open water ends there without making port`() {
        val start = waterAround(openSea, 0f)
        val end = openSea.cpy().add(250f, 0f)
        world.shipPosition.set(start)

        assertTrue(world.setCourse(end, null))
        autopilot(120f)

        assertEquals(NavMode.MANUAL, world.navMode, "never got there")
        assertTrue(world.shipPosition.dst(end) < 20f, "stopped at ${world.shipPosition}, not $end")
        assertTrue(events.arrivals.isEmpty(), "made port in open water")
    }

    // --- the helm ----------------------------------------------------------------------------

    @Test
    fun `at the helm the ship drops its course and never runs ashore, however hard it is steered at the land`() {
        val haven = ports.first { !worldMap.isWater(it.worldPos.x, it.worldPos.y) }
        world.shipPosition.set(haven.approachPoint)
        assertTrue(world.setCourse(waterAround(haven.approachPoint, 300f), null))
        val towardLand = haven.worldPos.cpy().sub(haven.approachPoint)

        var t = 0f
        while (t < 20f) {
            world.sailByHand(towardLand.x, towardLand.y, TICK)
            val p = world.shipPosition
            assertTrue(worldMap.isWater(p.x, p.y), "steered aground at $p after ${t}s")
            t += TICK
        }

        assertEquals(NavMode.MANUAL, world.navMode, "the autopilot still has the ship")
        assertTrue(world.path.isEmpty())
        assertNull(world.destinationPort)
        assertEquals(MathUtils.atan2(towardLand.y, towardLand.x) * MathUtils.radDeg, world.shipHeadingDeg, 0.01f)
    }

    // --- the hunt ----------------------------------------------------------------------------

    @Test
    fun `a hunted ship is run down to contact, where the player's ship holds, and the hunt ends when it is gone`() {
        val quarry = AiShip(
            role = AiShipRole.TRADE, faction = Faction.SPAIN, spritePath = "t.png", tint = Color.WHITE,
            speed = 10f, maxHull = 100, minDamage = 0, maxDamage = 0, goldReward = 100, tier = 1,
            position = openSea.cpy().add(200f, 20f)
        )
        world.aiShips += quarry
        world.shipPosition.set(openSea)
        assertTrue(world.setCourse(openSea.cpy().add(0f, 30f), null))

        world.huntShip(quarry)
        assertEquals(NavMode.MANUAL, world.navMode, "the hunt left the autopilot in charge")
        assertTrue(world.path.isEmpty())

        var t = 0f
        while (t < 60f && world.shipPosition.dst(quarry.position) >= Encounters.CONTACT_RADIUS) {
            world.updateShipHunt(TICK)
            t += TICK
        }
        assertTrue(world.shipPosition.dst(quarry.position) < Encounters.CONTACT_RADIUS, "never came alongside")
        val alongside = world.shipPosition.cpy()
        world.updateShipHunt(TICK)
        assertEquals(alongside, world.shipPosition, "sailed on past the ship it came alongside")
        assertSame(quarry, world.huntedShip)

        world.aiShips.remove(quarry)
        world.updateShipHunt(TICK)
        assertNull(world.huntedShip, "still hunting a ship that is no longer at sea")
    }

    // --- a long frame is a long way, not a jump (REVIEW.md, finding 15) ---

    @Test
    fun `one long frame takes the ship to its waypoint, not past it`() {
        val end = openSea.cpy().add(100f, 0f)
        world.shipPosition.set(openSea)
        assertTrue(world.setCourse(end, null))

        world.updateAutopilot(60f)

        assertTrue(world.shipPosition.dst(end) < 1f, "a sixty-second frame left the ship at ${world.shipPosition}, not $end")
        assertTrue(worldMap.isWater(world.shipPosition.x, world.shipPosition.y))
    }

    @Test
    fun `one long step at the helm does not jump a strip of land`() {
        val cell = 10f
        val pixmap = Pixmap(20, 20, Pixmap.Format.RGBA8888)
        pixmap.setColor(1f, 1f, 1f, 1f) // water
        pixmap.fill()
        pixmap.setColor(0f, 0f, 0f, 1f) // land: one column at gx = 10, x 100..110
        for (gy in 0 until 20) pixmap.drawPixel(10, gy)
        val strip = WorldMap(pixmap, 200f, 200f, cell)
        pixmap.dispose()
        world = GameWorld(strip, emptyList(), player, events)
        world.shipPosition.set(75f, 105f)

        world.sailByHand(1f, 0f, 60f / player.effectiveSpeed)

        assertTrue(world.shipPosition.x < 100f, "the ship jumped the land to ${world.shipPosition}")
        assertTrue(strip.isWater(world.shipPosition.x, world.shipPosition.y))
    }

    @Test
    fun `a frame of five seconds sails the ship where five frames of a second do`() {
        val end = openSea.cpy().add(280f, 0f)
        val other = GameWorld(worldMap, ports, Player(), events)
        for (w in listOf(world, other)) {
            w.shipPosition.set(openSea)
            assertTrue(w.setCourse(end, null))
        }

        world.updateAutopilot(5f)
        repeat(5) { other.updateAutopilot(1f) }

        assertTrue(world.shipPosition.dst(other.shipPosition) < 1f, "${world.shipPosition} against ${other.shipPosition}")
    }
}
