package com.jev.probe.capture

/**
 * The pure decisions behind the capture service's failure paths.
 *
 * These live outside [ChatCaptureService] because every one of them used to be a
 * silent dead end there: the manual "分析当前对话" tap did literally nothing when
 * no snapshot had been read, and the manual "截屏识别一次" answered with one vague
 * toast covering three unrelated causes. WeChat hits both, because it strips the
 * node text its adapter matches bubbles and titles against.
 */
internal object CaptureRules {

    /**
     * Foregrounds where the bubble only gets in the way, and where a capture
     * would read our own UI instead of a conversation. An *unadapted* chat app is
     * deliberately NOT excluded: the bubble menu is its only way in.
     */
    fun isExcludedForeground(pkg: String?, ownPackage: String): Boolean {
        if (pkg.isNullOrBlank()) return true
        return pkg == ownPackage ||
            pkg == "com.android.systemui" ||
            pkg == "com.miui.home" ||
            pkg.contains("launcher", ignoreCase = true)
    }

    /**
     * Why a manual analyze tap cannot run, checked in the order a user would
     * notice: a master switch that is off outranks everything, then an analysis
     * already in flight, then "we never read this conversation".
     */
    fun manualBlock(hasSnapshot: Boolean, analyzing: Boolean, enabled: Boolean): ManualBlock =
        when {
            !enabled -> ManualBlock.DISABLED
            analyzing -> ManualBlock.BUSY
            !hasSnapshot -> ManualBlock.NO_SNAPSHOT
            else -> ManualBlock.NONE
        }

    /**
     * A bounded, content-free inventory of the resource ids in a tree, for
     * logcat. Only `viewIdResourceName` ever reaches this — never node text — so
     * it cannot leak a conversation.
     *
     * This is how an adapter's hard-coded id gets re-matched after an app update
     * renames it: the log names every id the current version actually exposes.
     */
    fun idInventory(ids: Iterable<String?>, limit: Int = 40): String {
        val distinct = ids.mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }.distinct().sorted()
        if (distinct.isEmpty()) return "0 个不同 id（树里没有带 id 的节点）"
        val shown = distinct.take(limit)
        val truncated = distinct.size > shown.size
        val head = if (truncated) "${distinct.size} 个不同 id（前 ${shown.size}）："
        else "${distinct.size} 个不同 id："
        return head + shown.joinToString(", ") + if (truncated) "…" else ""
    }
}

/**
 * Why the manual "分析当前对话" tap cannot run right now. [message] is what the
 * panel says; null means there is nothing to explain and the analysis proceeds.
 */
internal enum class ManualBlock(val message: String?) {
    NONE(null),
    DISABLED("助手已停用，去设置里把总开关打开"),
    BUSY("正在分析中，等这一轮跑完"),
    NO_SNAPSHOT("还没读到这个会话的内容，长按悬浮球选「截屏识别一次」")
}
