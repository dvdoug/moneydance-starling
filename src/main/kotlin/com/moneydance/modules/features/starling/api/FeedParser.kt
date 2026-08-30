package com.moneydance.modules.features.starling.api

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

object FeedParser {
    private val PENDING_STATUSES = setOf("PENDING", "UPCOMING", "RETRYING")
    private val SKIP_STATUSES = setOf("DECLINED", "ACCOUNT_CHECK", "UPCOMING_CANCELLED")

    fun parseFeedItems(text: String): List<BankTxn> {
        val root = parseJson(text).obj()
        return root["feedItems"]?.arr().orEmpty().mapNotNull { parseItem(it.obj()) }
    }

    internal fun parseItem(o: Map<String, JsonVal>): BankTxn? {
        val uid = o.str("feedItemUid")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val status = o.str("status")?.trim()?.uppercase().orEmpty()
        if (status in SKIP_STATUSES) return null
        if (status == "REVERSED" && o.str("settlementTime").isNullOrBlank()) return null

        val pending = status in PENDING_STATUSES
        val dateIso = pickDate(o, pending) ?: return null
        val direction = o.str("direction")?.trim()?.uppercase().orEmpty()
        val amountObj = o["amount"]?.obj() ?: return null
        val minor = amountObj.long("minorUnits") ?: return null
        val currency = amountObj.str("currency")?.trim().orEmpty().ifEmpty { "GBP" }
        val major = kotlin.math.abs(minor) / 100.0
        val signed = when (direction) {
            "OUT" -> -major
            else -> major
        }
        val payee = o.str("counterPartyName")?.trim()?.takeIf { it.isNotEmpty() }
        val reference = o.str("reference")?.trim()?.takeIf { it.isNotEmpty() }
        return BankTxn(
            id = uid,
            categoryUid = o.str("categoryUid")?.trim().orEmpty(),
            amount = signed,
            currency = currency,
            date = dateIso,
            merchant = payee,
            description = reference ?: payee,
            isPending = pending,
            source = o.str("source")?.trim(),
            counterPartyType = o.str("counterPartyType")?.trim(),
            counterPartyUid = o.str("counterPartyUid")?.trim(),
            direction = direction
        )
    }

    fun parseAccounts(text: String): List<StarlingAccount> {
        val root = parseJson(text).obj()
        return root["accounts"]?.arr().orEmpty().mapNotNull { row ->
            val o = row.obj()
            val uid = o.str("accountUid")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val cat = o.str("defaultCategory")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            StarlingAccount(
                accountUid = uid,
                defaultCategory = cat,
                name = o.str("name")?.trim()?.takeIf { it.isNotEmpty() } ?: o.str("accountType") ?: "Account",
                currency = o.str("currency")?.trim()?.takeIf { it.isNotEmpty() } ?: "GBP",
                accountType = o.str("accountType")?.trim().orEmpty(),
                createdAt = o.str("createdAt")
            )
        }
    }

    fun parseSpaces(accountUid: String, parentName: String, text: String): List<StarlingSpace> {
        val root = parseJson(text).obj()
        val out = mutableListOf<StarlingSpace>()
        for (row in root["savingsGoals"]?.arr().orEmpty()) {
            val o = row.obj()
            val uid = o.str("savingsGoalUid")?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val state = o.str("state")?.uppercase().orEmpty()
            out.add(
                StarlingSpace(
                    accountUid = accountUid,
                    categoryUid = uid,
                    name = o.str("name")?.trim()?.ifEmpty { null } ?: "Savings",
                    kind = SourceKind.SAVINGS,
                    archived = state == "ARCHIVED" || state == "ARCHIVING",
                    parentName = parentName
                )
            )
        }
        for (row in root["spendingSpaces"]?.arr().orEmpty()) {
            val o = row.obj()
            val uid = o.str("spaceUid")?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val state = o.str("state")?.uppercase().orEmpty()
            out.add(
                StarlingSpace(
                    accountUid = accountUid,
                    categoryUid = uid,
                    name = o.str("name")?.trim()?.ifEmpty { null } ?: "Space",
                    kind = SourceKind.SPENDING,
                    archived = state == "ARCHIVED" || state == "ARCHIVING",
                    parentName = parentName
                )
            )
        }
        return out
    }

    fun parseHolderName(text: String): String =
        parseJson(text).obj().str("accountHolderName")?.trim().orEmpty()

    fun parseHolderType(text: String): String =
        parseJson(text).obj().str("accountHolderType")?.trim().orEmpty()

    private fun pickDate(o: Map<String, JsonVal>, pending: Boolean): String? {
        val raw = if (pending) {
            o.str("transactionTime") ?: o.str("updatedAt")
        } else {
            o.str("settlementTime") ?: o.str("transactionTime")
        } ?: return null
        return toIsoDate(raw)
    }

    fun toIsoDate(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.length >= 10 && trimmed[4] == '-' && trimmed[7] == '-') {
            if (trimmed.length == 10) return trimmed
            return try {
                Instant.parse(trimmed).atZone(ZoneOffset.UTC).toLocalDate().toString()
            } catch (_: DateTimeParseException) {
                trimmed.take(10)
            }
        }
        return null
    }
}
