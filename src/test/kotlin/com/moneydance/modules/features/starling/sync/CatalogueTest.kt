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
        assertTrue(charity.displayName.startsWith(" "))
    }

    @Test
    fun liveSpaceNotArchived() {
        val acc = StarlingAccount("acc", "main", "Personal", "GBP", "PRIMARY", null)
        val live = listOf(StarlingSpace("acc", "bills", "Bills", SourceKind.SPENDING, archived = false, parentName = "Personal"))
        val rows = Catalogue.stitch(listOf(acc), live, emptyList())
        val bills = rows.first { it.categoryUid == "bills" }
        assertEquals(false, bills.archived)
        assertTrue(bills.displayName.startsWith(" "))
        assertEquals("Personal", rows.first().displayName)
    }

    @Test
    fun groupsChildrenUnderAccount() {
        val personal = StarlingAccount("p", "pm", "Personal", "GBP", "PRIMARY", null)
        val saver = StarlingAccount("s", "sm", "Easy Saver", "GBP", "SAVINGS", null)
        val live = listOf(
            StarlingSpace("s", "hol", "Home repairs", SourceKind.SAVINGS, parentName = "Easy Saver"),
            StarlingSpace("p", "bills", "Bills", SourceKind.SPENDING, parentName = "Personal")
        )
        val names = Catalogue.stitch(listOf(saver, personal), live, emptyList()).map { it.name }
        assertEquals(listOf("Personal", "Bills", "Easy Saver", "Home repairs"), names)
    }
}
