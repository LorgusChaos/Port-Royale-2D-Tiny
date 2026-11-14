package com.portroyale.tiny.diagnostics

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The rule from CLAUDE.md - every chance the game takes comes from [GameRandom] - checked against
 * the source itself (REVIEW.md, finding 4). A stray `MathUtils.random` or `.random()` makes an
 * outcome impossible to replay from the session seed and forces tests into statistical margins,
 * and nothing else would ever notice one.
 *
 * Two files are exempt: GameRandom itself, and IconFactory, whose seeded `kotlin.random.Random`
 * paints texture noise and decides nothing.
 */
class RandomnessSourceTest {

    private val sources = File("src/main/kotlin")
    private val exempt = setOf("GameRandom.kt", "IconFactory.kt")
    private val forbidden = listOf(
        Regex("""MathUtils\.random\s*\("""),
        Regex("""\.random\(\)"""),
        Regex("""\.shuffled\(\)"""),
        Regex("""kotlin\.random""")
    )

    private fun isComment(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
    }

    @Test
    fun `the guard is looking at the game's own sources`() {
        assertTrue(File(sources, "com/portroyale/tiny/diagnostics/GameRandom.kt").isFile, "no sources under ${sources.absolutePath}")
    }

    @Test
    fun `game logic takes its chances only from GameRandom`() {
        val offences = sources.walk()
            .filter { it.isFile && it.extension == "kt" && it.name !in exempt }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val code = line.substringBefore(" //")
                    if (!isComment(line) && forbidden.any { it.containsMatchIn(code) }) "${file.path}:${index + 1}: ${line.trim()}" else null
                }
            }
            .toList()
        assertTrue(offences.isEmpty(), "randomness outside GameRandom:\n" + offences.joinToString("\n"))
    }
}
