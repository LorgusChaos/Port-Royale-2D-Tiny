package com.portroyale.tiny.world

private const val TIME_SCALE = 10f
private const val GAME_MINUTES_PER_DAY = 24 * 60
private const val START_YEAR = 1600
private const val DAYS_PER_MONTH = 30
private const val MONTHS_PER_YEAR = 12
private val MONTH_NAMES = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

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

    /** A calendar date for the HUD - a simplified 30-day/12-month calendar laid over [day],
     *  starting 1 [START_YEAR] - not real Gregorian months/leap years, nothing else in the game
     *  depends on real month lengths, this is purely display flavor. */
    val calendarLabel: String
        get() {
            val zeroBasedDay = day - 1
            val year = START_YEAR + zeroBasedDay / (DAYS_PER_MONTH * MONTHS_PER_YEAR)
            val month = (zeroBasedDay / DAYS_PER_MONTH) % MONTHS_PER_YEAR
            val dayOfMonth = zeroBasedDay % DAYS_PER_MONTH + 1
            return "$dayOfMonth ${MONTH_NAMES[month]} $year"
        }

    /** For turning a game-time span (e.g. time left on a loan) into a real-world wait, for
     *  display - the inverse of the 10x dilation in [update]. */
    fun gameMinutesToRealMinutes(gameMinutes: Float): Float = gameMinutes / TIME_SCALE

    /** The forward direction of [update]'s dilation - used by FleetSimulation to turn a real
     *  sailing distance/speed into a game-minute leg duration, so a captain's ship takes exactly
     *  as long (in compressed game-time) as the player's own ship would take to sail the same
     *  distance at the same speed. */
    fun realSecondsToGameMinutes(realSeconds: Float): Float = (realSeconds / 60f) * TIME_SCALE

    fun reset() {
        totalGameMinutes = 0f
    }

    fun snapshot(): Float = totalGameMinutes

    fun restore(value: Float) {
        totalGameMinutes = value
    }
}
