package com.portroyale.tiny.world

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.GdxNativesLoader
import com.portroyale.tiny.ships.ShipTypes
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A ship given a course actually gets there, at something like its own speed.
 *
 * This is the most failure-prone rule in the game and it had no test, because following a course
 * lived inside an ApplicationAdapter and an ApplicationAdapter needs a window. What the suite had
 * instead were assertions that one constant was smaller than another - true, and blind to the
 * fault standing next to them: every hull in the game turns inside a circle of 22 to 27 units, A*
 * handed them a waypoint every 10, and so the whole fleet spent its life overshooting waypoints
 * in open water. Sixty per cent of a session log was the watchdog saying so.
 *
 * [WorldMap] reads its mask from a Pixmap and needs no GL context, so the real Caribbean can be
 * sailed right here - which is the only way this class of bug was ever going to be caught by
 * anything other than a human watching the screen.
 */
class PilotTest {

    private lateinit var worldMap: WorldMap

    @BeforeTest
    fun loadRealMap() {
        GdxNativesLoader.load()
        val bytes = File("../assets/maps/caribbean_mask.png").readBytes()
        val pixmap = Pixmap(bytes, 0, bytes.size)
        worldMap = WorldMap(pixmap, MapProjection.MAP_WIDTH, MapProjection.MAP_HEIGHT, 10f)
        pixmap.dispose()
    }

    /** As PortRoyaleGame's own AI_SHIP_ARRIVE_THRESHOLD. */
    private val arriveThreshold = 16f

    /** Demanding passages: round the length of Cuba, through the Windward Passage, along the
     *  Gulf coast, and down the island chain. Nothing here is open-ocean cruising. */
    private val passages = listOf(
        "Havana" to "Santiago de Cuba",
        "Port Royal" to "Port-au-Prince",
        "Veracruz" to "Campeche",
        "St John's" to "Bridgetown"
    )

    private fun approach(name: String): Vector2 {
        val port = Ports.ALL.first { it.name == name }
        val projected = MapProjection.project(port.lat, port.lon)
        return assertNotNull(
            worldMap.findNearestWater(projected.x, projected.y),
            "$name has no water to sail from"
        )
    }

    private class TestHull(
        override val speed: Float,
        maneuverability: Int,
        start: Vector2
    ) : Pilot.Hull {
        override val position: Vector2 = start.cpy()
        override var headingDeg: Float = 0f
        override val turnRate: Float = Helm.turnRate(maneuverability)
        override var path: List<Vector2> = emptyList()
        override var pathIndex: Int = 0
        override var trackedWaypoint: Vector2? = null
        override var closestApproach: Float = Float.MAX_VALUE
        override var stalledFor: Float = 0f
    }

    private class Voyage(
        val arrived: Boolean,
        val gameSeconds: Float,
        val worstStallFraction: Float,
        val whereStalled: Vector2?,
        /** Whether the pilot ever reported that it had stopped getting anywhere. */
        val reportedStalled: Boolean = false
    )

    /**
     * Sails [hull] along its route a tick at a time, watching for the two ways this goes wrong:
     * never arriving, and arriving at a crawl.
     *
     * The stall window is deliberately the watchdog's own rule - how much of the way it could
     * have made did it actually make - so a failure here and a line in the player's session log
     * mean the same thing.
     */
    private fun sail(hull: TestHull, delta: Float, limitSeconds: Float): Voyage {
        var elapsed = 0f
        var sinceWindow = 0f
        var windowStart = hull.position.cpy()
        var worst = 1f
        var worstAt: Vector2? = null
        var reportedStalled = false
        while (elapsed < limitSeconds) {
            val before = hull.position.cpy()
            val step = Pilot.follow(hull, arriveThreshold, delta, worldMap::isWater)
            elapsed += delta
            assertTrue(
                worldMap.isWater(hull.position.x, hull.position.y),
                "hull sailed onto land at ${hull.position}"
            )
            // Only where the chord and the path are the same thing - see assertStayedOnWater.
            if (delta <= CHORD_IS_THE_PATH) assertStayedOnWater(before, hull.position, delta)
            if (step == Pilot.Step.STALLED) reportedStalled = true
            if (step == Pilot.Step.ARRIVED) {
                return Voyage(true, elapsed, worst, worstAt, reportedStalled)
            }
            sinceWindow += delta
            if (sinceWindow >= STALL_WINDOW_SECONDS) {
                val made = windowStart.dst(hull.position)
                val fraction = made / (hull.speed * sinceWindow)
                if (fraction < worst) {
                    worst = fraction
                    worstAt = hull.position.cpy()
                }
                sinceWindow = 0f
                windowStart = hull.position.cpy()
            }
        }
        return Voyage(false, elapsed, worst, worstAt, reportedStalled)
    }

    /**
     * A tick may not carry a hull across land.
     *
     * Checking only where a ship ends up is not enough, and that is not a hypothetical: the move
     * was tested at its far end only, so a hull could slip diagonally between two corners of
     * land - sea where it started, sea where it finished, land on the line between - which is the
     * one move A* refuses to plan. It was caught here at a third of a second per tick, an
     * ordinary frame at 20x, so it was not a rarity at the edge of the frame rate.
     *
     * Applied only to short ticks, because this measures the straight line between where a tick
     * began and where it ended. Over a long tick a hull turns as it goes and sails an arc, and
     * the chord of an arc is not the water the ship crossed - asking about it would report land
     * the hull sailed round. The guarantee for long ticks is in the production code instead,
     * where each slice of movement is checked along its own length.
     */
    private fun assertStayedOnWater(from: Vector2, to: Vector2, delta: Float) {
        val distance = from.dst(to)
        if (distance <= 0f) return
        // Half a cell, the granularity at which the map itself is defined.
        val steps = (distance / 5f).toInt() + 1
        for (step in 0..steps) {
            val along = step.toFloat() / steps
            val x = from.x + (to.x - from.x) * along
            val y = from.y + (to.y - from.y) * along
            assertTrue(
                worldMap.isWater(x, y),
                "a ${delta}s tick carried the hull from $from to $to, across land at ($x, $y)"
            )
        }
    }

    private fun routeFor(from: String, to: String): List<Vector2> =
        assertNotNull(worldMap.findPath(approach(from), approach(to)), "no route $from -> $to")

    private fun lengthOf(path: List<Vector2>): Float {
        var total = 0f
        for (index in 0 until path.size - 1) total += path[index].dst(path[index + 1])
        return total
    }

    @Test
    fun `every class of hull completes every passage, at something like its own speed`() {
        // Both regimes: a frame at 1x, and a frame at 20x. The fault this guards against only
        // ever showed at speed, because a tick twenty times longer is a turn twenty times
        // coarser - and a hull that cannot fetch up on a waypoint circles it instead.
        for (delta in listOf(1f / 60f, 1f / 3f, 1f, 2f)) {
            for (shipType in ShipTypes.ALL) {
                for ((from, to) in passages) {
                    val route = routeFor(from, to)
                    val hull = TestHull(shipType.speed, shipType.baseManeuverability, route.first())
                    hull.path = route
                    val straight = lengthOf(route) / shipType.speed
                    val voyage = sail(hull, delta, straight * CRAWL_ALLOWANCE)
                    assertFalse(
                        voyage.reportedStalled,
                        "${shipType.name} was called stuck on an ordinary passage $from -> $to " +
                            "at delta $delta - a false alarm costs a re-routed course every time"
                    )
                    assertTrue(
                        voyage.arrived,
                        "${shipType.name} never got from $from to $to at delta $delta - " +
                            "gave up at ${hull.position}, waypoint ${hull.pathIndex} of " +
                            "${route.size}, after ${voyage.gameSeconds.toInt()}s of a possible " +
                            "${straight.toInt()}s"
                    )
                }
            }
        }
    }

    @Test
    fun `a hull keeps making way for the whole passage, never grinding to a halt`() {
        // The bug in the player's own session log, as a test: a hull that stops dead in open
        // water for five seconds at a time is not sailing, whatever the map says about where it
        // is. Measured in the same terms the watchdog reports, so the two cannot disagree.
        for (shipType in ShipTypes.ALL) {
            for ((from, to) in passages) {
                val route = routeFor(from, to)
                val hull = TestHull(shipType.speed, shipType.baseManeuverability, route.first())
                hull.path = route
                val straight = lengthOf(route) / shipType.speed
                val voyage = sail(hull, 1f / 3f, straight * CRAWL_ALLOWANCE)
                assertTrue(
                    voyage.worstStallFraction > WATCHDOG_STUCK_FRACTION,
                    "${shipType.name} on $from -> $to made only " +
                        "${(voyage.worstStallFraction * 100).toInt()}% of its possible way in a " +
                        "${STALL_WINDOW_SECONDS.toInt()}s window near ${voyage.whereStalled}"
                )
            }
        }
    }

    @Test
    fun `a hull given a waypoint it cannot turn inside still gets past it`() {
        // Straight from the player's session log: ships in the open ocean - clearance 8, a valid
        // route, nothing chasing them - making twenty-three units of a possible sixteen hundred.
        // They were not stopped. They were going in circles: the watchdog measures displacement,
        // and a hull orbiting a waypoint at full speed displaces nothing.
        //
        // It happens whenever a hull is handed a waypoint closer than its own turning circle and
        // off the bow, which is the normal case after any re-route - a threat avoided, a chase
        // broken off, a destination changed. Every class turns inside 21 to 27 units; the arrival
        // circle was a flat 16, so none of them could fetch up on such a waypoint at all.
        val openSea = Vector2(2169f, 426f)
        assertTrue(
            worldMap.clearanceCellsAt(openSea.x, openSea.y) >= 6,
            "this test needs open ocean; ${openSea} is no longer that"
        )
        for (shipType in ShipTypes.ALL) {
            val turningCircle = Helm.turningRadius(shipType.speed, shipType.baseManeuverability)
            for (offBow in listOf(90f, 135f, 180f)) {
                for (range in listOf(18f, 25f, 35f)) {
                    val waypoint = Vector2(range, 0f).rotateDeg(offBow).add(openSea)
                    // Somewhere to go on afterwards, so "arrived" means it got past the awkward
                    // waypoint rather than simply running out of route at it.
                    val beyond = Vector2(waypoint).add(Vector2(300f, 0f).rotateDeg(offBow))
                    val hull = TestHull(shipType.speed, shipType.baseManeuverability, openSea)
                    hull.path = listOf(waypoint, beyond)
                    // Pointing along the x axis, so offBow really is the angle off the bow.
                    hull.headingDeg = 0f
                    // One full circle plus the run out, with room to spare: anything that still
                    // has not got there is not arriving at all.
                    val allowance = (360f / (shipType.speed / turningCircle)) + 300f / shipType.speed
                    val voyage = sail(hull, 1f / 3f, allowance * 3f)
                    assertTrue(
                        voyage.arrived,
                        "${shipType.name} (turning circle ${turningCircle.toInt()}) could not " +
                            "fetch up on a waypoint ${range.toInt()} units away, ${offBow.toInt()}" +
                            " degrees off the bow - gave up at waypoint ${hull.pathIndex}, " +
                            "${hull.position.dst(waypoint).toInt()} units from it"
                    )
                }
            }
        }
    }

    @Test
    fun `how fast a hull gets there does not depend on the frame rate`() {
        // The world advances by real frame time times the game speed, so the tick length is not
        // a constant of the simulation - it is whatever the machine and the speed control happen
        // to produce, from a sixtieth of a second to several seconds. A passage that takes half
        // again as long because the frame rate dipped is a simulation that reports different
        // physics on different hardware.
        for (shipType in ShipTypes.ALL) {
            for ((from, to) in passages) {
                val route = routeFor(from, to)
                val straight = lengthOf(route) / shipType.speed
                val times = listOf(1f / 60f, 1f, 2f).map { delta ->
                    val hull = TestHull(shipType.speed, shipType.baseManeuverability, route.first())
                    hull.path = route
                    val voyage = sail(hull, delta, straight * CRAWL_ALLOWANCE)
                    assertTrue(voyage.arrived, "${shipType.name} $from -> $to never arrived at $delta")
                    delta to voyage.gameSeconds
                }
                val quickest = times.minOf { it.second }
                val slowest = times.maxBy { it.second }
                assertTrue(
                    slowest.second <= quickest * FRAME_RATE_TOLERANCE,
                    "${shipType.name} on $from -> $to took ${slowest.second.toInt()}s with a " +
                        "${slowest.first}s tick against ${quickest.toInt()}s at best - the frame " +
                        "rate is deciding how fast ships sail"
                )
            }
        }
    }

    @Test
    fun `a hull wedged against a headland works itself off, however long the frame`() {
        // Watched happening on screen: a ship butts at a cape for a long time and then sails off
        // in a quite different direction - the signature of steer() finding no water in any of
        // the ten headings it tries, and the voyage eventually being abandoned.
        //
        // The cause is that a tick has no fixed length. The world advances by real frame time
        // times the game speed, so at 20x a hull covers tens of units per frame, and only the far
        // end of that leap was ever tested for water. In open sea that is harmless. In a corner it
        // is fatal: a forty-unit jump is on land in every direction, including the eight for
        // working off a lee shore, where a two-unit step finds water immediately.
        val (wedge, intoLand) = findWedge()
        val seaward = assertNotNull(openWaterNear(wedge), "no open water within reach of $wedge")
        for (delta in listOf(1f / 60f, 1f, 2f)) {
            for (shipType in ShipTypes.ALL) {
                val route = assertNotNull(
                    worldMap.findPath(wedge.cpy(), seaward.cpy()),
                    "no route off the headland at $wedge"
                )
                val hull = TestHull(shipType.speed, shipType.baseManeuverability, wedge)
                hull.path = route
                // Bow hard into the land, which is how a ship shoved sideways by crowding or
                // carried wide by its own turn ends up against a shore in the first place.
                hull.headingDeg = intoLand
                val straight = lengthOf(route) / shipType.speed
                val voyage = sail(hull, delta, straight * CRAWL_ALLOWANCE + TURN_ALLOWANCE)
                assertTrue(
                    voyage.arrived,
                    "${shipType.name} could not work off the headland at $wedge with a " +
                        "${delta}s tick - still ${hull.position.dst(seaward).toInt()} units from " +
                        "open water, having moved ${hull.position.dst(wedge).toInt()} units in all"
                )
            }
        }
    }

    /** The first spot on the map, scanned in a fixed order, that is water a single cell off the
     *  shore with land straight ahead - a hull's-eye view of being wedged against a cape. */
    private fun findWedge(): Pair<Vector2, Float> {
        var step = 70f
        while (step > 10f) {
            var y = step
            while (y < MapProjection.MAP_HEIGHT) {
                var x = step
                while (x < MapProjection.MAP_WIDTH) {
                    if (worldMap.isWater(x, y) && worldMap.clearanceCellsAt(x, y) == 1) {
                        for (bearing in 0 until 8) {
                            val heading = bearing * 45f
                            val ahead = Vector2(25f, 0f).rotateDeg(heading)
                            if (!worldMap.isWater(x + ahead.x, y + ahead.y)) {
                                return Vector2(x, y) to heading
                            }
                        }
                    }
                    x += step
                }
                y += step
            }
            step /= 2f
        }
        throw AssertionError("the Caribbean has no coastline, which cannot be right")
    }

    /** Somewhere with room to manoeuvre, found by walking outward from [from]. */
    private fun openWaterNear(from: Vector2): Vector2? {
        for (radius in 10..40) {
            for (bearing in 0 until 16) {
                val point = Vector2(radius * 10f, 0f).rotateDeg(bearing * 22.5f).add(from)
                if (worldMap.clearanceCellsAt(point.x, point.y) >= 5) return point
            }
        }
        return null
    }

    @Test
    fun `a hull that cannot reach its waypoint says so instead of creeping for ever`() {
        // The fault watched on screen and then found in the log: a ship butting at a cape, one
        // unit of progress in fifty seconds, a valid route it was making no headway along, and
        // nothing ever asking why. It was not blocked - the fan of headings for working off a
        // lee shore always finds somewhere to put the bow - so the pilot called it sailing and
        // the world believed it. The hull has to be able to say "I am not getting there".
        //
        // The geometry here is the real one: a waypoint on the far side of a headland, which is
        // what a hull carried off its course by crowding, a chase or a flight ends up with.
        val (wedge, intoLand) = findWedge()
        val waypoint = Vector2(STALL_WAYPOINT_RANGE, 0f).rotateDeg(intoLand).add(wedge)
        assertTrue(
            crossesLand(wedge, waypoint),
            "this test needs a waypoint behind land; $wedge -> $waypoint is open water"
        )
        for (shipType in ShipTypes.ALL) {
            val hull = TestHull(shipType.speed, shipType.baseManeuverability, wedge)
            hull.path = listOf(waypoint)
            hull.headingDeg = intoLand
            val voyage = sail(hull, 1f / 3f, STALL_REPORT_ALLOWANCE)
            assertTrue(
                voyage.reportedStalled,
                "${shipType.name} spent ${STALL_REPORT_ALLOWANCE.toInt()}s failing to reach a " +
                    "waypoint behind a headland without ever reporting it - it got " +
                    "${hull.position.dst(waypoint).toInt()} units away, having started " +
                    "${wedge.dst(waypoint).toInt()} away"
            )
        }
    }

    /** Whether the straight line between two points passes over land. */
    private fun crossesLand(from: Vector2, to: Vector2): Boolean {
        val steps = (from.dst(to) / 5f).toInt() + 1
        for (step in 0..steps) {
            val along = step.toFloat() / steps
            val x = from.x + (to.x - from.x) * along
            val y = from.y + (to.y - from.y) * along
            if (!worldMap.isWater(x, y)) return true
        }
        return false
    }

    @Test
    fun `a route is a handful of legs, not a staircase of cells`() {
        val route = routeFor("Havana", "Bridgetown")
        val legs = route.size - 1
        val meanLeg = lengthOf(route) / legs
        // The widest turning circle on the map is the Galleon's, a touch over 26 units. A leg
        // shorter than that is a leg no hull can steer down.
        val widestTurningCircle = ShipTypes.ALL.maxOf {
            Helm.turningRadius(it.speed, it.baseManeuverability)
        }
        assertTrue(
            meanLeg > widestTurningCircle,
            "mean leg $meanLeg is inside the widest turning circle $widestTurningCircle - " +
                "$legs legs over ${lengthOf(route).toInt()} units"
        )
    }

    @Test
    fun `a route keeps a berth off the shore where the sea allows one`() {
        // Not every yard of it can: a harbour approach is inshore by definition, and some straits
        // are narrow. The rule is about the passage as a whole - most of its length should be in
        // water a hull can turn in.
        //
        // Measured along the length rather than over the waypoints, and the difference is not
        // pedantry: string-pulling leaves a waypoint only where the line past it would cross
        // something, so every waypoint that survives is a headland. Averaging over those asks
        // "how close are the corners to land", which has exactly one possible answer.
        val clearances = clearanceAlongLength(routeFor("Havana", "Bridgetown"))
        val median = clearances[clearances.size / 2]
        val hugging = clearances.count { it < 2 }.toFloat() / clearances.size
        assertTrue(median >= 3, "median clearance along a cross-map route is only $median cells")
        assertTrue(
            hugging < 0.1f,
            "${(hugging * 100).toInt()}% of the passage is within a cell of the beach"
        )
    }

    /** Clearance sampled every cell along the route's actual length, sorted. */
    private fun clearanceAlongLength(path: List<Vector2>): List<Int> {
        val samples = mutableListOf<Int>()
        for (index in 0 until path.size - 1) {
            val from = path[index]
            val to = path[index + 1]
            val steps = (from.dst(to) / 10f).toInt().coerceAtLeast(1)
            for (step in 0 until steps) {
                val along = step.toFloat() / steps
                samples.add(
                    worldMap.clearanceCellsAt(
                        from.x + (to.x - from.x) * along,
                        from.y + (to.y - from.y) * along
                    )
                )
            }
        }
        return samples.sorted()
    }

    private companion object {
        /** The watchdog's window and threshold, mirrored - see PortRoyaleGame. */
        const val STALL_WINDOW_SECONDS = 5f
        const val WATCHDOG_STUCK_FRACTION = 0.1f

        /**
         * How much longer than a straight run at full speed a real passage may take.
         *
         * Generous on purpose: a route round an island is longer than the line through it, and a
         * hull loses way in every turn. What it rules out is the crawl - three and four times the
         * honest passage time, which is what overshooting a waypoint every ten units costs.
         */
        const val CRAWL_ALLOWANCE = 2.5f

        /** Extra seconds allowed for coming round from a standing start with the bow ashore. */
        const val TURN_ALLOWANCE = 60f

        /** How far behind the headland the unreachable waypoint is put. */
        const val STALL_WAYPOINT_RANGE = 120f

        /** How long a hull may creep before the pilot is obliged to report it. Comfortably more
         *  than Pilot's own threshold, so the test measures that the report happens rather than
         *  precisely when. */
        const val STALL_REPORT_ALLOWANCE = 60f

        /** The longest tick over which the straight line between a hull's two positions is still
         *  the water it actually crossed - see [assertStayedOnWater]. */
        const val CHORD_IS_THE_PATH = 1f / 3f

        /** How much slower a passage may be when the world is stepped in long ticks instead of
         *  short ones. A hull's progress should be a property of the hull, not of the frame rate;
         *  some difference is honest, since a longer tick coarsens every turn. */
        const val FRAME_RATE_TOLERANCE = 1.6f
    }
}
