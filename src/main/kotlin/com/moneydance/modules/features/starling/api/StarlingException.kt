package com.moneydance.modules.features.starling.api

sealed class StarlingException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingToken : StarlingException("Paste a personal access token first.")
    class Unauthorized : StarlingException(
        "Starling rejected the personal access token. Create a new token in the Starling Developer Portal and paste it here."
    )
    class Forbidden(val missingScopes: List<String> = emptyList()) : StarlingException(
        if (missingScopes.isEmpty()) {
            "Starling refused access. The token may be missing a required permission (scope)."
        } else {
            "This token is missing: ${missingScopes.joinToString(", ")}. " +
                "Create a new token in the Starling Developer Portal (you cannot add ticks to an existing one) " +
                "and include those permissions. The Setup guide lists every box to tick."
        }
    )
    class NotFound : StarlingException("Starling could not find that account or Space.")
    class RateLimited : StarlingException(
        "Starling asked us to slow down (too many requests). Wait a minute and try again."
    )
    class Network(cause: Throwable) : StarlingException(
        "Could not reach Starling. Check your internet connection.",
        cause
    )
    class Http(val status: Int) : StarlingException("Starling returned HTTP $status.")
    class Parse(detail: String) : StarlingException("Unexpected response from Starling ($detail).")
}
