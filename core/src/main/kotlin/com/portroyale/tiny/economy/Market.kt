package com.portroyale.tiny.economy

/**
 * Each port's fixed historical price bias - the "identity" half of pricing, e.g. Portobelo is
 * always relatively cheap for silver. Keyed by port name to match
 * com.portroyale.tiny.world.Ports.Port.name. Specialties lean on real Caribbean-era trade history
 * (e.g. Portobelo/Veracruz as Spanish silver ports, Barbados as a sugar colony) for flavor. The
 * other half - current supply/demand, buy/sell spread - is [MarketState], which multiplies on
 * top of this baseline rather than replacing it.
 */
object Market {
    private val multipliers: Map<String, Map<Good, Float>> = mapOf(
        "Havana" to mapOf(
            Good.TOBACCO to 0.6f, Good.WOOD to 0.7f, Good.COFFEE to 0.7f,
            Good.SPICES to 1.4f, Good.CLOTH to 1.3f
        ),
        "Port Royal" to mapOf(
            Good.RUM to 0.6f,
            Good.COTTON to 1.3f, Good.SPICES to 1.3f, Good.SILVER to 1.3f
        ),
        "Santo Domingo" to mapOf(
            Good.SUGAR to 0.6f, Good.CACAO to 0.7f,
            Good.TOBACCO to 1.3f, Good.CLOTH to 1.2f
        ),
        "San Juan" to mapOf(
            Good.COTTON to 0.6f, Good.COFFEE to 0.7f,
            Good.RUM to 1.3f, Good.SILVER to 1.2f
        ),
        "Cartagena" to mapOf(
            Good.SPICES to 0.7f, Good.SILVER to 0.6f,
            Good.WOOD to 1.4f, Good.CLOTH to 1.3f
        ),
        "Nassau" to mapOf(
            Good.WOOD to 0.6f,
            Good.SUGAR to 1.3f, Good.COFFEE to 1.2f
        ),
        "Port-au-Prince" to mapOf(
            Good.SUGAR to 0.65f, Good.COTTON to 0.7f, Good.CACAO to 0.7f,
            Good.SPICES to 1.3f, Good.SILVER to 1.3f
        ),
        "Santiago de Cuba" to mapOf(
            Good.TOBACCO to 0.65f, Good.COFFEE to 0.6f,
            Good.CLOTH to 1.3f
        ),
        "Bridgetown" to mapOf(
            Good.SUGAR to 0.55f, Good.RUM to 0.65f,
            Good.SPICES to 1.3f
        ),
        "Willemstad" to mapOf(
            Good.CLOTH to 0.6f, Good.SILVER to 0.7f,
            Good.WOOD to 1.3f
        ),
        "Veracruz" to mapOf(
            Good.SILVER to 0.5f, Good.CACAO to 0.7f,
            Good.CLOTH to 1.2f
        ),
        "Portobelo" to mapOf(
            Good.SILVER to 0.45f,
            Good.TOBACCO to 1.3f, Good.CLOTH to 1.3f
        )
    )

    fun historicalMultiplier(portName: String, good: Good): Float = multipliers[portName]?.get(good) ?: 1f
}
