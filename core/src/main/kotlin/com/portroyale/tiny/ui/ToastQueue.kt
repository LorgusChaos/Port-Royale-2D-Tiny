package com.portroyale.tiny.ui

/** One thing waiting to be shown in the event toast; [payload] is whatever the screen draws. */
sealed class ToastEntry<P>(val payload: P)

/** A message the player acknowledges and moves on from. */
class ToastNotice<P>(payload: P) : ToastEntry<P>(payload)

/** A question with two answers; exactly one of them is acted on, exactly once. */
class ToastChoice<P>(payload: P, val onPrimary: () -> Unit, val onSecondary: () -> Unit) : ToastEntry<P>(payload)

/**
 * What the event toast has to show, in the order it arrived (REVIEW.md, finding 20).
 *
 * The toast used to hold one message and replace it with the next. Some messages come from
 * code that runs while one is already open - a loan falling due, a captain lost at sea - so the
 * report of a lost captain could be wiped before the player saw it, and an open "Fight / Sail On"
 * was replaced by an ordinary message with both of its answers lost. Now a new message waits
 * behind the current one, and a question is never pushed aside: it stays until it is answered.
 *
 * No Scene2D here, so the rules are tested without a window; [EventToastUI] draws the head.
 */
class ToastQueue<P> {

    private val waiting = ArrayDeque<ToastEntry<P>>()

    /** What is on screen now, or null when nothing is. */
    val current: ToastEntry<P>? get() = waiting.firstOrNull()

    val isShowing: Boolean get() = waiting.isNotEmpty()

    /** Queues [entry]; answers whether it went straight on screen. */
    fun add(entry: ToastEntry<P>): Boolean {
        waiting.addLast(entry)
        return waiting.size == 1
    }

    /** Closes the current message - a notice only: a question cannot be waved away. */
    fun dismiss() {
        if (current is ToastNotice) waiting.removeFirst()
    }

    /** Answers the current question and acts on the answer. Taken off the queue first, so an
     *  answer that raises a message of its own puts it behind whatever was already waiting. */
    fun choose(primary: Boolean) {
        val question = current as? ToastChoice ?: return
        waiting.removeFirst()
        if (primary) question.onPrimary() else question.onSecondary()
    }

    /** Everything dropped - a new game or a load starts with nothing pending. */
    fun clear() {
        waiting.clear()
    }
}
