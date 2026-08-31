package com.moneydance.modules.features.starling.sync

import com.infinitekind.moneydance.model.Account
import com.infinitekind.moneydance.model.AccountBook
import com.infinitekind.moneydance.model.ParentTxn
import com.moneydance.modules.features.starling.api.BankTxn
import com.moneydance.modules.features.starling.api.MappableSource
import com.moneydance.modules.features.starling.settings.AccountMapping
import com.moneydance.modules.features.starling.ui.MdNotify
import java.time.LocalDate
import javax.swing.SwingUtilities

data class AccountSyncResult(
    val postedAdded: Int = 0,
    val postedSkipped: Int = 0,
    val pendingAdded: Int = 0,
    val pendingUpdated: Int = 0,
    val pendingRemoved: Int = 0,
    val pendingPromoted: Int = 0,
    val pendingPayees: List<String> = emptyList(),
    val lastPostedDate: String? = null,
    val oldestPendingDate: String? = null,
    val error: String? = null,
    val otherSideSourceIds: List<String> = emptyList()
)

private data class PendingRelink(val fitId: String, val otherUuid: String, val otherSourceId: String)

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
        stripPendingFromDownloads(mdAccount)
        val mappedIds = mappings.filter { it.moneydanceAccountUuid.isNotBlank() }.map { it.sourceId }.toSet()
        val mappingBySource = mappings.associateBy { it.sourceId }
        val relinks = mutableListOf<PendingRelink>()
        val pendingLf = txns.filter { it.isPending }
        val postedLf = txns.filter { !it.isPending && it.id.isNotBlank() }

        val ourPending = linkedMapOf<String, ParentTxn>()
        for (txn in MdAccess.txnsForAccount(book, mdAccount)) {
            if (txn !is ParentTxn) continue
            if (!MdAccess.sameAccount(MdAccess.accountOf(txn), mdAccount)) continue
            val id = MdAccess.registerFiTxnId(txn, FitIds.PROTOCOL)
            if (FitIds.isOurs(id) || FitIds.isPending(id)) {
                sanitizeOrigPayee(txn)
            }
            if (FitIds.isPending(id) && id != null) ourPending[id] = txn
        }

        val desiredPending = linkedMapOf<String, BankTxn>()
        for (txn in pendingLf) {
            desiredPending[FitIds.pendingKey(FitIds.feedCategory(txn, source.categoryUid), txn)] = txn
        }

        val newPosted = postedLf.filter {
            FitIds.posted(FitIds.feedCategory(it, source.categoryUid), it.id) !in known
        }
        val dropped = ourPending.filterKeys { it !in desiredPending }.mapNotNull { (key, parent) ->
            val snap = snapshotFromParent(mdAccount, parent) ?: return@mapNotNull null
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
            val existing = ourPending.remove(pair.pendingKey) ?: continue
            val fitId = FitIds.posted(FitIds.feedCategory(pair.posted, source.categoryUid), pair.posted.id)
            val confirmed = !MdAccess.isNew(existing)
            copyTxnText(existing, pair.posted)
            if (!confirmed) {
                MdAccess.setDateInt(existing, isoToDateInt(pair.posted.date))
            }
            sanitizeOrigPayee(existing)
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
            if (addPostedOrTransfer(mdAccount, source, txn, fitId, pending = false, sources, mappedIds, mappingBySource, relinks)) {
                postedAdded++
            } else {
                postedSkipped++
            }
            known.add(fitId)
            latestPosted = maxDate(latestPosted, txn.date)
        }

        for ((key, txn) in desiredPending) {
            val existing = ourPending.remove(key)
            if (existing != null) {
                pendingUpdated++
            } else if (key in known) {
                pendingUpdated++
            } else {
                addPostedOrTransfer(mdAccount, source, txn, key, pending = true, sources, mappedIds, mappingBySource, relinks)
                known.add(key)
                pendingAdded++
            }
        }

        for ((key, existing) in ourPending) {
            if (key in promotedPendingKeys) continue
            MdAccess.deleteTxn(existing)
            pendingRemoved++
        }

        finishDownloads(mdAccount)
        scheduleRelinks(mdAccount, relinks)
        schedulePendingLabels(mdAccount, desiredPending)

        return AccountSyncResult(
            postedAdded = postedAdded,
            postedSkipped = postedSkipped,
            pendingAdded = pendingAdded,
            pendingUpdated = pendingUpdated,
            pendingRemoved = pendingRemoved,
            pendingPromoted = pendingPromoted,
            lastPostedDate = latestPosted,
            oldestPendingDate = oldestOpenPendingDate(book, mapping),
            otherSideSourceIds = relinks.map { it.otherSourceId }
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
        mappingBySource: Map<String, AccountMapping>,
        relinks: MutableList<PendingRelink>
    ): Boolean {
        val other = TxnRouter.transferCounterpart(txn, source, sources, mappedIds)
        val otherUuid = other?.let { mappingBySource[it.id]?.moneydanceAccountUuid }?.takeIf { it.isNotBlank() }
        val otherAccount = otherUuid?.let { book.getAccountByUUID(it) }
        if (other != null && otherUuid != null && otherAccount != null &&
            !MdAccess.sameAccount(mdAccount, otherAccount)
        ) {
            val dateInt = isoToDateInt(txn.date)
            val amount = MdAccess.toMinorUnits(mdAccount, txn.amount)
            val existing = MdAccess.findUniqueTransfer(book, mdAccount, otherAccount, dateInt, amount)
            if (existing != null) {
                MdAccess.setRegisterFitId(existing, fitId)
                MdNotify.log("tagged existing transfer $fitId -> ${MdAccess.fullAccountName(otherAccount)}")
                return false
            }
            addDownloadTxn(mdAccount, txn, fitId, pending)
            relinks.add(PendingRelink(fitId, otherUuid, other.id))
            return true
        }
        addDownloadTxn(mdAccount, txn, fitId, pending)
        return true
    }

    /**
     * [processDownloaded] queues Moneydance's auto-add on the EDT. Relink after that
     * so the Confirm row is already in the register, then point its split at the Space.
     */
    private fun scheduleRelinks(mdAccount: Account, relinks: List<PendingRelink>) {
        if (relinks.isEmpty()) return
        SwingUtilities.invokeLater {
            SwingUtilities.invokeLater {
                for (link in relinks) {
                    val parent = MdAccess.findByFitId(book, mdAccount, FitIds.PROTOCOL, link.fitId)
                    val other = book.getAccountByUUID(link.otherUuid)
                    if (parent == null || other == null) {
                        MdNotify.log("could not link ${link.fitId} (download missing or account gone)")
                        continue
                    }
                    if (MdAccess.relinkUnconfirmedSplit(parent, other)) {
                        MdNotify.log("linked ${link.fitId} -> ${MdAccess.fullAccountName(other)}")
                    } else {
                        MdNotify.log("left ${link.fitId} as downloaded; Confirm as a transfer to ${MdAccess.fullAccountName(other)}")
                    }
                }
            }
        }
    }

    private fun addDownloadTxn(account: Account, txn: BankTxn, fitId: String, pending: Boolean) {
        val payee = txn.payee()
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
        MdNotify.log("${MdAccess.fullAccountName(account)} download list ${MdAccess.txnCount(downloaded)}")
        MdAccess.sortTxns(downloaded)
        MdAccess.syncList(downloaded)
        MdAccess.notifyDownloaded(downloaded)
        MdAccess.downloadedUpdated(account)
        processDownloaded(account)
    }

    private fun schedulePendingLabels(mdAccount: Account, desiredPending: Map<String, BankTxn>) {
        if (desiredPending.isEmpty()) return
        SwingUtilities.invokeLater {
            SwingUtilities.invokeLater {
                for ((key, txn) in desiredPending) {
                    val parent = MdAccess.findByFitId(book, mdAccount, FitIds.PROTOCOL, key) ?: continue
                    tagRegisterPending(parent, txn)
                }
            }
        }
    }

    private fun stripPendingFromDownloads(account: Account) {
        val downloaded = MdAccess.downloadedTxns(account) ?: return
        var changed = false
        for (i in 0 until MdAccess.txnCount(downloaded)) {
            val row = MdAccess.txnAt(downloaded, i) ?: continue
            val name = MdAccess.getName(row) ?: continue
            if (!name.startsWith(FitIds.PENDING_LABEL)) continue
            val clean = FitIds.stripPendingLabel(name)
            MdAccess.setName(row, clean)
            MdAccess.setMerchantName(row, clean)
            changed = true
        }
        if (!changed) return
        MdAccess.syncList(downloaded)
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

    private fun copyTxnText(parent: ParentTxn, txn: BankTxn) {
        MdAccess.setDescription(parent, txn.payee())
        MdAccess.setMemo(parent, txn.memo())
    }

    private fun tagRegisterPending(parent: ParentTxn, txn: BankTxn) {
        sanitizeOrigPayee(parent)
        copyTxnText(parent, txn)
        parent.setParameter(FitIds.PARAM_PENDING, true)
        parent.syncItem()
    }

    private fun sanitizeOrigPayee(txn: ParentTxn) {
        val orig = txn.getParameter(FitIds.ORIG_PAYEE_TAG, "") ?: return
        if (!orig.startsWith(FitIds.PENDING_LABEL)) return
        txn.setParameter(FitIds.ORIG_PAYEE_TAG, FitIds.stripPendingLabel(orig))
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
        val desc = FitIds.stripPendingLabel(MdAccess.getDescription(parent).orEmpty()).trim()
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
        fun fetchFromDate(mapping: AccountMapping, oldestPending: String? = null): String? =
            AccountMapping.fetchFromDate(mapping.syncStartDate, mapping.lastPostedDate, oldestPending)

        fun oldestOpenPendingDate(book: AccountBook, mapping: AccountMapping): String? {
            val account = book.getAccountByUUID(mapping.moneydanceAccountUuid) ?: return null
            var oldest: Int? = null
            for (txn in MdAccess.txnsForAccount(book, account)) {
                if (txn !is ParentTxn) continue
                if (!MdAccess.sameAccount(MdAccess.accountOf(txn), account)) continue
                if (!FitIds.isPending(MdAccess.registerFiTxnId(txn, FitIds.PROTOCOL))) continue
                val dateInt = MdAccess.getDateInt(txn)
                if (oldest == null || dateInt < oldest) oldest = dateInt
            }
            return oldest?.let { dateIntToIso(it) }
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
