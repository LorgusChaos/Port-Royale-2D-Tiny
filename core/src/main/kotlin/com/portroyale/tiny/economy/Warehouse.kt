package com.portroyale.tiny.economy

const val WAREHOUSE_PURCHASE_PRICE = 1500
const val WAREHOUSE_BASE_CAPACITY = 100
const val WAREHOUSE_EXPANSION_CAPACITY = 50
const val WAREHOUSE_MAX_LEVEL = 3
private const val WAREHOUSE_EXPANSION_BASE_COST = 800

/** One port's private storage: goods and gold left behind for safekeeping (defeat only costs
 *  what's on the ship - see PortRoyaleGame's defeat handling - so a warehouse is also a hedge
 *  against losing everything to pirates). Starts at level 1 the moment it's purchased; each
 *  further level adds capacity for a rising price. */
class Warehouse(var level: Int = 1) {
    val goods = linkedMapOf<Good, Int>().apply { Good.entries.forEach { put(it, 0) } }
    var gold: Int = 0

    val capacity: Int get() = WAREHOUSE_BASE_CAPACITY + (level - 1) * WAREHOUSE_EXPANSION_CAPACITY
    val goodsUsed: Int get() = goods.values.sum()

    fun expansionCost(): Int? =
        if (level >= WAREHOUSE_MAX_LEVEL) null else WAREHOUSE_EXPANSION_BASE_COST * level
}
