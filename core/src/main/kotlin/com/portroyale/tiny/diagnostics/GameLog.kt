package com.portroyale.tiny.diagnostics

import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A deliberately small diagnostic log: enough to reconstruct what the world was doing before
 * something went wrong, and nothing else.
 *
 * The rule that keeps it useful is what it does NOT record. No per-frame anything, no positions
 * streaming by, no "entered function" noise - only *decisions* and *outcomes*: a pursuit begun or
 * abandoned, a captain lost, a fight resolved, standing crossing a tier, a save written. A log
 * nobody can read is the same as no log, and the failure mode of logging is always volume.
 *
 * Two consumers:
 *  - a rolling file, so a session can be looked at after the fact;
 *  - an in-memory ring of the last [RING_CAPACITY] lines, which is what a crash report carries.
 *    The lines immediately before a crash are the ones worth having, and keeping them in memory
 *    means they survive even if the file write is what failed.
 *
 * Everything here is free of libGDX on purpose - the file sink is injected (see [start]), so the
 * formatting and the ring buffer are plain testable code rather than something that needs a live
 * backend to exercise.
 */
object GameLog {

    enum class Category { SYSTEM, AI, FLEET, ECONOMY, COMBAT, SAVE }

    /** Roughly a few minutes of real decisions - enough context to see what led to a crash
     *  without turning the report into something nobody scrolls through. */
    private const val RING_CAPACITY = 200

    private val ring = ArrayDeque<String>()
    private var sink: ((String) -> Unit)? = null
    private var environment: List<String> = emptyList()
    private var contextProvider: (() -> List<String>)? = null
    private val timestampFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    /**
     * Begins a session. [sink] receives each formatted line for persisting (null keeps everything
     * in memory only, which is what tests use); [environment] is the fixed machine/build detail
     * printed at the top of a crash report; [contextProvider] is asked for a snapshot of the game
     * world at the moment a crash happens - lazily, so it costs nothing while things are fine.
     */
    fun start(
        environment: List<String>,
        sink: ((String) -> Unit)? = null,
        contextProvider: (() -> List<String>)? = null
    ) {
        ring.clear()
        this.sink = sink
        this.environment = environment
        this.contextProvider = contextProvider
        log(Category.SYSTEM, "session started")
    }

    fun log(category: Category, message: String) {
        val line = "${LocalDateTime.now().format(timestampFormat)} [${category.name}] $message"
        ring.addLast(line)
        while (ring.size > RING_CAPACITY) ring.removeFirst()
        sink?.invoke(line)
    }

    /** The lines still in memory, oldest first. */
    fun recent(): List<String> = ring.toList()

    /**
     * A crash report meant to be read by a person and pasted into a bug report whole: what
     * happened, on what machine, in what state of the world, and what the game had just been
     * doing. A bare stack trace answers only the first of those, and for a simulation the other
     * three are usually where the answer actually is.
     */
    fun buildCrashReport(throwable: Throwable): String = buildString {
        appendLine("=== Port Royale 2D Tiny - crash report ===")
        appendLine("when: ${LocalDateTime.now()}")
        appendLine()

        appendLine("--- environment ---")
        environment.forEach { appendLine(it) }
        appendLine()

        appendLine("--- world state at the moment of the crash ---")
        val context = try {
            contextProvider?.invoke() ?: listOf("(no context registered)")
        } catch (secondary: Throwable) {
            // The snapshot must never be able to replace the real crash with its own.
            listOf("(context unavailable: ${secondary::class.java.simpleName}: ${secondary.message})")
        }
        context.forEach { appendLine(it) }
        appendLine()

        appendLine("--- exception ---")
        appendLine(stackTraceOf(throwable))
        appendLine()

        appendLine("--- last ${ring.size} logged events (oldest first) ---")
        if (ring.isEmpty()) appendLine("(nothing logged)") else ring.forEach { appendLine(it) }
    }

    fun stackTraceOf(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return writer.toString().trimEnd()
    }

    /** Test seam - drops every consumer and clears the ring. */
    fun reset() {
        ring.clear()
        sink = null
        environment = emptyList()
        contextProvider = null
    }
}
