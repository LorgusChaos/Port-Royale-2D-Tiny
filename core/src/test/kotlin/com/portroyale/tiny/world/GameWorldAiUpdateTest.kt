package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.GdxNativesLoader
import com.portroyale.tiny.combat.CombatOutcome
import com.portroyale.tiny.diagnostics.GameRandom
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.factions.Faction
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
 * The AI ships under way, the watchdog, and meeting the player, run through [GameWorld] on the real
 * map - the fourth block moved out of PortRoyaleGame (review batch 2, step 4).
 *
 * Every test fixes the GameRandom seed, and the fights that matter are rigged so only one outcome
 * can happen (a hull with no guns cannot win - see docs/testing.md), so the assertions are about
 * the rules rather than about a lucky roll.
 */
class GameWorldAiUpdateTest {

    companion object {
        private lateinit var worldMap: WorldMap
        private lateinit var ports: List<ResolvedPort>
        /** Open water as far from every harbour as the map allows, with clear sea to the east. */
        private lateinit var openSea: Vector2

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

        private const val SEED = 31L
        private const val TICK = 0.25f
        private const val ROUTES_PER_TICK = 6
        private val EXPECTED_FLEET = (TRADE_SHIPS_PER_FACTION + MILITARY_SHIPS_PER_FACTION) * 4 + PIRATE_SHIP_COUNT
    }

    private class RecordedEvents : WorldEvents {
        val toasts = mutableListOf<Pair<String, ToastIcon>>()
        val offered = mutableListOf<AiShip>()
        val fights = mutableListOf<AiShip>()
        override fun toast(message: String, icon: ToastIcon) {
            toasts += message to icon
        }
        override fun offerEncounter(ship: AiShip) {
            offered += ship
        }
        override fun arrivedAtPort(port: ResolvedPort) = Unit
        override fun startCombat(ship: AiShip) {
            fights += ship
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

    /** One frame of sailing as the game runs it: a fresh allowance of courses, then the ships. */
    private fun sail(seconds: Float, tick: Float = TICK, stopWhen: () -> Boolean = { false }) {
        var t = 0f
        while (t < seconds && !stopWhen()) {
            world.routeBudget = ROUTES_PER_TICK
            world.updateAiShips(tick)
            t += tick
        }
    }

    private fun pirate(at: Vector2, tier: Int = 3, speed: Float = 12f, hull: Int = 400) = AiShip(
        role = AiShipRole.PIRATE, faction = null, spritePath = "p.png", tint = Color.WHITE,
        speed = speed, maxHull = hull, minDamage = 20, maxDamage = 30, goldReward = 150, tier = tier,
        position = at.cpy()
    ).also { it.anchor = at.cpy() }

    /** A merchantman with no guns at all: it can never win a fight, which pins every fight's outcome. */
    private fun unarmedTrader(at: Vector2, speed: Float = 10f) = AiShip(
        role = AiShipRole.TRADE, faction = Faction.SPAIN, spritePath = "t.png", tint = Color.WHITE,
        speed = speed, maxHull = 30, minDamage = 0, maxDamage = 0, goldReward = 100, tier = 1,
        position = at.cpy()
    ).also { it.anchor = at.cpy() }

    // --- under way ---------------------------------------------------------------------------

    @Test
    fun `a populated world sails three minutes with no hull aground, lost or standing still`() {
        GameRandom.withSeed(SEED) {
            world.shipPosition.set(ports.first().worldPos)
            world.spawnAiShips()
            val opening = world.aiShips.toList()
            val sailed = opening.associateWith { 0f }.toMutableMap()
            val lastSeen = opening.associateWith { it.position.cpy() }

            var t = 0f
            while (t < 180f) {
                world.routeBudget = ROUTES_PER_TICK
                world.updateAiShips(TICK)
                world.runWatchdog(TICK, TICK)
                for (ship in world.aiShips) {
                    val p = ship.position
                    assertTrue(p.x.isFinite() && p.y.isFinite(), "${ship.displayName} at a non-finite position at ${t}s")
                    assertTrue(worldMap.isWater(p.x, p.y), "${ship.displayName} aground at $p at ${t}s")
                    val before = lastSeen[ship] ?: continue
                    sailed[ship] = sailed.getValue(ship) + before.dst(p)
                    before.set(p)
                }
                t += TICK
            }

            assertEquals(EXPECTED_FLEET, world.aiShips.size, "every loss is replaced")
            // Distance sailed, not distance from the start: a trader that has been out and back is
            // where it began, and has done exactly what it should.
            for (ship in opening.filter { it in world.aiShips }) {
                assertTrue(sailed.getValue(ship) > 500f, "${ship.displayName}#${ship.id} sailed only ${sailed.getValue(ship)} in three minutes")
            }
        }
    }

    /** A pirate of hull class [type], as the factory sails them, out on station at [at]. */
    private fun hunterOfClass(type: com.portroyale.tiny.ships.ShipType, at: Vector2) = AiShip(
        role = AiShipRole.PIRATE, faction = null, spritePath = type.spritePath, tint = Color.WHITE,
        speed = type.speed, maxHull = type.maxHull, minDamage = type.minDamage, maxDamage = type.maxDamage,
        goldReward = 150, tier = 1, position = at.cpy(), maneuverability = type.baseManeuverability
    ).also {
        it.anchor = at.cpy()
        it.voyage = PirateVoyage.CRUISING
        it.cruisingGround = at.cpy()
        it.cruiseSecondsLeft = 600f
    }

    /** Sails [hunter] after [prey] for up to [seconds]; reports whether it ever gave chase. */
    private fun chase(hunter: AiShip, prey: AiShip, seconds: Float, tick: Float = 0.1f): Boolean {
        world.aiShips += hunter
        world.aiShips += prey
        var chased = false
        sail(seconds, tick) {
            if (hunter.quarry === prey) chased = true
            prey !in world.aiShips
        }
        return chased
    }

    @Test
    fun `a pirate on station runs down a merchantman it sees and takes it`() {
        GameRandom.withSeed(SEED) {
            world.shipPosition.set(ports.first().worldPos)
            val hunter = hunterOfClass(ShipTypes.SLOOP, openSea)
            val prey = unarmedTrader(openSea.cpy().add(150f, 20f), speed = 4f)

            val chased = chase(hunter, prey, 120f)

            assertTrue(chased, "the pirate never went after the merchantman")
            assertFalse(prey in world.aiShips, "the merchantman got away")
            assertTrue(hunter in world.aiShips, "an unarmed merchantman sank the pirate")
            assertNull(hunter.quarry, "the chase is over once the prize is taken")
            assertTrue(hunter.attackCooldown > 0f, "straight from one fight into the next")
            assertTrue(
                world.aiShips.any { it !== prey && it.role == AiShipRole.TRADE && it.faction == Faction.SPAIN },
                "the lost merchantman is replaced"
            )
        }
    }

    /** A Galleon turns wider than contact range: it used to fetch up a hull's length short of its
     *  prize and lie there until the chase was given up (REVIEW.md, finding 30). */
    @Test
    fun `a galleon runs alongside a merchantman lying still and takes it`() {
        GameRandom.withSeed(SEED) {
            world.shipPosition.set(ports.first().worldPos)
            val hunter = hunterOfClass(ShipTypes.GALLEON, openSea)
            val prey = unarmedTrader(openSea.cpy().add(150f, 20f), speed = 0f)

            val chased = chase(hunter, prey, 30f)

            assertTrue(chased, "the galleon never went after the merchantman")
            assertFalse(prey in world.aiShips, "the galleon hung off the merchantman and never took it")
        }
    }

    /** A Brigantine's turning circle is exactly contact range: against a target under way it used
     *  to stop short as the Galleon does - and every warship is a Brigantine (finding 30). */
    @Test
    fun `a brigantine runs down a merchantman under way and takes it`() {
        GameRandom.withSeed(SEED) {
            world.shipPosition.set(ports.first().worldPos)
            val hunter = hunterOfClass(ShipTypes.BRIGANTINE, openSea)
            val prey = unarmedTrader(openSea.cpy().add(150f, 20f), speed = 8f)

            val chased = chase(hunter, prey, 120f)

            assertTrue(chased, "the brigantine never went after the merchantman")
            assertFalse(prey in world.aiShips, "the brigantine kept station off the merchantman and never took it")
        }
    }

    // --- meeting the player ------------------------------------------------------------------

    private fun alongsidePlayer(ship: AiShip): AiShip {
        world.shipPosition.set(openSea)
        ship.position.set(openSea).add(Encounters.CONTACT_RADIUS * 0.5f, 0f)
        world.aiShips += ship
        return ship
    }

    @Test
    fun `a hostile ship met by chance offers the player a choice and starts nothing`() {
        val ship = alongsidePlayer(pirate(openSea))

        world.checkAiEncounters()

        assertEquals(listOf(ship), events.offered)
        assertTrue(events.fights.isEmpty())
        assertNull(world.currentAiTarget, "no fight until the player chooses one")
    }

    @Test
    fun `a ship that hunted the player down attacks outright`() {
        val ship = alongsidePlayer(pirate(openSea)).also { it.isPursuing = true }

        world.checkAiEncounters()

        assertEquals(listOf(ship), events.fights)
        assertTrue(events.offered.isEmpty(), "no choice when caught")
        assertSame(ship, world.currentAiTarget)
        assertFalse(ship.isPursuing, "the chase is over once the fight begins")
    }

    @Test
    fun `the player's own hunt ends in a fight even with a ship that would never attack`() {
        val trader = alongsidePlayer(unarmedTrader(openSea))
        world.huntedShip = trader

        world.checkAiEncounters()

        assertEquals(listOf(trader), events.fights)
        assertSame(trader, world.currentAiTarget)
        assertNull(world.huntedShip, "the hunt is over once the fight begins")
    }

    @Test
    fun `nothing engages the player in harbour or in the ship's boat`() {
        world.aiShips += pirate(openSea).also { it.isPursuing = true }
        world.shipPosition.set(ports.first().worldPos)
        world.aiShips.single().position.set(ports.first().worldPos).add(Encounters.CONTACT_RADIUS * 0.5f, 0f)
        world.checkAiEncounters()
        assertTrue(events.fights.isEmpty() && events.offered.isEmpty(), "attacked in harbour")

        alongsidePlayer(world.aiShips.removeAt(0))
        player.castAdrift()
        world.checkAiEncounters()
        assertTrue(events.fights.isEmpty() && events.offered.isEmpty(), "the ship's boat was attacked")
    }

    @Test
    fun `sailing on sends the ship away for a while, and sooner back the more it outguns the player`() {
        val even = alongsidePlayer(pirate(openSea, tier = 1))
        world.checkAiEncounters()
        assertEquals(listOf(even), events.offered)

        world.declineEncounter(even)
        assertTrue(even.giveUpCooldown > 0f, "declining cost the ship nothing")
        world.checkAiEncounters()
        assertEquals(1, events.offered.size, "the same ship asked again straight away")

        val lopsided = pirate(openSea, tier = 3)
        world.declineEncounter(lopsided)
        assertTrue(lopsided.giveUpCooldown < even.giveUpCooldown, "a pirate that badly outguns the player should come back sooner")
    }

    @Test
    fun `a won fight takes the enemy out of the world once, pays nothing twice, and a new ship sails`() {
        GameRandom.withSeed(SEED) {
            val trader = alongsidePlayer(unarmedTrader(openSea))
            world.huntedShip = trader
            world.checkAiEncounters()
            val gold = player.gold
            val standing = player.reputation[Faction.SPAIN] ?: 0
            val fleet = world.aiShips.size

            world.finishCombat(CombatOutcome.VICTORY)

            assertFalse(trader in world.aiShips)
            assertEquals(fleet, world.aiShips.size, "the sunk ship is replaced")
            assertEquals(gold, player.gold, "the prize was paid by the battle itself")
            assertTrue((player.reputation[Faction.SPAIN] ?: 0) < standing, "sinking a Spanish ship cost nothing with Spain")
            assertEquals(ToastIcon.Coin, events.toasts.single().second)
            assertTrue(events.toasts.single().first.startsWith("Victory!"))
            assertNull(world.currentAiTarget)
        }
    }

    @Test
    fun `a lost fight leaves the player adrift and the victor lets them go`() {
        val ship = alongsidePlayer(pirate(openSea)).also { it.isPursuing = true }
        world.checkAiEncounters()
        ship.isPursuing = true // as if it had picked the scent up again during the battle

        world.finishCombat(CombatOutcome.DEFEAT)

        assertEquals(ShipTypes.LONGBOAT, player.ship)
        assertEquals(0, player.gold)
        assertFalse(ship.isPursuing, "the victor is still hunting the boat it just emptied")
        assertTrue(ship.giveUpCooldown > 0f)
        assertTrue(ship in world.aiShips, "the victor sails on")
        assertEquals(ToastIcon.Castaway, events.toasts.single().second)
        assertNull(world.currentAiTarget)
    }

    @Test
    fun `a ship the player outran comes back only after its cooldown`() {
        val ship = alongsidePlayer(pirate(openSea, tier = 1)).also { it.isPursuing = true }
        world.checkAiEncounters()

        world.finishCombat(CombatOutcome.FLED)

        assertTrue(ship.giveUpCooldown > 0f)
        assertTrue(ship in world.aiShips)
        assertTrue(events.toasts.isEmpty(), "getting away is not news")
        assertNull(world.currentAiTarget)
    }

    // --- a ship leaves the world once, by one door (REVIEW.md, finding 7) ---

    @Test
    fun `a ship sunk twice is replaced once`() {
        GameRandom.withSeed(SEED) {
            world.shipPosition.set(ports.first().worldPos)
            world.spawnAiShips()
            val victim = world.aiShips.first()

            world.sinkShip(victim, "a test")
            world.sinkShip(victim, "a test")

            assertEquals(EXPECTED_FLEET, world.aiShips.size, "the second sinking brought in a second replacement")
        }
    }

    @Test
    fun `a raider the player sinks leaves the raid and is replaced once`() {
        GameRandom.withSeed(SEED) {
            world.shipPosition.set(ports.first().worldPos)
            world.spawnAiShips()
            world.secondsUntilNextRaid = 0f
            world.routeBudget = ROUTES_PER_TICK
            world.updateRaids(TICK)
            val raid = assertNotNull(world.activeRaid, "no raid formed")
            val raider = raid.band.first()

            world.startAiCombat(raider)
            world.finishCombat(CombatOutcome.VICTORY)

            assertFalse(raid.displacedAnchors.containsKey(raider), "the raid still holds the ship the player sank")
            assertFalse(raider in world.aiShips)
            assertEquals(EXPECTED_FLEET, world.aiShips.size, "the fleet is not back to strength exactly")
        }
    }

    @Test
    fun `choosing to fight a ship that has meanwhile sunk starts no fight`() {
        GameRandom.withSeed(SEED) {
            world.shipPosition.set(openSea)
            val ship = pirate(openSea.cpy().add(10f, 0f))
            world.aiShips += ship
            world.sinkShip(ship, "a captain")

            world.startAiCombat(ship)

            assertTrue(events.fights.isEmpty(), "a fight began with a wreck")
            assertNull(world.currentAiTarget)
        }
    }
}
