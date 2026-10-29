# 环境速查（开发 / 生产）

> 三端：`server/` · `client/` · `web/`。**所有命令都在项目根执行**：`cd D:\workspace\code\project\bjtu_food`

---

## 一、开发环境

| 端 | 读取的配置 | 连接目标 |
|---|---|---|
| server | `.env.development`（+ 可选 `.env.local`） | 本机库 `127.0.0.1:3306` |
| client | `.env.development` | `http://127.0.0.1:8000` |
| web | `.env.development` | `http://127.0.0.1:8000` |

### 1. 后端（先启动）

```bash
conda activate bjtu-food
pip install -r server/requirements.txt -r server/requirements-dev.txt
cd server
uvicorn app.main:app --reload --port 8000
```

日志出现 `APP_ENV=dev` 即开发档，`/docs` 可用。

### 2. 管理后台

```bash
cd web
npm install
npm run dev        # http://localhost:5173
```

### 3. 小程序

```bash
cd client
npm install
npm run dev:mp-weixin
```

开发者工具导入 `client/dist/dev/mp-weixin`，勾选「详情 → 本地设置 → 不校验合法域名」。

真机预览：新建 `client/.env.development.local` 写 `VITE_API_BASE_URL=http://<电脑局域网IP>:8000`，重新编译。

---

## 二、生产环境

| 端 | 动作 |
|---|---|
| server | 云托管控制台用 `server/Dockerfile` 发布；变量在控制台注入（镜像不含 `.env`，已固定 `APP_PROFILE=production`） |
| client | `npm run build:mp-weixin` → 导入 `dist/build/mp-weixin` 上传 |
| web | `npm run build` → 部署 `web/dist` |

发布前三端各跑一次门禁：`npm run verify`（client / web）。

体验版及以上不接受 http：须 https 备案域名，或配 `VITE_TCB_ENV` 走云调用。

---

## 三、怎么切换

| 目的 | 操作 |
|---|---|
| 后端本地连云端库排查 | `$env:APP_PROFILE="production"` 后再 `uvicorn ...`；用完 `Remove-Item Env:\APP_PROFILE` |
| 前端切环境 | 换命令即可（`dev:*` = 开发，`build:*` = 生产） |
| 配置生效 | 前端地址编译期内联 ⇒ 改完必须重编译；后端改 env 文件 ⇒ 重启进程 |

---

## 四、环境变量（只列名，值不入库）

| 端 | 变量 |
|---|---|
| server | `APP_ENV` · `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` · `JWT_SECRET` · `ADMIN_JWT_SECRET`（🔴 不得相同）· `WECHAT_APPID` / `WECHAT_SECRET` · `COS_*` · `CORS_ALLOW_ORIGINS` |
| client / web | `VITE_API_BASE_URL` |
| client | `VITE_TCB_ENV` / `VITE_TCB_SERVICE` · `VITE_USE_COS_THUMB` |

> 入库的只有公开地址：`client/.env.development`、`client/.env.production`、`web/.env.development`、`web/.env.production`。`*.local` 与 `server/.env.*` 一律不入库。

---

## 五、常见问题

| 现象 | 处理 |
|---|---|
| `Cannot find path ...\server\server` | 目录层叠了，先 `cd D:\workspace\code\project\bjtu_food` |
| 改了 `.env` 没生效 | 前端重编译、后端重启 |
| 小程序请求发不出 | 开发者工具未勾「不校验合法域名」 |
| 真机页面能开但没数据 | 用了 `127.0.0.1`（那是手机自己）⇒ 换局域网 IP 重编译 |
