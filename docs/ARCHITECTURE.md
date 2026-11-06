# 架构与模块职责

> 三端（学生端小程序 / 运营端 Web / 后端 API）的**模块划分、依赖方向与横切口径**。
> 端点与字段见 [`api/`](./api/)，库表见 [`schema/`](./schema/)，页面形态见 [`ui/`](./ui/)，业务流程见 [`func/`](./func/)，安全边界见 [`secur/`](./secur/)。

## 1 · 后端（`server/`，FastAPI + SQLAlchemy）

| 目录 | 职责 | 依赖规则 |
|---|---|---|
| `app/core/` | 横切能力：配置 / DB 会话 / 鉴权依赖 / 信封 / 错误 / 分页 / 存储 URL / 内容安全 / 限频 / 缓存 / 检索（向量与精排） | **不依赖任何业务域与 models 之外的业务代码**；对外只暴露稳定入口 |
| `app/models/` | ORM 表映射（唯一真源在 [`schema/`](./schema/)） | 只依赖 `models` 与 `core`（Base） |
| `app/student/<域>/` | 学生端业务域（`auth` · `dish` · `feedback`），每域三层：`router`（HTTP 契约 + 入出参模型）→ `service`（业务规则）→ `repo`（SQL） | router 不直连 repo / model；service 不依赖 router；repo 不依赖 service / router |
| `app/admin/<域>/` | 运营端业务域（`auth` · `canteen` · `stall` · `dish` · `feedback` · `image`），分层同上 | 同上；`image` 无 repo（不落库） |
| `app/main.py` | 组合根：中间件（限频 → CORS → 压缩）、异常收敛、路由挂载、生命周期（启动预检 / 关闭连接池） | 只做装配，不承载业务规则 |
| `scripts/` | 一次性运维脚本（如向量回填），与线上请求路径无关 | 走 `core.db.session_scope()`，不复制业务规则 |

依赖契约由 `pyproject.toml` 的 import-linter 四条规则固化（`.venv/Scripts/lint-imports.exe`）。

## 2 · 运营端（`web/`，Vue 3 + TS + Element Plus）

| 目录 | 职责 | 依赖规则 |
|---|---|---|
| `src/api/` | 请求层 `request.ts`（基址 / 超时 / 信封 / 401 发现）+ 按资源拆分的端点模块（`auth` · `canteen` · `stall` · `dish` · `feedback` · `images`）与 `types.ts` | 视图与组件只经 `@/api` 调用；请求层不 import router / store |
| `src/stores/` | 会话状态（token / 账号名 / 登录登出） | 只依赖 `api`，不依赖视图 |
| `src/composables/` | 跨页状态机（分页 `usePagedList`、图片放大 `useImageZoom`） | 不依赖具体视图 |
| `src/components/` | ≥2 页复用的公共件（弹窗、列表卡、分页器、图片字段、图标、状态块） | 不依赖视图 |
| `src/views/` + `src/layouts/` | 路由页与框架层：只做编排（取数 → 状态 → 模板） | 页面私有逻辑留在页内，不上提公共层 |
| `src/styles/` | `theme.css`（token 与公共类唯一覆盖层）+ `motion.css`（动效与减弱动效） | 组件不各写一份 token 或跨页样式覆盖 |

**401 处置单一出口**：请求层只负责发现凭证失效（清凭证），会话状态与路由由 `main.ts` 注册的处理器收口 —— 避免「storage 已清、store 仍认为已登录」的双份真源。

## 3 · 学生端（`client/`，uni-app + Vue 3 + TS，编译目标微信小程序）

| 目录 | 职责 | 依赖规则 |
|---|---|---|
| `src/api/` | 按资源拆分的端点（`dish` · `feedback`）+ 出参类型 | 页面不直连 `utils/request` 之外的传输细节 |
| `src/utils/` | 与视图无关的技术能力：`request`（双通道传输 / 去重 / 取消 / 超时 / ETag）、`backoff`（429 文案解析与退避）、`image`（缩略图 URL） | 不依赖组件与页面；不写死 UI 文案 |
| `src/hooks/` | 组合式状态与副作用（`useTheme` · `useImageSource` · `useTimers`） | 可依赖 `api` / `utils` / `constants` |
| `src/components/` | 公共件（`AppIcon` · `ImagePlaceholder` · `PageLoading` · `RetryBlock` · `StatusBlock`） | 不依赖页面 |
| `src/pages/` | 页面与页面私有组件（`pages/home/**`） | 只做编排；页面私有件不下沉公共层，公共件不上提页面逻辑 |
| `src/theme/` | 颜色真源 `tokens.ts`（浅色 / 深色双份同键）+ 产物 `generated-colors.css` + 非颜色 `design-tokens.css` + 图标映射 `icons.ts` | 组件只消费语义 token，不写主题分支 |
| `src/constants.ts` | 魔法数字 / 阈值 / storage 键的唯一登记处 | 组件与页面不得内联同名数字 |

## 4 · 横切约定

- **事务边界**：写操作在 service 层显式 `session.commit()`（响应前落库），提交**之后**才失效 `student:dishes` 缓存；请求级会话依赖只负责异常回滚与归还连接。只读端点不提交空事务。
- **来源 IP**：限频主体取 `X-Forwarded-For` 的**受信跳**（从右数 `FORWARDED_HOPS`，默认 1；0 = 不看该头），左侧地址段不参与取址。
- **入参边界**：字符串长度与数值上下界在入参模型里与库列定义对齐，越界在参数层即 `400`，不落到数据库报错。
- **文档优先**：契约、库表、页面稿、业务规则分别以 `api/` · `schema/` · `ui/` · `func/` 为唯一真源；代码变更同批同步，冲突时按真源分级裁定。
- **契约快照**：后端出参模型是契约单源，`client/openapi.json` 与 `client/src/types/generated/api.d.ts` 由 `npm run gen:api:fresh` 生成，`check:contract` 校验一致。
- **静态门禁**（三端均可离线跑）：
  - 后端：`pytest`（SQLite 单测）· `ruff` · `mypy` · `lint-imports`；需验证唯一索引 / 外键级联 / 列长度严格模式时用 `TEST_MYSQL_URL=<含 test 的库> pytest tests/integration`（未提供则自动跳过）；
  - 运营端：`check:tokens` · `vitest` · `eslint` · `vue-tsc` · `build`；
  - 学生端：`check:contract` · `check:icon` · `check:ui` · `check:events` · `check:doc` · `eslint` · `vue-tsc` · `vitest` · `build:mp-weixin`。
- **运行时验证**（接口实跑 / 真机 / 浏览器实测）由人工执行，静态门禁不替代。
