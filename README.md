# 知行食记

![Python](https://img.shields.io/badge/Python-3.12-3776AB)
![FastAPI](https://img.shields.io/badge/FastAPI-0.115-009688)
![MySQL](https://img.shields.io/badge/MySQL-5.7-4479A1)
![uni-app](https://img.shields.io/badge/uni--app-Vue3-4FC08D)
![Vue3](https://img.shields.io/badge/Vue-3-42b883)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

**校园菜品展示与轻量反馈平台** —— 帮学生解决「今天吃什么」。

菜品与档口信息由运营录入；学生只做一件事：**看图找菜、点反馈（点赞 / 纠错）**。

## 产品形态（学生端单页全屏沉浸）

- **美食地图**：圆角正方形卡片（菜品磁贴 + 功能方块 + `2×2` 大菜品卡）在可平移缩放的 2D 平面上**随机分布**；点磁贴唤起底部弹窗看详情、点赞与纠错。
- **无 TabBar / 全局顶栏 / 页标题** —— 无搜索、无「我的」页面、无菜品详情页。
- **全匿名**：微信 `openid` 静默识别，身份**不在任何界面展示**；不做注册 / 登录 / 认证 / 注销。
- **零自由输入**：学生侧只有「点赞」与「纠错选项」两类动作，没有文字与图片 UGC。
- **群体热度 = 点赞数**，不设风味评级 / 等级维度。

## 闭环

```
运营录入菜品 → 学生在美食地图上看图选菜 → 点磁贴看详情并点赞 / 纠错 → 点赞数与纠错记录回流运营端 → 反馈影响后续菜品展示与决策
```

## 管理后台（运营端）

- **食堂 / 档口 / 菜品**的录入与维护
- **反馈数据**（点赞数 / 纠错记录）查看与处置，**纠错选项字典**维护

## 技术栈

| 端 | 技术 |
|---|---|
| 后端 | **Python · FastAPI · SQLAlchemy · MySQL 5.7（TDSQL-C）** |
| 小程序 | uni-app · Vue 3 · TypeScript |
| 管理后台 | Vue 3 · TypeScript · Element Plus |

> **刻意不引入**（防后人顺手加，详见 `docs/`）：内容安全机审（学生侧零自由输入）· Redis（限频走进程内）· 用户认证与角色权限 · 操作审计表 · 数据导出。

## 项目结构

```
bjtu_food/
├── server/    # 后端服务（FastAPI）
├── client/    # 微信小程序（uni-app）
├── web/       # 管理后台（Vue 3）
└── docs/      # 设计文档 —— 唯一真源（定位 / 功能 / 契约 / 库结构 / UI / 安全）
```

## 快速开始

### 环境要求

Python **3.12**（conda）· Node.js 18+ · npm · 微信开发者工具 · MySQL **5.7+**

### 1. 后端环境

```bash
conda create -n bjtu-food python=3.12 -y
conda activate bjtu-food
pip install -r server/requirements.txt -r server/requirements-dev.txt
```

> 网络慢时加国内源：`-i https://pypi.tuna.tsinghua.edu.cn/simple`

### 2. 初始化数据库

> **本仓库不维护初始化脚本。** 库结构唯一真源 = [`docs/schema/`](./docs/schema/README.md)。

```bash
# 建库建表：按 docs/schema/ 各表文档的「列定义 / 索引」逐表 CREATE TABLE
# 演示数据：自行准备（运营端账号直接写入 admin 表）
```

### 3. 配置环境变量

**后端**：复制 `server/.env.example` 为 `server/.env`（`.env.local` 放个人差别值，均不入库）：

| 变量 | 说明 |
|---|---|
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | 数据库连接 |
| `JWT_SECRET` | **学生端** JWT 签名密钥 |
| `ADMIN_JWT_SECRET` | **运营端** JWT 签名密钥 —— 🔴 **必须与学生端不同**（否则学生 token 可冒充运营端） |
| `WECHAT_APPID` / `WECHAT_SECRET` | 微信小程序凭证（换 openid） |
| `COS_*` | 对象存储（档口海报 / 菜品图） |

**管理后台**：`web/.env.development`（连本机）/ `web/.env.production`（连云端），Vite 标准机制，开箱即用。

**小程序**：两套 env 已入库，本地开发**开箱即用**，无需复制：

| 文件 | mode | 用途 |
|---|---|---|
| `client/.env.development` | development（`npm run dev:mp-weixin`） | 连**本机后端** `http://127.0.0.1:8000` |
| `client/.env.production` | production（`npm run build:mp-weixin` / `npm run dev:mp-weixin:cloud`） | 连**云端**（云调用 `VITE_TCB_ENV`，或 https 域名直连） |

> 环境变量**构建期内联** —— 改完 env 必须重新编译。
> 真机预览请把「本机局域网 IP」写进 `client/.env.development.local`（不入库），别改已入库的 development：模拟器 `127.0.0.1` 指本机，**真机上 `127.0.0.1` 指手机自己**。

### 4. 启动

```bash
# 后端（🔴 单 worker 启动；接口文档 http://localhost:8000/docs）
cd server && uvicorn app.main:app --reload --port 8000
# 等价写法（任一层级目录下均可，默认也是 127.0.0.1:8000）：cd server/app && python main.py

# 管理后台（http://localhost:5173）
cd web && npm install && npm run dev

# 小程序 —— 用微信开发者工具导入 client/dist/dev/mp-weixin
cd client && npm install
npm run dev:mp-weixin          # 本地后端（默认，读 .env.development）
npm run dev:mp-weixin:cloud    # 同一份代码连云端后端读数据
npm run build:mp-weixin        # 发布构建（连云端）
```

> **运营端账号没有注册入口** —— 账号行直接建在 `admin` 表，口令存 **BCrypt 哈希**；改密同样是直连库更新哈希（步骤见 [`docs/schema/admin.md`](./docs/schema/admin.md)）。
> **学生端没有账号** —— 打开即用，冷启动静默换取 openid。

### 5. 切换环境（编译 / 部署）

小程序**没有运行期切换**：地址在构建期内联，换环境 = 换命令重新编译。三端的开发环境 / 生产环境配置、启动步骤与打包流程见 [ENVIRONMENT.md](./ENVIRONMENT.md)。

```bash
cd client
npm run check:env                 # 确认接下来编译出来的包连哪个后端
npm run dev:mp-weixin             # 本地开发 → 连本机 http://127.0.0.1:8000
npm run dev:mp-weixin:cloud       # 同一份代码连云端数据
npm run verify && npm run build:mp-weixin   # 发布构建（连云端）
```

> **真机预览要换 IP**：把 `VITE_API_BASE_URL=http://<电脑局域网IP>:8000` 写进 `client/.env.development.local`（不入库），再重新编译 —— 真机上 `127.0.0.1` 是手机自己，够不到电脑上的后端。

## 文档

| 文档 | 说明 |
|---|---|
| [ENVIRONMENT](ENVIRONMENT.md) | **三端开发与生产环境**（环境变量 / 依赖 / 启动 / 打包 / 排错） |
| [项目总览与文档树](docs/README.md) | 产品定位 · 分层边界 · 技术栈 · 变更流程 |
| [功能设计（学生端）](docs/func/client/README.md) | 美食地图 / 点赞 / 纠错的业务规则 + 项目约定与红线 |
| [功能设计（运营端）](docs/func/web/README.md) | 食堂 / 档口 / 菜品 / 反馈 / 纠错 / 登录 |
| [接口契约](docs/api/README.md) | 端点 / 入参 / 出参 / 错误码（通用约定在最前） |
| [数据库设计](docs/schema/README.md) | 9 张表的列 / 索引 / 约束（**库结构唯一真源**） |
| [UI 设计（学生端）](docs/ui/client/README.md) | 单页设计稿 + 公共组件与形态基线 + 设计变量 |
| [UI 设计（运营端）](docs/ui/web/README.md) | 列表 + 弹窗范式 + 公共组件基线 |
| [安全设计](docs/secur/README.md) | 身份凭证 · 鉴权与归属 · 限频 · 凭据与部署边界 |

## 贡献指南

欢迎任何形式的贡献 —— 报告 Bug、提交功能建议、改进文档，或直接发 Pull Request。

### 参与流程

1. Fork 本仓库，从 `main` 切出特性分支（`git checkout -b feat/amazing-feature`）
2. 按上面的「快速开始」搭好本地环境，完成改动
3. 提交前跑通各端自检：

   ```bash
   pytest                          # 后端：pytest + 分层护栏
   cd client && npm run verify     # 小程序：契约门禁 + 类型检查
   cd web && npm run verify        # 管理后台：token 门禁 + 单测 + lint + 类型检查 + 构建
   ```

4. 使用 [Conventional Commits](https://www.conventionalcommits.org/) 风格提交（如 `feat(client): ...`、`fix(server): ...`、`docs: ...`）
5. 发起 PR，说明改动动机与影响范围，等待 Review

### 报告问题

- Bug 与功能建议请提 [Issue](https://github.com/jolly326/bjtu_food/issues)，尽量附上复现步骤、运行环境与预期行为

### 项目约定

- 🔴 **文档优先**：改设计先改 `docs/` 对应真源，再动代码；文档与代码不一致时以 **`docs/`** 为准
- 涉及后端字段 / 端点变更时，须刷新端上契约产物（`cd client && npm run gen:api:fresh`）
- UI 改动以 `docs/ui/client/`（学生端）与 `docs/ui/web/`（运营端）的设计规范为准
- 数据库变更：**先改 `docs/schema/` 表文档，再执行 `ALTER`**，两者必须一致

## License

MIT
