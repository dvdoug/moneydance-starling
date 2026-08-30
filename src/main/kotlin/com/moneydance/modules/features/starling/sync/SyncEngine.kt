package com.moneydance.modules.features.starling.sync

import com.infinitekind.moneydance.model.Account
import com.infinitekind.moneydance.model.AccountBook
import com.infinitekind.moneydance.model.ParentTxn
import com.moneydance.modules.features.starling.api.BankTxn
import com.moneydance.modules.features.starling.api.MappableSource
import com.moneydance.modules.features.starling.settings.AccountMapping
import java.time.LocalDate

data class AccountSyncResult(
    val postedAdded: Int = 0,
    val postedSkipped: Int = 0,
    val pendingAdded: Int = 0,
    val pendingUpdated: Int = 0,
    val pendingRemoved: Int = 0,
    val pendingPromoted: Int = 0,
    val pendingPayees: List<String> = emptyList(),
    val lastPostedDate: String? = null,
    val error: String? = null
)

class SyncEngine(
    private val book: AccountBook,
    private val processDownloaded: (Account) -> Unit = {}
) {

    fun apply(
        mapping: AccountMapping,
        source: MappableSource,
        txns: List<BankTxn>,
        sources: List<MappableSource> = emptyList(),
        mappings: List<AccountMapping> = emptyList()
    ): AccountSyncResult {
        val mdAccount = book.getAccountByUUID(mapping.moneydanceAccountUuid)
            ?: return AccountSyncResult(error = "Mapped Moneydance account is missing.")
        val mdCurrency = MdAccess.currencyId(mdAccount)
        val srcCurrency = source.currency.trim()
        if (srcCurrency.isNotEmpty() && !srcCurrency.equals(mdCurrency, ignoreCase = true)) {
            return AccountSyncResult(
                error = "Currency mismatch: Starling $srcCurrency vs Moneydance $mdCurrency."
            )
        }

        val known = collectRegisterFitIds(mdAccount)
        pruneStaleDownloads(mdAccount, known)
        val mappedIds = mappings.filter { it.moneydanceAccountUuid.isNotBlank() }.map { it.sourceId }.toSet()
        val mappingBySource = mappings.associateBy { it.sourceId }
        val pendingLf = txns.filter { it.isPending }
        val postedLf = txns.filter { !it.isPending && it.id.isNotBlank() }

        val pendingRegister = mutableMapOf<String, ParentTxn>()
        for (txn in MdAccess.txnsForAccount(book, mdAccount)) {
            if (txn !is ParentTxn) continue
            if (!MdAccess.sameAccount(MdAccess.accountOf(txn), mdAccount)) continue
            if (!MdAccess.isNew(txn)) continue
            val id = MdAccess.registerFiTxnId(txn, FitIds.PROTOCOL)
            if (FitIds.isPending(id) && id != null) pendingRegister[id] = txn
        }

        val desiredPending = linkedMapOf<String, BankTxn>()
        for (txn in pendingLf) {
            desiredPending[FitIds.pendingKey(FitIds.feedCategory(txn, source.categoryUid), txn)] = txn
        }

        val newPosted = postedLf.filter {
            FitIds.posted(FitIds.feedCategory(it, source.categoryUid), it.id) !in known
        }
        val dropped = pendingRegister.keys.filter { it !in desiredPending }.mapNotNull { key ->
            val snap = snapshotFromParent(mdAccount, pendingRegister[key]!!) ?: return@mapNotNull null
            key to snap
        }
        val promotions = PendingMatch.uniquePairs(dropped, newPosted)
        val promotedPostedIds = promotions.map { it.posted.id }.toSet()
        val promotedPendingKeys = promotions.map { it.pendingKey }.toSet()

        var postedAdded = 0
        var postedSkipped = 0
        var pendingAdded = 0
        var pendingUpdated = 0
        var pendingRemoved = 0
        var pendingPromoted = 0
        var latestPosted: String? = mapping.lastPostedDate

        for (pair in promotions) {
            val existing = pendingRegister.remove(pair.pendingKey) ?: continue
            val fitId = FitIds.posted(FitIds.feedCategory(pair.posted, source.categoryUid), pair.posted.id)
            MdAccess.setDescription(existing, pair.posted.payee())
            MdAccess.clearPendingFlag(existing)
            MdAccess.setRegisterFitId(existing, fitId)
            known.add(fitId)
            pendingPromoted++
            latestPosted = maxDate(latestPosted, pair.posted.date)
        }

        for (txn in postedLf) {
            val fitId = FitIds.posted(FitIds.feedCategory(txn, source.categoryUid), txn.id)
            if (txn.id in promotedPostedIds) continue
            if (fitId in known) {
                postedSkipped++
                latestPosted = maxDate(latestPosted, txn.date)
                continue
            }
            if (addPostedOrTransfer(mdAccount, source, txn, fitId, pending = false, sources, mappedIds, mappingBySource)) {
                postedAdded++
            } else {
                postedSkipped++
            }
            known.add(fitId)
            latestPosted = maxDate(latestPosted, txn.date)
        }

        for ((key, txn) in desiredPending) {
            val existing = pendingRegister.remove(key)
            if (existing != null) {
                labelRegisterPending(existing, txn.payee())
                pendingUpdated++
            } else if (key in known) {
                MdAccess.findByFitId(book, mdAccount, FitIds.PROTOCOL, key)?.let {
                    labelRegisterPending(it, txn.payee())
                }
                pendingUpdated++
            } else {
                addPostedOrTransfer(mdAccount, source, txn, key, pending = true, sources, mappedIds, mappingBySource)
                known.add(key)
                pendingAdded++
            }
        }

        for ((key, existing) in pendingRegister) {
            if (key in promotedPendingKeys) continue
            MdAccess.deleteTxn(existing)
            pendingRemoved++
        }

        pruneStaleDownloads(mdAccount, known)
        finishDownloads(mdAccount)

        return AccountSyncResult(
            postedAdded = postedAdded,
            postedSkipped = postedSkipped,
            pendingAdded = pendingAdded,
            pendingUpdated = pendingUpdated,
            pendingRemoved = pendingRemoved,
            pendingPromoted = pendingPromoted,
            lastPostedDate = latestPosted
        )
    }

    /**
     * @return true if a new row was created, false if an existing transfer was tagged with this FITID.
     */
    private fun addPostedOrTransfer(
        mdAccount: Account,
        source: MappableSource,
        txn: BankTxn,
        fitId: String,
        pending: Boolean,
        sources: List<MappableSource>,
        mappedIds: Set<String>,
        mappingBySource: Map<String, AccountMapping>
    ): Boolean {
        val other = TxnRouter.transferCounterpart(txn, source, sources, mappedIds)
        val otherUuid = other?.let { mappingBySource[it.id]?.moneydanceAccountUuid }?.takeIf { it.isNotBlank() }
        val otherAccount = otherUuid?.let { book.getAccountByUUID(it) }
        if (otherAccount != null && !MdAccess.sameAccount(mdAccount, otherAccount)) {
            val dateInt = isoToDateInt(txn.date)
            val amount = MdAccess.toMinorUnits(mdAccount, txn.amount)
            val existing = MdAccess.findUniqueTransfer(book, mdAccount, otherAccount, dateInt, amount)
            if (existing != null) {
                MdAccess.setRegisterFitId(existing, fitId)
                return false
            }
            val payee = if (pending) FitIds.PENDING_LABEL + txn.payee() else txn.payee()
            MdAccess.addTransfer(
                book,
                mdAccount,
                otherAccount,
                dateInt,
                amount,
                payee,
                txn.memo(),
                fitId,
                pending
            )
            return true
        }
        addDownloadTxn(mdAccount, txn, fitId, pending)
        return true
    }

    private fun addDownloadTxn(account: Account, txn: BankTxn, fitId: String, pending: Boolean) {
        val payee = if (pending) FitIds.PENDING_LABEL + txn.payee() else txn.payee()
        MdAccess.addDownload(
            account,
            isoToDateInt(txn.date),
            MdAccess.toMinorUnits(account, txn.amount),
            payee,
            txn.memo(),
            fitId,
            pending,
            MdAccess.currencyId(account)
        )
    }

    private fun finishDownloads(account: Account) {
        val downloaded = MdAccess.downloadedTxns(account) ?: return
        MdAccess.sortTxns(downloaded)
        MdAccess.syncList(downloaded)
        MdAccess.notifyDownloaded(downloaded)
        MdAccess.downloadedUpdated(account)
        processDownloaded(account)
    }

    private fun pruneStaleDownloads(account: Account, registerIds: Set<String>) {
        val downloaded = MdAccess.downloadedTxns(account) ?: return
        val toRemove = mutableListOf<com.infinitekind.moneydance.model.OnlineTxn>()
        for (i in 0 until MdAccess.txnCount(downloaded)) {
            val row = MdAccess.txnAt(downloaded, i) ?: continue
            val fitId = MdAccess.fiTxnId(row)
            if (fitId.isNullOrBlank() || fitId in registerIds || MdAccess.isAcceptedDownload(row)) {
                toRemove.add(row)
            }
        }
        if (toRemove.isEmpty()) return
        toRemove.forEach { MdAccess.removeTxn(downloaded, it) }
        MdAccess.syncList(downloaded)
        MdAccess.downloadedUpdated(account)
    }

    private fun labelRegisterPending(txn: ParentTxn, payee: String) {
        if (!MdAccess.isNew(txn)) return
        val current = MdAccess.getDescription(txn).orEmpty()
        if (current.startsWith(FitIds.PENDING_LABEL)) return
        MdAccess.setDescription(txn, FitIds.PENDING_LABEL + current.ifBlank { payee })
        txn.setParameter(FitIds.PARAM_PENDING, true)
        txn.syncItem()
    }

    private fun collectRegisterFitIds(account: Account): MutableSet<String> {
        val ids = mutableSetOf<String>()
        for (txn in MdAccess.txnsForAccount(book, account)) {
            if (txn !is ParentTxn) continue
            if (!MdAccess.sameAccount(MdAccess.accountOf(txn), account)) continue
            val id = MdAccess.registerFiTxnId(txn, FitIds.PROTOCOL)
            if (!id.isNullOrBlank()) ids.add(id)
        }
        return ids
    }

    private fun snapshotFromParent(mdAccount: Account, parent: ParentTxn): BankTxn? {
        val desc = MdAccess.getDescription(parent)?.removePrefix(FitIds.PENDING_LABEL)?.trim().orEmpty()
        return BankTxn(
            id = "",
            categoryUid = "",
            amount = MdAccess.toMajorUnits(mdAccount, MdAccess.getValue(parent)),
            currency = MdAccess.currencyId(mdAccount),
            date = dateIntToIso(MdAccess.getDateInt(parent)),
            merchant = desc,
            description = desc,
            isPending = true,
            source = null,
            counterPartyType = null,
            counterPartyUid = null,
            direction = null
        )
    }

    companion object {
        fun fetchFromDate(mapping: AccountMapping): String? {
            mapping.syncStartDate?.takeIf { it.isNotBlank() }?.let { return it }
            val last = mapping.lastPostedDate?.takeIf { it.isNotBlank() } ?: return null
            return AccountMapping.nextStartAfter(last)
        }

        fun isoToDateInt(iso: String): Int {
            val d = LocalDate.parse(iso.take(10))
            return d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
        }

        fun dateIntToIso(dateInt: Int): String {
            val y = dateInt / 10000
            val m = (dateInt / 100) % 100
            val d = dateInt % 100
            return "%04d-%02d-%02d".format(y, m, d)
        }

        fun maxDate(a: String?, b: String?): String? {
            if (a.isNullOrBlank()) return b
            if (b.isNullOrBlank()) return a
            return if (a >= b) a else b
        }
    }
}
