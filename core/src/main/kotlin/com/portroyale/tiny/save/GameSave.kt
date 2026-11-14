package com.portroyale.tiny.save

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter
import com.badlogic.gdx.utils.SerializationException
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.charset.Charset
import com.portroyale.tiny.diagnostics.GameLog
import com.portroyale.tiny.economy.Captain
import com.portroyale.tiny.economy.DEFAULT_LEG_MINUTES
import com.portroyale.tiny.economy.Good
import com.portroyale.tiny.economy.Loan
import com.portroyale.tiny.economy.MarketState
import com.portroyale.tiny.economy.OwnedShip
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.economy.Warehouse
import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.ships.MAX_UPGRADE_LEVEL
import com.portroyale.tiny.ships.ShipCondition
import com.portroyale.tiny.ships.ShipType
import com.portroyale.tiny.ships.ShipTypes
import com.portroyale.tiny.world.GameClock

private const val SAVE_DIR = "PortRoyaleTiny/saves/"
const val AUTOSAVE_NAME = "autosave"

/** Plain no-arg-constructor POJO shape for libGDX's reflection-based Json (de)serializer. */
class GameSaveData {
    var captainName: String = ""
    var gold: Int = 10000
    var shipTypeName: String = "Sloop"
    var cargo: MutableMap<String, Int> = mutableMapOf()
    var shipX: Float = 0f
    var shipY: Float = 0f
    var currentHull: Int = 100
    var fouling: Float = 0f
    var cannonLevel: Int = 0
    var oarLevel: Int = 0
    var sailLevel: Int = 0
    var reputation: MutableMap<String, Int> = mutableMapOf()
    /** Sub-point reputation still owed from past sales - see Player.recordSale. Absent from
     *  saves written before M14, which simply resume from a clean zero. */
    var reputationRemainder: MutableMap<String, Float> = mutableMapOf()
    var marketState: MutableMap<String, Float> = mutableMapOf()
    var savedAt: Long = 0L
    var gameClockMinutes: Float = 0f
    /** port name -> level */
    var warehouseLevels: MutableMap<String, Int> = mutableMapOf()
    /** port name -> gold stored there */
    var warehouseGold: MutableMap<String, Int> = mutableMapOf()
    /** "portName|GOOD_NAME" -> quantity stored there */
    var warehouseGoods: MutableMap<String, Int> = mutableMapOf()
    var hasLoan: Boolean = false
    var loanPrincipal: Int = 0
    var loanRepayAmount: Int = 0
    var loanTakenAtGameMinutes: Float = 0f
    var loanDeadlineGameMinutes: Float = 0f
    var loanFactionName: String = ""
    var loanOverdue: Boolean = false
    var captains: MutableList<CaptainSaveEntry> = mutableListOf()
    var ownedShips: MutableList<OwnedShipSaveEntry> = mutableListOf()
    var activeShipId: Int = 1
    /** The port a rescued castaway is to be taken to; "" when nobody is aboard - which is also
     *  what a save written before passengers were saved reads as (REVIEW.md, finding 12). */
    var passengerPort: String = ""
    /** The whole sea around the player - see WorldSave. Absent (null) in a save written before
     *  the world was saved, which then loads around a fresh world. */
    var world: WorldSaveData? = null
}

/** Plain no-arg-constructor POJO for one Captain - see GameSaveData.captains. A nested list of
 *  these (rather than parallel port/tier maps like warehouses use) since a captain is several
 *  fields that only make sense together, not one value per key. [shipId] uses -1 as "idle, no
 *  ship" rather than a nullable Int, to keep this a plain-primitive POJO for the reflection-based
 *  Json (de)serializer - same reasoning for [cargoGoodName] using "" for "empty hold". */
class CaptainSaveEntry {
    var name: String = ""
    var tradeSense: Float = 0f
    var caution: Float = 0f
    var shipId: Int = -1
    var portA: String = ""
    var portB: String = ""
    var cargoGoodName: String = ""
    var headingToB: Boolean = true
    var cargoUnits: Int = 0
    var cargoCostTotal: Int = 0
    var arrivesAtGameMinutes: Float = 0f
    var legDurationGameMinutes: Float = DEFAULT_LEG_MINUTES
    var lastReport: String = ""
    var totalProfit: Int = 0
    var sheltering: Boolean = false
    var shelterPort: String = ""
}

/** Plain no-arg-constructor POJO for one OwnedShip - see GameSaveData.ownedShips. [currentHull]
 *  of 0 means "not saved with a real value" (an older save, or a fresh ship) - resolved to that
 *  ship type's full maxHull rather than taken literally, since 0 HP would mean already sunk. */
class OwnedShipSaveEntry {
    var id: Int = 0
    var name: String = ""
    var shipTypeName: String = "Sloop"
    var dockedAt: String = ""
    var currentHull: Int = 0
    var fouling: Float = 0f
    var cannonLevel: Int = 0
    var oarLevel: Int = 0
    var sailLevel: Int = 0
}

/** One save's identity for list screens - name plus when it was written, so the player can tell
 *  saves apart beyond just their typed name. */
class SaveInfo(val name: String, val savedAt: Long)

/** What reading a save file came to: the data - possibly from the copy kept before the last
 *  write, when the file itself was damaged - or why there is none. */
sealed interface LoadResult {
    class Loaded(val data: GameSaveData, val fromBackup: Boolean) : LoadResult
    class Failed(val reason: String) : LoadResult
}

/** What [SaveManager.checkSaveName] made of a typed save name: the name to write under, or why
 *  there is none. */
sealed interface SaveNameCheck {
    data class Ok(val name: String) : SaveNameCheck
    data class Rejected(val reason: String) : SaveNameCheck
}

/**
 * Save games as JSON snapshots under the user's home directory (Gdx external storage),
 * independent of wherever the game happens to be installed/run from. A save holds the whole game:
 * the player's state and, through WorldSave, the sea around them - every AI ship at its task, the
 * raid on its clock, the sea events - so a load carries on from the same moment. A load resumes
 * at sea even from a save made in port, and saving is closed during a fight (REVIEW.md, finding
 * 14), so there is no combat sub-state to serialize.
 */
object SaveManager {

    private fun dir(): FileHandle = Gdx.files.external(SAVE_DIR)

    fun listSaveInfos(): List<SaveInfo> {
        val d = dir()
        if (!d.exists()) return emptyList()
        return d.list(".json")
            .map { SaveInfo(it.nameWithoutExtension(), it.lastModified()) }
            .sortedByDescending { it.savedAt }
    }

    fun save(
        name: String,
        captainName: String,
        player: Player,
        shipPosition: Vector2,
        passengerPort: String? = null,
        world: WorldSaveData? = null
    ) {
        val data = snapshot(captainName, player, shipPosition, passengerPort)
        data.world = world
        writeSaveFile(dir().child("$name.json").file(), toJson(data))
    }

    /** The player's half of a save, gathered from the live game. [passengerPort] is the
     *  castaway's destination, null with nobody aboard. The sea itself - AI ships, the raid,
     *  sea events, courses - is WorldSave's, added by [save]. */
    fun snapshot(captainName: String, player: Player, shipPosition: Vector2, passengerPort: String?): GameSaveData {
        val data = GameSaveData()
        data.captainName = captainName
        data.gold = player.gold
        data.shipTypeName = player.ship.name
        data.cargo = player.cargo.entries.associate { it.key.name to it.value }.toMutableMap()
        data.shipX = shipPosition.x
        data.shipY = shipPosition.y
        data.currentHull = player.condition.currentHull
        data.fouling = player.condition.fouling
        data.cannonLevel = player.condition.cannonLevel
        data.oarLevel = player.condition.oarLevel
        data.sailLevel = player.condition.sailLevel
        data.reputation = player.reputation.entries.associate { it.key.name to it.value }.toMutableMap()
        data.reputationRemainder =
            player.reputationRemainder.entries.associate { it.key.name to it.value }.toMutableMap()
        data.marketState = MarketState.snapshot().toMutableMap()
        data.savedAt = System.currentTimeMillis()
        data.gameClockMinutes = GameClock.snapshot()

        for ((portName, warehouse) in player.warehouses) {
            data.warehouseLevels[portName] = warehouse.level
            data.warehouseGold[portName] = warehouse.gold
            for ((good, qty) in warehouse.goods) {
                if (qty > 0) data.warehouseGoods["$portName|${good.name}"] = qty
            }
        }

        player.loan?.let { loan ->
            data.hasLoan = true
            data.loanPrincipal = loan.principal
            data.loanRepayAmount = loan.repayAmount
            data.loanTakenAtGameMinutes = loan.takenAtGameMinutes
            data.loanDeadlineGameMinutes = loan.deadlineGameMinutes
            data.loanFactionName = loan.faction.name
            data.loanOverdue = loan.overdue
        }

        for (captain in player.captains) {
            val entry = CaptainSaveEntry()
            entry.name = captain.name
            entry.tradeSense = captain.tradeSense
            entry.caution = captain.caution
            entry.shipId = captain.shipId ?: -1
            entry.portA = captain.portA
            entry.portB = captain.portB
            entry.cargoGoodName = captain.cargoGood?.name ?: ""
            entry.headingToB = captain.headingToB
            entry.cargoUnits = captain.cargoUnits
            entry.cargoCostTotal = captain.cargoCostTotal
            entry.arrivesAtGameMinutes = captain.arrivesAtGameMinutes
            entry.legDurationGameMinutes = captain.legDurationGameMinutes
            entry.lastReport = captain.lastReport
            entry.totalProfit = captain.totalProfit
            entry.sheltering = captain.sheltering
            entry.shelterPort = captain.shelterPort
            data.captains.add(entry)
        }

        for (ownedShip in player.ownedShips) {
            val entry = OwnedShipSaveEntry()
            entry.id = ownedShip.id
            entry.name = ownedShip.name
            entry.shipTypeName = ownedShip.shipType.name
            entry.dockedAt = ownedShip.dockedAt
            entry.currentHull = ownedShip.condition.currentHull
            entry.fouling = ownedShip.condition.fouling
            entry.cannonLevel = ownedShip.condition.cannonLevel
            entry.oarLevel = ownedShip.condition.oarLevel
            entry.sailLevel = ownedShip.condition.sailLevel
            data.ownedShips.add(entry)
        }
        data.activeShipId = player.activeShipId
        data.passengerPort = passengerPort ?: ""
        return data
    }

    fun toJson(data: GameSaveData): String {
        val json = Json()
        json.setOutputType(JsonWriter.OutputType.json)
        return json.prettyPrint(data)
    }

    fun fromJson(text: String): GameSaveData =
        Json().fromJson(GameSaveData::class.java, text) ?: throw SerializationException("the save is empty")

    /**
     * Writes [text] as [target] whole or not at all (REVIEW.md, finding 19).
     *
     * It goes to a temporary file beside the target first and is then moved over it, so a crash,
     * a power cut or a full disk mid-write leaves the old save, not half of a new one. The save
     * being replaced is kept as `<name>.json.bak` - with one autosave overwritten every ten
     * minutes, that copy is the only thing between a damaged file and a lost game. Neither the
     * temporary file nor the backup ends in `.json`, so the save list never shows them.
     */
    fun writeSaveFile(target: File, text: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(text, SAVE_CHARSET)
        if (target.exists()) Files.copy(target.toPath(), backupOf(target).toPath(), StandardCopyOption.REPLACE_EXISTING)
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Reads [target]; if it is damaged, the copy kept before its last write, and says so. */
    fun readSaveFile(target: File): LoadResult {
        val reason = try {
            return LoadResult.Loaded(fromJson(target.readText(SAVE_CHARSET)), fromBackup = false)
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
        val backup = backupOf(target)
        if (backup.exists()) {
            try {
                return LoadResult.Loaded(fromJson(backup.readText(SAVE_CHARSET)), fromBackup = true)
            } catch (e: Exception) {
                // Both copies are gone; the first reason is the one worth reporting.
            }
        }
        return LoadResult.Failed(reason)
    }

    private fun backupOf(target: File): File = File(target.parentFile, target.name + ".bak")

    /** The platform charset, exactly as `FileHandle.writeString`/`readString` used before
     *  writing moved onto java.io: every save already on disk was written in it. */
    private val SAVE_CHARSET: Charset = Charset.defaultCharset()

    /** Names Windows keeps for devices; a file called one of these cannot be written, whatever
     *  its extension. */
    private val WINDOWS_RESERVED_NAMES: Set<String> =
        setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }

    /**
     * Turns what the player typed into a name safe to use as a file name, or says why it can't be.
     *
     * The name becomes `<name>.json` in the saves folder, so only letters (any alphabet), digits,
     * spaces, `_` and `-` survive: no separators or `..` to leave the folder, nothing Windows
     * refuses in a file name. Dots go with the rest, which also means no name can end in one;
     * spaces at either end are trimmed - Windows silently strips both, so "name." would land on
     * top of "name". What is left is rejected if empty, a Windows device name, or the autosave's.
     */
    fun checkSaveName(typed: String): SaveNameCheck {
        if (typed.isBlank()) return SaveNameCheck.Rejected("Enter a name first.")
        val name = typed.filter { it.isLetterOrDigit() || it == ' ' || it == '_' || it == '-' }.trim()
        return when {
            name.isEmpty() -> SaveNameCheck.Rejected("Use letters, digits, spaces, _ or -.")
            name.uppercase() in WINDOWS_RESERVED_NAMES ->
                SaveNameCheck.Rejected("\"$name\" is reserved by Windows - pick another name.")
            name.equals(AUTOSAVE_NAME, ignoreCase = true) ->
                SaveNameCheck.Rejected("\"$name\" is kept for the autosave - pick another name.")
            else -> SaveNameCheck.Ok(name)
        }
    }

    fun load(name: String): LoadResult = readSaveFile(dir().child("$name.json").file())

    /**
     * The hull the active ship comes back with: never below 1 and never above [maxHull].
     *
     * A save taken in the defeat window used to hold 0 here with the ship, hold and purse still
     * aboard; saving in a fight is now closed (REVIEW.md, finding 14), and this keeps the saves
     * already written from loading a ship afloat on nothing. Not topped up to full the way the
     * docked ships are (see resolveOwnedShips): a reload must not be a free repair.
     */
    fun resolveActiveHull(saved: Int, maxHull: Int): Int = saved.coerceIn(1, maxHull.coerceAtLeast(1))

    /** The castaway's destination if it is one of [portNames]; nobody aboard otherwise - a save
     *  from before passengers were saved, or a port the game no longer has (finding 12). */
    fun resolvePassenger(saved: String, portNames: Collection<String>): String? = saved.takeIf { it in portNames }

    /** The saved active ship if the fleet has it, otherwise the first ship - an edited or damaged
     *  save used to leave the player aboard a ship id that no longer existed (finding 38). */
    fun resolveActiveShipId(data: GameSaveData, ships: List<OwnedShip>): Int =
        if (ships.any { it.id == data.activeShipId }) data.activeShipId else ships.firstOrNull()?.id ?: data.activeShipId

    /** The name the ship's boat used to carry. Old saves still hold it. */
    private const val LEGACY_LONGBOAT_NAME = "Wreck"

    /**
     * The hull a save says the player was sailing.
     *
     * The longboat has to resolve to the longboat, and that is a fix rather than a detail. It
     * used to fall through to a Sloop, on the reasoning that loading should not strand anyone in
     * a wreck - but the longboat is not a dead end by design, and the fall-through handed out a
     * free ship: lose a battle, reload, and the defeat was undone. The autosave runs every ten
     * minutes, so it undid itself even when nobody was trying. A defeat that a reload erases is
     * not a stake.
     */
    fun resolveShipType(name: String): ShipType =
        ShipTypes.ALL.firstOrNull { it.name == name }
            ?: ShipTypes.LONGBOAT.takeIf { name == it.name || name == LEGACY_LONGBOAT_NAME }
            ?: ShipTypes.SLOOP

    fun resolveCargo(saved: Map<String, Int>): Map<Good, Int> =
        Good.entries.associateWith { saved[it.name] ?: 0 }

    fun resolveReputation(saved: Map<String, Int>): Map<Faction, Int> =
        Faction.entries.associateWith { saved[it.name] ?: 0 }

    /** Only entries that are actually a fraction survive: a whole point sitting here would mean
     *  reputation the player earned but never had credited, and a value outside (-1, 1) can only
     *  come from a hand-edited or corrupted save. */
    fun resolveReputationRemainder(saved: Map<String, Float>): Map<Faction, Float> =
        Faction.entries.mapNotNull { faction ->
            val value = saved[faction.name] ?: return@mapNotNull null
            if (value > -1f && value < 1f) faction to value else null
        }.toMap()

    fun resolveWarehouses(data: GameSaveData): Map<String, Warehouse> {
        val result = mutableMapOf<String, Warehouse>()
        for ((portName, level) in data.warehouseLevels) {
            result[portName] = Warehouse(level)
        }
        for ((portName, gold) in data.warehouseGold) {
            result.getOrPut(portName) { Warehouse() }.gold = gold
        }
        for ((key, qty) in data.warehouseGoods) {
            val parts = key.split("|")
            if (parts.size != 2) continue
            val good = Good.entries.find { it.name == parts[1] } ?: continue
            result.getOrPut(parts[0]) { Warehouse() }.goods[good] = qty
        }
        return result
    }

    fun resolveLoan(data: GameSaveData): Loan? {
        if (!data.hasLoan) return null
        val faction = Faction.entries.find { it.name == data.loanFactionName } ?: run {
            // Not silently: the debt is gone and the gold is kept, which the log should say.
            GameLog.log(GameLog.Category.SAVE, "loan from unknown faction \"${data.loanFactionName}\" dropped on load")
            return null
        }
        return Loan(
            faction = faction,
            principal = data.loanPrincipal,
            repayAmount = data.loanRepayAmount,
            takenAtGameMinutes = data.loanTakenAtGameMinutes,
            deadlineGameMinutes = data.loanDeadlineGameMinutes,
            overdue = data.loanOverdue
        )
    }

    fun resolveCaptains(data: GameSaveData): MutableList<Captain> =
        data.captains.map { entry ->
            // A hold whose good no longer resolves (renamed, removed, or an empty name) has to
            // drop its unit count and cost with it. A captain left holding cargoUnits > 0 with
            // no cargoGood is stuck for good: FleetSimulation.attemptSell bails out on the null
            // good, and attemptBuy is skipped because the hold reads as non-empty - so the
            // captain sails a permanent loop, never trading again.
            val cargoGood = Good.entries.find { it.name == entry.cargoGoodName }
            Captain(
                name = entry.name,
                tradeSense = entry.tradeSense,
                caution = entry.caution,
                shipId = if (entry.shipId >= 0) entry.shipId else null,
                portA = entry.portA,
                portB = entry.portB,
                headingToB = entry.headingToB,
                cargoGood = cargoGood,
                cargoUnits = if (cargoGood == null) 0 else entry.cargoUnits,
                cargoCostTotal = if (cargoGood == null) 0 else entry.cargoCostTotal,
                arrivesAtGameMinutes = entry.arrivesAtGameMinutes,
                legDurationGameMinutes = if (entry.legDurationGameMinutes > 0f) entry.legDurationGameMinutes else DEFAULT_LEG_MINUTES,
                lastReport = entry.lastReport,
                totalProfit = entry.totalProfit,
                sheltering = entry.sheltering,
                shelterPort = entry.shelterPort
            )
        }.toMutableList()

    fun resolveOwnedShips(data: GameSaveData): MutableList<OwnedShip> =
        data.ownedShips.map { entry ->
            val shipType = resolveShipType(entry.shipTypeName)
            val condition = ShipCondition(shipType.maxHull)
            // Within bounds, whatever the file says: an edited or damaged save used to load hulls
            // above their maximum and upgrades past the last level (REVIEW.md, finding 38).
            condition.currentHull = if (entry.currentHull > 0) entry.currentHull.coerceAtMost(shipType.maxHull) else shipType.maxHull
            condition.fouling = entry.fouling.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
            condition.cannonLevel = entry.cannonLevel.coerceIn(0, MAX_UPGRADE_LEVEL)
            condition.oarLevel = entry.oarLevel.coerceIn(0, MAX_UPGRADE_LEVEL)
            condition.sailLevel = entry.sailLevel.coerceIn(0, MAX_UPGRADE_LEVEL)
            OwnedShip(
                id = entry.id,
                name = entry.name,
                shipType = shipType,
                dockedAt = entry.dockedAt,
                condition = condition
            )
        }.toMutableList()
}
