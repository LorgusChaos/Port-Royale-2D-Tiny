package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.utils.GdxNativesLoader
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Laying a course has to be cheap, because the world now asks for one constantly.
 *
 * It did not used to. While a ship wanted a route once every half-minute, an A* that allocated a
 * position object, a node and several hash entries for every cell it touched was merely wasteful.
 * Then chases, flights and formation-keeping all began re-laying courses every second or two and
 * the same code turned the whole game into a slideshow - every ship on the map moving as though
 * it were drawn at three frames a second. This is the guard against that coming back.
 */
class PathfindingCostTest {

    private companion object {
        /** Two thirds of MAX_EXPANSIONS, where a search gives up: a cross-map voyage that comes
         *  near the cap risks being refused outright, and one that needs more has got worse. */
        const val CROSS_MAP_BUDGET = 40_000
    }

    private lateinit var worldMap: WorldMap

    @BeforeTest
    fun loadRealMap() {
        GdxNativesLoader.load()
        val bytes = File("../assets/maps/caribbean_mask.png").readBytes()
        val pixmap = Pixmap(bytes, 0, bytes.size)
        worldMap = WorldMap(pixmap, MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT, 10f)
        pixmap.dispose()
    }

    private fun approach(name: String) = Ports.ALL.first { it.name == name }
        .let { MapProjection.project(it.lat, it.lon) }
        .let { assertNotNull(worldMap.findNearestWater(it.x, it.y)) }

    // The work a search does, not the time it takes: a count of opened cells is the same on every
    // machine, while a stopwatch fails on a busy CI runner with no change in the code (REVIEW.md,
    // finding 26). The stopwatch versions live on in PathfindingTimingTest, outside :core:test.

    @Test
    fun `a cross-map route opens only part of the sea`() {
        // The longest the map allows, corner to corner. Each must stay well clear of the point
        // where the search gives up (MAX_EXPANSIONS), or a long voyage would be refused outright.
        val far = listOf(
            approach("Havana") to approach("Bridgetown"),
            approach("Veracruz") to approach("San Juan"),
            approach("Campeche") to approach("Cartagena")
        )
        for ((from, to) in far) {
            assertNotNull(worldMap.findPath(from.cpy(), to.cpy()), "no route $from -> $to")
            val opened = worldMap.lastSearchExpanded
            assertTrue(opened < CROSS_MAP_BUDGET, "$from -> $to opened $opened cells")
        }
    }

    @Test
    fun `an unreachable goal gives up promptly instead of surveying the whole sea`() {
        // Inland: no route can exist. The old search would explore every reachable cell first.
        val inland = MapProjection.project(19.0f, -71.0f) // the middle of Hispaniola
        val route = worldMap.findPath(approach("Havana").cpy(), inland.cpy())
        assertTrue(route == null, "a route was found to dry land")
        assertEquals(0, worldMap.lastSearchExpanded, "searched the sea for a goal on dry land")
    }

    @Test
    fun `routes still start where the ship is and end where it was sent`() {
        val from = approach("Havana")
        val to = approach("Santiago de Cuba")
        val route = assertNotNull(worldMap.findPath(from.cpy(), to.cpy()))
        assertTrue(route.isNotEmpty())
        assertEquals(to, route.last(), "a route must finish exactly where it was aimed")
        assertTrue(route.first().dst(from) < 20f, "a route must begin at the ship, not near it")
    }

    @Test
    fun `every step of a route is water a hull can actually cross`() {
        val route = assertNotNull(worldMap.findPath(approach("Havana"), approach("Bridgetown")))
        for (point in route) {
            assertTrue(worldMap.isWater(point.x, point.y), "route crosses land at $point")
        }
    }

    @Test
    fun `repeated searches do not contaminate one another`() {
        // The working arrays are reused between calls and marked with a search number rather than
        // cleared. Get that wrong and the second route inherits the first one's costs.
        val a = approach("Havana")
        val b = approach("Bridgetown")
        val first = assertNotNull(worldMap.findPath(a.cpy(), b.cpy()))
        repeat(5) { worldMap.findPath(approach("Veracruz"), approach("San Juan")) }
        val again = assertNotNull(worldMap.findPath(a.cpy(), b.cpy()))
        assertEquals(first.size, again.size, "the same journey came out a different length")
    }
}
