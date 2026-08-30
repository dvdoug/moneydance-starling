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
    private val current = src("acc:main", "acc", "main", "Current", SourceKind.MAIN)
    private val bills = src("acc:bills", "acc", "bills", "Bills", SourceKind.SPENDING, parent = "Current")
    private val easy = src("sav:main", "sav", "emain", "Easy Saver", SourceKind.MAIN)
    private val holiday = src("sav:hol", "sav", "hol", "Holiday", SourceKind.SAVINGS, parent = "Easy Saver")
    private val all = listOf(current, bills, easy, holiday)

    @Test
    fun unmappedSpendingInternalSkippedOnMain() {
        val txn = move("INTERNAL_TRANSFER", "bills")
        assertNull(TxnRouter.destination(txn, current, all, mappedIds = setOf(current.id)))
    }

    @Test
    fun mappedSpendingInternalStaysOnMain() {
        val txn = move("INTERNAL_TRANSFER", "bills")
        assertEquals(current, TxnRouter.destination(txn, current, all, setOf(current.id, bills.id)))
    }

    @Test
    fun unmappedSavingsOnUsStaysOnCurrent() {
        val txn = move("ON_US_PAY_ME", "hol")
        assertEquals(current, TxnRouter.destination(txn, current, all, setOf(current.id)))
    }

    @Test
    fun unmappedSpendingMerchantGoesToParent() {
        val tesco = BankTxn("1", "bills", -4.0, "GBP", "2026-01-01", "Tesco", "Tesco", false, "MASTER_CARD", "MERCHANT", null, "OUT")
        assertEquals(current, TxnRouter.destination(tesco, bills, all, setOf(current.id)))
    }

    @Test
    fun mappedSpendingMerchantGoesToSpace() {
        val tesco = BankTxn("1", "bills", -4.0, "GBP", "2026-01-01", "Tesco", "Tesco", false, "MASTER_CARD", "MERCHANT", null, "OUT")
        assertEquals(bills, TxnRouter.destination(tesco, bills, all, setOf(current.id, bills.id)))
    }

    @Test
    fun unmappedSavingsFeedSkippedIfParentUnmapped() {
        val inn = BankTxn("1", "hol", 100.0, "GBP", "2026-01-01", "Douglas", "Transfer", false, "ON_US_PAY_ME", "CATEGORY", "main", "IN")
        assertNull(TxnRouter.destination(inn, holiday, all, setOf(current.id)))
    }

    @Test
    fun unmappedSavingsFeedFoldsToParentIfParentMapped() {
        val inn = BankTxn("1", "hol", 100.0, "GBP", "2026-01-01", "Douglas", "Transfer", false, "ON_US_PAY_ME", "CATEGORY", "emain", "IN")
        assertEquals(easy, TxnRouter.destination(inn, holiday, all, setOf(current.id, easy.id)))
    }

    @Test
    fun mappedSavingsSpaceWinsOverParentCatchAll() {
        val inn = BankTxn("1", "hol", 100.0, "GBP", "2026-01-01", "Douglas", "Transfer", false, "ON_US_PAY_ME", "CATEGORY", "emain", "IN")
        assertEquals(holiday, TxnRouter.destination(inn, holiday, all, setOf(current.id, easy.id, holiday.id)))
    }

    @Test
    fun spaceSideHolderOnUsSkippedWhenOtherAccountMapped() {
        assertNull(TxnRouter.destination(holderOnUs("IN"), holiday, all, setOf(current.id, easy.id, holiday.id)))
        assertNull(TxnRouter.destination(holderOnUs("OUT"), holiday, all, setOf(current.id, easy.id, holiday.id)))
    }

    @Test
    fun spaceSideHolderOnUsKeptIfOtherAccountUnmapped() {
        val inn = holderOnUs("IN")
        assertEquals(holiday, TxnRouter.destination(inn, holiday, all, setOf(holiday.id)))
    }

    @Test
    fun leftoverHolderOnUsNotFoldedWhenOtherAccountMapped() {
        assertNull(TxnRouter.destination(holderOnUs("IN"), holiday, all, setOf(current.id, easy.id)))
        assertNull(TxnRouter.destination(holderOnUs("OUT"), holiday, all, setOf(current.id, easy.id)))
    }

    @Test
    fun spaceInterestStillImportsWhenOtherAccountMapped() {
        val interest = BankTxn(
            "1", "hol", 1.23, "GBP", "2026-01-01", "Starling Bank", "Interest",
            false, "INTEREST_PAYMENT", "STARLING", null, "IN"
        )
        assertEquals(holiday, TxnRouter.destination(interest, holiday, all, setOf(current.id, easy.id, holiday.id)))
    }

    @Test
    fun transferCounterpartIsMappedSpace() {
        val txn = move("ON_US_PAY_ME", "hol")
        assertEquals(
            holiday,
            TxnRouter.transferCounterpart(txn, current, all, setOf(current.id, easy.id, holiday.id))
        )
    }

    @Test
    fun transferCounterpartFoldsUnmappedSavingsToParent() {
        val txn = move("ON_US_PAY_ME", "hol")
        assertEquals(easy, TxnRouter.transferCounterpart(txn, current, all, setOf(current.id, easy.id)))
    }

    @Test
    fun transferCounterpartNullIfSavingsUnmapped() {
        val txn = move("ON_US_PAY_ME", "hol")
        assertNull(TxnRouter.transferCounterpart(txn, current, all, setOf(current.id)))
    }

    @Test
    fun spendingSpaceInternalSkippedWhenParentMapped() {
        val inn = BankTxn(
            "1", "bills", 100.0, "GBP", "2026-01-01", "Current", "Transfer",
            false, "INTERNAL_TRANSFER", "CATEGORY", "main", "IN"
        )
        assertNull(TxnRouter.destination(inn, bills, all, setOf(current.id, bills.id)))
    }

    @Test
    fun spendingSpaceInternalKeptIfParentUnmapped() {
        val inn = BankTxn(
            "1", "bills", 100.0, "GBP", "2026-01-01", "Current", "Transfer",
            false, "INTERNAL_TRANSFER", "CATEGORY", "main", "IN"
        )
        assertEquals(bills, TxnRouter.destination(inn, bills, all, setOf(bills.id)))
    }

    @Test
    fun shouldFetchUnmappedSpaceWhenParentMapped() {
        assertTrue(TxnRouter.shouldFetch(holiday, all, setOf(easy.id)))
        assertFalse(TxnRouter.shouldFetch(holiday, all, setOf(current.id)))
    }

    private fun holderOnUs(direction: String) = BankTxn(
        id = "1",
        categoryUid = "hol",
        amount = if (direction == "OUT") -100.0 else 100.0,
        currency = "GBP",
        date = "2026-01-01",
        merchant = "Account holder",
        description = "Transfer into Easy Saver",
        isPending = false,
        source = "ON_US_PAY_ME",
        counterPartyType = "CUSTOMER",
        counterPartyUid = "holder-uid",
        direction = direction
    )

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
