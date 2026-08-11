package com.portroyale.tiny.diagnostics

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import java.time.LocalDateTime
import java.util.zip.Deflater
import java.time.format.DateTimeFormatter

private const val DIAGNOSTICS_DIR = "PortRoyaleTiny/diagnostics/"
/** Enough history to compare "it worked yesterday" with today, without ever needing pruning by
 *  hand. Crash reports and screenshots are never rotated away - they are the rare, wanted ones. */
private const val KEEP_SESSIONS = 5

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

    /** Where a tester should look, for showing in-game. */
    var directoryPath: String? = null
        private set

    fun beginSession(version: String): FileHandle? = try {
        val dir = Gdx.files.external(DIAGNOSTICS_DIR)
        dir.mkdirs()
        directoryPath = dir.file().absolutePath
        rotate(dir)
        val file = dir.child("session-${stamp()}.log")
        file.writeString("Port Royale 2D Tiny $version\n", false)
        session = file
        file
    } catch (e: Exception) {
        println("Diagnostics: could not open a log file (${e.message}) - continuing without one")
        null
    }

    fun append(line: String) {
        val file = session ?: return
        try {
            file.writeString("$line\n", true)
        } catch (e: Exception) {
            session = null
            println("Diagnostics: log file became unwritable (${e.message}) - continuing without one")
        }
    }

    /** Writes a crash report as its own file so it is obvious which one to send, and returns the
     *  path to show the player. */
    fun writeCrashReport(report: String): String? = write("crash-${stamp()}.txt", report)

    /**
     * Grabs whatever is currently on screen. A visual bug ("the port labels vanished") takes one
     * keypress to capture and no words at all to describe afterwards.
     *
     * Only valid on the render thread with a live GL context, which is why the crash path treats
     * failure here as expected rather than exceptional - by the time some crashes are handled,
     * there is nothing left to read a framebuffer from.
     */
    fun writeScreenshot(prefix: String): String? = try {
        val pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        val dir = Gdx.files.external(DIAGNOSTICS_DIR)
        dir.mkdirs()
        val file = dir.child("$prefix-${stamp()}.png")
        // flipY, because a framebuffer's origin is bottom-left and a PNG's is top-left - without
        // it every screenshot came out upside down, which is a poor way to report a visual bug.
        PixmapIO.writePNG(file, pixmap, Deflater.DEFAULT_COMPRESSION, true)
        pixmap.dispose()
        file.file().absolutePath
    } catch (e: Throwable) {
        println("Diagnostics: no screenshot (${e.message})")
        null
    }

    private fun write(name: String, content: String): String? = try {
        val dir = Gdx.files.external(DIAGNOSTICS_DIR)
        dir.mkdirs()
        val file = dir.child(name)
        file.writeString(content, false)
        file.file().absolutePath
    } catch (e: Exception) {
        println("Diagnostics: could not write $name (${e.message})")
        null
    }

    private fun stamp(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

    private fun rotate(dir: FileHandle) {
        val sessions = dir.list(".log").sortedBy { it.name() }
        val excess = sessions.size - (KEEP_SESSIONS - 1)
        if (excess > 0) sessions.take(excess).forEach { it.delete() }
    }
}
