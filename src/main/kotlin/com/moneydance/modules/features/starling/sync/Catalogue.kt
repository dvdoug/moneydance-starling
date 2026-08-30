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
                    archived = false,
                    accountType = acc.accountType,
                    createdAt = acc.createdAt
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
                    archived = archived,
                    accountType = acc?.accountType.orEmpty(),
                    createdAt = acc?.createdAt
                )
            )
        }
        return order(accounts, rows)
    }

    fun order(accounts: List<StarlingAccount>, rows: List<MappableSource>): List<MappableSource> {
        val byAcc = rows.groupBy { it.accountUid }
        val accOrder = accounts.sortedWith(
            compareBy<StarlingAccount> { typeRank(it.accountType) }.thenBy { it.name.lowercase() }
        )
        val out = mutableListOf<MappableSource>()
        val seen = mutableSetOf<String>()
        for (acc in accOrder) {
            val group = byAcc[acc.accountUid] ?: continue
            seen.add(acc.accountUid)
            val main = group.filter { it.kind == SourceKind.MAIN }
            val live = group.filter { it.kind != SourceKind.MAIN && !it.archived }
                .sortedBy { it.name.lowercase() }
            val archived = group.filter { it.archived }.sortedBy { it.name.lowercase() }
            out += main + live + archived
        }
        byAcc.forEach { (uid, group) ->
            if (uid in seen) return@forEach
            out += group
        }
        return out
    }

    private fun typeRank(type: String): Int = when (type.uppercase()) {
        "PRIMARY" -> 0
        "ADDITIONAL" -> 1
        "SAVINGS" -> 2
        else -> 9
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
    fun shouldFetch(src: MappableSource, sources: List<MappableSource>, mappedIds: Set<String>): Boolean {
        if (src.id in mappedIds) return true
        val parent = sources.firstOrNull { it.accountUid == src.accountUid && it.kind == SourceKind.MAIN }
        return parent != null && parent.id in mappedIds && src.kind != SourceKind.MAIN
    }

    fun mappingForFetch(
        src: MappableSource,
        sources: List<MappableSource>,
        mapped: List<com.moneydance.modules.features.starling.settings.AccountMapping>
    ): com.moneydance.modules.features.starling.settings.AccountMapping? {
        mapped.firstOrNull { it.sourceId == src.id }?.let { return it }
        val parent = sources.firstOrNull { it.accountUid == src.accountUid && it.kind == SourceKind.MAIN }
            ?: return null
        return mapped.firstOrNull { it.sourceId == parent.id }
    }

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

        // Space feeds list the other-account leg as ON_US_PAY_ME + CUSTOMER (holder uid),
        // not CATEGORY. Skip that duplicate when the other Starling account is mapped.
        if (feed.kind != SourceKind.MAIN &&
            isOnUsFromCustomer(txn) &&
            otherAccountMainMapped(feed, sources, mappedIds)
        ) {
            return null
        }
        if (feed.kind != SourceKind.MAIN && isInternalMove(txn) &&
            otherMappedMain(txn, feed, sources, byCat, mappedIds) != null
        ) {
            return null
        }

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

        // Savings Space: own mapping, else parent savings account catch-all, else skip.
        if (feedMapped) return feed
        return if (parentMapped) parentMain else null
    }

    private fun isOnUsFromCustomer(txn: BankTxn): Boolean {
        return txn.source.equals("ON_US_PAY_ME", ignoreCase = true) &&
            txn.counterPartyType.equals("CUSTOMER", ignoreCase = true)
    }

    private fun otherAccountMainMapped(
        feed: MappableSource,
        sources: List<MappableSource>,
        mappedIds: Set<String>
    ): Boolean {
        return sources.any {
            it.kind == SourceKind.MAIN && it.accountUid != feed.accountUid && it.id in mappedIds
        }
    }

    /**
     * The other Starling **account** (MAIN row) for a CATEGORY internal move, if that account
     * is mapped and is not this feed's account. That MAIN feed already has the transfer.
     */
    private fun otherMappedMain(
        txn: BankTxn,
        feed: MappableSource,
        sources: List<MappableSource>,
        byCat: Map<String, MappableSource>,
        mappedIds: Set<String>
    ): MappableSource? {
        val other = txn.counterPartyUid?.let { byCat[it] }
        val otherMain = when {
            other?.kind == SourceKind.MAIN -> other
            other != null -> sources.firstOrNull { it.accountUid == other.accountUid && it.kind == SourceKind.MAIN }
            else -> sources.firstOrNull { it.kind == SourceKind.MAIN && it.categoryUid == txn.counterPartyUid }
        } ?: return null
        if (otherMain.accountUid == feed.accountUid) return null
        if (otherMain.id !in mappedIds) return null
        return otherMain
    }
}
