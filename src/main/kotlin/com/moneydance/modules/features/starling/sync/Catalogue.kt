package com.moneydance.modules.features.starling.sync

import com.moneydance.modules.features.starling.api.BankTxn
import com.moneydance.modules.features.starling.api.MappableSource
import com.moneydance.modules.features.starling.api.SourceKind
import com.moneydance.modules.features.starling.api.StarlingAccount
import com.moneydance.modules.features.starling.api.StarlingSpace
import com.moneydance.modules.features.starling.settings.CatalogueEntry

object Catalogue {
    fun stitch(
        accounts: List<StarlingAccount>,
        liveSpaces: List<StarlingSpace>,
        stored: List<CatalogueEntry>
    ): List<MappableSource> {
        val liveByCat = liveSpaces.associateBy { it.categoryUid }
        val storedByCat = stored.associateBy { it.categoryUid }
        val accountByUid = accounts.associateBy { it.accountUid }
        val uids = linkedSetOf<String>()
        accounts.forEach { uids.add(it.defaultCategory) }
        liveByCat.keys.forEach { uids.add(it) }
        storedByCat.keys.forEach { uids.add(it) }

        val rows = mutableListOf<MappableSource>()
        for (acc in accounts) {
            rows.add(
                MappableSource(
                    id = MappableSource.idFor(acc.accountUid, acc.defaultCategory),
                    accountUid = acc.accountUid,
                    categoryUid = acc.defaultCategory,
                    name = acc.name,
                    parentName = acc.name,
                    currency = acc.currency,
                    kind = SourceKind.MAIN,
                    archived = false
                )
            )
        }
        val seen = rows.map { it.categoryUid }.toMutableSet()
        val extraUids = uids.filter { it !in seen }
        for (uid in extraUids) {
            val live = liveByCat[uid]
            val saved = storedByCat[uid]
            val accountUid = live?.accountUid ?: saved?.accountUid ?: continue
            val acc = accountByUid[accountUid]
            val archived = live == null
            rows.add(
                MappableSource(
                    id = MappableSource.idFor(accountUid, uid),
                    accountUid = accountUid,
                    categoryUid = uid,
                    name = live?.name ?: saved?.name ?: "Space",
                    parentName = live?.parentName ?: saved?.parentName ?: acc?.name.orEmpty(),
                    currency = acc?.currency ?: "GBP",
                    kind = live?.kind ?: saved?.kind ?: SourceKind.SAVINGS,
                    archived = archived
                )
            )
        }
        return rows
    }

    fun mergeStored(
        stored: List<CatalogueEntry>,
        discovered: List<CatalogueEntry>
    ): List<CatalogueEntry> {
        val byCat = linkedMapOf<String, CatalogueEntry>()
        stored.forEach { byCat[it.categoryUid] = it }
        discovered.forEach { next ->
            val prev = byCat[next.categoryUid]
            byCat[next.categoryUid] = if (prev == null) next else prev.copy(
                name = next.name.ifBlank { prev.name },
                parentName = next.parentName.ifBlank { prev.parentName },
                kind = if (next.kind != SourceKind.SAVINGS || prev.kind == SourceKind.SAVINGS) next.kind else prev.kind
            )
        }
        return byCat.values.toList()
    }

    fun fromLive(account: StarlingAccount, spaces: List<StarlingSpace>): List<CatalogueEntry> {
        val rows = mutableListOf<CatalogueEntry>()
        rows.add(
            CatalogueEntry(
                accountUid = account.accountUid,
                categoryUid = account.defaultCategory,
                name = account.name,
                kind = SourceKind.MAIN,
                parentName = account.name
            )
        )
        spaces.forEach { s ->
            rows.add(
                CatalogueEntry(
                    accountUid = s.accountUid,
                    categoryUid = s.categoryUid,
                    name = s.name,
                    kind = s.kind,
                    parentName = s.parentName.ifBlank { account.name }
                )
            )
        }
        return rows
    }

    fun fromFeed(account: StarlingAccount, txns: List<BankTxn>): List<CatalogueEntry> {
        val latest = linkedMapOf<String, String>()
        for (txn in txns) {
            if (!txn.counterPartyType.equals("CATEGORY", ignoreCase = true)) continue
            val uid = txn.counterPartyUid?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val name = txn.merchant?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            latest[uid] = name
        }
        return latest.map { (uid, name) ->
            CatalogueEntry(
                accountUid = account.accountUid,
                categoryUid = uid,
                name = name,
                kind = SourceKind.SAVINGS,
                parentName = account.name
            )
        }
    }
}

object TxnRouter {
    fun isInternalMove(txn: BankTxn): Boolean {
        if (txn.counterPartyType.equals("CATEGORY", ignoreCase = true)) {
            val src = txn.source?.uppercase().orEmpty()
            return src == "INTERNAL_TRANSFER" || src == "ON_US_PAY_ME"
        }
        return txn.source.equals("INTERNAL_TRANSFER", ignoreCase = true)
    }

    /**
     * Where this feed item should land in Moneydance, or null to skip.
     * [mappedIds] is sourceId -> mapping present.
     */
    fun destination(
        txn: BankTxn,
        feed: MappableSource,
        sources: List<MappableSource>,
        mappedIds: Set<String>
    ): MappableSource? {
        val byCat = sources.associateBy { it.categoryUid }
        val parentMain = sources.firstOrNull {
            it.accountUid == feed.accountUid && it.kind == SourceKind.MAIN
        }
        val feedMapped = feed.id in mappedIds
        val parentMapped = parentMain != null && parentMain.id in mappedIds

        if (feed.kind == SourceKind.MAIN) {
            if (isInternalMove(txn)) {
                val other = txn.counterPartyUid?.let { byCat[it] }
                if (other != null && other.id in mappedIds) return feed
                if (other?.kind == SourceKind.SPENDING && other.accountUid == feed.accountUid) {
                    return null
                }
                return feed
            }
            return feed
        }

        if (feed.kind == SourceKind.SPENDING) {
            if (isInternalMove(txn)) {
                return if (feedMapped) feed else null
            }
            return when {
                feedMapped -> feed
                parentMapped -> parentMain
                else -> null
            }
        }

        // Savings pot (possibly on another Starling account)
        return if (feedMapped) feed else null
    }
}
