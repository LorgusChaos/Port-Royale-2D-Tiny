package com.portroyale.tiny.economy

import com.portroyale.tiny.world.Ports
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarketTest {

    /** MarketState's stock is process-wide, so clear whatever an earlier test class left behind
     *  before reading any price here. */
    @BeforeTest
    fun clearMarket() {
        MarketState.reset()
    }

    @Test
    fun `every port on the map has at least one historical specialty`() {
        // Market keys its table by port NAME, so a typo there fails silently: the port simply
        // loses its identity and prices like anywhere else. This is the guard against that.
        for (port in Ports.ALL) {
            val biased = Good.entries.filter { Market.historicalMultiplier(port.name, it) != 1f }
            assertTrue(biased.isNotEmpty(), "${port.name} has no price bias - name mismatch with Market's table?")
        }
    }

    @Test
    fun `every port has something worth buying there`() {
        // A port whose only bias is "expensive" would be a pure trap - nothing to load up on.
        for (port in Ports.ALL) {
            val cheap = Good.entries.filter { Market.historicalMultiplier(port.name, it) < 1f }
            assertTrue(cheap.isNotEmpty(), "${port.name} sells nothing at a discount")
        }
    }

    @Test
    fun `historical multipliers stay within a sane band`() {
        for (port in Ports.ALL) {
            for (good in Good.entries) {
                val m = Market.historicalMultiplier(port.name, good)
                assertTrue(m in 0.4f..1.6f, "${port.name}/${good.name} multiplier $m is out of band")
            }
        }
    }

    @Test
    fun `a port not on the map has no bias at all`() {
        for (good in Good.entries) {
            assertEquals(1f, Market.historicalMultiplier("Atlantis", good))
        }
    }

    @Test
    fun `a good a port has no opinion about prices neutrally`() {
        assertEquals(1f, Market.historicalMultiplier("Havana", Good.SILVER))
    }

    @Test
    fun `the silver ports really are the cheapest place to buy silver`() {
        // Flavor the design leans on (Portobelo and Veracruz as the Spanish silver ports);
        // if a rebalance ever breaks it, the trade routes built around it break with it.
        val cheapest = Ports.ALL.minByOrNull { Market.historicalMultiplier(it.name, Good.SILVER) }
        assertEquals("Portobelo", cheapest?.name)
        assertTrue(Market.historicalMultiplier("Veracruz", Good.SILVER) < 1f)
    }

    @Test
    fun `at least one profitable route exists for every good`() {
        // Somewhere must buy each good high enough to beat the cheapest port's asking price,
        // or that good is dead cargo no captain (or player) would ever carry.
        for (good in Good.entries) {
            val cheapest = Ports.ALL.minOf { MarketState.buyPrice(it.name, good) }
            val dearest = Ports.ALL.maxOf { MarketState.sellPrice(it.name, good) }
            assertTrue(dearest > cheapest, "${good.name} cannot be profitably traded anywhere")
        }
    }
}
