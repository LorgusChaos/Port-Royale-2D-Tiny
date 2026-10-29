package com.portroyale.tiny.world

private val STEPS = floatArrayOf(1f, 2f, 5f, 10f, 20f)

/** A player-controlled fast-forward multiplier for the whole simulated world - ship movement
 *  (player, fleet captains, AI ships), GameClock, fouling, market drift, sea-event timers. NOT
 *  applied to real-time UI/meta things (Scene2D stage animation, camera pan, the autosave
 *  interval, click-feedback flashes) - see PortRoyaleGame's worldDelta split in render(). Reset
 *  to 1x on every new game/load (see PortRoyaleGame.newGame/loadGame) - deliberately not part of
 *  the save data, it's a play-session convenience, not world state. */
object GameSpeed {
    var stepIndex = 0
        private set

    /** Held separately from the ladder rather than as a zeroth step, so that un-pausing returns
     *  the player to the speed they were running at instead of dropping them back to 1x. */
    var paused = false

    /** What the player chose - this is what the HUD shows, pause or no pause. */
    val multiplier: Float get() = STEPS[stepIndex]

    /** What the world actually advances by this frame. */
    val worldMultiplier: Float get() = if (paused) 0f else multiplier

    fun cycle() {
        stepIndex = (stepIndex + 1) % STEPS.size
    }

    fun reset() {
        stepIndex = 0
        paused = false
    }
}
