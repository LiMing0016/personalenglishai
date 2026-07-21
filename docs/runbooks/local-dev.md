---
title: 本地开发
status: active
owner: project
last_updated: 2026-07-21
review_cycle: on-change
related_code:
  - README.md
  - setup-local.ps1
  - start-local.bat
  - docker-compose.yml
  - python/ai_orchestrator/
related_docs:
  - docs/runbooks/environment-variables.md
  - docs/api/vocabulary.md
  - docs/ai/vocabulary-image-recognition.md
---

# 本地开发

## 适用场景

根目录 `README.md` 是通用启动入口。本文补充单词图片导入的本地启动、确定性验收、真实冒烟和回滚步骤。

## 常规启动

```powershell
.\setup-local.ps1
.\start-local.bat
```

本地脚本细节见[本地脚本](./local-scripts.md)，变量说明见[环境变量](./environment-variables.md)。

## 图片导入部署顺序

1. 历史库先执行既有 vocabulary migration，再执行 `backend/src/main/resources/db/migrate_create_vocabulary_product_events.sql`；新库只执行 `schema.sql`。
2. 启动 Python，配置视觉模型、45 秒总预算、OpenAI 凭据和共享 internal token。
3. 启动 Java，配置同一个视觉模型、Python base URL、55 秒 timeout 和同一个 token。
4. 启动 Web，初始保持 `VITE_VOCABULARY_IMAGE_RECOGNITION_ENABLED=false`。
5. 完成健康检查和真实冒烟后，最后把 Web 开关改为 `true` 并重启 Vite 或重新构建。

宿主机开发使用 `http://127.0.0.1:8011`；Compose 内 Java 必须使用 `http://assistant-orchestrator:8002`。Python 与 Java 的 `VOCABULARY_IMAGE_RECOGNITION_MODEL` 必须逐字一致。

## 确定性验收

```powershell
python -m pytest python/ai_orchestrator/tests/test_vocabulary_image_recognition_schemas.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_agent.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_workflow.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_endpoint.py python/ai_orchestrator/tests/test_vocabulary_image_recognition_real_smoke.py -q

cd backend
$env:JWT_SECRET='test-jwt-secret-for-vocabulary-image-32-bytes'
mvn test

cd ..\web
npx tsx --test "tests/vocabulary*.test.ts"
npm run build

cd ..\docs
npm run build
```

默认情况下真实图片 smoke 必须显示为 skipped。浏览器和 Playwright 只有在用户明确授权后才能运行；未运行时必须在验收报告中单独记录。

## 真实冒烟（按需启用）

仅在隔离环境和凭据齐备时设置：

```powershell
$env:RUN_VOCABULARY_IMAGE_RECOGNITION_REAL_SMOKE='1'
$env:VOCABULARY_IMAGE_RECOGNITION_MODEL='<vision-capable-model>'
$env:VOCABULARY_IMAGE_RECOGNITION_SMOKE_IMAGE='<local-image-path>'
```

用一张单词列表、一张含拼写错误的笔记和一个无标记段落验收。检查正常一次模型调用、结构错误最多两次、词典核验、OCR 来源 metadata、卡片 ready、事件 trace 关联，以及日志中没有图片、base64、`rawText`、词条、文件名、上下文、Prompt 或 token。

延迟验收至少记录图片识别 `elapsed_ms`，在同一受控流量窗口计算 P50/P95；同时确认取消或换图后的旧响应不会渲染。产品事件表只应出现白名单计数、时延和稳定枚举。

## 回滚与恢复

1. 将 `VITE_VOCABULARY_IMAGE_RECOGNITION_ENABLED=false`，重新构建或重启 Web。
2. 保留事件表、OCR 来源和已生成卡片，不执行破坏性 migration。
3. 若 Java 图片接口异常，再回退 Java；若内部调用异常，再回退 Python。
4. 恢复时按 Python、Java、Web 顺序重新部署，最后开启功能开关。

文本沉淀、词典收藏和 legacy URL 不依赖图片开关，回滚后应继续可用。

## 升级处理条件

出现以下任一情况应停止放量：结构错误率上升、P95 超过 Java timeout、模型名不一致导致事件拒绝、日志发现敏感内容、图片或 `rawText` 被持久化、重复词产生新卡、旧响应覆盖新图片结果。先关闭前端开关，再保留 trace 和安全计数排查。
