package com.portroyale.tiny.world

import com.badlogic.gdx.math.MathUtils

/**
 * How fast a hull can change its mind about where it is pointing.
 *
 * Every ship in the game used to turn instantly: heading was simply assigned from whatever
 * direction the next waypoint happened to lie in, so a course change of a hundred and eighty
 * degrees took exactly one frame. The ship appeared to stop dead, spin on the spot and set off
 * again - which is the "jerky" motion, and it is also why a fleet rounding a headland looked like
 * a row of hulls snapping round one after another.
 *
 * Manoeuvrability was already a real statistic on every ship class, used to decide who lands
 * blows in a fight, and it is exactly the number this wants: a Cutter at 95 comes about in a few
 * seconds, a Galleon at 30 takes the better part of ten. Nothing here grants speed - a ship still
 * sails at its own pace, it simply cannot point somewhere new the instant it decides to.
 */
object Helm {

    /** Degrees per second even the most sluggish hull manages. */
    const val TURN_BASE_DEGREES = 10f

    /** Extra degrees per second for each point of manoeuvrability. */
    const val TURN_PER_MANEUVER_POINT = 0.35f

    /** How fast [maneuverability] lets a hull come round, in degrees per second. */
    fun turnRate(maneuverability: Int): Float =
        TURN_BASE_DEGREES + maneuverability.coerceAtLeast(0) * TURN_PER_MANEUVER_POINT

    /**
     * How wide a circle a ship of this speed and handling carves. Useful for deciding when a
     * waypoint counts as reached: a hull that physically cannot turn inside the arrival radius
     * would otherwise circle its own destination forever, which is a far worse bug than arriving
     * a few units wide of it.
     */
    fun turningRadius(speed: Float, maneuverability: Int): Float =
        turningRadiusAt(speed, turnRate(maneuverability))

    /** As [turningRadius], from a rate already worked out - which is what [Pilot] has to hand,
     *  since following a course needs nothing of a hull but its speed and how fast it comes
     *  round. */
    fun turningRadiusAt(speed: Float, turnRateDegrees: Float): Float {
        val radiansPerSecond = turnRateDegrees * MathUtils.degRad
        if (radiansPerSecond <= 0.0001f) return Float.MAX_VALUE
        return speed / radiansPerSecond
    }

    /** How much of her way a ship loses with the helm hard over. */
    const val MANOEUVRE_SPEED_LOSS = 0.6f

    /**
     * The fraction of her speed a ship carries while coming round [degreesToTurn].
     *
     * Ships slow in a turn - that is simply what happens - and modelling it removes the need for
     * any special pleading elsewhere: a hull moving slower also turns inside a tighter circle, so
     * even a Galleon fetches up on its waypoints without anyone having to widen what "arrived"
     * means for it. It costs the pursuer and the pursued alike, so a chase is still decided by
     * the two ships' speed and handling and by nothing else.
     */
    fun paceThroughTurn(degreesToTurn: Float): Float {
        val demand = (kotlin.math.abs(degreesToTurn) / 180f).coerceIn(0f, 1f)
        return 1f - MANOEUVRE_SPEED_LOSS * demand
    }

    /** Signed shortest way round from [from] to [to], in degrees, always within +/-180. */
    fun shortestTurn(from: Float, to: Float): Float {
        var difference = (to - from) % 360f
        if (difference > 180f) difference -= 360f
        if (difference < -180f) difference += 360f
        return difference
    }

    /** [current] heading brought as far towards [desired] as [maxDegrees] allows. */
    fun turnToward(current: Float, desired: Float, maxDegrees: Float): Float {
        val difference = shortestTurn(current, desired)
        if (MathUtils.isZero(difference, 0.001f)) return desired
        val step = difference.coerceIn(-maxDegrees, maxDegrees)
        return normalise(current + step)
    }

    fun normalise(degrees: Float): Float {
        var result = degrees % 360f
        if (result > 180f) result -= 360f
        if (result < -180f) result += 360f
        return result
    }
}
