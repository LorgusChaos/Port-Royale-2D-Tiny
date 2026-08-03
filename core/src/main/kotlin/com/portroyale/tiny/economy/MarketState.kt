package com.portroyale.tiny.economy

private const val TRADE_IMPACT = 0.06f
private const val DRIFT_PER_SECOND = 1f / 600f // a fully depleted good drifts back to neutral in ~10 min
private const val STOCK_SENSITIVITY = 0.3f
private const val NOTABLE_THRESHOLD = 0.15f
private const val BUY_SPREAD = 1.1f
private const val SELL_SPREAD = 0.9f
private const val MAX_NOTABLE_GOODS = 4

/**
 * The "current conditions" half of pricing, layered on top of [Market]'s fixed historical bias
 * rather than replacing it: each port/good pair has a stock level in [-1, 1] (0 = neutral) that
 * the player's own buying/selling nudges, and that drifts back toward neutral over time. That
 * drift stands in for the "background economy" (other traders, production, consumption) without
 * actually simulating any of it - a deliberately light illusion of a living market rather than a
 * full simulation, so trading still feels responsive without a pile of interacting parameters.
 */
object MarketState {

    private val stock = mutableMapOf<String, MutableMap<Good, Float>>()

    private fun stockAt(port: String, good: Good): Float = stock[port]?.get(good) ?: 0f

    private fun setStock(port: String, good: Good, value: Float) {
        stock.getOrPut(port) { mutableMapOf() }[good] = value.coerceIn(-1f, 1f)
    }

    /** > 1 when the port is short on this good (buying up stock pushes it here); < 1 when
     *  flooded with it. */
    fun dynamicMultiplier(port: String, good: Good): Float = 1f - stockAt(port, good) * STOCK_SENSITIVITY

    fun combinedMultiplier(port: String, good: Good): Float =
        Market.historicalMultiplier(port, good) * dynamicMultiplier(port, good)

    fun buyPrice(port: String, good: Good): Int =
        (good.basePrice * combinedMultiplier(port, good) * BUY_SPREAD).toInt().coerceAtLeast(1)

    fun sellPrice(port: String, good: Good): Int =
        (good.basePrice * combinedMultiplier(port, good) * SELL_SPREAD).toInt().coerceAtLeast(1)

    /** Buying a good depletes the port's stock of it (price rises); selling floods it (price
     *  falls) - the only two things that move stock other than the passive drift in [update]. */
    fun onBuy(port: String, good: Good) = setStock(port, good, stockAt(port, good) - TRADE_IMPACT)
    fun onSell(port: String, good: Good) = setStock(port, good, stockAt(port, good) + TRADE_IMPACT)

    fun update(delta: Float) {
        val step = DRIFT_PER_SECOND * delta
        for (goods in stock.values) {
            for (good in goods.keys.toList()) {
                val v = goods.getValue(good)
                goods[good] = when {
                    v > step -> v - step
                    v < -step -> v + step
                    else -> 0f
                }
            }
        }
    }

    /** Goods notably cheap (true = surplus) or notably expensive (false = shortage) at this
     *  port right now, capped so the map doesn't get cluttered with every good at once. */
    fun notableGoods(port: String): List<Pair<Good, Boolean>> =
        Good.entries.mapNotNull { good ->
            when {
                combinedMultiplier(port, good) <= 1f - NOTABLE_THRESHOLD -> good to true
                combinedMultiplier(port, good) >= 1f + NOTABLE_THRESHOLD -> good to false
                else -> null
            }
        }.take(MAX_NOTABLE_GOODS)

    fun reset() {
        stock.clear()
    }

    fun snapshot(): Map<String, Float> =
        stock.flatMap { (port, goods) -> goods.map { (good, v) -> "$port|${good.name}" to v } }.toMap()

    fun restore(data: Map<String, Float>) {
        stock.clear()
        for ((key, value) in data) {
            val parts = key.split("|")
            if (parts.size != 2) continue
            val good = Good.entries.find { it.name == parts[1] } ?: continue
            setStock(parts[0], good, value)
        }
    }
}
