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
 * Six purchasable classes spanning small/fast to big/tough, each using a genuinely different
 * source sprite (not just a tint) from the pack's undamaged ship liveries, so they read as
 * distinct vessels rather than palette swaps - the gray/skull livery is deliberately skipped
 * here since it's reserved for pirate-flagged AI ships. Cutter trades cargo/hull for speed and
 * maneuverability rather than being strictly worse than the Sloop, for a real playstyle choice
 * rather than a pure upgrade ladder.
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
    val CUTTER = ShipType(
        name = "Cutter",
        spritePath = "sprites/ships/dinghyLarge1.png",
        spriteScale = 0.85f,
        tint = Color.WHITE,
        price = 900,
        cargoCapacity = 30,
        speed = 165f,
        maxHull = 70,
        minDamage = 8,
        maxDamage = 16,
        baseManeuverability = 95
    )
    val BARQUE = ShipType(
        name = "Barque",
        spritePath = "sprites/ships/ship_04.png",
        spriteScale = 1.1f,
        tint = Color.WHITE,
        price = 1800,
        cargoCapacity = 75,
        speed = 120f,
        maxHull = 130,
        minDamage = 18,
        maxDamage = 28,
        baseManeuverability = 58
    )
    val BRIGANTINE = ShipType(
        name = "Brigantine",
        spritePath = "sprites/ships/ship_05.png",
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
        spritePath = "sprites/ships/ship_03.png",
        spriteScale = 1.55f,
        tint = Color.WHITE,
        price = 6000,
        cargoCapacity = 160,
        speed = 95f,
        maxHull = 220,
        minDamage = 28,
        maxDamage = 45,
        baseManeuverability = 30
    )
    val MAN_OF_WAR = ShipType(
        name = "Man-of-War",
        spritePath = "sprites/ships/ship_06.png",
        spriteScale = 1.8f,
        tint = Color.WHITE,
        price = 11000,
        cargoCapacity = 210,
        speed = 80f,
        maxHull = 280,
        minDamage = 35,
        maxDamage = 55,
        baseManeuverability = 22
    )

    val ALL = listOf(SLOOP, CUTTER, BARQUE, BRIGANTINE, GALLEON, MAN_OF_WAR)
}
