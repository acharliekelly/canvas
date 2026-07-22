FROM python:3.13-slim AS runtime
WORKDIR /app
COPY pyproject.toml ./
RUN pip install --no-cache-dir \
    "fastapi==0.115.12" \
    "uvicorn[standard]==0.34.2" \
    "httpx==0.28.1" \
    "pytest==8.3.5"
COPY src ./src
COPY tests ./tests
ENV PYTHONPATH=/app/src
EXPOSE 8000
CMD ["uvicorn", "canvas_caption_worker.main:app", "--host", "0.0.0.0", "--port", "8000"]

FROM runtime AS test
CMD ["python", "-m", "pytest", "tests/test_health.py", "-q"]

FROM runtime AS application
