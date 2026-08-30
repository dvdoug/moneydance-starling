package com.moneydance.modules.features.starling.sync

import com.infinitekind.moneydance.model.OnlineTxn
import com.moneydance.modules.features.starling.api.BankTxn

object FitIds {
    const val PROTOCOL: Int = OnlineTxn.PROTO_TYPE_OFX
    const val PARAM_PENDING: String = "starling.pending"
    const val PREFIX_POSTED: String = "starling:"
    const val PREFIX_PENDING: String = "starling:pending:"
    const val PENDING_LABEL: String = "[PENDING] "
    /** Moneydance similar-payee tag. Must not include [PENDING] — the matcher is prefix-based. */
    const val ORIG_PAYEE_TAG: String = "ol.orig-payee"

    fun posted(categoryUid: String, txnId: String): String = "$PREFIX_POSTED$categoryUid:$txnId"

    fun pendingKey(categoryUid: String, txn: BankTxn): String =
        "$PREFIX_PENDING$categoryUid:${txn.id}"

    fun feedCategory(txn: BankTxn, fallback: String): String =
        txn.categoryUid.trim().ifBlank { fallback }

    fun isOurs(fitId: String?): Boolean {
        val v = fitId ?: return false
        return v.startsWith(PREFIX_POSTED)
    }

    fun isPending(fitId: String?): Boolean {
        val v = fitId ?: return false
        return v.startsWith(PREFIX_PENDING)
    }

    fun stripPendingLabel(text: String): String = text.removePrefix(PENDING_LABEL)

    fun withPendingLabel(text: String): String = PENDING_LABEL + stripPendingLabel(text)

    /** Confirmed rows keep the user's Description minus our label. Unconfirmed take the settled payee. */
    fun settledDescription(current: String, postedPayee: String, alreadyConfirmed: Boolean): String {
        if (!alreadyConfirmed) return postedPayee
        return stripPendingLabel(current).ifBlank { postedPayee }
    }
}
