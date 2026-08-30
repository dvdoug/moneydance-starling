package com.moneydance.modules.features.starling.api

object ScopeCheck {
    val REQUIRED: List<String> = listOf(
        "account:read",
        "account-list:read",
        "transaction:read",
        "space:read",
        "account-holder-name:read",
        "account-holder-type:read",
        "customer:read"
    )

    fun parseInsufficient(body: String): Pair<List<String>, List<String>> {
        val desc = try {
            parseJson(body).obj().str("error_description") ?: body
        } catch (_: Exception) {
            body
        }
        val required = bracketList(desc, "Required")
        val granted = bracketList(desc, "Granted")
        val missing = if (required.isNotEmpty()) {
            required.filter { it.isNotBlank() && it !in granted }
        } else {
            emptyList()
        }
        return missing to granted
    }

    fun forbiddenFromBody(body: String): StarlingException.Forbidden {
        val (missing, _) = parseInsufficient(body)
        return StarlingException.Forbidden(missing)
    }

    private fun bracketList(text: String, label: String): List<String> {
        val key = "$label:"
        val start = text.indexOf(key, ignoreCase = true)
        if (start < 0) return emptyList()
        val open = text.indexOf('[', start)
        val close = text.indexOf(']', open + 1)
        if (open < 0 || close < 0) return emptyList()
        return text.substring(open + 1, close).split(',')
            .map { it.trim().trim('"', '\'') }
            .filter { it.isNotEmpty() }
    }
}
