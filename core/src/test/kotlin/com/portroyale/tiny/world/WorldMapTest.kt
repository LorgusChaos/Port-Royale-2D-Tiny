package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.GdxNativesLoader
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pathfinding runs on a grid sampled from the map image's own pixels, so these tests build tiny
 * synthetic Pixmaps (white = water, black = land, matching the mask WorldMap now reads) instead of
 * loading the real 2000x1344 relief map - a 20x20 world makes each expected route obvious by hand.
 *
 * Pixmap is backed by libGDX's native gdx2d code; GdxNativesLoader.load() pulls that in without a
 * GL context or a window, so none of this needs the game to be running.
 */
class WorldMapTest {

    private val cellSize = 10f
    private val worldSize = 200f // 20x20 cells

    @BeforeTest
    fun loadNatives() {
        GdxNativesLoader.load()
    }

    /** [land] marks grid cells (gx, gy) as impassable; everything else is open water. Pixel rows
     *  run top-down while world y runs bottom-up, matching how WorldMap samples the real map. */
    private fun map(vararg land: Pair<Int, Int>): WorldMap {
        val cells = (worldSize / cellSize).toInt()
        val pixmap = Pixmap(cells, cells, Pixmap.Format.RGBA8888)
        pixmap.setColor(1f, 1f, 1f, 1f) // white: water
        pixmap.fill()
        pixmap.setColor(0f, 0f, 0f, 1f) // black: land
        for ((gx, gy) in land) {
            pixmap.drawPixel(gx, cells - 1 - gy)
        }
        return WorldMap(pixmap, worldSize, worldSize, cellSize)
    }

    /** Centre of grid cell (gx, gy) in world coordinates. */
    private fun cell(gx: Int, gy: Int) = Vector2((gx + 0.5f) * cellSize, (gy + 0.5f) * cellSize)

    // --- water sampling ---

    @Test
    fun `open water is sailable`() {
        val worldMap = map()
        assertTrue(worldMap.isWater(5f, 5f))
        assertTrue(worldMap.isWater(worldSize - 1f, worldSize - 1f))
    }

    @Test
    fun `land is not sailable`() {
        val worldMap = map(3 to 4)
        assertFalse(worldMap.isWater(35f, 45f))
        assertTrue(worldMap.isWater(35f, 55f), "the cell above the island is still water")
    }

    @Test
    fun `the world edge is a wall, not a wrap-around`() {
        val worldMap = map()
        assertFalse(worldMap.isWater(-1f, 50f))
        assertFalse(worldMap.isWater(50f, -1f))
        assertFalse(worldMap.isWater(worldSize + 1f, 50f))
        assertFalse(worldMap.isWater(50f, worldSize + 1f))
    }

    @Test
    fun `the image is sampled right way up`() {
        // A pixel drawn in the image's top row must read as land near the TOP of the world
        // (high y), not the bottom - the flip that keeps the coastline matching the drawn map.
        val worldMap = map(0 to 19)
        assertFalse(worldMap.isWater(5f, 195f), "top-left of the world should be the island")
        assertTrue(worldMap.isWater(5f, 5f), "bottom-left should still be water")
    }

    // --- nearest water ---

    @Test
    fun `a port already on water keeps its own position`() {
        val worldMap = map()
        val found = worldMap.findNearestWater(55f, 55f)
        assertNotNull(found)
        assertEquals(cell(5, 5), found)
    }

    @Test
    fun `a port sitting inland is given the nearest sailable spot`() {
        val worldMap = map(5 to 5)
        val found = worldMap.findNearestWater(55f, 55f)
        assertNotNull(found)
        assertTrue(worldMap.isWater(found.x, found.y), "the anchorage must actually be water")
        assertTrue(found.dst(cell(5, 5)) <= cellSize * 1.5f, "it should be adjacent, not across the map")
    }

    @Test
    fun `a port walled in beyond the search radius has no anchorage`() {
        val cells = (worldSize / cellSize).toInt()
        val allLand = (0 until cells).flatMap { gx -> (0 until cells).map { gy -> gx to gy } }
        val worldMap = map(*allLand.toTypedArray())
        assertNull(worldMap.findNearestWater(55f, 55f))
    }

    // --- pathfinding ---

    @Test
    fun `a straight run across open water is found`() {
        val worldMap = map()
        val path = worldMap.findPath(cell(1, 1), cell(1, 15))
        assertNotNull(path)
        assertTrue(path.isNotEmpty())
        assertEquals(cell(1, 15), path.last(), "a path must end exactly on the requested point")
    }

    @Test
    fun `a path is a chain of legs a hull can sail in a straight line`() {
        // This used to require consecutive waypoints to be neighbouring cells, which was a
        // statement about the search rather than about the route: a route is now string-pulled,
        // so a leg across open water is one waypoint to the next and hundreds of units long.
        // What actually has to hold is that the water between them is continuous - the invariant
        // the old wording was standing in for.
        val worldMap = map(*(0..15).map { 10 to it }.toTypedArray()) // wall with a gap at the top
        val path = worldMap.findPath(cell(2, 2), cell(18, 2))
        assertNotNull(path)
        for (i in 1 until path.size) {
            val from = path[i - 1]
            val to = path[i]
            val steps = (from.dst(to) / (cellSize * 0.5f)).toInt().coerceAtLeast(1)
            for (step in 0..steps) {
                val along = step.toFloat() / steps
                val x = from.x + (to.x - from.x) * along
                val y = from.y + (to.y - from.y) * along
                assertTrue(
                    worldMap.isWater(x, y),
                    "leg $i from $from to $to crosses land at ($x, $y)"
                )
            }
        }
    }

    @Test
    fun `every waypoint on a path is sailable`() {
        val worldMap = map(*(0..15).map { 10 to it }.toTypedArray()) // wall with a gap at the top
        val path = worldMap.findPath(cell(2, 2), cell(18, 2))
        assertNotNull(path)
        for (point in path.dropLast(1)) {
            assertTrue(worldMap.isWater(point.x, point.y), "the route runs aground at $point")
        }
    }

    @Test
    fun `a wall is sailed around, not through`() {
        val worldMap = map(*(0..15).map { 10 to it }.toTypedArray())
        val blocked = worldMap.findPath(cell(2, 2), cell(18, 2))
        val open = map().findPath(cell(2, 2), cell(18, 2))

        assertNotNull(blocked)
        assertNotNull(open)
        assertTrue(blocked.size > open.size, "the detour should be longer than the straight run")
        // The wall occupies x = 105 for y = 5..155; the only way past is over the top at y >= 165.
        assertTrue(blocked.none { it.x in 100f..110f && it.y < 160f }, "the route cut through the wall")
    }

    @Test
    fun `a destination on land is unreachable`() {
        val worldMap = map(9 to 9)
        assertNull(worldMap.findPath(cell(1, 1), cell(9, 9)))
    }

    @Test
    fun `a start on land is unreachable`() {
        val worldMap = map(1 to 1)
        assertNull(worldMap.findPath(cell(1, 1), cell(9, 9)))
    }

    @Test
    fun `a fully walled-off destination is unreachable`() {
        // Seal cell (5,5) behind land on all eight sides.
        val wall = listOf(
            4 to 4, 4 to 5, 4 to 6,
            5 to 4, 5 to 6,
            6 to 4, 6 to 5, 6 to 6
        )
        val worldMap = map(*wall.toTypedArray())
        assertNull(worldMap.findPath(cell(1, 1), cell(5, 5)))
    }

    @Test
    fun `a ship cannot squeeze diagonally between two corners of land`() {
        // Both orthogonal neighbours of a diagonal step must be open, or a ship would clip a
        // headland it should have sailed around.
        val worldMap = map(5 to 6, 6 to 5)
        val path = worldMap.findPath(cell(5, 5), cell(6, 6))
        assertNotNull(path)
        assertTrue(path.size > 2, "the ship cut the corner instead of going around: $path")
    }

    @Test
    fun `a path to where the ship already is is trivially satisfied`() {
        val worldMap = map()
        val target = cell(5, 5)
        val path = worldMap.findPath(target, target)
        assertNotNull(path)
        assertEquals(listOf(target), path)
    }

    // --- avoiding danger ---

    @Test
    fun `a threat in the way pushes the route around it`() {
        val worldMap = map()
        val direct = worldMap.findPath(cell(2, 10), cell(18, 10))
        val evasive = worldMap.findPath(cell(2, 10), cell(18, 10), avoid = cell(10, 10), avoidRadiusWorld = 40f)

        assertNotNull(direct)
        assertNotNull(evasive)
        assertTrue(
            evasive.none { it.dst(cell(10, 10)) <= 35f },
            "the evasive route still sailed through the danger circle"
        )
        assertTrue(evasive.size >= direct.size, "avoiding danger should not somehow be shorter")
    }

    @Test
    fun `a zero-radius threat changes nothing`() {
        val worldMap = map()
        val plain = worldMap.findPath(cell(2, 10), cell(18, 10))
        val withNoop = worldMap.findPath(cell(2, 10), cell(18, 10), avoid = cell(10, 10), avoidRadiusWorld = 0f)
        assertEquals(plain?.size, withNoop?.size)
    }

    @Test
    fun `sailing past danger beats freezing in place`() {
        // If the avoid circle swallows the only channel, WorldMap falls back to the direct route
        // rather than returning null and leaving the ship stuck.
        val worldMap = map()
        val path = worldMap.findPath(
            cell(2, 10),
            cell(18, 10),
            avoid = cell(10, 10),
            avoidRadiusWorld = worldSize * 2f // covers the entire world
        )
        assertNotNull(path, "a captain with nowhere safe to go should still sail")
        assertEquals(cell(18, 10), path.last())
    }

    @Test
    fun `a threat sitting on the destination does not strand the ship`() {
        val worldMap = map()
        val path = worldMap.findPath(cell(2, 10), cell(18, 10), avoid = cell(18, 10), avoidRadiusWorld = 30f)
        assertNotNull(path)
        assertEquals(cell(18, 10), path.last())
    }
}
