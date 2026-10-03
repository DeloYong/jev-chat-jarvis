package com.jev.probe.jev

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Which of the three API routes a failure came from. Used to build error text
 * the user can act on ("判断接口 HTTP 401：…" vs "回复接口 …").
 */
object Route {
    const val JUDGE = "判断接口"
    const val REPLY = "回复接口"
    const val VISION = "视觉接口"
    const val CLOUD = "云端服务"
}

/**
 * Carries the route, the HTTP status (null = transport failure) and the first
 * 120 chars of the response body so the settings page can show the real reason.
 */
class ApiException(
    val route: String,
    val status: Int?,
    val snippet: String,
    /**
     * False when repeating the request cannot possibly help — a wrong address or
     * an incompatible protocol — so [HttpJson.post] fails fast instead of burning
     * three round trips to report the same thing.
     */
    val retryable: Boolean = true,
    /** 官方托管网关的错误码(如 trial_exhausted / plan_expired / daily_cap);非网关错误恒为 null。 */
    val code: String? = null
) : RuntimeException(buildMessage(route, status, snippet)) {

    /** 402 = 托管额度用完或订阅过期,UI 据此展示付费引导而不是普通报错。 */
    val isPaywall: Boolean get() = status == 402

    companion object {
        fun buildMessage(route: String, status: Int?, snippet: String): String =
            if (status != null) "$route HTTP $status：${snippet.take(120)}"
            else "$route 请求失败：${snippet.take(120)}"
    }
}

/**
 * Shared POST-JSON helper: UTF-8 body, exponential backoff on 429/529, no retry
 * on other 4xx, and every failure normalized to [ApiException]. Keys are passed
 * in per call and never logged.
 */
object HttpJson {

    private const val MAX_ATTEMPTS = 3

    /**
     * @param route one of [Route], used only for error text.
     * @param extraHeaders additional request headers (e.g. OpenRouter attribution).
     */
    fun post(
        url: String,
        key: String,
        body: JSONObject,
        route: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): JSONObject {
        var attempt = 0
        var last: ApiException? = null
        while (attempt < MAX_ATTEMPTS) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Request cancelled")
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 40000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $key")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
                }
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                conn.outputStream.use { os: OutputStream -> os.write(bytes) }
                val code = conn.responseCode
                if (code == 429 || code == 529) {
                    // Our gateway also uses 429 for a hard daily cap: retrying cannot help.
                    if (code == 429) {
                        val body = readBody(conn.errorStream)
                        if (body.contains("\"gateway\"")) throw httpError(route, code, body)
                    }
                    last = ApiException(route, code, "服务繁忙，已重试")
                    attempt++
                    if (attempt < MAX_ATTEMPTS) Thread.sleep(500L * (1L shl attempt))
                    continue
                }
                // Branch on the status code FIRST. Reading the body must never be
                // able to lose it: errorStream is null on some failures (and on
                // some OEM stacks), and a read can throw on a truncated response —
                // either way this used to surface as a transport failure with no
                // status, which then got retried even for a 401.
                if (code !in 200..299) throw httpError(route, code, readBody(conn.errorStream))
                val text = readBody(conn.inputStream)
                if (text.isBlank()) throw ApiException(route, code, "响应体为空")
                // A 2xx status alone is not a success: some gateways answer an
                // unknown path with 200 and an error body, which used to parse
                // into an empty result and reach the UI as 成功.
                return ResponseShape.ok(route, code, text)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw e
            } catch (e: ApiException) {
                // No retry on a client error, nor on anything already known to be
                // permanent: throttling is signalled by the status code and
                // handled above, before the body is read.
                if (!e.retryable || (e.status != null && e.status in 400..499)) throw e
                last = e
                attempt++
                if (attempt < MAX_ATTEMPTS) Thread.sleep(500L * (1L shl attempt))
            } catch (e: Exception) {
                last = ApiException(route, null, describe(e))
                attempt++
                if (attempt < MAX_ATTEMPTS) Thread.sleep(500L * (1L shl attempt))
            } finally {
                conn?.disconnect()
            }
        }
        throw last ?: ApiException(route, null, "请求失败")
    }

    /**
     * Non-2xx -> [ApiException]. Our own gateway marks its errors with
     * `{"gateway":true,"error":{"code","message"}}`; only those are unwrapped, so
     * every third-party provider keeps showing its raw body exactly as before.
     */
    private fun httpError(route: String, status: Int, errText: String): ApiException {
        if (errText.contains("\"gateway\"")) {
            try {
                val o = JSONObject(errText)
                val err = o.optJSONObject("error")
                if (o.optBoolean("gateway") && err != null) {
                    return ApiException(route, status, err.optString("message").ifBlank { "（无说明）" },
                        code = err.optString("code").ifBlank { null })
                }
            } catch (_: Exception) { /* fall through to the raw body */ }
        }
        return ApiException(route, status, errText.ifBlank { "（响应体为空）" })
    }

    /**
     * One GET, no retry: used for cheap idempotent reads (entitlement, order
     * status) where the caller just polls again.
     */
    fun get(url: String, key: String, route: String): JSONObject {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("Authorization", "Bearer $key")
            }
            val code = conn.responseCode
            if (code !in 200..299) throw httpError(route, code, readBody(conn.errorStream))
            val text = readBody(conn.inputStream)
            if (text.isBlank()) throw ApiException(route, code, "响应体为空")
            return JSONObject(text)
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(route, null, describe(e))
        } finally {
            conn?.disconnect()
        }
    }

    /** Body text, or "" — a null stream or a read failure never costs us the status code. */
    private fun readBody(stream: java.io.InputStream?): String {
        stream ?: return ""
        return try {
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        } catch (_: Exception) { "" }
    }

    /** OpenRouter wants attribution headers; other hosts reject unknown ones politely. */
    fun headersFor(url: String): Map<String, String> =
        if (url.contains("openrouter.ai", ignoreCase = true))
            mapOf("HTTP-Referer" to "https://jev-assistant.local", "X-Title" to "Jev Assistant")
        else emptyMap()

    /** Human-readable transport failures (no key material ever appears here). */
    private fun describe(e: Exception): String {
        val m = e.message ?: e.javaClass.simpleName
        return when {
            m.contains("timed out") || m.contains("timeout", true) -> "网络超时，请检查连接"
            m.contains("Unable to resolve host") -> "域名解析失败，地址填错或无网络"
            m.contains("Failed to connect") || m.contains("ECONNREFUSED") -> "无法连接该地址"
            m.contains("CertPath") || m.contains("SSL") -> "HTTPS 证书校验失败"
            else -> m
        }
    }
}
