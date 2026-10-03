---
kind: external_dependency
name: 智谱 GLM — 候选回复起草模型
slug: bigmodel-glm
category: external_dependency
category_hints:
    - vendor_identity
    - framework_behavior
scope:
    - '**'
source_files:
    - app/src/main/java/com/jev/probe/jev/ReplyClient.kt
---

### 角色
回复链路的生成模型：根据判断结果 + 上下文起草 3 条口语化候选，再由 Jev 排序。

### 集成点
- 默认 Base URL `https://open.bigmodel.cn/api/paas/v4`，模型 `glm-4.5`（也可换 `glm-4.5-air` / `glm-5.3-flash` 等）。

### 稳定用法与坑
- **GLM-5.3-flash 是强制思考模型**，API 不接受 `thinking.type=disabled`；未显式传参时默认 `reasoning_effort=max`（深度推理），导致短回复也跑完整思维链，延迟显著高于判断链路。降档需按域名 gate 注入 `reasoning_effort: low`，不能加小的 `max_tokens`（思考 token 计入输出预算）。
- 智谱网关对不存在的路径（如 `/api/anthropic`）返回 HTTP 200 + `{"code":500,"msg":"404 NOT_FOUND","success":false}`，旧代码会把空响应当成功；ResponseShape 已修复为抛错。
- Anthropic 原生 `/v1/messages` 在本项目不支持（App 强制拼 `/chat/completions`、不发 `anthropic-version`、只解析 `choices`）。