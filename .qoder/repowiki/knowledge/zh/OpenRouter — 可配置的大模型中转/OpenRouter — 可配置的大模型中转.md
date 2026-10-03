---
kind: external_dependency
name: OpenRouter — 可配置的大模型中转
slug: openrouter
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
source_files:
    - app/src/main/java/com/jev/probe/core/Prefs.kt
---

### 角色
用户可选的判断 / 回复模型提供方之一，通过 OpenAI 兼容接口接入任意上游模型。

### 集成点
- 判断预设：Base URL `https://openrouter.ai/api`，模型 `typesafe/jev-1.13`。
- 回复预设：Base URL `https://openrouter.ai/api`，模型可换（如 `deepseek/deepseek-chat-v3.1`）。

### 稳定约束
- 判断链路要求 Jev decisions 协议（`/alpha/decisions` 或 `/v1/systemone`），不是通用 chat API；回复链路要求 OpenAI 兼容 `chat/completions`。