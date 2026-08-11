package com.portroyale.tiny.world

import com.portroyale.tiny.ships.ShipTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ships used to turn instantly - heading was assigned outright from whichever way the next
 * waypoint lay, so a hundred and eighty degree reversal took one frame and read as a ship
 * stopping dead and spinning on the spot. Manoeuvrability was already a real statistic on every
 * class, used to decide who lands blows in a fight; it decides how fast a hull comes round too.
 */
class HelmTest {

    private fun secondsToReverse(maneuverability: Int) = 180f / Helm.turnRate(maneuverability)

    @Test
    fun `handier hulls come round faster`() {
        val cutter = ShipTypes.CUTTER.baseManeuverability
        val galleon = ShipTypes.GALLEON.baseManeuverability
        assertTrue(Helm.turnRate(cutter) > Helm.turnRate(galleon))
        assertTrue(
            secondsToReverse(galleon) > secondsToReverse(cutter) * 1.5f,
            "a Galleon should be markedly more ponderous than a Cutter"
        )
    }

    @Test
    fun `even the most sluggish hull still answers her helm`() {
        assertTrue(Helm.turnRate(0) > 0f, "a ship that cannot turn at all can never reach anything")
    }

    @Test
    fun `putting about takes a visible number of seconds for every class`() {
        // The complaint was that reversals were instantaneous. Anything under a second or two
        // would still look like a snap.
        for (type in ShipTypes.ALL) {
            val seconds = secondsToReverse(type.baseManeuverability)
            assertTrue(seconds > 2f, "${type.name} reverses in ${seconds}s, which still reads as a snap")
            assertTrue(seconds < 20f, "${type.name} takes ${seconds}s to come about, which is a derelict")
        }
    }

    // --- the arithmetic of turning ---

    @Test
    fun `a turn takes the short way round`() {
        assertEquals(-20f, Helm.shortestTurn(10f, -10f), 0.001f)
        // The long way round from 170 to -170 is 340 degrees; the short way is 20.
        assertEquals(20f, Helm.shortestTurn(170f, -170f), 0.001f)
        assertEquals(-20f, Helm.shortestTurn(-170f, 170f), 0.001f)
    }

    @Test
    fun `a heading moves towards its target and no further`() {
        assertEquals(10f, Helm.turnToward(0f, 90f, 10f), 0.001f)
        assertEquals(-10f, Helm.turnToward(0f, -90f, 10f), 0.001f)
    }

    @Test
    fun `a small correction lands exactly rather than overshooting`() {
        assertEquals(90f, Helm.turnToward(88f, 90f, 45f), 0.001f)
    }

    @Test
    fun `crossing the back of the compass does not send a ship the long way`() {
        // 175 to -175 is ten degrees across the wrap, or three hundred and fifty the other way.
        // A step too small to finish the turn shows which way it actually went.
        assertEquals(179f, Helm.turnToward(175f, -175f, 4f), 0.001f)
        assertEquals(-179f, Helm.turnToward(-175f, 175f, 4f), 0.001f)
        // And when the step is ample it lands exactly on the target rather than overshooting.
        assertEquals(-175f, Helm.turnToward(175f, -175f, 20f), 0.001f)
    }

    @Test
    fun `headings stay within a single turn of the compass`() {
        for (degrees in listOf(0f, 359f, 540f, -540f, 720f)) {
            val normalised = Helm.normalise(degrees)
            assertTrue(normalised in -180f..180f, "$degrees normalised to $normalised")
        }
    }

    // --- turning radius, and why arrival has to know about it ---

    @Test
    fun `a ponderous hull carves a wider circle than a handy one`() {
        val galleon = Helm.turningRadius(ShipTypes.GALLEON.speed, ShipTypes.GALLEON.baseManeuverability)
        val cutter = Helm.turningRadius(ShipTypes.CUTTER.speed, ShipTypes.CUTTER.baseManeuverability)
        assertTrue(galleon > 0f && cutter > 0f)
        assertTrue(galleon > cutter, "galleon $galleon, cutter $cutter")
    }

    @Test
    fun `losing way in a turn is what lets even a Galleon fetch up on its waypoints`() {
        // The alternative considered - and rejected - was letting sluggish hulls count a wider
        // miss as an arrival. That is special pleading dressed as physics. Slowing in a turn is
        // what actually happens, and it tightens the circle for free: at the pace a ship carries
        // through a hard turn, every class comes round inside the arrival radius.
        val arriveThreshold = 16f
        for (type in ShipTypes.ALL) {
            val hardOver = Helm.turningRadius(
                type.speed * Helm.paceThroughTurn(180f),
                type.baseManeuverability
            )
            assertTrue(
                hardOver < arriveThreshold,
                "${type.name} still needs $hardOver units to reverse, wider than arrival at $arriveThreshold"
            )
        }
    }

    @Test
    fun `a ship holds her speed on a straight course and loses it hard over`() {
        assertEquals(1f, Helm.paceThroughTurn(0f), 0.001f)
        assertTrue(Helm.paceThroughTurn(180f) < Helm.paceThroughTurn(90f))
        assertTrue(Helm.paceThroughTurn(90f) < Helm.paceThroughTurn(10f))
        // Slowed, never stopped: a ship that came to a halt to turn would be the same jerky
        // motion in a different disguise.
        assertTrue(Helm.paceThroughTurn(180f) > 0.2f)
    }

    @Test
    fun `the cost of a turn does not depend on which way it goes`() {
        assertEquals(Helm.paceThroughTurn(90f), Helm.paceThroughTurn(-90f), 0.001f)
    }
}
