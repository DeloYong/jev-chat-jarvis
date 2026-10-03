---
kind: external_dependency
name: OpenCode Zen — Jev 判断中转
slug: opencode-zen
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
source_files:
    - app/src/main/java/com/jev/probe/core/Prefs.kt
---

### 角色
Jev 判断模型的第三方托管入口，提供免费和付费两种模型档位。

### 集成点
- 请求 `/v1/systemone`，鉴权用 OpenCode Zen 的 key。

### 稳定约束
- 仅用于判断链路；协议与 TypeSafe 直连一致。