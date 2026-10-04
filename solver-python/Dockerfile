# Stratum program-synthesis solver (Python, STR-45)
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /srv/solver

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY solver/ ./solver/

# Cloud Run provides $PORT; default 8080 for local runs.
ENV PORT=8080 \
    SOLVER_MAX_TIME_SECONDS=120
EXPOSE 8080

CMD ["python", "-m", "solver.app"]
