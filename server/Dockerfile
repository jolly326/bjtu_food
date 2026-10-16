# 微信云托管 · Python 后端镜像
# 依据 docs/README.md「技术栈」与 docs/secur/S-03「限频走进程内」
FROM python:3.12-slim

WORKDIR /app

# 先装依赖，利用 Docker 层缓存（源码变更不触发重装）
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    TZ=Asia/Shanghai

COPY app ./app

EXPOSE 8000

# 🔴 单 worker 启动：限频计数在进程内，多 worker 会让实际阈值 × worker 数（见 docs/secur/S-03）
# 端口取平台注入的 $PORT（微信云托管按服务配置注入），未注入时回退 8000
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000}"]
