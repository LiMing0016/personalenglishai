FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt requirements-dev.txt ./
RUN pip install --no-cache-dir -r requirements.txt -r requirements-dev.txt

COPY . ./python/ai_orchestrator
RUN touch ./python/__init__.py
ENV PYTHONPATH=/app

EXPOSE 8002

CMD ["uvicorn", "python.ai_orchestrator.app:app", "--host", "0.0.0.0", "--port", "8002"]
