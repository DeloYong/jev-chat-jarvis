package com.jev.probe.jev

import org.json.JSONArray
import org.json.JSONObject

/**
 * The response contract of the three routes: what a well-formed answer looks
 * like, and the error to raise when the body does not match.
 *
 * This exists because two very different failures used to reach the UI as
 * success:
 *
 * - Gateways answer an unknown path with HTTP 200 and an error *body*. Zhipu's
 *   `/api/anthropic` prefix returns `{"code":500,"msg":"404 NOT_FOUND",
 *   "success":false}` for anything it does not serve, so a status-only check
 *   lets it through.
 * - A body in the wrong shape parses to nothing at all. `optJSONObject` /
 *   `optString` then yield an empty result with `error == null`, so the settings
 *   page printed 成功 266ms · 意图=?（置信 ?）and 成功 76ms · 返回：(nothing),
 *   and the overlay had no judgment and no candidates to show.
 *
 * Every function here throws [ApiException] instead of returning an empty
 * stand-in, so the reason ends up on screen.
 */
object ResponseShape {

    /** Keeps the panel at three rows when the model returned fewer candidates. */
    private const val FILLER = "（稍等，我看下）"

    /**
     * Parse a 2xx body, rejecting the ones that report failure inside a success
     * status. A malformed body still throws JSONException so [HttpJson.post] can
     * keep retrying it as a truncated response.
     */
    fun ok(route: String, status: Int, text: String): JSONObject {
        val resp = JSONObject(text)
        val failure = bodyFailure(resp)
        // Not retryable: throttling is signalled by the status code and handled
        // before the body is even read, so a 2xx carrying an error means the
        // address or the protocol is wrong — repeating it cannot help.
        if (failure != null) throw ApiException(route, status, failure, retryable = false)
        return resp
    }

    /**
     * The assistant text of an OpenAI-compatible chat completion, trimmed. An
     * empty string is an error, not an answer: it used to travel all the way to
     * the UI as a successful-but-blank result.
     */
    fun chatContent(route: String, resp: JSONObject): String {
        val content = resp.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content")?.trim()
        if (!content.isNullOrEmpty()) return content
        throw ApiException(route, null, when {
            // Anthropic's Messages API puts the text in content[0].text. Pointing
            // this app at such a base cannot work even with the right path: the
            // request appends /chat/completions and never sends max_tokens.
            resp.optJSONArray("content") != null || resp.optString("type") == "message" ->
                "响应是 Anthropic messages 格式（正文在 content[0].text），本 App 只支持 " +
                    "OpenAI 兼容的 chat/completions，请改填该服务商的 OpenAI 兼容地址"
            resp.has("choices") -> "choices[0].message.content 为空，模型没有返回内容"
            else -> "响应里没有 choices[0].message.content，该地址不是 OpenAI 兼容的 chat/completions"
        }, retryable = false)
    }

    /**
     * The Jev decisions answer set. The judge route is not a chat API, so
     * pointing it at a generic LLM endpoint returns a body with no `answers` —
     * which used to parse into seven nulls and no error.
     */
    fun jevAnswers(route: String, resp: JSONObject): JSONObject =
        resp.optJSONObject("answers") ?: throw ApiException(route, null,
            "响应里没有 answers 字段，该地址不是 Jev decisions 接口" +
                "（判断接口只认 /alpha/decisions 或 /v1/systemone，通用大模型地址不能用）",
            retryable = false)

    /**
     * The three candidate replies out of one chat completion. A JSON array is
     * what the prompt asks for; the line split is the fallback for a model that
     * ignores it. Fewer than three real candidates are padded, but *nothing*
     * usable is an error — that is the case which used to surface as three
     * identical placeholders.
     */
    fun threeCandidates(route: String, content: String): List<String> {
        val found = fromJsonArray(content) ?: fromLines(content)
        if (found.isEmpty()) {
            throw ApiException(route, null,
                "模型没有返回可用的候选回复（内容：${content.trim().take(60).ifBlank { "空" }}）",
                retryable = false)
        }
        val out = found.take(3).toMutableList()
        while (out.size < 3) out.add(FILLER)
        return out
    }

    // ------------------------------------------------------------------ private

    /** The failure a 2xx body reports, or null when it reports none. */
    private fun bodyFailure(resp: JSONObject): String? {
        // `error` as an object (OpenAI / Anthropic) or as a bare string. An
        // explicit null means "no error" — some gateways always include the key.
        when (val err = resp.opt("error")) {
            null, JSONObject.NULL -> Unit
            is JSONObject -> {
                val msg = err.optString("message").ifBlank { err.optString("msg") }
                    .ifBlank { err.optString("code") }
                return msg.ifBlank { err.toString() }.ifBlank { "接口返回 error" }
            }
            else -> return err.toString().ifBlank { "接口返回 error" }
        }
        if (!resp.optBoolean("success", true)) return reason(resp) ?: "接口返回 success=false"
        // A business status inside a 200: 0 / 200 mean fine, 400+ does not.
        val code = resp.optInt("code", 0)
        if (code >= 400) return reason(resp) ?: "接口返回 code=$code"
        return null
    }

    private fun reason(resp: JSONObject): String? =
        resp.optString("msg").ifBlank { resp.optString("message") }.ifBlank { null }

    /** The first `[ … ]` block in the text, when it holds at least one string. */
    private fun fromJsonArray(content: String): List<String>? {
        val start = content.indexOf('[')
        val end = content.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return try {
            val arr = JSONArray(content.substring(start, end + 1))
            val out = ArrayList<String>()
            for (i in 0 until arr.length()) out.add(arr.optString(i).trim())
            out.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    /** Numbered / bulleted lines, for a model that ignored "output a JSON array". */
    private fun fromLines(content: String): List<String> =
        content.split("\n").map { it.trim().trimStart('-', '*', '1', '2', '3', '.', ' ', '"') }
            .filter { it.isNotBlank() }
}
