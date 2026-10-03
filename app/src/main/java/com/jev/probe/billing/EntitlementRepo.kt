package com.jev.probe.billing

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.jev.probe.core.Prefs
import com.jev.probe.jev.ApiException
import com.jev.probe.jev.HttpJson
import com.jev.probe.jev.Route
import org.json.JSONObject
import java.util.UUID

/**
 * Hosted-mode entitlement as reported by the gateway. The server is the only
 * authority (it meters every call); this copy exists to render numbers and to
 * decide which screen to show, never to grant access.
 */
data class Entitlement(
    /** "pro" subscribed | "trial" free credits left | "none" nothing left. */
    val plan: String,
    /** Subscription end, epoch ms; 0 = never subscribed. */
    val planExpiresAt: Long,
    val trialRemaining: Int,
    /** Fair-use ceiling of analyses per day for subscribers. */
    val dailyCap: Int,
    val dailyUsed: Int
) {
    val isPro: Boolean get() = plan == "pro"

    fun toJson(): String = JSONObject()
        .put("plan", plan).put("planExpiresAt", planExpiresAt).put("trialRemaining", trialRemaining)
        .put("dailyCap", dailyCap).put("dailyUsed", dailyUsed).toString()

    companion object {
        fun fromJson(o: JSONObject) = Entitlement(
            plan = o.optString("plan", "none"),
            planExpiresAt = o.optLong("planExpiresAt", 0L),
            trialRemaining = o.optInt("trialRemaining", 0),
            dailyCap = o.optInt("dailyCap", 0),
            dailyUsed = o.optInt("dailyUsed", 0)
        )
    }
}

/**
 * Talks to the official gateway for identity and balance. Every function here
 * blocks on the network: call from a worker thread, never the main thread.
 * The token is a credential — it is stored in [Prefs] and never logged.
 */
object EntitlementRepo {

    private const val TAG = "JEVASSIST"

    /** Last balance seen, or null before the first successful sync. */
    fun cached(prefs: Prefs): Entitlement? {
        val raw = prefs.cloudEntitlementJson
        if (raw.isBlank()) return null
        return try { Entitlement.fromJson(JSONObject(raw)) } catch (_: Exception) { null }
    }

    /**
     * Stable id the gateway uses to stop "reinstall for fresh trial credits".
     * ANDROID_ID survives reinstalls for the same signing key on the same device
     * (【据我所知，需真机实测】) and changes on factory reset. A random fallback
     * covers the rare device that reports null or the known-bad shared value.
     */
    fun deviceId(ctx: Context, prefs: Prefs): String {
        val aid = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        if (!aid.isNullOrBlank() && aid != BROKEN_ANDROID_ID) return "aid-$aid"
        if (prefs.cloudDeviceFallback.isBlank()) prefs.cloudDeviceFallback = "rnd-" + UUID.randomUUID()
        return prefs.cloudDeviceFallback
    }

    /**
     * Register this device and keep the token. Idempotent on the server: the same
     * device gets the same account (same trial balance) with a fresh token.
     * Does NOT switch hosted mode on — the caller owns the consent flow.
     */
    fun register(ctx: Context, prefs: Prefs): Entitlement {
        require(prefs.cloudAvailable()) { "this build has no hosted gateway" }
        require(prefs.cloudConsent) { "hosted mode needs the user's consent first" }
        val resp = HttpJson.post(
            "${prefs.cloudBase()}/v1/device/register", "",
            JSONObject().put("deviceId", deviceId(ctx, prefs)), Route.CLOUD
        )
        val token = resp.optString("token")
        if (token.isBlank()) throw ApiException(Route.CLOUD, null, "网关没有返回 token", retryable = false)
        prefs.cloudToken = token
        val ent = Entitlement.fromJson(resp.getJSONObject("entitlement"))
        prefs.cloudEntitlementJson = ent.toJson()
        Log.i(TAG, "cloud registered plan=${ent.plan} trial=${ent.trialRemaining}")
        return ent
    }

    /** Pull the current balance; a missing or rejected token triggers one re-register. */
    fun refresh(ctx: Context, prefs: Prefs): Entitlement {
        if (prefs.cloudToken.isBlank()) return register(ctx, prefs)
        return try {
            fetch(prefs)
        } catch (e: ApiException) {
            if (e.status != 401) throw e
            register(ctx, prefs)
        }
    }

    /** [refresh] for fire-and-forget callers: a failure just leaves the cached balance in place. */
    fun refreshQuietly(ctx: Context, prefs: Prefs) {
        // 未同意前绝不发请求; 订阅版没有"启用托管"开关, 只看是否可用。
        if (!prefs.cloudAvailable() || !prefs.cloudConsent) return
        if (!prefs.cloudEnabled && !com.jev.probe.BuildConfig.HOSTED_ONLY) return
        try { refresh(ctx, prefs) } catch (e: Exception) { Log.w(TAG, "cloud refresh failed: ${e.javaClass.simpleName}") }
    }

    /** A freshly created order: open [payUrl] in the system browser to pay. */
    data class OrderTicket(val orderId: String, val payUrl: String)

    /**
     * Ask the gateway for a payment link. [channel] is "alipay" | "wechat" (| "mock",
     * which the gateway only honours when its own ALLOW_MOCK_PAY is on). Creating an
     * order grants nothing: access starts only once the payment callback reaches the
     * gateway, which [orderStatus] / [refresh] then report.
     */
    fun createOrder(prefs: Prefs, planCode: String, channel: String): OrderTicket {
        val resp = HttpJson.post(
            "${prefs.cloudBase()}/v1/orders", prefs.cloudToken,
            JSONObject().put("planCode", planCode).put("channel", channel), Route.CLOUD
        )
        val url = resp.optString("payUrl")
        if (url.isBlank() || !url.startsWith("https://")) {
            throw ApiException(Route.CLOUD, null, "网关没有返回有效的支付链接", retryable = false)
        }
        return OrderTicket(resp.getString("orderId"), url)
    }

    /** "created" | "paid". */
    fun orderStatus(prefs: Prefs, orderId: String): String =
        HttpJson.get("${prefs.cloudBase()}/v1/orders/$orderId", prefs.cloudToken, Route.CLOUD).optString("status")

    private fun fetch(prefs: Prefs): Entitlement {
        val resp = HttpJson.get("${prefs.cloudBase()}/v1/entitlement", prefs.cloudToken, Route.CLOUD)
        val ent = Entitlement.fromJson(resp)
        prefs.cloudEntitlementJson = ent.toJson()
        return ent
    }

    /** A widely-reported emulator/old-device value shared by many phones: useless as an identity. */
    private const val BROKEN_ANDROID_ID = "9774d56d682e549c"
}
