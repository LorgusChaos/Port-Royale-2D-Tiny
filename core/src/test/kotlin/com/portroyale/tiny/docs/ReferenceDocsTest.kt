package com.portroyale.tiny.docs

import com.portroyale.tiny.economy.Captain
import com.portroyale.tiny.economy.Good
import com.portroyale.tiny.economy.LOAN_OFFERS
import com.portroyale.tiny.economy.WAREHOUSE_BASE_CAPACITY
import com.portroyale.tiny.economy.WAREHOUSE_EXPANSION_CAPACITY
import com.portroyale.tiny.economy.WAREHOUSE_MAX_LEVEL
import com.portroyale.tiny.economy.WAREHOUSE_PURCHASE_PRICE
import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.factions.MilitaryStance
import com.portroyale.tiny.factions.REPUTATION_MAX
import com.portroyale.tiny.factions.REPUTATION_MIN
import com.portroyale.tiny.factions.ReputationTier
import com.portroyale.tiny.factions.buyPriceMultiplier
import com.portroyale.tiny.factions.militaryStance
import com.portroyale.tiny.factions.reputationTier
import com.portroyale.tiny.factions.sellPriceMultiplier
import com.portroyale.tiny.ships.CANNON_DAMAGE_BONUS
import com.portroyale.tiny.ships.FOULING_SPEED_PENALTY
import com.portroyale.tiny.ships.MAX_UPGRADE_LEVEL
import com.portroyale.tiny.ships.OAR_MANEUVER_BONUS
import com.portroyale.tiny.ships.SAIL_SPEED_BONUS
import com.portroyale.tiny.ships.ShipCondition
import com.portroyale.tiny.ships.ShipType
import com.portroyale.tiny.ships.ShipTypes
import java.io.File
import org.junit.jupiter.api.Tag
import kotlin.test.Test

/**
 * Writes the numeric tables of `Reference/` straight out of the code they describe.
 *
 * It is a test only because that is the cheapest way to get a JVM with the game on its classpath
 * (the same trick `BalanceSimulation` uses) - it asserts nothing about behaviour. It is tagged
 * `reference` and excluded from `:core:test`, so an ordinary test run never rewrites files;
 * `./gradlew.bat :core:reference` is what regenerates them.
 *
 * Only the regions between `<!-- generated:key -->` and `<!-- /generated:key -->` are touched.
 * Everything a human wrote around them - what a ship looks like, why a number is what it is -
 * is left exactly as it was, which is the whole point of the split: the facts that can go stale
 * are machine-written, and the facts that cannot be derived are hand-written.
 */
@Tag("reference")
class ReferenceDocsTest {

    private val referenceDir = File("../Reference")

    /** Every generated region: the file it lives in, its key, and what the code says it holds. */
    internal fun generatedBlocks(): List<Triple<File, String, String>> {
        val ships = File(referenceDir, "ships.md")
        val economy = File(referenceDir, "goods-and-economy.md")
        return listOf(
            Triple(ships, "hulls", hullTable()),
            Triple(ships, "longboat", longboatBlock()),
            Triple(ships, "upgrades", upgradeTable()),
            Triple(economy, "goods", goodsTable()),
            Triple(economy, "reputation", reputationTable()),
            Triple(economy, "warehouse", warehouseBlock()),
            Triple(economy, "loans", loanTable()),
            Triple(economy, "captains", captainBlock()),
        )
    }

    @Test
    fun `regenerate the reference tables`() {
        for ((file, key, body) in generatedBlocks()) writeBlock(file, key, body)
        println("reference docs regenerated in ${referenceDir.canonicalPath}")
    }

    // --- the tables ------------------------------------------------------------------------

    private fun hullTable(): String = table(
        listOf("Class", "Price", "Cargo", "Speed", "Hull", "Damage", "Manoeuvre", "Sprite"),
        ShipTypes.ALL.map { ship ->
            listOf(
                ship.name,
                if (ship.price == 0) "start" else "${ship.price}g",
                ship.cargoCapacity.toString(),
                trim(ship.speed),
                ship.maxHull.toString(),
                "${ship.minDamage}-${ship.maxDamage}",
                ship.baseManeuverability.toString(),
                spriteName(ship),
            )
        },
    )

    private fun longboatBlock(): String {
        val boat = ShipTypes.LONGBOAT
        return "`${boat.name}` - cargo ${boat.cargoCapacity}, speed ${trim(boat.speed)}, " +
            "hull ${boat.maxHull}, damage ${boat.minDamage}-${boat.maxDamage}, " +
            "manoeuvre ${boat.baseManeuverability}, sprite `${spriteName(boat)}` " +
            "drawn at ${trim(boat.spriteScale)}x. Not in `ShipTypes.ALL`, so no shipyard sells it."
    }

    private fun upgradeTable(): String {
        val costs = { base: (ShipCondition) -> Int? ->
            (0 until MAX_UPGRADE_LEVEL).map { level ->
                val condition = ShipCondition(100)
                condition.cannonLevel = level
                condition.oarLevel = level
                condition.sailLevel = level
                base(condition)?.let { "${it}g" } ?: "-"
            }
        }
        val rows = listOf(
            listOf("Cannons", "+$CANNON_DAMAGE_BONUS damage") + costs { it.cannonUpgradeCost() },
            listOf("Extra Oars", "+$OAR_MANEUVER_BONUS manoeuvre") + costs { it.oarUpgradeCost() },
            listOf("Extra Sails", "+${trim(SAIL_SPEED_BONUS)} speed") + costs { it.sailUpgradeCost() },
        )
        return table(listOf("Upgrade", "Per level", "to L1", "to L2", "to L3"), rows) +
            "\n\nFully fouled costs ${trim(FOULING_SPEED_PENALTY * 100f)}% of speed; " +
            "cleaning is ${ShipCondition(100).cleanCost()}g flat, and repair is " +
            "${ShipCondition(100).let { it.currentHull = 99; it.repairCost(100) }}g per hull point."
    }

    private fun goodsTable(): String = table(
        listOf("Good", "Base price", "Icon"),
        Good.entries.map {
            listOf(it.displayName, "${it.basePrice}g", "`sprites/goods/${it.name.lowercase()}.png`")
        },
    )

    private fun reputationTable(): String {
        val rows = ReputationTier.entries.map { tier ->
            val values = (REPUTATION_MIN..REPUTATION_MAX).filter { reputationTier(it) == tier }
            val sample = values.first()
            listOf(
                tier.label,
                "${values.first()}..${values.last()}",
                percent(buyPriceMultiplier(sample)),
                percent(sellPriceMultiplier(sample)),
                stance(militaryStance(sample)),
            )
        }
        return table(listOf("Standing", "Range", "Buy price", "Sell price", "That navy"), rows) +
            "\n\nFour powers: " + Faction.entries.joinToString(", ") { it.displayName } + "."
    }

    private fun warehouseBlock(): String =
        "Buying one in a port costs ${WAREHOUSE_PURCHASE_PRICE}g and holds " +
            "$WAREHOUSE_BASE_CAPACITY units. Each of the $WAREHOUSE_MAX_LEVEL expansion levels " +
            "adds $WAREHOUSE_EXPANSION_CAPACITY, so a fully expanded warehouse holds " +
            "${WAREHOUSE_BASE_CAPACITY + WAREHOUSE_MAX_LEVEL * WAREHOUSE_EXPANSION_CAPACITY}."

    private fun loanTable(): String = table(
        listOf("Borrow", "Repay", "Interest", "Term"),
        LOAN_OFFERS.map {
            listOf(
                "${it.principal}g",
                "${it.repayAmount}g",
                "${(it.repayAmount - it.principal) * 100 / it.principal}%",
                "${trim(it.termGameMinutes / (24f * 60f))} game days",
            )
        },
    )

    private fun captainBlock(): String =
        "Hiring the Nth captain costs " +
            (0 until 5).joinToString(", ") { "${Captain.hireCost(it)}g" } +
            " for the 1st through 5th."

    // --- plumbing --------------------------------------------------------------------------

    private fun spriteName(ship: ShipType) = ship.spritePath.substringAfterLast('/')

    private fun stance(stance: MilitaryStance) = when (stance) {
        MilitaryStance.IGNORES -> "ignores you"
        MilitaryStance.OPPORTUNISTIC -> "picks on the weak"
        MilitaryStance.HUNTS -> "hunts you"
    }

    /** Rounded, not truncated: a -20% discount stored as 0.8f comes out of the obvious
     *  `toInt()` as -19%, which is wrong and looks like a balance decision. */
    private fun percent(multiplier: Float): String {
        val delta = Math.round((multiplier - 1f) * 100f)
        return if (delta == 0) "base" else if (delta > 0) "+$delta%" else "$delta%"
    }

    /** Locale.ROOT on purpose - the default locale here writes 16,5 for a speed of 16.5. */
    private fun trim(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString()
        else String.format(java.util.Locale.ROOT, "%.2f", value).trimEnd('0')

    private fun table(header: List<String>, rows: List<List<String>>): String {
        val widths = header.indices.map { column ->
            maxOf(header[column].length, rows.maxOfOrNull { it[column].length } ?: 0)
        }
        fun line(cells: List<String>) =
            cells.mapIndexed { i, cell -> cell.padEnd(widths[i]) }.joinToString(" | ", "| ", " |")
        return buildString {
            appendLine(line(header))
            appendLine(widths.joinToString(" | ", "| ", " |") { "-".repeat(it) })
            rows.forEach { appendLine(line(it)) }
        }.trimEnd()
    }

    /** Replaces one marked region in place. Fails loudly rather than appending: a missing marker
     *  means the hand-written file and this generator have drifted apart, and silently writing
     *  the table somewhere else would hide that. */
    /** What a generated region of [file] holds now, line endings aside. */
    internal fun readBlock(file: File, key: String): String {
        require(file.isFile) { "no such reference file: ${file.canonicalPath}" }
        val text = file.readText().replace("\r\n", "\n")
        val begin = "<!-- generated:$key -->"
        val from = text.indexOf(begin)
        val to = text.indexOf("<!-- /generated:$key -->")
        require(from >= 0 && to > from) { "${file.name} has no $begin region" }
        return text.substring(from + begin.length, to).trim()
    }

    private fun writeBlock(file: File, key: String, body: String) {
        require(file.isFile) { "no such reference file: ${file.canonicalPath}" }
        val begin = "<!-- generated:$key -->"
        val end = "<!-- /generated:$key -->"
        val text = file.readText()
        val from = text.indexOf(begin)
        val to = text.indexOf(end)
        require(from >= 0 && to > from) { "${file.name} has no $begin ... $end region" }
        file.writeText(text.substring(0, from + begin.length) + "\n" + body + "\n" + text.substring(to))
    }
}

/**
 * The guard the generator could not be: `Reference/` holds the game's numbers so nobody has to
 * read the code for them, and nothing noticed when the code moved on and the tables did not
 * (REVIEW.md, finding 41). This runs in `:core:test`; when it fails, run
 * `./gradlew.bat :core:reference` and commit what it rewrites.
 */
class ReferenceDocsCurrentTest {

    @Test
    fun `the reference tables say what the code says`() {
        val generator = ReferenceDocsTest()
        val stale = generator.generatedBlocks()
            .filter { (file, key, body) -> generator.readBlock(file, key) != body.replace("\r\n", "\n").trim() }
            .map { (file, key, _) -> "${file.name}#$key" }
        kotlin.test.assertTrue(stale.isEmpty(), "Reference/ is behind the code in $stale - run ./gradlew.bat :core:reference")
    }
}
