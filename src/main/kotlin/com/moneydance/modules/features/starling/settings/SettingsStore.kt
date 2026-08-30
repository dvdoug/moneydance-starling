package com.moneydance.modules.features.starling.settings

import com.infinitekind.moneydance.model.AccountBook
import com.infinitekind.moneydance.model.LocalStorage

class SettingsStore(
    private val getAuth: (String) -> String?,
    private val setAuth: (String, String) -> Unit,
    private val clearAuth: (String) -> Unit,
    private val getPlain: (String) -> String? = { null },
    private val setPlain: (String, String) -> Unit = { _, _ -> },
    private val removePlain: (String) -> Unit = { }
) {
    fun pats(): List<SavedPat> = PatIndexCodec.fromJson(getPlain(PAT_INDEX))

    fun patToken(id: String): String? {
        val key = tokenKey(id)
        getAuth(key)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return getPlain(key)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun setPat(id: String, token: String, description: String, historyWalked: Boolean) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            removePat(id)
            return
        }
        setAuth(tokenKey(id), trimmed)
        setPlain(tokenKey(id), trimmed)
        val next = pats().filter { it.id != id } + SavedPat(id, description, historyWalked)
        setPlain(PAT_INDEX, PatIndexCodec.toJson(next))
    }

    fun updatePatMeta(id: String, description: String? = null, historyWalked: Boolean? = null) {
        val next = pats().map { p ->
            if (p.id != id) p
            else p.copy(
                description = description ?: p.description,
                historyWalked = historyWalked ?: p.historyWalked
            )
        }
        setPlain(PAT_INDEX, PatIndexCodec.toJson(next))
    }

    fun removePat(id: String) {
        clearAuth(tokenKey(id))
        removePlain(tokenKey(id))
        setPlain(PAT_INDEX, PatIndexCodec.toJson(pats().filter { it.id != id }))
    }

    fun tokens(): List<Pair<SavedPat, String>> =
        pats().mapNotNull { p -> patToken(p.id)?.let { p to it } }

    fun mappings(): List<AccountMapping> = AccountMappingCodec.fromJson(getPlain(MAPPINGS))

    fun setMappings(mappings: List<AccountMapping>) {
        setPlain(MAPPINGS, AccountMappingCodec.toJson(mappings))
    }

    fun catalogue(): List<CatalogueEntry> = CatalogueCodec.fromJson(getPlain(CATALOGUE))

    fun setCatalogue(entries: List<CatalogueEntry>) {
        setPlain(CATALOGUE, CatalogueCodec.toJson(entries))
    }

    fun importOnOpen(): Boolean = getPlain(IMPORT_ON_OPEN) == "true"

    fun setImportOnOpen(enabled: Boolean) {
        setPlain(IMPORT_ON_OPEN, if (enabled) "true" else "false")
    }

    companion object {
        const val PAT_INDEX: String = "starling.pats"
        const val MAPPINGS: String = "starling.mappings"
        const val CATALOGUE: String = "starling.catalogue"
        const val IMPORT_ON_OPEN: String = "starling.importOnOpen"

        fun tokenKey(id: String): String = "starling.pat.$id"

        fun fromBook(book: AccountBook?): SettingsStore? {
            val storage: LocalStorage = book?.localStorage ?: return null
            return SettingsStore(
                getAuth = { storage.getCachedAuthentication(it) },
                setAuth = { key, value ->
                    storage.cacheAuthentication(key, value)
                    storage.save()
                },
                clearAuth = { key ->
                    storage.clearAuthenticationCache(key)
                    storage.save()
                },
                getPlain = { storage[it] },
                setPlain = { key, value ->
                    storage.put(key, value)
                    storage.save()
                },
                removePlain = { key ->
                    storage.remove(key)
                    storage.save()
                }
            )
        }
    }
}
