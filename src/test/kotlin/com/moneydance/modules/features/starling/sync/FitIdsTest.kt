package com.moneydance.modules.features.starling.sync

import com.moneydance.modules.features.starling.api.BankTxn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FitIdsTest {
    @Test
    fun postedAndPendingPrefixes() {
        val txn = BankTxn("fid", "cat", -1.0, "GBP", "2026-01-01", "Tesco", "Tesco", true, "MASTER_CARD", null, null, "OUT")
        val posted = FitIds.posted("cat", "fid")
        val pending = FitIds.pendingKey("cat", txn)
        assertEquals("starling:cat:fid", posted)
        assertEquals("starling:pending:cat:fid", pending)
        assertTrue(FitIds.isOurs(posted))
        assertTrue(FitIds.isPending(pending))
        assertTrue(FitIds.isOurs(pending))
    }
}
