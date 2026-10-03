---
kind: external_dependency
name: Bocha Jev 决策模型（意图判断 / 排序）
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

### Bocha Jev
- 角色：本项目的**意图判断**与**候选排序**后端，不是通用聊天模型。App 的「判断接口」走的是 Jev decisions 协议，不是 OpenAI `chat/completions`。
- 请求体：`{model, state, questions}`；期望响应 `{answers:{true_intent:{choice,confidence,probabilities}, danger_level:{...}, should_reply_now:{...}}}`。
- 鉴权：`Authorization: Bearer <key>`，key 在 **open.bocha.cn**（博查AI开放平台）创建，与博查搜索 API 是同一把 key；`jev.bocha.cn` 只是 playground，不发 key。
- 稳定约束：不能把通用大模型地址（GLM、Anthropic、OpenRouter 根路径等）当判断接口用——它们不实现 decisions 协议，会静默返回空 `answers` 被误判为成功（已在 ResponseShape 中加固但仍需选对 pill）。
- verify exact API/params against official docs at https://jev.bocha.cn/v1/systemone and open.bocha.cn developer console.