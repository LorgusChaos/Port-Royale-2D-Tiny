package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.utils.GdxNativesLoader
import com.portroyale.tiny.diagnostics.GameRandom
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.factions.Faction
import org.junit.jupiter.api.BeforeAll
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A new game and a load each build a new [GameWorld] (review batch 2, decision 10; REVIEW.md,
 * finding 12). The game's `newWorld()` is three lines and needs a window; what can go wrong is the
 * world itself carrying something over - state kept outside the object, or shared with the world
 * it replaces. So: play one world into a raid, with a castaway aboard and a hunt on, then build
 * the next exactly as `newWorld()` does and look at what it starts with.
 */
class GameWorldNewWorldTest {

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

        private const val SEED = 59L
        private const val TICK = 0.25f
        private val EXPECTED_FLEET = (TRADE_SHIPS_PER_FACTION + MILITARY_SHIPS_PER_FACTION) * 4 + PIRATE_SHIP_COUNT
    }

    private object Silent : WorldEvents {
        override fun toast(message: String, icon: ToastIcon) = Unit
        override fun offerEncounter(ship: AiShip) = Unit
        override fun arrivedAtPort(port: ResolvedPort) = Unit
        override fun startCombat(ship: AiShip) = Unit
    }

    /** What the game's `newWorld()` builds. */
    private fun newWorld(player: Player) = GameWorld(worldMap, ports, player, Silent).also { world ->
        world.spawnAiShips()
        world.seaEventSlots.forEach { world.spawnSeaEvent(it) }
    }

    @Test
    fun `the next game's world has no raid, a full fleet of its own, no passenger and no hunt`() {
        GameRandom.withSeed(SEED) {
            val player = Player()
            val old = newWorld(player)
            old.shipPosition.set(ports.first { it.port.name == "Port Royal" }.approachPoint)
            old.secondsUntilNextRaid = 0f
            old.routeBudget = 6
            old.updateRaids(TICK)
            assertNotNull(old.activeRaid, "the first world never raised a raid to carry over")
            old.passengerDestination = ports.last()
            old.huntedShip = old.aiShips.first()
            old.navalEscalation[Faction.SPAIN] = 2
            repeat(40) {
                old.routeBudget = 6
                old.updateAiShips(TICK)
                old.updateRaids(TICK)
                old.runWatchdog(TICK, TICK)
            }

            val next = newWorld(player)

            assertNull(next.activeRaid, "the raid sailed on into the next game")
            assertEquals(newWorld(Player()).secondsUntilNextRaid, next.secondsUntilNextRaid, "the raid clock is not a new game's")
            assertNull(next.passengerDestination, "the castaway from the last game is still aboard")
            assertNull(next.huntedShip, "the last game's hunt is still on")
            assertNull(next.currentAiTarget)
            assertTrue(next.navalEscalation.isEmpty(), "the navies remember the last game's raid")
            assertTrue(next.fleetPaths.isEmpty())
            assertEquals(EXPECTED_FLEET, next.aiShips.size, "the new world's fleet is not a full one")
            assertTrue(next.aiShips.none { it in old.aiShips }, "a ship from the last game sails in the new one")
            assertTrue(next.aiShips.none { it.quarry != null || it.isPursuing }, "a ship starts the new game mid-chase")
            assertTrue(next.seaEventSlots.all { it.point != null }, "the new world's sea is not stocked")
        }
    }
}
