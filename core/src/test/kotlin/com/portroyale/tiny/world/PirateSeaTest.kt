package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.utils.GdxNativesLoader
import com.portroyale.tiny.diagnostics.GameRandom
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Sea of Pirates has to satisfy the two conditions that made it the choice in the first
 * place, and both are checkable rather than a matter of taste: it must be reachable open water,
 * and it must be a backwater rather than a crossroads. A lair between ports is not a source of
 * danger, it is a permanent blockade of the trade that passes there.
 *
 * The map is due to be redrawn in M16 with a hand-drawn coastline, which is exactly when a lair
 * measured against the old artwork could quietly end up inland. These run against the real image.
 */
class PirateSeaTest {

    private lateinit var worldMap: WorldMap
    private val portSafeRadius = 160f

    @BeforeTest
    fun loadRealMap() {
        GdxNativesLoader.load()
        val bytes = File("../assets/maps/caribbean_relief.png").readBytes()
        val pixmap = Pixmap(bytes, 0, bytes.size)
        worldMap = WorldMap(pixmap, MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT, 10f)
        pixmap.dispose()
    }

    private fun portDistances() = Ports.ALL
        .map { it.name to MapProjection.project(it.lat, it.lon).dst(PirateSea.lair) }
        .sortedBy { it.second }

    @Test
    fun `the lair is open water`() {
        assertTrue(worldMap.isWater(PirateSea.lair.x, PirateSea.lair.y), "the lair is on land")
    }

    @Test
    fun `a ship can sail from the trading world to the lair and back`() {
        // Tampa Bay and Lake Maracaibo both look like fine water and are enclosed basins the
        // pathfinder cannot enter; a lair in one would be a lair no pirate could ever leave.
        val havana = Ports.ALL.first { it.name == "Havana" }
        val from = MapProjection.project(havana.lat, havana.lon)
        val start = assertNotNull(worldMap.findNearestWater(from.x, from.y))
        val lair = assertNotNull(worldMap.findNearestWater(PirateSea.lair.x, PirateSea.lair.y))

        assertNotNull(worldMap.findPath(start.cpy(), lair.cpy()), "no route out to the lair")
        assertNotNull(worldMap.findPath(lair.cpy(), start.cpy()), "no route home from the lair")
    }

    @Test
    fun `the lair is well outside every port's safe harbour`() {
        val (name, distance) = portDistances().first()
        assertTrue(distance > portSafeRadius * 2, "$name is only $distance away")
    }

    @Test
    fun `the lair is a backwater, not a crossroads`() {
        // The measurement that ruled out the classic havens: at a junction several ports sit at
        // much the same range (Cayman Brac 346/391/430), while a backwater has one moderately
        // near and the rest far (this one 795/947/1158).
        val nearest = portDistances()
        val spread = nearest[2].second - nearest[0].second
        assertTrue(spread > 300f, "ports at ${nearest.take(3)} - spread of only $spread is a junction")
    }

    @Test
    fun `the fog covers the lair`() {
        assertTrue(PirateSea.contains(PirateSea.lair), "the lair should sit under its own fog")
    }

    @Test
    fun `the fog is a real rectangle on the map`() {
        assertTrue(PirateSea.fogWidth > 0f)
        assertTrue(PirateSea.fogHeight > 0f)
        val origin = PirateSea.fogOrigin
        assertTrue(origin.x >= 0f && origin.x + PirateSea.fogWidth <= MapProjection.MAP_WIDTH)
        assertTrue(origin.y >= 0f && origin.y + PirateSea.fogHeight <= MapProjection.MAP_HEIGHT)
    }

    @Test
    fun `no port stands inside the pirate sea`() {
        // If one ever did, that port's trade would sit permanently on top of the pirates' home.
        for (port in Ports.ALL) {
            val position = MapProjection.project(port.lat, port.lon)
            assertTrue(!PirateSea.contains(position), "${port.name} is inside the Sea of Pirates")
        }
    }

    @Test
    fun `the sea has a name to put on the map`() {
        assertTrue(PirateSea.NAME.isNotBlank())
    }
}

/** Home waters are a weighting, and a weighting is only right if it actually leans - and only
 *  safe if it never becomes a wall. */
class HomeWatersWeightingTest {

    private val scale = 500f

    private fun weightFor(distance: Float): Float {
        val falloff = 1f + distance / scale
        return 1f / (falloff * falloff)
    }

    @Test
    fun `a nearby destination is far likelier than a distant one`() {
        assertTrue(weightFor(0f) > weightFor(500f))
        assertTrue(weightFor(500f) > weightFor(2000f))
        // The whole map across is roughly 4000 units; the far side must be unlikely, not banned.
        assertTrue(weightFor(4000f) > 0f, "nowhere may be impossible")
    }

    @Test
    fun `the falloff is steep enough to read as a territory`() {
        // A neighbouring port should be worth several times a port on the far side of the map,
        // or "home waters" is a phrase with no observable meaning.
        assertTrue(weightFor(200f) / weightFor(2000f) > 10f)
    }

    @Test
    fun `weighted picking follows the weights`() {
        val items = listOf("near", "far")
        val counts = mutableMapOf("near" to 0, "far" to 0)
        GameRandom.withSeed(31337L) {
            repeat(2000) {
                val pick = GameRandom.pickWeighted(items) { if (it == "near") weightFor(100f) else weightFor(2000f) }
                counts[pick] = counts.getValue(pick) + 1
            }
        }
        assertTrue(counts.getValue("near") > counts.getValue("far") * 5, "counts were $counts")
        assertTrue(counts.getValue("far") > 0, "the far option must remain possible")
    }

    @Test
    fun `all-zero weights fall back to a plain pick instead of failing`() {
        val chosen = GameRandom.withSeed(1L) { GameRandom.pickWeighted(listOf("a", "b", "c")) { 0f } }
        assertTrue(chosen in listOf("a", "b", "c"))
    }

    @Test
    fun `a single candidate is always the answer`() {
        assertEquals("only", GameRandom.pickWeighted(listOf("only")) { 1f })
    }

    @Test
    fun `negative weights cannot poison the draw`() {
        val chosen = GameRandom.withSeed(2L) {
            List(50) { GameRandom.pickWeighted(listOf("bad", "good")) { if (it == "bad") -5f else 1f } }
        }
        assertTrue(chosen.all { it == "good" }, "a negative weight should count as zero")
    }
}
