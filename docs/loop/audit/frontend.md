# 前端排查报告（2026-09-14）

> 审计员：前端审计员（**只读**，未修改任何代码 / spec / tasks）
> 范围：小程序 `client/` + 管理后台 `web/`
> 方法：全仓 grep + 逐文件读证 + 与 `server/**` 控制器路由逐条 diff（**未跑构建**，构建由主 agent 执行）
> 基线：`docs/project_spec.md`（§2.1 页面表 / §3 契约 / §4.9 UI 红线 / §5 一致性红线）+ `.codebuddy/rules/client-components-org.md`
> 说明：文中「排除误报」项（注释、`.pressed`/非 icons 的 `name=`、`toAbsoluteImageUrl` 等）已逐条人工复核。

---

## 汇总

| 级别 | 数量 |
| --- | --- |
| 🔴 阻断 / 正确性缺陷 | 2 |
| 🟠 不一致或规范偏离 | 9 |
| 🔵 优化 | 5 |
| **合计** | **16** |

**结论：不能放行。** 存在 2 个 🔴（管理后台 401 兜底跳转指向已删除路由、web `adminId` 自保护链为死代码导致「禁止操作自己」永不生效），另有 9 个 🟠（含单页组件滞留公共层、死存储状态、内联裸 hex 等）。

---

## 一、接口契约（对照 spec §3 / §5，diff 后端 `@*Mapping`）

### 结论：前端调用的**全部**路径/方法在后端均可命中 —— ✅ 无「调用已下线接口」问题

逐条比对结果（前端 → 后端控制器）：

**client（14 个端点全中）**
| 前端调用 | 位置 | 后端 |
| --- | --- | --- |
| `POST /auth/email-code` | `client/src/api/user.ts:49` | `AuthController.java:62` ✅ |
| `POST /auth/wechat-login` | `client/src/api/user.ts:54` | `AuthController.java:94` ✅ |
| `POST /auth/verify-email` | `client/src/api/user.ts:63` | `AuthController.java:113` ✅ |
| `GET/PUT /auth/profile`、`DELETE /auth/account` | `client/src/api/user.ts:72,77,86` | `AuthController.java:124,141,183` ✅ |
| `GET /canteens`、`/canteens/all` | `client/src/api/canteen.ts:6,20` | `CanteenController.java:24,34` ✅ |
| `GET /categories` | `client/src/api/category.ts:15` | `CategoryController.java:38` ✅ |
| `GET /dishes/hot`、`/dishes`、`/dishes/{id}`、`/dishes/{id}/view`、`/dishes/new`、`/dishes/promotions`、`/dishes/rising`、`/dishes/hot-search`、`/dishes/recommend` | `client/src/api/dish.ts:67,73/99,110,121,128,132,137,142` + `recommend.ts:24` | `DishController.java:30,79,112,125,42,48,66,57,92` ✅ |
| `GET /reviews`（dishId/stallId/canteenId + sort）、`/reviews/{id}/useful`、`DELETE /reviews/{id}`、`GET /my/reviews`、`POST /reviews` | `client/src/api/review.ts:53,74,83,91,113` | `ReviewController.java:45,119,104,73,95` ✅ |
| `GET /my/notifications`、`/unread-count`、`PUT /{id}/read` | `client/src/api/notify.ts:53,61,70` | `NotificationController.java:36,49,58` ✅ |
| `POST /feedback` | `client/src/api/feedback.ts:10` | `FeedbackController.java:41` ✅ |
| `POST /upload/images` | `client/src/api/upload.ts:77` | `UploadController.java:62` ✅ |

**web（全部 /admin/** 命中）**
| 前端调用 | 位置 | 后端 |
| --- | --- | --- |
| `GET/POST /admin/dishes`、`PUT/DELETE /admin/dishes/{id}` | `web/src/api/dish.ts:15,26,30,34` | `DishAdminController.java:28,49,56,67` ✅ |
| `GET/POST /admin/categories`、`PUT /{id}`、`PUT /{id}/status`、`DELETE /{id}` | `web/src/api/category.ts:14,19,24,29,34` | `CategoryAdminController.java:37,44,51,62,73` ✅ |
| `GET/POST/PUT/DELETE /admin/canteens*`、`/admin/stalls*` | `web/src/api/canteen.ts`、`stall.ts` | `CanteenAdminController.java` ✅ |
| `GET /admin/audit`、`POST /{type}/{id}/approve|reject` | `web/src/api/audit.ts:11,17,22` | `AuditController.java:34,47,58` ✅ |
| `GET /admin/reviews`、`PUT /{id}/hide`、`PUT /{id}/sec-state`、`DELETE /{id}` | `web/src/api/audit.ts:30/47,59,64,69`；`review.ts:24,58,62,71` | `ReviewAdminController.java:27,69,57,81` ✅ |
| `GET /admin/feedbacks`、`PUT /{id}` | `web/src/api/feedback.ts:71,80` | `FeedbackAdminController.java:33,53` ✅ |
| `GET /admin/operation-logs` | `web/src/api/operationLog.ts:55` | `OperationLogAdminController.java:28` ✅ |
| `GET /admin/dashboard` | `web/src/api/dashboard.ts:62` | `DashboardController.java` ✅ |
| `GET/PUT /admin/users`、`PUT /{id}/status` | `web/src/api/user.ts:21,66` | `UserAdminController.java:18,44` ✅ |
| `GET/PUT /auth/profile`、`PUT /auth/password` | `web/src/api/user.ts:49,53,57` | `AuthController.java:124,141,158` ✅ |
| `POST /upload/image` | `web/src/api/upload.ts:27` | `UploadController.java:38` ✅ |

**字段名 / camelCase**：`secState`（`web/src/api/adapter.ts:193`、`client/src/api/review.ts:32`）、`isHidden`、`targetType/targetId`、`images` 命名与 spec §5.x 一致；web `snake_case` 仅出现在 `api/adapter.ts` 内部，未泄漏到 `types/` 或视图层 —— ✅。

**错误码**：client 仅分流 `401/403/4031/400/500`（`client/src/api/http.ts:202,222,228,235`），无非标码 —— ✅。

**分页**：两端均对后端 `{list,total}` 做兼容兜底（`client/src/api/shared.ts` `recordsOf`、`web/src/api/adapter.ts:6` `pageRecords`）。契约层后端仍是 `{list,total}`（后端审计报告 #4 已登记），前端兜底一致 —— 记 🔵（见下 #13）。

### 🟠 契约路径「幽灵目标」：`/dashboard/account`（web）未被任何路由注册

- 证据：`web/src/router/index.ts:13-28` 注册路由仅 `dashboard / content / audit / system / canteens/:id / …/stalls/:id / …/dishes/:id`，**无 `/dashboard/account`**。
- 调用方：
  - `web/src/views/layout/AdminLayout.vue:50` `goAccount()` → `router.push('/dashboard/account')`（用户菜单「账号设置」入口，`AdminLayout.vue:84`）。
  - `AdminLayout.vue:38-39` 还把 `/dashboard/account` 写进导航激活判断（死分支）。
- 影响：点击右上角用户菜单「账号设置」→ 路由未命中 → 内容区空白（无 catch-all 兜底）。学生账号管理实际在 `/dashboard/system?tab=account`（`SystemManageView.vue:22-33`）。
- 归属：Web管理后台开发工程师。修复建议：`goAccount()` 改 `router.push('/dashboard/system?tab=account')`，并清理 `:38-39` 激活判断与「账号设置页」相关注释；或补注册该路由并挂 `AccountView`。

---

## 二、死代码 / 不可达页面

### 🔴 #1 `web/src/main.ts` 401 兜底跳转指向已删除的 `/login`

- 证据：`web/src/main.ts:20-22`
  ```ts
  // P1-W1：401 统一引导登录（清 token + 跳 /login），对齐小程序 auth:unauthorized
  onUnauthorized(() => {
    if (router.currentRoute.value.path !== '/login') router.push('/login')
  })
  ```
- 事实：`web/src/router/index.ts:5-9` 注释明示「移除登录页、全局角色守卫与账号设置页」；全仓 grep `/login` 仅命中此文件与过期注释，**路由表无 `/login`**。
- 触发链路：`web/src/api/http.ts:68-70`（业务码 401 → `emitUnauthorized()`）、`upload.ts:37-39`（HTTP 401 → `emitUnauthorized()`）→ 均会 push `/login`。
- 影响：管理端任何 401 会把用户导到空路由（空白页），且原意「清 token + 引导登录」的管理端登录体系已不存在，属**功能性死链**。
- 归属：Web管理后台开发工程师。修复建议：删除该 `onUnauthorized` 订阅（管理端已改 `X-Admin-Token` 口令体系，401 语义为「口令无效」，应由 `http.ts` 提示文案承担），或改为不跳转仅提示。

### 🔴 #2 `web` 「禁止操作自己」自保护链为死代码，`adminId` 恒为 `null`

- 定义：`web/src/stores/userStore.ts:10` `const adminId = ref<number|null>(null)`；唯一赋值点在 `loadProfile()`（`userStore.ts:27-34`）。
- 事实：全仓 grep `loadProfile` → **仅定义处 `userStore.ts:27` 与 return 导出 `userStore.ts:48`，零调用方**（路由守卫已随登录体系移除）。
- 消费点：`web/src/views/user/UserView.vue:84`（单条切换自保护）、`:106`（批量自保护）均判 `userStore.adminId != null` → 恒为 `null` → 分支永不进入。
- 影响：`UserView` 的「不能操作当前登录的账号 / 避免批量封禁把自己踢下线」两道防护**实际失效**（当前因学生列表过滤 `role !== 'admin'`（`UserView.vue:41`）未酿成故障，但防护是死代码，语义已误导）。
- 归属：Web管理后台开发工程师（+ 技术负责人确认管理端是否还需「操作人身份」概念）。修复建议：二选一——(a) 若口令体系下无操作人身份，删除 `adminId`/`role`/`loadProfile`/`clearAuth` 与两处自保护分支（同步清理 `localStorage 'token'/'adminId'/'rememberedUsername'`）；(b) 若需保留，改为从口令体系注入固定操作人标识并在进入页时赋值。

### 🟠 #3 web 已下线的管理员体系三件套仍以「占位文件」驻留仓库

- `web/src/api/admin.ts`（全文 10 行，仅 `export {}` + 注释「待布局与页面收口后物理删除」）
- `web/src/stores/adminUserStore.ts`（全文 19 行，注释「待页面收口后随本文件一并物理删除」）
- `web/src/views/admin/AdminManageView.vue`（全文 14 行，仅 `<div />`，注释「待整体收口后物理删除」）
- 佐证引用：`web/src/api/index.ts:8` `export * as adminApi from './admin'`（仍导出空模块）；`AdminLayout.vue:8,15` 仍 `useAdminUserStore()` 并读 `myRole`（恒 `'admin'`，`adminUserStore.ts:15`）→ 顶部「管理员/超级管理员」文案由 `myRole === 'super_admin'`（`AdminLayout.vue:81`）判定，**恒为「管理员」**，`super_admin` 分支为死分支。
- 影响：死文件 + 假状态（`myRole` 硬编码）误导；与后端审计报告 #5/#6（`/auth/admin/login`、`/admin/admins*` 均不存在）呼应。
- 归属：Web管理后台开发工程师（清理）+ 技术负责人（确认无未决项）。建议：物理删除三文件与 `index.ts` 导出；`AdminLayout` 顶部固定文案，移除 `useAdminUserStore`。

### 🟠 #4 client `components/` 存在仅被单一页面包使用的组件（违反组件组织原则第 1 条）

- 规则：`.codebuddy/rules/client-components-org.md:7-9`「只允许被 ≥2 个页面包直接引用；仅被单一页面包使用的必须下沉」。
- 违规清单（已全仓 grep 确认引用方唯一）：
  | 组件 | 唯一引用方 | 建议下沉至 |
  | --- | --- | --- |
  | `client/src/components/ActionSheet.vue` | `client/src/pages/detail/dish/index.vue:108,130` | `pages/detail/dish/` |
  | `client/src/components/ReportModal.vue` | `client/src/pages/detail/dish/index.vue:97,129` | `pages/detail/dish/` |
  | `client/src/components/ListPickerSheet.vue` | `client/src/pages/me/feedback/index.vue:83,109,120,164` | `pages/me/feedback/` |
- 说明：`BaseSheet.vue`（被 ActionSheet/AuthSheet/ListPickerSheet/ReviewComposer 等 ≥2 包使用）、`AppButton`（me/ 下 2 页）、`TagLabel`（detail/ + home/）、`CardSection`（3 页）、`SectionTitle`（经 CardSection 间接多页）均**合规**，不计违规。
- 归属：小程序开发工程师。修复建议：按迁移门禁（同规则第 6 条）下沉三组件、同步 `./X` 引用与注释，迁移后 `type-check` + `mp-weixin` 构建全绿、旧路径零残留。

### 🟠 #5 client `composables/useReport.ts` 仅被单一页面编排引用（违反 composable 归属第 13 条）

- 规则：`.codebuddy/rules/client-components-org.md:37`「仅被单一页面引用的编排 composable 与私有子件同目录放页面包」。
- 证据：全仓 grep `useReport` → 唯一消费方 `client/src/pages/detail/dish/useDishPage.ts:29`；`client/src/composables/useReport.ts` 滞留全局 `composables/`。
- 对照：`useSheetFocus`（`composables/useSheetFocus.ts`）被 `components/BaseSheet.vue:51` 引用（公共壳，多页间接使用）→ **合规**；`useDishPage`/`useFeedback` 已在页面包内 → **合规**。
- 归属：小程序开发工程师。修复建议：`useReport.ts` 下沉至 `client/src/pages/detail/dish/`，改 `./useReport` 引用（`useReport` 也被 `ReportModal` 场景复用需一并核对）。

### 🟠 #6 client `stores/dish.ts` 导出大量零消费状态/方法（死 store）

- 位置：`client/src/stores/dish.ts:460-473`（return 导出）。
- 全仓 grep（排除该文件）**零外部引用**的成员：`recommendList`、`fetchRecommend`(定义 :116)、`guessList`、`fetchGuess`(定义 :126)、`newDishes`、`fetchNewDishes`(:234)、`promotionDishes`、`fetchPromotionDishes`(:246)、`risingDishes`、`fetchRising`(:269)、`stallDishes`、`fetchStallDishes`(:305)、`searchPage`(:151)、`fetchCategories`、`navParams`、`dishList`。
- 仅内部自用的占位也可以保留：`withLocalDistance`(:287)、`refreshLocalDistance`(:444)、`fetchHotSearch`(:259)、`resetUserScopedData`(:187) 等**有引用**，不计。
- 影响：`/dishes/new`、`/dishes/promotions`、`/dishes/rising`、`/dishes/hot`、`/dishes?stallId=`、`/dishes/recommend`（经 `fetchRecommend`）等一批 api 仅被死方法调用 → 形成「死 api + 死 store」链（与后端审计 #12「promotion 死查询」同源）。
- 归属：小程序开发工程师（清理）+ 产品经理/技术负责人（确认上新/促销/新晋黑马/猜你喜欢板块是否彻底不做）。修复建议：确认下线后删除死状态/方法，并同步删 api（`dish.ts:127,131,136,66,71`、`recommend.ts`）——**若保留则需接回页面消费，不得留悬空**。

### 🔵 #7 `client` 未使用 import

- `client/src/components/TabBar.vue:25` `import { onMounted } from 'vue'` —— 全文件仅此出现（`grep onMounted` 仅 1 行），**未使用**。
- 归属：小程序开发工程师。建议：删除该 import（`vue-tsc --noEmit` 若开 `noUnusedLocals` 会报错）。

### 🔵 #8 `client/src/api/notify.ts` 文档注释声明了不存在的接口

- 证据：`client/src/api/notify.ts:7` `* PUT /my/notifications/read-all    全部已读`；全仓 grep `read-all|readAll` → 前端**仅此注释**、后端**0 处**（`NotificationController` 仅 `GET /my/notifications`、`GET /unread-count`、`PUT /{id}/read`）。
- 影响：注释宣称的「全部已读」接口为幽灵，误导后续开发（无运行时影响，未被调用）。
- 归属：小程序开发工程师（或技术负责人若确需该能力则补契约）。建议：删除该行注释，或按需补后端 `read-all` + 前端调用。

---

## 三、命名与目录规范

### ✅ `pages.json` 与 spec §2.1 页面表一致（9 页）

- `client/src/pages.json:2-78`：主包 3（`pages/home/index`、`pages/find/index`、`pages/mine/index`）+ 分包 `pages/detail/`（1：`dish/index`）+ `pages/me/`（5：`notifications/profile/feedback/my-reviews/privacy`）= **9 页**，与 `project_spec.md:158-176` 逐条一致。
- TabBar 仅 2 页（`client/src/components/TabBar.vue:31-34` `home`/`profile`），符合 §2.1.4「TabBar 固定 2 页」。
- 路由集中注册表 `client/src/utils/routes.ts:11-24` 的 `PATH` 与 pages.json 完全对应；`feedbackEntryUrl`/`dishDetailUrl` 为唯一 URL 构造函数 —— ✅。

### ✅ import 路径风格合规

- 全仓 grep `"from '\.\./"` → **0 处**（跨层 `../` 逃逸红线通过）。
- `components/` 内 grep `@/components/` → **0 处**（无自引用）。
- 未发现 barrel/`index.ts` 重导出。

### 🔵 #9 web 视图命名语义漂移：`views/audit/ApplyFeedbackView.vue` 实为「反馈容器」

- 证据：`web/src/views/audit/ApplyFeedbackView.vue:6,12` 仅 `import FeedbackView from '@/views/admin/FeedbackView.vue'` 并 `<FeedbackView />`；文件注释亦写「反馈举报处理聚合（UGC 申请审核已随 apply 全链路下线）」。
- 影响：`Apply*` 前缀暗示「申请审核」，与实际职责（反馈）不符；`FeedbackView` 同时存在于 `views/admin/` 与 `views/audit/` 两个目录，易混淆。
- 归属：Web管理后台开发工程师。建议：重命名为 `FeedbackSection.vue` 或直接由 `AuditManageView.vue:13,72` 引用 `views/admin/FeedbackView.vue`，删除中转文件。

---

## 四、UI 红线（静态 grep，逐条复核误报）

### client 端 grep 结果

| 检查项 | 期望 | 实测 | 结论 |
| --- | --- | --- | --- |
| `@click` | 0 | 1 处，**为注释** `pages/home/DishCard.vue:59`（"父组件 @click 编译为原生 bindclick…"） | ✅ 通过（误报已排除） |
| 裸 `scale(` | 0 | 0 处（`grayscale(` 正则误命中已排除，无实际命中） | ✅ 通过 |
| 裸 hex（除 `App.vue`/`tokens.ts`） | 0 | 2 处：`pages/detail/dish/index.vue:215`（**注释**，非样式）、`pages/mine/index.vue:181`（**真实代码**，见 #10） | 🟠 见 #10 |
| emoji 充当图标 | 0 | 0 处（`⚠️`/`→` 命中均为 CSS/Script 注释文本，非模板图标） | ✅ 通过 |
| 图标统一 `<IconSvg>` | — | 全量字面量 `name=` 与 `ICONS` 键 diff：模板字面量 `arrow/arrow-left/canteen/check/clock/close/dish/empty/filter/heart(注释)/image/location/lock/more-v/price/report/search/search-fill/star/star-filled/thumb/user` **全部已注册**；动态键（`gridCells` 的 `report/bell/star`、`feedback` 的 `lightbulb-fill/dish-fill/report-fill`、`ErrorForm` 的 `price/edit/location/image/report/comment`、`useFeedback` 的 `canteen/stall/plus`、`TabBar` 的 `home/profile`）均命中 `IconSvg.vue:26-99` 的 `ICONS` | ✅ 通过 |
| 中性占位 `empty` vs `dish` | 中性用 `empty` | `ImagePicker.vue:29`/`ImageSwiper.vue:16`/`ReviewItem.vue:61`/`my-reviews:44`/`ImageFallback.vue:5` 均 `empty` ✅；`FindResults.vue:31`、`DishCard.vue:15` 为**菜品语义**占位用 `dish` ✅（符合 §4.9「仅组件语义明确为菜品才可用 dish」） | ✅ 通过 |
| `<template #card>` 向 WaterfallList 具名 slot | 0 | 0 处；`HomeContent.vue:19-29` 已内联瀑布流并直渲染 `DishCard`（`import DishCard from './DishCard.vue'`，`:60`） | ✅ 通过 |
| 固定底栏避让 | `.scroll-wrap` 含 `padding-bottom: calc(var(--action-bar-height)+env(...))` | `pages/me/profile/index.vue:139` 含 `--action-bar-height` ✅；`pages/detail/dish/index.vue:183` 页面容器含 `--action-bar-height` ✅（该页走自然滚动，非 scroll-wrap，等效避让） | ✅ 通过 |
| composable 生命周期在函数体内 | 顶层 0 | `useDishPage.ts:131,135,138,195,208,253` 与 `useFeedback.ts:119,581` 全部位于 `export function useXxx()`（`useDishPage.ts:36` / `useFeedback.ts:27`）**函数体内**，页面 setup 同步调用 | ✅ 通过 |

### 🟠 #10 client 内联裸 hex 未走注册常量（`uni.showModal.confirmColor`）

- 证据：`client/src/pages/mine/index.vue:181` `confirmColor: '#C45549',`
- 对照合规实现：同语义另有 3 处均引用注册常量 `MODAL_CONFIRM_DANGER_COLOR`（`pages/detail/dish/useDishPage.ts:278`、`pages/find/index.vue:190`、`pages/me/my-reviews/index.vue:191`，常量定义于 `theme/tokens.ts:68`）。
- 规则：`project_spec.md:279`「原生 API 不接受 var() 的颜色例外必须集中在 `theme/tokens.ts` 注释登记…**禁止在页面内联写裸 hex**——业务代码一律引用注册常量」。
- 影响：① 违反裸 hex 红线；② 同一「showModal 确认色」语义双口径（3 处走 `#FF3B30` 危险色 token，1 处硬编码 `#C45549` 主色）——语义本身也需设计师裁决（注销属破坏性操作，是否应与其它确认统一为危险色）。
- 归属：小程序开发工程师（代码）；确认色语义若涉取舍交 UI-UX 设计师。建议：`theme/tokens.ts` 增登记（如 `MODAL_CONFIRM_PRIMARY_COLOR`）并在 `mine/index.vue` 引用；或与设计师统一为危险色 token。

### web 端组件复用 —— ✅ 通过

- 抽查 `DataTable`（`ApplyReviewView.vue:197`、`FeedbackView.vue:212`、`OperationLogView.vue:128`、`CanteenDetailView.vue:338,384`、`CanteensView.vue:182`、`DishDetailView.vue:470`、`DishManageView.vue:178`、`StallDetailView.vue:392`、`CategoryManage.vue:128`、`UserView.vue:156`，**10+ 处复用**）、`FormDialog`、`ConfirmDialog`（`AdminLayout.vue:56`）、`StatusTag`（10+ 处）、`ImageUpload`（5 处）——均为自封装组件，无 `<el-table>`/`<el-dialog>` 重复造轮子。
- 搜索框统一 `el-input`（spec §4.2「`SearchInput` 已删除，统一用 `el-input`」）：全仓 grep `SearchInput` → 0 处 ✅。
- 设计 Token：`web/src/styles/variables.css` 真实定义并被 `AdminLayout.vue:105-127` 等引用，未发现硬编码默认色（抽查 `--bg-page/--nav-bg/--press-scale`），`scale(var(--press-scale))` 为 spec §4.9 登记豁免 ✅。

---

## 五、逻辑不一致（同一规则多口径）

### 🔵 #11 图片 URL 拼接：client 统一 `getImageUrl`，web 统一 `toAbsoluteImageUrl` —— ✅ 无重复实现

- client：全部展示点走 `utils/image.ts` 的 `getImageUrl`/`getThumbUrl`（`shared.ts:64,70,72` 在 api 层统一归一化；`DishCard.vue:66`、`FindResults.vue:119`、`ReviewItem.vue:88` 等消费），**无第二套拼接** ✅。
- web：仅 `api/adapter.ts:69` `toAbsoluteImageUrl`（`ImageUpload.vue:32` 复用），无 `getImageUrl` 私有副本 ✅。

### 🔵 #12 金额分↔元：两端均在 api 层统一转换 —— ✅ 通过

- client：`utils/money.ts` 的 `fenToYuan/yuanToFen`，消费于 `api/dish.ts:24,49,50,94,95`；模板展示 `price`（已为元）不裸算 ✅。
- web：`api/adapter.ts:144,160,163,173,184` 统一 `/100`、`*100`，View 层不处理 ✅。
- 与后端审计报告「金额一律分」结论一致。

### 🔵 #13 状态值域映射（active/inactive ↔ open/closed / on/off）—— ✅ 通过

- web `api/adapter.ts:86,99`（canteen open/closed）、`:113,130`（stall）、`:151,178`（dish on/off）+ `user` status `active/disabled`（`api/user.ts:65-66`），映射集中在 `adapter.ts`，未泄漏到视图层 ✅（符合 spec §5.x「Web 内部 active/inactive 须经 adapter 映射回后端枚举」）。

### 🔵 #14 审核/隐藏态展示：`secState` 元数据已集中，无多口径

- web `constants/index.ts:29-42` 的 `SEC_STATE_META`/`SEC_FILTER_OPTIONS` 被 `ApplyReviewView.vue:229,281`、`FeedbackView.vue:252,319`、`DishDetailView.vue:494,542`、`UserActivityModal.vue` 共用，**无本地重复定义**（grep 本地 `正常/待复核/已驳回` 文案 → 0 处）✅。
- client `api/review.ts:32` 将 `secState` 收敛为 `'review'|'pass'`（对非作者不可见由后端过滤，前端不兜底）✅；`my-reviews` 与 `notifications` 的游客静默口径（`notifications/index.vue:30,43,93-96`）符合 `client-auth-boundary` ✅。

### 🔵 #15 反馈状态映射局部硬编码（轻微）

- `web/src/views/admin/FeedbackView.vue:28-29` 本地定义 `statusTag`/`statusText`（`pending/handled`），与其它业务常量（`constants/index.ts`）未并列；单一消费方，风险低。
- 归属：Web管理后台开发工程师。建议：如需统一，迁入 `constants/index.ts`。

---

## 问题清单

| # | 级别 | 端 | 问题 | 位置(file:line) | 影响 | 建议归属角色 | 修复建议 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 🔴 | web | 401 兜底跳转指向已删除的 `/login` 路由（无 catch-all） | `web/src/main.ts:20-22`；路由表 `web/src/router/index.ts:13-28` | 任何 401 → 空白页；与已改口令体系语义冲突 | Web管理后台开发工程师 | 删除该 `onUnauthorized` 订阅（或改为仅提示），清理「跳 /login」注释；`upload.ts:13`「清 token + 跳登录」注释同步修正 |
| 2 | 🔴 | web | `userStore.adminId` 唯一赋值点 `loadProfile()` 零调用 → `UserView` 自保护分支恒不生效 | 定义 `web/src/stores/userStore.ts:10,27-34`；消费 `web/src/views/user/UserView.vue:84,106` | 「不能操作当前账号 / 批量封禁自保护」失效（当前被 role 过滤掩盖） | Web管理后台开发工程师 + 技术负责人 | 确认管理端是否需操作人身份：无则删 `adminId/role/loadProfile/clearAuth` 与两处分支；有则按口令体系注入并赋值 |
| 3 | 🟠 | web | 已下线管理员体系三件套仍驻留（占位文件） | `web/src/api/admin.ts`；`web/src/stores/adminUserStore.ts`；`web/src/views/admin/AdminManageView.vue`；导出 `web/src/api/index.ts:8` | 死文件 + `myRole` 假状态（`AdminLayout.vue:81` super_admin 分支死） | Web管理后台开发工程师 | 物理删除三文件与 index 导出；`AdminLayout` 移除 `useAdminUserStore`，文案定值 |
| 4 | 🟠 | client | `components/` 内 3 组件仅被单一页面包引用（组件组织红线） | `components/ActionSheet.vue`（唯一 `pages/detail/dish/index.vue:108`）；`components/ReportModal.vue`（同上 `:97`）；`components/ListPickerSheet.vue`（唯一 `pages/me/feedback/index.vue:83`） | 公共层被单页组件污染，违反 org 规则第 1/4 条 | 小程序开发工程师 | 按迁移门禁下沉至对应页面包，同步 `./X` 引用与注释，构建全绿、旧路径零残留 |
| 5 | 🟠 | client | `composables/useReport.ts` 仅被单一页面编排引用 | `client/src/composables/useReport.ts`；唯一消费 `pages/detail/dish/useDishPage.ts:29` | 违反 composable 归属第 13 条（应下沉页面包） | 小程序开发工程师 | 下沉至 `pages/detail/dish/`，改 `./useReport` |
| 6 | 🟠 | client | `stores/dish.ts` 大量零消费状态/方法（死 store + 死 api 链） | `stores/dish.ts:460-473` 导出；死成员 `recommendList/fetchRecommend/guessList/fetchGuess/newDishes/fetchNewDishes/promotionDishes/fetchPromotionDishes/risingDishes/fetchRising/stallDishes/fetchStallDishes/searchPage/fetchCategories/navParams/dishList` | 死代码；连带 `/dishes/new|promotions|rising|hot|recommend`、`?stallId=` 成死 api | 小程序开发工程师 + 产品经理/技术负责人 | 确认板块下线后删死状态/方法与对应 api；若要保留须接回页面消费 |
| 7 | 🟠 | web | `/dashboard/account` 未被任何路由注册，用户菜单「账号设置」点击空白 | `web/src/views/layout/AdminLayout.vue:50`（+`:38-39,84`）；`router/index.ts:13-28` | 导航死链，功能不可达 | Web管理后台开发工程师 | 改跳 `/dashboard/system?tab=account`；清理死激活分支与「账号设置页」注释 |
| 8 | 🟠 | client | `uni.showModal` 的 `confirmColor` 内联裸 hex，未走注册常量，且与同类 3 处口径不一 | `pages/mine/index.vue:181`（`'#C45549'`）vs `theme/tokens.ts:68`；合规同类 `useDishPage.ts:278`/`find/index.vue:190`/`my-reviews/index.vue:191` | 违反 §4.9 裸 hex 红线；破坏性操作确认色语义双口径 | 小程序开发工程师（语义交 UI-UX 设计师） | `tokens.ts` 登记常量并引用；或与设计师统一为危险色 token |
| 9 | 🟠 | client | `api/notify.ts` doc 注释声明不存在的 `PUT /my/notifications/read-all` | `client/src/api/notify.ts:7`（后端 0 处实现） | 幽灵接口误导开发（未调用，无运行时影响） | 小程序开发工程师 | 删注释，或补后端接口 + 前端调用 |
| 10 | 🔵 | client | 未使用 import `onMounted` | `client/src/components/TabBar.vue:25` | 死 import（lint/`noUnusedLocals` 风险） | 小程序开发工程师 | 删除该 import |
| 11 | 🔵 | web | `ApplyFeedbackView.vue` 命名与职责不符（实为反馈容器中转） | `web/src/views/audit/ApplyFeedbackView.vue:6,12`；消费 `views/audit/AuditManageView.vue:13,72` | 命名误导；`FeedbackView` 跨两目录 | Web管理后台开发工程师 | 重命名或直连 `views/admin/FeedbackView.vue`，删中转文件 |
| 12 | 🔵 | client | 分页字段兼容兜底（后端恒 `{list,total}`）散在两个工具 | `client/src/api/shared.ts` `recordsOf`；`web/src/api/adapter.ts:6` `pageRecords` | 契约层脱钩的后端问题由前端兜底（后端审计 #4 已登记） | 技术负责人（裁决）/ 后端（对齐 spec） | 由技术负责人裁决后端 `PageResult` 补 `records/page/pageSize` 后，前端可收敛兜底 |
| 13 | 🔵 | web | 反馈 `pending/handled` 状态映射本地硬编码 | `web/src/views/admin/FeedbackView.vue:28-29` | 业务常量未集中（单一消费方，风险低） | Web管理后台开发工程师 | 迁入 `constants/index.ts` 统一管理 |
| 14 | 🔵 | web | `stores/userStore.ts` 遗留登录态字段（`adminId/role`、`localStorage token/rememberedUsername`） | `web/src/stores/userStore.ts:9-12,27-44` | 登录体系已移除，遗留字段误导 | Web管理后台开发工程师 | 随 #2 一并清理 |
| 15 | 🔵 | web | `api/upload.ts:13` 注释「清 token + 跳登录」已过期（无登录页） | `web/src/api/upload.ts:13` | 过期注释 | Web管理后台开发工程师 | 修正注释（口令体系语义） |
| 16 | 🔵 | client | `notify`「游客静默」与「入口直达」已合规，但 `read-all`/未读数无批量已读能力 | `notifications/index.vue:132-145`（仅单条已读） | 体验缺口（非缺陷） | 产品经理（确认是否需要） | 若需要「一键已读」，后端补 `read-all` + 前端接入 |

---

## 待确认项

| # | 待确认内容 | 所需手段 | 关联问题 |
| --- | --- | --- | --- |
| A | 管理端口令体系下是否还需「操作人身份」（决定 #2 是删字段还是补赋值） | 技术负责人拍板；若保留需后端在口令校验处注入固定操作人标识 | #2 #14 |
| B | `/dashboard/account` 是「应删入口」还是「应补路由挂 AccountView」 | 产品经理 + Web 开发确认用户菜单「账号设置」的产品定位 | #7 |
| C | 注销确认色语义：应与其它确认统一为危险色 `#FF3B30`，还是保留主色 `#C45549` | UI-UX 设计师裁决 | #8 |
| D | 上新/促销/新晋黑马/猜你喜欢/档口菜品 板块是否彻底下线（决定 #6 死 store 与死 api 是否可直接删） | 产品经理确认（后端审计 #12 亦待确认 `promotion` 入口去留） | #6 |
| E | `ActionSheet`/`ReportModal`/`ListPickerSheet` 下沉是否会破坏既有行为（是否有隐藏动态引用） | 下沉后跑 `npm run type-check` + `npm run build:mp-weixin` 验证（由主 agent 执行） | #4 |
| F | 前端 `recordsOf`/`pageRecords` 兜底是否需在后端补齐 `PageResult.records/page/pageSize` 后收敛 | 技术负责人裁决后端契约 | #12 |

---

## 已通过项（放行留档）

- ✅ **接口契约**：client 14 端点 + web 全部 `/admin/**` 调用均可命中后端实际路由（逐条 diff，无 `/admin/admins*`、`/auth/admin/login` 等已下线调用）
- ✅ **错误码**：前端仅分流 `400/401/403/4031/500`，无非标码；client `http.ts:222` 对 4031 与 403 分流正确
- ✅ **camelCase / 字段命名**：`secState`/`isHidden`/`targetType`/`images` 合规；web `snake_case` 未出 `adapter.ts`
- ✅ **pages.json 与 spec §2.1 页面表一致**（9 页 = 主包 3 + detail/ 1 + me/ 5），TabBar 仅 home/mine
- ✅ **import 路径**：`../` 跨层逃逸 0 处；`components/` 内 `@/components/` 自引用 0 处；无 barrel
- ✅ **@click 0 处**（唯一命中为注释）；**裸 `scale(` 0 处**（`grayscale(` 误报已排除）
- ✅ **emoji 充当图标 0 处**（命中项均为注释文本）；图标全部经 `<IconSvg>` 且字面量键全注册；中性占位 `empty` / 菜品占位 `dish` 边界正确
- ✅ **WaterfallList 具名 slot 禁令**：0 处具名 slot；`HomeContent` 直渲染 `DishCard`
- ✅ **固定底栏避让**：profile（scroll-wrap）与 dish detail（页面容器）均含 `--action-bar-height`
- ✅ **composable 生命周期**：`useDishPage`/`useFeedback` 全部生命周期在函数体内、页面同步调用（无顶层调用）
- ✅ **web 组件复用**：`DataTable/FormDialog/ConfirmDialog/StatusTag/ImageUpload` 全面复用，无 el-table/el-dialog 重复造轮子；无 `SearchInput` 残留（统一 `el-input`）
- ✅ **图片 URL 拼接统一**（client `getImageUrl` / web `toAbsoluteImageUrl`，无第二套实现）
- ✅ **金额分↔元统一在 api 层**（`utils/money` / `adapter.ts`），视图层无裸算
- ✅ **状态值域映射集中于 adapter**（open/closed、on/off、active/inactive）
- ✅ **收藏 / STALL_OWNER 残留 0 处**；`userToLegacy` 已无 `stall_id` 映射
- ✅ **安检态展示**：web `SEC_STATE_META` 集中、client `secState` 收敛为 review/pass，游客静默口径合规

---

## 备注

- 本报告仅为**排查结论**，未修改任何代码、spec 或 tasks；**未执行任何构建命令**（由主 agent 统一执行）。
- 所有 🔴/🟠 项均给出 `file:line` 证据；grep 误报（`grayscale(`、注释内 `@click`/`scale`/裸 hex、slot 名 `extra`、注释内 `heart`）已逐条排除并写明。
- 报告落盘路径：`docs/loop/audit/frontend.md`
