---
kind: business_term
name: Business Glossary
category: business_term
scope:
    - '**'
---

### 判断接口
- Definition：本项目的意图分类与是否立即回复的决策后端，走 Jev decisions 协议（POST `{model, state, questions}`，返回 `{answers:{true_intent,...}, danger_level,...}`），由 Bocha Jev 提供，不是通用聊天模型。
- Aliases：意图判断接口、decision endpoint、judge client

### 回复接口
- Definition：本项目的聊天回复生成后端，走 OpenAI 兼容 `chat/completions`，默认对接智谱 GLM（`open.bigmodel.cn/api/paas/v4`）。
- Aliases：reply endpoint、draft client

### 悬浮球
- Definition：Android 无障碍服务通过 `OverlayController` 在聊天应用上显示的浮动入口按钮，长按弹出截屏识别、分析、填入等操作菜单。
- Aliases：浮球、overlay bubble

### 截屏识别一次
- Definition：悬浮球菜单中的手动 OCR 入口：绕过树路径直接调用系统截屏 + ML Kit 中文 OCR，用于微信等无法从无障碍树读到正文的场景。
- Aliases：手动 OCR、manual capture

### OCR 模式自动分析
- Definition：设置项 `ocrAutoAnalyze`（默认关闭），打开后 OCR 成功后自动进入意图判断与回复流程，否则需要手动点击分析。
- Aliases：ocrAutoAnalyze、auto analyze via OCR

### 气泡 id（BUBBLE_ID）
- Definition：WeChatAdapter 用来识别微信聊天窗口的固定 view id（`com.tencent.mm:id/bkl`），微信升级后该 id 可能变化导致树路径失效，必须靠截屏 OCR 兜底。
- Aliases：bkl、bubble id、WeChat bubble id

### 适配层（Adapters）
- Definition：按聊天 App 实现的会话提取策略集合（QQ、X、飞书、微信等），负责从无障碍树或截屏中抽取消息快照和会话标题。
- Aliases：adapters、ChatAppAdapter

### 测试判断 / 测试回复
- Definition：设置页的两个连通性诊断按钮：前者调判断接口验证 Jev decisions 协议，后者调回复接口 ping 模型返回「收到」。
- Aliases：ping judge、ping reply

### debug 包 / release 包
- Definition：本项目构建出的两种 APK：debug 包带 `application-debuggable=true`、公开调试签名，可明文读取 shared_prefs 中的 API Key；release 包用项目 keystore 签名，不可调试。两者 `versionName=1.4`、`versionCode=5` 相同，装完界面无法区分。
- Aliases：debug apk、release apk

### 防截屏风控
- Definition：微信对部分账号/设备开启的系统级保护，使无障碍服务的 `takeScreenshot` 返回 `FLAG_SECURE`（错误码 6），导致 OCR 兜底失败。这是 v1.4 最初禁用微信的原因。
- Aliases：FLAG_SECURE、screenshot protection

### 伪装服务（SelectToSpeakService）
- Definition：以 `com.google.android.accessibility.selecttospeak.SelectToSpeakService` 类名注册的无障碍服务，用于绕过某些厂商对无障碍服务截图能力的限制；在 HyperOS 上可能不被允许截屏。
- Aliases：disguised service、SelectToSpeakService
