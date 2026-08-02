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
 * Only neutral/merchant liveries here - pirate-flagged sprite variants are reserved for enemy
 * ships (see com.portroyale.tiny.combat.Enemies). The pack's "ship" sprites are all the same
 * pixel size, so bigger tiers are communicated via spriteScale + a warmer tint rather than a
 * larger source image.
 */
object ShipTypes {
    val SLOOP = ShipType(
        name = "Sloop",
        spritePath = "sprites/ships/ship_01.png",
        spriteScale = 1f,
        tint = Color.WHITE,
        price = 0,
        cargoCapacity = 50,
        speed = 130f,
        maxHull = 100,
        minDamage = 15,
        maxDamage = 25,
        baseManeuverability = 70
    )
    val BRIGANTINE = ShipType(
        name = "Brigantine",
        spritePath = "sprites/ships/ship_13.png",
        spriteScale = 1.25f,
        tint = Color.WHITE,
        price = 2500,
        cargoCapacity = 90,
        speed = 115f,
        maxHull = 150,
        minDamage = 20,
        maxDamage = 32,
        baseManeuverability = 50
    )
    val GALLEON = ShipType(
        name = "Galleon",
        spritePath = "sprites/ships/ship_01.png",
        spriteScale = 1.55f,
        tint = Color(0.85f, 0.75f, 0.55f, 1f),
        price = 6000,
        cargoCapacity = 160,
        speed = 95f,
        maxHull = 220,
        minDamage = 28,
        maxDamage = 45,
        baseManeuverability = 30
    )

    val ALL = listOf(SLOOP, BRIGANTINE, GALLEON)
}
