package com.jev.probe

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.jev.probe.billing.Entitlement
import com.jev.probe.billing.EntitlementRepo
import com.jev.probe.billing.PlanActivity
import com.jev.probe.core.Prefs
import kotlin.math.roundToInt

/**
 * Home / setup screen. Card-based layout with a live readiness summary, a
 * guided permission checklist (each row reflects its real granted state), a
 * prominent on/off switch, and a link to settings.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var container: LinearLayout
    private val a11yComponent =
        "com.jev.probe/com.google.android.accessibility.selecttospeak.SelectToSpeakService"

    private val accent = Color.parseColor("#3A7AFE")
    private val green = Color.parseColor("#16A34A")
    private val red = Color.parseColor("#DC2626")
    private val ink = Color.parseColor("#111827")
    private val sub = Color.parseColor("#6B7280")

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.decorView.setBackgroundColor(Color.parseColor("#F2F3F5"))

        val scroll = ScrollView(this)
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(28))
        }
        container.padForSystemBars()   // edge-to-edge: keep the title off the status bar
        scroll.addView(container)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        container.removeAllViews()

        container.addView(text("Jev 聊天助手", 24f, ink, bold = true))
        container.addView(text("在聊天 App 旁读对方消息（已支持 QQ、X、飞书），给出判断和候选回复。发送始终由你手动点。",
            13f, sub).apply { setPadding(0, dp(6), 0, dp(16)) })

        val a11y = isA11yEnabled()
        val overlay = Settings.canDrawOverlays(this)
        val key = prefs.hasAccess()   // own judge key or an active hosted session: either lets analysis run
        val ready = a11y && overlay && key

        // Readiness card
        container.addView(statusCard(ready, a11y, overlay, key))
        container.addView(privacyHint())

        // Hosted mode (trial / plan). Absent entirely in builds without a gateway.
        if (prefs.cloudAvailable()) container.addView(cloudCard())

        // Permission checklist
        container.addView(sectionLabel("权限设置"))
        container.addView(permCard("无障碍权限", "读取当前聊天窗口的消息文字", a11y) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        container.addView(permCard("悬浮窗权限", "在聊天窗口上方显示分析卡片", overlay) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        container.addView(permCard("自启动 + 省电无限制", "小米/HyperOS 必做，否则服务被冻结、读不到消息", null) {
            runCatching {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        })

        // Actions
        container.addView(sectionLabel("其他"))
        container.addView(actionRow("设置",
            if (BuildConfig.HOSTED_ONLY) "模型 · 关系 · 透明度 · 会话白名单" else "密钥 · 模型 · 关系 · 透明度 · 会话白名单") {
            startActivity(Intent(this, SettingsActivity::class.java))
        })

        // Master toggle
        val toggle = bigToggle(prefs.enabled)
        toggle.setOnClickListener {
            prefs.enabled = !prefs.enabled
            build()
        }
        container.addView(toggle)
    }

    // ---------------------------------------------------------------- cards

    private fun statusCard(ready: Boolean, a11y: Boolean, overlay: Boolean, key: Boolean): View {
        val c = cardBox()
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(dot(if (ready) green else red).apply {
            (layoutParams as LinearLayout.LayoutParams).rightMargin = dp(10)
        })
        head.addView(text(if (ready) "已就绪，可以用了" else "尚未就绪", 16f, if (ready) green else ink, bold = true))
        c.addView(head)
        c.addView(checkLine("无障碍", a11y))
        c.addView(checkLine("悬浮窗", overlay))
        val hosted = prefs.cloudActive() || BuildConfig.HOSTED_ONLY
        c.addView(checkLine(if (hosted) "官方托管" else "密钥", key,
            okWord = if (hosted) "已启用" else "已设", noWord = if (hosted) "未启用" else "未设"))
        // History recording is opt-in (off by default). Mention it here, never block on it.
        if (!prefs.contextEnabled) {
            c.addView(text("关联上下文未开启，可在设置里开启", 12f, sub).apply {
                setPadding(0, dp(8), 0, 0)
            })
        }
        return c
    }

    /**
     * Entry point of the hosted service, in one of three shapes:
     * - active: balance + 开通/续费 + a way back to the user's own key;
     * - own key already set (existing users): a quiet opt-in row, nothing changes by itself;
     * - nothing configured (new users): the primary call to action, free trial first.
     */
    private fun cloudCard(): View {
        val c = cardBox()
        if (prefs.cloudActive()) {
            val ent = EntitlementRepo.cached(prefs)
            c.addView(text("官方托管 · " + describe(ent), 15f, ink, bold = true))
            c.addView(text("免填密钥，分析走官方服务。", 12f, sub).apply { setPadding(0, dp(3), 0, dp(8)) })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(btn(if (ent?.isPro == true) "续费" else "开通订阅", true) {
                startActivity(Intent(this, PlanActivity::class.java))
            })
            // 订阅版没有自带密钥这条路。
            if (!BuildConfig.HOSTED_ONLY) row.addView(text("改用自己的密钥", 12f, accent).apply {
                setPadding(dp(16), dp(8), 0, dp(8))
                setOnClickListener {
                    prefs.cloudEnabled = false
                    if (!prefs.hasKey()) startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    build()
                }
            })
            c.addView(row)
            refreshBalance()
            return c
        }

        val newcomer = !prefs.hasKey()
        c.addView(text(if (newcomer) "免配置试用" else "官方托管服务", 15f, ink, bold = true))
        c.addView(text(if (newcomer) "不用申请密钥，送免费分析次数，用完再决定是否订阅。"
            else "不想自己管密钥？可改用官方托管，你现有的密钥设置不会被改动。", 12f, sub)
            .apply { setPadding(0, dp(3), 0, dp(8)) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(btn(if (newcomer) "开始试用" else "启用托管", true) { askConsentThenEnable() })
        if (newcomer && !BuildConfig.HOSTED_ONLY) row.addView(text("我有自己的密钥", 12f, accent).apply {
            setPadding(dp(16), dp(8), 0, dp(8))
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        })
        c.addView(row)
        return c
    }

    private fun describe(ent: Entitlement?): String = when {
        ent == null -> "同步中…"
        ent.isPro -> "订阅至 " + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
            .format(java.util.Date(ent.planExpiresAt))
        ent.trialRemaining > 0 -> "试用剩余 ${ent.trialRemaining} 次"
        else -> "试用已用完"
    }

    /** Re-read the balance in the background and redraw only if it changed. */
    private fun refreshBalance() {
        val before = prefs.cloudEntitlementJson
        Thread {
            EntitlementRepo.refreshQuietly(applicationContext, prefs)
            runOnUiThread { if (!isFinishing && !isDestroyed && prefs.cloudEntitlementJson != before) build() }
        }.start()
    }

    /**
     * Hosted mode sends chat text to the operator's gateway, which the app never
     * did before. So it starts only after an explicit confirmation (never
     * pre-accepted), and the consent is remembered per install.
     */
    private fun askConsentThenEnable() {
        if (prefs.cloudConsent) { enableCloud(); return }
        AlertDialog.Builder(this)
            .setTitle("启用官方托管前请确认")
            .setMessage("启用后，每次分析时的聊天文字和你开启的背景信息，会发送到本应用运营方的服务器，" +
                "再转发给模型服务商生成结果。服务器不保存聊天正文；截图仍只在本机识别。\n\n" +
                (if (BuildConfig.HOSTED_ONLY) "不同意则无法使用分析功能，可以先不开通。"
                else "不想这样，可以随时改回自己的密钥，数据就只发往你自己配置的接口。"))
            .setNegativeButton("取消", null)
            .setNeutralButton("隐私政策") { _, _ -> openUrl(PRIVACY_URL) }
            .setPositiveButton("同意并继续") { _, _ -> prefs.cloudConsent = true; enableCloud() }
            .show()
    }

    private fun enableCloud() {
        Toast.makeText(this, "正在开通试用…", Toast.LENGTH_SHORT).show()
        Thread {
            val err = try { EntitlementRepo.register(applicationContext, prefs); null } catch (e: Exception) { e.message ?: "请求失败" }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (err == null) prefs.cloudEnabled = true
                else Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                build()
            }
        }.start()
    }

    /** One tappable line under the readiness card, opening the privacy policy page. */
    private fun privacyHint(): View = text(
        if (prefs.cloudActive() || BuildConfig.HOSTED_ONLY) "读取的聊天内容会发往官方托管服务 · 隐私政策"
        else "读取的聊天内容只发往你自己配置的接口 · 隐私政策", 11f, sub).apply {
        setPadding(dp(2), dp(8), 0, 0)
        setOnClickListener { openUrl(PRIVACY_URL) }
    }

    /** Opens an external link; swallows the failure with a toast rather than crashing. */
    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            Toast.makeText(this, "打不开浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkLine(label: String, ok: Boolean, okWord: String = "已开", noWord: String = "未开"): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, 0)
        }
        row.addView(text(if (ok) "✓" else "✗", 14f, if (ok) green else red, bold = true).apply {
            (this as TextView).width = dp(22)
        })
        row.addView(text(label + (if (ok) okWord else noWord), 13f, sub))
        return row
    }

    private fun permCard(title: String, desc: String, granted: Boolean?, onClick: () -> Unit): View {
        val c = cardBox()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        left.addView(text(title, 15f, ink, bold = true))
        left.addView(text(desc, 12f, sub).apply { setPadding(0, dp(3), 0, 0) })
        if (granted == true) left.addView(text("✓ 已开启", 12f, green, bold = true).apply { setPadding(0, dp(4), 0, 0) })
        row.addView(left)
        row.addView(btn(if (granted == true) "已开启" else "去开启", granted != true, onClick))
        c.addView(row)
        return c
    }

    private fun actionRow(title: String, desc: String, onClick: () -> Unit): View {
        val c = cardBox()
        c.setOnClickListener { onClick() }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        left.addView(text(title, 15f, ink, bold = true))
        left.addView(text(desc, 12f, sub).apply { setPadding(0, dp(3), 0, 0) })
        row.addView(left)
        row.addView(text("›", 22f, sub))
        c.addView(row)
        return c
    }

    private fun bigToggle(on: Boolean): View {
        return TextView(this).apply {
            text = if (on) "助手已开启 · 点击关闭" else "助手已关闭 · 点击开启"
            textSize = 15f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (on) Color.WHITE else accent)
            background = roundBg(dp(14), if (on) accent else Color.WHITE, stroke = !on)
            setPadding(dp(16), dp(15), dp(16), dp(15))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(18) }
        }
    }

    // ---------------------------------------------------------------- atoms

    private fun cardBox(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundBg(dp(14), Color.WHITE)
        setPadding(dp(14), dp(13), dp(14), dp(13))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) }
    }

    private fun sectionLabel(t: String) = text(t, 12f, sub, bold = true).apply {
        setPadding(dp(2), dp(18), 0, dp(2))
    }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dot(color: Int) = View(this).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        layoutParams = LinearLayout.LayoutParams(dp(10), dp(10))
    }

    private fun btn(label: String, enabled: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = label; textSize = 13f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD)
        setTextColor(if (enabled) Color.WHITE else sub)
        background = roundBg(dp(10), if (enabled) accent else Color.parseColor("#E5E7EB"))
        setPadding(dp(16), dp(8), dp(16), dp(8))
        if (enabled) setOnClickListener { onClick() }
    }

    private fun roundBg(radius: Int, color: Int, stroke: Boolean = false) = GradientDrawable().apply {
        cornerRadius = radius.toFloat(); setColor(color)
        if (stroke) setStroke(dp(1), accent)
    }

    private fun isA11yEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains(a11yComponent)
    }

    companion object {
        private const val PRIVACY_URL = "https://chatjevs.com/privacy.html"
    }
}
