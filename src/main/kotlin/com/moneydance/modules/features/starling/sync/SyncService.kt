package com.moneydance.modules.features.starling.sync

import com.infinitekind.moneydance.model.AccountBook
import com.moneydance.apps.md.view.gui.MoneydanceGUI
import com.moneydance.modules.features.starling.api.BankTxn
import com.moneydance.modules.features.starling.api.MappableSource
import com.moneydance.modules.features.starling.api.StarlingClient
import com.moneydance.modules.features.starling.api.StarlingException
import com.moneydance.modules.features.starling.settings.AccountMapping
import com.moneydance.modules.features.starling.settings.SettingsStore
import com.moneydance.modules.features.starling.ui.ImportStatus
import com.moneydance.modules.features.starling.ui.MdNotify
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingWorker

object SyncService {
    @Volatile
    var inFlight: Boolean = false
        private set

    private val runId = AtomicInteger(0)

    fun discardInFlight() {
        runId.incrementAndGet()
        inFlight = false
    }

    fun start(
        book: AccountBook,
        settings: SettingsStore,
        gui: MoneydanceGUI,
        mappings: List<AccountMapping>,
        sources: List<MappableSource>,
        onStatus: (String) -> Unit = {},
        onBusy: (Boolean) -> Unit = {},
        onSources: (List<MappableSource>) -> Unit = {},
        onMappings: (List<AccountMapping>) -> Unit = {}
    ): Boolean {
        if (inFlight) {
            MdNotify.log("skip import (already running)")
            return false
        }
        val mapped = mappings.filter { it.moneydanceAccountUuid.isNotBlank() }
        if (mapped.isEmpty()) {
            MdNotify.log("skip import (no mapped accounts)")
            onStatus("Choose a Moneydance account for at least one row.")
            return false
        }
        val tokens = settings.tokens()
        if (tokens.isEmpty()) {
            onStatus("Add a personal access token first.")
            return false
        }
        val id = runId.incrementAndGet()
        inFlight = true
        onBusy(true)
        val n = mapped.size
        MdNotify.log("import started ($n mapped account${if (n == 1) "" else "s"})")
        MdNotify.bar(gui, "importing", 0.02)
        onStatus("Importing…")

        object : SwingWorker<FetchBundle, Progress>() {
            private fun superseded(): Boolean = id != runId.get()

            override fun doInBackground(): FetchBundle {
                if (superseded()) return FetchBundle(emptyList(), emptyList())
                val byId = sources.associateBy { it.id }
                val today = LocalDate.now()
                val tokenByAccount = linkedMapOf<String, String>()
                for ((_, token) in tokens) {
                    val client = StarlingClient(token)
                    try {
                        client.listAccounts().forEach { tokenByAccount[it.accountUid] = token }
                    } catch (_: StarlingException) {
                    }
                }
                val buckets = linkedMapOf<String, MutableList<BankTxn>>()
                mapped.forEach { buckets[it.sourceId] = mutableListOf() }

                val mappedIds = mapped.map { it.sourceId }.toSet()
                val feedsToFetch = sources.filter { TxnRouter.shouldFetch(it, sources, mappedIds) }

                feedsToFetch.forEachIndexed { index, src ->
                    if (superseded()) return FetchBundle(emptyList(), emptyList())
                    val token = tokenByAccount[src.accountUid] ?: tokens.first().second
                    val client = StarlingClient(token)
                    val mapping = TxnRouter.mappingForFetch(src, sources, mapped)
                    val oldestPending = mapping?.let { SyncEngine.oldestOpenPendingDate(book, it) }
                    val fromIso = mapping?.let { SyncEngine.fetchFromDate(it, oldestPending) }
                    val parsedFrom = try {
                        if (fromIso.isNullOrBlank()) {
                            com.moneydance.modules.features.starling.api.DateChunks.EARLIEST
                        } else {
                            LocalDate.parse(fromIso.take(10))
                        }
                    } catch (_: Exception) {
                        com.moneydance.modules.features.starling.api.DateChunks.EARLIEST
                    }
                    val from = com.moneydance.modules.features.starling.api.DateChunks.notBeforeOpened(
                        parsedFrom,
                        src.createdAt
                    )
                    publish(Progress("fetching ${src.name}", (index + 0.35) / feedsToFetch.size.coerceAtLeast(1)))
                    try {
                        val txns = client.transactionsBetween(src.accountUid, src.categoryUid, from, today)
                        for (txn in txns) {
                            val dest = TxnRouter.destination(txn, src, sources, mappedIds)
                                ?: continue
                            if (dest.id in buckets) buckets[dest.id]?.add(txn)
                        }
                    } catch (e: Exception) {
                        val msg = if (e is StarlingException) e.message else e.message
                        return FetchBundle(sources, mapped.map { Fetched(it, byId[it.sourceId], null, msg ?: "Import failed.") })
                    }
                }
                val fetched = mapped.map { mapping ->
                    val src = byId[mapping.sourceId]
                    if (src == null) {
                        Fetched(mapping, null, null, "That Starling account is no longer listed.")
                    } else {
                        Fetched(mapping, src, buckets[mapping.sourceId].orEmpty(), null)
                    }
                }
                return FetchBundle(sources, fetched)
            }

            override fun process(chunks: List<Progress>) {
                if (superseded()) return
                val last = chunks.last()
                MdNotify.bar(gui, last.text, last.progress.coerceIn(0.02, 0.9))
                onStatus(last.text.replaceFirstChar { it.uppercase() } + "…")
            }

            override fun done() {
                try {
                    val bundle = get()
                    if (superseded()) {
                        MdNotify.log("import discarded (file closed)")
                        return
                    }
                    applyFetched(book, settings, gui, bundle, onStatus, onSources, onMappings)
                } catch (e: Exception) {
                    if (superseded()) return
                    val cause = e.cause ?: e
                    val msg = cause.message ?: "Import failed."
                    MdNotify.log("import failed: ${cause.javaClass.simpleName}: $msg", cause)
                    MdNotify.bar(gui, msg, 0.0)
                    onStatus(msg)
                } finally {
                    if (id == runId.get()) {
                        inFlight = false
                        onBusy(false)
                    }
                }
            }
        }.execute()
        return true
    }

    private fun applyFetched(
        book: AccountBook,
        settings: SettingsStore,
        gui: MoneydanceGUI,
        bundle: FetchBundle,
        onStatus: (String) -> Unit,
        onSources: (List<MappableSource>) -> Unit,
        onMappings: (List<AccountMapping>) -> Unit
    ) {
        val engine = SyncEngine(book) { account -> gui.showDownloadedTxns(account) }
        val updated = mutableListOf<AccountMapping>()
        val results = mutableListOf<AccountSyncResult>()
        val lines = mutableListOf<String>()
        val total = bundle.fetched.size.coerceAtLeast(1)
        bundle.fetched.forEachIndexed { index, item ->
            if (item.error != null || item.source == null || item.txns == null) {
                updated.add(item.mapping.withSource(item.source))
                results.add(AccountSyncResult(error = item.error ?: "Error"))
                return@forEachIndexed
            }
            MdNotify.bar(gui, "importing ${item.source.name}", 0.55 + 0.4 * (index + 1) / total)
            val result = engine.apply(
                item.mapping,
                item.source,
                item.txns,
                bundle.sources,
                bundle.fetched.map { it.mapping }
            )
            val named = item.mapping.withSource(item.source)
            updated.add(
                if (result.error == null) {
                    named.afterSuccessfulImport(result.lastPostedDate, result.oldestPendingDate)
                } else {
                    named
                }
            )
            results.add(result)
        }
        val extras = mutableMapOf<String, Int>()
        for (result in results) {
            for (id in result.otherSideSourceIds) {
                extras[id] = extras.getOrDefault(id, 0) + 1
            }
        }
        bundle.fetched.forEachIndexed { index, item ->
            val result = results[index]
            val name = item.source?.name ?: item.mapping.sourceId
            val extra = extras[item.mapping.sourceId] ?: 0
            val shown = if (extra == 0) result else result.copy(postedAdded = result.postedAdded + extra)
            val line = ImportStatus.line(name, shown)
            lines.add(line)
            MdNotify.log(line)
        }
        settings.setMappings(updated)
        onSources(bundle.sources)
        onMappings(updated)
        val overall = ImportStatus.overall(results)
        MdNotify.log("import finished: $overall")
        MdNotify.bar(gui, overall, 1.0)
        onStatus(lines.joinToString("\n"))
    }

    private data class Progress(val text: String, val progress: Double)
    private data class Fetched(
        val mapping: AccountMapping,
        val source: MappableSource?,
        val txns: List<BankTxn>?,
        val error: String?
    )
    private data class FetchBundle(
        val sources: List<MappableSource>,
        val fetched: List<Fetched>
    )
}
