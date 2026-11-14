package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.GdxNativesLoader
import com.portroyale.tiny.diagnostics.GameRandom
import com.portroyale.tiny.economy.Player
import org.junit.jupiter.api.BeforeAll
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pirate raids and sinking a ship, run through [GameWorld] on the real map - the third block moved
 * out of PortRoyaleGame (review batch 2, step 3).
 *
 * Ships do not sail on their own here: moving them is updateAiShips, which is still in the game
 * until step 4. So a test moves a hull by hand where the story needs it to have got somewhere, and
 * asserts what the raid does about it. Every test fixes the GameRandom seed, so the raid it gets is
 * always the same one.
 */
class GameWorldRaidsTest {

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

        private const val SEED = 21L
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

    private lateinit var events: RecordedEvents
    private lateinit var world: GameWorld

    @BeforeTest
    fun newWorld() {
        events = RecordedEvents()
        world = GameWorld(worldMap, ports, Player(), events)
    }

    /** A populated world sailed through its opening quiet spell, up to the moment a raid forms.
     *  A site that turns out to be inland is skipped and tried again next tick - hence the retries. */
    private fun startRaid(): PirateRaid {
        world.spawnAiShips()
        val quiet = world.secondsUntilNextRaid
        world.updateRaids(quiet * 0.5f)
        assertNull(world.activeRaid, "a raid while the sea should still be quiet")
        world.updateRaids(quiet * 0.5f)
        repeat(20) { if (world.activeRaid == null) world.updateRaids(0.1f) }
        return assertNotNull(world.activeRaid, "no raid once the quiet spell ran out")
    }

    /** Brings every member of the band to the rally point, which is what sets it out. */
    private fun setOut(raid: PirateRaid) {
        raid.band.forEach { it.position.set(raid.rallyPoint) }
        world.updateRaids(0.1f)
        assertEquals(RaidPhase.SAILING, raid.phase)
    }

    private fun Vector2.near(other: Vector2) = epsilonEquals(other, 0.01f)

    @Test
    fun `a raid forms once the quiet spell runs out and says nothing while it gathers`() {
        GameRandom.withSeed(SEED) {
            val raid = startRaid()

            assertEquals(RaidPhase.GATHERING, raid.phase)
            assertEquals(3, raid.band.size, "a raid calls up three pirates")
            for (pirate in raid.band) {
                assertEquals(AiShipRole.PIRATE, pirate.role)
                assertTrue(pirate.anchor.near(raid.rallyPoint), "${pirate.displayName} not sent to the rally point")
            }
            assertTrue(events.toasts.isEmpty(), "a raid still assembling has not happened yet")
        }
    }

    @Test
    fun `the band sets out only when every member has reached the rally point`() {
        GameRandom.withSeed(SEED) {
            val raid = startRaid()
            val straggler = raid.band.last()
            raid.band.forEach { it.position.set(raid.rallyPoint) }
            straggler.position.set(ports.maxBy { it.approachPoint.dst(raid.rallyPoint) }.approachPoint)

            world.updateRaids(0.1f)
            assertEquals(RaidPhase.GATHERING, raid.phase, "set out without the straggler")

            straggler.position.set(raid.rallyPoint)
            world.updateRaids(0.1f)
            assertEquals(RaidPhase.SAILING, raid.phase)
            for (pirate in raid.band) assertTrue(pirate.anchor.near(raid.centre), "${pirate.displayName} not bound for the corridor")
        }
    }

    @Test
    fun `followers sail in line astern along the water the leader has already crossed`() {
        GameRandom.withSeed(SEED) {
            val raid = startRaid()
            setOut(raid)
            val leader = assertNotNull(raid.leader(world.aiShips))
            val followers = raid.followers(world.aiShips)
            assertTrue(followers.isNotEmpty())

            world.routeBudget = 1_000
            world.updateRaids(0.1f)
            val route = leader.path
            assertTrue(route.isNotEmpty(), "the leader was given no course to the corridor")
            // The leader makes some way along its course, a little at a time as a hull does - the
            // route's waypoints can lie hundreds of units apart, and the wake is what it drops on the
            // way between them. The followers are left at the rally point, close enough to the line
            // that they should simply take their places on it.
            var next = 1
            while (leader.position.dst(raid.rallyPoint) < 150f && next <= route.lastIndex) {
                val toGo = Vector2(route[next]).sub(leader.position)
                if (toGo.len() <= 10f) {
                    leader.position.set(route[next])
                    next++
                } else {
                    leader.position.add(toGo.setLength(10f))
                }
                leader.pathIndex = next.coerceAtMost(route.lastIndex)
                world.updateRaids(0.1f)
            }
            assertEquals(RaidPhase.SAILING, raid.phase, "still on passage")

            val wake = leader.wake
            for (point in wake) assertTrue(worldMap.isWater(point.x, point.y), "wake on land at $point")
            var ahead = wake.lastIndex
            for (follower in followers) {
                assertEquals(wake, follower.path, "${follower.displayName} is not following the wake")
                assertTrue(follower.pathIndex < ahead, "${follower.displayName} is not astern of the ship ahead")
                ahead = follower.pathIndex
            }
        }
    }

    @Test
    fun `the raid is announced only when the band reaches the corridor`() {
        GameRandom.withSeed(SEED) {
            val raid = startRaid()
            setOut(raid)
            world.updateRaids(0.1f)
            assertTrue(events.toasts.isEmpty(), "announced while still on passage")

            assertNotNull(raid.leader(world.aiShips)).position.set(raid.centre)
            world.updateRaids(0.1f)

            assertEquals(RaidPhase.RAIDING, raid.phase)
            val (message, icon) = events.toasts.single()
            assertEquals(ToastIcon.Debt, icon)
            assertTrue(raid.label in message, "the toast names the corridor: $message")
        }
    }

    @Test
    fun `after the response delay the two ports' flags send their nearest warships and say so`() {
        GameRandom.withSeed(SEED) {
            val raid = startRaid()
            setOut(raid)
            assertNotNull(raid.leader(world.aiShips)).position.set(raid.centre)
            world.updateRaids(0.1f)
            events.toasts.clear()

            world.updateRaids(raid.responseDelay + 0.1f)

            assertTrue(raid.responseSent)
            val owners = ports.filter { it.port.name == raid.portA || it.port.name == raid.portB }
                .map { it.port.faction }.toSet()
            assertTrue(raid.squadrons.isNotEmpty(), "nobody answered")
            for ((flag, squadron) in raid.squadrons) {
                assertTrue(flag in owners, "$flag owns neither port")
                assertTrue(squadron.isNotEmpty())
                for (warship in squadron) {
                    assertEquals(AiShipRole.MILITARY, warship.role)
                    assertEquals(flag, warship.faction)
                    assertTrue(warship.anchor.near(raid.centre), "${warship.displayName} not sent to the trouble")
                }
            }
            assertEquals(raid.squadrons.size, events.toasts.count { it.second == ToastIcon.Property })
        }
    }

    @Test
    fun `when the raid's time is up the pirates go home and the all-clear is given`() {
        GameRandom.withSeed(SEED) {
            val raid = startRaid()
            // The raid clock only runs once the band is on station - by then it has been announced.
            raid.phase = RaidPhase.RAIDING
            raid.markAnnounced()
            raid.secondsRemaining = 0f

            world.updateRaids(0.1f)

            assertNull(world.activeRaid)
            assertTrue(raid.pirates(world.aiShips).isEmpty(), "pirates still held by a finished raid")
            for (pirate in raid.band) assertEquals(PirateVoyage.HOMEBOUND, pirate.voyage)
            val (message, icon) = events.toasts.single()
            assertEquals(ToastIcon.Property, icon)
            assertTrue("subsided" in message, message)
            assertTrue(world.secondsUntilNextRaid in 8 * 60f..15 * 60f, "next raid in ${world.secondsUntilNextRaid}s")
        }
    }

    @Test
    fun `a ship sunk inside a raid leaves a mark on the water, leaves the world and is replaced`() {
        GameRandom.withSeed(SEED) {
            val raid = startRaid()
            val victim = raid.band.first()
            victim.position.set(raid.centre)
            val hunter = world.aiShips.first { it !in raid.band }
            hunter.quarry = victim
            val fleetSize = world.aiShips.size

            world.sinkShip(victim, "a test")

            assertFalse(world.aiShips.any { it === victim }, "the wreck is still afloat")
            assertEquals(fleetSize, world.aiShips.size, "the loss was not replaced")
            assertEquals(AiShipRole.PIRATE, world.aiShips.last().role)
            val mark = raid.marks.single()
            assertTrue(mark.wasPirate)
            assertTrue(mark.position.near(raid.centre))
            assertFalse(raid.displacedAnchors.containsKey(victim), "the raid still counts the wreck")
            assertNull(hunter.quarry, "still chasing a ship that has sunk")
            assertTrue(events.toasts.isEmpty(), "a sinking is logged, never announced")
        }
    }

    @Test
    fun `a ship sunk with no raid running leaves no mark and is still replaced`() {
        GameRandom.withSeed(SEED) {
            world.spawnAiShips()
            val victim = world.aiShips.first { it.role == AiShipRole.TRADE }
            val fleetSize = world.aiShips.size

            world.sinkShip(victim, "a test")

            assertNull(world.activeRaid)
            assertFalse(world.aiShips.any { it === victim })
            assertEquals(fleetSize, world.aiShips.size)
            val replacement = world.aiShips.last()
            assertEquals(AiShipRole.TRADE, replacement.role)
            assertEquals(victim.faction, replacement.faction)
        }
    }

    /** REVIEW.md, finding 13: a band that is gone ends its raid in any phase - and a raid nobody was
     *  ever told about ends without an all-clear for it. */
    @Test
    fun `a raid whose whole band is lost before it is announced ends without a word`() {
        GameRandom.withSeed(SEED) {
            val raid = startRaid()
            for (pirate in raid.band.toList()) world.sinkShip(pirate, "a test")
            events.toasts.clear()

            world.updateRaids(0.1f)

            assertNull(world.activeRaid, "the raid outlived its whole band")
            assertTrue(events.toasts.isEmpty(), "an all-clear for a raid nobody was told about: ${events.toasts}")
        }
    }
}
