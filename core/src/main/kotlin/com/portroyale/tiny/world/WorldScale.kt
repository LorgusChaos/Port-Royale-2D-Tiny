package com.portroyale.tiny.world

/**
 * What a world unit actually is, and the distances defined in terms of it.
 *
 * Every threshold in this game was originally picked as a bare number, by eye, without anyone
 * knowing what a unit meant - which is how a pirate raid came to be sited five ship-lengths from
 * a harbour, inside the very exclusion zone that forbids anything from happening. Two honest
 * yardsticks, measured rather than assumed:
 *
 *  - **Kilometres.** The map spans 41 degrees of longitude in 4000 units and 26 of latitude in
 *    2688, so one unit is 1.08 km either way at 19N. Useful for sanity, misleading for design:
 *    by this measure a Galleon is 43 km long. The map and the ships are drawn to different
 *    scales, as game maps always are.
 *  - **Ship lengths.** A hull is 24 to 40 units, so the whole Caribbean is about 100-150 ships
 *    across, ships engage at under two lengths, and a harbour's safe water is a handful. This is
 *    the measure that matches what a player actually sees, and the one to reason in.
 *
 * Lengths come from the sprite: width is [SHIP_SPRITE_WIDTH] times the class's spriteScale, and
 * length follows the artwork's aspect ratio. Recorded here as measured constants because the
 * aspect ratio lives in a PNG the game logic cannot read without a graphics backend.
 */
object WorldScale {

    /** One world unit in kilometres, north-south and east-west alike at the map's middle
     *  latitude. The projection is very nearly isotropic through the playable band. */
    const val KM_PER_UNIT = 1.08f

    /** World-unit width of a ship sprite before its class scale - see PortRoyaleGame's rendering. */
    const val SHIP_SPRITE_WIDTH = 13f

    /** Measured hull lengths, shortest and longest of the five classes. */
    const val CUTTER_LENGTH = 23.6f
    const val SLOOP_LENGTH = 26.8f
    const val GALLEON_LENGTH = 39.6f

    /** A convenient middling hull to think in. */
    const val TYPICAL_SHIP_LENGTH = 30f

    /**
     * How far a harbour's protection reaches: **two Galleon lengths**.
     *
     * It was 160 units - six Sloop lengths, or a sixth of all the water on the map once the
     * twenty-eight harbours were counted together. That is a large fraction of the sea in which
     * nothing may happen, and it is what put a whole pirate raid somewhere its own rules made
     * inert. Two hulls is enough that leaving port cannot drop the player straight into an
     * ambush, and small enough that danger is allowed to exist near the places worth sailing to.
     */
    const val PORT_SAFE_RADIUS = GALLEON_LENGTH * 2f

    /**
     * How far apart two hulls must keep, centre to centre: **one Galleon length**.
     *
     * It was 34 - shorter than the longest ship on the map, so two big hulls held exactly at the
     * limit still overlapped bow to stern on screen. Clearance has to be the longest hull, not an
     * average one, or the biggest ships are precisely the ones seen sailing through each other.
     */
    const val HULL_CLEARANCE = GALLEON_LENGTH

    /**
     * World units per second a crowded hull drifts aside, per neighbour crowding it.
     *
     * It was 26 against hull speeds of 9.5 to 16.5, applied once per neighbour and never
     * bounded - so a ship in any crowd was pushed harder than it could sail and stopped making
     * headway altogether. That went unnoticed for a long time only because a broken docking
     * exemption had separation switched off for essentially every moving ship.
     */
    const val SEPARATION_DRIFT = 5f

    /** The most of its own speed a ship may be shoved by crowding, however many neighbours it
     *  has. Separation exists to stop hulls sharing a pixel, never to overpower steering. */
    const val SEPARATION_SPEED_FRACTION = 0.5f

    fun unitsToKm(units: Float): Float = units * KM_PER_UNIT

    fun unitsToShipLengths(units: Float): Float = units / TYPICAL_SHIP_LENGTH
}
