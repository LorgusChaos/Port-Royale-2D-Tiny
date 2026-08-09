package com.portroyale.tiny.ships

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShipTypesTest {

    @Test
    fun `the shipyard offers the full five-stage progression`() {
        assertEquals(5, ShipTypes.ALL.size)
        assertEquals(
            listOf("Sloop", "Cutter", "Barque", "Brigantine", "Galleon"),
            ShipTypes.ALL.map { it.name }
        )
    }

    @Test
    fun `a better ship always costs more than the one before it`() {
        val prices = ShipTypes.ALL.map { it.price }
        assertEquals(prices.sorted(), prices, "the shipyard list must read as a price ladder")
        assertEquals(0, ShipTypes.SLOOP.price, "the starting sloop is not for sale")
    }

    @Test
    fun `each class uses its own sprite from the shared five-tier set`() {
        val paths = ShipTypes.ALL.map { it.spritePath }
        assertEquals(paths.size, paths.distinct().size, "two classes share a sprite")
        ShipTypes.ALL.forEachIndexed { index, shipType ->
            assertEquals("sprites/ships/ship2_white_${index + 1}.png", shipType.spritePath)
        }
    }

    @Test
    fun `a bigger class is drawn bigger`() {
        val scales = ShipTypes.ALL.map { it.spriteScale }
        assertEquals(scales.sorted(), scales)
    }

    @Test
    fun `every purchasable ship has usable stats`() {
        for (shipType in ShipTypes.ALL) {
            assertTrue(shipType.maxHull > 0, "${shipType.name} has no hull")
            assertTrue(shipType.speed > 0f, "${shipType.name} cannot move")
            assertTrue(shipType.cargoCapacity > 0, "${shipType.name} cannot carry anything")
            assertTrue(shipType.minDamage > 0, "${shipType.name} cannot fight back")
            assertTrue(shipType.minDamage <= shipType.maxDamage, "${shipType.name} damage range is inverted")
            assertTrue(shipType.baseManeuverability > 0, "${shipType.name} cannot maneuver")
        }
    }

    @Test
    fun `no class is strictly better than another in every way`() {
        // The whole point of the ladder is trade-offs (a Cutter buys speed with hull and cargo);
        // a strictly dominant hull would collapse the choice into "buy the newest one".
        for (a in ShipTypes.ALL) {
            for (b in ShipTypes.ALL) {
                if (a === b) continue
                val dominates = a.price <= b.price &&
                    a.maxHull >= b.maxHull &&
                    a.speed >= b.speed &&
                    a.cargoCapacity >= b.cargoCapacity &&
                    a.maxDamage >= b.maxDamage &&
                    a.baseManeuverability >= b.baseManeuverability
                assertFalse(dominates, "${a.name} makes ${b.name} pointless")
            }
        }
    }

    @Test
    fun `a wreck cannot be bought from the shipyard`() {
        assertFalse(ShipTypes.ALL.contains(ShipTypes.WRECK))
    }

    @Test
    fun `a wreck can limp home but cannot fight`() {
        assertEquals(0, ShipTypes.WRECK.minDamage)
        assertEquals(0, ShipTypes.WRECK.maxDamage)
        assertTrue(ShipTypes.WRECK.speed > 0f, "a wreck must still be able to reach a port")
        assertTrue(ShipTypes.WRECK.maxHull > 0)
        assertTrue(ShipTypes.WRECK.speed < ShipTypes.ALL.minOf { it.speed }, "a wreck is the slowest thing afloat")
        assertTrue(
            ShipTypes.WRECK.cargoCapacity < ShipTypes.ALL.minOf { it.cargoCapacity },
            "a wreck holds less than any real ship"
        )
    }

    @Test
    fun `the galleon is the trading hull and the cutter the runner`() {
        assertEquals(ShipTypes.GALLEON, ShipTypes.ALL.maxByOrNull { it.cargoCapacity })
        assertEquals(ShipTypes.CUTTER, ShipTypes.ALL.maxByOrNull { it.speed })
        assertEquals(ShipTypes.CUTTER, ShipTypes.ALL.maxByOrNull { it.baseManeuverability })
    }
}
