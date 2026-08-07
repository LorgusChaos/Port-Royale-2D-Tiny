package com.portroyale.tiny.ships

import com.badlogic.gdx.graphics.Color

data class ShipType(
    val name: String,
    val spritePath: String,
    val spriteScale: Float,
    val tint: Color,
    val price: Int,
    val cargoCapacity: Int,
    val speed: Float,
    val maxHull: Int,
    val minDamage: Int,
    val maxDamage: Int,
    val baseManeuverability: Int
)

/**
 * Five purchasable classes, each the matching tier (1-5) of a single coherent five-stage ship
 * progression (ship2_white_N.png - a small single-sail boat through a full tall ship), so the
 * lineup reads as one real ship growing up rather than unrelated hulls. Color is reserved for
 * faction identity on AI ships (see AiShipFactory) - the player always sails under white sails.
 */
object ShipTypes {
    val SLOOP = ShipType(
        name = "Sloop",
        spritePath = "sprites/ships/ship2_white_1.png",
        spriteScale = 0.9f,
        tint = Color.WHITE,
        price = 0,
        cargoCapacity = 50,
        speed = 13f,
        maxHull = 100,
        minDamage = 15,
        maxDamage = 25,
        baseManeuverability = 70
    )
    val CUTTER = ShipType(
        name = "Cutter",
        spritePath = "sprites/ships/ship2_white_2.png",
        spriteScale = 1.05f,
        tint = Color.WHITE,
        price = 900,
        cargoCapacity = 30,
        speed = 16.5f,
        maxHull = 70,
        minDamage = 8,
        maxDamage = 16,
        baseManeuverability = 95
    )
    val BARQUE = ShipType(
        name = "Barque",
        spritePath = "sprites/ships/ship2_white_3.png",
        spriteScale = 1.2f,
        tint = Color.WHITE,
        price = 1800,
        cargoCapacity = 75,
        speed = 12f,
        maxHull = 130,
        minDamage = 18,
        maxDamage = 28,
        baseManeuverability = 58
    )
    val BRIGANTINE = ShipType(
        name = "Brigantine",
        spritePath = "sprites/ships/ship2_white_4.png",
        spriteScale = 1.4f,
        tint = Color.WHITE,
        price = 2500,
        cargoCapacity = 90,
        speed = 11.5f,
        maxHull = 150,
        minDamage = 20,
        maxDamage = 32,
        baseManeuverability = 50
    )
    val GALLEON = ShipType(
        name = "Galleon",
        spritePath = "sprites/ships/ship2_white_5.png",
        spriteScale = 1.65f,
        tint = Color.WHITE,
        price = 6000,
        cargoCapacity = 160,
        speed = 9.5f,
        maxHull = 220,
        minDamage = 28,
        maxDamage = 45,
        baseManeuverability = 30
    )

    val ALL = listOf(SLOOP, CUTTER, BARQUE, BRIGANTINE, GALLEON)

    /** What's left after a defeat at sea (see PortRoyaleGame.finishCombat) - barely afloat, no
     *  fighting capability, just enough to limp to the nearest port. Deliberately NOT in [ALL]
     *  so it can never be bought from the shipyard; reuses the Sloop's sprite (smallest hull
     *  already in the game) rather than a bespoke dismasted-hull asset. AI ships are told never
     *  to engage a player sailing this. */
    val WRECK = ShipType(
        name = "Wreck",
        spritePath = "sprites/ships/ship2_white_1.png",
        spriteScale = 0.9f,
        tint = Color.WHITE,
        price = 0,
        cargoCapacity = 5,
        speed = 5f,
        maxHull = 30,
        minDamage = 0,
        maxDamage = 0,
        baseManeuverability = 20
    )
}
