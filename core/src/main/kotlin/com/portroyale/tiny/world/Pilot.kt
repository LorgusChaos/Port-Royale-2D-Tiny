package com.portroyale.tiny.world

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2

/**
 * Following a laid course without running aground - the one piece of seamanship every hull in
 * the world shares.
 *
 * It used to live inside PortRoyaleGame, which is an ApplicationAdapter, which needs a GL
 * context - so the single most failure-prone rule in the game ("a ship given a route actually
 * gets there") could not be tested at all. What the test suite had instead were assertions that
 * one constant was smaller than another. Out here it is ordinary code: [WorldMap] reads its mask
 * from a Pixmap and needs no window, so a test can sail a real hull across the real Caribbean and
 * check that it arrives.
 *
 * Nothing here knows about factions, pirates or raids. It is given a hull, a route and a way to
 * ask whether a point is water, and it moves the hull one tick along that route.
 */
object Pilot {

    /**
     * What following a course asks of a hull, and nothing more. [AiShip] satisfies it as it
     * stands; a test can satisfy it with six fields.
     */
    interface Hull {
        val position: Vector2
        var headingDeg: Float
        /** World units per second at full speed, before any loss in a turn. */
        val speed: Float
        /** Degrees per second this hull can come round - see [Helm]. */
        val turnRate: Float
        val path: List<Vector2>
        var pathIndex: Int

        /**
         * The pilot's own notebook: which waypoint it is working on, the closest it has managed
         * to get to it, and how long it has been failing to beat that.
         *
         * Kept against the waypoint's identity rather than its index, so it looks after itself.
         * A caller that hands this hull a whole new route does not have to remember to clear
         * anything - the new route is made of new points, and the pilot notices. Requiring
         * callers to reset it would be the same mistake that let a pursuit outlive its target:
         * a rule that has to be applied in four places gets applied in three.
         */
        var trackedWaypoint: Vector2?
        var closestApproach: Float
        var stalledFor: Float
    }

    /** What one tick along a route came to. */
    enum class Step {
        /** Made way, or fetched up on a waypoint and took the next one. */
        SAILING,
        /** The last waypoint is behind us; this hull needs somewhere new to go. */
        ARRIVED,
        /** Could not move at all this tick - every heading tried was land. */
        BLOCKED,

        /**
         * Still moving, but no longer getting any closer to where it is going.
         *
         * The outcome that was missing, and the reason ships sat against headlands for a minute
         * at a time. A hull pinned bow-on to a shore is not blocked: the fan of headings for
         * working off a lee shore finds *something* every tick, so it creeps a unit or two
         * sideways and the pilot reported it as sailing. One unit in fifty seconds, with a valid
         * route it was making no progress along, and nothing in the world ever asked why. Being
         * stuck is a statement about progress, not about whether the hull moved at all.
         */
        STALLED
    }

    /**
     * Headings to try either side of the intended course when the bow is towards a shore.
     *
     * Wide enough to work off a headland, never so wide as to sail back the way it came. This
     * exists because turning takes time: a hull whose bow is towards the beach keeps that bow for
     * a second or two after it has decided to leave, and with only the two obvious attempts it
     * pinned itself there, asked for a new route every frame and never moved again. Half the
     * fleet spent a session parked along coastlines that way.
     */
    private val SHORE_CLEARANCE_ANGLES = intArrayOf(25, -25, 50, -50, 75, -75, 100, -100)

    /** How far out the "drawn abeam" rule applies, in turning circles - see [fetchedUp]. */
    private const val ABEAM_REACH = 2f

    /** The longest a single slice of movement may be, in world units: half a pathfinding cell,
     *  so no slice can step over a cell of land. */
    private const val MAX_SLICE_UNITS = 5f

    /** A tick longer than this many slices is a hitch, not a frame. Movement is truncated rather
     *  than letting one bad frame cost thousands of collision checks per hull. */
    private const val MAX_SLICES = 64

    /** Below this there is no tick left worth sailing. */
    private const val SPENT = 0.00001f

    /** How finely the water under a move is sampled: a quarter of a pathfinding cell, so no
     *  move can pass through a cell without being asked about it. */
    private const val SAMPLE_UNITS = 2.5f

    /** How much closer a hull has to get before that counts as progress rather than as yawing
     *  about - half a pathfinding cell, the same distance a slice of movement is capped at. */
    private const val PROGRESS_MARGIN = 5f

    /**
     * How long a hull may fail to get any closer before the caller is told.
     *
     * In sailing seconds, and generous: the slowest hull covers sixty units in this time, and
     * even a Galleon putting about through a hundred and eighty degrees is round inside nine. So
     * nothing honest trips it - a ship coming round a headland is still closing the range on the
     * waypoint beyond it. The same figure the chase logic uses for the same judgement
     * (Encounters.CHASE_STALL_SECONDS), because it is the same question: am I getting there?
     */
    private const val STALL_SECONDS = 12f

    /**
     * Moves [hull] one tick of [delta] seconds along its route.
     *
     * [arriveThreshold] is how close counts as having reached a waypoint. It is deliberately the
     * caller's business: a ship closing its final destination wants to actually get there, while
     * one rounding the third corner of a long passage does not care about a few units.
     */
    fun follow(
        hull: Hull,
        arriveThreshold: Float,
        delta: Float,
        isWater: (Float, Float) -> Boolean
    ): Step {
        var remaining = delta
        var moved = false
        var slices = 0
        while (slices < MAX_SLICES) {
            if (hull.pathIndex >= hull.path.size) return Step.ARRIVED
            val target = hull.path[hull.pathIndex]
            val dx = target.x - hull.position.x
            val dy = target.y - hull.position.y
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            // Tested every slice rather than once a tick, and that is the whole reason this loop
            // exists. Checked once a tick, a hull on a two-second tick sails thirty units past a
            // waypoint before it notices, then has to come round and fetch it again - which made
            // a passage take three quarters as long again at ten frames a second as at sixty.
            // How fast a ship sails must be a property of the ship, not of the frame rate.
            if (fetchedUp(hull, dx, dy, distance, arriveThreshold)) {
                hull.pathIndex++
                continue
            }
            if (hull.trackedWaypoint !== target) {
                hull.trackedWaypoint = target
                hull.closestApproach = distance
                hull.stalledFor = 0f
            } else if (distance < hull.closestApproach - PROGRESS_MARGIN) {
                hull.closestApproach = distance
                hull.stalledFor = 0f
            } else if (hull.stalledFor >= STALL_SECONDS) {
                return Step.STALLED
            }
            if (remaining <= SPENT) return if (moved) Step.SAILING else Step.BLOCKED
            val length = kotlin.math.min(remaining, sliceSeconds(hull, remaining))
            if (!sail(hull, dx / distance, dy / distance, length, isWater)) {
                return if (moved) Step.SAILING else Step.BLOCKED
            }
            moved = true
            hull.stalledFor += length
            remaining -= length
            slices++
        }
        return if (moved) Step.SAILING else Step.BLOCKED
    }

    /**
     * Whether this waypoint counts as fetched up on - and the fix for ships going in circles.
     *
     * A flat arrival circle cannot work, because it was smaller than the circle the hulls turn
     * in: 16 units against 21 to 27. Handed a waypoint closer than that and off the bow - the
     * ordinary case after any re-route - a hull could not reach it however long it tried. It
     * orbited instead, at full speed, and so never advanced to the next one. In the player's own
     * session log that was ships in the open ocean making twenty-three units of a possible
     * sixteen hundred: not stuck, going round and round. [Helm.turningRadius] was written to
     * prevent exactly this and had never been called by anything.
     *
     * So: a waypoint is reached when the hull is inside the circle it can actually turn in, and
     * also when it has drawn abeam of it - the waypoint is no longer ahead, so this is as close
     * as this hull is ever going to get, and pressing on towards it means another lap. Beyond a
     * couple of turning circles the question does not arise: something has gone properly wrong
     * and the caller should lay a new course, not skip waypoints.
     */
    private fun fetchedUp(
        hull: Hull,
        dx: Float,
        dy: Float,
        distance: Float,
        arriveThreshold: Float
    ): Boolean {
        val turningCircle = Helm.turningRadiusAt(hull.speed, hull.turnRate)
        if (distance < maxOf(arriveThreshold, turningCircle)) return true
        if (distance > turningCircle * ABEAM_REACH) return false
        val ahead = MathUtils.cosDeg(hull.headingDeg) * dx + MathUtils.sinDeg(hull.headingDeg) * dy
        return ahead <= 0f
    }

    /**
     * Brings [hull] round towards ([dirX], [dirY]) as fast as her helm allows and moves her as
     * far as that leaves her able to go. Reports whether she made any way at all.
     *
     * A ship carrying rudder loses way, as one does in reality - and because she is slower she
     * also turns inside a tighter circle, which is what lets her fetch up on a waypoint she could
     * not have reached at full speed. That is the honest fix; widening what counts as "arrived"
     * for sluggish hulls was papering over the missing manoeuvre.
     */
    fun steer(
        hull: Hull,
        dirX: Float,
        dirY: Float,
        delta: Float,
        isWater: (Float, Float) -> Boolean
    ): Boolean {
        // Sailed in slices short enough that a hull cannot cross a cell in one of them.
        //
        // This matters because a tick is not a fixed length: the world runs on real frame time
        // multiplied by the game speed, so at 20x a hull covers tens of units between one frame
        // and the next - and the only thing ever tested was the far end of that leap. Out in open
        // water that is harmless. Wedged bow-on against a headland it is not: a forty-unit jump
        // lands on land in every direction tried, including the eight the ship would work off a
        // lee shore with, while a two-unit step would have found water at once. The ship then sat
        // there butting at the cape until it gave up and went somewhere else entirely - which is
        // exactly what it looks like on screen.
        //
        // Slicing the tick makes a ship behave the same at 1x and at 20x, which it had never
        // done, and costs a handful of array lookups.
        var remaining = delta
        var moved = false
        var slices = 0
        while (remaining > SPENT && slices < MAX_SLICES) {
            val length = kotlin.math.min(remaining, sliceSeconds(hull, remaining))
            if (!sail(hull, dirX, dirY, length, isWater)) break
            moved = true
            remaining -= length
            slices++
        }
        return moved
    }

    /**
     * One slice: come round as far as the helm allows in that time, then make what way the turn
     * leaves her.
     *
     * Turned per slice as well as moved, so a long tick traces the arc a ship really sails rather
     * than one straight chord at the heading she finished the turn on.
     */
    private fun sail(
        hull: Hull,
        dirX: Float,
        dirY: Float,
        length: Float,
        isWater: (Float, Float) -> Boolean
    ): Boolean {
        val desired = MathUtils.atan2(dirY, dirX) * MathUtils.radDeg
        val pace = hull.speed * Helm.paceThroughTurn(Helm.shortestTurn(hull.headingDeg, desired))
        hull.headingDeg = Helm.turnToward(hull.headingDeg, desired, hull.turnRate * length)
        return advance(hull, dirX, dirY, pace * length, isWater)
    }

    /** How long a slice may last before the hull could cross a whole cell within it. */
    private fun sliceSeconds(hull: Hull, delta: Float): Float {
        if (hull.speed <= 0f) return delta
        return kotlin.math.min(delta, MAX_SLICE_UNITS / hull.speed)
    }

    /** One slice of movement: along the heading if there is water there, else the ways a ship
     *  works off a lee shore. Reports whether the hull moved at all. */
    private fun advance(
        hull: Hull,
        dirX: Float,
        dirY: Float,
        step: Float,
        isWater: (Float, Float) -> Boolean
    ): Boolean {
        val hx = MathUtils.cosDeg(hull.headingDeg)
        val hy = MathUtils.sinDeg(hull.headingDeg)
        if (nudgeIfWater(hull, hx * step, hy * step, isWater)) return true
        // Mid-turn the heading can point at a shore that the straight line to the waypoint
        // clears. Take the direct step rather than stall against the beach.
        if (nudgeIfWater(hull, dirX * step, dirY * step, isWater)) return true
        for (offset in SHORE_CLEARANCE_ANGLES) {
            val heading = Vector2(dirX, dirY).rotateDeg(offset.toFloat())
            if (nudgeIfWater(hull, heading.x * step, heading.y * step, isWater)) return true
        }
        return false
    }

    /**
     * Moves the hull if the water it would cross is clear the whole way; reports whether it did.
     *
     * Only the far end of the move used to be tested, and that is not the same question. A hull
     * could slip diagonally between two corners of land - both the cell it left and the cell it
     * entered are sea, and the line between them is not - which is precisely the move A* refuses
     * to plan ("no cutting the corner of a headland diagonally"). Movement allowing what
     * pathfinding forbids is how a ship ends up somewhere no route could have taken it, with the
     * shore between it and its own course.
     *
     * Caught at a third of a second per tick, which is an ordinary frame at 20x speed - so this
     * was not a rare event at the edge of the frame rate. It was happening all the time.
     */
    private fun nudgeIfWater(
        hull: Hull,
        dx: Float,
        dy: Float,
        isWater: (Float, Float) -> Boolean
    ): Boolean {
        val x = hull.position.x + dx
        val y = hull.position.y + dy
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        val samples = (distance / SAMPLE_UNITS).toInt() + 1
        for (sample in 0..samples) {
            val along = sample.toFloat() / samples
            if (!isWater(hull.position.x + dx * along, hull.position.y + dy * along)) return false
        }
        hull.position.set(x, y)
        return true
    }
}
