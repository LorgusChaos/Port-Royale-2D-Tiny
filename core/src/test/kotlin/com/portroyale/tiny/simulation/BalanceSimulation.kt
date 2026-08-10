package com.portroyale.tiny.simulation

import com.portroyale.tiny.diagnostics.GameRandom
import com.portroyale.tiny.economy.Captain
import com.portroyale.tiny.economy.FleetSimulation
import com.portroyale.tiny.economy.MarketState
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.ships.ShipTypes
import com.portroyale.tiny.world.MapProjection
import com.portroyale.tiny.world.Ports
import org.junit.jupiter.api.Tag
import java.io.File
import kotlin.test.Test

/**
 * A headless run of the trading economy: no window, no rendering, a few hundred game-days in a
 * second or so.
 *
 * This exists for M15. Balancing by playing means an hour per data point and a memory of how it
 * felt; balancing against a printed curve means changing one constant and seeing where the whole
 * thing lands. It is not a test - nothing here asserts a "correct" answer, because what the right
 * answer *is* is exactly what M15 has to decide - so it is tagged out of the normal suite and run
 * on demand:
 *
 *     ./gradlew.bat :core:simulate
 *
 * Output goes to the console and to core/build/simulation/ as CSV.
 *
 * What it models: hired captains running port-to-port routes, buying and selling against the real
 * market with real price impact and drift, moving reputation exactly as they do in game. What it
 * does not model: the player's own trading, combat losses, or warehouses. Those are the parts a
 * human decides moment to moment, and a simulated stand-in for them would be inventing the very
 * behaviour the numbers are meant to measure.
 */
@Tag("simulation")
class BalanceSimulation {

    private val gameMinutesPerStep = 30f
    private val realSecondsPerStep = gameMinutesPerStep * 6f // GameClock runs 10x: 1 game min = 6s

    /**
     * The axis is REAL hours at the keyboard, not game days, and the difference is not cosmetic:
     * the clock runs ten times faster than life, so one game day is 2.4 hours of play. Plotting
     * game days would put "200 days" at twenty real days of continuous sailing, and every
     * threshold read off it would be wrong by a factor of 144. Every target in
     * Project design/01-progression.md is stated in hours played, so this matches it.
     */
    private val gameMinutesPerRealHour = 600f

    /** Wealth thresholds from 10-game-vision.md 7.1, so a run says which stage it reached.
     *  Latin here on purpose - the Windows console mangles Cyrillic, and an unreadable table is
     *  the one thing this tool cannot afford to produce. */
    private fun wealthTitle(gold: Int): String = when {
        gold < 100_000 -> "commoner"
        gold < 500_000 -> "merchant"
        gold < 2_000_000 -> "magnate"
        else -> "BARON(cap)"
    }

    @Test
    fun `captain-run trade network over 30 hours of play`() {
        val realHours = 30
        val seed = 20260810L
        val rows = mutableListOf<String>()
        rows.add("hours,gold,title,captains,ships,${Faction.entries.joinToString(",") { it.name.lowercase() }}")

        GameRandom.withSeed(seed) {
            MarketState.reset()
            val player = Player(10_000)
            val distances = seaDistances()

            println("=== captain-run trade network, seed $seed ===")
            println("hours |      gold | title      | cpt | " + Faction.entries.joinToString(" | ") { it.name.take(3) })

            var minutes = 0f
            val totalSteps = (realHours * gameMinutesPerRealHour / gameMinutesPerStep).toInt()
            var lastReportedHour = -1

            repeat(totalSteps) {
                minutes += gameMinutesPerStep
                MarketState.update(realSecondsPerStep)
                growFleet(player, minutes, distances)
                FleetSimulation.update(player, minutes, distances)

                val hour = (minutes / gameMinutesPerRealHour).toInt()
                if (hour != lastReportedHour && hour % 2 == 0) {
                    lastReportedHour = hour
                    val reputations = Faction.entries.map { player.reputation[it] ?: 0 }
                    println(
                        "%5d | %9d | %-10s | %3d | ".format(
                            hour, player.gold, wealthTitle(player.gold), player.captains.count { !it.isIdle }
                        ) + reputations.joinToString(" | ") { "%3d".format(it) }
                    )
                    rows.add(
                        "$hour,${player.gold},${wealthTitle(player.gold)}," +
                            "${player.captains.count { !it.isIdle }},${player.ownedShips.size}," +
                            reputations.joinToString(",")
                    )
                }
            }

            println()
            println("after $realHours hours of play:")
            println("  gold:      ${player.gold} (${wealthTitle(player.gold)})")
            println("  captains:  ${player.captains.size} (lifetime profit ${player.captains.sumOf { it.totalProfit }})")
            println("  ships:     ${player.ownedShips.size}")
            println("  standing:  " + Faction.entries.joinToString(", ") { "${it.displayName} ${player.reputation[it]}" })
            println("  influence ceiling (3 factions at +50): " +
                (player.reputation.values.count { it >= 50 } >= 3))
        }

        val out = File("build/simulation")
        out.mkdirs()
        val csv = File(out, "fleet-economy.csv")
        csv.writeText(rows.joinToString("\n"))
        println()
        println("csv: ${csv.absolutePath}")
    }

    /** Buys a hull and hires a captain whenever both are comfortably affordable - a stand-in for
     *  a player who reinvests, which is what the progression document assumes they do. */
    private fun growFleet(player: Player, minutes: Float, distances: (String, String) -> Float) {
        val hireCost = Captain.hireCost(player.captains.size)
        val shipCost = ShipTypes.BARQUE.price
        if (player.captains.size >= 4) return
        if (player.gold < (hireCost + shipCost) * 2) return

        player.buyShip(ShipTypes.BARQUE, "Trader ${player.ownedShips.size}", HOME_PORT)
        val free = player.ownedShips.firstOrNull { FleetSimulation.isShipFree(player, it.id) } ?: return
        val route = ROUTES[player.captains.size % ROUTES.size]
        FleetSimulation.hire(player, Captain.rollCandidate(), free.id, route.first, route.second, minutes, distances)
    }

    /** Straight-line distance between ports. The real game uses a sea route around the coast, so
     *  legs here are optimistic - fine for comparing constants against each other, which is what
     *  this is for, and noted so nobody reads the absolute day counts as gospel. */
    private fun seaDistances(): (String, String) -> Float = { a, b ->
        val pa = Ports.ALL.first { it.name == a }
        val pb = Ports.ALL.first { it.name == b }
        MapProjection.project(pa.lat, pa.lon).dst(MapProjection.project(pb.lat, pb.lon))
    }

    private companion object {
        const val HOME_PORT = "Havana"
        /** Deliberately spread across factions - a single-faction network would measure the
         *  reputation mechanic's best case rather than a plausible one. */
        val ROUTES = listOf(
            "Havana" to "Portobelo",
            "Port Royal" to "Cartagena",
            "Saint-Pierre" to "Bridgetown",
            "Willemstad" to "Santo Domingo"
        )
    }
}
