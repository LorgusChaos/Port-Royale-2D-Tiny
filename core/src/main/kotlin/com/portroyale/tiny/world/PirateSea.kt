package com.portroyale.tiny.world

import com.badlogic.gdx.math.Vector2

/**
 * The Sea of Pirates - the northern Gulf of Mexico, the one stretch of this map with no port on
 * it at all.
 *
 * Putting the pirates' home there solves two problems with one decision. A lair on a shipping
 * crossroads is not a source of danger but a permanent blockade: pirates concentrate around their
 * home, and if home sits between three ports, that trade simply stops. And an empty third of the
 * map was doing nothing. The measurement that settled it: the classic havens all sit *between*
 * ports (Cayman Brac has three within 85 units of each other's range; Tortuga is 68 from
 * Cap-Francais, inside its safe harbour radius), while the northern Gulf has nothing within 680.
 *
 * The region is drawn and named on the map rather than hidden. That is not a breach of the
 * information asymmetry the design pillars ask for: the player is told *where* a sea has a bad
 * name, not how dangerous any particular voyage is - which is what M11b will make earnable.
 */
object PirateSea {

    const val NAME = "Sea of Pirates"

    /** Centre of the anchorage pirates spawn in and return to - open Gulf water well clear of
     *  any coast, not a point on the shore. Measured: water, a route exists to it from the
     *  trading world, nearest port 684 units away. */
    private const val LAIR_LAT = 28.6f
    private const val LAIR_LON = -86.3f

    /** The lair is an anchorage, not a pin: four Galleon lengths across, so a band gathers as a
     *  cluster riding at anchor rather than a stack of hulls on one coordinate. */
    const val LAIR_RADIUS = WorldScale.GALLEON_LENGTH * 2f

    /** The fogged area, Sarasota to New Orleans. Both corners fall on land, which is fine for fog
     *  over a region - but the lair itself has to be water, and is. */
    private const val NORTH_LAT = 30.8f
    private const val SOUTH_LAT = 27.0f
    private const val WEST_LON = -90.5f
    private const val EAST_LON = -82.5f

    val lair: Vector2 get() = MapProjection.project(LAIR_LAT, LAIR_LON)

    /** A berth somewhere in the anchorage. [angle] and [distanceFraction] are 0..1 rolls from the
     *  caller, so this stays free of any randomness of its own and can be tested exactly. */
    fun berth(angle: Float, distanceFraction: Float): Vector2 {
        val centre = lair
        val distance = LAIR_RADIUS * distanceFraction
        return Vector2(
            centre.x + kotlin.math.cos(angle) * distance,
            centre.y + kotlin.math.sin(angle) * distance
        )
    }

    /** Bottom-left corner of the fog rectangle in world units. */
    val fogOrigin: Vector2 get() = MapProjection.project(SOUTH_LAT, WEST_LON)

    val fogWidth: Float get() = MapProjection.project(SOUTH_LAT, EAST_LON).x - fogOrigin.x
    val fogHeight: Float get() = MapProjection.project(NORTH_LAT, WEST_LON).y - fogOrigin.y

    fun contains(position: Vector2): Boolean {
        val origin = fogOrigin
        return position.x >= origin.x && position.x <= origin.x + fogWidth &&
            position.y >= origin.y && position.y <= origin.y + fogHeight
    }
}
