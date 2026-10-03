---
kind: external_dependency
name: 智谱 GLM 回复模型（OpenAI 兼容）
slug: bigmodel-glm
category: external_dependency
category_hints:
    - client_constraint
    - framework_behavior
scope:
    - '**'
source_files:
    - app/src/main/java/com/jev/probe/jev/ReplyClient.kt
    - app/src/main/java/com/jev/probe/jev/HttpJson.kt
---

### 智谱 GLM（BigModel）
- 角色：本项目的**回复生成**后端，通过 OpenAI 兼容的 `chat/completions` 接入。
- 稳定约束：
  - App **不支持 Anthropic messages 形态**（`/api/anthropic`、`/v1/messages`）；即使网关返回 HTTP 200，解析也会落空并伪装成成功。
  - `glm-5.3-flash` 是强制思考模型且默认 `reasoning_effort=max`，App 不传 `thinking`/`reasoning_effort`，导致短回复也要跑深度思维链，显著拖慢延迟。
  - 判断接口不能用智谱地址——它不是 Jev decisions 协议端点。
- 已知行为：智谱网关对不存在的路径返回 HTTP 200 + `{"code":500,"msg":"404 NOT_FOUND","success":false}`，旧版 HttpJson 仅按状态码分支会把这种软 404 当成成功。