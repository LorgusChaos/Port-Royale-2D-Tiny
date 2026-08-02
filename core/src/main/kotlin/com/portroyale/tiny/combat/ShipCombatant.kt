package com.portroyale.tiny.combat

data class ShipCombatant(
    val name: String,
    val maxHull: Int,
    val minDamage: Int,
    val maxDamage: Int
)

object Enemies {
    val PIRATE_SLOOP = ShipCombatant("Pirate Sloop", maxHull = 70, minDamage = 10, maxDamage = 20)
}
