package com.portroyale.tiny.world

import com.badlogic.gdx.math.Vector2

/**
 * Calibrated against the documented bounding box of the "Middle America relief location map"
 * (Wikimedia Commons: North 32.0N, South 6.0N, West 99.0W, East 58.0W, equirectangular),
 * used here at its native 2000x1344 resolution. Exact published bounds, not read off the art
 * by eye, so this should line up closely with the actual coastline artwork.
 */
object MapProjection {
    const val MAP_WIDTH = 2000f
    const val MAP_HEIGHT = 1344f

    private const val LON_WEST = -99f
    private const val LON_EAST = -58f
    private const val LAT_NORTH = 32f
    private const val LAT_SOUTH = 6f

    private val pxPerDegLon = MAP_WIDTH / (LON_EAST - LON_WEST)
    private val pxPerDegLat = MAP_HEIGHT / (LAT_NORTH - LAT_SOUTH)

    fun project(lat: Float, lon: Float): Vector2 {
        val x = (lon - LON_WEST) * pxPerDegLon
        val yFromTop = (LAT_NORTH - lat) * pxPerDegLat
        val y = MAP_HEIGHT - yFromTop
        return Vector2(x, y)
    }
}
