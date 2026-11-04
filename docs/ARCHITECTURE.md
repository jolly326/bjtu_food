# 架构与分包

> 本文件说明**三端（server / web / client）的包结构、职责边界与依赖方向**，是「代码放哪里、能不能这样依赖」的最高口径。
> 功能口径见 [`func/`](./func/)，接口契约见 [`api/`](./api/)，库表见 [`schema/`](./schema/)，视觉见 [`ui/`](./ui/)。

## 1 · 三端总览

| 端 | 目录 | 角色 | 技术栈 |
|---|---|---|---|
| 服务端 | `server/` | 唯一数据出口，学生端 / 运营端共用 | FastAPI · SQLAlchemy 2.0 · MySQL 8.0 · Redis（可选） |
| 运营端 | `web/` | 管理后台（食堂 / 档口 / 菜品 / 反馈处理） | Vue 3 · TypeScript · Vite · Element Plus（主题覆盖） |
| 学生端 | `client/` | 微信小程序（菜品首页浏览 / 搜索 / 反馈） | uni-app · Vue 3 · TypeScript |

三端**不共享运行时**；唯一跨端资产是 **SVG 图标资源**（各自入库，见 [`ui/client/图标资源.md`](./ui/client/图标资源.md)）。

## 2 · 服务端分层

```
server/app/
├── main.py                # 应用装配：路由聚合、中间件、异常处理
├── core/                  # 横切能力（被各层依赖，不反向依赖业务）
│   ├── config.py          # 环境与配置（pydantic-settings）
│   ├── db.py              # Engine / SessionLocal
│   ├── deps.py            # 依赖注入（当前会话、当前用户）
│   ├── envelope.py        # 统一响应封套
│   ├── errors.py          # 业务异常（BadRequest / NotFound / …）
│   ├── pagination.py      # 分页参数与结果
│   ├── cache.py           # 缓存读 / 失效（invalidate）
│   ├── ratelimit.py       # 限流
│   ├── schemas.py         # 入参 / 出参基类（camelCase 别名）
│   ├── security.py        # 口令 / 令牌
│   ├── storage.py         # COS 路径换算（to_relative / to_absolute）
│   ├── content_security.py # 微信 msgSecCheck（反馈正文）
│   ├── embedding.py       # 菜品描述向量（语义匹配输入）
│   └── formatters.py      # 时间等格式化
├── models/                # SQLAlchemy 模型（库结构唯一映射）
│   ├── base.py / admin.py / user.py
│   └── canteen.py / stall.py / dish.py / dish_offering.py / feedback.py
├── admin/                 # 运营端入口（按业务域分包）
│   ├── auth/  canteen/  stall/  dish/  feedback/  image/
│   └── 每个域：router.py（路由 + Pydantic 入/出参）· service.py（业务/校验/缓存失效）· repo.py（查询）
└── student/               # 学生端入口（只读 + 文本反馈）
    ├── auth/  dish/  feedback/
    └── 每个域同样 router → service → repo
```

**依赖方向（单向）**：`router → service → repo → models`；`core` 可被任意层引用，但 **`core` 不得反向导入业务**；`admin/` 与 `student/` **互不导入**；`models/` 不导入业务层。

**入口分离**：运营端与学生端是两套路由（`/admin/*` 与 `/student/*`），各自的鉴权、出参裁剪独立，**不要在一个域里混写两种身份**。

**约定与例外**：
- `admin/image/` 只有 `router.py` + `service.py`（无 `repo.py`）—— 它是上传中转，不落业务表。
- 库结构变更**不走 alembic、不维护 SQL 脚本**：先改 `docs/schema/` 对应表文档，再直连远程库执行，二者必须一致。
- 分层依赖由 `server/pyproject.toml` 的 `[tool.importlinter]` 契约约束（禁止跨层反向依赖）；新增业务域时若引入新层，需同步补充契约。

## 3 · 运营端（web）分包

```
web/src/
├── main.ts / App.vue          # 入口与根组件
├── router/index.ts            # 路由（按页面懒加载）
├── layouts/AdminLayout.vue    # 顶栏 + 底部应用坞（Dock）骨架
├── views/                     # 页面：一个页面一个 *View.vue
│   └── LoginView · CanteenView · StallView · DishView · FeedbackView
├── components/                # ≥2 页面复用的公共件
│   └── AppIcon · ConfirmDeleteCard · DishEditDialog · ImageField · ImageZoomOverlay · ListError · PaginationBar
├── composables/               # 可复用逻辑（无视图）
│   └── usePagedList · useAllPages · useBatchSelection · useImageZoom
├── directives/zoomImg.ts      # v-zoomimg（点图放大）
├── api/                       # 领域 API 封装 + 统一请求层
│   └── request.ts · auth · canteen · stall · dish · feedback · query · types
├── stores/session.ts          # 登录态
├── styles/                    # theme.css（唯一主题覆盖层）+ motion.css
├── utils/                     # 纯函数：image（上传前压缩 / 地址处理）
└── assets/icons/              # Plump Solid 实心图标（fill=currentColor，13 枚）
```

**依赖方向**：`views → components / composables → api → request`；`api/` **不得**导入 `views/` 或 `components/`；`components/` **不得**导入 `views/`；样式只从 `styles/theme.css` 与组件内 `scoped` 来。

**组件归属判定**：先问「是否 ≥2 页面使用」——是 ⇒ `components/`；否 ⇒ 留在页面内联或页面私有（当前运营端页面较薄，个别页面私有逻辑先放 `views/*.vue`，超过阈值再抽 `views/<page>/components/`）。

**弹层规范**：统一用自定义 `.mask` + `.dialog`（非 Element Plus Dialog）；删除确认用 `ConfirmDeleteCard` **整卡切换**；图片字段统一 `ImageField`（16:9、延迟上传）。

## 4 · 学生端（client）分包

```
client/src/
├── pages/home/index.vue           # 唯一页面：菜品图标网格 + 搜索 dock
│   └── components/                # 页面私有：HomeHeader · DishGrid · DishTile · DishExpandCard · SearchDock · FeedbackPanel
├── components/                    # ≥2 页复用的公共件
│   └── AppIcon · ImagePlaceholder · RetryBlock · PageLoading · StatusBlock
├── hooks/                         # 组合式逻辑：useImageSource · useTimers（统一出口 `hooks/index.ts`）
├── utils/                         # 纯函数：backoff · image · request
├── theme/                         # token 真源：tokens.ts · generated-colors.css · design-tokens.css · icons.ts · index.ts
├── api/                           # 学生端接口封装
├── types/generated/api.d.ts       # OpenAPI 生成类型
├── constants.ts                   # 魔法数字集中处
└── styles/                        # global.scss（基础样式）+ motion.scss（动效与减弱动效兜底）
```

**依赖方向**：`pages → pages/components → components / hooks / utils / theme / api`；页面私有组件**不跨页引用**；`utils` / `theme` **不得**导入 `pages`。

**约定**：一句话——「跨页才进 `components/`，单页下沉 `pages/home/components/`」；尺寸/时长/阈值/存储键集中在 `constants.ts`。

## 5 · 命名与新增清单

| 场景 | 规则 |
|---|---|
| 服务端业务域 | `app/{admin,student}/<domain>/{router,service,repo}.py`；模型 `models/<entity>.py` |
| 运营端页面 | `views/<Page>View.vue`；接口 `api/<domain>.ts`；公共件 `components/<Name>.vue` |
| 学生端页面私有件 | `pages/<page>/components/<Name>.vue`；逻辑 `hooks/use<X>.ts`；纯函数 `utils/<x>.ts` |
| 组件命名 | 组件一律 `<Name>.vue`（名词短语）；🔴 **`View` 后缀只给路由页面**（`views/<Page>View.vue`），公共件不得使用 |

新增一个业务域的推荐顺序：`docs/schema` → `models` → `admin|student` 的 `repo/service/router` → `docs/api` → `web/client` 的 `api` 与页面 → `docs/func|ui` → 门禁。

## 6 · 质量门禁

| 端 | 命令 | 覆盖 |
|---|---|---|
| 服务端 | `pytest`（本机需安装依赖；CI / Docker 执行） | 单测 + 分层约束 |
| 运营端 | `cd web && npm run verify` | `check:tokens` / `vitest` / `eslint` / `vue-tsc` / `prettier` / `build` |
| 学生端 | `cd client && npm run verify` | `check:contract` / `check:icon` / `check:ui` / `check:events` / `check:doc` / lint / `vue-tsc` / `vitest` / 构建 |

文档一致性：`docs/api`、`docs/schema`、`docs/func`、`docs/ui` 为真源，改代码必须同步；运营端无 `check:doc` 脚本，靠评审与旧口径检索兜底。

## 7 · 已知取舍

| 项 | 取舍 | 理由 |
|---|---|---|
| 菜品属性 | 只保留少量结构化硬字段（`price` 在售卖点）+ 一段 `description`；口味交给语义向量 | 避免「每道菜 × 每个维度」打标的高维护成本 |
| 售卖点 | 位置与价格落在 `dish_offering`，一菜可多食堂 / 多档口、各价独立 | 真实售卖情况是多对多带属性，单列装不下 |
| 反馈 | 产品反馈 + 菜品反馈统一文本表，正文过内容安全 | 任意表达、单表少一套接口与索引 |
| 图标资源库 | 两端**同源取形、分别入库**：矢量同源（Streamline Plump · Solid），着色按端落地 —— 学生端 `<image>` 不支持 `currentColor`，故写死 `fill="#8C7B6B"`；运营端一律 `fill="currentColor"` + `fill-rule="evenodd"` 挖孔（无描边）靠宿主上色。**未消费的图标键零消费即删** | 两端渲染机制不同，无法一端资源双端复用；不保留未消费资源，避免资源集漂移。CC BY 4.0 署名见 [`ui/client/图标资源.md`](./ui/client/图标资源.md) §6 |
| 学生端字体 | 用**系统圆体**（`--font-sans`），不引入 M PLUS Rounded / 思源圆体自定义字体文件 | 小程序 `wx.loadFontFace` 需 CDN 域名白名单且中文字体包体大；系统圆体已命中圆润字形 |
| 契约快照同步 | 走**自动链路**：本机有可用 conda 环境，后端契约变更后用 `gen:api:fresh` 重新抓取即可，**不维护手工同步脚本与副本** | 手工同步会与后端真源漂移；自动链路以 Backend Pydantic 模型为端上类型唯一真源 |
