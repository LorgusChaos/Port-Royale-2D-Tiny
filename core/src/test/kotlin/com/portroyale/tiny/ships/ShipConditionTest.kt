package com.portroyale.tiny.ships

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShipConditionTest {

    @Test
    fun `a new hull starts undamaged, unfouled and unupgraded`() {
        val condition = ShipCondition(ShipTypes.BARQUE.maxHull)
        assertEquals(ShipTypes.BARQUE.maxHull, condition.currentHull)
        assertEquals(0f, condition.fouling)
        assertEquals(0, condition.cannonLevel)
        assertEquals(0, condition.oarLevel)
        assertEquals(0, condition.sailLevel)
    }

    @Test
    fun `resetting wipes damage, wear and upgrades for the new hull`() {
        val condition = ShipCondition(100)
        condition.currentHull = 12
        condition.fouling = 0.9f
        condition.cannonLevel = 2
        condition.oarLevel = 1
        condition.sailLevel = 3

        condition.resetFor(ShipTypes.GALLEON.maxHull)

        assertEquals(ShipTypes.GALLEON.maxHull, condition.currentHull)
        assertEquals(0f, condition.fouling)
        assertEquals(0, condition.cannonLevel)
        assertEquals(0, condition.oarLevel)
        assertEquals(0, condition.sailLevel)
    }

    @Test
    fun `about fifteen minutes at sea fully fouls a hull`() {
        val condition = ShipCondition(100)
        condition.accumulateFouling(15 * 60f)
        assertEquals(1f, condition.fouling, 0.0001f)
    }

    @Test
    fun `fouling accumulates gradually and never exceeds fully fouled`() {
        val condition = ShipCondition(100)
        condition.accumulateFouling(60f)
        val afterOneMinute = condition.fouling
        assertTrue(afterOneMinute > 0f && afterOneMinute < 1f)

        condition.accumulateFouling(60f)
        assertTrue(condition.fouling > afterOneMinute)

        condition.accumulateFouling(100_000f)
        assertEquals(1f, condition.fouling)
    }

    @Test
    fun `fouling never goes negative`() {
        val condition = ShipCondition(100)
        condition.accumulateFouling(-1000f)
        assertEquals(0f, condition.fouling)
    }

    @Test
    fun `each upgrade tier costs more than the last, then stops being offered`() {
        val condition = ShipCondition(100)

        assertEquals(900, condition.cannonUpgradeCost())
        condition.cannonLevel = 1
        assertEquals(1800, condition.cannonUpgradeCost())
        condition.cannonLevel = 2
        assertEquals(2700, condition.cannonUpgradeCost())
        condition.cannonLevel = MAX_UPGRADE_LEVEL
        assertNull(condition.cannonUpgradeCost())
    }

    @Test
    fun `oars and sails follow the same rising cost curve`() {
        val condition = ShipCondition(100)
        assertEquals(500, condition.oarUpgradeCost())
        assertEquals(600, condition.sailUpgradeCost())

        condition.oarLevel = MAX_UPGRADE_LEVEL
        condition.sailLevel = MAX_UPGRADE_LEVEL
        assertNull(condition.oarUpgradeCost())
        assertNull(condition.sailUpgradeCost())
    }

    @Test
    fun `a level past the cap is still refused`() {
        val condition = ShipCondition(100)
        condition.cannonLevel = MAX_UPGRADE_LEVEL + 5
        assertNull(condition.cannonUpgradeCost())
    }

    @Test
    fun `repairs are billed per point of missing hull`() {
        val condition = ShipCondition(100)
        assertEquals(0, condition.repairCost(100), "an intact hull is free")

        condition.currentHull = 60
        assertEquals(160, condition.repairCost(100))
    }

    @Test
    fun `scrubbing the hull is a flat fee`() {
        val condition = ShipCondition(100)
        val clean = condition.cleanCost()
        condition.fouling = 1f
        assertEquals(clean, condition.cleanCost())
        assertTrue(clean > 0)
    }
}

class EffectiveStatsTest {

    @Test
    fun `an unupgraded, clean ship sails at exactly its class speed`() {
        val condition = ShipCondition(ShipTypes.SLOOP.maxHull)
        assertEquals(ShipTypes.SLOOP.speed, effectiveSpeed(ShipTypes.SLOOP, condition), 0.0001f)
    }

    @Test
    fun `each sail upgrade adds a flat bonus to speed`() {
        val condition = ShipCondition(ShipTypes.SLOOP.maxHull)
        condition.sailLevel = 2
        assertEquals(
            ShipTypes.SLOOP.speed + 2 * SAIL_SPEED_BONUS,
            effectiveSpeed(ShipTypes.SLOOP, condition),
            0.0001f
        )
    }

    @Test
    fun `a fully fouled hull loses a fixed share of its speed`() {
        val condition = ShipCondition(ShipTypes.SLOOP.maxHull)
        condition.fouling = 1f
        assertEquals(
            ShipTypes.SLOOP.speed * (1f - FOULING_SPEED_PENALTY),
            effectiveSpeed(ShipTypes.SLOOP, condition),
            0.0001f
        )
    }

    @Test
    fun `fouling eats into the upgraded speed, not the base speed`() {
        // The two effects are multiplicative on purpose: a maxed-out ship still loses 35% when
        // fouled, so upgrades never make maintenance irrelevant.
        val condition = ShipCondition(ShipTypes.SLOOP.maxHull)
        condition.sailLevel = MAX_UPGRADE_LEVEL
        condition.fouling = 1f
        val upgraded = ShipTypes.SLOOP.speed + MAX_UPGRADE_LEVEL * SAIL_SPEED_BONUS
        assertEquals(upgraded * (1f - FOULING_SPEED_PENALTY), effectiveSpeed(ShipTypes.SLOOP, condition), 0.0001f)
    }

    @Test
    fun `a fouled hull is always slower than a clean one`() {
        for (shipType in ShipTypes.ALL) {
            val clean = ShipCondition(shipType.maxHull)
            val fouled = ShipCondition(shipType.maxHull).apply { fouling = 0.5f }
            assertTrue(
                effectiveSpeed(shipType, fouled) < effectiveSpeed(shipType, clean),
                "${shipType.name} gains nothing from being fouled"
            )
        }
    }

    @Test
    fun `cannon upgrades raise both ends of the damage range equally`() {
        val condition = ShipCondition(ShipTypes.BARQUE.maxHull)
        condition.cannonLevel = 3
        val bonus = 3 * CANNON_DAMAGE_BONUS
        assertEquals(ShipTypes.BARQUE.minDamage + bonus, effectiveMinDamage(ShipTypes.BARQUE, condition))
        assertEquals(ShipTypes.BARQUE.maxDamage + bonus, effectiveMaxDamage(ShipTypes.BARQUE, condition))
    }

    @Test
    fun `damage never rolls backwards - min stays at or below max`() {
        for (shipType in ShipTypes.ALL + ShipTypes.WRECK) {
            for (level in 0..MAX_UPGRADE_LEVEL) {
                val condition = ShipCondition(shipType.maxHull).apply { cannonLevel = level }
                assertTrue(
                    effectiveMinDamage(shipType, condition) <= effectiveMaxDamage(shipType, condition),
                    "${shipType.name} at cannon level $level"
                )
            }
        }
    }

    @Test
    fun `oar upgrades add maneuverability`() {
        val condition = ShipCondition(ShipTypes.GALLEON.maxHull)
        condition.oarLevel = 2
        assertEquals(
            ShipTypes.GALLEON.baseManeuverability + 2 * OAR_MANEUVER_BONUS,
            effectiveManeuverability(ShipTypes.GALLEON, condition)
        )
    }

    @Test
    fun `maneuverability is unaffected by fouling`() {
        // Only speed is; if this ever changes, the flee-chance formula changes with it.
        val clean = ShipCondition(ShipTypes.SLOOP.maxHull)
        val fouled = ShipCondition(ShipTypes.SLOOP.maxHull).apply { fouling = 1f }
        assertEquals(
            effectiveManeuverability(ShipTypes.SLOOP, clean),
            effectiveManeuverability(ShipTypes.SLOOP, fouled)
        )
    }
}
