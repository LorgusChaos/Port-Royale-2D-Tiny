package com.portroyale.tiny.combat

data class ShipCombatant(
    val name: String,
    val maxHull: Int,
    val minDamage: Int,
    val maxDamage: Int,
    val spritePath: String
)
