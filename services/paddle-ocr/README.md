# PaddleOCR 本地扫描服务

这是 Personal English AI 的独立 OCR Provider，用于把扫描版 PDF、图片教材和截图资料转换成后端可消费的结构化 OCR JSON。

## 能力

- `GET /health`：健康检查，返回 PaddleOCR SDK 是否加载成功。
- `POST /ocr/pdf`：接收 PDF base64，按页渲染后 OCR。
- `POST /ocr/image`：接收 PNG/JPG/JPEG base64，输出单页 OCR 结果。
- 输出页码、整页文本、文本块、bbox、confidence、warnings。
- 公式识别通过 `enableFormula` 和 `PADDLE_OCR_ENABLE_FORMULA` 预留开关。当前默认关闭；未配置公式模型时返回 warning，不阻塞文本 OCR。

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

## 后端连接方式

本机运行 Spring Boot 时：

```bash
APP_OCR_PROVIDER=paddle
APP_OCR_PADDLE_BASE_URL=http://127.0.0.1:8090
APP_OCR_PADDLE_ENDPOINT=/ocr/pdf
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
  "enableTextOcr": true,
  "enableFormula": false,
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
      "warnings": []
    }
  ],
  "warnings": [],
  "elapsedMs": 1234
}
```

## 常见问题

- `/health` 返回 `DEGRADED`：PaddleOCR SDK 或模型加载失败，查看容器日志。
- `PDF_RENDERER_UNAVAILABLE`：PyMuPDF 未安装或镜像构建失败。
- `MAX_PAGES_TRUNCATED`：PDF 超过 `maxPages`，服务只处理前 N 页，避免一次性拖垮 OCR。
- `LOW_CONFIDENCE`：页面文本块平均置信度较低，应在前端提示用户复核。
- `FORMULA_ENGINE_UNAVAILABLE`：公式识别开启但公式模型未加载；文本 OCR 仍可正常返回。
