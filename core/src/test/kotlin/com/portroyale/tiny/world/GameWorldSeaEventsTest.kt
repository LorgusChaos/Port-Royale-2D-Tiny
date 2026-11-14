package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.GdxNativesLoader
import com.portroyale.tiny.economy.Good
import com.portroyale.tiny.economy.Player
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
 * Sea events, run through [GameWorld] on the real map - the first block moved out of
 * PortRoyaleGame (review batch 2, step 1). These pin down today's rules, not today's dice: sea
 * events still roll MathUtils.random rather than GameRandom (finding 4, batch 6), so every
 * assertion here holds whatever the roll, and nothing seeds MathUtils.
 */
class GameWorldSeaEventsTest {

    companion object {
        private lateinit var worldMap: WorldMap
        private lateinit var ports: List<ResolvedPort>

        /** Once per class: nothing here changes the map, and reading it is the slow part. */
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
    }

    private class RecordedEvents : WorldEvents {
        val toasts = mutableListOf<Pair<String, ToastIcon>>()
        override fun toast(message: String, icon: ToastIcon) {
            toasts += message to icon
        }
        override fun offerEncounter(ship: AiShip) = Unit
        override fun arrivedAtPort(port: ResolvedPort) = Unit
        override fun startCombat(ship: AiShip) = Unit
    }

    private lateinit var player: Player
    private lateinit var events: RecordedEvents
    private lateinit var world: GameWorld

    @BeforeTest
    fun newWorld() {
        player = Player()
        events = RecordedEvents()
        world = GameWorld(worldMap, ports, player, events)
        // Every slot asleep, so the only event in play is the one a test places.
        world.seaEventSlots.forEach { it.respawnTimer = Float.MAX_VALUE }
        world.shipPosition.set(ports.first().approachPoint)
    }

    /** Puts an event of [type] in the first slot, [offset] units east of the ship. */
    private fun placeEvent(type: SeaEventType, offset: Float = 0f): SeaEventSlot {
        val slot = world.seaEventSlots.first()
        slot.point = SeaEventPoint(Vector2(world.shipPosition).add(offset, 0f), type)
        return slot
    }

    /** Lets the world lay down events in [count] fresh slots and returns what it placed. */
    private fun spawnMany(count: Int = 300): List<SeaEventPoint> {
        val slots = List(count) { SeaEventSlot() }
        slots.forEach { world.spawnSeaEvent(it) }
        return slots.mapNotNull { it.point }
    }

    @Test
    fun `sailing into a sea event raises it once and frees the slot`() {
        val slot = placeEvent(SeaEventType.BOTTLE)

        world.updateSeaEvents(0.1f)
        world.updateSeaEvents(0.1f)

        assertEquals(listOf<ToastIcon>(ToastIcon.Bottle), events.toasts.map { it.second })
        assertNull(slot.point, "a spent event leaves its slot")
        assertTrue(slot.respawnTimer in 44f..120f, "the slot counts down to a new event, got ${slot.respawnTimer}")
    }

    @Test
    fun `a sea event out of reach stays where it is`() {
        val slot = placeEvent(SeaEventType.BOTTLE, offset = 200f)

        world.updateSeaEvents(0.1f)

        assertTrue(events.toasts.isEmpty())
        assertNotNull(slot.point)
    }

    @Test
    fun `a chest of gold pays into the purse`() {
        val goldBefore = player.gold
        placeEvent(SeaEventType.CHEST_GOLD)

        world.updateSeaEvents(0.1f)

        assertTrue(player.gold - goldBefore in 200..800, "paid ${player.gold - goldBefore}")
        assertEquals(ToastIcon.Coin, events.toasts.single().second)
    }

    @Test
    fun `a chest of goods with room in the hold adds one good and shows its icon`() {
        val goldBefore = player.gold
        val cargoBefore = player.cargo.toMap()
        placeEvent(SeaEventType.CHEST_GOODS)

        world.updateSeaEvents(0.1f)

        val gained = Good.entries.filter { (player.cargo[it] ?: 0) != (cargoBefore[it] ?: 0) }
        val good = gained.single()
        assertTrue((player.cargo[good] ?: 0) - (cargoBefore[good] ?: 0) in 1..3)
        assertEquals(goldBefore, player.gold)
        assertEquals(ToastIcon.Cargo(good), events.toasts.single().second)
    }

    @Test
    fun `a chest of goods with the hold full pays loose coin instead`() {
        player.cargo[Good.entries.first()] = player.cargoCapacity
        val cargoBefore = player.cargo.toMap()
        val goldBefore = player.gold
        placeEvent(SeaEventType.CHEST_GOODS)

        world.updateSeaEvents(0.1f)

        assertEquals(goldBefore + 100, player.gold)
        assertEquals(cargoBefore, player.cargo.toMap())
        assertEquals(ToastIcon.Coin, events.toasts.single().second)
    }

    /** REVIEW.md, finding 8. A chest holds one to three units, drawn outside GameRandom (finding
     *  4), so one chest alone could pass by luck: ten in a row with a single berth free each time
     *  cannot all hold just one unit except by a one-in-59000 chance. */
    @Test
    fun `a chest of goods takes aboard only what the hold has room for`() {
        repeat(10) { chest ->
            Good.entries.forEach { player.cargo[it] = 0 }
            player.cargo[Good.entries.first()] = player.cargoCapacity - 1
            placeEvent(SeaEventType.CHEST_GOODS)

            world.updateSeaEvents(0.1f)

            assertEquals(player.cargoCapacity, player.cargoUsed, "chest ${chest + 1} left the hold over or under full")
            val (message, _) = events.toasts.last()
            assertTrue(message.contains("yields 1 "), "chest ${chest + 1} told of more than went aboard: $message")
        }
    }

    /** REVIEW.md, finding 9: two castaways can be in the water at once, and picking up the second
     *  used to overwrite the first - the first passenger and their reward gone without a word. */
    @Test
    fun `with a passenger aboard another castaway is left in the water for later`() {
        val aboard = ports.first()
        world.passengerDestination = aboard
        val slot = placeEvent(SeaEventType.CASTAWAY)

        world.updateSeaEvents(0.1f)

        assertSame(aboard, world.passengerDestination, "the second castaway took the first one's berth")
        assertTrue(slot.point != null, "the second castaway was picked up and lost")
        assertTrue(events.toasts.isEmpty(), "told of a rescue that did not happen")
    }

    @Test
    fun `a rescued castaway asks to be taken to one of the ports`() {
        placeEvent(SeaEventType.CASTAWAY)

        world.updateSeaEvents(0.1f)

        val destination = assertNotNull(world.passengerDestination)
        assertTrue(ports.any { it === destination })
        val (message, icon) = events.toasts.single()
        assertEquals(ToastIcon.Castaway, icon)
        assertTrue(destination.port.name in message, "the toast names the port: $message")
    }

    @Test
    fun `delivering the castaway pays them off and frees the berth`() {
        val destination = ports.last()
        world.passengerDestination = destination
        val goldBefore = player.gold

        world.deliverPassenger(destination)

        assertNull(world.passengerDestination)
        assertTrue(player.gold - goldBefore in 300..700, "paid ${player.gold - goldBefore}")
        assertEquals(ToastIcon.Coin, events.toasts.single().second)
    }

    @Test
    fun `the castaway stays aboard through every other port`() {
        val destination = ports.last()
        world.passengerDestination = destination
        val goldBefore = player.gold

        world.deliverPassenger(ports.first())

        assertSame(destination, world.passengerDestination)
        assertEquals(goldBefore, player.gold)
        assertTrue(events.toasts.isEmpty())
    }

    @Test
    fun `sea events appear on open water and never in a harbour`() {
        val points = spawnMany()

        assertTrue(points.isNotEmpty(), "300 slots and not one event placed")
        for (point in points) {
            val at = point.position
            assertTrue(worldMap.isWater(at.x, at.y), "event on land at $at")
            assertFalse(world.isNearAnyPort(at), "event inside a harbour at $at")
        }
    }

    @Test
    fun `no second castaway is offered while one is aboard`() {
        world.passengerDestination = ports.first()

        val points = spawnMany()

        assertTrue(points.isNotEmpty())
        assertTrue(points.none { it.type == SeaEventType.CASTAWAY })
    }
}
