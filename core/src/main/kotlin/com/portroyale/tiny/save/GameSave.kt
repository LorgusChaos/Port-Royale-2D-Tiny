package com.portroyale.tiny.save

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter
import com.portroyale.tiny.economy.Captain
import com.portroyale.tiny.economy.DEFAULT_LEG_MINUTES
import com.portroyale.tiny.economy.Good
import com.portroyale.tiny.economy.Loan
import com.portroyale.tiny.economy.MarketState
import com.portroyale.tiny.economy.OwnedShip
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.economy.Warehouse
import com.portroyale.tiny.factions.Faction
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

/**
 * Save games as small JSON snapshots under the user's home directory (Gdx external storage),
 * independent of wherever the game happens to be installed/run from. Only the persistent player
 * state is captured (gold/ship/cargo/position/captain name) - saving always resumes at sea, even
 * if triggered mid-port-visit or mid-combat, so there's no UI/combat sub-state to serialize.
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

    fun save(name: String, captainName: String, player: Player, shipPosition: Vector2) {
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

        val json = Json()
        json.setOutputType(JsonWriter.OutputType.json)
        dir().child("$name.json").writeString(json.prettyPrint(data), false)
    }

    fun load(name: String): GameSaveData? {
        return try {
            val json = Json()
            json.fromJson(GameSaveData::class.java, dir().child("$name.json").readString())
        } catch (e: Exception) {
            null
        }
    }

    fun resolveShipType(name: String): ShipType =
        ShipTypes.ALL.firstOrNull { it.name == name } ?: ShipTypes.SLOOP

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
        val faction = Faction.entries.find { it.name == data.loanFactionName } ?: return null
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
            condition.currentHull = if (entry.currentHull > 0) entry.currentHull else shipType.maxHull
            condition.fouling = entry.fouling
            condition.cannonLevel = entry.cannonLevel
            condition.oarLevel = entry.oarLevel
            condition.sailLevel = entry.sailLevel
            OwnedShip(
                id = entry.id,
                name = entry.name,
                shipType = shipType,
                dockedAt = entry.dockedAt,
                condition = condition
            )
        }.toMutableList()
}
