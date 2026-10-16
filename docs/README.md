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
> [`todo.md`](./todo.md) 是**待决事项清单（非真源）**：登记需要所有者拍板的开放议题；拍板后结论写入对应真源，再从该文件移除。

## 产品定位

**校园菜品展示与轻量评分平台** —— 帮学生解决「今天吃什么」。菜品与档口信息由运营录入；学生只做三件事：**看海报、刷菜、点反馈**。

- **学生端三个 tab**：海报（有哪些档口、卖什么、在哪）｜ 推荐菜品（撕日历式一次一道，可点「想吃」）｜ 记录（按日期分组，回来补评分）
- **运营端**：食堂 / 档口（含海报）/ 菜品录入 + 反馈数据查看
- **无搜索**；**无「我的」页面**
- **全匿名**：不做注册 / 登录 / 认证 / 注销；用户身份由微信 openid 静默识别，不在任何界面展示
- **无分享与文字 UGC**：学生侧仅「想吃」与基础评分两类动作

## 技术栈

- **后端**：Java 21 · Spring Boot 3.2 · MyBatis-Plus · MySQL 5.7（TDSQL-C）
- **学生端**：Vue 3 + TypeScript · uni-app（微信小程序）
- **运营端**：Vue 3 + TypeScript + Vite
- **质量门禁**：后端 JUnit 5 + ArchUnit；前端 `vue-tsc` / `check:contract` / `vitest`

## 文档树

| 目录 | 文档 |
|---|---|
| `docs/` | [`README.md`](./README.md) · [`todo.md`](./todo.md) |
| [`func/client/`](./func/client/README.md) | `README` · `A1-档口海报` · `A2-推荐菜品` · `A3-食堂筛选` · `B1-想吃标记` · `B2-评分反馈` · `C1-openid 静默识别` · `D1-隐私政策与用户协议` |
| [`func/web/`](./func/web/README.md) | `README` · `A1-食堂管理` · `A2-档口管理` · `A3-菜品管理` · `B1-反馈查看` · `C1-管理员登录` |
| [`ui/client/`](./ui/client/README.md) | `README` · `公共组件与形态基线` · `设计变量` · `档口海报` · `推荐菜品` · `记录与反馈` |
| [`ui/web/`](./ui/web/README.md) | `README` · `公共组件与形态基线` · `设计变量` · `登录页` · `食堂管理` · `档口管理` · `菜品管理` · `反馈数据` |
| [`api/`](./api/README.md) | `README` · `client/`（`auth` · `posters` · `dishes` · `canteens` · `reactions`） · `web/`（`auth` · `canteens` · `stalls` · `dishes` · `reactions` · `upload`） |
| [`schema/`](./schema/README.md) | `README` · `canteen` · `stall` · `dish` · `user` · `admin` · `dish_reaction` · `dish_want` |
| [`secur/`](./secur/README.md) | `README` · `S-01-身份与凭证` · `S-02-鉴权与归属校验` · `S-03-限频与接口防护` · `S-04-凭据与部署边界` |

> **归档区**：旧体系文档（126 份）整体归档于 [`old/`](./old/README.md)，**非真源**、不参与文档一致性与命名约束。

## 变更流程

1. 改**代码**时同步更新对应层文档（先按上表定位归属）。
2. 改**契约**（后端 VO / 端点）后需刷新端上类型：由 agent 本地启动后端（**仅限**抓取契约快照 `GET /v3/api-docs`）并执行 `client/` 下 `npm run gen:api:fresh`，随后跑 `npm run verify`。
3. 改**库表**：先改 [`schema/`](./schema/README.md) 对应表文档（唯一真源），再直连远程库执行 `ALTER` / `CREATE`；**禁只 ALTER 不同步文档**。
4. 提交前跑质量门禁：
   - 后端：`./mvnw test`
   - 前端：`cd client && npm run verify`

## 约定

- 文档只写**当前口径**：变更过程与修订轮次不维护（需回溯从 git 历史查）。
- 本目录**入库 git 跟踪**（真源须可 diff、可回滚）；**文档内不得出现任何凭据**（口令 / token / Webhook 地址 / 密钥），一律经环境变量与 `server/.env` 流转。
- 范围外的能力不建文档。
- 命名：目录内用 `<板块前缀>-<主题>.md`。
