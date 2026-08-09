package com.portroyale.tiny.economy

import com.portroyale.tiny.world.Ports
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MarketState is a singleton holding process-wide mutable stock, so every test starts from a
 *  cleared board - otherwise trades from one test would leak into the next one's prices. */
class MarketStateTest {

    @BeforeTest
    fun clearMarket() {
        MarketState.reset()
    }

    @Test
    fun `a port always buys lower than it sells`() {
        // The spread is what makes instant round-tripping in one port a loss - if it ever
        // inverted, the player could farm gold by buying and selling on the spot.
        for (port in Ports.ALL) {
            for (good in Good.entries) {
                assertTrue(
                    MarketState.buyPrice(port.name, good) > MarketState.sellPrice(port.name, good),
                    "${port.name}/${good.name}: buy must cost more than the port pays"
                )
            }
        }
    }

    @Test
    fun `buying up a port's stock drives its price up`() {
        val before = MarketState.buyPrice("Havana", Good.SPICES)
        repeat(10) { MarketState.onBuy("Havana", Good.SPICES) }
        assertTrue(MarketState.buyPrice("Havana", Good.SPICES) > before)
    }

    @Test
    fun `flooding a port with a good drives its price down`() {
        val before = MarketState.sellPrice("Havana", Good.SPICES)
        repeat(10) { MarketState.onSell("Havana", Good.SPICES) }
        assertTrue(MarketState.sellPrice("Havana", Good.SPICES) < before)
    }

    @Test
    fun `one port's trading does not move another port's prices`() {
        val elsewhere = MarketState.buyPrice("Nassau", Good.SUGAR)
        repeat(50) { MarketState.onBuy("Havana", Good.SUGAR) }
        assertEquals(elsewhere, MarketState.buyPrice("Nassau", Good.SUGAR))
    }

    @Test
    fun `stock is clamped so prices cannot run away`() {
        repeat(500) { MarketState.onBuy("Havana", Good.SUGAR) }
        // stock floors at -1, so the dynamic half of the price tops out at 1 + 1 * 0.3.
        assertEquals(1.3f, MarketState.dynamicMultiplier("Havana", Good.SUGAR), 0.0001f)

        repeat(1000) { MarketState.onSell("Havana", Good.SUGAR) }
        assertEquals(0.7f, MarketState.dynamicMultiplier("Havana", Good.SUGAR), 0.0001f)
    }

    @Test
    fun `stock drifts back toward neutral over time`() {
        repeat(10) { MarketState.onBuy("Havana", Good.SUGAR) }
        val disturbed = MarketState.dynamicMultiplier("Havana", Good.SUGAR)
        assertTrue(disturbed > 1f)

        MarketState.update(60f)
        val partlyRecovered = MarketState.dynamicMultiplier("Havana", Good.SUGAR)
        assertTrue(partlyRecovered < disturbed, "60s of drift should have moved it back")
        assertTrue(partlyRecovered > 1f, "60s is not enough to fully recover")
    }

    @Test
    fun `drift settles exactly on neutral instead of overshooting past it`() {
        repeat(5) { MarketState.onSell("Havana", Good.SUGAR) }
        MarketState.update(10_000f) // far more than enough to close the gap
        assertEquals(1f, MarketState.dynamicMultiplier("Havana", Good.SUGAR), 0.0001f)
    }

    @Test
    fun `drift on an untouched market changes nothing`() {
        val before = MarketState.buyPrice("Havana", Good.SUGAR)
        MarketState.update(600f)
        assertEquals(before, MarketState.buyPrice("Havana", Good.SUGAR))
    }

    @Test
    fun `combined multiplier is the historical bias times the current conditions`() {
        repeat(7) { MarketState.onBuy("Havana", Good.TOBACCO) }
        val expected = Market.historicalMultiplier("Havana", Good.TOBACCO) *
            MarketState.dynamicMultiplier("Havana", Good.TOBACCO)
        assertEquals(expected, MarketState.combinedMultiplier("Havana", Good.TOBACCO), 0.0001f)
    }

    @Test
    fun `a port's historical specialty reads as notably cheap`() {
        // Portobelo is the game's cheapest silver port (0.45x) - well past the 0.15 threshold.
        assertEquals(true, MarketState.notability("Portobelo", Good.SILVER))
    }

    @Test
    fun `a port's historically scarce good reads as notably expensive`() {
        assertEquals(false, MarketState.notability("Portobelo", Good.TOBACCO))
    }

    @Test
    fun `a good with no bias either way is not notable`() {
        assertNull(MarketState.notability("Nowhere", Good.SUGAR))
    }

    @Test
    fun `trading alone can make a good notable at a port with no historical bias`() {
        assertNull(MarketState.notability("Nowhere", Good.SUGAR))
        repeat(30) { MarketState.onSell("Nowhere", Good.SUGAR) } // saturate: flooded, so cheap
        assertEquals(true, MarketState.notability("Nowhere", Good.SUGAR))
    }

    @Test
    fun `the notable-goods list is capped so the map stays readable`() {
        listOf(Good.SUGAR, Good.RUM, Good.TOBACCO, Good.COTTON, Good.SPICES, Good.WOOD)
            .forEach { good -> repeat(30) { MarketState.onSell("Nowhere", good) } }
        assertEquals(4, MarketState.notableGoods("Nowhere").size)
    }

    @Test
    fun `notable goods agree with the single-good notability check`() {
        repeat(30) { MarketState.onSell("Havana", Good.SILVER) }
        for ((good, cheap) in MarketState.notableGoods("Havana")) {
            assertEquals(MarketState.notability("Havana", good), cheap)
        }
    }

    @Test
    fun `a snapshot survives a round trip through restore`() {
        repeat(4) { MarketState.onBuy("Havana", Good.SUGAR) }
        repeat(9) { MarketState.onSell("Nassau", Good.RUM) }
        val saved = MarketState.snapshot()
        val havanaBefore = MarketState.buyPrice("Havana", Good.SUGAR)
        val nassauBefore = MarketState.sellPrice("Nassau", Good.RUM)

        MarketState.reset()
        MarketState.restore(saved)

        assertEquals(havanaBefore, MarketState.buyPrice("Havana", Good.SUGAR))
        assertEquals(nassauBefore, MarketState.sellPrice("Nassau", Good.RUM))
    }

    @Test
    fun `restore keeps the port and good apart in the composite key`() {
        repeat(4) { MarketState.onBuy("Port Royal", Good.SUGAR) } // a port name containing a space
        val saved = MarketState.snapshot()
        MarketState.reset()
        MarketState.restore(saved)
        assertTrue(MarketState.dynamicMultiplier("Port Royal", Good.SUGAR) > 1f)
    }

    @Test
    fun `restore ignores malformed and unknown entries instead of crashing`() {
        MarketState.restore(
            mapOf(
                "Havana|SUGAR" to -0.5f,
                "no-separator" to 0.4f,
                "Havana|NOT_A_GOOD" to 0.4f,
                "Havana|SUGAR|extra" to 0.4f
            )
        )
        assertTrue(MarketState.dynamicMultiplier("Havana", Good.SUGAR) > 1f)
    }

    @Test
    fun `restore clamps out-of-range values from a tampered save`() {
        MarketState.restore(mapOf("Havana|SUGAR" to 99f))
        assertEquals(0.7f, MarketState.dynamicMultiplier("Havana", Good.SUGAR), 0.0001f)
    }

    @Test
    fun `reset clears the board back to neutral`() {
        repeat(20) { MarketState.onBuy("Havana", Good.SUGAR) }
        MarketState.reset()
        assertEquals(1f, MarketState.dynamicMultiplier("Havana", Good.SUGAR), 0.0001f)
        assertTrue(MarketState.snapshot().isEmpty())
    }

    @Test
    fun `prices never drop to zero or below`() {
        for (port in Ports.ALL) {
            for (good in Good.entries) {
                repeat(30) { MarketState.onSell(port.name, good) }
                assertTrue(MarketState.sellPrice(port.name, good) >= 1, "${port.name}/${good.name}")
                assertTrue(MarketState.buyPrice(port.name, good) >= 1, "${port.name}/${good.name}")
            }
        }
    }

    @Test
    fun `a snapshot only records ports that were actually traded in`() {
        MarketState.onBuy("Havana", Good.SUGAR)
        val keys = MarketState.snapshot().keys
        assertEquals(1, keys.size)
        assertNotNull(keys.firstOrNull { it.startsWith("Havana|") })
    }
}
