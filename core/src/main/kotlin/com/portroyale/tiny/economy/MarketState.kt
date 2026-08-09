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
    fun dynamicMultiplier(port: String, good: Good): Float = dynamicFor(stockAt(port, good))

    private fun dynamicFor(stock: Float): Float = 1f - stock * STOCK_SENSITIVITY

    fun combinedMultiplier(port: String, good: Good): Float =
        combinedFor(port, good, stockAt(port, good))

    private fun combinedFor(port: String, good: Good, stock: Float): Float =
        Market.historicalMultiplier(port, good) * dynamicFor(stock)

    private fun priceFor(good: Good, combined: Float, spread: Float): Int =
        (good.basePrice * combined * spread).toInt().coerceAtLeast(1)

    fun buyPrice(port: String, good: Good): Int =
        priceFor(good, combinedMultiplier(port, good), BUY_SPREAD)

    fun sellPrice(port: String, good: Good): Int =
        priceFor(good, combinedMultiplier(port, good), SELL_SPREAD)

    /** What each of [units] successive sales here would fetch, in order - every sale floods the
     *  port a little further (see [onSell]), so each price is a shade below the one before it.
     *  Nothing is actually sold; this is how a seller can find out what a bulk sale would REALLY
     *  return before committing to it, rather than reading one price off the board and assuming
     *  the whole hold clears at it. See FleetSimulation.attemptSell/attemptBuy. */
    fun projectedSellPrices(port: String, good: Good, units: Int): List<Int> {
        var stock = stockAt(port, good)
        return List(units) {
            val price = priceFor(good, combinedFor(port, good, stock), SELL_SPREAD)
            stock = (stock + TRADE_IMPACT).coerceIn(-1f, 1f)
            price
        }
    }

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

    /** Whether a good is notably cheap (true), notably expensive (false), or neither (null) at
     *  this port right now - the single-good version of [notableGoods], without its display
     *  cap, for screens (e.g. the market) that list every good and don't need decluttering. */
    fun notability(port: String, good: Good): Boolean? = when {
        combinedMultiplier(port, good) <= 1f - NOTABLE_THRESHOLD -> true
        combinedMultiplier(port, good) >= 1f + NOTABLE_THRESHOLD -> false
        else -> null
    }

    /** Goods notably cheap (true = surplus) or notably expensive (false = shortage) at this
     *  port right now, capped so the map doesn't get cluttered with every good at once. */
    fun notableGoods(port: String): List<Pair<Good, Boolean>> =
        Good.entries.mapNotNull { good -> notability(port, good)?.let { good to it } }.take(MAX_NOTABLE_GOODS)

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
