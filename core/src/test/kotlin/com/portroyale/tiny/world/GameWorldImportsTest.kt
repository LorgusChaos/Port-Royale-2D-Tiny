package com.portroyale.tiny.world

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The world runs without a window - that is the point of moving it out of PortRoyaleGame (review
 * batch 2, decision 8). The compiler cannot hold that line: a `Gdx.graphics` read or an
 * `IconFactory` texture in [GameWorld] compiles fine, and the first sign would be the world's
 * tests dying for want of a GL context - or the game class quietly creeping back in.
 *
 * Vector2, MathUtils, Color and Pixmap stay allowed: they run in a plain JVM, and WorldMap is
 * built on Pixmap.
 */
class GameWorldImportsTest {

    private val worldFiles = listOf("GameWorld.kt", "WorldEvents.kt", "ResolvedPort.kt")
        .map { File("src/main/kotlin/com/portroyale/tiny/world/$it") }

    /** Each needs a live application, a GL context or a sound device - or is the game itself. */
    private val forbidden = listOf(
        "com.badlogic.gdx.Gdx",
        "com.badlogic.gdx.scenes",
        "com.badlogic.gdx.audio",
        "com.badlogic.gdx.graphics.Texture",
        "com.badlogic.gdx.graphics.g2d",
        "com.badlogic.gdx.graphics.glutils",
        "com.portroyale.tiny.ui",
        "com.portroyale.tiny.audio",
        "com.portroyale.tiny.PortRoyaleGame"
    )

    private fun importsOf(file: File): List<String> =
        file.readLines().map { it.trim() }.filter { it.startsWith("import ") }.map { it.removePrefix("import ").trim() }

    @Test
    fun `the world imports nothing that needs a window, a sound device or the game class`() {
        for (file in worldFiles) {
            assertTrue(file.isFile, "the guard is looking in the wrong place: ${file.absolutePath}")
            val offending = importsOf(file).filter { name -> forbidden.any { name == it || name.startsWith("$it.") } }
            assertTrue(offending.isEmpty(), "${file.name} imports $offending")
        }
        // A guard that reads nothing passes everything.
        assertTrue(importsOf(worldFiles.first()).isNotEmpty())
    }

    @Test
    fun `the world does not reach those classes by their full names either`() {
        val gdxCall = Regex("""\bGdx\.""")
        for (file in worldFiles) {
            val code = file.readLines().filterNot {
                val line = it.trimStart()
                line.startsWith("*") || line.startsWith("/*") || line.startsWith("//") || line.startsWith("import ")
            }
            val offending = code.filter { line -> gdxCall.containsMatchIn(line) || forbidden.any { line.contains(it) } }
            assertTrue(offending.isEmpty(), "${file.name}: $offending")
        }
    }
}
