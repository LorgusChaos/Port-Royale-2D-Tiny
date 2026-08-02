package com.portroyale.tiny.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound

private const val MUSIC_VOLUME = 0.35f

private val SAILING_TRACK_PATHS = listOf(
    "audio/music/sailing_1.wav",
    "audio/music/sailing_2.wav",
    "audio/music/sailing_3.wav",
)

/** Short one-shot SFX and background Music, loaded lazily on first use. Single shared instance
 *  so no screen needs to load its own copies of the same sounds. Sailing music rotates through
 *  a small playlist (pausing/resuming in place across port visits); combat music is a single
 *  track that restarts fresh at the beginning of every fight. All playback goes through
 *  [effective] so the Settings screen's master volume/mute apply uniformly to music and SFX. */
object AudioManager {
    var masterVolume: Float = 1f
        private set
    var muted: Boolean = false
        private set

    private val cannonFire: Sound by lazy { Gdx.audio.newSound(Gdx.files.internal("audio/cannon_fire.ogg")) }
    private val hit: Sound by lazy { Gdx.audio.newSound(Gdx.files.internal("audio/hit.ogg")) }
    private val click: Sound by lazy { Gdx.audio.newSound(Gdx.files.internal("audio/click.ogg")) }
    private val coins: Sound by lazy { Gdx.audio.newSound(Gdx.files.internal("audio/coins.ogg")) }
    private val portArrival: Sound by lazy { Gdx.audio.newSound(Gdx.files.internal("audio/port_arrival.ogg")) }
    private val marketEnter: Sound by lazy { Gdx.audio.newSound(Gdx.files.internal("audio/market_enter.ogg")) }
    private val shipyardEnter: Sound by lazy { Gdx.audio.newSound(Gdx.files.internal("audio/shipyard_enter.ogg")) }

    private val sailingTracks: List<Music> by lazy {
        SAILING_TRACK_PATHS.map { path ->
            Gdx.audio.newMusic(Gdx.files.internal(path)).apply {
                volume = effective(MUSIC_VOLUME)
                setOnCompletionListener { advanceSailingTrack() }
            }
        }
    }
    private var sailingIndex = 0

    private val combatTrack: Music by lazy {
        Gdx.audio.newMusic(Gdx.files.internal("audio/music/combat.wav")).apply {
            isLooping = true
            volume = effective(MUSIC_VOLUME)
        }
    }

    fun playCannonFire() { cannonFire.play(effective(0.6f)) }
    fun playHit() { hit.play(effective(0.7f)) }
    fun playClick() { click.play(effective(0.5f)) }
    fun playCoins() { coins.play(effective(0.6f)) }
    fun playPortArrival() { portArrival.play(effective(0.8f)) }
    fun playMarketEnter() { marketEnter.play(effective(0.6f)) }
    fun playShipyardEnter() { shipyardEnter.play(effective(0.6f)) }

    private fun advanceSailingTrack() {
        sailingIndex = (sailingIndex + 1) % sailingTracks.size
        sailingTracks[sailingIndex].play()
    }

    /** Resumes the sailing playlist from wherever it last paused (or starts it fresh). */
    fun playSailingMusic() {
        combatTrack.pause()
        val current = sailingTracks[sailingIndex]
        if (!current.isPlaying) current.play()
    }

    /** Pauses the sailing playlist in place, e.g. while in a port menu. */
    fun pauseSailingMusic() {
        sailingTracks.forEach { if (it.isPlaying) it.pause() }
    }

    /** Starts the combat track fresh from the beginning, pausing the sailing playlist. */
    fun playCombatMusic() {
        pauseSailingMusic()
        combatTrack.stop()
        combatTrack.play()
    }

    fun stopCombatMusic() {
        combatTrack.stop()
    }

    fun setMasterVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
        applyMusicVolumes()
    }

    fun setMuted(value: Boolean) {
        muted = value
        applyMusicVolumes()
    }

    private fun applyMusicVolumes() {
        sailingTracks.forEach { it.volume = effective(MUSIC_VOLUME) }
        combatTrack.volume = effective(MUSIC_VOLUME)
    }

    private fun effective(base: Float): Float = if (muted) 0f else base * masterVolume

    fun dispose() {
        cannonFire.dispose()
        hit.dispose()
        click.dispose()
        coins.dispose()
        portArrival.dispose()
        marketEnter.dispose()
        shipyardEnter.dispose()
        sailingTracks.forEach { it.dispose() }
        combatTrack.dispose()
    }
}
