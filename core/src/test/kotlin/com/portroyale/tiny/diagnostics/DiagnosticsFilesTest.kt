package com.portroyale.tiny.diagnostics

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticsFilesTest {

    @Test
    fun `two files named in the same millisecond get different names`() {
        val dir = createTempDirectory("diag").toFile()
        try {
            val first = uniqueName(dir, "crash", "txt", 1_700_000_000_123L)
            File(dir, first).writeText("one")
            val second = uniqueName(dir, "crash", "txt", 1_700_000_000_123L)
            assertNotEquals(first, second)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a name already taken gets a suffix`() {
        val dir = createTempDirectory("diag").toFile()
        try {
            val plain = uniqueName(dir, "shot", "png", 42L)
            File(dir, plain).writeText("x")
            val second = uniqueName(dir, "shot", "png", 42L)
            assertEquals(plain.removeSuffix(".png") + "-2.png", second)
            File(dir, second).writeText("x")
            assertEquals(plain.removeSuffix(".png") + "-3.png", uniqueName(dir, "shot", "png", 42L))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the session log stops growing at its limit and says so once`() {
        val dir = createTempDirectory("diag").toFile()
        try {
            val log = File(dir, "session.log")
            val budget = LogBudget(100L)
            var notices = 0
            repeat(50) { i ->
                val text = budget.accept(log.length(), "line $i with some padding")
                if (text != null) {
                    if (text.startsWith("[log limit")) notices++
                    log.appendText(text)
                }
            }
            assertEquals(1, notices)
            val size = log.length()
            assertNull(budget.accept(log.length(), "late"))
            assertEquals(size, log.length())
            assertTrue(log.readText().trimEnd().endsWith("further lines are dropped]"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
