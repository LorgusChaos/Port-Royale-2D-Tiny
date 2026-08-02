package com.portroyale.tiny.world

import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.factions.Faction.ENGLAND
import com.portroyale.tiny.factions.Faction.FRANCE
import com.portroyale.tiny.factions.Faction.NETHERLANDS
import com.portroyale.tiny.factions.Faction.SPAIN

/** Real-world lat/lon; projected onto the map via MapProjection at load time. Faction is each
 *  port's real 17th/18th-century colonial owner - lopsided toward Spain on purpose, since Spain
 *  really did hold most of the Caribbean coastline this map covers. */
object Ports {
    data class Port(val name: String, val lat: Float, val lon: Float, val faction: Faction)

    val ALL = listOf(
        Port("Havana", 23.13f, -82.35f, SPAIN),
        Port("Port Royal", 17.97f, -76.8f, ENGLAND),
        Port("Santo Domingo", 18.47f, -69.9f, SPAIN),
        Port("San Juan", 18.47f, -66.1f, SPAIN),
        Port("Cartagena", 10.4f, -75.5f, SPAIN),
        Port("Nassau", 25.05f, -77.35f, ENGLAND),
        Port("Port-au-Prince", 18.53f, -72.3f, FRANCE),
        Port("Santiago de Cuba", 20.02f, -75.83f, SPAIN),
        Port("Bridgetown", 13.1f, -59.62f, ENGLAND),
        Port("Willemstad", 12.11f, -68.93f, NETHERLANDS),
        Port("Veracruz", 19.2f, -96.13f, SPAIN),
        Port("Portobelo", 9.55f, -79.65f, SPAIN)
    )
}
