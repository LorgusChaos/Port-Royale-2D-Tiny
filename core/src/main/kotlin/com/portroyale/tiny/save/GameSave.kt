package com.portroyale.tiny.save

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter
import com.portroyale.tiny.economy.Good
import com.portroyale.tiny.economy.MarketState
import com.portroyale.tiny.economy.Player
import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.ships.ShipType
import com.portroyale.tiny.ships.ShipTypes

private const val SAVE_DIR = "PortRoyaleTiny/saves/"

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
    var marketState: MutableMap<String, Float> = mutableMapOf()
}

/**
 * Save games as small JSON snapshots under the user's home directory (Gdx external storage),
 * independent of wherever the game happens to be installed/run from. Only the persistent player
 * state is captured (gold/ship/cargo/position/captain name) - saving always resumes at sea, even
 * if triggered mid-port-visit or mid-combat, so there's no UI/combat sub-state to serialize.
 */
object SaveManager {

    private fun dir(): FileHandle = Gdx.files.external(SAVE_DIR)

    fun listSaves(): List<String> {
        val d = dir()
        if (!d.exists()) return emptyList()
        return d.list(".json").map { it.nameWithoutExtension() }.sorted()
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
        data.marketState = MarketState.snapshot().toMutableMap()

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
}
