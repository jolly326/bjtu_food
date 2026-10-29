# ENVIRONMENT（开发 / 生产配置说明）

> 三端：`server/` 后端 · `client/` 学生端小程序 · `web/` 运营管理后台。
> 相关真源：`docs/api/`（契约）· `docs/schema/`（库结构）· `docs/secur/S-04-凭据与部署边界.md`（部署与凭据纪律）。

---

## 1. 三端一览

| 端 | 目录 | 依赖 | 开发命令 | 生产命令 |
|---|---|---|---|---|
| 后端 server | `server/` | `pip install -r requirements.txt -r requirements-dev.txt` | `uvicorn app.main:app --reload --port 8000` | 云托管控制台用 `server/Dockerfile` 发布 |
| 小程序 client | `client/` | `npm install` | `npm run dev:mp-weixin` | `npm run build:mp-weixin` |
| 管理后台 web | `web/` | `npm install` | `npm run dev` | `npm run build` |

**后端地址端口约定**：本地一律 `8000`（后端监听、两端 env 与文档同一口径）；容器取平台注入的 `$PORT`（未注入回退 `8080`）。

---

## 2. 开发环境：启动顺序与运行步骤

### 2.1 后端（先起，两端都依赖它）

```bash
conda activate bjtu-food            # Python 3.12
pip install -r server/requirements.txt -r server/requirements-dev.txt

cd server
cp .env.example .env                # 数据库连接 / JWT_SECRET / ADMIN_JWT_SECRET / WECHAT_* / COS_*
# 在 server/.env.local 写个人差别值 + APP_ENV=dev
uvicorn app.main:app --reload --port 8000
# 等价脚本方式（任意目录，默认 127.0.0.1:8000）：cd server/app && python main.py
```

`APP_ENV=dev` 才开放 `http://localhost:8000/docs` 与 `/openapi.json`（契约刷新依赖它）。
读取顺序：`server/.env` → `server/.env.local` → 进程环境变量（后者优先）。

### 2.2 管理后台（可视化，适合先联调后端）

```bash
cd web && npm install
npm run dev          # http://localhost:5173，读 web/.env.development ⇒ http://127.0.0.1:8000
```

浏览器直连不受小程序「合法域名」限制，所以后台可以直接连本机后端。

### 2.3 小程序

```bash
cd client && npm install
npm run check:env                 # 确认即将内联的地址
npm run dev:mp-weixin             # 产物 dist/dev/mp-weixin，连 http://127.0.0.1:8000
```

1. 微信开发者工具导入 `client/dist/dev/mp-weixin`
2. 勾 **详情 → 本地设置 → 不校验合法域名、web-view、TLS 以及 HTTPS 证书**
3. 控制台出现 `[request] 通道 = 直连 http://127.0.0.1:8000` 即生效

**真机预览**：真机上 `127.0.0.1` 是手机自己 ⇒ 把局域网 IP 写进 `client/.env.development.local`（不入库、优先级最高），重新编译：

```
VITE_API_BASE_URL=http://192.168.x.x:8000
```

**不改代码连云端排查**：`npm run dev:mp-weixin:cloud`（前端仍是未压缩产物，地址取 `client/.env.production`）。

---

## 3. 生产环境：配置与打包

### 3.1 后端

| 项 | 口径 |
|---|---|
| 凭据 | 只在**云托管控制台**注入环境变量，不进镜像、不进文档、不进代码 |
| 运行 | 单容器 + 单 worker（`uvicorn --workers 1`，限频计数在进程内） |
| 预检 | 缺 `JWT_SECRET` / `ADMIN_JWT_SECRET` 或两者相同 ⇒ **拒绝启动**（fail-fast） |
| 端口 | 取平台注入的 `$PORT`，须与探活端口一致 |
| 发布 | 控制台用 `server/Dockerfile` 构建并发布（镜像不含任何 `.env`） |

### 3.2 小程序

```bash
cd client
npm run verify                     # 契约 / 图标 / UI token / 事件命名 / 文档一致 / lint / 类型 / 单测
npm run check:env -- production    # 🔴 确认内联的是云端地址
npm run build:mp-weixin            # 产物 client/dist/build/mp-weixin
```

开发者工具打开 `client/dist/build/mp-weixin` → 上传 → 小程序后台（体验版 / 提审 / 发布）。
🔴 体验版 / 正式版**不接受 http 直连**：须 https 已备案域名并登记 request 合法域名，或在 `client/.env.production` 启用云调用（`VITE_TCB_ENV`，真机免白名单；两通道同时配置时云调用优先）。

### 3.3 管理后台

```bash
cd web
npm run verify                     # token 门禁 / 单测 / lint / 类型 / prettier / 构建
npm run build                      # 产物 web/dist（静态资源，挂在任意静态托管或随后端发布）
```

生产地址取 `web/.env.production`；改云端域名时须与 `client/.env.production` **同步**。

---

## 4. 环境变量清单

| 端 | 变量 | 说明 |
|---|---|---|
| server | `APP_ENV` | `dev` / `development` / `local` = 开发；其它（含缺省）= 生产 |
| server | `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | 数据库连接 |
| server | `JWT_SECRET` / `ADMIN_JWT_SECRET` | 学生端 / 运营端签名密钥，🔴 **不得相同** |
| server | `WECHAT_APPID` / `WECHAT_SECRET` | 换 openid |
| server | `COS_*` | 对象存储（菜品图 / 档口海报） |
| client / web | `VITE_API_BASE_URL` | 直连基址（后端在根路径提供服务，无版本段） |
| client | `VITE_TCB_ENV` / `VITE_TCB_SERVICE` | 云调用：环境 ID / 服务名（非空即接管通道） |
| client / web | `VITE_USE_COS_THUMB` | 地图磁贴是否追加 COS 缩略图参数 |

> 入库的文件只有不含凭据的公开基址：`client/.env.development`、`client/.env.production`、`web/.env.development`、`web/.env.production`；`*.local` 与 `server/.env*` 一律不入库。

---

## 5. 排错

| 现象 | 先看 |
|---|---|
| 请求发不出去 | `npm run check:env`（两通道都没配会以退出码 1 失败） |
| 本地包却访问云端 | 用了 `dev:mp-weixin:cloud` 或 `build:mp-weixin` |
| 真机能开页面无数据 | 用了 `127.0.0.1`（指手机）⇒ 换局域网 IP 并重新编译 |
| 模拟器「不在合法域名列表」 | 未勾「不校验合法域名」 |
| 改了 env 没生效 | 端上地址**构建期内联**，必须重新编译；后端则改完**重启** |
| 体验版请求失败 | http 直连不被允许 ⇒ https 备案域名或云调用 |
