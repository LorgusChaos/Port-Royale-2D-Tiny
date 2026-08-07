package com.portroyale.tiny.economy

import com.badlogic.gdx.math.MathUtils

private const val BASE_HIRE_COST = 2000
private const val HIRE_COST_SCALING = 2.75f
private const val TRADE_SENSE_RANGE = 0.10f
private const val CAUTION_MIN = 0.2f
private const val CAUTION_MAX = 0.9f

/** Placeholder leg duration for a captain who hasn't actually departed yet (idle candidates,
 *  and the brief moment before FleetSimulation.departFor stamps a real distance/speed-derived
 *  duration) - never drives movement itself, since legProgress is only ever sampled for captains
 *  who are !isIdle, and hire/assign always call departFor before anything can render. */
const val DEFAULT_LEG_MINUTES = 90f

private const val ESCAPE_CHANCE_BASE = 0.5f
private const val ESCAPE_CHANCE_PER_SPEED_RATIO = 1f
private const val ESCAPE_CHANCE_PER_CAUTION = 0.3f
private const val ESCAPE_CHANCE_MIN = 0.05f
private const val ESCAPE_CHANCE_MAX = 0.95f
private const val SPEED_RATIO_CLAMP = 0.5f

/** Real seconds after evading an encounter before the same captain can roll another one - see
 *  PortRoyaleGame.updateFleetEncounters. A short technical debounce (not a simulated game
 *  quantity, so it's real time regardless of GameSpeed), just enough that a threat still
 *  lingering in engage range next frame doesn't get re-rolled on every single frame. */
const val ENCOUNTER_COOLDOWN_SECONDS = 4f

private val CAPTAIN_FIRST_NAMES = listOf(
    "Isabela", "Mateo", "Constance", "Diego", "Margery", "Tomas", "Beatriz", "Jonas",
    "Verity", "Rafael", "Odalys", "Willem"
)
private val CAPTAIN_LAST_NAMES = listOf(
    "Vane", "Cortez", "Ashworth", "de Rojas", "Winters", "Almeida", "Fletcher", "Reyes",
    "Blackwood", "Serrano", "Hargreave", "Duval"
)

/** A hired captain, either idle (shipId == null - awaiting a ship, see FleetDockUI/TavernUI) or
 *  running an independent A<->B trade route while the player is elsewhere (shipId points at an
 *  OwnedShip - see Player.ownedShips, FleetSimulation). Deliberately abstracted rather than fully
 *  simulated: only a countdown to the next leg's arrival is tracked internally, not a physical
 *  path - PortRoyaleGame derives an on-screen position from portA/portB/progress purely for
 *  rendering (see drawFleetShips), so this doesn't become a second sailing engine.
 *
 *  The captain, not the player, decides what to carry (see FleetSimulation.attemptBuy/attemptSell)
 *  - [cargoGood]/[cargoUnits]/[cargoCostTotal] reflect whatever they're currently holding, not a
 *  player-set instruction. The player only sets [portA]/[portB]. */
class Captain(
    val name: String,
    val tradeSense: Float,
    val caution: Float,
    var shipId: Int? = null,
    var portA: String = "",
    var portB: String = "",
    var headingToB: Boolean = true,
    var cargoGood: Good? = null,
    var cargoUnits: Int = 0,
    var cargoCostTotal: Int = 0,
    var arrivesAtGameMinutes: Float = 0f,
    var legDurationGameMinutes: Float = DEFAULT_LEG_MINUTES,
    var lastReport: String = "Awaiting a ship and a route.",
    var totalProfit: Int = 0,
    var encounterCooldown: Float = 0f
) {
    val isIdle: Boolean get() = shipId == null

    /** How far through the current leg this captain is, 0..1 - purely for placing their ship
     *  icon on the map between portA/portB, not used by the simulation itself. */
    fun legProgress(currentGameMinutes: Float): Float {
        val remaining = (arrivesAtGameMinutes - currentGameMinutes) / legDurationGameMinutes
        return (1f - remaining).coerceIn(0f, 1f)
    }

    companion object {
        fun hireCost(existingCaptainCount: Int): Int =
            (BASE_HIRE_COST * Math.pow(HIRE_COST_SCALING.toDouble(), existingCaptainCount.toDouble())).toInt()

        /** Rolls a candidate's identity/stats only - no ship or route yet, since hiring now
         *  offers a choice of 3 candidates (see TavernUI) before either is committed to. */
        fun rollCandidate(): Captain {
            val name = "${CAPTAIN_FIRST_NAMES.random()} ${CAPTAIN_LAST_NAMES.random()}"
            val tradeSense = MathUtils.random(-TRADE_SENSE_RANGE, TRADE_SENSE_RANGE)
            val caution = MathUtils.random(CAUTION_MIN, CAUTION_MAX)
            return Captain(name = name, tradeSense = tradeSense, caution = caution)
        }
    }

    /** Chance to slip away once a real hostile ship is actually in range (see
     *  PortRoyaleGame.updateFleetEncounters) - grounded in the two real things that would matter:
     *  how this ship's speed compares to the threat's ([mySpeed]/[threatSpeed], both real
     *  ShipTypes/AiShip stats), and this captain's own [caution] (nerve to cut losses and run
     *  rather than freeze). No combat resolution in the MVP - failing to escape is always a total
     *  loss, same simplification Project design/03-combat-threat-model.md recommends. */
    fun escapeChance(mySpeed: Float, threatSpeed: Float): Float {
        val speedRatio = ((mySpeed - threatSpeed) / threatSpeed).coerceIn(-SPEED_RATIO_CLAMP, SPEED_RATIO_CLAMP)
        val cautionBonus = (caution - 0.5f) * ESCAPE_CHANCE_PER_CAUTION
        return (ESCAPE_CHANCE_BASE + speedRatio * ESCAPE_CHANCE_PER_SPEED_RATIO + cautionBonus)
            .coerceIn(ESCAPE_CHANCE_MIN, ESCAPE_CHANCE_MAX)
    }
}
