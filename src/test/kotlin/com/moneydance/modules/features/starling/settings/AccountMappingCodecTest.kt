package com.moneydance.modules.features.starling.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountMappingCodecTest {
    @Test
    fun mappingRoundTrip() {
        val original = listOf(
            AccountMapping("acc:main", "uuid-1", "2026-03-01", "2026-03-10", "Personal", null)
        )
        val parsed = AccountMappingCodec.fromJson(AccountMappingCodec.toJson(original)).single()
        assertEquals("acc:main", parsed.sourceId)
        assertEquals("uuid-1", parsed.moneydanceAccountUuid)
        assertEquals("Personal", parsed.sourceName)
    }

    @Test
    fun patIndexRoundTrip() {
        val json = PatIndexCodec.toJson(listOf(SavedPat("id1", "Douglas (Personal)", true)))
        val parsed = PatIndexCodec.fromJson(json).single()
        assertEquals("id1", parsed.id)
        assertEquals("Douglas (Personal)", parsed.description)
        assertTrue(parsed.historyWalked)
    }

    @Test
    fun emptyJson() {
        assertEquals(emptyList(), AccountMappingCodec.fromJson(null))
        assertEquals(emptyList(), PatIndexCodec.fromJson(""))
        assertFalse(CatalogueCodec.fromJson(null).isNotEmpty())
    }

    @Test
    fun omittedFromIsNullNotThisMonth() {
        val mapping = AccountMapping("acc:main", "uuid")
        assertNull(mapping.syncStartDate)
    }

    @Test
    fun newRowGetsDefaultFromExistingKeepsBlank() {
        val existing = AccountMapping("acc:main", "uuid", null, "2026-08-01")
        assertNull(AccountMapping.fromDateForRow(existing))
        assertEquals("2026-08-01", AccountMapping.fromDateForRow(existing.copy(syncStartDate = "2026-08-01")))
        assertEquals(AccountMapping.defaultStartDate(), AccountMapping.fromDateForRow(null))
    }

    @Test
    fun keepUnlistedPreservesSavedWhenRefreshOmitsThem() {
        val table = listOf(AccountMapping("a", "u1", "2026-08-01"))
        val saved = listOf(
            AccountMapping("a", "u1", "2026-01-01", "2026-08-20"),
            AccountMapping("b", "u2", "2026-03-01", "2026-08-15")
        )
        val merged = AccountMapping.keepUnlisted(table, saved, setOf("a"))
        assertEquals(2, merged.size)
        assertEquals("b", merged[1].sourceId)
        assertEquals("2026-08-15", merged[1].lastPostedDate)
    }
}
