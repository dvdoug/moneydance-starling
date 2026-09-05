package com.moneydance.modules.features.starling.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedParserTest {
    @Test
    fun cardPendingUsesTransactionTimeAndNegativeAmount() {
        val txn = item(
                """
                {"feedItemUid":"a","categoryUid":"c","amount":{"currency":"GBP","minorUnits":215},
                 "direction":"OUT","status":"PENDING","transactionTime":"2026-08-28T09:21:59.000Z",
                 "counterPartyName":"Tesco","source":"MASTER_CARD"}
                """.trimIndent()
        )
        assertTrue(txn.isPending)
        assertEquals(-2.15, txn.amount, 0.001)
        assertEquals("2026-08-28", txn.date)
        assertEquals("Tesco", txn.payee())
    }

    @Test
    fun settledUsesSettlementTime() {
        val txn = item(
                """
                {"feedItemUid":"a","categoryUid":"c","amount":{"currency":"GBP","minorUnits":215},
                 "direction":"OUT","status":"SETTLED",
                 "transactionTime":"2026-08-28T09:21:59.000Z",
                 "settlementTime":"2026-08-29T04:49:36.000Z",
                 "counterPartyName":"Tesco","source":"MASTER_CARD"}
                """.trimIndent()
        )
        assertFalse(txn.isPending)
        assertEquals("2026-08-29", txn.date)
    }

    @Test
    fun upcomingIsPending() {
        val txn = item(
                """
                {"feedItemUid":"dd","categoryUid":"c","amount":{"currency":"GBP","minorUnits":10000},
                 "direction":"OUT","status":"UPCOMING","transactionTime":"2026-09-01T00:00:00.000Z",
                 "counterPartyName":"Council"}
                """.trimIndent()
        )
        assertTrue(txn.isPending)
    }

    @Test
    fun declinedSkipped() {
        assertNull(
            itemOrNull(
                    """{"feedItemUid":"x","status":"DECLINED","amount":{"currency":"GBP","minorUnits":1},
                       "direction":"OUT","transactionTime":"2026-01-01T00:00:00.000Z"}"""
            )
        )
    }

    @Test
    fun reversedWithoutSettlementSkipped() {
        assertNull(
            itemOrNull(
                    """{"feedItemUid":"x","status":"REVERSED","amount":{"currency":"GBP","minorUnits":1},
                       "direction":"OUT","transactionTime":"2026-01-01T00:00:00.000Z"}"""
            )
        )
    }

    @Test
    fun inboundCreditPositive() {
        val txn = item(
                """{"feedItemUid":"s","categoryUid":"c","amount":{"currency":"GBP","minorUnits":585660},
                   "direction":"IN","status":"SETTLED","settlementTime":"2026-08-26T23:06:28.000Z",
                   "counterPartyName":"TravelPerk","source":"DIRECT_CREDIT"}"""
        )
        assertEquals(5856.60, txn.amount, 0.001)
        assertEquals("2026-08-27", txn.date)
    }

    @Test
    fun bacsJustBeforeUtcMidnightUsesUkDate() {
        val txn = item(
            """{"feedItemUid":"s","categoryUid":"c","amount":{"currency":"GBP","minorUnits":7011},
               "direction":"IN","status":"SETTLED",
               "transactionTime":"2026-08-20T23:01:00.000Z",
               "settlementTime":"2026-08-20T23:01:16.872Z",
               "counterPartyName":"TravelPerk","source":"DIRECT_CREDIT"}"""
        )
        assertEquals("2026-08-21", txn.date)
        assertFalse(txn.isPending)
    }

    @Test
    fun parseAccountsAndSpaces() {
        val accs = FeedParser.parseAccounts(
            """{"accounts":[{"accountUid":"aaa","name":"Personal","accountType":"PRIMARY",
               "defaultCategory":"cat","currency":"GBP","createdAt":"2019-03-11T16:50:01.000Z"}]}"""
        )
        assertEquals("Personal", accs.single().name)
        val spaces = FeedParser.parseSpaces(
            "aaa",
            "Personal",
            """{"savingsGoals":[{"savingsGoalUid":"g1","name":"Holiday","state":"ACTIVE"}],
               "spendingSpaces":[{"spaceUid":"s1","name":"Bills","state":"ACTIVE"}]}"""
        )
        assertEquals(2, spaces.size)
        assertEquals(SourceKind.SAVINGS, spaces[0].kind)
        assertEquals(SourceKind.SPENDING, spaces[1].kind)
    }

    private fun item(json: String): BankTxn = FeedParser.parseFeedItems("""{"feedItems":[$json]}""").single()

    @Test
    fun errorEnvelopeIsNotAnEmptyFeed() {
        try {
            FeedParser.parseFeedItems("""{"error":"QUERY_EXCEEDING_MAX_TIME_RANGE"}""")
            kotlin.test.fail("expected parse error")
        } catch (_: StarlingException.Parse) {
        }
    }

    private fun itemOrNull(json: String): BankTxn? = FeedParser.parseFeedItems("""{"feedItems":[$json]}""").singleOrNull()
}
