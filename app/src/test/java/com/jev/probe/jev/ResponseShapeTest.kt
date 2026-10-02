package com.jev.probe.jev

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The response contract for all three routes.
 *
 * Every case here is a real failure that used to pass silently: a gateway that
 * answers HTTP 200 with an error body, and a body whose shape does not match
 * what the caller parses. Both used to degrade to "empty success" — the
 * settings page printed 成功 with 意图=? and a blank 返回, and the overlay had
 * nothing to show.
 */
class ResponseShapeTest {

    // ------------------------------------------- 2xx body that reports failure

    /** Zhipu's gateway answers unknown paths with HTTP 200 + this body. */
    @Test fun gatewaySoftNotFoundWithHttp200IsRejected() {
        val e = assertThrows(ApiException::class.java) {
            ResponseShape.ok(Route.JUDGE, 200, """{"code":500,"msg":"404 NOT_FOUND","success":false}""")
        }
        assertTrue(e.message!!, e.message!!.contains("404 NOT_FOUND"))
        assertFalse("a wrong address never fixes itself on retry", e.retryable)
        assertEquals(Route.JUDGE, e.route)
    }

    @Test fun errorEnvelopeWithHttp200IsRejected() {
        val e = assertThrows(ApiException::class.java) {
            ResponseShape.ok(Route.REPLY, 200,
                """{"error":{"message":"令牌已过期或验证不正确","type":"401"}}""")
        }
        assertTrue(e.message!!, e.message!!.contains("令牌已过期或验证不正确"))
        assertFalse(e.retryable)
    }

    @Test fun errorEnvelopeAsPlainStringIsRejected() {
        val e = assertThrows(ApiException::class.java) {
            ResponseShape.ok(Route.VISION, 200, """{"error":"model not found"}""")
        }
        assertTrue(e.message!!, e.message!!.contains("model not found"))
    }

    @Test fun successFalseWithoutMessageIsStillRejected() {
        val e = assertThrows(ApiException::class.java) {
            ResponseShape.ok(Route.REPLY, 200, """{"success":false}""")
        }
        assertFalse(e.retryable)
        assertTrue(e.message!!, e.message!!.contains("success"))
    }

    @Test fun businessCodeAbove400IsRejected() {
        assertThrows(ApiException::class.java) {
            ResponseShape.ok(Route.REPLY, 200, """{"code":4001,"message":"invalid api key"}""")
        }
    }

    /** A null `error` is how some gateways say "no error" — never a failure. */
    @Test fun nullErrorFieldInSuccessBodyIsNotAnError() {
        val resp = ResponseShape.ok(Route.REPLY, 200,
            """{"error":null,"choices":[{"message":{"content":"收到"}}]}""")
        assertEquals("收到", ResponseShape.chatContent(Route.REPLY, resp))
    }

    @Test fun wellFormedBodiesPass() {
        ResponseShape.ok(Route.REPLY, 200, """{"choices":[],"usage":{"code":0}}""")
        ResponseShape.ok(Route.JUDGE, 200, """{"answers":{},"code":0,"success":true}""")
    }

    @Test fun malformedJsonStillBubblesUpAsJsonException() {
        // A truncated body stays retryable at the transport layer, so post()
        // keeps its existing retry behaviour for it.
        assertThrows(org.json.JSONException::class.java) {
            ResponseShape.ok(Route.REPLY, 200, "<html>not json")
        }
    }

    // ------------------------------------------------------- chat completions

    @Test fun chatContentIsReturned() {
        val resp = JSONObject("""{"choices":[{"message":{"role":"assistant","content":" 收到 "}}]}""")
        assertEquals("收到", ResponseShape.chatContent(Route.REPLY, resp))
    }

    /** The Anthropic shape: `content[0].text`, no `choices` anywhere. */
    @Test fun anthropicShapedResponseIsRejectedWithHint() {
        val resp = JSONObject(
            """{"id":"msg_1","type":"message","role":"assistant",
               "content":[{"type":"text","text":"收到"}]}""")
        val e = assertThrows(ApiException::class.java) { ResponseShape.chatContent(Route.REPLY, resp) }
        assertTrue(e.message!!, e.message!!.contains("Anthropic"))
    }

    @Test fun responseWithoutChoicesIsRejected() {
        val e = assertThrows(ApiException::class.java) {
            ResponseShape.chatContent(Route.REPLY, JSONObject("""{"id":"1","data":[]}"""))
        }
        assertTrue(e.message!!, e.message!!.contains("choices"))
    }

    @Test fun blankContentIsRejectedInsteadOfReturningEmptyString() {
        assertThrows(ApiException::class.java) {
            ResponseShape.chatContent(Route.REPLY, JSONObject("""{"choices":[{"message":{"content":"   "}}]}"""))
        }
        assertThrows(ApiException::class.java) {
            ResponseShape.chatContent(Route.VISION, JSONObject("""{"choices":[{"message":{}}]}"""))
        }
    }

    // ---------------------------------------------------------- jev decisions

    @Test fun answersObjectIsReturned() {
        val resp = JSONObject("""{"answers":{"true_intent":{"choice":"seeking_attention"}}}""")
        assertEquals("seeking_attention",
            ResponseShape.jevAnswers(Route.JUDGE, resp).getJSONObject("true_intent").getString("choice"))
    }

    @Test fun responseWithoutAnswersIsRejectedWithProtocolHint() {
        val e = assertThrows(ApiException::class.java) {
            ResponseShape.jevAnswers(Route.JUDGE, JSONObject("""{"choices":[{"message":{"content":"{}"}}]}"""))
        }
        assertTrue(e.message!!, e.message!!.contains("answers"))
    }

    // -------------------------------------------------------- three candidates

    @Test fun jsonArrayContentYieldsExactlyThree() {
        assertEquals(listOf("在的", "怎么了", "稍等"),
            ResponseShape.threeCandidates(Route.REPLY, """["在的","怎么了","稍等"]"""))
    }

    @Test fun jsonArrayWrappedInProseYieldsThree() {
        assertEquals(listOf("a", "b", "c"),
            ResponseShape.threeCandidates(Route.REPLY, "好的：\n[\"a\",\"b\",\"c\"]\n以上"))
    }

    @Test fun lineFallbackYieldsThree() {
        assertEquals(listOf("a", "b", "c"),
            ResponseShape.threeCandidates(Route.REPLY, "1. a\n2. b\n3. c"))
    }

    @Test fun fewerThanThreeRealCandidatesArePadded() {
        assertEquals(listOf("在的", "怎么了", "（稍等，我看下）"),
            ResponseShape.threeCandidates(Route.REPLY, """["在的","怎么了"]"""))
    }

    /** Empty model output must never become three identical placeholders. */
    @Test fun blankContentYieldsNoPlaceholders() {
        assertThrows(ApiException::class.java) { ResponseShape.threeCandidates(Route.REPLY, "") }
        assertThrows(ApiException::class.java) { ResponseShape.threeCandidates(Route.REPLY, "   \n  ") }
    }

    // ------------------------------------------------------------ retryability

    @Test fun transportAndStatusFailuresStayRetryable() {
        assertTrue(ApiException(Route.JUDGE, null, "网络超时").retryable)
        assertTrue(ApiException(Route.JUDGE, 500, "内部错误").retryable)
    }
}
