# PaddleOCR 本地扫描服务

这是 Personal English AI 的独立 OCR Provider，用于把扫描版 PDF、图片教材和截图资料转换成后端可消费的结构化 OCR JSON。

## 能力

- `GET /health`：健康检查，返回 PaddleOCR SDK 是否加载成功。
- `POST /ocr/pdf`：接收 PDF base64，按页渲染后 OCR。
- `POST /ocr/image`：接收 PNG/JPG/JPEG base64，输出单页 OCR 结果。
- 输出页码、整页文本、文本块、结构化 `elements`、bbox、confidence、qualityScore、warnings。
- 支持 `standard` 和 `high_quality` 两种 `parseMode`。
- `high_quality` 会优先调用 PaddleOCR `PPStructureV3` 文档解析 pipeline，尽量返回标题、段落、表格、公式等结构化 elements。
- 公式、表格、版面能力按可用性接入：当前服务没有对应模型或 pipeline 不可用时返回 `*_ENGINE_UNAVAILABLE` warning，不阻塞文本 OCR。

## 运行模式

| 模式 | 推荐运行环境 | 用途 | 说明 |
| --- | --- | --- | --- |
| `standard` | 当前 Docker CPU、本机开发环境 | 稳定 OCR 文本、bbox、confidence、elements | 默认模式，保持低依赖和旧链路兼容 |
| `high_quality` | MacBook Pro M 系列、GPU 机器、独立 OCR 服务器 | 请求版面、表格、公式、方向检测、页面矫正 | 已接 `PPStructureV3` 适配层；pipeline 可用时返回结构化元素，不可用时降级 warning |

当前 Windows 项目不需要本机直接跑 Apple Silicon 能力。推荐把 Mac 或 GPU 机器作为远程 OCR 服务，在后端配置 `APP_OCR_PADDLE_BASE_URL=http://<remote-ip>:8090` 调用。

## 本机 Docker 启动

```bash
docker compose -f docker-compose.local.yml up paddle-ocr
```

健康检查：

```bash
curl http://127.0.0.1:8090/health
```

第一次启动会下载 PaddleOCR 模型，耗时和网络、磁盘有关。CPU 模式可用，但大 PDF 会比较慢；GPU 部署后续可以单独做镜像。
当前镜像固定使用 `paddleocr==3.7.0` 与 `paddlepaddle==3.2.2`，这是已通过本地 CPU 容器烟测的组合，避免自动安装到不兼容的最新 PaddlePaddle 版本。

## Mac / M 系列 high_quality 启动

Mac 或 GPU 机器建议作为远程 OCR 服务单独启动，不直接嵌入 Windows 后端。基础流程：

```bash
cd services/paddle-ocr
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8090
```

启动后在主项目后端配置：

```bash
APP_OCR_PROVIDER=paddle
APP_OCR_PADDLE_BASE_URL=http://<mac-or-gpu-ip>:8090
APP_OCR_PADDLE_PARSE_MODE=high_quality
APP_OCR_PADDLE_ENABLE_LAYOUT=true
APP_OCR_PADDLE_ENABLE_TABLE=true
APP_OCR_PADDLE_ENABLE_FORMULA=true
APP_OCR_PADDLE_ENABLE_ORIENTATION=true
APP_OCR_PADDLE_ENABLE_UNWARPING=true
```

验收：

```bash
curl http://<mac-or-gpu-ip>:8090/health
python scripts/smoke_ocr.py --base-url http://<mac-or-gpu-ip>:8090 --pdf sample.pdf
```

如果 `/health` 是 `DEGRADED` 或 high_quality 返回 `LAYOUT_ENGINE_UNAVAILABLE`，说明当前环境没有成功加载 `PPStructureV3` 或相关模型；服务仍会回退文本 OCR。

## 后端连接方式

本机运行 Spring Boot 时：

```bash
APP_OCR_PROVIDER=paddle
APP_OCR_PADDLE_BASE_URL=http://127.0.0.1:8090
APP_OCR_PADDLE_ENDPOINT=/ocr/pdf
APP_OCR_PADDLE_PARSE_MODE=standard
APP_OCR_PADDLE_MAX_PAGES=500
APP_OCR_PADDLE_DPI=220
APP_OCR_PADDLE_ENABLE_LAYOUT=false
APP_OCR_PADDLE_ENABLE_TABLE=false
APP_OCR_PADDLE_ENABLE_FORMULA=false
APP_OCR_PADDLE_ENABLE_ORIENTATION=true
APP_OCR_PADDLE_ENABLE_UNWARPING=false
```

Docker Compose 网络内运行后端时：

```bash
APP_OCR_PROVIDER=paddle
APP_OCR_PADDLE_BASE_URL=http://paddle-ocr:8090
```

部署在另一台电脑时：

```bash
APP_OCR_PROVIDER=paddle
APP_OCR_PADDLE_BASE_URL=http://<remote-ip>:8090
APP_OCR_PADDLE_PARSE_MODE=high_quality
APP_OCR_PADDLE_ENABLE_LAYOUT=true
APP_OCR_PADDLE_ENABLE_TABLE=true
APP_OCR_PADDLE_ENABLE_FORMULA=true
APP_OCR_PADDLE_ENABLE_ORIENTATION=true
APP_OCR_PADDLE_ENABLE_UNWARPING=true
```

需要确认远程电脑防火墙放行 `8090`，且两台电脑处于可互通的局域网或 VPN。

## PDF OCR 示例

```bash
python scripts/smoke_ocr.py --base-url http://127.0.0.1:8090 --pdf sample.pdf
```

请求字段：

```json
{
  "documentBase64": "...",
  "language": "ch,eng",
  "pageStart": 1,
  "pageEnd": 3,
  "maxPages": 20,
  "parseMode": "standard",
  "enableTextOcr": true,
  "enableLayout": false,
  "enableTable": false,
  "enableFormula": false,
  "enableOrientation": true,
  "enableUnwarping": false,
  "dpi": 220
}
```

响应字段：

```json
{
  "status": "SUCCEEDED",
  "provider": "PaddleOCR",
  "pageCount": 1,
  "recognizedPageCount": 1,
  "pages": [
    {
      "pageNumber": 1,
      "text": "...",
      "rawText": "...",
      "cleanedText": "...",
      "elements": [
        {
          "type": "paragraph",
          "text": "...",
          "bbox": [[0, 0], [10, 0], [10, 10], [0, 10]],
          "confidence": 0.98,
          "order": 1,
          "source": "paddle_ocr",
          "rawType": "text",
          "warnings": []
        }
      ],
      "blocks": [
        {
          "text": "...",
          "bbox": [[0, 0], [10, 0], [10, 10], [0, 10]],
          "confidence": 0.98,
          "order": 1
        }
      ],
      "formulas": [],
      "confidence": 0.98,
      "qualityScore": 0.98,
      "layoutStatus": "NOT_REQUESTED",
      "tableStatus": "NOT_REQUESTED",
      "formulaStatus": "NOT_REQUESTED",
      "warnings": []
    }
  ],
  "warnings": [],
  "elapsedMs": 1234,
  "metadata": {
    "parseMode": "standard",
    "maxPages": 20,
    "dpi": 220
  }
}
```

## 常见问题

- `/health` 返回 `DEGRADED`：PaddleOCR SDK 或模型加载失败，查看容器日志。
- `PDF_RENDERER_UNAVAILABLE`：PyMuPDF 未安装或镜像构建失败。
- `MAX_PAGES_TRUNCATED`：PDF 超过 `maxPages`，服务只处理前 N 页，避免一次性拖垮 OCR。
- `LOW_CONFIDENCE`：页面文本块平均置信度较低，应在前端提示用户复核。
- `FORMULA_ENGINE_UNAVAILABLE`：公式识别开启但公式模型未加载；文本 OCR 仍可正常返回。
