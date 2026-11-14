package com.portroyale.tiny.diagnostics

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.Deflater
import java.time.format.DateTimeFormatter

private const val DIAGNOSTICS_DIR = "PortRoyaleTiny/diagnostics/"
/** Enough history to compare "it worked yesterday" with today, without ever needing pruning by
 *  hand. Crash reports and screenshots are never rotated away - they are the rare, wanted ones. */
private const val KEEP_SESSIONS = 5
/** A session log that reaches this size stops growing (REVIEW.md, finding 37). */
internal const val MAX_LOG_BYTES = 5L * 1024 * 1024

private val STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")

/** A free file name in [dir]: "base-yyyyMMdd-HHmmss-SSS.ext" for [timestamp] (epoch millis), and
 *  with "-2", "-3", ... before the extension if even that is taken - two crash reports or
 *  screenshots in the same moment must not overwrite each other (REVIEW.md, finding 37). */
internal fun uniqueName(dir: java.io.File, base: String, extension: String, timestamp: Long): String {
    val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
    val stem = "$base-${time.format(STAMP_FORMAT)}"
    var name = "$stem.$extension"
    var n = 2
    while (java.io.File(dir, name).exists()) name = "$stem-${n++}.$extension"
    return name
}

/** Decides what, if anything, one log line appends to a file already [limit] bytes at most:
 *  the line itself while there is room, then one closing notice, then nothing for the rest of
 *  the session. */
internal class LogBudget(private val limit: Long) {
    private var stopped = false

    fun accept(currentLength: Long, line: String): String? {
        if (stopped) return null
        if (currentLength >= limit) {
            stopped = true
            return "[log limit of $limit bytes reached - further lines are dropped]\n"
        }
        return "$line\n"
    }
}

/**
 * Everything a tester might need to hand over, in one directory: session logs, crash reports and
 * screenshots, all under the user's home beside the saves. One folder, zip it, send it.
 *
 * Deliberately not next to the executable - an install directory is frequently read-only, and a
 * diagnostic that cannot be written precisely when something goes wrong is worse than none. Every
 * operation here swallows its own failures for the same reason: logging must never be the thing
 * that stops the game.
 *
 * Kept apart from [GameLog] so that the log's formatting and buffering stay testable without a
 * live libGDX backend.
 */
object DiagnosticsFiles {

    private var session: FileHandle? = null
    private var budget = LogBudget(MAX_LOG_BYTES)

    /** Where a tester should look, for showing in-game. */
    var directoryPath: String? = null
        private set

    fun beginSession(version: String): FileHandle? = try {
        val dir = Gdx.files.external(DIAGNOSTICS_DIR)
        dir.mkdirs()
        directoryPath = dir.file().absolutePath
        rotate(dir)
        val file = dir.child(uniqueName(dir.file(), "session", "log", System.currentTimeMillis()))
        file.writeString("Port Royale 2D Tiny $version\n", false)
        session = file
        budget = LogBudget(MAX_LOG_BYTES)
        file
    } catch (e: Exception) {
        println("Diagnostics: could not open a log file (${e.message}) - continuing without one")
        null
    }

    fun append(line: String) {
        val file = session ?: return
        try {
            val text = budget.accept(file.length(), line) ?: return
            file.writeString(text, true)
        } catch (e: Exception) {
            session = null
            println("Diagnostics: log file became unwritable (${e.message}) - continuing without one")
        }
    }

    /** Writes a crash report as its own file so it is obvious which one to send, and returns the
     *  path to show the player. */
    fun writeCrashReport(report: String): String? = write("crash", "txt", report)

    /**
     * Grabs whatever is currently on screen. A visual bug ("the port labels vanished") takes one
     * keypress to capture and no words at all to describe afterwards.
     *
     * Only valid on the render thread with a live GL context, which is why the crash path treats
     * failure here as expected rather than exceptional - by the time some crashes are handled,
     * there is nothing left to read a framebuffer from.
     */
    fun writeScreenshot(prefix: String): String? = try {
        val dir = Gdx.files.external(DIAGNOSTICS_DIR)
        dir.mkdirs()
        grabFrameBuffer(dir.child(uniqueName(dir.file(), prefix, "png", System.currentTimeMillis())))
    } catch (e: Throwable) {
        println("Diagnostics: no screenshot (${e.message})")
        null
    }

    /** The same grab, to an exact path - for the --shot launch (see ShotRequest), whose caller
     *  needs to know where the picture is without searching the diagnostics folder for it. */
    fun writeScreenshotTo(path: String): String? = try {
        val file = Gdx.files.absolute(java.io.File(path).absolutePath)
        file.parent().mkdirs()
        grabFrameBuffer(file)
    } catch (e: Throwable) {
        println("Diagnostics: no screenshot at $path (${e.message})")
        null
    }

    private fun grabFrameBuffer(file: FileHandle): String {
        val pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        try {
            // flipY, because a framebuffer's origin is bottom-left and a PNG's is top-left - without
            // it every screenshot came out upside down, which is a poor way to report a visual bug.
            PixmapIO.writePNG(file, pixmap, Deflater.DEFAULT_COMPRESSION, true)
        } finally {
            pixmap.dispose()
        }
        return file.file().absolutePath
    }

    private fun write(base: String, extension: String, content: String): String? = try {
        val dir = Gdx.files.external(DIAGNOSTICS_DIR)
        dir.mkdirs()
        val name = uniqueName(dir.file(), base, extension, System.currentTimeMillis())
        val file = dir.child(name)
        file.writeString(content, false)
        file.file().absolutePath
    } catch (e: Exception) {
        println("Diagnostics: could not write $base.$extension (${e.message})")
        null
    }

    private fun rotate(dir: FileHandle) {
        val sessions = dir.list(".log").sortedBy { it.name() }
        val excess = sessions.size - (KEEP_SESSIONS - 1)
        if (excess > 0) sessions.take(excess).forEach { it.delete() }
    }
}
