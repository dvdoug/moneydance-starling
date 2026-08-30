package com.moneydance.modules.features.starling.api

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StarlingClientTest {
    @Test
    fun unauthorized() {
        val client = StarlingClient("tok", opener = { fake(401, """{"error":"invalid_token"}""") }, sleeper = {})
        assertFailsWith<StarlingException.Unauthorized> { client.listAccounts() }
    }

    @Test
    fun listsAccounts() {
        val body = """{"accounts":[{"accountUid":"a","defaultCategory":"c","name":"Personal","currency":"GBP","accountType":"PRIMARY"}]}"""
        val client = StarlingClient("tok", opener = { fake(200, body) }, sleeper = {})
        val acc = client.listAccounts().single()
        assertEquals("Personal", acc.name)
        assertEquals("c", acc.defaultCategory)
    }

    private fun fake(status: Int, body: String): HttpURLConnection {
        return object : HttpURLConnection(URI("https://api.starlingbank.com/api/v2/accounts").toURL()) {
            override fun connect() {}
            override fun disconnect() {}
            override fun usingProxy() = false
            override fun getResponseCode() = status
            override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
            override fun getErrorStream() = ByteArrayInputStream(body.toByteArray())
        }
    }
}
