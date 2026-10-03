package com.jev.probe.core

/**
 * 订阅版(HOSTED_ONLY)下的访问判定。抽成纯函数, 不依赖 Android, 便于单测。
 * 核心不变量: 订阅版任何情况下都不能把聊天文字发往第三方, 自带密钥也不能授予访问权。
 */
object HostedPolicy {
    /** 托管会话是否生效。订阅版不再有"启用托管"开关, 只看是否已注册拿到令牌。 */
    fun cloudActive(hostedOnly: Boolean, enabled: Boolean, available: Boolean, token: String): Boolean =
        available && token.isNotBlank() && (hostedOnly || enabled)

    /** 端点与凭证是否一律走网关。订阅版即使令牌暂缺也走网关(得到 401 自愈), 不回落到第三方。 */
    fun gatewayRoute(hostedOnly: Boolean, cloudActive: Boolean): Boolean = hostedOnly || cloudActive

    /** 是否允许发起分析。订阅版只认托管会话。 */
    fun hasAccess(hostedOnly: Boolean, cloudActive: Boolean, ownJudgeKey: String): Boolean =
        cloudActive || (!hostedOnly && ownJudgeKey.isNotBlank())
}
