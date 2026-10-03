# 订阅版配置收口与输入入口关闭 — 设计

状态：已与项目主人确认（2026-10-02），待审阅本文后进入实施计划。
范围：本文只覆盖 M0、M1；M2–M5 见末尾路线，各自单独立项。

## 1. 目标与非目标

**目标**
1. 所有上游接口的 URL、密钥、模型集中在**网关的一个 `.env`**，换模型/换上游只改配置，不发 APK。
2. 订阅版（构建时带 https 网关地址）**彻底没有**自带密钥入口：设置页无「接口」区，首页/悬浮窗/付费页无"填自己的密钥"。
3. 不带网关地址的构建（开源自行编译）行为完全不变。

**非目标**：上游自动切换、用量统计（M2）；支付联调（M3）；视觉接口走网关（OCR 用本机 ML Kit，视觉接口仅设置页"测试视觉"用，订阅版随设置页接口区一并消失）。

## 2. 关键约束与取舍

| 约束 | 结论 | 依据 |
|---|---|---|
| APK 内任何内容可被反编译取出 | 上游密钥**只放网关**；客户端 `.env` 只有非敏感的 `JEV_CLOUD_BASE` | 【通用最佳实践】；`CLAUDE.md` 硬约束 5（密钥不进 git/日志） |
| `.env` 不得含真实密钥入库 | 仓库只提交 `.env.example`（占位符），`.env`/`.dev.vars` 进 `.gitignore` | `CLAUDE.md` 硬约束 5 |
| 订阅版不得把聊天文字发往第三方 | `Prefs` 在订阅版下端点恒指向网关，令牌为空时返回 401 而不是回落到第三方 | 隐私承诺（PRIVACY.md 第 2A 节） |
| 同意先于上传 | 无 `cloudConsent` 时 `register` / `refreshQuietly` 一律不发请求 | PRIVACY.md 第 2A 节 |
| 旧版自带密钥 | 订阅版首次启动一次性清除 `judge/reply/vision` 密钥与旧 `openrouter_key`，不动 URL/模型字段 | 项目主人 2026-10-02 确认 |

## 3. 设计 A：网关配置收口（M0）

仓库：`/Users/luckincoffee/Documents/project/AI/jev-chat-gateway`（独立于本仓库）。

- `.env` 是唯一配置源，包含：判断上游（`JUDGE_UPSTREAM_URL/KEY`、`JUDGE_MODEL`）、回复上游（`CHAT_UPSTREAM_BASE/KEY`、`CHAT_MODEL`、可选 `REPLY_EXTRA_JSON`）、计量参数、支付参数、`TOKEN_PEPPER`。
- `src/config.ts` 的 `upstreamsOf(env)` 集中解析并校验：缺项、非 https、`REPLY_EXTRA_JSON` 非 JSON 对象都返回 `missing` 列表；`REPLY_EXTRA_JSON` 中的 `model/messages/stream/max_tokens/temperature` 被丢弃，防止配置绕过网关强制的输出上限。
- `proxy.ts` 只通过 `upstreamsOf` 取上游；校验失败返回 500 `upstream_not_configured`，日志只写缺失的**变量名**。
- `scripts/sync-env.mjs`：`.env` → 本地 `.dev.vars`；`--push` 经 `wrangler secret bulk` 把全部键作为 secrets 推到线上（线上不再用 `wrangler.toml [vars]` 放配置，避免同名冲突）。PEM 值在 `.env` 中写成单行并用字面量 `\n`，脚本还原。`wrangler secret bulk` 的确切子命令与参数【未验证，实施第一步先查 `npx wrangler secret --help`】。
- 客户端：仓库根 `.env`（gitignore）只放 `JEV_CLOUD_BASE`；`app/build.gradle.kts` 优先级 `-PjevCloudBase` > `.env` > 空。

## 4. 设计 B：关闭输入入口（M1）

- `BuildConfig.HOSTED_ONLY = CLOUD_BASE_URL.startsWith("https://")`。
- 访问判定抽成无 Android 依赖的 `HostedPolicy`（便于单测）：

| 函数 | 规则 |
|---|---|
| `cloudActive` | `available && token 非空 && (hostedOnly \|\| enabled)` |
| `gatewayRoute` | `hostedOnly \|\| cloudActive` — 为真时端点/密钥一律走网关 |
| `hasAccess` | `cloudActive \|\| (!hostedOnly && 自有密钥非空)` |

- `Prefs`：`judgeRouteKey/replyRouteKey/judgeEndpoint/replyEndpoint/cloudHeaders` 改用 `gatewayRoute`；`hasKey()` 订阅版恒 false；新增一次性 `wipeByokIfHostedOnly()`。
- `SettingsActivity`：「接口」三张卡抽成 `buildApiSection(root)`，返回保存闭包；订阅版不调用。关于文案按版本切换。
- `MainActivity`：去掉"改用自己的密钥""我有自己的密钥"；设置入口副标题去掉"密钥"；状态行与隐私提示按"托管"措辞；同意框去掉"随时改回自己的密钥"。
- `OverlayController.showPaywall`、`PlanActivity`：订阅版去掉"填自己的密钥"提示。
- `ChatCaptureService`：无访问权限时提示"打开 App 首页点「开始试用」"。
- `EntitlementRepo`：`register` 要求已同意；`refreshQuietly` 订阅版也需已同意且可用才请求。
- `PRIVACY.md`、`site/privacy.html`：加入"订阅版只有托管模式"的声明，并删掉 7 处"可切回自己的密钥"类承诺。

**错误处理**：订阅版令牌为空/失效 → 网关 401 → 客户端既有 `refreshEntitlementAsync` 自愈（受同意门控）；网关未配置上游 → 500 `upstream_not_configured`，客户端按通用错误展示。

## 5. 测试与验收

- 网关：`node --experimental-transform-types --test test/config.test.ts`；`npx tsc --noEmit`。
- Android：`HostedPolicyTest`（纯 JVM）；`./gradlew :app:testDebugUnitTest`；`assembleDebug` 分别在带/不带 `-PjevCloudBase` 下通过。
- 静态检查：APK 产物与仓库中无 `sk-or-`；`.env` 未被 git 跟踪。
- **自验缺口（需真机）**：订阅版设置页确无接口区、首页无密钥入口、旧版升级后旧密钥已清、首次同意前抓包无任何对网关的请求。

## 6. 后续路线（各自单独立项，不在本次范围）

| 期 | 内容 | 启动条件 |
|---|---|---|
| M2 | 上游备用、按模型参数、用量/错误率统计（不含正文） | M0/M1 上线后出现上游故障或需要看成本 |
| M3 | 真实商户支付联调、对账、退款 | 商户号与回调域名就绪 |
| M4 | 额度调参、到期提醒、季/年卡、兑换码 | 有试用转化数据 |
| M5 | 设备风控升级、备案等合规 | 面向公开分发之前 |
