package com.moneydance.modules.features.starling.api

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StarlingClientTest {
    @Test
    fun unauthorized() {
        val client = StarlingClient("tok", opener = { fake(401, """{"error":"invalid_token"}""") }, sleeper = {})
        assertFailsWith<StarlingException.Unauthorized> { client.listAccounts() }
    }

    @Test
    fun retriesOn429ThenSucceeds() {
        var calls = 0
        val waits = mutableListOf<Long>()
        val body = """{"accounts":[{"accountUid":"a","defaultCategory":"c","name":"Personal","currency":"GBP","accountType":"PRIMARY"}]}"""
        val client = StarlingClient(
            "tok",
            opener = {
                calls++
                if (calls == 1) fake(429, "{}", retryAfter = "1") else fake(200, body)
            },
            sleeper = { waits.add(it) }
        )
        assertEquals("Personal", client.listAccounts().single().name)
        assertEquals(2, calls)
        assertTrue(waits.any { it >= 1000L })
    }

    @Test
    fun listsAccounts() {
        val body = """{"accounts":[{"accountUid":"a","defaultCategory":"c","name":"Personal","currency":"GBP","accountType":"PRIMARY"}]}"""
        val client = StarlingClient("tok", opener = { fake(200, body) }, sleeper = {})
        val acc = client.listAccounts().single()
        assertEquals("Personal", acc.name)
        assertEquals("c", acc.defaultCategory)
    }

    private fun fake(status: Int, body: String, retryAfter: String? = null): HttpURLConnection {
        return object : HttpURLConnection(URI("https://api.starlingbank.com/api/v2/accounts").toURL()) {
            override fun connect() {}
            override fun disconnect() {}
            override fun usingProxy() = false
            override fun getResponseCode() = status
            override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
            override fun getErrorStream() = ByteArrayInputStream(body.toByteArray())
            override fun getHeaderField(name: String?): String? {
                if (name.equals("Retry-After", ignoreCase = true)) return retryAfter
                return super.getHeaderField(name)
            }
        }
    }
}
