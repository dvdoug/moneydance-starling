package com.moneydance.modules.features.starling.sync

import com.moneydance.modules.features.starling.api.SourceKind
import com.moneydance.modules.features.starling.api.StarlingAccount
import com.moneydance.modules.features.starling.api.StarlingSpace
import com.moneydance.modules.features.starling.settings.CatalogueEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogueTest {
    @Test
    fun archivedWhenMissingFromLiveList() {
        val acc = StarlingAccount("acc", "main", "Personal", "GBP", "PRIMARY", null)
        val stored = listOf(
            CatalogueEntry("acc", "old", "Charity", SourceKind.SPENDING, "Personal")
        )
        val rows = Catalogue.stitch(listOf(acc), emptyList(), stored)
        val charity = rows.first { it.categoryUid == "old" }
        assertTrue(charity.archived)
        assertTrue(charity.displayName.contains("(archived)"))
    }

    @Test
    fun liveSpaceNotArchived() {
        val acc = StarlingAccount("acc", "main", "Personal", "GBP", "PRIMARY", null)
        val live = listOf(StarlingSpace("acc", "bills", "Bills", SourceKind.SPENDING, archived = false, parentName = "Personal"))
        val rows = Catalogue.stitch(listOf(acc), live, emptyList())
        val bills = rows.first { it.categoryUid == "bills" }
        assertEquals(false, bills.archived)
    }
}
