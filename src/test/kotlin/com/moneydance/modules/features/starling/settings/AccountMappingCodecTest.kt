package com.moneydance.modules.features.starling.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
