package com.portroyale.tiny.save

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter

private const val SETTINGS_PATH = "PortRoyaleTiny/settings.json"

class SettingsData {
    var masterVolume: Float = 1f
    var muted: Boolean = false
    var fullscreen: Boolean = false
    var windowWidth: Int = 1280
    var windowHeight: Int = 720
}

/**
 * Small persisted settings blob (volume/mute/resolution), loaded once at startup and rewritten
 * whenever the player changes something in SettingsUI. Falls back to defaults if the file is
 * missing or unreadable - this is user-editable external state, not an internal invariant.
 */
object SettingsManager {

    private fun file() = Gdx.files.external(SETTINGS_PATH)

    fun load(): SettingsData {
        val f = file()
        if (!f.exists()) return SettingsData()
        return try {
            Json().fromJson(SettingsData::class.java, f.readString())
        } catch (e: Exception) {
            SettingsData()
        }
    }

    fun save(data: SettingsData) {
        val json = Json()
        json.setOutputType(JsonWriter.OutputType.json)
        file().writeString(json.prettyPrint(data), false)
    }
}
