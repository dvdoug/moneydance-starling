package com.moneydance.modules.features.starling.sync

import com.moneydance.modules.features.starling.api.BankTxn
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class OpenHold(
    val key: String,
    val txn: BankTxn,
    /** Confirmed, or already split. The register text belongs to the user. */
    val userOwned: Boolean
)

data class MatchPair(
    val pendingKey: String,
    val posted: BankTxn,
    /** Leave description, memo, and splits. The FITID still moves to the settled item. */
    val keepRegisterText: Boolean = false
)

object PendingMatch {
    const val MAX_DAY_GAP: Long = 7

    fun uniquePairs(
        droppedPending: List<OpenHold>,
        newPosted: List<BankTxn>
    ): List<MatchPair> {
        val usedPending = mutableSetOf<String>()
        val usedPosted = mutableSetOf<String>()
        val pairs = mutableListOf<MatchPair>()
        // Same feed item once it settles, even when Merge kept the reminder name.
        collect(droppedPending, newPosted, usedPending, usedPosted, pairs, Mode.FEED_ITEM)
        collect(droppedPending, newPosted, usedPending, usedPosted, pairs, Mode.PAYEE)
        // Confirmed or split rows only: amount and date are unique, description is the user's.
        collect(droppedPending, newPosted, usedPending, usedPosted, pairs, Mode.USER_AMOUNT)
        return pairs
    }

    fun matches(pending: BankTxn, posted: BankTxn): Boolean =
        paired(pending, posted, Mode.PAYEE)

    private enum class Mode { FEED_ITEM, PAYEE, USER_AMOUNT }

    private fun collect(
        droppedPending: List<OpenHold>,
        newPosted: List<BankTxn>,
        usedPending: MutableSet<String>,
        usedPosted: MutableSet<String>,
        pairs: MutableList<MatchPair>,
        mode: Mode
    ) {
        for (hold in droppedPending) {
            if (hold.key in usedPending) continue
            if (mode == Mode.USER_AMOUNT && !hold.userOwned) continue
            val pending = hold.txn
            val hits = newPosted.filter { posted ->
                posted.id !in usedPosted && paired(pending, posted, mode)
            }
            if (hits.size != 1) continue
            val posted = hits[0]
            val reverse = droppedPending.count { other ->
                other.key !in usedPending &&
                    (mode != Mode.USER_AMOUNT || other.userOwned) &&
                    paired(other.txn, posted, mode)
            }
            if (reverse != 1) continue
            usedPending.add(hold.key)
            usedPosted.add(posted.id)
            val keepText = hold.userOwned && !samePayee(pending, posted)
            pairs.add(MatchPair(hold.key, posted, keepText))
        }
    }

    private fun paired(pending: BankTxn, posted: BankTxn, mode: Mode): Boolean {
        if (mode == Mode.FEED_ITEM) {
            if (pending.id.isBlank() || pending.id != posted.id) return false
        } else if (!withinDayGap(pending, posted)) {
            return false
        }
        if (mode == Mode.PAYEE && !samePayee(pending, posted)) return false
        if (!sameAmount(pending.amount, posted.amount)) return false
        return sameCurrency(pending, posted)
    }

    private fun sameAmount(a: Double, b: Double): Boolean = abs(abs(a) - abs(b)) < 0.001

    private fun sameCurrency(pending: BankTxn, posted: BankTxn): Boolean {
        val pCur = pending.currency.trim()
        val sCur = posted.currency.trim()
        return pCur.isEmpty() || sCur.isEmpty() || pCur.equals(sCur, ignoreCase = true)
    }

    private fun samePayee(pending: BankTxn, posted: BankTxn): Boolean =
        payeeKey(pending).equals(payeeKey(posted), ignoreCase = true)

    private fun withinDayGap(pending: BankTxn, posted: BankTxn): Boolean {
        val days = abs(ChronoUnit.DAYS.between(parseDate(pending.date), parseDate(posted.date)))
        return days <= MAX_DAY_GAP
    }

    private fun payeeKey(txn: BankTxn): String {
        val m = txn.merchant?.trim().orEmpty()
        if (m.isNotEmpty()) return m
        return txn.description?.trim().orEmpty()
    }

    private fun parseDate(iso: String): LocalDate = LocalDate.parse(iso.take(10))
}
