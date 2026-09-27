# UI 设计稿总览（按页面拆分）

> 本目录是「**页面 UI 设计稿**」的唯一真源：每个页面（或页内 UI 承载物）一份独立文档，文件名以板块前缀开头，与 [`docs/feature/`](../feature/) **同名一一对应**（例外登记见文末）。
> **口径归属**：页面 UI（布局 / 视觉 / 交互 / 三态 / 控件）以本目录为准；功能流程 / 接口 / 字段 / 数据口径以 [`docs/feature/`](../feature/) 为准；跨端视觉规范与红线以 [`docs/project_spec.md`](../project_spec.md) §4 为准。
> **本目录只写「当前口径」**：逐轮变更过程 / 被推翻的方案 / 失败尝试一律归口 [`docs/UI_FRONTEND_LOOP.md`](../UI_FRONTEND_LOOP.md)（UI 统一 loop 的流程真源 + 轮次记录）。

## 板块与命名规则

| 文件名前缀 | 板块 | 代码目录 | 说明 |
|---|---|---|---|
| `client-` | **学生端（微信小程序）** | `client/` | 18 份页面 / 页内承载物设计稿 —— UI 统一 loop（R1–R29）覆盖范围 |
| `web-` | **管理端（Web 后台）** | `web/` | 7 份，**当前为要点级骨架，尚未细化**（该 loop 范围仅 `client/src`） |

> 文档固定结构：**页面设计 → 组件清单 → 数据映射（显示在哪个组件）→ 出参 / 入参 / 错误码 → 控件类型**（末段供「UI 精修」逐项核对）。

## 修正状态标记

| 标记 | 含义 |
|---|---|
| ✅ **已完成** | 该设计稿**已随 UI 统一 loop 修订并对齐当前口径**（括号内为最近修订轮次），文档与实现一致 |
| ⚠️ **待重设计** | 已登记待办（需 UI/UX 出稿或用户拍板），文档暂记录现状 |
| ▫️ **未细化** | 要点级 / 骨架：有落点与页面要点，**无交互细节 / 三态 / 组件清单 / 数据映射**，尚未纳入修订 |
| ⛔ **已下线** | 功能已全链下线，文档仅作历史留痕（**不得据以实现**） |

> 本列只反映**设计稿修订状态**，不代表代码落地状态（代码侧以 `vue-tsc` / 构建产物 / 真机为准）。
> 轮次定义见 [UI_FRONTEND_LOOP.md](../UI_FRONTEND_LOOP.md) §4（**范围 = `client/src`**，不含 `server/`、`web/`）。

---

## client- · 学生端（微信小程序，18 份）

| 编号 | 页面 / 承载物 | UI 设计稿 | 代码落点 | 归属功能文档 | 修正状态（最近轮次） |
|---|---|---|---|---|---|
| A-01 | 微信静默登录 / 游客态（全局态，无独立页面） | [client-微信静默登录与游客态.md](./client-微信静默登录与游客态.md) | `stores/user.ts`、`api/http.ts`（401 静默重试） | [client-微信静默登录与游客态.md](../feature/client-微信静默登录与游客态.md) | ✅ 已完成（R17） |
| A-02 | 首页菜品浏览（**全站基准稿**） | [client-首页菜品浏览.md](./client-首页菜品浏览.md) | `pages/home/index.vue` | [client-首页菜品浏览.md](../feature/client-首页菜品浏览.md) | ✅ 已完成（R29 重写 · R28 重构） |
| A-03 | 搜索 | [client-搜索.md](./client-搜索.md) | `pages/find/index.vue`、`pages/find/DishResultCard.vue` | [client-搜索.md](../feature/client-搜索.md) | ✅ 已完成（R18·R20·R21·R26–R27c·R28） |
| A-04 | 菜品详情 | [client-菜品详情.md](./client-菜品详情.md) | `pages/detail/dish/index.vue` | [client-菜品详情.md](../feature/client-菜品详情.md) | ✅ 已完成（R19·R22–R25） |
| A-05 | 浏览计数（无独立界面） | [client-浏览计数.md](./client-浏览计数.md) | 详情页进页上报 | [client-浏览计数.md](../feature/client-浏览计数.md) | ✅ 已完成（随 A-04） |
| A-06 | 写评价 / 重新评价（详情页底部抽屉） | [client-写评价.md](./client-写评价.md) | `pages/detail/dish/ReviewComposer.vue` | [client-写评价.md](../feature/client-写评价.md) | ✅ 已完成（R13·R23·R25） |
| A-07 | 评价「有用」 | [client-评价有用.md](./client-评价有用.md) | **无**（能力与组件均已删除） | [client-评价有用.md](../feature/client-评价有用.md) | ⛔ 已下线（2026-09-20 全链下线，留痕） |
| A-08 | 删除本人评价（详情页 ActionSheet） | [client-删除本人评价.md](./client-删除本人评价.md) | `pages/detail/dish/index.vue`（`ActionSheet`） | [client-删除本人评价.md](../feature/client-删除本人评价.md) | ✅ 已完成（R17 死事件链清理） |
| A-09 | 举报评价（详情页弹层） | [client-举报评价.md](./client-举报评价.md) | `pages/detail/dish/ReportModal.vue` | [client-举报评价.md](../feature/client-举报评价.md) | ✅ 已完成（R7·R13） |
| A-10 | 我的评价（承载于「我的主页」） | [client-我的主页.md](./client-我的主页.md) | `pages/my-reviews/index.vue` | [client-我的评价.md](../feature/client-我的评价.md) | ✅ 已完成（R12·R14·R17·R24） |
| A-11 | 意见反馈 | [client-意见反馈.md](./client-意见反馈.md) | `pages/feedback/index.vue` | [client-意见反馈.md](../feature/client-意见反馈.md) | ✅ 已完成（R1–R3·R13·R17·R25） |
| A-12 | 系统通知（处理回执） | [client-系统通知.md](./client-系统通知.md) | `pages/notifications/index.vue` | [client-系统通知.md](../feature/client-系统通知.md) | ✅ 已完成（R1–R3·R12·R13·R17） |
| A-13 | 个人资料（查看于我的主页 / 编辑于个人信息编辑页） | [client-个人信息编辑.md](./client-个人信息编辑.md) | `pages/profile/index.vue` | [client-个人资料.md](../feature/client-个人资料.md) | ✅ 已完成（R1·R6·R12·R14·R17） |
| A-14 | 邮箱认证 | [client-邮箱认证.md](./client-邮箱认证.md) | `pages/auth/index.vue` | [client-邮箱认证.md](../feature/client-邮箱认证.md) | ✅ 已完成（R1·R12·R17·R26） |
| A-15 | 注销账号（入口在「我的」页） | [client-注销账号.md](./client-注销账号.md) | `pages/mine/index.vue`（危险弱化行 + `showModal`） | [client-注销账号.md](../feature/client-注销账号.md) | ⚠️ **待重设计**（功能总览待办 #5） |
| A-16 | 隐私政策 / 用户协议 | [client-隐私政策.md](./client-隐私政策.md) · [client-用户协议.md](./client-用户协议.md) | `pages/privacy/{index,agreement}.vue`、`pages/privacy/DocPage.vue` | [client-隐私政策与用户协议.md](../feature/client-隐私政策与用户协议.md) | ✅ 已完成（R4 抽 `DocPage`） |
| — | 我的页（TabBar 主页，非功能页） | [client-我的页.md](./client-我的页.md) | `pages/mine/index.vue` | —（导航壳 + 入口聚合） | ✅ 已完成（R1·R5·R12·R17） |

> `client-首页菜品预览.png` 为首页设计参考图（静态资源，非文档）。

## web- · 管理端（Web 后台，7 份）

| 编号 | 页面 / 承载物 | UI 设计稿 | 代码落点 | 归属功能文档 | 修正状态 |
|---|---|---|---|---|---|
| B-01 | 菜品管理（后台默认落地页 `/dashboard/content`） | [web-菜品管理.md](./web-菜品管理.md) | `web/src/views/content/DishManageView.vue` | [web-菜品管理.md](../feature/web-菜品管理.md) | ▫️ 未细化 |
| B-02 | 菜品详情查看 | [web-菜品详情查看.md](./web-菜品详情查看.md) | `web/src/views/content/DishDetailView.vue` | [web-菜品详情查看.md](../feature/web-菜品详情查看.md) | ▫️ 未细化 |
| B-03 | 评价管理（事后处置） | [web-评价管理.md](./web-评价管理.md) | `web/src/views/audit/ReviewManageView.vue` | [web-评价管理.md](../feature/web-评价管理.md) | ▫️ 未细化 |
| B-04 | 反馈处理 | [web-反馈处理.md](./web-反馈处理.md) | `web/src/views/audit/FeedbackView.vue` | [web-反馈处理.md](../feature/web-反馈处理.md) | ▫️ 未细化 |
| B-05 | 学生账号管理 | [web-学生账号管理.md](./web-学生账号管理.md) | `web/src/views/system/UserView.vue` | [web-学生账号管理.md](../feature/web-学生账号管理.md) | ▫️ 未细化 |
| B-06 | 图片上传（表单内，无独立页面） | [web-图片上传.md](./web-图片上传.md) | 各表单弹窗内 | [web-图片上传.md](../feature/web-图片上传.md) | ▫️ 未细化 |
| B-07 | 信息纠错处理 | [web-信息纠错.md](./web-信息纠错.md) | `web/src/views/audit/CorrectionView.vue` | [web-信息纠错.md](../feature/web-信息纠错.md) | ▫️ 未细化 |

> **web 端未细化的统一口径**：管理后台 7 份设计稿目前只有「落点 + 页面要点」，缺少管理端 UI 规范（表格 / 表单 / 弹窗 / 反馈）与三态定义 —— 细化前，**管理端视觉与交互无唯一真源**。

---

## 与功能文档的对应关系（例外登记）

绝大多数为 **1:1 同名对应**（`docs/ui/X.md` ↔ `docs/feature/X.md`），以下为例外：

| UI 设计稿 | 归属功能文档 | 关系 |
|---|---|---|
| `client-隐私政策.md` · `client-用户协议.md` | `client-隐私政策与用户协议.md` | **1 功能 : 2 页面**（两页共用 `DocPage` 外壳） |
| `client-个人信息编辑.md` | `client-个人资料.md` | 同一功能；**页面名与功能名不同**（个人资料的「查看」在「我的主页」信息卡） |
| `client-我的主页.md` | `client-我的评价.md`（+ `client-个人资料.md` 的查看段） | 功能「我的评价」**承载于**「我的主页」评价区 |
| `client-我的页.md` | — | TabBar 主页，**无独立功能文档**（导航壳 + 入口聚合） |
| `client-评价有用.md` | `client-评价有用.md` | 功能已下线（双方均留痕）；该功能**未出现在 `docs/feature/README.md` 索引表**（该表 A-06 后直达 A-08） |

## 待办 / 未决清单

| # | 事项 | 现状 | 影响面 |
|---|---|---|---|
| 1 | **注销入口 UI 重设计** | 已登记（`docs/feature/README.md` 待办 #5）；需 UI/UX 出稿，**不动接口** | 「我的」页内布局（`client-注销账号.md` / `client-我的页.md`） |
| 2 | **web- 7 份设计稿细化** | 要点级骨架（无交互 / 三态 / 组件清单 / 数据映射） | 管理后台 7 个页面（见上表 ▫️ 行） |
| 3 | `client-评价有用.md` 未进功能索引 | 待核（该表 A-07 缺号，可能因「已下线」有意省略） | 索引一致性（`docs/feature/README.md`） |
| 4 | loop 候选池中的 UI 待拍板项 | 待拍板：白卡壳收敛范围 / 圆角裸值定档 / 阴影三选一 / 通知未读双指示器 / `AppTitleBand` 与 `AppHeader` 是否合并 | 见 [UI_FRONTEND_LOOP.md](../UI_FRONTEND_LOOP.md) §5 |

## 与其它文档的关系

- [`docs/feature/`](../feature/)：功能 / 接口 / 字段 / 数据（其每份文档的 `## UI` 段是指向本目录的**指针**）。
- [`docs/UI_FRONTEND_LOOP.md`](../UI_FRONTEND_LOOP.md)：UI 统一 loop 的流程真源与**轮次记录**（逐轮变更归此处，本目录只留当前口径）。
- [`docs/project_spec.md`](../project_spec.md) §4：跨端 UI 视觉规范（红线）。
- `CODEBUDDY.md`：仓库约定与红线。
