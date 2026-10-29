# 环境配置与切换

> **真源声明**：各端的 `.env*` 文件是配置真源，本文件只说明「怎么切、切了读哪份、注意什么」。
> 🔴 **本文件不得出现任何真值凭据**（口令 / token / 密钥 / 连接串真值）—— 一律用占位符。

三端各自独立切换，**互不影响**：

| 端 | 切换开关 | 开发档读 | 生产档读 |
|---|---|---|---|
| 后端 `server/` | 环境变量 `APP_PROFILE` | `.env.development`（+ `.env.local` 覆盖） | `.env.production` |
| 小程序 `client/` | Vite mode（由 npm script 决定） | `.env.development` | `.env.production` |
| 管理后台 `web/` | Vite mode（由 npm script 决定） | `.env.development` | `.env.production` |

---

## 0 · 一分钟速查

| 我想做什么 | 端 | 命令 | 连的后端 |
|---|---|---|---|
| 本地起后端 | server | `uvicorn app.main:app --reload --host 127.0.0.1 --port 8000` | — |
| 后台本地开发（热更新） | web | `npm run dev` | **本地** `127.0.0.1:8000` |
| 后台本地跑、连线上数据 | web | `npm run dev:cloud` | **云端** |
| 后台打包部署 | web | `npm run build` | **云端**（地址烧进产物） |
| 后台预览产物 | web | `npm run preview` | 云端 |
| 小程序本地联调 | client | `npm run dev:mp-weixin` | **本地** `127.0.0.1:8000` |
| 小程序本地跑、连线上数据 | client | `npm run dev:mp-weixin:cloud` | **云端** |
| 小程序打包上传 | client | `npm run build:mp-weixin` | **云端** |

> 前后端地址必须**成对切换**：前端切到云端而后端还在本地（或反之），表现就是列表一直「服务开小差」。

---

## 1 · 后端 `server/`

### 1.1 档位开关：`APP_PROFILE`

由 `server/app/core/config.py` 按下表选文件（**路径锚定 `server/`**，与启动时的工作目录无关）：

| `APP_PROFILE` | 加载的文件（后者覆盖前者） |
|---|---|
| 缺省 / `development` | `server/.env.development` → `server/.env.local` |
| `production` | `server/.env.production` |

- 🔴 **进程环境变量优先级最高**，会覆盖文件里的同名键。
- 🔴 **生产容器不携带任何 `.env*`**（`.dockerignore` 已排除），凭据一律由云托管控制台注入。
- 文件不存在时静默跳过 —— 生产实际只吃进程环境变量。

切换方式：

```powershell
# 开发档（缺省即开发，也可显式指定）
$env:APP_PROFILE = "development"

# 生产档
$env:APP_PROFILE = "production"
```

### 1.2 运行模式：`APP_ENV`

独立于档位，只控制**是否暴露调试面**：

| `APP_ENV` | 行为 |
|---|---|
| `dev` / `development` / `local` | 暴露 `/docs` 与 `/openapi.json`（本地联调、契约刷新用） |
| `prod` / `production` / 留空 | **不暴露**（云端默认即生产，无需设置） |

### 1.3 启动

```powershell
cd server
uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
# 等价：cd server/app && PORT=8000 python main.py
```

> 本项目 Python 环境为 conda 环境 `bjtu-food`。若 `python` / `uvicorn` 不在 PATH，
> 用完整路径调用：`D:\workspace\env\miniconda3\envs\bjtu-food\python.exe -m uvicorn ...`

### 1.4 变量清单（占位，真值写进对应 `.env*`）

| 变量 | 说明 |
|---|---|
| `APP_PROFILE` | 配置档位（见 §1.1） |
| `APP_ENV` | 运行模式（见 §1.2） |
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | 数据库连接（MySQL + PyMySQL），三选一组合即可 |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | 上一组变量的等价写法，填任一套 |
| `JWT_SECRET` | 学生端签名密钥（≥32 字节随机串） |
| `ADMIN_JWT_SECRET` | 运营端签名密钥，🔴 **必须与 `JWT_SECRET` 不同**，否则学生 token 可冒充运营端 |
| `JWT_TTL_SECONDS` | JWT 有效期（秒，默认 `604800` = 7 天） |
| `WECHAT_APPID` / `WECHAT_SECRET` | 微信小程序；与小程序后台不一致时 `/auth/wechat-login` 返回 400 |
| `CORS_ALLOW_ORIGINS` | `*` 或逗号分隔 Origin 白名单；生产取 `*` 会打告警，建议收敛为具体域名 |
| `COS_BUCKET` / `COS_REGION` / `COS_SECRET_ID` / `COS_SECRET_KEY` | 腾讯云 COS（图片转存） |
| `COS_DOMAIN` | 图片访问域名，🔴 **所有环境必填** —— 缺失时服务拒绝启动 |

---

## 2 · 小程序 `client/`

切换由 npm script 的 Vite mode 决定，无需手动设环境变量：

| 命令 | mode | 读的文件 | 连的后端 |
|---|---|---|---|
| `npm run dev:mp-weixin` | development | `.env.development` | 本地 `http://127.0.0.1:8000` |
| `npm run dev:mp-weixin:cloud` | production | `.env.production` | 云端 |
| `npm run build:mp-weixin` | production | `.env.production` | 云端 |

变量：`VITE_API_BASE_URL`（直连地址）· `VITE_TCB_ENV` + `VITE_TCB_SERVICE`（云调用 `wx.cloud.callContainer`）· `VITE_USE_COS_THUMB`。

- 🔴 **同时配置时云调用优先** —— 配了 `VITE_TCB_ENV` 就会绕开 `VITE_API_BASE_URL`。本地开发**不要配**云调用。
- 个人差异值写 `.env.development.local`（不入库，覆盖 `.env.development`）。

**真机调试的地址怎么填**（模拟器 / 真机不是一回事）：

| 跑法 | 地址 | 附加条件 |
|---|---|---|
| 开发者工具模拟器 | `127.0.0.1` | 勾选「详情 → 本地设置 → **不校验合法域名、web-view、TLS 以及 HTTPS 证书**」 |
| 真机预览 / 真机调试 | **本机局域网 IP**（如 `192.168.1.23`） | 手机与电脑同一 WiFi；Windows 防火墙放行 8000 入站；IP 写进 `.env.development.local`，不要改主文件 |

> 真机上 `127.0.0.1` 指的是**手机自己**，填它必然连不上电脑上的后端。

---

## 3 · 管理后台 `web/`

| 命令 | mode | 读的文件 | 连的后端 |
|---|---|---|---|
| `npm run dev` | development | `.env.development` | 本地 `http://127.0.0.1:8000` |
| `npm run dev:cloud` | production | `.env.production` | 云端（本地起服务，便于连线上数据排查） |
| `npm run build` / `build-only` | production | `.env.production` | 云端 |
| `npm run preview` | production | 用已构建的产物 | 云端 |

变量：`VITE_API_BASE_URL`。

🔴 **云端后端换了域名，须同步改 `web/.env.production` 与 `client/.env.production`** —— 两处同一口径。

---

## 4 · 凭据纪律（红线）

1. 🔴 **真值只写各端 `.env*`**，且这些文件均已 gitignore；`server/.env.example` **只列占位符，勿填真值**。
2. 🔴 **凭据不得进入文档 / 代码 / commit message / 规则文件** —— 本文件与 `docs/` 内一律只写变量名与占位符。
3. 🔴 生产凭据一律由**云托管控制台注入**（进程环境变量），不随镜像、不进仓库。
4. 需要改共享配置时改 `.env.development` / `.env.production`；**个人差异项改 `.env.*.local`**，避免污染共享口径。
5. 数据库直连凭据从 `server/.env` 读取，用于结构设计变更；变更后回写 `docs/schema/`。
