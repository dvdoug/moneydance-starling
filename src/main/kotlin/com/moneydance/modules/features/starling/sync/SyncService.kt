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
import javax.swing.SwingWorker

object SyncService {
    @Volatile
    var inFlight: Boolean = false
        private set

    fun start(
        book: AccountBook,
        settings: SettingsStore,
        gui: MoneydanceGUI,
        mappings: List<AccountMapping>,
        sources: List<MappableSource>,
        reason: String,
        onStatus: (String) -> Unit = {},
        onBusy: (Boolean) -> Unit = {},
        onSources: (List<MappableSource>) -> Unit = {},
        onMappings: (List<AccountMapping>) -> Unit = {}
    ): Boolean {
        if (inFlight) {
            MdNotify.log("skip $reason (already running)")
            return false
        }
        val mapped = mappings.filter { it.moneydanceAccountUuid.isNotBlank() }
        if (mapped.isEmpty()) {
            MdNotify.log("skip $reason (no mapped accounts)")
            onStatus("Choose a Moneydance account for at least one row.")
            return false
        }
        val tokens = settings.tokens()
        if (tokens.isEmpty()) {
            onStatus("Add a personal access token first.")
            return false
        }
        inFlight = true
        onBusy(true)
        val n = mapped.size
        MdNotify.log("$reason started ($n mapped account${if (n == 1) "" else "s"})")
        val startText = if (reason == "auto-import") "importing on file open" else "importing"
        MdNotify.bar(gui, startText, 0.02)
        onStatus(startText.replaceFirstChar { it.uppercase() } + "…")

        object : SwingWorker<FetchBundle, Progress>() {
            override fun doInBackground(): FetchBundle {
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
                    val token = tokenByAccount[src.accountUid] ?: tokens.first().second
                    val client = StarlingClient(token)
                    val mapping = TxnRouter.mappingForFetch(src, sources, mapped)
                    val fromIso = mapping?.let { SyncEngine.fetchFromDate(it) }
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
                val last = chunks.last()
                MdNotify.bar(gui, last.text, last.progress.coerceIn(0.02, 0.9))
                onStatus(last.text.replaceFirstChar { it.uppercase() } + "…")
            }

            override fun done() {
                try {
                    val bundle = get()
                    applyFetched(book, settings, gui, bundle, reason, onStatus, onSources, onMappings)
                } catch (e: Exception) {
                    val cause = e.cause ?: e
                    val msg = cause.message ?: "Import failed."
                    MdNotify.log("$reason failed: ${cause.javaClass.simpleName}: $msg", cause)
                    MdNotify.bar(gui, msg, 0.0)
                    onStatus(msg)
                } finally {
                    inFlight = false
                    onBusy(false)
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
        reason: String,
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
                val line = item.error ?: "Error"
                updated.add(item.mapping.withSource(item.source))
                results.add(AccountSyncResult(error = line))
                lines.add(if (item.source != null) "${item.source.name}: $line" else line)
                MdNotify.log("$reason ${item.source?.name ?: item.mapping.sourceId}: $line")
                return@forEachIndexed
            }
            MdNotify.bar(gui, "importing ${item.source.name}", 0.55 + 0.4 * (index + 1) / total)
            val result = engine.apply(item.mapping, item.source, item.txns)
            val named = item.mapping.withSource(item.source)
            updated.add(
                if (result.error == null) named.afterSuccessfulImport(result.lastPostedDate) else named
            )
            results.add(result)
            val line = ImportStatus.line(item.source.name, result)
            lines.add(line)
            MdNotify.log(line)
        }
        settings.setMappings(updated)
        onSources(bundle.sources)
        onMappings(updated)
        val overall = ImportStatus.overall(results)
        val prefix = if (reason == "auto-import") "auto-import " else ""
        MdNotify.log("$reason finished: $overall")
        MdNotify.bar(gui, prefix + overall, 1.0)
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
