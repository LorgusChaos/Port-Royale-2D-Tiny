package com.portroyale.tiny.economy

import com.portroyale.tiny.ships.ShipType
import com.portroyale.tiny.ships.ShipTypes

// TODO balance: bumped for testing so Brigantine/Galleon are affordable without a long grind;
// revisit for real economy balance once the core loop is confirmed working.
class Player(var gold: Int = 10000) {
    var ship: ShipType = ShipTypes.SLOOP
        private set

    val cargo = linkedMapOf<Good, Int>().apply {
        Good.entries.forEach { put(it, 0) }
    }

    val cargoCapacity: Int get() = ship.cargoCapacity
    val cargoUsed: Int get() = cargo.values.sum()

    fun canBuy(price: Int): Boolean = gold >= price && cargoUsed < cargoCapacity

    fun buy(good: Good, price: Int) {
        gold -= price
        cargo[good] = (cargo[good] ?: 0) + 1
    }

    fun canSell(good: Good): Boolean = (cargo[good] ?: 0) > 0

    fun sell(good: Good, price: Int) {
        gold += price
        cargo[good] = (cargo[good] ?: 0) - 1
    }

    fun canBuyShip(shipType: ShipType): Boolean = shipType != ship && gold >= shipType.price

    fun buyShip(shipType: ShipType) {
        gold -= shipType.price
        ship = shipType
    }

    /** Resets to a fresh captain's starting state, for New Game. */
    fun reset() {
        gold = 10000
        ship = ShipTypes.SLOOP
        Good.entries.forEach { cargo[it] = 0 }
    }

    /** Overwrites state from a loaded save, bypassing the buy/sell gold accounting. */
    fun loadState(gold: Int, ship: ShipType, cargo: Map<Good, Int>) {
        this.gold = gold
        this.ship = ship
        Good.entries.forEach { this.cargo[it] = cargo[it] ?: 0 }
    }
}
