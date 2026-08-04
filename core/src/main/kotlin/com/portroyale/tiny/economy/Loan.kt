package com.portroyale.tiny.economy

import com.portroyale.tiny.factions.Faction

private const val GAME_MINUTES_PER_DAY = 24 * 60

/** One of the three fixed sums a commandant will lend, with the term picked roughly for how long
 *  it'd realistically take to earn that much back through ordinary trading - a rough first pass,
 *  not tuned against real playtesting yet. */
data class LoanOffer(val principal: Int, val repayAmount: Int, val termGameMinutes: Float)

val LOAN_OFFERS = listOf(
    LoanOffer(1000, 1300, 5 * GAME_MINUTES_PER_DAY.toFloat()),
    LoanOffer(3000, 4200, 10 * GAME_MINUTES_PER_DAY.toFloat()),
    LoanOffer(6000, 9000, 15 * GAME_MINUTES_PER_DAY.toFloat())
)

/** An outstanding debt to one faction's commandant - see Player.loan. Only one at a time; the
 *  faction that issued it is the one whose ships turn hostile if it goes [overdue] (see
 *  PortRoyaleGame's loan-overdue check), which doubles [repayAmount] once, on the spot. */
class Loan(
    val faction: Faction,
    val principal: Int,
    var repayAmount: Int,
    val takenAtGameMinutes: Float,
    val deadlineGameMinutes: Float,
    var overdue: Boolean = false
)
