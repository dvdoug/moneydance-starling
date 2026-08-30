package com.moneydance.modules.features.starling.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScopeCheckTest {
    @Test
    fun parsesStarlingInsufficientScopeBody() {
        val body = """{"error":"insufficient_scope","error_description":"Insufficient scope. Required: [space:read]. Granted: [account:read, account-list:read, transaction:read]"}"""
        val (missing, granted) = ScopeCheck.parseInsufficient(body)
        assertEquals(listOf("space:read"), missing)
        assertTrue("account:read" in granted)
    }

    @Test
    fun messageListsMissingScopes() {
        val msg = StarlingException.Forbidden(listOf("space:read", "transaction:read")).message!!
        assertTrue(msg.contains("space:read"))
        assertTrue(msg.contains("transaction:read"))
        assertTrue(msg.contains("new token"))
    }
}
