---
kind: external_dependency
name: Google ML Kit 中文离线 OCR
slug: google-mlkit-text-recognition-chinese
category: external_dependency
category_hints:
    - vendor_identity
    - client_constraint
scope:
    - '**'
source_files:
    - app/build.gradle.kts
    - app/src/main/java/com/jev/probe/capture/ocr/MlKitOcr.kt
---

### 角色
Android 端截屏后的整屏中文文字识别，用于微信树节点不可读或飞书自绘正文时的兜底采集。

### 集成点

### 稳定约束
- 包体约 27 MB（含 bundled 中文模型），仅支持 arm64-v8a。
- 需要无障碍服务被系统允许截屏（XML 中 `canTakeScreenshot=true`），改完必须关闭再开启无障碍才生效；HyperOS 上伪装服务的截屏能力未验。
- 受保护窗口（`FLAG_SECURE`）无法截屏；长消息被截断部分读不到；识别有错字。
- 识别前无降采样，原图（~1200×1900）直接喂给 ML Kit，是手动截屏的主要耗时来源。