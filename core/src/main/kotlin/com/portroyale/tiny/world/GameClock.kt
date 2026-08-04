package com.portroyale.tiny.world

private const val TIME_SCALE = 10f
private const val GAME_MINUTES_PER_DAY = 24 * 60

/** A fictional in-game calendar running 10x faster than real (wall-clock) time while playing -
 *  used for anything that should feel like it takes "days" in the game world (currently just
 *  loan deadlines) without actually asking the player to wait that long in real life. Only
 *  advances during active SAILING/IN_PORT/COMBAT play (see PortRoyaleGame), not while paused or
 *  on a menu screen. */
object GameClock {
    var totalGameMinutes: Float = 0f
        private set

    fun update(realDeltaSeconds: Float) {
        totalGameMinutes += (realDeltaSeconds / 60f) * TIME_SCALE
    }

    val day: Int get() = (totalGameMinutes / GAME_MINUTES_PER_DAY).toInt() + 1

    /** For turning a game-time span (e.g. time left on a loan) into a real-world wait, for
     *  display - the inverse of the 10x dilation in [update]. */
    fun gameMinutesToRealMinutes(gameMinutes: Float): Float = gameMinutes / TIME_SCALE

    fun reset() {
        totalGameMinutes = 0f
    }

    fun snapshot(): Float = totalGameMinutes

    fun restore(value: Float) {
        totalGameMinutes = value
    }
}
