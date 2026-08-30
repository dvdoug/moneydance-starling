package com.moneydance.modules.features.starling.sync

import com.infinitekind.moneydance.model.OnlineTxn
import com.moneydance.modules.features.starling.api.BankTxn

object FitIds {
    const val PROTOCOL: Int = OnlineTxn.PROTO_TYPE_OFX
    const val PARAM_PENDING: String = "starling.pending"
    const val PREFIX_POSTED: String = "starling:"
    const val PREFIX_PENDING: String = "starling:pending:"
    const val PENDING_LABEL: String = "[PENDING] "

    fun posted(categoryUid: String, txnId: String): String = "$PREFIX_POSTED$categoryUid:$txnId"

    fun pendingKey(categoryUid: String, txn: BankTxn): String =
        "$PREFIX_PENDING$categoryUid:${txn.id}"

    fun isOurs(fitId: String?): Boolean {
        val v = fitId ?: return false
        return v.startsWith(PREFIX_POSTED)
    }

    fun isPending(fitId: String?): Boolean {
        val v = fitId ?: return false
        return v.startsWith(PREFIX_PENDING)
    }
}
