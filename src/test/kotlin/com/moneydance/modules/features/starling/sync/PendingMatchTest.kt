package com.moneydance.modules.features.starling.sync

import com.moneydance.modules.features.starling.api.BankTxn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingMatchTest {
    @Test
    fun settledFeedItemKeepsMergedReminder() {
        val hold = hold(
            key = "starling:pending:cat:salary",
            id = "salary",
            amount = 2500.0,
            payee = "Salary",
            date = "2026-10-27",
            userOwned = true
        )
        val posted = txn("salary", 2500.0, "ACME LTD", "2026-10-27", pending = false)
        val other = txn("other", 2500.0, "ACME LTD", "2026-10-27", pending = false)

        val pairs = PendingMatch.uniquePairs(listOf(hold), listOf(posted, other))

        assertEquals(1, pairs.size)
        assertEquals("starling:pending:cat:salary", pairs[0].pendingKey)
        assertEquals("salary", pairs[0].posted.id)
        assertTrue(pairs[0].keepRegisterText)
    }

    @Test
    fun sameFeedItemWithDifferentAmountDoesNotSettle() {
        val hold = hold("p", "salary", 2400.0, "Salary", "2026-10-27", userOwned = true)
        val posted = txn("salary", 2500.0, "ACME LTD", "2026-10-27", pending = false)
        assertEquals(0, PendingMatch.uniquePairs(listOf(hold), listOf(posted)).size)
    }

    @Test
    fun signFlipStillMatchesMerchant() {
        val hold = hold("p", "card", 12.5, "Tesco", "2026-10-20", userOwned = false)
        val posted = txn("settled", -12.5, "Tesco", "2026-10-21", pending = false)
        val pairs = PendingMatch.uniquePairs(listOf(hold), listOf(posted))
        assertEquals(1, pairs.size)
        assertFalse(pairs[0].keepRegisterText)
    }

    @Test
    fun renamedHoldMatchesOnAmountOnlyWhenTheUserOwnsIt() {
        val owned = hold("p", "old-id", 2500.0, "Salary", "2026-10-27", userOwned = true)
        val posted = txn("new-id", 2500.0, "ACME LTD", "2026-10-27", pending = false)
        val pairs = PendingMatch.uniquePairs(listOf(owned), listOf(posted))
        assertEquals(1, pairs.size)
        assertEquals("new-id", pairs[0].posted.id)
        assertTrue(pairs[0].keepRegisterText)
    }

    @Test
    fun unconfirmedHoldDoesNotAttachToADifferentMerchant() {
        val plain = hold("p", "old-id", 4.5, "Cafe A", "2026-10-27", userOwned = false)
        val posted = txn("new-id", -4.5, "Cafe B", "2026-10-27", pending = false)
        assertEquals(0, PendingMatch.uniquePairs(listOf(plain), listOf(posted)).size)
    }

    @Test
    fun twoSettledAmountsAreNotGuessed() {
        val owned = hold("p", "old-id", 2500.0, "Salary", "2026-10-27", userOwned = true)
        val a = txn("a", 2500.0, "ACME LTD", "2026-10-27", pending = false)
        val b = txn("b", 2500.0, "OTHER LTD", "2026-10-26", pending = false)
        assertEquals(0, PendingMatch.uniquePairs(listOf(owned), listOf(a, b)).size)
    }

    @Test
    fun merchantMatchOutsideSevenDaysStaysOpen() {
        val hold = hold("p", "card", -10.0, "Tesco", "2026-10-01", userOwned = false)
        val posted = txn("settled", -10.0, "Tesco", "2026-10-09", pending = false)
        assertEquals(0, PendingMatch.uniquePairs(listOf(hold), listOf(posted)).size)
    }

    private fun hold(
        key: String,
        id: String,
        amount: Double,
        payee: String,
        date: String,
        userOwned: Boolean
    ) = OpenHold(key, txn(id, amount, payee, date, pending = true), userOwned)

    private fun txn(id: String, amount: Double, payee: String, date: String, pending: Boolean) = BankTxn(
        id = id,
        categoryUid = "cat",
        amount = amount,
        currency = "GBP",
        date = date,
        merchant = payee,
        description = payee,
        isPending = pending,
        source = "FASTER_PAYMENTS_IN",
        counterPartyType = null,
        counterPartyUid = null,
        direction = if (amount < 0) "OUT" else "IN"
    )
}
