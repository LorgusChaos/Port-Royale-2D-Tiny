package com.portroyale.tiny.world

/** Real-world lat/lon; projected onto the map via MapProjection at load time. */
object Ports {
    data class Port(val name: String, val lat: Float, val lon: Float)

    val ALL = listOf(
        Port("Havana", 23.13f, -82.35f),
        Port("Port Royal", 17.97f, -76.8f),
        Port("Santo Domingo", 18.47f, -69.9f),
        Port("San Juan", 18.47f, -66.1f),
        Port("Cartagena", 10.4f, -75.5f),
        Port("Nassau", 25.05f, -77.35f),
        Port("Port-au-Prince", 18.53f, -72.3f),
        Port("Santiago de Cuba", 20.02f, -75.83f),
        Port("Bridgetown", 13.1f, -59.62f),
        Port("Willemstad", 12.11f, -68.93f),
        Port("Veracruz", 19.2f, -96.13f),
        Port("Portobelo", 9.55f, -79.65f)
    )
}
