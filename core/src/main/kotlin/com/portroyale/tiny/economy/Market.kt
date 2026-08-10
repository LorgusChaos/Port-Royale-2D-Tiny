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
        ),

        // --- added with the M14b roster expansion ---

        // Antigua: sugar island, and stripped of its own timber to feed the cane boilers.
        "St John's" to mapOf(
            Good.SUGAR to 0.6f, Good.RUM to 0.7f,
            Good.WOOD to 1.4f, Good.SPICES to 1.3f
        ),
        // The logwood and mahogany coast - the colony's whole reason to exist.
        "Belize Town" to mapOf(
            Good.WOOD to 0.45f,
            Good.SUGAR to 1.3f, Good.CLOTH to 1.3f, Good.SILVER to 1.2f
        ),
        // Saint-Domingue's northern capital: the richest sugar and coffee colony on earth.
        "Cap-Francais" to mapOf(
            Good.COFFEE to 0.55f, Good.SUGAR to 0.65f,
            Good.WOOD to 1.3f, Good.SILVER to 1.3f
        ),
        // Martinique: sugar and, above all, rum.
        "Saint-Pierre" to mapOf(
            Good.RUM to 0.55f, Good.SUGAR to 0.7f,
            Good.WOOD to 1.3f, Good.SILVER to 1.2f
        ),
        // Guadeloupe: sugar and coffee, everything manufactured shipped in.
        "Basse-Terre" to mapOf(
            Good.SUGAR to 0.65f, Good.COFFEE to 0.7f,
            Good.CLOTH to 1.3f, Good.SILVER to 1.2f
        ),
        // Aruba: arid, no plantations - a Dutch trading post living off shipped-in cloth.
        "Oranjestad" to mapOf(
            Good.CLOTH to 0.65f,
            Good.WOOD to 1.4f, Good.SUGAR to 1.3f
        ),
        // Tucacas: a contraband post where Dutch cloth met Venezuelan cacao, both moving without
        // troubling any customs house.
        "Tucacas" to mapOf(
            Good.CACAO to 0.6f, Good.CLOTH to 0.7f,
            Good.SILVER to 1.3f, Good.SPICES to 1.25f
        ),
        // Sint Eustatius, the "Golden Rock": a free-port entrepot where everything passed
        // through cheaply and nothing was actually grown.
        "Sint Eustatius" to mapOf(
            Good.CLOTH to 0.6f, Good.SPICES to 0.65f, Good.TOBACCO to 0.75f,
            Good.SUGAR to 1.3f, Good.RUM to 1.2f
        ),

        // --- filling out the western and southern reaches of the map ---

        // Campeche shipped the dyewood the whole Yucatan was logged for.
        "Campeche" to mapOf(
            Good.WOOD to 0.5f, Good.COTTON to 0.75f,
            Good.CLOTH to 1.3f, Good.SPICES to 1.3f
        ),
        // Honduras: cacao country, and timber behind it.
        "Trujillo" to mapOf(
            Good.CACAO to 0.55f, Good.WOOD to 0.7f,
            Good.CLOTH to 1.35f, Good.RUM to 1.2f
        ),
        // A turtling and smuggling base in the Bay Islands - it grows nothing, everything
        // manufactured has to arrive by sea.
        "Roatan" to mapOf(
            Good.WOOD to 0.65f,
            Good.CLOTH to 1.4f, Good.SPICES to 1.35f, Good.SILVER to 1.2f
        ),
        // The Mosquito Coast: mahogany and cacao, and nothing else within a week's sail.
        "Bluefields" to mapOf(
            Good.WOOD to 0.55f, Good.CACAO to 0.7f,
            Good.CLOTH to 1.35f, Good.SILVER to 1.3f
        ),
        // Old Providence grew cotton and little besides - an isolated colony paying dearly
        // for everything it could not raise itself.
        "Providencia" to mapOf(
            Good.COTTON to 0.55f,
            Good.SPICES to 1.4f, Good.CLOTH to 1.35f, Good.SILVER to 1.3f
        ),
        // Southern Saint-Domingue: the same sugar and coffee wealth as the north coast.
        "Les Cayes" to mapOf(
            Good.SUGAR to 0.6f, Good.COFFEE to 0.65f,
            Good.CLOTH to 1.3f, Good.SILVER to 1.25f
        ),
        // Grenada under the French: sugar and cacao.
        "Grenada" to mapOf(
            Good.CACAO to 0.6f, Good.SUGAR to 0.7f,
            Good.WOOD to 1.3f, Good.SILVER to 1.2f
        ),
        // Demerara cotton was famous enough to name the cloth; sugar behind it, and every
        // manufactured thing shipped in across the whole map.
        "Demerara" to mapOf(
            Good.COTTON to 0.5f, Good.SUGAR to 0.7f,
            Good.CLOTH to 1.35f, Good.SPICES to 1.3f, Good.SILVER to 1.3f
        )
    )

    fun historicalMultiplier(portName: String, good: Good): Float = multipliers[portName]?.get(good) ?: 1f
}
