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
    val baseManeuverability: Int,
    /**
     * Whether anything in the world will attack a ship of this class at all.
     *
     * A property of the class rather than a comparison made at each place that needed it, and
     * that is the fix for a real bug: "nothing attacks the ship's boat" had been written twice,
     * once where a pursuit *begins* and once where combat *starts*, and the first of those only
     * ran while a ship was not already pursuing. So a pirate chasing the player at the moment
     * they lost kept its orders, closed to firing range, found no fight waiting - and simply
     * orbited the boat until its own give-up timer ran out. One rule in one place cannot be
     * half-applied that way.
     */
    val attackable: Boolean = true,
    /**
     * Extra degrees to turn the artwork by when drawing it, for a sprite whose bow does not
     * point up the image.
     *
     * Every hull in the ship set is drawn bow-up, and the renderer leans on that: it rotates a
     * sprite by the heading less ninety degrees and nothing else. The ship's boat comes from a
     * different, older set and is drawn bow-*down*, so it sailed stern first - measurably, not
     * arguably: the narrow end of the artwork is at the bottom of the image where every ship has
     * it at the top. Recording the orientation per class is the honest fix; mirroring the texture
     * would reverse an asymmetric hull, and repainting a CC0 asset to suit the renderer puts the
     * knowledge somewhere no one will find it again.
     */
    val spriteRotationDeg: Float = 0f
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

    /**
     * What a lost battle leaves: the ship's boat, and the clothes you stand up in.
     *
     * Not a damaged ship - a different thing altogether. No hold, so nothing can be carried and
     * nothing can be traded; no guns; the slowest thing afloat. The pirates take the ship, the
     * cargo and every coin in your purse and sail away, and what is left is just enough to reach
     * a harbour, where a hull and everything in it has to be bought again.
     *
     * Deliberately NOT in [ALL], so it can never be bought from a shipyard - and no NPC will
     * attack a player sailing it, because sinking a man already in his boat is not danger, it is
     * a dead end. This is the bottom of a game and it is meant to be reachable: the player should
     * fall into it once and learn that the game does not end there.
     */
    val LONGBOAT = ShipType(
        name = "Longboat",
        spritePath = "sprites/ships/dinghyLarge1.png",
        // Half the beam of the smallest ship in the game: on the map it has to read as a boat
        // among ships at a glance, with no label needed.
        spriteScale = 0.5f,
        tint = Color.WHITE,
        price = 0,
        cargoCapacity = 0,
        speed = 5f,
        maxHull = 30,
        minDamage = 0,
        maxDamage = 0,
        baseManeuverability = 20,
        attackable = false,
        spriteRotationDeg = 180f
    )
}
