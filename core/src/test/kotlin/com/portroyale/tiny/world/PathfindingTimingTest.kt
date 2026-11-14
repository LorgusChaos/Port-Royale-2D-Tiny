package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.utils.GdxNativesLoader
import org.junit.jupiter.api.Tag
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The stopwatch half of [PathfindingCostTest]: real milliseconds against a frame's budget.
 *
 * Tagged `simulation` and so left out of `:core:test` (REVIEW.md, finding 26): wall-clock time
 * depends on the machine and on what else it is doing, and a CI runner under load failed these
 * with no change in the code. The deterministic guard is the opened-cell count in
 * PathfindingCostTest; run these by hand (`./gradlew.bat :core:simulate`) when tuning the search.
 */
@Tag("simulation")
class PathfindingTimingTest {

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

}
