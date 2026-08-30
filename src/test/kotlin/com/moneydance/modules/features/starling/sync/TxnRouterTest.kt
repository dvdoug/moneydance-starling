package com.moneydance.modules.features.starling.sync

import com.moneydance.modules.features.starling.api.BankTxn
import com.moneydance.modules.features.starling.api.MappableSource
import com.moneydance.modules.features.starling.api.SourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TxnRouterTest {
    private val personal = src("acc:main", "acc", "main", "Personal", SourceKind.MAIN)
    private val bills = src("acc:bills", "acc", "bills", "Bills", SourceKind.SPENDING, parent = "Personal")
    private val easy = src("sav:main", "sav", "emain", "Easy Saver", SourceKind.MAIN)
    private val holiday = src("sav:hol", "sav", "hol", "Holiday", SourceKind.SAVINGS, parent = "Easy Saver")
    private val all = listOf(personal, bills, easy, holiday)

    @Test
    fun unmappedSpendingInternalSkippedOnMain() {
        val txn = move("INTERNAL_TRANSFER", "bills")
        assertNull(TxnRouter.destination(txn, personal, all, mappedIds = setOf(personal.id)))
    }

    @Test
    fun mappedSpendingInternalStaysOnMain() {
        val txn = move("INTERNAL_TRANSFER", "bills")
        assertEquals(personal, TxnRouter.destination(txn, personal, all, setOf(personal.id, bills.id)))
    }

    @Test
    fun unmappedSavingsOnUsStaysOnPersonal() {
        val txn = move("ON_US_PAY_ME", "hol")
        assertEquals(personal, TxnRouter.destination(txn, personal, all, setOf(personal.id)))
    }

    @Test
    fun unmappedSpendingMerchantGoesToParent() {
        val tesco = BankTxn("1", "bills", -4.0, "GBP", "2026-01-01", "Tesco", "Tesco", false, "MASTER_CARD", "MERCHANT", null, "OUT")
        assertEquals(personal, TxnRouter.destination(tesco, bills, all, setOf(personal.id)))
    }

    @Test
    fun mappedSpendingMerchantGoesToSpace() {
        val tesco = BankTxn("1", "bills", -4.0, "GBP", "2026-01-01", "Tesco", "Tesco", false, "MASTER_CARD", "MERCHANT", null, "OUT")
        assertEquals(bills, TxnRouter.destination(tesco, bills, all, setOf(personal.id, bills.id)))
    }

    @Test
    fun unmappedSavingsFeedSkippedIfParentUnmapped() {
        val inn = BankTxn("1", "hol", 100.0, "GBP", "2026-01-01", "Douglas", "Transfer", false, "ON_US_PAY_ME", "CATEGORY", "main", "IN")
        assertNull(TxnRouter.destination(inn, holiday, all, setOf(personal.id)))
    }

    @Test
    fun unmappedSavingsFeedFoldsToParentIfParentMapped() {
        val inn = BankTxn("1", "hol", 100.0, "GBP", "2026-01-01", "Douglas", "Transfer", false, "ON_US_PAY_ME", "CATEGORY", "emain", "IN")
        assertEquals(easy, TxnRouter.destination(inn, holiday, all, setOf(personal.id, easy.id)))
    }

    @Test
    fun mappedSavingsPotWinsOverParentCatchAll() {
        val inn = BankTxn("1", "hol", 100.0, "GBP", "2026-01-01", "Douglas", "Transfer", false, "ON_US_PAY_ME", "CATEGORY", "emain", "IN")
        assertEquals(holiday, TxnRouter.destination(inn, holiday, all, setOf(personal.id, easy.id, holiday.id)))
    }

    @Test
    fun shouldFetchUnmappedPotWhenParentMapped() {
        assertTrue(TxnRouter.shouldFetch(holiday, all, setOf(easy.id)))
        assertFalse(TxnRouter.shouldFetch(holiday, all, setOf(personal.id)))
    }

    private fun move(source: String, otherCat: String) = BankTxn(
        id = "t",
        categoryUid = "main",
        amount = -100.0,
        currency = "GBP",
        date = "2026-01-01",
        merchant = "Pot",
        description = "Transfer",
        isPending = false,
        source = source,
        counterPartyType = "CATEGORY",
        counterPartyUid = otherCat,
        direction = "OUT"
    )

    private fun src(
        id: String,
        account: String,
        cat: String,
        name: String,
        kind: SourceKind,
        parent: String = name
    ) = MappableSource(id, account, cat, name, parent, "GBP", kind, archived = false)
}
