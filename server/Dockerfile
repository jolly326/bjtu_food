# 微信云托管 · Python 后端镜像
# 依据 docs/README.md「技术栈」、docs/secur/S-03（限频走进程内）与 S-04（部署边界加固）
FROM python:3.12-slim

WORKDIR /app

# tzdata：兑现 ENV TZ=Asia/Shanghai（slim 镜像不带时区数据，否则时间戳回落 UTC）
# 同时建容器内低权用户：镜像内一切以非 root 运行（docs/secur/S-04 §四）
RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 10001 appuser

# 先装依赖，利用 Docker 层缓存（源码变更不触发重装）
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    TZ=Asia/Shanghai \
    APP_PROFILE=production

COPY app ./app

USER appuser

EXPOSE 8080

# 🔴 单 worker 启动：限频计数在进程内，多 worker 会让实际阈值 × worker 数（见 docs/secur/S-03）
# --proxy-headers + --forwarded-allow-ips=*：信任平台反代的 X-Forwarded-For（访问日志给出真实客户端 IP）
# 端口取平台注入的 $PORT；未注入时回退 8080 —— 与云托管服务的探活端口一致
# （不一致时探针会 connect: connection refused，容器虽已启动却判为部署失败）
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8080} --workers 1 --proxy-headers --forwarded-allow-ips=*"]
