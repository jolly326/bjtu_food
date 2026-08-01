# 食在交大 Web 管理后台 UI 设计方案（定样稿）

Web 管理后台（`web/`，Vue3 + Vite + TS + Element Plus + ECharts，无 Pinia）全部页面的 UI 设计方案。本文件已与 UI-UX 设计师**逐页讨论定样**，作为 Web 端整改依据。

- 配套小程序端定稿：`docs/mini-app-ui.md`
- 执行依据（定样收口后建）：`tasks/task-1x-web-ui-design.md`（逐页整改清单 + 验收门禁）
- 最高红线：`project_spec.md` §4；Web 端额外遵循本文件 §0 规范。
- 视觉规范依据：Apple Design（克制 / 材质层级 / 即时反馈 / 弹簧动效）+ ui-ux-pro-max（优先级 4「SVG icons (no emoji)」、优先级 1「对比度 4.5:1 / 键盘可达 / aria」、优先级 2「44×44 触控 / 加载反馈」、优先级 5「响应式无横向滚动」、优先级 7「动效 150–300ms + reduced-motion」、优先级 10「图表须有图例/提示」）。

> ✅ 本文档为**定样稿**：§2 起各页均已给出明确「定位 / 信息架构 / 主面板布局 / 组件 / 视觉规范要点」，Web 开发工程师据此实现，无需再逐页确认。唯一的待裁定项集中在文末 §八。

---

## 0. 设计基线（Web 端）

### 0.1 技术栈与既有约束
- Vue 3 `<script setup>` + TS + Vite；**状态管理：无 Pinia**（与 `project_spec.md` §1「Web 管理端 … 无 Pinia」一致；页面级状态用 `ref`/`reactive` 或 `pageStore` 等轻量 store，禁止引入 Pinia）。路由 Vue Router（后台为左侧固定导航 + 内容区布局）。
- UI 组件库 **Element Plus**（表格 / 表单 / 弹窗 / 消息 / 上传等优先用其组件，保持后台一致性）；图表 **ECharts**。
- 后台为**内部工具型 dashboard**：ui-ux-pro-max 建议 `density=高`（8–32px 间距尺度），信息密度优先、克制装饰。
- 金额分↔元统一在 api 层（`fenToYuan`/`yuanToFen`），页面禁裸算（与小程序端同红线，§3）。

### 0.2 布局骨架（已定样）
统一外壳 `web/src/views/layout/AdminLayout.vue`：

- **左侧固定导航侧栏**（220px，深红 `#6B1010` 底，见 §4.2）：品牌区（「食在交大管理系统」）+ 三组菜单（概览 / 内容 / 运营），详见 §2.1。
- **顶部操作条**（半透材质 `blur(20px)`，60px）：左侧折叠按钮（`<960px` 时切浮层抽屉 + scrim）、右侧面包屑（来自 `pageStore.breadcrumbs`，最后一级加粗，可点击回跳）。
- **右侧内容工作区** `<main class="content">`：路由出口 `<router-view>`；所有页面统一经 `PageContainer` 包裹（见 §七），自带「页面标题 + 操作区」与三态（加载骨架 / 空态 / 正常）。
- **折叠**：`>=960px` 常驻、`<960px` 移出视口（`translateX(-100%)`）以浮层 + scrim 弹出；过渡 0.3s，尊重 `prefers-reduced-motion`。
- **暗色主题 / 多页签工作区**：本期不做（待定，见 §八）。

### 0.3 全局视觉 Token（可核对清单，单位 px；与 project_spec §4.2 对齐）

> 小程序端用 rpx、Web 用 px，本端一律 px。以下为 QA 可直接核对项。

| Token | 值 | 用途 / 备注 |
|---|---|---|
| 品牌主色（深红） | `#6B1010` | 侧栏底、品牌区、关键强调（与小程序端同品牌色） |
| `--radius-card` | `16px` | 卡片圆角（§4.2 卡片 16px，Web 直接用 px） |
| 按钮圆角 | `8px` | Element Plus 默认按钮风格 |
| 弹窗/抽屉圆角 | `12px` | `el-dialog`/`el-drawer` |
| 弹层阴影 | `0 -8px 30px rgba(0,0,0,0.12)` | 抽屉/浮层（§4.2）；Web 用 `el-overlay` 阴影 |
| 半透材质 | `backdrop-filter: blur(20px) saturate(180%)` | 顶部操作条/侧栏浮层（§4.2/§4.5）；`prefers-reduced-transparency` 降级纯色 |
| 按下/悬停反馈 | Element Plus 默认 hover；关键操作即时反馈 | Apple Design §1 响应即反馈 |
| 间距尺度（高密度） | `8 / 12 / 16 / 24px` | dashboard（ui-ux-pro-max density=高） |
| 字体 | 系统字体优先；大标题负 tracking `-0.02em`；正文行高 `1.5` | §4.6 |
| 状态色（语义与小程序端 T22 一致） | approved/on/enabled **绿** `#2E7D32`（或 EP success）；pending **琥珀** `#B26A00`（warning，非红）；rejected/off/disabled **灰** `#9E9E9E` | 负向状态**弱化非红删**（见 §0.4） |
| 触控/点击区 | 最小 `44×44px` 等效 | 图标按钮带 `aria-label`（ui-ux-pro-max P1/P2） |
| 对比度 | 正文 ≥ `4.5:1` | 次级灰文字不得过浅（P1） |
| 动效时长 | `150–300ms`；弹簧/过渡可中断 | 尊重 `prefers-reduced-motion`（§4.3/§4.7） |

- **状态三态（强制）**：加载中（skeleton/`v-loading`）/ 空态（`EmptyState` 或 `el-empty`）/ 正常；三者缺一即缺陷。
- **状态色原则**：approved/on/enabled 绿、pending 琥珀（**警示用琥珀非红**）、rejected/off/disabled 灰；负向操作用灰弱化，不做红色删除式强调（与小程序端 T22 对齐）。

### 0.4 全局统一约束
- **状态三态**：表格/列表须齐备 加载中（skeleton/loading）/ 空态（Empty）/ 正常；操作有反馈（message/notification）。
- **表单规范**：可见 label（禁仅 placeholder）、错误就近展示、必填校验、提交防重复；破坏性操作（删除/下架）走二次确认（Element Plus `ElMessageBox.confirm`）。
- **响应式**：后台以桌面为主，但须无横向滚动（ui-ux-pro-max P5）；断点：`<960px` 侧栏转浮层 + scrim；最小可用宽度建议 `1280px` 内容区不挤压（待裁定见 §八）。
- **可访问性**：对比度 ≥ 4.5:1、图标按钮带 `aria-label`/文字、键盘可达（Tab 顺序合理，P1）。
- **动效**：150–300ms，弹簧/过渡须可中断；尊重 `prefers-reduced-motion`（§4.7）。
- **金额**：分↔元仅在 api 层（`fenToYuan`/`yuanToFen`），页面/模板禁止 `/100` 裸算（§3）。

### 0.5 图标资源规范（与小程序端一致）
- 所有功能图标与情感图标一律用 **SVG 矢量图标**；来源优先级：① Element Plus 内置图标（`@element-plus/icons-vue`）② 本地 `web/src/assets/icons` ③ 阿里云矢量库（Iconfont）经 MCP 拉取。
- **禁止 emoji 字符当图标**（ui-ux-pro-max P4 红线「Emoji as icons」为反模式）。
- 视觉一致：线性风格、统一描边与 24px 网格、语义清晰克制。
- 情感语义唯一：喜欢 / 有用（与小程序端 `ic-heart`/`ic-thumb` 同义，避免两端语义错位）；Web 端如用图标，优先 Element Plus 内置，缺的再从 Iconfont 补，且须与小程序端 §0.5 映射表语义 1:1 对齐（对齐表待裁定，见 §八）。
- **侧栏图标已核实存在**（Element Plus 内置）：`House` / `OfficeBuilding` / `PriceTag` / `Picture` / `Document` / `ChatDotRound` / `ChatLineSquare` / `User` / `UserFilled` / `Tickets` / `Setting` 均为真实导出名（已在 `node_modules/@element-plus/icons-vue` 核实）。

---

## 一、登录与入口

### 1.1 登录页 `web/src/views/login/*`（或 `Login.vue`）
**定位**：管理员鉴权入口。
**现状基线**：通常账号密码登录 + JWT；Element Plus 表单。
**待定项（讨论）**：
- 视觉调性（品牌色 / 居中介卡片 / 左右分栏插画）。
- 是否支持验证码 / 记住登录 / 错误态就近提示。
- 与小程序端视觉语言是否呼应（品牌一致）。

---

## 二、主框架与侧栏信息架构（已定样）

### 2.1 主框架与侧栏条目 `web/src/layout/AdminLayout.vue`

**定位**：后台统一外壳 + 全局导航。

**侧栏菜单结构（三组，已定样）**

侧栏按业务域分为三组，每组含若干条目；每条目标注「图标（Element Plus 内置，禁 emoji）/ 文案 / 路由 / 权限」。

| 分组 | 条目 | 图标（Element Plus 内置，已核实存在） | 路由 | 权限 | 右侧主面板要点（详见对应章节） |
|---|---|---|---|---|---|
| 概览 | 仪表盘 | `House` | `/dashboard` | ADMIN | KPI 卡 + 趋势图，见 §2.2 |
| 内容 | 食堂 | `OfficeBuilding` | `/dashboard/canteens` | ADMIN | 食堂表格 + 级联档口，见 §3.1 |
| 内容 | 菜品 | `PriceTag` | `/dashboard/dishes` | ADMIN | 菜品表格 + 审核状态，见 §3.3 |
| 内容 | 轮播 | `Picture` | `/dashboard/banners` | ADMIN | Banner CRUD + 跳转配置，见 §3.4 |
| 运营 | 审核中心 | `Document` | `/dashboard/reviews` | ADMIN | UGC 审核队列，见 §4.1 |
| 运营 | 动态管理 | `ChatDotRound` | `/dashboard/moments` | ADMIN | 学生动态审核/隐藏，见 §4.3 |
| 运营 | 反馈举报 | `ChatLineSquare` | `/dashboard/feedbacks` | ADMIN | 反馈/Bug 处理，见 §5.2 |
| 运营 | 用户 | `User` | `/dashboard/users` | ADMIN | 学生账号查看/封禁，见 §5.1 |
| 运营 | 管理员 | `UserFilled` | `/dashboard/admins` | **仅 super_admin** | 管理员增删/角色，见 §5.3 |
| 运营 | 操作日志 | `Tickets` | `/dashboard/operation-logs` | ADMIN | 操作留痕列表，见 §6.2 |
| 运营 | 账号设置 | `Setting` | `/dashboard/account` | ADMIN | 个人/通用设置，见 §6.1 |

> **图标修正项（已核实，强制落地）**：原代码存在重复图标——`House` 同时用于「仪表盘」与「食堂」、`Document` 同时用于「审核中心」与「操作日志」。定样后修正为语义区分图标，**以下三个替换目标均为 Element Plus 真实导出图标（已在 `node_modules/@element-plus/icons-vue` 核实存在，无需 Iconfont 兜底）**：
> - 「食堂」由 `House` → **`OfficeBuilding`**（建筑语义，区分仪表盘 Home）
> - 「操作日志」由 `Document` → **`Tickets`**（票据/留痕语义，区分审核中心 Document）
> - 「账号设置」由 `UserFilled` → **`Setting`**（齿轮设置语义，区分管理员 UserFilled）
>
> 其余图标（`House`/`PriceTag`/`Picture`/`Document`/`ChatDotRound`/`ChatLineSquare`/`User`/`UserFilled`）均为 Element Plus 内置真实图标，可直接引用。**管理员条目按 `adminUser.myRole === 'super_admin'` 条件渲染**。

**侧栏交互规范**
- 激活态：轻亮背景 `white/14%` + 左侧 3px 白色竖条；非激活 `white/80%`，hover `white/8%` 背景。
- 条目点击 `router.push`，移动态顺带关闭浮层；图标 `18px`，`color: inherit` 随文字态变色。
- 语义唯一：禁用 emoji 当图标（§0.5）。

### 2.2 数据概览 Dashboard `web/src/views/dashboard/DashboardView.vue`

**定位**：运营数据总览，管理员登录后首屏。

**主面板布局（自上而下）**
1. **KPI 卡片行**（顶部，4 张等宽卡，栅格 `el-row :gutter=16`）：
   - 食堂数 / 档口数 / 菜品数（approved）/ 待审核数（带「前往审核」链接，点击跳 `/dashboard/reviews`）。
   - 每张卡：图标 + 大数字（32px 粗体）+ 标题（12px 次级灰）；待审核卡数字用警示色（琥珀，非红）提示。
2. **趋势图区**（KPI 下，2 列栅格）：
   - 左：近 7 日「菜品上新趋势」折线图（ECharts `line`，须有图例 + tooltip，P10）。
   - 右：菜品状态占比环形图（`pie`，图例在右，hover tooltip 显数值+百分比）。
3. **快捷入口区**（底部卡片）：审核中心、动态管理、反馈举报 三个入口按钮，分别带待办角标（如「待审 12」）。

**组件**：`KpiCard` + `ChartCard`（封装 ECharts，统一图例/tooltip/resize）+ `QuickEntry`。
**视觉**：卡片圆角 `16px`（§0.3）、轻阴影（层级用阴影区分）；图表加载显 `v-loading` 骨架，无数据显 `EmptyState`；待审核 KPI 数字用琥珀色（`#B26A00`，警示非红）。
**交互**：KPI 卡与快捷入口点击就近跳转（router.push）；图表尊重 `prefers-reduced-motion`（去入场动画）。
**ASCII 线框（Dashboard）**
```
┌ TopBar(blur) [折叠]  面包屑：概览 / 仪表盘        ┐
├────────────────────────────────────────────────┤
│ [KPI 食堂数][KPI 档口数][KPI 菜品数][KPI 待审⚠]  │  el-row gutter16
│ [折线:近7日上新趋势]      [环形:菜品状态占比]     │  2列栅格
│ [快捷:审核中心(待审12)] [动态管理] [反馈举报]     │  QuickEntry 带角标
└────────────────────────────────────────────────┘
```

---

## 三、内容管理（CRUD，已定样）

> 内容管理各页共用 `PageContainer`（标题 + 操作区 + 三态）+ `DataTable`（表格三态：加载骨架 / 空态 / 正常）+ `FormDialog`（弹窗表单）+ `StatusTag`（状态徽标）+ `ImageUpload`（图片上传，≤5MB 复用后端 upload）。列表页统一结构：**筛选条（上）→ 表格（中）→ 分页（下）**。

### 3.1 食堂管理 `web/src/views/canteen/CanteensView.vue`
**定位**：食堂 CRUD、排序、状态（open/closed）。
**主面板布局（区块清单）**
- 顶部操作区（`PageContainer` 操作区）：「新建食堂」`AppButton`(primary) → `FormDialog`。
- 筛选条：`DataTable` 筛选区——关键字（名称）+ 状态（`el-select`：全部/营业中 open/已关闭 closed）。
- 表格（`DataTable`）：封面缩略图、名称、所属校区、档口数、状态（`StatusTag`：open 绿 / closed 灰）、排序、操作（查看 / 编辑 / 下架·`ElMessageBox` 二次确认）。
- 分页（下）。
- 行「查看」→ `/dashboard/canteens/:canteenId` 详情（级联档口→档口详情→菜品详情，三级钻取）。
- 批量：勾选 + 批量上/下架（破坏性走 `ElMessageBox.confirm` 二次确认）。

**ASCII 线框**
```
[ PageContainer: 食堂管理 .................... [+ 新建食堂] ]
[ 筛选: 关键字____ 状态[全部▼] ][ 查询 ][ 重置 ] ]
┌───────────────────────────────────────────────┐
│ 封面 | 名称 | 校区 | 档口数 | 状态 | 排序 | 操作 │  DataTable
│ img  | 学一 | 主区 | 12   | 绿(open)| 1  | 查看编辑下架│
└───────────────────────────────────────────────┘
[< 1 2 3 >]   批量:[✓全选] [批量上架][批量下架]
```
**组件拆分**：`PageContainer` + `DataTable` + `FormDialog` + `StatusTag` + `ImageUpload`（表单内）。
**视觉**：状态色见 §0.3；表格三态齐备；破坏性操作二次确认。

### 3.2 档口管理（钻取页，非侧栏独立条目）
**定位**：档口 CRUD（从属食堂），经食堂详情钻取，不单列侧栏。
- `CanteenDetailView`：食堂信息卡（`el-card`）+ 其档口 `DataTable`（名称/封面/状态/菜品数/操作）。
- `StallDetailView`：档口信息 + 其菜品 `DataTable`，行点进 `DishDetailView`。
- 表单字段：名称、所属食堂（只读）、封面、状态、简介；图片走 `ImageUpload`。
**组件拆分**：`PageContainer` + `DataTable` + `FormDialog` + `StatusTag` + `ImageUpload`。

### 3.3 菜品管理 `web/src/views/canteen/DishManageView.vue`
**定位**：菜品 CRUD、标签、审核状态（pending/approved/rejected）、上架/下架。
**主面板布局（区块清单）**
- 顶部操作区：「新建菜品」→ `FormDialog`。
- 筛选条：关键字 + 审核状态（`el-select`：pending/approved/rejected）+ 上架状态（on/off）+ 按食堂筛选。
- 表格列：封面、名称、食堂/档口、价格（已为元，禁 `/100`）、审核状态（`StatusTag`：approved 绿 / pending 琥珀 / rejected 灰，负向弱化非红删，参照小程序端 T22）、上架状态（on 绿 / off 灰）、操作（查看 / 编辑 / 上架·下架·二次确认）。
- 批量上下架 + 审核标记；rejected 行显 `reject_reason`（`el-tooltip` 或展开）。
- 行「查看」→ `DishDetailView`（菜品字段 + 关联评价摘要）。
**组件拆分**：`PageContainer` + `DataTable` + `FormDialog` + `StatusTag` + `ImageUpload`。
**视觉**：审核状态色严格按 §0.3（pending 琥珀非红、rejected 灰）；价格模板禁 `/100`。

### 3.4 轮播图 / Banner 管理 `web/src/views/banner/BannerManageView.vue`
**定位**：轮播图 CRUD、排序、跳转配置（`targetType` 枚举 DISH/URL/NONE）。
**主面板布局（区块清单）**
- 表格列：封面缩略图、标题、跳转类型（`StatusTag` 语义：DISH=菜品 / URL=外链 / NONE=无）、排序、状态（enabled 绿 / disabled 灰）、操作（编辑/删除·二次确认）。
- 表单 `FormDialog`：标题、封面（`ImageUpload`）、跳转类型（`el-select`：选 DISH 显菜品搜索选择、选 URL 显链接输入、选 NONE 隐藏）、排序（数字）、状态开关。
- 预览：表单内右侧显 Banner 预览卡（圆角 `16px`，模拟小程序首页轮播比例）。
- 排序：本期用序号输入 + 保存重排（保持简单，不引拖拽库）。
**组件拆分**：`PageContainer` + `DataTable` + `FormDialog` + `StatusTag` + `ImageUpload`。
**视觉**：预览卡圆角 `16px`（对齐 §0.3）；状态色统一。

### 3.5 活动 / 特价管理（已移除，不单列）
按 `project_spec.md` §0.3 决策：**活动不独立成模块**，统一经 Banner（`targetType=URL` 外链）触达。故无独立活动页；如需「特价/新品」标记，在菜品 `tags` 字段体现，由 Banner 文案引导。本条目不再占用侧栏。

---

## 四、内容审核（已定样）

### 4.1 审核中心（统一队列） `web/src/views/admin/ApplyReviewView.vue`
**定位**：学生 UGC 提交审核（食堂 / 档口 / 菜品 / 评价），通过/驳回统一入口。
**主面板布局（区块清单）**
- 顶部 Tab 切换审核对象：菜品 / 档口 / 食堂 / 评价（`el-tabs`，默认「菜品·待审」）。
- 筛选条：状态（`el-select`：pending/approved/rejected）+ 关键字。
- 列表：每条 UGC 卡片化（非纯表格）——左缩略图/封面、右标题+提交人+时间+状态 `StatusTag`；行点开右侧 `el-drawer` 看完整内容（图文详情预览）。
- 操作（抽屉内）：「通过」/「驳回」；驳回必填 `reject_reason`（`ElMessageBox.prompt` 或抽屉内文本域），写回原记录 `reject_reason` 并置 `rejected`；学生可复用原记录重提。
- 批量：列表勾选 + 批量通过（驳回必须单条填理由，不支持批量驳回）。

**ASCII 线框**
```
[ 审核中心 ]  Tab: [菜品·待审|档口|食堂|评价]
[ 状态:pending▼ 关键字____ ][查询]
┌─ UGC 卡片 ───────────────┐      ┌─ el-drawer ──────┐
│ [封面] 标题 提交人 时间   │ ──▶ │ 图文全文预览       │
│ StatusTag(pending琥珀)    │      │ [通过][驳回]      │
└──────────────────────────┘      │ 驳回理由:____     │
[✓批量通过]
```
**组件拆分**：`PageContainer` + `DataTable`（卡片化模式）或自定义卡片列表 + `StatusTag` + `el-drawer` + `ElMessageBox`。
**视觉**：状态色见 §0.3（pending 琥珀）；抽屉 spring 0.8/0.3 可中断（§4.4）。

### 4.2 评价与晒图审核（并入审核中心「评价」Tab）
不单列页面，作为 §4.1 的「评价」Tab：列表显评价正文 + 晒图缩略图（点开大图），敏感词命中高亮（后端返回标记字段，前端黄底高亮）；操作：隐藏（`isHidden=1`，非删除）/ 删除（二次确认）/ 通过。

### 4.3 动态管理 `web/src/views/admin/MomentManageView.vue`
**定位**：学生动态（moment）审核 / 隐藏 / 删除。
**主面板布局（区块清单）**
- 筛选条：状态（`el-select`：待审 pending/已发布 approved/已隐藏 hidden）+ 关键字。
- 列表（卡片/表格）：动态封面或首图、作者、内容摘要、时间、状态 `StatusTag`。
- 操作：查看（抽屉预览全文+图）、隐藏（非删，`isHidden=1`）、删除（`ElMessageBox` 二次确认）；违规动态走隐藏优先、删除二次确认。
**组件拆分**：`PageContainer` + `DataTable` + `StatusTag` + `el-drawer` + `ElMessageBox`。
**视觉**：状态色见 §0.3；隐藏用灰弱化、删除红色须二次确认。

---

## 五、用户与反馈（已定样）

### 5.1 用户管理 `web/src/views/user/UserView.vue`
**定位**：学生账号查看、封禁/解封（仅 `STUDENT`，`UserVO` 不含 `stallId`）。
**主面板布局（区块清单）**
- 筛选条：关键字（邮箱/昵称）+ 状态（`el-select`：正常/已封禁）。
- 表格列：头像、昵称、邮箱（`@bjtu.edu.cn`）、角色（`STUDENT` 标签）、注册时间、状态 `StatusTag`、操作（查看 / 封禁·解封·`ElMessageBox` 二次确认）。
- 行「查看」抽屉：基本资料 + 贡献数（菜品/评价/动态）+ 举报记录关联。
- 封禁为破坏性操作，走 `ElMessageBox.confirm` 二次确认；不提供删除账号（注销走小程序端 `DELETE /my/account`，见 project_spec §5.x）。
**组件拆分**：`PageContainer` + `DataTable` + `StatusTag` + `el-drawer` + `ElMessageBox`。

### 5.2 反馈举报 `web/src/views/admin/FeedbackView.vue`
**定位**：用户反馈 / Bug 查看与处理（复用 `user_feedback` 表，`related_type`/`related_id` 关联举报对象）。
**主面板布局（区块清单）**
- 顶部 Tab：反馈 / Bug / 举报（按 `type` 分组）。
- 筛选条：处理状态（`el-select`：待处理/已处理/已忽略）+ 关键字。
- 列表卡片：类型标签、内容摘要、关联对象链接（如举报的菜品/评价，点跳详情）、提交人、时间、状态 `StatusTag`。
- 操作：标记已处理 / 忽略 / 回复（回复走 `ElMessageBox` 二次确认，写回 `user_feedback.reply`）；举报类可联动跳转被举报内容审核。
**组件拆分**：`PageContainer` + 卡片列表 + `StatusTag` + `ElMessageBox`。

### 5.3 管理员管理 `web/src/views/admin/AdminManageView.vue`（仅 super_admin）
**定位**：管理员增删、角色（admin / super_admin）。
**主面板布局（区块清单）**
- 表格列：账号、角色 `StatusTag`（super_admin 区别于 admin，用不同色阶）、创建时间、状态、操作（改角色 / 禁用 / 删除·`ElMessageBox` 二次确认，**super_admin 不可删自己**）。
- 顶部「新增管理员」→ `FormDialog`（邮箱+初始密码+角色）；角色变更走二次确认。
- 权限：非 super_admin 看不到此侧栏条目（§2.1 条件渲染）。
**组件拆分**：`PageContainer` + `DataTable` + `FormDialog` + `StatusTag` + `ElMessageBox`。

---

## 六、系统（已定样）

### 6.1 账号设置 `web/src/views/admin/AccountSettingsView.vue`
**定位**：管理员个人设置 + 通用设置（不含未生效的虚假控制）。
**主面板布局（区块清单）**
- 两个 `el-card` 卡片区：①「个人资料」（昵称/头像/修改密码，密码走 `ElMessageBox` 二次确认）②「通用」（**仅渲染已接入后端**的项）。
- 约束：未生效/未接后端的设置项**禁止渲染**（禁虚假控制，同小程序端 T28）；若「通知开关」等暂无后端订阅能力，须显式标注「（即将推出）」或默认隐藏，不得做假开关（详见 §八 待裁定）。
**组件拆分**：`PageContainer` + `el-card` + `ElMessageBox`。

### 6.2 操作日志 `web/src/views/admin/OperationLogView.vue`
**定位**：管理员操作留痕审计。
**主面板布局（区块清单）**
- 筛选条：操作人 + 操作类型 + 时间范围（`el-date-picker`）。
- 表格列：时间、操作人（角色标签）、操作类型、对象、IP、结果（`StatusTag` 成功绿/失败红）；行点开显详情抽屉（含请求摘要）。
- 只读页，无写操作。
**组件拆分**：`PageContainer` + `DataTable` + `StatusTag` + `el-drawer`。

---

## 七、组件拆分总览（去重，已定样）

**基础布局/容器组件（自封装）**
- `PageContainer`：页面标题 + 右侧操作区 + 三态（加载骨架/空态/正常）容器，所有内容页包裹。
- `DataTable`：封装 `el-table`，统一三态、操作列按钮（查看/编辑/下架/删除，破坏性走二次确认）。
- `FormDialog`：弹窗表单，可见 label + 错误就近 + 必填校验 + 提交防重复。
- `StatusTag`：状态徽标（与小程序端语义一致：approved 绿 / pending 琥珀 / rejected 灰 / on 绿 / off 灰 / enabled 绿 / disabled 灰）。
- `ImageUpload`：图片上传（≤5MB，复用后端 upload）。
- `ChartCard`：ECharts 封装（统一图例 + tooltip + resize 监听 + 加载态）。
- `KpiCard` / `QuickEntry`：Dashboard 专用。
- `EmptyState` / 加载骨架：全局统一（与小程序端视觉呼应）。

**交互组件（Element Plus 原生，统一规范）**
- `el-drawer`：详情预览（审核中心/动态/用户/操作日志），spring 0.8/0.3 可中断（§4.4）。
- `ElMessageBox.confirm` / `ElMessageBox.prompt`：破坏性二次确认 / 驳回理由。
- `el-tabs` / `el-select` / `el-date-picker`：分段/Tab、筛选、时间范围。
- 状态色严格按 §0.3（approved/on/enabled 绿、pending 琥珀、rejected/off/disabled 灰）。

---

## 八、待与用户/技术负责人裁定项（集中，禁止散落臆测）

以下问题需求未定 / 后端契约缺失 / 需技术负责人拍板，**不臆测实现**：

1. **图标修正已核实落地（非待定）**：§2.1 重复图标修正（`House→OfficeBuilding` 食堂、`Document→Tickets` 操作日志、`UserFilled→Setting` 账号设置）所用三个 Element Plus 图标**均已在 `node_modules/@element-plus/icons-vue` 核实真实存在**，无需 Iconfont 兜底，Web 开发工程师直接替换即可（见 §2.1 注）。
2. **暗色主题 / 多页签工作区**：本期不做，后续若做需补 `theme` token 与 `el-tabs` 工作区改造。
3. **后端接口缺口**：若某管理页（如操作日志、动态管理）后端尚未提供契约，须在 `tasks/CONTRACT_IMPACT.md` 登记后再实现。
4. **Dashboard 数据口径**：KPI/趋势图取数口径以 §2.2 为准，若后端口径调整须回同步本文件。
5. **「通知开关」等通用设置后端能力**：§6.1「通用」卡片中若通知订阅等暂无后端能力，须标「（即将推出）」或隐藏，禁止虚假控制；具体哪些开关已接后端待技术负责人确认。
6. **最小可用宽度断点**：§0.4 响应式建议内容区 `1280px` 不挤压，确切断点与侧栏浮层阈值（当前 `<960px`）是否需细分待技术负责人裁定。
7. **两端图标语义对齐表**：Web Element Plus 图标与小程序 `ic-*` 的 1:1 语义映射（如"食堂"Web `OfficeBuilding` vs 小程序 `ic-dish`？）待技术负责人裁定统一映射表，写入小程序端 §0.5 与本文 §0.5。
