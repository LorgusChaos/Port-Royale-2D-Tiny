package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.utils.GdxNativesLoader
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every port has to be somewhere a ship can actually get to, checked against the real map image
 * rather than a synthetic one.
 *
 * This is the one failure the game does not report. PortRoyaleGame resolves each port with
 * `worldMap.findNearestWater(pos.x, pos.y) ?: pos` - so a harbour placed too far inland silently
 * falls back to its own dry-land position, A* refuses every route to it, and the port simply sits
 * there being unvisitable. Nothing logs, nothing crashes.
 *
 * It matters most for the map redraw planned in M16: a crisper coastline moves the water boundary
 * by a few cells, and a port that hugged the shore can end up inside it. Run this against the new
 * artwork before anything else.
 */
class PortReachabilityTest {

    /** Matches PortRoyaleGame's own grid resolution. */
    private val cellSize = 10f

    private lateinit var worldMap: WorldMap

    @BeforeTest
    fun loadRealMap() {
        GdxNativesLoader.load()
        // Read as bytes rather than through Gdx.files: that needs a live backend, and Pixmap can
        // decode an encoded PNG straight from memory.
        val file = File("../assets/maps/caribbean_relief.png")
        assertTrue(file.exists(), "map asset not found at ${file.absolutePath}")
        val bytes = file.readBytes()
        val pixmap = Pixmap(bytes, 0, bytes.size)
        worldMap = WorldMap(pixmap, MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT, cellSize)
        pixmap.dispose()
    }

    @Test
    fun `every port has an approach point on water`() {
        for (port in Ports.ALL) {
            val position = MapProjection.project(port.lat, port.lon)
            val approach = worldMap.findNearestWater(position.x, position.y)
            assertNotNull(approach, "${port.name} has no reachable water within the search radius")
            assertTrue(
                worldMap.isWater(approach.x, approach.y),
                "${port.name}'s approach point is not on water"
            )
        }
    }

    @Test
    fun `a ship can sail between every pair of ports`() {
        // Not merely "is on water" - the whole network has to be one connected sea. A port inside
        // a lagoon the pathfinder cannot leave would pass the check above and still be useless.
        val approaches = Ports.ALL.map { port ->
            val position = MapProjection.project(port.lat, port.lon)
            port.name to worldMap.findNearestWater(position.x, position.y)
        }
        val origin = approaches.first()
        val originPoint = assertNotNull(origin.second, "${origin.first} has no approach point")

        for ((name, approach) in approaches.drop(1)) {
            val target = assertNotNull(approach, "$name has no approach point")
            assertNotNull(
                worldMap.findPath(originPoint.cpy(), target.cpy()),
                "no sea route from ${origin.first} to $name"
            )
        }
    }

    @Test
    fun `an approach point is close enough to still be that port`() {
        // findNearestWater walks outward up to 30 cells. Landing near the far end of that means
        // the port marker and the place ships actually arrive are visibly different spots.
        val tolerance = cellSize * 12f
        for (port in Ports.ALL) {
            val position = MapProjection.project(port.lat, port.lon)
            val approach = assertNotNull(worldMap.findNearestWater(position.x, position.y))
            val drift = approach.dst(position)
            assertTrue(drift < tolerance, "${port.name}'s anchorage drifted $drift away from the port")
        }
    }
}
