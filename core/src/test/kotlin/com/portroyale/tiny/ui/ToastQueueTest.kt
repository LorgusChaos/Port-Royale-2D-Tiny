package com.portroyale.tiny.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The event toast's queue (REVIEW.md, finding 20): nothing shown is lost to what comes next. */
class ToastQueueTest {

    @Test
    fun `messages are shown in the order they came, each after the last is closed`() {
        val queue = ToastQueue<String>()
        assertTrue(queue.add(ToastNotice("a captain lost")), "the first message waited")
        assertFalse(queue.add(ToastNotice("a loan overdue")), "the second message went over the first")
        queue.add(ToastNotice("a chest found"))

        val shown = mutableListOf<String>()
        while (queue.isShowing) {
            shown += queue.current!!.payload
            queue.dismiss()
        }

        assertEquals(listOf("a captain lost", "a loan overdue", "a chest found"), shown)
        assertNull(queue.current)
    }

    @Test
    fun `a message that arrives during a question waits behind it`() {
        val queue = ToastQueue<String>()
        var fought = false
        queue.add(ToastChoice("Fight or sail on?", onPrimary = { fought = true }, onSecondary = {}))
        queue.add(ToastNotice("a captain lost"))

        assertEquals("Fight or sail on?", queue.current!!.payload, "the question was pushed aside")
        queue.choose(primary = true)

        assertTrue(fought)
        assertEquals("a captain lost", queue.current!!.payload)
    }

    @Test
    fun `a question cannot be closed without an answer`() {
        val queue = ToastQueue<String>()
        queue.add(ToastChoice("Fight or sail on?", onPrimary = {}, onSecondary = {}))

        queue.dismiss()

        assertTrue(queue.current is ToastChoice, "the question was waved away")
    }

    @Test
    fun `the answer to a question is acted on exactly once`() {
        val queue = ToastQueue<String>()
        var primary = 0
        var secondary = 0
        queue.add(ToastChoice("Fight or sail on?", onPrimary = { primary++ }, onSecondary = { secondary++ }))

        queue.choose(primary = false)
        queue.choose(primary = false)
        queue.choose(primary = true)

        assertEquals(0, primary)
        assertEquals(1, secondary)
        assertFalse(queue.isShowing)
    }

    @Test
    fun `a message raised by an answer comes after what was already waiting`() {
        val queue = ToastQueue<String>()
        queue.add(ToastChoice("Fight or sail on?", onPrimary = { queue.add(ToastNotice("battle report")) }, onSecondary = {}))
        queue.add(ToastNotice("a loan overdue"))

        queue.choose(primary = true)

        assertEquals("a loan overdue", queue.current!!.payload)
        queue.dismiss()
        assertEquals("battle report", queue.current!!.payload)
    }

    @Test
    fun `clearing drops everything pending`() {
        val queue = ToastQueue<String>()
        queue.add(ToastNotice("one"))
        queue.add(ToastChoice("two", onPrimary = {}, onSecondary = {}))

        queue.clear()

        assertFalse(queue.isShowing)
    }
}
