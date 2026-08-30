package com.moneydance.modules.features.starling.api

enum class SourceKind {
    MAIN,
    SPENDING,
    SAVINGS
}

data class StarlingAccount(
    val accountUid: String,
    val defaultCategory: String,
    val name: String,
    val currency: String,
    val accountType: String,
    val createdAt: String?
)

data class StarlingSpace(
    val accountUid: String,
    val categoryUid: String,
    val name: String,
    val kind: SourceKind,
    val archived: Boolean = false,
    val parentName: String = ""
)

/** One row in the mapping table: a Starling account or Space. */
data class MappableSource(
    val id: String,
    val accountUid: String,
    val categoryUid: String,
    val name: String,
    val parentName: String,
    val currency: String,
    val kind: SourceKind,
    val archived: Boolean,
    val accountType: String = "",
    val createdAt: String? = null
) {
    val displayName: String
        get() {
            val archivedTag = if (archived) " (archived)" else ""
            val prefix = if (kind == SourceKind.MAIN) "" else "    "
            return "$prefix$name$archivedTag"
        }

    companion object {
        fun idFor(accountUid: String, categoryUid: String): String = "$accountUid:$categoryUid"
    }
}

data class BankTxn(
    val id: String,
    val categoryUid: String,
    val amount: Double,
    val currency: String,
    val date: String,
    val merchant: String?,
    val description: String?,
    val isPending: Boolean,
    val source: String?,
    val counterPartyType: String?,
    val counterPartyUid: String?,
    val direction: String?
) {
    fun payee(): String {
        val m = merchant?.trim().orEmpty()
        if (m.isNotEmpty()) return m
        return description?.trim().orEmpty().ifEmpty { "Starling" }
    }

    fun memo(): String = description?.trim().orEmpty()
}

data class HolderInfo(
    val name: String,
    val type: String
)
