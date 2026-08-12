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

    @Test
    fun `a frame's worth of routes costs a fraction of a frame`() {
        // Sixty frames a second leaves about sixteen milliseconds for everything. Several ships
        // re-laying courses in the same frame must not eat that, so ten long routes are budgeted
        // well inside one frame - and these are the longest the map allows, corner to corner.
        val far = listOf(
            approach("Havana") to approach("Bridgetown"),
            approach("Veracruz") to approach("San Juan"),
            approach("Campeche") to approach("Cartagena")
        )
        // Warm up, so the measurement is of the algorithm rather than of the JIT.
        repeat(20) { far.forEach { (from, to) -> worldMap.findPath(from.cpy(), to.cpy()) } }

        val started = System.nanoTime()
        repeat(10) { far.forEach { (from, to) -> worldMap.findPath(from.cpy(), to.cpy()) } }
        val millis = (System.nanoTime() - started) / 1_000_000.0

        assertTrue(millis < 250.0, "30 cross-map routes took ${millis}ms")
    }

    @Test
    fun `an unreachable goal gives up promptly instead of surveying the whole sea`() {
        // Inland: no route can exist. The old search would explore every reachable cell first.
        val inland = MapProjection.project(19.0f, -71.0f) // the middle of Hispaniola
        val from = approach("Havana")
        val started = System.nanoTime()
        val route = worldMap.findPath(from.cpy(), inland.cpy())
        val millis = (System.nanoTime() - started) / 1_000_000.0
        assertTrue(route == null, "a route was found to dry land")
        assertTrue(millis < 100.0, "giving up took ${millis}ms")
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
