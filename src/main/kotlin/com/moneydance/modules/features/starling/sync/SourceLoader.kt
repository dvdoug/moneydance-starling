package com.moneydance.modules.features.starling.sync

import com.moneydance.modules.features.starling.api.HolderInfo
import com.moneydance.modules.features.starling.api.MappableSource
import com.moneydance.modules.features.starling.api.StarlingClient
import com.moneydance.modules.features.starling.api.StarlingException
import com.moneydance.modules.features.starling.settings.CatalogueEntry
import com.moneydance.modules.features.starling.settings.SavedPat
import com.moneydance.modules.features.starling.settings.SettingsStore
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class LoadedSources(
    val sources: List<MappableSource>,
    val holderLabel: String
)

object SourceLoader {
    fun labelFor(info: HolderInfo): String {
        val name = info.name.trim()
        val type = when (info.type.uppercase()) {
            "INDIVIDUAL" -> "Personal"
            "JOINT" -> "Joint"
            "BUSINESS" -> "Business"
            "SOLE_TRADER" -> "Sole trader"
            else -> info.type.lowercase().replaceFirstChar { it.titlecase() }
        }
        return if (name.isEmpty()) type else "$name ($type)"
    }

    fun loadActive(
        tokens: List<Pair<SavedPat, String>>,
        stored: List<CatalogueEntry>,
        onProgress: (String) -> Unit = {}
    ): LoadedSources {
        val liveSpaces = mutableListOf<com.moneydance.modules.features.starling.api.StarlingSpace>()
        val accounts = mutableListOf<com.moneydance.modules.features.starling.api.StarlingAccount>()
        var label = ""
        for ((pat, token) in tokens) {
            val client = StarlingClient(token)
            onProgress("listing accounts")
            val holder = try {
                client.holderInfo()
            } catch (_: StarlingException) {
                HolderInfo("", "")
            }
            if (label.isEmpty()) label = pat.description.ifBlank { labelFor(holder) }
            val accs = client.listAccounts()
            accounts.addAll(accs)
            for (acc in accs) {
                onProgress("listing Spaces for ${acc.name}")
                liveSpaces.addAll(client.listSpaces(acc.accountUid, acc.name))
            }
        }
        return LoadedSources(Catalogue.stitch(accounts, liveSpaces, stored), label)
    }

    fun walkHistory(
        token: String,
        settings: SettingsStore,
        onProgress: (String, Double) -> Unit
    ): LoadedSources {
        val client = StarlingClient(token)
        onProgress("checking token", 0.05)
        val (holder, accounts) = client.checkRequiredAccess()
        val label = labelFor(holder)
        onProgress("listing accounts", 0.1)
        val liveSpaces = mutableListOf<com.moneydance.modules.features.starling.api.StarlingSpace>()
        val discovered = mutableListOf<CatalogueEntry>()
        val today = LocalDate.now()
        accounts.forEachIndexed { accIndex, acc ->
            onProgress("listing Spaces for ${acc.name}", 0.15 + 0.1 * accIndex / accounts.size.coerceAtLeast(1))
            val spaces = client.listSpaces(acc.accountUid, acc.name)
            liveSpaces.addAll(spaces)
            discovered.addAll(Catalogue.fromLive(acc, spaces))
            val created = parseCreated(acc.createdAt) ?: today.minusYears(8)
            onProgress("reading ${acc.name} history", 0.3)
            val txns = client.transactionsBetween(
                acc.accountUid,
                acc.defaultCategory,
                created,
                today
            ) { chunk, total ->
                val base = 0.3 + 0.6 * accIndex / accounts.size.coerceAtLeast(1)
                val span = 0.6 / accounts.size.coerceAtLeast(1)
                onProgress("reading ${acc.name} history ($chunk of $total)", base + span * chunk / total.coerceAtLeast(1))
            }
            discovered.addAll(Catalogue.fromFeed(acc, txns))
        }
        val merged = Catalogue.mergeStored(settings.catalogue(), discovered)
        settings.setCatalogue(merged)
        return LoadedSources(Catalogue.stitch(accounts, liveSpaces, merged), label)
    }

    private fun parseCreated(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(raw).withOffsetSameInstant(ZoneOffset.UTC).toLocalDate()
        } catch (_: Exception) {
            try {
                LocalDate.parse(raw.take(10))
            } catch (_: Exception) {
                null
            }
        }
    }
}
