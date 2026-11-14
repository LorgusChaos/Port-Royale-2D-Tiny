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

private const val MIN_WINDOW_SIDE = 320
private const val MAX_WINDOW_SIDE = 16384

/** The file is user-editable, so a volume may arrive as NaN, Infinity or 100: the volume is
 *  brought into 0..1 (NaN and infinities become the default, since coerceIn passes NaN through)
 *  and the window size into a sane pixel range (REVIEW.md, finding 36). Pure - no Gdx. */
fun SettingsData.sanitized(): SettingsData {
    val defaults = SettingsData()
    val result = SettingsData()
    result.masterVolume = when {
        !masterVolume.isFinite() -> defaults.masterVolume
        else -> masterVolume.coerceIn(0f, 1f)
    }
    result.muted = muted
    result.fullscreen = fullscreen
    result.windowWidth = windowWidth.coerceIn(MIN_WINDOW_SIDE, MAX_WINDOW_SIDE)
    result.windowHeight = windowHeight.coerceIn(MIN_WINDOW_SIDE, MAX_WINDOW_SIDE)
    return result
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
            // fromJson returns null for an empty file; both that and bad numbers fall back here.
            (Json().fromJson(SettingsData::class.java, f.readString()) ?: SettingsData()).sanitized()
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
