package com.portroyale.tiny.combat

data class ShipCombatant(
    val name: String,
    val maxHull: Int,
    val minDamage: Int,
    val maxDamage: Int,
    val spritePath: String
)

object Enemies {
    val PIRATE_SLOOP = ShipCombatant(
        name = "Pirate Sloop",
        maxHull = 70,
        minDamage = 10,
        maxDamage = 20,
        spritePath = "sprites/ships/ship_02.png"
    )
}
