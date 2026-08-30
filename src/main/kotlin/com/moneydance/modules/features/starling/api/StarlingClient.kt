package com.moneydance.modules.features.starling.api

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate


class StarlingClient(
    private val token: String,
    private val opener: (URI) -> HttpURLConnection = defaultOpener,
    private val sleeper: (Long) -> Unit = { ms -> Thread.sleep(ms) }
) {
    fun holderInfo(): HolderInfo {
        val type = FeedParser.parseHolderType(get("/api/v2/account-holder"))
        val name = try {
            FeedParser.parseHolderName(get("/api/v2/account-holder/name"))
        } catch (_: StarlingException) {
            ""
        }
        return HolderInfo(name = name, type = type)
    }

    fun listAccounts(): List<StarlingAccount> = FeedParser.parseAccounts(get("/api/v2/accounts"))

    fun listSpaces(accountUid: String, parentName: String): List<StarlingSpace> {
        val fromSpaces = FeedParser.parseSpaces(
            accountUid,
            parentName,
            get("/api/v2/account/$accountUid/spaces")
        )
        val fromGoals = try {
            FeedParser.parseSavingsGoals(
                accountUid,
                parentName,
                get("/api/v2/account/$accountUid/savings-goals")
            )
        } catch (_: StarlingException.Forbidden) {
            emptyList()
        } catch (_: StarlingException.NotFound) {
            emptyList()
        }
        val byUid = linkedMapOf<String, StarlingSpace>()
        (fromSpaces + fromGoals).forEach { byUid[it.categoryUid] = it }
        return byUid.values.toList()
    }

    fun transactionsBetween(
        accountUid: String,
        categoryUid: String,
        from: LocalDate,
        to: LocalDate,
        onChunk: ((Int, Int) -> Unit)? = null
    ): List<BankTxn> {
        val chunks = DateChunks.windows(from, to)
        val out = mutableListOf<BankTxn>()
        chunks.forEachIndexed { index, window ->
            onChunk?.invoke(index + 1, chunks.size)
            val q = listOf(
                "minTransactionTimestamp" to formatTs(window.first),
                "maxTransactionTimestamp" to formatTs(window.second, endOfDay = true)
            )
            val path = "/api/v2/feed/account/$accountUid/category/$categoryUid/transactions-between"
            out.addAll(FeedParser.parseFeedItems(get(path, q)))
        }
        return out
    }

    fun get(path: String, query: List<Pair<String, String>> = emptyList()): String {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) throw StarlingException.MissingToken()
        val url = buildUrl(path, query)
        sleeper(MIN_GAP_MS)
        val conn = opener(url)
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 30_000
            conn.readTimeout = 90_000
            conn.setRequestProperty("Authorization", "Bearer $trimmed")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val status = conn.responseCode
            val body = readBody(conn, status)
            if (status in 200..299) return body
            throw mapStatus(status, body)
        } catch (e: StarlingException) {
            throw e
        } catch (e: IOException) {
            throw StarlingException.Network(e)
        } finally {
            conn.disconnect()
        }
    }

    fun checkRequiredAccess() {
        val missing = linkedSetOf<String>()
        try {
            listAccounts()
        } catch (e: StarlingException.Forbidden) {
            missing += e.missingScopes.ifEmpty { listOf("account:read", "account-list:read") }
            if (missing.isNotEmpty()) throw StarlingException.Forbidden(missing.toList())
        }
        try {
            holderInfo()
        } catch (e: StarlingException.Forbidden) {
            missing += e.missingScopes.ifEmpty {
                listOf("account-holder-name:read", "account-holder-type:read", "customer:read")
            }
        }
        val accounts = try {
            listAccounts()
        } catch (_: StarlingException) {
            emptyList()
        }
        val first = accounts.firstOrNull()
        if (first != null) {
            try {
                get("/api/v2/account/${first.accountUid}/spaces")
            } catch (e: StarlingException.Forbidden) {
                missing += e.missingScopes.ifEmpty { listOf("space:read") }
            }
            try {
                val today = LocalDate.now()
                transactionsBetween(first.accountUid, first.defaultCategory, today.minusDays(2), today)
            } catch (e: StarlingException.Forbidden) {
                missing += e.missingScopes.ifEmpty { listOf("transaction:read") }
            }
        }
        if (missing.isNotEmpty()) throw StarlingException.Forbidden(missing.toList())
    }

    private fun mapStatus(status: Int, body: String): StarlingException = when (status) {
        401 -> StarlingException.Unauthorized()
        403 -> ScopeCheck.forbiddenFromBody(body)
        404 -> StarlingException.NotFound()
        429 -> StarlingException.RateLimited()
        else -> StarlingException.Http(status)
    }

    private fun readBody(conn: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        return stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    private fun buildUrl(path: String, query: List<Pair<String, String>>): URI {
        val q = query.joinToString("&") { (k, v) ->
            "${enc(k)}=${enc(v)}"
        }
        val full = if (q.isEmpty()) "$BASE$path" else "$BASE$path?$q"
        return URI(full)
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun formatTs(date: LocalDate, endOfDay: Boolean = false): String {
        val t = if (endOfDay) "T23:59:59.000Z" else "T00:00:00.000Z"
        return date.toString() + t
    }

    companion object {
        const val BASE: String = "https://api.starlingbank.com"
        const val USER_AGENT: String = "moneydance-starling/1"
        const val MIN_GAP_MS: Long = 220
        val defaultOpener: (URI) -> HttpURLConnection = { uri ->
            uri.toURL().openConnection() as HttpURLConnection
        }
    }
}

object DateChunks {
    const val MAX_DAYS: Long = 180

    fun windows(from: LocalDate, to: LocalDate): List<Pair<LocalDate, LocalDate>> {
        var start = from
        val end = if (to.isBefore(from)) from else to
        val out = mutableListOf<Pair<LocalDate, LocalDate>>()
        while (start <= end) {
            val chunkEnd = start.plusDays(MAX_DAYS - 1).let { if (it.isAfter(end)) end else it }
            out.add(start to chunkEnd)
            start = chunkEnd.plusDays(1)
        }
        return out
    }
}


