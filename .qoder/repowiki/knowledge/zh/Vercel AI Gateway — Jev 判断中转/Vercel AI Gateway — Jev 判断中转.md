---
kind: external_dependency
name: Vercel AI Gateway — Jev 判断中转
slug: vercel-ai-gateway
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
source_files:
    - app/src/main/java/com/jev/probe/core/Prefs.kt
---

### 角色
Jev 判断模型的第三方托管入口，协议与 TypeSafe 直连相同。

### 集成点
- 判断预设：Base URL `https://ai-gateway.vercel.sh/typesafe`，模型 `typesafe-ai/jev`。
- 请求 `/v1/systemone`，鉴权用 Vercel AI Gateway 的 key。

### 稳定约束
- 仅用于判断链路；回复链路仍需走 OpenAI 兼容 chat API。