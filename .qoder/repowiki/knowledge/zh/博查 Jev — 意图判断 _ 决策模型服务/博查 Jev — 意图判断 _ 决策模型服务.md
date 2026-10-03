---
kind: external_dependency
name: 博查 Jev — 意图判断 / 决策模型服务
slug: bocha-jev
category: external_dependency
category_hints:
    - vendor_identity
    - sdk_real_api
scope:
    - '**'
source_files:
    - app/src/main/java/com/jev/probe/jev/JudgeClient.kt
    - app/src/main/java/com/jev/probe/core/Prefs.kt
---

### 角色
项目内置的「判断」链路专用供应商：只返回选择题、打分、是非（true_intent / danger_level / should_reply_now），不生成自然语言回复。

### 集成点
- 内置预设：博查 Jev (`https://jev.bocha.cn`, `bocha-jev-v1`)、Vercel AI Gateway (`ai-gateway.vercel.sh/typesafe`, `typesafe-ai/jev`)、OpenCode Zen (`opencode.ai/zen`, `jev-1.13`)，以及 OpenRouter / TypeSafe 直连。

### 稳定用法
- 请求体 `{model, state, questions}`，响应体必须含 `answers` 字段（`true_intent` / `danger_level` / `should_reply_now` 子对象），否则走 ResponseShape 报错。
- API Key 在博查开放平台 `open.bocha.cn`（= `open.bochaai.com`）创建，与博查搜索 API 是同一把 key；`jev.bocha.cn` 只是 playground，不发 key。
- 验证 endpoint：`POST https://jev.bocha.cn/v1/systemone` 返回 401 `{"detail":"invalid API key"}`，非 `/v1/systemone` 路径（如 `/alpha/decisions`）返回 404。

verify exact API/params against official docs at open.bocha.cn / bocha-ai.feishu.cn wiki.