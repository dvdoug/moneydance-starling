package com.moneydance.modules.features.starling.settings

import com.moneydance.modules.features.starling.api.MappableSource
import com.moneydance.modules.features.starling.api.SourceKind
import com.moneydance.modules.features.starling.api.bool as jsonBool
import com.moneydance.modules.features.starling.api.parseJson
import com.moneydance.modules.features.starling.api.str as jsonStr
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

data class SavedPat(
    val id: String,
    val description: String,
    val historyWalked: Boolean
)

data class CatalogueEntry(
    val accountUid: String,
    val categoryUid: String,
    val name: String,
    val kind: SourceKind,
    val parentName: String
)

data class AccountMapping(
    val sourceId: String,
    val moneydanceAccountUuid: String,
    val syncStartDate: String? = defaultStartDate(),
    val lastPostedDate: String? = null,
    val sourceName: String? = null,
    val parentName: String? = null
) {
    fun afterSuccessfulImport(latestPosted: String?): AccountMapping {
        val latest = latestPosted?.takeIf { it.isNotBlank() } ?: lastPostedDate
        val rolled = nextStartAfter(latest)
        val nextStart = when {
            rolled == null -> syncStartDate
            syncStartDate.isNullOrBlank() -> rolled
            else -> maxIso(syncStartDate, rolled)
        }
        return copy(lastPostedDate = latest, syncStartDate = nextStart)
    }

    fun withSource(source: MappableSource?): AccountMapping {
        if (source == null) return this
        return copy(sourceName = source.name, parentName = source.parentName)
    }

    companion object {
        fun defaultStartDate(): String =
            YearMonth.now(ZoneId.systemDefault()).atDay(1).toString()

        fun plusDays(isoDate: String, days: Long): String =
            LocalDate.parse(isoDate).plusDays(days).toString()

        fun nextStartAfter(lastPosted: String?): String? {
            val last = lastPosted?.takeIf { it.isNotBlank() } ?: return null
            return plusDays(last, -OVERLAP_DAYS)
        }

        fun maxIso(a: String, b: String): String {
            val left = LocalDate.parse(a.take(10))
            val right = LocalDate.parse(b.take(10))
            return if (left >= right) a.take(10) else b.take(10)
        }

        const val OVERLAP_DAYS: Long = 31
    }
}

object JsonStrings {
    fun quote(value: String): String = buildString {
        append('"')
        for (c in value) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
        append('"')
    }
}

object AccountMappingCodec {
    fun toJson(mappings: List<AccountMapping>): String {
        val rows = mappings.joinToString(",") { m ->
            buildString {
                append("{")
                append("\"id\":").append(JsonStrings.quote(m.sourceId))
                append(",\"md\":").append(JsonStrings.quote(m.moneydanceAccountUuid))
                if (!m.syncStartDate.isNullOrBlank()) append(",\"from\":").append(JsonStrings.quote(m.syncStartDate))
                if (!m.lastPostedDate.isNullOrBlank()) append(",\"last\":").append(JsonStrings.quote(m.lastPostedDate))
                if (!m.sourceName.isNullOrBlank()) append(",\"name\":").append(JsonStrings.quote(m.sourceName))
                if (!m.parentName.isNullOrBlank()) append(",\"parent\":").append(JsonStrings.quote(m.parentName))
                append("}")
            }
        }
        return "{\"mappings\":[$rows]}"
    }

    fun fromJson(text: String?): List<AccountMapping> {
        if (text.isNullOrBlank()) return emptyList()
        val root = parseJson(text).obj()
        return root["mappings"]?.arr().orEmpty().mapNotNull { row ->
            val o = row.obj()
            val id = o.jsonStr("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val md = o.jsonStr("md")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AccountMapping(
                sourceId = id,
                moneydanceAccountUuid = md,
                syncStartDate = o.jsonStr("from")?.takeIf { it.isNotBlank() },
                lastPostedDate = o.jsonStr("last")?.takeIf { it.isNotBlank() },
                sourceName = o.jsonStr("name")?.takeIf { it.isNotBlank() },
                parentName = o.jsonStr("parent")?.takeIf { it.isNotBlank() }
            )
        }
    }
}

object PatIndexCodec {
    fun toJson(pats: List<SavedPat>): String {
        val rows = pats.joinToString(",") { p ->
            "{\"id\":${JsonStrings.quote(p.id)},\"desc\":${JsonStrings.quote(p.description)},\"walked\":${p.historyWalked}}"
        }
        return "{\"pats\":[$rows]}"
    }

    fun fromJson(text: String?): List<SavedPat> {
        if (text.isNullOrBlank()) return emptyList()
        val root = parseJson(text).obj()
        return root["pats"]?.arr().orEmpty().mapNotNull { row ->
            val o = row.obj()
            val id = o.jsonStr("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SavedPat(
                id = id,
                description = o.jsonStr("desc").orEmpty(),
                historyWalked = o.jsonBool("walked") ?: false
            )
        }
    }

    fun newId(): String = UUID.randomUUID().toString()
}

object CatalogueCodec {
    fun toJson(entries: List<CatalogueEntry>): String {
        val rows = entries.joinToString(",") { e ->
            buildString {
                append("{")
                append("\"acc\":").append(JsonStrings.quote(e.accountUid))
                append(",\"cat\":").append(JsonStrings.quote(e.categoryUid))
                append(",\"name\":").append(JsonStrings.quote(e.name))
                append(",\"kind\":").append(JsonStrings.quote(e.kind.name))
                append(",\"parent\":").append(JsonStrings.quote(e.parentName))
                append("}")
            }
        }
        return "{\"spaces\":[$rows]}"
    }

    fun fromJson(text: String?): List<CatalogueEntry> {
        if (text.isNullOrBlank()) return emptyList()
        val root = parseJson(text).obj()
        return root["spaces"]?.arr().orEmpty().mapNotNull { row ->
            val o = row.obj()
            val acc = o.jsonStr("acc")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val cat = o.jsonStr("cat")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val kind = try {
                SourceKind.valueOf(o.jsonStr("kind") ?: "SAVINGS")
            } catch (_: Exception) {
                SourceKind.SAVINGS
            }
            CatalogueEntry(
                accountUid = acc,
                categoryUid = cat,
                name = o.jsonStr("name")?.trim()?.ifEmpty { null } ?: "Space",
                kind = kind,
                parentName = o.jsonStr("parent").orEmpty()
            )
        }
    }
}
