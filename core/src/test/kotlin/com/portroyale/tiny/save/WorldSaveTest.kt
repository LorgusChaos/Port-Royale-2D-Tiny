package com.portroyale.tiny.save

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.utils.GdxNativesLoader
import com.portroyale.tiny.diagnostics.GameRandom
import com.portroyale.tiny.economy.Captain
import com.portroyale.tiny.economy.FleetSimulation
import com.portroyale.tiny.economy.MarketState
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.ships.ShipTypes
import com.portroyale.tiny.world.AiShip
import com.portroyale.tiny.world.FleetPathEntry
import com.portroyale.tiny.world.GameClock
import com.portroyale.tiny.world.GameWorld
import com.portroyale.tiny.world.MapProjection
import com.portroyale.tiny.world.ResolvedPort
import com.portroyale.tiny.world.ToastIcon
import com.portroyale.tiny.world.WorldEvents
import com.portroyale.tiny.world.WorldMap
import com.portroyale.tiny.world.resolvePorts
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
 * The full save (Project design/decisions/full-save.md): a world saved mid-game - ships under
 * way and in a chase, a raid on its clock, sea events in the water, a captain on a detour, the
 * player on a course - goes through the same JSON the game writes and comes back as the same
 * world, which then carries on exactly as the saved one would have.
 */
class WorldSaveTest {

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

        private const val SEED = 61L
        private const val TICK = 0.25f
    }

    private object Silent : WorldEvents {
        override fun toast(message: String, icon: ToastIcon) = Unit
        override fun offerEncounter(ship: AiShip) = Unit
        override fun startCombat(ship: AiShip) = Unit
        override fun arrivedAtPort(port: ResolvedPort) = Unit
    }

    private lateinit var player: Player
    private lateinit var captain: Captain

    @BeforeTest
    fun setUp() {
        GameClock.reset()
        MarketState.reset()
        player = Player(100_000)
    }

    @AfterEach
    fun tearDown() {
        GameClock.reset()
    }

    private fun port(name: String) = ports.first { it.port.name == name }

    private fun step(world: GameWorld, ticks: Int) = repeat(ticks) {
        world.routeBudget = 6
        world.updateAiShips(TICK)
        world.updateRaids(TICK)
    }

    /** A world well into a game, with every kind of state a save has to carry. */
    private fun busyWorld(): GameWorld {
        val world = GameWorld(worldMap, ports, player, Silent)
        world.shipPosition.set(port("Port Royal").approachPoint)
        world.spawnAiShips()
        world.seaEventSlots.forEach { world.spawnSeaEvent(it) }

        val sloopId = player.activeShipId
        player.buyShip(ShipTypes.CUTTER, "Runner", "Havana")
        captain = Captain(name = "Test Captain", tradeSense = 0f, caution = 0.5f)
        assertTrue(FleetSimulation.hire(player, captain, sloopId, "Havana", "Portobelo", GameClock.totalGameMinutes, world::fleetSeaDistance))

        step(world, 120)
        world.secondsUntilNextRaid = 0f
        step(world, 1)
        assertNotNull(world.activeRaid, "no raid to save")
        step(world, 40)

        // Every reference kind, set outright so the test never depends on one happening.
        val s = world.aiShips
        s[0].quarry = s[1]
        s[2].fleeingFrom = s[3]
        s[4].isPursuing = true
        s[4].pursuingCaptain = captain
        s[6].avoidedThreat = s[7]
        s[8].currentHull = s[8].maxHull / 2
        world.navalEscalation[Faction.SPAIN] = 2
        assertTrue(world.setCourse(port("Havana").approachPoint, port("Havana")))
        world.huntedShip = s[9]
        world.fleetShipTransform(captain)
        val drawn = world.fleetPaths.getValue(captain)
        world.fleetPaths[captain] = FleetPathEntry(drawn.origin, drawn.dest, drawn.path, s[10])
        return world
    }

    /** Saved through the very JSON the game writes, and loaded into a new world. */
    private fun roundTrip(world: GameWorld): GameWorld {
        val saved = GameSaveData().also { it.world = WorldSave.capture(world, player) }
        val data = SaveManager.fromJson(SaveManager.toJson(saved))
        val back = GameWorld(worldMap, ports, player, Silent)
        back.shipPosition.set(world.shipPosition)
        assertTrue(WorldSave.restore(assertNotNull(data.world, "the world was not in the save"), back, player))
        return back
    }

    /** Everything about a ship that decides what it does next. */
    private fun fingerprint(ship: AiShip): Map<String, Any?> = linkedMapOf(
        "id" to ship.id, "role" to ship.role, "faction" to ship.faction, "sprite" to ship.spritePath,
        "tint" to Color.rgba8888(ship.tint), "speed" to ship.speed, "maxHull" to ship.maxHull,
        "minDamage" to ship.minDamage, "maxDamage" to ship.maxDamage, "goldReward" to ship.goldReward,
        "tier" to ship.tier, "maneuverability" to ship.maneuverability, "position" to ship.position,
        "headingDeg" to ship.headingDeg, "path" to ship.path, "pathIndex" to ship.pathIndex,
        "trackedWaypoint" to ship.trackedWaypoint, "closestApproach" to ship.closestApproach,
        "stalledFor" to ship.stalledFor, "currentHull" to ship.currentHull, "repairProgress" to ship.repairProgress,
        "quarry" to ship.quarry?.id, "chaseSeconds" to ship.chaseSeconds, "chaseBestRange" to ship.chaseBestRange,
        "chaseStalledFor" to ship.chaseStalledFor, "repathCooldown" to ship.repathCooldown,
        "retargetCooldown" to ship.retargetCooldown, "fleeingFrom" to ship.fleeingFrom?.id,
        "fleeCommitment" to ship.fleeCommitment, "voyage" to ship.voyage, "cruisingGround" to ship.cruisingGround,
        "cruiseSecondsLeft" to ship.cruiseSecondsLeft, "huntCheckCooldown" to ship.huntCheckCooldown,
        "wake" to ship.wake.toList(), "groundCovered" to ship.groundCovered, "odometerMark" to ship.odometerMark,
        "destination" to ship.destination, "routeRelays" to ship.routeRelays, "anchor" to ship.anchor,
        "outbound" to ship.outbound, "attackCooldown" to ship.attackCooldown, "avoidedThreat" to ship.avoidedThreat?.id,
        "threatCheckCooldown" to ship.threatCheckCooldown, "giveUpCooldown" to ship.giveUpCooldown,
        "isPursuing" to ship.isPursuing, "pursuingCaptain" to ship.pursuingCaptain?.name,
        "pursuitBlockedFor" to ship.pursuitBlockedFor
    )

    /** The fields where two fleets differ, ship by ship - readable where a whole-list diff is not. */
    private fun differences(a: List<AiShip>, b: List<AiShip>): List<String> {
        if (a.size != b.size) return listOf("fleet of ${a.size} against ${b.size}")
        return a.zip(b).flatMap { (x, y) ->
            val fx = fingerprint(x)
            val fy = fingerprint(y)
            fx.keys.filter { fx[it] != fy[it] }.map { "ship #${x.id}/#${y.id} $it: ${fx[it]} against ${fy[it]}" }
        }
    }

    @Test
    fun `every AI ship comes back where it was, doing what it was doing`() {
        GameRandom.withSeed(SEED) {
            val world = busyWorld()

            val back = roundTrip(world)

            val diff = differences(world.aiShips, back.aiShips)
            assertTrue(diff.isEmpty(), diff.take(12).joinToString("\n"))
            assertSame(back.aiShips[1], back.aiShips[0].quarry, "the chase points at a copy, not at the ship in the world")
            assertSame(captain, back.aiShips[4].pursuingCaptain)
        }
    }

    @Test
    fun `a raid comes back at the same stage, on the same clock, with the same ships`() {
        GameRandom.withSeed(SEED) {
            val world = busyWorld()
            val raid = world.activeRaid!!

            val back = roundTrip(world)
            val again = assertNotNull(back.activeRaid, "the raid was lost")

            assertEquals(raid.label, again.label)
            assertEquals(raid.phase, again.phase)
            assertEquals(raid.secondsRemaining, again.secondsRemaining)
            assertEquals(raid.responseDelay, again.responseDelay)
            assertEquals(raid.gatherSecondsLeft, again.gatherSecondsLeft)
            assertEquals(raid.announced, again.announced)
            assertEquals(raid.responseSent, again.responseSent)
            assertEquals(raid.centre, again.centre)
            assertEquals(raid.rallyPoint, again.rallyPoint)
            assertEquals(raid.band.map { it.id }, again.band.map { it.id })
            assertEquals(raid.displacedAnchors.mapKeys { it.key.id }, again.displacedAnchors.mapKeys { it.key.id })
            assertTrue(again.band.all { member -> back.aiShips.any { it === member } }, "the band is made of copies")
            assertEquals(world.secondsUntilNextRaid, back.secondsUntilNextRaid)
            assertEquals(mapOf(Faction.SPAIN to 2), back.navalEscalation.toMap())
        }
    }

    @Test
    fun `sea events come back where they lay, with the same clocks`() {
        GameRandom.withSeed(SEED) {
            val world = busyWorld()

            val back = roundTrip(world)

            assertEquals(
                world.seaEventSlots.map { Triple(it.point?.position, it.point?.type, it.respawnTimer) },
                back.seaEventSlots.map { Triple(it.point?.position, it.point?.type, it.respawnTimer) }
            )
        }
    }

    @Test
    fun `the player's course and hunt and the captain's detour come back`() {
        GameRandom.withSeed(SEED) {
            val world = busyWorld()

            val back = roundTrip(world)

            assertEquals(world.navMode, back.navMode)
            assertEquals(world.path, back.path)
            assertEquals(world.pathIndex, back.pathIndex)
            assertSame(port("Havana"), back.destinationPort)
            assertEquals(world.shipHeadingDeg, back.shipHeadingDeg)
            assertEquals(world.huntedShip?.id, back.huntedShip?.id)
            val detour = assertNotNull(back.fleetPaths[captain], "the captain's course was lost")
            assertEquals(world.fleetPaths.getValue(captain).path, detour.path)
            assertEquals(world.aiShips[10].id, detour.avoidedThreat?.id)
            assertTrue(AiShip.nextId > back.aiShips.maxOf { it.id }, "a new ship could take the id of one already at sea")
        }
    }

    @Test
    fun `the loaded world carries on exactly as the saved one would have`() {
        GameRandom.withSeed(SEED) {
            val world = busyWorld()
            val back = roundTrip(world)
            val idsFrom = AiShip.nextId

            GameRandom.withSeed(SEED + 1) { step(world, 40) }
            AiShip.nextId = idsFrom
            GameRandom.withSeed(SEED + 1) { step(back, 40) }

            val diff = differences(world.aiShips, back.aiShips)
            assertTrue(diff.isEmpty(), diff.take(12).joinToString("\n"))
            assertEquals(world.activeRaid?.phase, back.activeRaid?.phase)
            assertEquals(world.activeRaid?.secondsRemaining, back.activeRaid?.secondsRemaining)
        }
    }

    @Test
    fun `a save without a world, or with one this build cannot read, restores nothing`() {
        GameRandom.withSeed(SEED) {
            val old = SaveManager.fromJson(SaveManager.toJson(SaveManager.snapshot("T", player, com.badlogic.gdx.math.Vector2(), null)))
            assertNull(old.world, "a player-only save grew a world")

            val world = busyWorld()
            val future = WorldSave.capture(world, player).also { it.version = WORLD_SAVE_VERSION + 1 }
            val target = GameWorld(worldMap, ports, player, Silent)
            target.spawnAiShips()
            val before = target.aiShips.toList()

            assertFalse(WorldSave.restore(future, target, player))
            assertEquals(before, target.aiShips, "an unreadable world was half-loaded")
        }
    }

    @Test
    fun `references to ships, captains and ports that are gone come back empty`() {
        GameRandom.withSeed(SEED) {
            val world = busyWorld()
            val data = SaveManager.fromJson(SaveManager.toJson(GameSaveData().also { it.world = WorldSave.capture(world, player) })).world!!
            val quarryId = world.aiShips[1].id
            data.ships.removeAll { it.id == quarryId }
            data.ships.first { it.id == world.aiShips[4].id }.pursuingCaptain = 99
            data.raid.band = data.raid.band + quarryId
            data.destinationPort = "Atlantis"

            val back = GameWorld(worldMap, ports, player, Silent)
            assertTrue(WorldSave.restore(data, back, player))

            val chaser = back.aiShips.first { it.id == world.aiShips[0].id }
            assertNull(chaser.quarry, "a chase after a ship that is not there")
            val pursuer = back.aiShips.first { it.id == world.aiShips[4].id }
            assertNull(pursuer.pursuingCaptain)
            assertFalse(pursuer.isPursuing, "pursuing a captain who is not in the roster")
            assertTrue(back.activeRaid!!.band.none { it.id == quarryId })
            assertNull(back.destinationPort)
        }
    }
}
