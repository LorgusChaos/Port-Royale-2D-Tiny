package com.portroyale.tiny.world

import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.factions.Faction.ENGLAND
import com.portroyale.tiny.factions.Faction.FRANCE
import com.portroyale.tiny.factions.Faction.NETHERLANDS
import com.portroyale.tiny.factions.Faction.SPAIN

/**
 * Real-world lat/lon; projected onto the map via MapProjection at load time. Faction is each
 * port's real 17th/18th-century colonial owner.
 *
 * Spain still holds the most, as it really did - but the roster is deliberately no longer 7/3/1/1.
 * Since M14 reputation runs off each faction's *share of your sales revenue*, a power with a
 * single port could only be kept friendly by routing a quarter of everything through that one
 * harbour, which made two of the four powers sink structurally rather than by the player's
 * choice. Every faction now has enough harbours for that to be a matter of routing.
 *
 * Ports are placed on their true coordinates even where the current relief image is too coarse to
 * draw the island underneath (the Lesser Antilles especially) - the map is slated for a redraw in
 * M16 with the land in the same places, so historical accuracy outlives the current artwork.
 * Nothing here may be renamed or removed: warehouses and captains' routes are addressed by port
 * NAME in save files.
 */
object Ports {
    data class Port(val name: String, val lat: Float, val lon: Float, val faction: Faction)

    val ALL = listOf(
        // --- Spain: the Main and the Greater Antilles ---
        Port("Havana", 23.13f, -82.35f, SPAIN),
        Port("Santo Domingo", 18.47f, -69.9f, SPAIN),
        Port("San Juan", 18.47f, -66.1f, SPAIN),
        Port("Cartagena", 10.4f, -75.5f, SPAIN),
        Port("Santiago de Cuba", 20.02f, -75.83f, SPAIN),
        Port("Veracruz", 19.2f, -96.13f, SPAIN),
        Port("Portobelo", 9.55f, -79.65f, SPAIN),
        Port("Campeche", 19.85f, -90.53f, SPAIN),
        Port("Trujillo", 15.92f, -85.95f, SPAIN),

        // --- England: Jamaica, the Bahamas, the Leewards and the logwood coast ---
        Port("Port Royal", 17.97f, -76.8f, ENGLAND),
        Port("Nassau", 25.05f, -77.35f, ENGLAND),
        Port("Bridgetown", 13.1f, -59.62f, ENGLAND),
        Port("St John's", 17.12f, -61.85f, ENGLAND),
        // Set on the outer anchorage rather than the river mouth itself: the relief image shows
        // the water inshore of the barrier reef as its own closed basin, so a marker on the town
        // is one no route can reach. Ships really did lie off the reef here. Worth revisiting
        // when the map is redrawn in M16.
        Port("Belize Town", 17.5f, -87.9f, ENGLAND),
        // English footholds inside Spain's western sea: the Bay Islands, the Mosquito Coast
        // protectorate and the old Puritan colony on Old Providence. They are what keeps the
        // west from being a solid Spanish wall - historically as well as on this map.
        Port("Roatan", 16.32f, -86.53f, ENGLAND),
        Port("Bluefields", 12.0f, -83.76f, ENGLAND),
        Port("Providencia", 13.35f, -81.37f, ENGLAND),

        // --- France: Saint-Domingue and the sugar islands ---
        Port("Port-au-Prince", 18.53f, -72.3f, FRANCE),
        Port("Cap-Francais", 19.76f, -72.2f, FRANCE),
        Port("Les Cayes", 18.19f, -73.75f, FRANCE),
        Port("Saint-Pierre", 14.74f, -61.18f, FRANCE),
        Port("Basse-Terre", 15.99f, -61.73f, FRANCE),
        Port("Grenada", 12.05f, -61.75f, FRANCE),

        // --- Netherlands: Curacao and Aruba, the mainland contraband coast, the Golden Rock,
        //     and Guiana. Bonaire is deliberately absent: Kralendijk sits close enough to
        //     Willemstad that the two read as one smudge on the map, and Tucacas gives the Dutch
        //     the same trade with room to breathe (see below).
        Port("Willemstad", 12.11f, -68.93f, NETHERLANDS),
        Port("Oranjestad", 12.52f, -70.03f, NETHERLANDS),
        // A real Dutch-Sephardic contraband settlement on the Venezuelan coast - the mainland end
        // of the same smuggling trade Curacao lived on, and the only Dutch foothold on the Main.
        Port("Tucacas", 10.8f, -68.32f, NETHERLANDS),
        Port("Sint Eustatius", 17.48f, -62.98f, NETHERLANDS),
        Port("Demerara", 6.8f, -58.16f, NETHERLANDS)
    )
}
