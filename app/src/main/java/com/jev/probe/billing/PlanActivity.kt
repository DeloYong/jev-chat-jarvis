package com.jev.probe.billing

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jev.probe.BuildConfig
import com.jev.probe.core.Prefs
import com.jev.probe.padForSystemBars
import kotlin.math.roundToInt

/**
 * Plan page of the hosted service: current balance, the monthly pass, and the
 * payment hand-off. Payment happens in the system browser (the gateway returns
 * an Alipay/WeChat web-pay link), so no payment SDK lives in this app and no
 * UI element of any chat app is ever touched.
 *
 * Granting is server-side only: coming back from the browser we poll the order
 * until the gateway has verified the payment callback, then re-read the balance.
 */
class PlanActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var container: LinearLayout
    private val main = Handler(Looper.getMainLooper())

    /** Bumped on every poll start so an older loop stops once a newer one begins. */
    @Volatile private var pollGeneration = 0

    private val accent = Color.parseColor("#3A7AFE")
    private val ink = Color.parseColor("#111827")
    private val sub = Color.parseColor("#6B7280")

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.decorView.setBackgroundColor(Color.parseColor("#F2F3F5"))
        if (!prefs.cloudActive()) {
            Toast.makeText(this, "请先在首页启用官方托管", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val scroll = ScrollView(this)
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(28))
        }
        container.padForSystemBars()
        scroll.addView(container)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        if (!::container.isInitialized) return
        render(EntitlementRepo.cached(prefs))
        Thread {
            val ent = try { EntitlementRepo.refresh(applicationContext, prefs) } catch (_: Exception) { null }
            if (ent != null) main.post { if (!isFinishing && !isDestroyed) render(ent) }
        }.start()
        if (prefs.cloudPendingOrder.isNotBlank()) pollPendingOrder()
    }

    override fun onPause() {
        super.onPause()
        pollGeneration++   // stop polling while we are in the background (the browser is up front)
    }

    private fun render(ent: Entitlement?) {
        container.removeAllViews()
        container.addView(label("订阅", 24f, ink, bold = true))

        val status = card()
        status.addView(label(
            when {
                ent == null -> "同步中…"
                ent.isPro -> "订阅中 · 至 " + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                    .format(java.util.Date(ent.planExpiresAt))
                ent.trialRemaining > 0 -> "免费试用剩余 ${ent.trialRemaining} 次"
                else -> "试用已用完"
            }, 16f, ink, bold = true))
        if (ent?.isPro == true && ent.dailyCap > 0) {
            status.addView(label("今日已用 ${ent.dailyUsed} / ${ent.dailyCap} 次", 12f, sub).apply { setPadding(0, dp(4), 0, 0) })
        }
        container.addView(status)

        val plan = card()
        plan.addView(label("月卡 · ¥9.9", 18f, ink, bold = true))
        plan.addView(label("30 天托管服务，免填密钥。可连续购买，时长自动叠加；到期不会自动续费。", 12f, sub)
            .apply { setPadding(0, dp(4), 0, dp(10)) })
        if (ent != null && ent.dailyCap > 0) {
            plan.addView(label("合理使用：每天最多 ${ent.dailyCap} 次分析，次日恢复。", 12f, sub)
                .apply { setPadding(0, 0, 0, dp(10)) })
        }
        plan.addView(payButton("支付宝支付", "alipay"))
        plan.addView(payButton("微信支付", "wechat"))
        // The gateway refuses "mock" unless it is running with ALLOW_MOCK_PAY, and
        // release builds never show it.
        if (BuildConfig.DEBUG) plan.addView(payButton("模拟支付（仅调试）", "mock"))
        container.addView(plan)

        val pending = prefs.cloudPendingOrder
        if (pending.isNotBlank()) {
            container.addView(label("订单 $pending 等待支付结果。已付款但没到账，请带着订单号联系我们。", 12f, sub)
                .apply { setPadding(dp(2), dp(12), 0, 0) })
        }
        if (!BuildConfig.HOSTED_ONLY) {
            container.addView(label("不想订阅？在设置里填自己的接口密钥，同样免费使用。", 12f, sub)
                .apply { setPadding(dp(2), dp(14), 0, 0) })
        }
    }

    private fun payButton(title: String, channel: String): View = TextView(this).apply {
        text = title; textSize = 15f; gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE)
        background = round(dp(12), accent)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        setOnClickListener { startPay(channel) }
    }

    private fun startPay(channel: String) {
        Toast.makeText(this, "正在创建订单…", Toast.LENGTH_SHORT).show()
        Thread {
            val result = try { EntitlementRepo.createOrder(prefs, PLAN_MONTH, channel) } catch (e: Exception) { e }
            main.post {
                if (isFinishing || isDestroyed) return@post
                when (result) {
                    is EntitlementRepo.OrderTicket -> {
                        prefs.cloudPendingOrder = result.orderId
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.payUrl))) }
                            .onFailure { Toast.makeText(this, "打不开浏览器，无法完成支付", Toast.LENGTH_LONG).show() }
                    }
                    is Exception -> Toast.makeText(this, result.message ?: "下单失败", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /**
     * Poll the pending order for up to ~60 s. Not finding "paid" is normal (the user
     * may have backed out), so the order id just stays on screen for support.
     */
    private fun pollPendingOrder() {
        val gen = ++pollGeneration
        val orderId = prefs.cloudPendingOrder
        Thread {
            repeat(POLL_TIMES) {
                if (gen != pollGeneration) return@Thread
                val paid = try { EntitlementRepo.orderStatus(prefs, orderId) == "paid" } catch (_: Exception) { false }
                if (paid) {
                    val ent = try { EntitlementRepo.refresh(applicationContext, prefs) } catch (_: Exception) { null }
                    prefs.cloudPendingOrder = ""
                    main.post {
                        if (isFinishing || isDestroyed) return@post
                        Toast.makeText(this, "开通成功", Toast.LENGTH_SHORT).show()
                        render(ent ?: EntitlementRepo.cached(prefs))
                    }
                    return@Thread
                }
                try { Thread.sleep(POLL_INTERVAL_MS) } catch (_: InterruptedException) { return@Thread }
            }
        }.start()
    }

    // ---------------------------------------------------------------- atoms

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = round(dp(14), Color.WHITE)
        setPadding(dp(14), dp(13), dp(14), dp(13))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
    }

    private fun label(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun round(radius: Int, color: Int) = GradientDrawable().apply {
        cornerRadius = radius.toFloat(); setColor(color)
    }

    companion object {
        /** Must match a key of PLANS in the gateway (src/orders.ts). */
        private const val PLAN_MONTH = "month"
        private const val POLL_TIMES = 30
        private const val POLL_INTERVAL_MS = 2000L
    }
}
