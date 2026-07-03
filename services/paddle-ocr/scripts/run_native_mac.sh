#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${SERVICE_DIR}"

if [[ ! -x ".venv/bin/uvicorn" ]]; then
  echo "Missing .venv. Run: python3 -m venv .venv && .venv/bin/python -m pip install -r requirements.txt" >&2
  exit 1
fi

export PADDLE_PDX_CACHE_HOME="${PADDLE_PDX_CACHE_HOME:-${SERVICE_DIR}/.cache/paddlex}"
export MODELSCOPE_CACHE="${MODELSCOPE_CACHE:-${SERVICE_DIR}/.cache/modelscope}"
export HF_HOME="${HF_HOME:-${SERVICE_DIR}/.cache/huggingface}"
export PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK="${PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK:-True}"
export PADDLE_OCR_VL_ENABLED="${PADDLE_OCR_VL_ENABLED:-true}"
export PADDLE_OCR_VL_PIPELINE_VERSION="${PADDLE_OCR_VL_PIPELINE_VERSION:-v1.6}"
export PADDLE_OCR_CPU_THREADS="${PADDLE_OCR_CPU_THREADS:-8}"
export PADDLE_PDX_CPU_NUM_THREADS="${PADDLE_PDX_CPU_NUM_THREADS:-${PADDLE_OCR_CPU_THREADS}}"

mkdir -p "${PADDLE_PDX_CACHE_HOME}" "${MODELSCOPE_CACHE}" "${HF_HOME}"

exec .venv/bin/uvicorn app.main:app --host "${PADDLE_OCR_HOST:-0.0.0.0}" --port "${PADDLE_OCR_PORT:-8091}"
