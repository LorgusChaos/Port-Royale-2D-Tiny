package com.portroyale.tiny.diagnostics

/**
 * Notices the world getting into a state it should not be in, and says so once.
 *
 * The bug that prompted this - a pirate frozen against a headland, unable to advance and never
 * giving up - produced no exception, no log line and no visible error. It was found because a
 * human happened to be watching. A stuck ship is trivially detectable ("has not moved in a while
 * and is not in port"), and the same goes for the rest of the family: a hull sitting on dry land,
 * a coordinate gone NaN, a captain holding cargo that is not any known good.
 *
 * The whole value is in reporting each one **once**. A check that fires every frame is noise, and
 * noise is indistinguishable from no logging at all - so each (subject, problem) pair is
 * remembered and stays quiet until it clears, at which point it becomes reportable again.
 */
class WorldWatchdog(
    /** How often to look. Frequently enough to catch things while a tester still remembers what
     *  they were doing, rarely enough to cost nothing. */
    private val intervalSeconds: Float = 5f,
    private val report: (String) -> Unit = { GameLog.log(GameLog.Category.SYSTEM, "WATCHDOG: $it") }
) {
    private var sinceLastCheck = 0f
    private val active = mutableSetOf<String>()

    /** Feeds elapsed real seconds; returns true when a check actually ran. */
    fun update(deltaSeconds: Float, checks: (Reporter) -> Unit): Boolean {
        sinceLastCheck += deltaSeconds
        if (sinceLastCheck < intervalSeconds) return false
        sinceLastCheck = 0f
        val seen = mutableSetOf<String>()
        checks(Reporter(seen))
        // Anything that was wrong and no longer is becomes reportable again, so a problem that
        // comes back is not silently swallowed by the first time it happened.
        active.retainAll(seen)
        return true
    }

    inner class Reporter(private val seen: MutableSet<String>) {
        /**
         * [key] identifies the subject and the problem together (e.g. "pirate-7/aground"), so two
         * different ships with the same fault are two reports and the same ship reporting every
         * five seconds is one.
         */
        fun problem(key: String, describe: () -> String) {
            seen.add(key)
            if (active.add(key)) report(describe())
        }
    }

    /** Test seam. */
    fun forget() {
        active.clear()
        sinceLastCheck = 0f
    }
}
