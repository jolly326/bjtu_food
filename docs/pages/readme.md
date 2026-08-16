# 小程序页面设计文档索引

> 本目录为小程序端（`client/`）页面级设计文档，**统一遵循 [`TEMPLATE.md`](./TEMPLATE.md) 结构规范**；复用组件见 `docs/project_spec.md` §4.2，设计 token 见 `client/src/uni.scss` / `client/src/theme/tokens.ts`。
> 路由以 `client/src/pages.json` 为权威；本索引与其保持同步。
> **2026-08-15 更新**：原「反馈中心」(`messages-services`) 已合并入「意见反馈」(`feedback`)，本目录不再有独立反馈中心文档。

## 一、页面总览

| 包 | 路由路径 | 标题 | 文档 | 入口 |
|---|---|---|---|---|
| 主包 | `pages/home/index` | 首页 | [首页.md](./首页.md) | TabBar |
| 主包 | `pages/find/index` | 搜索 | [搜索页.md](./搜索页.md) | 首页搜索框 `navigateTo` |
| 主包 | `pages/community/index` | 动态 | [动态页.md](./动态页.md) | TabBar |
| 主包 | `pages/feedback/index` | 意见反馈 | [意见反馈.md](./意见反馈.md) | 「我的」菜单 / 底部 Tab 入口 |
| 主包 | `pages/profile/index` | 我的 | [我的.md](./我的.md) | TabBar |
| 主包 | `pages/profile/messages/index` | 消息中心 | — | 「我的」菜单（路由已注册，文档待补） |
| 主包 | `pages/activity/index` | 最新活动 | [最新活动.md](./最新活动.md) | 首页万能区域（最新活动单元格点击） |
| 主包 | `pages/about/index` | 关于我们 | [关于我们.md](./关于我们.md) | 「我的」菜单 |
| 主包 | `pages/webview/index` | 外部链接 | — | 活动详情 `web-view`（仅活动使用） |
| 详情分包 | `pages/pages-detail/moment` | 动态详情 | [动态详情.md](./动态详情.md) | 动态/评价卡片点击 |
| 详情分包 | `pages/pages-detail/review` | 发表评价 | [发布动态.md](./发布动态.md) | 菜品详情「写评价」 |
| 详情分包 | `pages/pages-detail/dish` | 菜品详情 | [菜品详情.md](./菜品详情.md) | 首页/搜索/动态卡片点击 |
| 详情分包 | `pages/pages-detail/review-list` | 全部评价 | [全部评价页.md](./全部评价页.md) | 菜品详情「查看全部评价」 |
| 用户分包 | `pages/pages-user/publish-moment/index` | 发布动态 | [发布动态.md](./发布动态.md) | 「我的」/ 动态页发布入口 |
| 用户分包 | `pages/pages-user/my-moments/index` | 我的动态 | [我发布的.md](./我发布的.md) | 「我的」菜单 |
| 用户分包 | `pages/pages-user/my-reviews/index` | 我的评价 | [我发布的.md](./我发布的.md) | 「我的」菜单 |
| 用户分包 | `pages/pages-user/profile-edit/index` | 个人信息 | [个人信息.md](./个人信息.md) | 「我的」→ 头像/昵称 |
| 用户分包 | `pages/pages-user/publish-dish` | 发布菜品 | — | 孤儿路由（`pages.json` 已注册，无入口，待清理） |
| 用户分包 | `pages/pages-user/submit-stall` | 提交档口 | — | 孤儿路由（`pages.json` 已注册，无入口，待清理） |

> 说明：`publish-dish` / `submit-stall` 在 `pages.json` 中仍注册，但无功能入口（对应「我要贡献」弹层未实现），属待清理残留，故无独立设计文档。

## 二、TabBar（固定 3 个）

- 首页（`pages/home/index`）
- 动态（`pages/community/index`）
- 我的（`pages/profile/index`）

搜索、意见反馈、活动、关于、消息中心等均为二级页，经 TabBar 页面内入口 `navigateTo` 进入。

## 三、文档覆盖

已撰写（13 篇）：
首页、搜索页、动态页、意见反馈、我的、菜品详情、动态详情、发布动态、全部评价页、最新活动、关于我们、个人信息、我发布的。

> 说明：**搜索页**文档包含「搜索前 / 搜索后」两个页面（共用顶部）；**发布动态**文档统一承载「发表评价」(`pages/pages-detail/review`) 与「发布动态」(`pages/pages-user/publish-moment/index`) 两种入口（同一 `PublishReview` 组件）；**我发布的**文档合并原 `my-moments` + `my-reviews` 两条路由。

待补/无文档：
- `pages/profile/messages`（消息中心）——路由已注册，无设计文档。
- `pages/webview`（外部链接）——仅活动 `web-view` 承载页，无需独立文档。
- `publish-dish` / `submit-stall` —— 孤儿路由，待清理。

## 四、设计原则与模式

- 统一模板见 [`TEMPLATE.md`](./TEMPLATE.md)：每篇含固定 Meta 块 + 页面结构总览 + `## 一/二/…` 区块 + 状态与交互细节 + 细节设计（尺寸与样式规范，置最后）+ 交互路径。
- 二级页统一 `Header` + 底部固定操作栏；列表页用卡片列表 + 骨架/空状态/分页。
- 评价即动态（单一内容模型），发布走统一 `PublishReview` 组件（见 `发布动态.md`）。
- 食堂/档口降级为菜品属性，无独立详情页（见 `project_spec.md` §2.1.4）。
- 颜色 / 尺寸一律用 token，禁止硬编码 hex；图标统一 `IconSvg`（见 spec §4.2）。
