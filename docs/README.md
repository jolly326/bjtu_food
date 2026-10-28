# bjtu_food — 文档索引

本目录是项目文档的**唯一入口**。按「事实类型」分层，每个事实只有一个归属处，避免同一信息在多处各写一份而漂移。

## 分层边界（改文档前先确认归属）

| 目录 | 回答什么问题 | 唯一真源 |
|---|---|---|
| [`api/`](./api/README.md) | 传什么、收什么 | 接口契约（端点签名、参数、响应结构） |
| [`schema/`](./schema/README.md) | 怎么存 | 数据库设计（表 / 列 / 约束 / 索引）；**库结构唯一真源** |
| [`func/client/`](./func/client/README.md) | 为什么这么算 | 学生端**业务规则** |
| [`ui/client/`](./ui/client/README.md) | 长什么样 | 学生端**视觉与交互**（页面稿、形态基线） |
| [`func/web/`](./func/web/README.md) | 运营端怎么用 | 管理端各功能板块的设计文档 |
| [`ui/web/`](./ui/web/README.md) | 运营端长什么样 | 管理端**视觉与交互**（设计变量 + 公共组件基线 + 页面稿） |
| [`secur/`](./secur/README.md) | **安全吗** | **安全设计真源**（鉴权 / 频控 / 凭据与部署边界） |

> **同一事实只写一处**。需要跨目录说明时用链接，不要复制正文。
> feature 文档只写业务规则，字段签名一律以 `api/` 为准；UI 文档只写视觉，字段同理。

## 产品定位

**校园菜品展示与轻量反馈平台** —— 帮学生解决「今天吃什么」。菜品与档口信息由运营录入；学生只做一件事：**看图找菜、点反馈（点赞 / 纠错）**。

- **学生端单页全屏沉浸**：**美食地图** —— 菜品以圆角正方形大图在**错位方格**上密铺于可平移缩放的 2D 平面上，点击磁贴唤起底部弹窗看详情、点赞与纠错；无 TabBar / 全局顶栏 / 页标题
- **运营端**：食堂 / 档口 / 菜品 录入 + 反馈数据查看
- **无搜索**；**无「我的」页面**
- **全匿名**：不做注册 / 登录 / 认证 / 注销；用户身份由微信 openid 静默识别，不在任何界面展示
- **无分享与文字 UGC**：学生侧仅「点赞」「菜品纠错选项」两类动作

## 技术栈

- **后端**：**Python · FastAPI · SQLAlchemy · MySQL 5.7（TDSQL-C）**
- **学生端**：Vue 3 + TypeScript · uni-app（微信小程序）
- **运营端**：Vue 3 + TypeScript + Vite
- **质量门禁**：后端 **pytest** + **分层护栏**（`import-linter` 等静态依赖检查）；前端 `vue-tsc` / `check:contract` / `vitest`

## 文档树

| 目录 | 文档 |
|---|---|
| `docs/` | [`README.md`](./README.md) |
| [`func/client/`](./func/client/README.md) | `README` · `A1-美食地图` · `B1-点赞反馈` · `B2-菜品纠错` · `C1-openid静默识别` |
| [`func/web/`](./func/web/README.md) | `README` · `A1-食堂管理` · `A2-档口管理` · `A3-菜品管理` · `B1-反馈查看` · `B2-纠错查看` · `B3-纠错选项管理` · `C1-管理员登录` |
| [`ui/client/`](./ui/client/README.md) | `README` · `Q版风格指南` · `公共组件与形态基线` · `设计变量` · `图标资源` · `美食地图` |
| [`ui/web/`](./ui/web/README.md) | `README` · `公共组件与形态基线` · `设计变量` · `登录页` · `食堂管理` · `档口管理` · `菜品管理` · `反馈数据` · `纠错处理` · `纠错选项` |
| [`api/`](./api/README.md) | `README` · `client/`（`auth` · `dishes` · `canteens` · `issues`） · `web/`（`auth` · `canteens` · `stalls` · `dishes` · `issues` · `images`） |
| [`schema/`](./schema/README.md) | `README` · `canteen` · `stall` · `dish` · `dish_rating` · `dish_issue` · `dish_issue_option` · `user` · `admin` |
| [`secur/`](./secur/README.md) | `README` · `S-01-身份与凭证` · `S-02-鉴权与归属校验` · `S-03-限频与接口防护` · `S-04-凭据与部署边界` |

> 本目录即现行设计的唯一真源。

## 变更流程

1. 改**代码**时同步更新对应层文档（先按上表定位归属）。
2. 改**契约**（后端模型 / 端点）后需刷新端上类型：由 agent 本地启动后端（**仅限**抓取契约快照 `GET /openapi.json`）并执行 `client/` 下 `npm run gen:api:fresh`，随后跑 `npm run verify`。
3. 改**库表**：先改 [`schema/`](./schema/README.md) 对应表文档（唯一真源），再直连远程库执行 `ALTER` / `CREATE`；**禁只 ALTER 不同步文档**。
4. 提交前跑质量门禁：
   - 后端：`pytest`
   - 前端：`cd client && npm run verify`

## 约定

- 文档只写**当前口径**：变更过程与修订轮次不维护（需回溯从 git 历史查）。
- 本目录**入库 git 跟踪**（真源须可 diff、可回滚）；**文档内不得出现任何凭据**（口令 / token / Webhook 地址 / 密钥），一律经环境变量与 `server/.env` 流转。
- 范围外的能力不建文档。
- 命名：目录内用 `<板块前缀>-<主题>.md`。

## 已知取舍

| 项 | 取舍 | 理由 |
|---|---|---|
| 点赞数 | **学生端展示点赞数**，不展示其它评价维度；运营端只读 `likeCount` | 守住「无二级页面、无等级」红线 |
| `likeCount` | 学生端 `DishVO` 下发 `likeCount`；运营端「反馈数据」展示 `likeCount` | 群体热度由 `dish_rating` 聚合，不落风味评级 |
| 运营「菜品管理」反馈列 | 运营端仅展示 `likeCount`（点赞数），无等级列 | 群体热度即点赞数，不设等级 / 评级 |
| 图标资源库 | 两端**同源预置**（`client/src/assets/icons` 与 `web/src/assets/icons`），未消费的图标键**保留不删**；门禁只提示、不阻断 | 复用同一套 Phosphor 资源，避免两端图标集漂移 |
| 深色模式原生窗口底 | `pages.json` 窗口底必须字面 hex，**不随主题切换** —— 冷启动与切换瞬间有短暂暖白底 | 平台限制（微信校验要求 hexColor）；暗色界面经 CSS 变量全站切换，首屏瞬时浅底可接受 |
| 深色模式不跟随系统 | 仅手动切换 + storage 持久化，默认浅色；学生端无全局切换入口（全屏沉浸无顶栏） | 跟随系统会使首屏底色不可控，与「安静、确定性」的品牌调性不符 |
