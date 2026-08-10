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

    /** Where pirates spawn and return to. Measured: open water, a route exists to it from the
     *  trading world, and the nearest port (Havana) is 795 units away. Roughly modern Panama
     *  City, Florida - though nothing on the map marks it, which is the point. */
    private const val LAIR_LAT = 30.16f
    private const val LAIR_LON = -85.66f

    /** The fogged area, Sarasota to New Orleans. Both corners fall on land, which is fine for fog
     *  over a region - but the lair itself has to be water, and is. */
    private const val NORTH_LAT = 30.8f
    private const val SOUTH_LAT = 27.0f
    private const val WEST_LON = -90.5f
    private const val EAST_LON = -82.5f

    val lair: Vector2 get() = MapProjection.project(LAIR_LAT, LAIR_LON)

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
