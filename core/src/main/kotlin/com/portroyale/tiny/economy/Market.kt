package com.portroyale.tiny.economy

/**
 * One price per good per port (no separate buy/sell spread yet) - profit comes from buying at a
 * port that produces a good cheaply and selling where it's needed. Keyed by port name to match
 * com.portroyale.tiny.world.Ports.Port.name.
 */
object Market {
    private val multipliers: Map<String, Map<Good, Float>> = mapOf(
        "Havana" to mapOf(Good.TOBACCO to 0.6f, Good.WOOD to 0.7f, Good.SPICES to 1.4f),
        "Port Royal" to mapOf(Good.RUM to 0.6f, Good.COTTON to 1.3f, Good.SPICES to 1.3f),
        "Santo Domingo" to mapOf(Good.SUGAR to 0.6f, Good.TOBACCO to 1.3f),
        "San Juan" to mapOf(Good.COTTON to 0.6f, Good.RUM to 1.3f),
        "Cartagena" to mapOf(Good.SPICES to 0.7f, Good.WOOD to 1.4f),
        "Nassau" to mapOf(Good.WOOD to 0.6f, Good.SUGAR to 1.3f),
        "Port-au-Prince" to mapOf(Good.SUGAR to 0.65f, Good.COTTON to 0.7f, Good.SPICES to 1.3f)
    )

    fun priceAt(portName: String, good: Good): Int {
        val multiplier = multipliers[portName]?.get(good) ?: 1f
        return (good.basePrice * multiplier).toInt()
    }
}
