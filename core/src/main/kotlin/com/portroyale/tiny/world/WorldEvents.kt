package com.portroyale.tiny.world

import com.portroyale.tiny.economy.Good

/**
 * What the world tells the player. The game implements it and turns each call into a window, a
 * sound or a screen; [GameWorld] never sees any of those, which is what lets a test run the world
 * without a window.
 *
 * Calls are synchronous and made at the moment the thing happens - the same place the game used
 * to show its toast directly. A queue drained after the world's update would shift that moment,
 * and the move out of PortRoyaleGame is meant to change nothing the player can see.
 */
interface WorldEvents {
    fun toast(message: String, icon: ToastIcon)

    /** A ship came alongside without having hunted the player down: the player chooses. The game
     *  answers with [GameWorld.startAiCombat] or [GameWorld.declineEncounter]. */
    fun offerEncounter(ship: AiShip)

    /** The world has committed to a fight with [ship] (see [GameWorld.startAiCombat]): the game
     *  opens the battle. */
    fun startCombat(ship: AiShip)
    /** The player's ship has made [port] (see [GameWorld.arriveAtPort]): the game opens the port. */
    fun arrivedAtPort(port: ResolvedPort)
}

/** The picture beside a toast. The world only names it; the textures belong to the game
 *  (IconFactory), and the world must not load one. */
sealed interface ToastIcon {
    data object Coin : ToastIcon
    data object Bottle : ToastIcon
    data object Castaway : ToastIcon
    /** Named after the pictures, not the news: raids borrowed the ledger's debt mark for "pirate
     *  activity reported" and the property mark for the navy's answer and the all-clear. */
    data object Debt : ToastIcon
    data object Property : ToastIcon
    /** A good's own icon, for anything that hands the player cargo. */
    data class Cargo(val good: Good) : ToastIcon
}
