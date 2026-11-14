package com.portroyale.tiny.economy

import com.portroyale.tiny.factions.Faction
import com.portroyale.tiny.factions.REPUTATION_MIN
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A commandant's loan and the overdue rule (REVIEW.md, finding 42 - the class had no test). */
class LoanTest {

    private fun indebted(): Player = Player().also { it.takeLoan(LOAN_OFFERS.first(), Faction.SPAIN, currentGameMinutes = 0f) }

    @Test
    fun `before the deadline a loan stays as it was taken`() {
        val player = indebted()
        val loan = player.loan!!

        assertNull(player.checkLoanDeadline(loan.deadlineGameMinutes - 1f))
        assertFalse(loan.overdue)
        assertEquals(LOAN_OFFERS.first().repayAmount, loan.repayAmount)
        assertEquals(0, player.reputation[Faction.SPAIN] ?: 0)
    }

    @Test
    fun `at the deadline the debt doubles and the lender's navy turns hostile`() {
        val player = indebted()
        val loan = player.loan!!

        assertSame(loan, player.checkLoanDeadline(loan.deadlineGameMinutes), "going overdue was not reported")
        assertTrue(loan.overdue)
        assertEquals(LOAN_OFFERS.first().repayAmount * 2, loan.repayAmount)
        assertEquals(REPUTATION_MIN, player.reputation[Faction.SPAIN])
    }

    @Test
    fun `an overdue loan doubles only once`() {
        val player = indebted()
        val loan = player.loan!!
        player.checkLoanDeadline(loan.deadlineGameMinutes)

        assertNull(player.checkLoanDeadline(loan.deadlineGameMinutes + 10_000f), "reported overdue twice")
        assertEquals(LOAN_OFFERS.first().repayAmount * 2, loan.repayAmount)
    }

    @Test
    fun `with no loan there is nothing to fall due`() {
        assertNull(Player().checkLoanDeadline(1_000_000f))
    }

    @Test
    fun `every offer asks back more than it lends and gives time to do it`() {
        for (offer in LOAN_OFFERS) {
            assertTrue(offer.repayAmount > offer.principal, "$offer")
            assertTrue(offer.termGameMinutes > 0f, "$offer")
        }
    }
}
