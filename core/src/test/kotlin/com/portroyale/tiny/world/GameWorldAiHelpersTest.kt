package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.GdxNativesLoader
import com.portroyale.tiny.diagnostics.GameRandom
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.factions.REPUTATION_HOSTILE_THRESHOLD
import com.portroyale.tiny.ships.ShipTypes
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
 * The shared helpers of the AI ships, run through [GameWorld] on the real map - the second block
 * moved out of PortRoyaleGame (review batch 2, step 2): who sails at the start and from where, where
 * a ship goes next and by what water, what replaces a sunk ship, and who is hostile to whom.
 *
 * Everything here rolls GameRandom, so each test fixes the seed and the outcome is exact. The
 * assertions are still the rules rather than the rolls - they hold for any seed - so a seed that
 * happens to change in the future does not turn a correct world red.
 */
class GameWorldAiHelpersTest {

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

    private object NoEvents : WorldEvents {
        override fun toast(message: String, icon: ToastIcon) = Unit
        override fun offerEncounter(ship: AiShip) = Unit
        override fun arrivedAtPort(port: ResolvedPort) = Unit
        override fun startCombat(ship: AiShip) = Unit
    }

    private lateinit var player: Player
    private lateinit var world: GameWorld

    @BeforeTest
    fun newWorld() {
        player = Player()
        world = GameWorld(worldMap, ports, player, NoEvents)
    }

    /** A flag with more than one harbour, so "its own waters" leave a trader somewhere to go. */
    private val faction: Faction
        get() = Faction.entries.first { f -> ports.count { it.port.faction == f } > 1 }

    private fun harboursOf(faction: Faction): List<Vector2> =
        ports.filter { it.port.faction == faction }.map { it.approachPoint }

    private fun List<Vector2>.holds(point: Vector2) = any { it.epsilonEquals(point, 0.01f) }

    private fun assertAllWater(route: List<Vector2>) {
        for (point in route) assertTrue(worldMap.isWater(point.x, point.y), "route crosses land at $point")
    }

    @Test
    fun `a new world sails each flag's traders and warships out of that flag's own harbours`() {
        GameRandom.withSeed(7L) { world.spawnAiShips() }

        val flags = Faction.entries.filter { harboursOf(it).isNotEmpty() }
        for (flag in flags) {
            val own = world.aiShips.filter { it.faction == flag }
            assertEquals(TRADE_SHIPS_PER_FACTION, own.count { it.role == AiShipRole.TRADE }, "$flag traders")
            assertEquals(MILITARY_SHIPS_PER_FACTION, own.count { it.role == AiShipRole.MILITARY }, "$flag warships")
            for (ship in own) {
                assertTrue(harboursOf(flag).holds(ship.anchor), "${ship.displayName} anchored away from home")
            }
        }
        assertEquals(PIRATE_SHIP_COUNT, world.aiShips.count { it.role == AiShipRole.PIRATE })
        assertEquals(
            flags.size * (TRADE_SHIPS_PER_FACTION + MILITARY_SHIPS_PER_FACTION) + PIRATE_SHIP_COUNT,
            world.aiShips.size,
            "nobody else at sea"
        )
    }

    @Test
    fun `a new world's pirates start out on station and every ship starts on open water`() {
        GameRandom.withSeed(7L) { world.spawnAiShips() }

        for (ship in world.aiShips) {
            assertTrue(worldMap.isWater(ship.position.x, ship.position.y), "${ship.displayName} on land at ${ship.position}")
        }
        for (pirate in world.aiShips.filter { it.role == AiShipRole.PIRATE }) {
            assertNull(pirate.faction)
            assertEquals(PirateVoyage.CRUISING, pirate.voyage)
            val ground = assertNotNull(pirate.cruisingGround)
            assertTrue(worldMap.isWater(ground.x, ground.y), "cruising ground on land at $ground")
            assertTrue(
                pirate.cruiseSecondsLeft in CRUISE_MIN_SECONDS * 0.2f..CRUISE_MAX_SECONDS,
                "part of a cruise behind it, got ${pirate.cruiseSecondsLeft}"
            )
        }
    }

    /** Finding 29: the world is built before the first frame grants any courses, and start-up used
     *  to spend that empty allowance - nobody got a route, and every ship opened the game in harbour. */
    @Test
    fun `a new world opens with its ships already under way rather than in harbour`() {
        assertEquals(0, world.routeBudget, "as the game builds it: no frame drawn, no courses granted")

        GameRandom.withSeed(7L) { world.spawnAiShips() }

        for (ship in world.aiShips) {
            assertTrue(ship.path.isNotEmpty(), "${ship.displayName} starts with no route")
            assertTrue(
                ship.position.epsilonEquals(ship.path[ship.pathIndex], 0.01f),
                "${ship.displayName} is not on its own route"
            )
        }
        val flagShips = world.aiShips.filter { it.role != AiShipRole.PIRATE }
        val underWay = flagShips.count { it.position.dst(it.anchor) > AI_SHIP_ARRIVE_THRESHOLD * 2 }
        assertTrue(underWay * 2 > flagShips.size, "only $underWay of ${flagShips.size} flag ships have left harbour")
        assertEquals(0, world.routeBudget, "start-up leaves the first frame's allowance alone")
    }

    @Test
    fun `a trader is given a course over water to another harbour`() {
        val home = harboursOf(faction).first()
        for (seed in 1L..10L) {
            world.routeBudget = 10
            val ship = AiShipFactory.trade(faction, home.cpy()).also { it.anchor = home.cpy() }

            GameRandom.withSeed(seed) { world.retargetAiShip(ship) }

            val destination = assertNotNull(ship.destination, "seed $seed: no destination")
            assertTrue(ports.map { it.approachPoint }.holds(destination), "seed $seed: $destination is no harbour")
            assertTrue(destination.dst(home) > AI_SHIP_ARRIVE_THRESHOLD * 2, "seed $seed: going nowhere")
            assertTrue(ship.path.isNotEmpty(), "seed $seed: no route")
            assertAllWater(ship.path)
            assertEquals(0, ship.pathIndex)
            assertEquals(9, world.routeBudget, "one course laid, one paid for")
        }
    }

    @Test
    fun `a warship sails out to a harbour and then home to its anchor`() {
        world.routeBudget = 10
        val home = harboursOf(faction).first()
        val ship = AiShipFactory.military(faction, home.cpy()).also { it.anchor = home.cpy() }

        GameRandom.withSeed(5L) { world.retargetAiShip(ship) }
        val out = assertNotNull(ship.destination)
        assertTrue(out.dst(home) > AI_SHIP_ARRIVE_THRESHOLD * 2, "the outward leg leaves home")
        assertFalse(ship.outbound, "the next leg is the one home")
        assertAllWater(ship.path)

        ship.position.set(out)
        GameRandom.withSeed(6L) { world.retargetAiShip(ship) }

        assertTrue(assertNotNull(ship.destination).epsilonEquals(home, 0.01f), "homeward leg ends at the anchor")
        assertTrue(ship.outbound)
        assertTrue(ship.path.isNotEmpty())
        assertAllWater(ship.path)
    }

    @Test
    fun `a ship in a chase is routed after its quarry`() {
        world.routeBudget = 10
        val harbours = harboursOf(faction)
        val hunter = AiShipFactory.pirate(harbours[0].cpy())
        val quarry = AiShipFactory.trade(faction, harbours[1].cpy())
        hunter.quarry = quarry

        GameRandom.withSeed(11L) { world.retargetAiShip(hunter) }

        assertTrue(assertNotNull(hunter.destination).epsilonEquals(quarry.position, 0.01f))
        assertTrue(hunter.path.isNotEmpty())
        assertAllWater(hunter.path)
    }

    @Test
    fun `with the allowance for courses spent a ship keeps the course it has`() {
        world.routeBudget = 0
        val home = harboursOf(faction).first()
        val ship = AiShipFactory.trade(faction, home.cpy())
        val course = listOf(home.cpy(), harboursOf(faction)[1].cpy())
        ship.path = course

        GameRandom.withSeed(3L) { world.retargetAiShip(ship) }

        assertSame(course, ship.path, "not this frame - the present course stands")
        assertNull(ship.destination)
        assertEquals(0, ship.routeRelays)
        assertNull(world.routeTo(home, harboursOf(faction)[1]))
        assertEquals(0, world.routeBudget, "the allowance never goes below nothing")
    }

    @Test
    fun `a sunk trader or warship is replaced by one of the same flag out of its own harbours`() {
        for (role in listOf(AiShipRole.TRADE, AiShipRole.MILITARY)) {
            world.aiShips.clear()
            val far = ports.first { it.port.faction != faction }.approachPoint.cpy()
            val defeated = if (role == AiShipRole.TRADE) {
                AiShipFactory.trade(faction, far)
            } else {
                AiShipFactory.military(faction, far)
            }

            GameRandom.withSeed(9L) { world.respawnReplacement(defeated) }

            val replacement = world.aiShips.single()
            assertEquals(role, replacement.role)
            assertEquals(faction, replacement.faction)
            assertTrue(harboursOf(faction).holds(replacement.anchor), "$role replacement born away from home")
            assertTrue(replacement.position.epsilonEquals(replacement.anchor, 0.01f), "$role starts at its harbour")
        }
    }

    @Test
    fun `a sunk pirate is replaced by a pirate already out on station`() {
        val defeated = AiShipFactory.pirate(harboursOf(faction).first().cpy())

        GameRandom.withSeed(9L) { world.respawnReplacement(defeated) }

        val replacement = world.aiShips.single()
        assertEquals(AiShipRole.PIRATE, replacement.role)
        assertNull(replacement.faction)
        assertEquals(PirateVoyage.CRUISING, replacement.voyage)
        assertNotNull(replacement.cruisingGround)
        assertTrue(worldMap.isWater(replacement.position.x, replacement.position.y))
    }

    @Test
    fun `a pirate picks only fights it can win and a trader picks none`() {
        assertEquals(1, world.shipTier(ShipTypes.SLOOP))
        assertEquals(5, world.shipTier(ShipTypes.GALLEON))

        val pirate = AiShipFactory.pirate(harboursOf(faction).first().cpy())
        assertTrue(world.isHostile(pirate, pirate.tier))
        assertTrue(world.isHostile(pirate, pirate.tier - 1))
        assertFalse(world.isHostile(pirate, pirate.tier + 1))

        val trader = AiShipFactory.trade(faction, harboursOf(faction).first().cpy())
        assertFalse(world.isHostile(trader, 1))
    }

    @Test
    fun `a navy turns on the player as the player's standing with its flag sinks`() {
        val warship = AiShipFactory.military(faction, harboursOf(faction).first().cpy())
        val weaker = warship.tier
        val stronger = warship.tier + 1

        player.reputation[faction] = 0
        assertFalse(world.isHostile(warship, weaker), "neutral: leaves the player alone")

        player.reputation[faction] = -20
        assertTrue(world.isHostile(warship, weaker), "unfriendly: bullies what it outguns")
        assertFalse(world.isHostile(warship, stronger), "unfriendly: leaves a stronger ship alone")

        player.reputation[faction] = REPUTATION_HOSTILE_THRESHOLD
        assertTrue(world.isHostile(warship, stronger), "hostile: hunts whatever it meets")
    }
}
