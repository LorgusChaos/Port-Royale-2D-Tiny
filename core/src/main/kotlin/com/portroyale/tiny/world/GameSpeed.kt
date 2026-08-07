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

    val multiplier: Float get() = STEPS[stepIndex]

    fun cycle() {
        stepIndex = (stepIndex + 1) % STEPS.size
    }

    fun reset() {
        stepIndex = 0
    }
}
