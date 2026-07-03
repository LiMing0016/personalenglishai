# PaddleOCR 本地扫描服务

这是 Personal English AI 的独立 OCR Provider，用于把扫描版 PDF、图片教材和截图资料转换成后端可消费的结构化 OCR JSON。

## 能力

- `GET /health`：健康检查，返回 PaddleOCR 文本引擎、PPStructureV3 文档结构引擎和可选 PaddleOCR-VL 引擎状态。
- `POST /ocr/pdf`：接收 PDF base64，按页渲染后 OCR。
- `POST /vl/pdf`：接收 PDF base64，调用可选本地 PaddleOCR-VL 文档解析能力。
- `POST /ocr/image`：接收 PNG/JPG/JPEG base64，输出单页 OCR 结果。
- 输出页码、整页文本、文本块、结构化 `elements`、bbox、confidence、qualityScore、warnings。
- `PaddleOCR-VL` 会额外输出 `assets`，用于承载裁剪后的图片/图表区域；每个图片 asset 包含页码、bbox、mimeType、base64 图片数据、尺寸和 rawType。
- 支持 `standard` 和 `high_quality` 两种 `parseMode`。
- `high_quality` 默认调用 PaddleOCR `PPStructureV3` 文档解析 pipeline；前端选择本地 PaddleOCR-VL 后，后端会先尝试 `/vl/pdf`，失败再回退 `/ocr/pdf`。
- 公式、表格、版面能力按可用性接入：当前服务没有对应模型或 pipeline 不可用时返回 `*_ENGINE_UNAVAILABLE` warning，不阻塞文本 OCR。

## 运行模式

| 模式 | 推荐运行环境 | 用途 | 说明 |
| --- | --- | --- | --- |
| `standard` | 当前 Docker CPU、本机开发环境 | 稳定 OCR 文本、bbox、confidence、elements | 默认模式，保持低依赖和旧链路兼容 |
| `high_quality` | MacBook Pro M 系列、GPU 机器、独立 OCR 服务器 | 请求版面、表格、公式、方向检测、页面矫正 | 已接 `PPStructureV3` 适配层；pipeline 可用时返回结构化元素，不可用时降级 warning |
| `local-paddle-vl` | 32GB+ Docker 或宿主机 venv | 更接近 PaddleOCR-VL 演示页的 Markdown/版面解析 | 本地测试允许前端选择；只用于 `high_quality`，失败自动回退 `PPStructureV3` |

推荐把 Mac 或 GPU 机器作为远程 OCR 服务，在后端配置 `APP_OCR_PADDLE_BASE_URL=http://<remote-ip>:8090` 调用。

## OCR 运行画像配置

仓库根目录提供两份 OCR 画像配置，方便区分当前 Apple/Mac 路线和后续 NVIDIA A4000 路线：

| 配置文件 | 画像标识 | 适用场景 | 设备配置 |
| --- | --- | --- | --- |
| `.env.ocr.apple-mac.example` | `PEAI_OCR_RUNTIME_PROFILE=apple-local-mac` | 当前 Mac 本机原生 OCR 服务，Docker 后端通过 `host.docker.internal:8091` 调用 | `PADDLE_OCR_DEVICE=`，保持 CPU 路线 |
| `.env.ocr.nvidia-a4000.example` | `PEAI_OCR_RUNTIME_PROFILE=nvidia-a4000-remote` | 另一台 RTX A4000 电脑作为远程 OCR 服务器 | `PADDLE_OCR_DEVICE=gpu:0`，需要 GPU 版 PaddlePaddle |

`PEAI_OCR_RUNTIME_PROFILE` 只是人工标识，便于看配置时知道当前是哪条路线；代码实际调用仍由 `APP_OCR_PADDLE_BASE_URL` 和 `APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_BASE_URL` 决定。

## 本机 Docker 启动

```bash
docker compose -f docker-compose.local.yml up paddle-ocr
```

健康检查：

```bash
curl http://127.0.0.1:8090/health
```

第一次启动会下载 PaddleOCR 模型，耗时和网络、磁盘有关。CPU 模式可用，但大 PDF 会比较慢；GPU 部署后续可以单独做镜像。
当前镜像固定使用 `paddleocr==3.7.0`、`paddlex[ocr]==3.7.1` 与 `paddlepaddle==3.2.2`，这是用于文本 OCR 与 `PPStructureV3` 文档结构解析的兼容组合，避免自动安装到不兼容的最新 PaddlePaddle 版本。
Docker Compose 会持久化 `/root/.paddleocr` 和 `/root/.paddlex` 模型缓存，避免每次重建服务重复下载模型。
默认 Docker Compose 使用第一档 CPU 性能配置：`PADDLE_OCR_CPU_THREADS=4`，同时把 `PADDLE_PDX_CPU_NUM_THREADS` 传给 PaddleX 底层推理。不要通过 `OMP_NUM_THREADS`、`OPENBLAS_NUM_THREADS`、`MKL_NUM_THREADS` 拉高线程数；当前 PaddlePaddle/OpenBLAS 组合在 high_quality 懒加载 PPStructureV3 时可能发生 native segmentation fault。需要压测第二档时，优先在 `.env` 中把 `PADDLE_OCR_CPU_THREADS` 调到 `8` 后用固定 PDF 做基准测试。
`PaddleOCRVL` 类已随当前 `paddleocr==3.7.0` 安装进入镜像。稳定解析阶段默认 `PADDLE_OCR_VL_ENABLED=false`，避免普通上传误触发大模型加载。

## Apple / Mac 本机 high_quality 启动

Mac 本机建议作为独立 OCR 服务单独启动，不直接嵌入后端容器。基础流程：

```bash
cd services/paddle-ocr
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8090
```

如果是在当前 Mac 本机原生运行，建议使用脚本固定模型缓存和 CPU 线程数，默认监听 `8091`：

```bash
cd services/paddle-ocr
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
scripts/run_native_mac.sh
```

Docker 后端调用宿主机原生 OCR 时，把根目录 `.env` 指向 `host.docker.internal`：

```bash
PEAI_OCR_RUNTIME_PROFILE=apple-local-mac
APP_OCR_PADDLE_BASE_URL=http://host.docker.internal:8091
APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_BASE_URL=http://host.docker.internal:8091
APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_MAX_PAGES=500
PADDLE_OCR_DEVICE=
```

启动后在主项目后端配置：

```bash
APP_OCR_PROVIDER=paddle
APP_OCR_PADDLE_BASE_URL=http://<mac-or-gpu-ip>:8090
APP_OCR_PADDLE_PARSE_MODE=high_quality
APP_OCR_PADDLE_ENABLE_LAYOUT=true
APP_OCR_PADDLE_ENABLE_TABLE=true
APP_OCR_PADDLE_ENABLE_FORMULA=false
APP_OCR_PADDLE_ENABLE_ORIENTATION=true
APP_OCR_PADDLE_ENABLE_UNWARPING=true
```

验收：

```bash
curl http://<mac-or-gpu-ip>:8090/health
python scripts/smoke_ocr.py --base-url http://<mac-or-gpu-ip>:8090 --pdf sample.pdf
```

服务启动后会懒加载 PPStructureV3，因此首次 high_quality 请求前 `/health` 可能返回 `documentEngineLoaded=false`。如果首次 high_quality 后仍未加载，或请求返回 `LAYOUT_ENGINE_FAILED`，说明当前环境没有成功加载 `PPStructureV3` 或相关模型；服务仍会回退文本 OCR。
如果需要公式识别，可再打开 `APP_OCR_PADDLE_ENABLE_FORMULA=true` 与 `PADDLE_OCR_ENABLE_FORMULA=true`，但首次下载模型和 CPU 推理会更慢。

## 后端连接方式

本机运行 Spring Boot 时：

```bash
APP_OCR_PROVIDER=paddle
APP_OCR_PADDLE_BASE_URL=http://127.0.0.1:8090
APP_OCR_PADDLE_ENDPOINT=/ocr/pdf
APP_OCR_PADDLE_PARSE_MODE=standard
APP_OCR_PADDLE_TIMEOUT_MS=300000
APP_OCR_PADDLE_MAX_PAGES=20
APP_OCR_PADDLE_DPI=220
APP_OCR_PADDLE_ENABLE_LAYOUT=false
APP_OCR_PADDLE_ENABLE_TABLE=false
APP_OCR_PADDLE_ENABLE_FORMULA=false
APP_OCR_PADDLE_ENABLE_ORIENTATION=true
APP_OCR_PADDLE_ENABLE_UNWARPING=false
APP_TRANSLATION_DOCUMENT_IMPORT_INITIAL_OCR_PAGES=10
APP_TRANSLATION_DOCUMENT_IMPORT_BACKGROUND_CHUNK_PAGES=10
APP_DOCUMENT_PARSE_PDFBOX_ENABLED=false
APP_DOCUMENT_PARSE_PDFBOX_FALLBACK_ENABLED=false
```

Docker Compose 网络内运行后端时：

```bash
APP_OCR_PROVIDER=paddle
APP_OCR_PADDLE_BASE_URL=http://paddle-ocr:8090
APP_OCR_PADDLE_PARSE_MODE=high_quality
APP_OCR_PADDLE_TIMEOUT_MS=300000
APP_OCR_PADDLE_ENABLE_LAYOUT=true
APP_OCR_PADDLE_ENABLE_TABLE=true
APP_OCR_PADDLE_ENABLE_FORMULA=false
APP_OCR_PADDLE_ENABLE_ORIENTATION=true
APP_OCR_PADDLE_ENABLE_UNWARPING=false
APP_TRANSLATION_DOCUMENT_IMPORT_INITIAL_OCR_PAGES=10
APP_TRANSLATION_DOCUMENT_IMPORT_BACKGROUND_CHUNK_PAGES=10
APP_DOCUMENT_PARSE_PDFBOX_ENABLED=false
APP_DOCUMENT_PARSE_PDFBOX_FALLBACK_ENABLED=false
```

## NVIDIA RTX A4000 远程 GPU 启动

A4000 电脑适合作为独立 OCR 服务器。先在那台电脑确认 NVIDIA 驱动和 CUDA 运行时可用：

```bash
nvidia-smi
python -c "import paddle; print(paddle.device.cuda.device_count()); paddle.set_device('gpu:0'); print('gpu ok')"
```

当前仓库的 Dockerfile 安装的是 CPU 版 `paddlepaddle==3.2.2`，不能直接用显卡。A4000 电脑需要安装与 CUDA 版本匹配的 GPU 版 PaddlePaddle，或后续单独制作 GPU OCR 镜像。OCR 服务进程使用这组关键变量：

```bash
PEAI_OCR_RUNTIME_PROFILE=nvidia-a4000-remote
PADDLE_OCR_PORT=8090
PADDLE_OCR_DEVICE=gpu:0
PADDLE_OCR_VL_ENABLED=true
PADDLE_OCR_VL_PIPELINE_VERSION=v1.6
```

主项目切到 A4000 时：

```bash
PEAI_OCR_RUNTIME_PROFILE=nvidia-a4000-remote
APP_OCR_PROVIDER=paddle
APP_OCR_PADDLE_BASE_URL=http://<A4000_LAN_IP>:8090
APP_OCR_PADDLE_PARSE_MODE=high_quality
APP_OCR_PADDLE_ENABLE_LAYOUT=true
APP_OCR_PADDLE_ENABLE_TABLE=true
APP_OCR_PADDLE_ENABLE_FORMULA=false
APP_OCR_PADDLE_ENABLE_ORIENTATION=true
APP_OCR_PADDLE_ENABLE_UNWARPING=true
APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_ENABLED=true
APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_BASE_URL=http://<A4000_LAN_IP>:8090
APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_MAX_PAGES=500
```

后端 PDF 解析调度默认采用本地 PaddleOCR 稳定优先策略：只要 `APP_OCR_PROVIDER=paddle` 且 Paddle provider 可用，PDF 上传会调用本地 `/ocr/pdf`，不因 PDF 已有可复制文本层而绕过本地模型。导入接口先解析前 `APP_TRANSLATION_DOCUMENT_IMPORT_INITIAL_OCR_PAGES` 页并保存知识快照，返回工作台后由后台线程按 `APP_TRANSLATION_DOCUMENT_IMPORT_BACKGROUND_CHUNK_PAGES` 页继续补齐；后台完成前响应的 `ocrStatus` 为 `PARTIAL`，完成后更新为 `SUCCEEDED`。`high_quality` 仍可显式尝试结构化 OCR/VL 能力。`APP_DOCUMENT_PARSE_PDFBOX_ENABLED` 控制 PDFBox provider 是否可独立参与解析；`APP_DOCUMENT_PARSE_PDFBOX_FALLBACK_ENABLED` 控制 PaddleOCR 失败后是否允许回退 PDFBox。`docker-compose.local.yml` 默认两者都是 `false`，需要临时打开时在根目录 `.env` 中设置为 `true` 后重启 backend。

如果要单独实验本地 PaddleOCR-VL，把根目录 `.env` 改成：

```bash
PADDLE_OCR_VL_ENABLED=true
APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_ENABLED=true
APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_BASE_URL=http://paddle-ocr:8090
APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_ENDPOINT=/vl/pdf
APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_TIMEOUT_MS=300000
APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_MAX_PAGES=20
APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_DPI=220
APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_ENABLE_LAYOUT=true
APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_ENABLE_TABLE=true
APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_ENABLE_FORMULA=false
APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_ENABLE_ORIENTATION=false
APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_ENABLE_UNWARPING=false
```

然后重建 OCR 镜像并重启后端：

```bash
docker compose -f docker-compose.local.yml up -d --build paddle-ocr backend
```

启用后，`high_quality` PDF 解析顺序是：`local-paddle-vl` -> `paddle-ocr`/`PPStructureV3`；如果 `local-paddle-vl` 失败，后端日志会记录 provider 失败原因，并自动回退本地 PaddleOCR。`standard` 仍直接走 `/ocr/pdf`，避免普通上传触发大模型。
PDF 导入不会把整本书一次性塞给 VL：后端会先解析 `APP_TRANSLATION_DOCUMENT_IMPORT_INITIAL_OCR_PAGES` 页并返回页面，再按 `APP_TRANSLATION_DOCUMENT_IMPORT_BACKGROUND_CHUNK_PAGES` 页持续后台补齐整本书。`APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_MAX_PAGES` 只是单次 VL 请求上限，不要在本机测试时固定成 `2`，否则首批和后台分块都会被截断。

如果明确要接入百度官方 PaddleOCR-VL 文档解析 API，并保留本地 PaddleOCR 作为 fallback，在根目录 `.env` 中配置：

```bash
APP_DOCUMENT_PARSE_BAIDU_PADDLE_VL_ENABLED=true
APP_DOCUMENT_PARSE_BAIDU_PADDLE_VL_API_URL=<baidu-paddleocr-vl-api-url>
APP_DOCUMENT_PARSE_BAIDU_PADDLE_VL_TOKEN=<baidu-api-token>
APP_DOCUMENT_PARSE_BAIDU_PADDLE_VL_TIMEOUT_MS=300000
APP_DOCUMENT_PARSE_BAIDU_PADDLE_VL_MAX_PAGES=20
APP_DOCUMENT_PARSE_BAIDU_PADDLE_VL_USE_DOC_ORIENTATION_CLASSIFY=true
APP_DOCUMENT_PARSE_BAIDU_PADDLE_VL_USE_DOC_UNWARPING=true
APP_DOCUMENT_PARSE_BAIDU_PADDLE_VL_USE_LAYOUT_DETECTION=true
APP_DOCUMENT_PARSE_BAIDU_PADDLE_VL_USE_CHART_RECOGNITION=false
APP_DOCUMENT_PARSE_BAIDU_PADDLE_VL_PRETTIFY_MARKDOWN=true
```

百度云 API 默认关闭，不会产生费用。若同时启用本地 `local-paddle-vl` 和百度 `baidu-paddle-vl`，调度顺序是本地 VL 优先；本地 VL 失败后才会尝试百度 provider，之后再回退本地 `paddle-ocr`。`standard` 解析仍使用本地 PaddleOCR，避免普通上传消耗云端额度。

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
- `PaddleOCR-VL is disabled`：本地 VL 开关未打开；需要同时设置 `PADDLE_OCR_VL_ENABLED=true` 和 `APP_DOCUMENT_PARSE_LOCAL_PADDLE_VL_ENABLED=true`。
