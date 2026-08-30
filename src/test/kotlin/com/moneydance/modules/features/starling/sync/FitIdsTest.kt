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
    }

    @Test
    fun pendingLabelIsDisplayOnly() {
        assertEquals("Tesco", FitIds.stripPendingLabel("Tesco"))
        assertEquals("Tesco", FitIds.stripPendingLabel("[PENDING] Tesco"))
        assertEquals("[PENDING] Tesco", FitIds.withPendingLabel("Tesco"))
        assertEquals("[PENDING] Tesco", FitIds.withPendingLabel("[PENDING] Tesco"))
        assertEquals("ol.orig-payee", FitIds.ORIG_PAYEE_TAG)
    }

    @Test
    fun settledDescriptionKeepsConfirmedPayee() {
        assertEquals(
            "Tesco",
            FitIds.settledDescription("[PENDING] Tesco", "TESCO STORES 123", alreadyConfirmed = true)
        )
        assertEquals(
            "TESCO STORES 123",
            FitIds.settledDescription("[PENDING] Tesco", "TESCO STORES 123", alreadyConfirmed = false)
        )
        assertEquals(
            "Waitrose",
            FitIds.settledDescription("Waitrose", "WAITROSE 2844", alreadyConfirmed = true)
        )
    }
}
