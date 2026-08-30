package com.moneydance.modules.features.starling.ui

import com.moneydance.modules.features.starling.sync.AccountSyncResult
import kotlin.test.Test
import kotlin.test.assertEquals

class ImportStatusTest {
    @Test
    fun upToDate() {
        assertEquals("Personal: up to date.", ImportStatus.line("Personal", AccountSyncResult()))
    }

    @Test
    fun addedPosted() {
        assertEquals(
            "Personal: added 2 transactions.",
            ImportStatus.line("Personal", AccountSyncResult(postedAdded = 2))
        )
    }
}
