# 环境与配置

三端（`server/` · `web/` · `client/`）的开发 / 生产配置、启动与构建说明。业务与接口口径见 [`docs/`](./docs/INDEX.md)。

## 1 · 工具链

| 项 | 版本 / 说明 |
|---|---|
| Python | 3.12（conda 或 venv），服务端 |
| Node.js | 18+ 与 npm，`web/` 与 `client/` |
| 数据库 | MySQL 8.0（本地与 TDSQL-C 生产同版本，InnoDB / utf8mb4） |
| 小程序 | 微信开发者工具（导入 `client/dist/dev/mp-weixin`） |

## 2 · 服务端（`server/`）

环境文件按 `APP_PROFILE` 选择（路径锚定 `server/`）：

| `APP_PROFILE` | 加载文件 |
|---|---|
| `development`（缺省） | `server/.env.development` + `server/.env.local`（后者覆盖前者） |
| `production` | `server/.env.production` |

进程环境变量优先级最高；生产容器不携带 `.env*`，凭据由云托管控制台注入。

**数据库连接**（两组等价变量名，任填一套）：

- `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD`
- `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`

**其他关键变量**：`APP_ENV`（`dev` 时暴露 `/docs`，缺省视为生产）、`JWT_SECRET`（学生端）、`ADMIN_JWT_SECRET`（运营端，必须与学生端不同）、`WECHAT_APPID` / `WECHAT_SECRET`、`COS_BUCKET` / `COS_REGION` / `COS_SECRET_ID` / `COS_SECRET_KEY` / **`COS_DOMAIN`（必填，图片出参为绝对 URL，缺失即拒绝启动）**、`CORS_ALLOW_ORIGINS`（`*` 或逗号分隔白名单）。

**启动 / 测试**：

```bash
cd server
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000   # 单 worker
pytest
```

**库结构**：无 alembic，手工 SQL；真源为 [`docs/schema/`](./docs/schema/)，脚本在 `server/scripts/`。

## 3 · 运营端（`web/`）

- 配置：`web/.env.development`（连本机）、`web/.env.production`（连云端）；请求基址 `VITE_API_BASE_URL`。
- 启动 / 校验：

```bash
cd web
npm install
npm run dev      # http://localhost:5173
npm run verify   # check:tokens · vitest · eslint · type-check · prettier · build
```

## 4 · 学生端（`client/`）

两套 env 已入库，本地开发开箱即用：

| 文件 | 命令（mode） | 后端 |
|---|---|---|
| `client/.env.development` | `npm run dev:mp-weixin`（development） | 本机 `http://127.0.0.1:8000` |
| `client/.env.production` | `npm run build:mp-weixin` / `npm run dev:mp-weixin:cloud`（production） | 云端（`VITE_TCB_ENV` 云调用，或 https 域名直连） |

- 传输通道（构建期内联）：配 `VITE_TCB_ENV` 走 `wx.cloud.callContainer`；否则 `uni.request` + `VITE_API_BASE_URL`。
- 真机联调：把本机局域网 IP 写入 `client/.env.development.local`（不入库）。真机上 `127.0.0.1` 指手机自身。
- 环境变量构建期内联，改 env 必须重新编译。

```bash
cd client
npm install
npm run dev:mp-weixin
npm run verify
```

## 5 · 部署与切换

小程序没有运行期切换：换环境 = 换命令重新编译。运营端与后端各自按 `.env.production` / 进程环境变量部署；生产镜像不含 `.env*`。
