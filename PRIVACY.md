# 隐私政策

**Jev 聊天助手在你的设备上读取你正在看的聊天，把内容发给模型接口做判断和起草回复。接口有两种来源，由你选择：**

- **自带密钥模式（默认）**：内容只发往你自己在设置里配置的模型接口，运营方不运营中转服务器，收不到你的任何数据。
- **官方托管模式（可选，需你主动同意）**：免填密钥，分析请求经本应用运营方的网关转发给模型服务商，网关负责计量与订阅校验。本模式下，你的聊天文字**会经过运营方的服务器**，详见第 2A 节。

> **订阅版（内置托管网关地址的官方构建）只有托管模式，没有自带密钥入口，设置页没有接口/密钥输入框。** 下文"自带密钥模式"只适用于不带网关地址、自行从源码编译的构建。

版本 v1.2，生效日期 2026-10-02。适用范围：Android 端。Windows 版（[jev-chat-windows](https://github.com/jev-chat/jev-chat-windows)）与 macOS 版（[jev-chat-jarvis-mac](https://github.com/jev-chat/jev-chat-jarvis-mac)）是各自独立的仓库和安装包，不在本政策范围内，请分别查看它们自己的说明。官方托管模式仅存在于配置了托管网关地址的构建中；没有该地址的构建（例如自行从源码编译）不含任何托管入口，也不会连接任何运营方服务器。

如果你更习惯先看结论：本项目**不是**"零数据收集"产品——它一定会把你正在看的聊天文字发给一个模型接口。自带密钥模式下，这个接口是你自己填的；托管模式下，这个接口是运营方的网关，再由网关转发给模型服务商。除此之外的数据只留在你手机里，删应用或点一键清空都能删干净。下面逐项说清楚"发了什么、发给谁、什么时候发、存在哪、怎么删"。

---

## 1. 一句话总览

- 会离开你设备的，只有「用于生成判断和回复」这一批聊天文字和背景信息。自带密钥模式只发往**你自己在设置页填写的模型接口地址**；托管模式发往运营方网关。
- 自带密钥模式下，作者不运营任何后端服务器，代码里也没有向作者或任何固定第三方回传数据的逻辑；你的聊天内容作者看不到，也拿不到。
- 截图本身从不上传，识别文字（OCR）全部在手机本地完成。
- 密钥、设置、知识库、聊天历史都只存在手机本地的 App 私有目录，其它 App 读不到；卸载即清空。
- 无广告、无第三方统计 SDK、不用 Cookie 或广告标识符、不读通讯录、不读位置、不读其它 App 列表。

## 2. 会离开你设备的数据（自带密钥模式）

只发往一个地方：**你自己在设置页配置的模型接口地址**（默认是 OpenRouter 转发的 TypeSafe Jev / DeepSeek，你可以改成 TypeSafe 直连、Vercel AI Gateway、OpenCode Zen、DeepSeek 官方、通义千问兼容接口，或任意 OpenAI 兼容地址）。作者的服务器不在这条链路上，作者收不到、也看不到这些内容。

| 接口 | 默认服务商（可自行更换） | 每次发送的内容 | 触发时机 |
|---|---|---|---|
| 判断接口 | OpenRouter 转发 TypeSafe Jev | 当前聊天窗口最近 10 条消息的文本与方向（我方/对方）+ 你自己填写的关系描述 +（若开启知识库）命中的笔记与联系人备注 +（若开启历史记录）该联系人最近 N 条历史消息（N 默认 30，可调 0–100） | 你触发一次分析（自动或手动） |
| 回复接口 | OpenRouter 上的 DeepSeek | 同一批最近 10 条消息拼成的对话文本 + 同一份背景信息 + 生成提示词 | 判断完成后起草候选回复时 |
| 视觉接口 | 可配置，默认与判断接口一致 | 仅在你于设置页主动点击「测试视觉」时，发送一张 1×1 白色测试图；**不在日常采集主路径里** | 你手动点「测试视觉」 |

API 密钥会作为请求头（`Authorization`）随对应请求发给你自己配置的那个接口，只用于身份校验，不发往其它任何地方。

**不会发送的内容**：截图本身（识别文字在手机本地完成，见第 4 节）、通讯录、设备标识符、位置信息、其它 App 的列表或使用情况。

模型服务商拿到这些内容后如何处理，由它们各自的隐私政策决定，需要你自己去看：

- OpenRouter：https://openrouter.ai/privacy
- DeepSeek：[https://www.deepseek.com/privacy-policy](https://www.deepseek.com/privacy-policy)（或所选服务商官网的隐私政策页面）
- 阿里云百炼（通义千问兼容接口）：以阿里云官方隐私政策为准
- TypeSafe：以其官网公示的隐私政策为准

如果你自己填的是别的接口地址，那家服务商的政策同样适用，作者无法替它们承诺任何事。

## 2A. 官方托管模式（可选）

仅当你在首页点「开始试用 / 启用托管」并确认同意后才会启用；订阅版不提供切回自带密钥的入口；不同意则无法使用分析功能，已发出的请求按下文留存规则处理。

**发给运营方网关的内容**：与第 2 节判断接口、回复接口相同的那批内容（最近 10 条消息文本与方向、关系描述、你开启的知识库命中内容与历史），外加用于计量和鉴权的信息：设备令牌（Bearer token）、本次分析的随机编号（`X-Analysis-Id`）。

**网关如何处理**：
- 网关把上述内容转发给运营方选定的模型服务商（默认 OpenRouter 上的判断模型与 DeepSeek），模型由网关决定，不取决于你在设置里填的模型名。
- 网关**不保存聊天正文**：代码只做转发与计量，不把请求体写入数据库或日志。网关由运营方部署，部署环境的访问日志策略由运营方负责；请以运营方公示为准。
- 模型服务商如何处理收到的内容，由它们各自的隐私政策决定，运营方无法替它们承诺。

**网关会保存的数据**：

| 数据 | 用途 | 说明 |
|---|---|---|
| 设备标识的哈希值 | 防止重装反复领取免费试用次数 | 由 ANDROID_ID 加盐哈希得到，不存原值，不读 IMEI、手机号、通讯录 |
| 设备令牌的哈希值 | 鉴权 | 令牌原文只在你的手机里 |
| 试用剩余次数、订阅到期时间、当日已用次数 | 计量与订阅校验 | — |
| 分析编号与调用次数 | 同一次分析只计一次费 | 不含聊天内容 |
| 注册来源 IP 的哈希值（按日计数） | 限制同一网络批量注册 | 不存 IP 原文 |
| 订单记录（订单号、金额、渠道、状态、支付平台交易号） | 对账与售后 | 按财税法规要求的期限保存 |

**付款**：订阅通过支付宝或微信支付的网页支付完成，付款发生在支付平台页面，运营方拿到的只是订单结果，拿不到你的支付账号和银行卡信息。App 内不集成任何支付 SDK。

**删除与注销**：没有账号体系，身份即设备。卸载应用会删除手机上的令牌；如需删除网关侧与你设备关联的记录，请联系运营方（见第 10 节），需提供订单号或设备标识以便核验。

## 3. 只存在本机的数据

以下内容全部存放在 App 的私有目录（`/data/data/<包名>/`），其它 App 无法访问；应用私有目录内的数据不会随任何后台同步离开手机。

| 数据 | 用途 | 存放位置 | 保留时长 | 如何删除 |
|---|---|---|---|---|
| API 密钥、三路接口地址与模型名 | 连接你自己配置的模型服务 | SharedPreferences | 直到你修改或清除 | 设置页里改写，或卸载应用 |
| 托管设备令牌、同意记录、最近一次的试用/订阅余额、待支付订单号 | 托管模式的鉴权与界面展示（余额以服务器为准） | SharedPreferences | 直到切换或卸载 | 卸载应用；令牌保留在本机，卸载即清除；订阅版首次启动会清除升级前保存的自带密钥 |
| 关系描述、会话白名单、各项开关、悬浮窗位置与透明度 | 记住你的个性化配置 | SharedPreferences | 直到你修改 | 设置页里改，或卸载应用 |
| 知识库笔记（标题、内容、标签） | 你手动建的背景资料，供判断时检索引用 | `filesDir/kb/notes.json` | 直到你删除 | 设置页「清空知识库与历史」，或逐条删除 |
| 联系人档案（名称、别名、关系、备注） | 你手动建的联系人背景信息 | `filesDir/kb/contacts.json` | 直到你删除 | 同上 |
| 聊天历史 | 供判断时参考该联系人过往对话（默认关闭） | `filesDir/kb/logs/<联系人>.json` | 每位联系人最多保留 300 条，开启后才开始记录 | 设置页关闭该开关不再新增，「清空知识库与历史」一键清空 |

设置页的「清空知识库与历史」会删除 `kb` 目录下的全部内容，不影响密钥与其它设置；卸载应用会连同上述所有数据一起删除，没有云端备份。

日志（logcat）只输出消息条数、字符长度、异常类名这类调试信息，**不输出聊天正文**。

## 4. 权限与用途

| 权限 | 用途 | 不做什么 |
|---|---|---|
| 无障碍服务 | 读取当前聊天窗口的文字，把选中的回复填进输入框 | 不点发送键，不操作转账/红包/收款，不读取其它应用的数据库 |
| 截屏能力（无障碍服务附带） | 控件树读不到正文时（例如飞书），截取当前窗口做本地 OCR | 截图只在内存中处理，识别完即释放，不保存、不上传 |
| 悬浮窗 | 在聊天上方显示分析面板 | 不采集其它应用界面 |
| 网络 | 访问你自己配置的模型接口；托管模式下访问运营方网关 | 自带密钥模式不连接运营方的任何服务器；两种模式都无遥测、无埋点上报 |
| 前台服务 + 通知 | 保持服务不被系统冻结、清理 | 不推送营销通知 |

## 5. 我们不做什么

- 不自动发送消息：程序只把候选回复填进输入框，最后一步永远由你手动点发送。
- 不碰转账、红包、收款相关操作。
- 只处理你自己设备上、你自己有权查看的聊天，不处理其它人的设备。
- 无广告、无第三方分析或统计 SDK（不含 Google Analytics、Firebase、友盟等）、不使用 Cookie 或广告标识符。
- 自带密钥模式下，作者不运营任何服务器，不接收、不留存、不出售、不用于训练任何模型你的聊天内容——因为这些内容压根不经过作者。托管模式下内容会经过运营方网关：运营方承诺不保存聊天正文、不出售、不用于训练模型，但这是运营方的承诺而非技术上的"收不到"，不放心请不要使用订阅版，或自行编译开源版并使用自己的密钥。
- 开源：以上每一条说法，都可以在 GitHub 仓库里对照源码核实：https://github.com/jev-chat/jev-chat-jarvis

## 6. 你的控制权

- **关闭历史记录**：设置页里关掉「记录聊天历史」开关，之后不再新增；已有记录仍在本机，需手动清空。
- **清空知识库与历史**：设置页「清空知识库与历史」一键删除 `kb` 目录全部内容。
- **更换或自建接口**（仅自行编译的开源构建）：判断、回复、视觉三路接口地址、密钥、模型名都可以在设置页单独改成你信任的服务商，甚至自建的 OpenAI 兼容网关。
- **只用手动分析**：关闭自动分析开关后，只有你主动点击才会触发一次判断/生成，不会在后台持续读取。
- **会话白名单**：只对你加入白名单的会话生效，未加入的聊天不会被读取和分析。
- **卸载即清空**：卸载应用会删除本机存储的全部数据（密钥、设置、知识库、历史）。自带密钥模式没有云端账号或备份需要额外注销；若用过托管模式，网关侧还留有第 2A 节列出的记录，需另行联系运营方删除。
- **退出托管**：卸载应用即可停止发送；订阅版不提供切回自带密钥。

## 7. 第三方模型服务商

自带密钥模式下，你在设置页填写的接口地址决定了聊天内容最终发给谁；托管模式下由运营方网关选定的服务商处理。常见预设包括 OpenRouter、TypeSafe（直连）、DeepSeek 官方、阿里云百炼（通义千问兼容），你也可以填任意 OpenAI 兼容地址。这些服务商如何存储、使用、是否用于训练由它们自己的政策决定，请在使用前自行阅读对应服务商的隐私政策（见第 2 节链接）。作者不对第三方服务商的数据处理行为负责，也无法替它们做出承诺。

## 8. 儿童

本项目面向成年人，不面向 13 岁以下儿童，不主动收集年龄信息。如果你判断自己或被监护人不适合使用需要发送聊天内容到第三方接口的工具，请不要安装或使用本应用。

## 9. 变更

本政策如有修改，会同步更新本文件（GitHub 仓库）与官网 https://chatjevs.com/privacy.html 上的版本；涉及数据处理方式的重大变更，会在对应版本的发布说明（Release Notes / CHANGELOG）中提示。建议以仓库中的最新版本为准。

## 10. 联系

- GitHub Issues：https://github.com/jev-chat/jev-chat-jarvis/issues
- 公众号私信（二维码见仓库 README）
- 托管模式的运营方名称与联系方式：【发布托管版本前由运营方在此填写，未填写不得上线】

---

## English summary

Jev Chat Assistant (Android) reads the chat you are currently viewing on your device and sends that text to a **model endpoint you configure yourself** (default: OpenRouter routing to TypeSafe Jev / DeepSeek; you may switch to TypeSafe direct, DeepSeek official, Alibaba Cloud's Qwen-compatible endpoint, or any OpenAI-compatible URL) so it can judge intent and draft reply candidates.

- **Two modes.** In *bring-your-own-key* mode (default) no server is operated by the author and chat content never passes through author-controlled infrastructure. In the optional *hosted* mode (only in builds that ship a gateway address, and only after you explicitly consent) the chat text is sent to the operator's gateway, which forwards it to model providers it selects. The gateway does not store chat bodies; it stores only hashed device/token identifiers, quota and subscription state, analysis ids, per-day hashed-IP counters, and order records. Payment is done in the Alipay/WeChat web pages; no payment SDK is bundled. The subscription build has no bring-your-own-key option; build the open-source version from source to use your own key.
- **In bring-your-own-key mode, chat text goes only to your own configured model endpoint**, along with a relationship description you write, optionally matched knowledge-base notes/contact notes, and optionally recent history for that contact (default 30 messages, 0–100 adjustable, off by default).
- **Screenshots are never uploaded.** When a chat app's accessibility tree lacks readable text (e.g. Feishu), the screen is captured and OCR'd entirely on-device; the image is processed in memory and discarded, never saved or sent anywhere.
- **Local-only storage**: API keys, endpoint settings, knowledge-base notes/contacts, and (if enabled) per-contact chat history live only in the app's private storage on your device. Nothing syncs to the cloud. Uninstalling the app deletes all of it; a one-tap "clear knowledge base & history" option is also available.
- **No ads, no third-party analytics SDKs (no Google Analytics, Firebase, etc.), no cookies or advertising identifiers.**
- The app never sends messages automatically — you always press send yourself — and it never touches money transfers, red packets, or payments.
- The project is open source; every claim above can be verified against the source at https://github.com/jev-chat/jev-chat-jarvis.
- Third-party model providers you choose to use are governed by their own privacy policies, which you should review separately.

Version 1.2, effective 2026-10-02. Scope: Android app only.
