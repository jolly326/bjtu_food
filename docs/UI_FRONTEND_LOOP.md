# 客户端 UI / 前端统一 Loop（循环改进机制）

> 目的：把「全站视觉一致性 + 代码质量 + 文档同步」从一次性任务变成**可迭代、可验证、可停止**的循环。
> 本文档是**流程真源**；各页 UI 口径仍以 `docs/ui/client-*.md` 为准（首页为基准稿）。

---

## 0. 边界

- **范围**：`client/src`（uni-app + Vue3 微信小程序，仅学生端）。不含 `server/`、`web/`。
- **基准**：`docs/ui/client-首页菜品浏览.md`（首页 UI 设计）是其余页面的对齐基准。
- **不变的原则**：`theme/tokens.ts` → `theme/generated-colors.css` → `App.vue` 的 token 链路是唯一真源；页面不得自算导航尺寸、不得另立色值。

---

## 1. 角色与职责（一轮内按序切换）

| # | 角色 | 职责 | 产出 |
|---|---|---|---|
| 1 | **前端程序员** | 统一背景（壁纸层）等跨页基础设施；保证每页接入方式一致 | 代码 + 接入清单 |
| 2 | **UI 设计师** | 以首页为基准比对各页；判定差异是"有意"还是"跑偏"；给出对齐口径 | 差异清单 + 判定 |
| 3 | **UI 设计师 + 前端程序员** | 重构与性能优化、清除死代码/冗余、抽取共享组件与字段、设计统一空/加载/错误态 | 重构 + 新组件 |
| 4 | **前端程序员** | 把设计变更同步进 `docs/ui/*` 与本文档轮次记录 | 文档 |
| 5 | **UI/前端代码质量工程师** | 审计：文档与代码是否一致、质量是否达标；把不合格项放入下一轮候选池 | 审计报告 + 候选池 |

---

## 2. 每轮固定流程（5 步 + 验证闸门）

```
Step1 背景/基础设施统一  →  闸门①
Step2 UI 差异对齐        →  闸门②
Step3 重构 / 性能 / 死代码 →  闸门③
Step4 文档同步            →  闸门④
Step5 审计 → 候选池 → 提出下一轮方向（需讨论同意）
```

**验证闸门（每步做完必须过，否则不得进入下一步）**

| 闸门 | 检查项 | 通过标准 |
|---|---|---|
| ① | 背景接入 | 全部注册页面接入方式一致；无页面用私有底色盖住壁纸层 |
| ② | 设计 token 化 | 无新增裸色值 / 裸字号 / 裸圆角；差异项已在文档中标注"有意保留" |
| ③ | 构建与类型 | `npx vue-tsc --noEmit` exit 0、`npm run build:mp-weixin` DONE、主包 ≤2MB（当前 ≈0.41MB） |
| ④ | 文档一致 | 代码注释与实现不相矛盾；`docs/ui/*` 已同步；轮次记录已写入本文档 |

---

## 3. 红线（不得违反）

1. **Token 唯一真源**：色值/间距/圆角/字号/字重/层级一律走 token；确需字面量必须在注释中登记理由。
2. **页面 SHALL NOT 自算导航尺寸**：状态栏/胶囊/标题带高度一律取 `useNavMetrics()`。
3. **横条表面**：不得用"复制/近似页底壁纸"的方式做表面（六种做法已全部失败）；现口径为「未吸顶透明 + 吸顶态切片，偏移基准 = `titleBandPx` 同源」。
4. **分区标题一律 `SectionTitle`**（§4.9 红线）。
5. **不得引入新框架 / 新依赖**：跨端编译限制（禁自定义指令等）维持现状。

---

## 4. 轮次记录

### Round 1（2026-09-27）

**范围**：审计 + 只做"零争议"的收敛（不做任何需要设计拍板的改动）。

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① 背景 | 11 个注册页面**已全部接入** `<PageWallpaper fixed />`；发现 `feedback` 页仍用私有 `--bg-warm` 底色（唯一消费点、与本页注释矛盾） | 已改为随全局 `--bg-page`（壁纸层覆盖） |
| ② 对齐 | 裸 `50%` 圆角 ×2、裸 `font-weight: 600` ×2、裸 `#ffffff` ×1、裸 `16rpx` gap ×2 | 已换为 `--radius-circle` / `--weight-semibold` / 白字 token / `--spacing-sm` |
| ③ 重构 | 死声明 `.nickname--guest`（与基类同值的 no-op）、冗余 `.user-card--guest`；`my-reviews` 手写分区标题（违反 §4.9）；孤儿 token `--bg-warm`（全项目零消费） | 死声明/冗余规则已删；改用公共 `SectionTitle`；`--bg-warm` 从 `tokens.ts` + `generated-colors.css` **双源同步移除** |
| ④ 文档 | `PageWallpaper.vue` 与首页 3 处注释仍声称"不再有任何切片"，与代码矛盾 | 已同步为最终口径（页面级 1 处 + 容器内切片 1 处） |
| ⑤ 审计 | 见下方候选池 | — |

**闸门**：`vue-tsc` exit 0 / `build:mp-weixin` DONE / lint 0 诊断 / 主包 **0.41MB** / 注释与实现一致 / `--bg-warm` 零引用（仅剩说明性注释） ✅

**Round 1 改动文件**：`theme/tokens.ts`、`theme/generated-colors.css`、`pages/feedback/index.vue`、
`pages/mine/index.vue`、`pages/my-reviews/index.vue`、`pages/detail/dish/index.vue`、
`pages/detail/dish/ReportModal.vue`、`pages/feedback/ListPickerSheet.vue`、
`components/ReviewItem.vue`、`components/ImagePicker.vue`、`components/PageWallpaper.vue`、
`pages/home/index.vue`（注释同步）。

### Round 2（2026-09-27）—— 建立统一空态组件（首批迁移）

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① 前端 | 新建 `components/EmptyState.vue`：无底色居中极简列，可选 **图标 / 主文案 / 次文案 / CTA / 卡片变体 / 外边距**；全 token 化；与失败态 `RetryBlock`（凹陷卡、整块可点）明确分工 | 组件落地 |
| ② UI | 统一口径：主文案 `--font-body` + `--text-secondary` + medium；次文案 `--font-aux` + `--text-tertiary`；CTA 主色胶囊（`--color-on-primary` + semibold） | 与既有 3 套取最大公约数 |
| ③ 重构 | 首批迁移 **3 处**（notifications「暂无通知」/ my-reviews 两条提示 / DishSummaryCard「还没有评分」），删除各自 `.empty-tip` / `.empty-text` / `.summary-empty` 样式 | −6 段重复样式 |
| ④ 文档 | 组件内注释登记消费方与分工 | ✅ |

**闸门**：vue-tsc 0 / build DONE / lint 0 / 主包 0.42MB ✅

### Round 3（2026-09-27）—— 空态迁移收口（含 CTA 与整屏居中态）

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① 前端 | 组件补 **卡片变体**（`card`：白底 + 大圆角 + 柔和投影），供**整屏居中的主空态**使用；区块内小空态仍为无底色最简形态 | 两种形态归一 |
| ② UI | find「没搜到…推荐这道菜」→ 卡片变体 + `icon="search"` + CTA；DishReviewSection 两态（含「写第一条评价」CTA）；feedback 选择器两态 | 4 处迁移完成 |
| ③ 重构 | 删除 `.find-empty` / `.fe-*` / `.review-empty*` / `.pick-empty*` 共 **≈45 行**重复样式；find 保留 `.find-empty-host` 只做整屏居中占位（视觉全在组件内） | −45 行 |
| ④ 文档 | 本轮记录写入本文档；组件注释同步 | ✅ |

**闸门**：vue-tsc 0 / build DONE / lint 0 / 旧空态类名残留 **0**（仅剩说明性注释）/ 主包 **0.41MB** ✅

**Round 2+3 改动文件**：`components/EmptyState.vue`（新增）、`pages/notifications/index.vue`、
`pages/my-reviews/index.vue`、`pages/detail/dish/DishSummaryCard.vue`、`pages/find/index.vue`、
`pages/detail/dish/DishReviewSection.vue`、`pages/feedback/index.vue`。

### Round 4（2026-09-27）—— 文档两页去重 + 收掉零消费分支

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① 前端 | `pages/privacy/{index,agreement}.vue` **逐字节重复**同一套外壳（壁纸 + `AppHeader` + 滚动容器 + 文档白卡 + `.doc-*` 排版）→ 抽包内共享组件 **`pages/privacy/DocPage.vue`**（就近组织，与 `feedback/ListPickerSheet.vue` 同款做法） | 两页 66 行 → 各 ≈20 行（仅承载正文数据） |
| ② 重构 | `ListPickerSheet` 内置空态回退（原「输入关键词搜索」）—— 唯一消费方 `feedback` 恒提供 `#empty` 槽 ⇒ 分支为死代码，按「零消费即删」移除（槽本身保留） | 删 1 分支 + 1 条样式 |
| ③ 验证 | **实证发现并修正一处真实缺陷**（见下方「踩坑」）：槽方案会让正文丢排版，改数据驱动后复验通过 | ✅ |

**闸门**：vue-tsc 0 / build DONE / lint 0 / 主包 **0.41MB** / 死代码 `lp-empty-text` 残留 **0**

> ### ⚠️ 踩坑记录（Round 4，**重要教训，勿回退**）
> **小程序端：子组件用 `:deep()` 给「插槽内容」加样式不保证生效。** 实测编译产物——
> · 槽节点编译进**父页 wxml**，且**不带任何 `data-v-*` 作用域类**（裸 `<text class="doc-h">`）；
> · 子组件内的 `:deep(.doc-h)` 编译为 `.data-v-64630978 .doc-h`（跨组件边界的后代选择器）→ 命中依赖运行时树结构，**不可靠**；
> · 后果：正文失去字号 / 字色 / 行高排版。
> **结论（定为红线）**：需要排版的正文/内容**不要经插槽跨组件边界传递** —— 改用**数据驱动**（`sections: DocSection[]`，节点由组件自身模板渲染 ⇒ `scoped` 100% 命中，已由编译产物复验：`.doc-h.data-v-488056fa` + `<text class="doc-h data-v-488056fa">`）。

**Round 4 改动文件**：`pages/privacy/DocPage.vue`（新增）、`pages/privacy/index.vue`、`pages/privacy/agreement.vue`、
`pages/feedback/ListPickerSheet.vue`。

### Round 5（2026-09-27）—— 层级 token 化 + 死样式/死分支清除 + 色值表对账

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① 前端 | 页底壁纸层的裸 `z-index: -1` → 新增 **`--z-page-bg: -1`** token（`App.vue` 标度内），`PageWallpaper` 消费 | 单点可调、全站同源 |
| ② 重构 | 首页旧补丁 `.home-page-bg { z-index: 0 }` + `.scroll-wrap { z-index: 1 }`（互为补丁）**删除** —— 组件 token 化后首页不再需要例外 | 首页与其它 10 页统一为同一机制 |
| ③ 死代码 | `ImageFallback` 的 `font-size: 64rpx !important` 与 `ImageSwiper` 的 `.placeholder-icon` 规则：`IconSvg` 尺寸由 `size` prop **内联**控制，宿主节点 font-size 无效 ⇒ **两条规则 + 两个 class 全删** | −2 处 `!important` 死样式 |
| ④ 死分支 | `TabBar` 的 `wallpaper` prop 与其**白底分支**：两个主根页（home / mine）均传 `true` ⇒ 零消费，按「零消费即删」移除，透明底成为唯一行为 | `is-wallpaper` 残留 **0** |
| ⑤ token 退役 | `--bg-page-grad-from / -to`（原「顶部渐变」，注释自称「吸顶容器切片真源」—— 方案已废止）、`--card-bg`（与 `--bg-card` **同值**重复键）：三者零 `var()` 引用、零文档引用 ⇒ **双源删除**（`tokens.ts` + `generated-colors.css`） | CSS_VARS 57 → **54 键** |
| ⑥ 对账 | ⚠️ 发现**生成脚本 `scripts/gen-css-vars.ts` 与 `npm run gen:tokens` 不在本工作区**（`generated-colors.css` 头部声明"手工维护"）⇒ 已手工同步，并用脚本做**逐键对账** | 54 ↔ 54 **逐键一致** ✅ |

**闸门**：vue-tsc 0 / build DONE / lint 0 / **逐键一致 54↔54** / 主包 **0.41MB** / `is-wallpaper` 残留 0

**Round 5 改动文件**：`App.vue`、`theme/tokens.ts`、`theme/generated-colors.css`、
`components/PageWallpaper.vue`、`components/ImageFallback.vue`、`components/TabBar.vue`、
`pages/detail/dish/ImageSwiper.vue`、`pages/detail/dish/index.vue`（标注局部层叠）、
`pages/home/index.vue`、`pages/mine/index.vue`。

### Round 6（2026-09-27）—— 以首页为基准的全站 UI 差异审计 + 无视觉变化的裸值收口

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① 审计 | **以首页为基准**逐页比对 6 类（分区标题 / 卡片壳 / 间距栅格 / 字号字重 / 圆角 / 三态）—— 覆盖 `pages/**` 全部页面 + `components/**` | 产出 gap 清单（A 按页 / B 分两类 / C Top5） |
| ② 前端 | 取其中**同值替换、零视觉变化**的裸值：裸 `24rpx`×2（`HomeMealTabs` / `App.vue` 全局 `.scroll-wrap`）、`padding-right: 64rpx`（`ReviewItem`）、`border-radius: 0`（`ListPickerSheet`）→ 全部改走 token | −4 处裸值 |
| ③ 文档对账 | 修 2 条与实现矛盾的注释：`AppHeader`（要求同步 find 的 `.search-nav`，该元素已不存在）、`profile`（头像 112rpx，实际 120rpx）；同步 `home` 注释中引用的旧值 | 闸门④ 通过 |
| ④ 三态 | 加载态现状取证：**全站零骨架屏、零 `uni.showLoading`**（合规 §4.8）；主流 = 静默空白，3 处局部拉取给文案 | 口径已定（见下方「三态口径」） |

**闸门**：vue-tsc 0 / build DONE / lint 0 / 裸值残留 0 / 产物实证 `var(--spacing-2xl)`、`var(--radius-none)` 已落盘 / 主包 **0.41MB**

> ### 三态口径（Round 6 定稿，写入本文档作为后续判据）
> | 态 | 统一实现 | 现状 |
> |---|---|---|
> | **空态** | `components/EmptyState.vue`（7 套手写 → 1，Round 2/3） | 9 处用法 ✅ |
> | **失败态** | `components/RetryBlock.vue`（P3-03 上提） | 5 处 + 2 处登记例外（`detail-fail` 双 CTA / `ReportModal` 字典低频） |
> | **加载态** | **主流 = 静默空白**（§4.8 红线：不设骨架屏 / 不设 loading 指示）；**例外 = 用户明确知道「在等什么」的局部拉取**才给简短文案（触底「正在加载更多…」、`UpdateForm`「正在载入菜品信息…」、`ReportModal`「加载中…」）；**Round 13 追加例外**：**用户主动点击重试**可在 `RetryBlock` 内给**转圈**在途反馈 | 全站零骨架屏、零 `uni.showLoading` ✅ |
> ⇒ 判据：**首屏 / 切筛选 = 静默**；**局部、有明确等待对象 = 简短文案**。新增加载态须符此口径，不得引入骨架屏或 `showLoading`。

**Round 6 改动文件**：`App.vue`、`components/AppHeader.vue`、`components/ReviewItem.vue`、
`pages/home/HomeMealTabs.vue`、`pages/home/index.vue`、`pages/profile/index.vue`、
`pages/feedback/ListPickerSheet.vue`（均为同值替换 / 注释同步，零视觉变化）。

### Round 7（2026-09-27）—— 度量收口 + 补 `--spacing-3xs` 档 + 主钮圆角归档

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① 度量收口 | `AppHeader` 原自连 `wx.getMenuButtonBoundingClientRect()` + `getNavBarHeight()` 自算导航度量（与 `AppTitleBand` / `SearchBar` / `home` / `find` 的 `useNavMetrics()` **双源**，违反 `client-page-structure`）→ 改为消费 `useNavMetrics()`；**保留局部变量名**（`statusBarHeight` / `navBarHeight`）⇒ 模板零改动 | 删除 18 行自算代码；真机**零变化**（同函数同回退），仅 H5 退化默认值 56→44（与 `AppTitleBand` 同值 ⇒ 更一致） |
| ② 补档 | 新增 **`--spacing-3xs: 2rpx`**（值 = 既有裸值）并替换 6 处微间距（`my-reviews` / `DishSummaryCard` / `FindResults` / `ReviewItem` / `DishInfoCard`×2） | 裸 `gap/padding: 2rpx` 残留 **0**；**零视觉变化** |
| ③ 圆角归档 | `ReportModal` 提交钮 `12rpx` → **`--radius-btn`（16rpx，全站主钮档）** | ⚠️ 唯一一处**视觉微差**（12→16rpx），需真机确认 |
| ④ 边界 | 1–2rpx **描边宽度**（`border: 2rpx solid`）**未动** —— 描边宽 ≠ 间距，不并入门槛档 | 避免误改 |

**闸门**：vue-tsc 0 / build DONE / lint 0 / 裸 2rpx 残留 0 / `--spacing-3xs` 7 处（1 定义 + 6 消费）/ 主包 **0.41MB**

**Round 7 改动文件**：`App.vue`（补 token）、`components/AppHeader.vue`（度量收口）、`components/ReviewItem.vue`、
`pages/my-reviews/index.vue`、`pages/find/FindResults.vue`、`pages/detail/dish/{DishSummaryCard,DishInfoCard,ReportModal}.vue`。

### Round 8（2026-09-27）—— 度量第二源收口 + 性能复查 + 发现跨会话口径冲突

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① 度量收口 | `pages/detail/dish/useDishPage.ts` 原自算状态栏 / 导航行 → 改走 `useNavMetrics()`（与 `AppHeader` Round 7 同法）；**胶囊避让 `rightPad` 保留自持**（`navPadRight` 额外叠加 `env(safe-area-inset-right)`，与本页口径不等价 ⇒ 替换会改布局，需先确认） | 真机零变化（同函数同回退） |
| ② 性能复查 | 产物 **421 KB（0.41 MB）**，上限 2 MB，余量充足；构成：pages 155 / **static 115（壁纸 115 KB，最大单文件，≤300KB 合规）** / common 72 / components 48 / 其余 ≈31 KB；源码最大 `home/index.vue` 23 KB、`useDishPage.ts` 21.5 KB | **无需瘦身**；无骨架屏/无大图滥用 ✅ |
| ③ 裸值 | `10rpx`（`notifications .msg-dot { margin-top }`）为**光学对齐**值、不在 4pt 栅格，改档会动未读点位置 ⇒ **不改，转 UI 决策** | 留池 |
| ④ ⚠️ 冲突 | **发现跨会话冲突**（见下方 P0） | 转入候选池 |

**闸门**：vue-tsc 0 / build DONE / lint 0 / 主包 **0.41MB**

**Round 8 改动文件**：`pages/detail/dish/useDishPage.ts`（度量收口，真机零变化）。

### Round 9（2026-09-27）—— 层级冲突核对（R5 改动 ↔ 新切片）+ 矛盾注释对账

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① **核对**（上轮承诺） | R5 删掉的 `.home-page-bg{z-index:0}` / `.scroll-wrap{z-index:1}` 与**新切片**是否冲突？ ⇒ **不冲突**：切片 `.home-sticky-slice` 的 `z-index: -1` 是**相对于 `.home-sticky` 自身的层叠上下文**（该容器 `position: sticky` + `z-index: var(--z-header)` ⇒ 自成上下文），与页面级 `--z-page-bg` **互不影响** | ✅ 结论已写入代码注释 |
| ② 注释对账 | 修正 **Loop 自身留下的、与当前实现矛盾**的 2 处注释：①页面底壁纸层注释称"标题带/吸顶容器/TabBar 都不需要再铺任何表面" ②滚动区注释称"全页零表面、零切片、零滚动监听"（而实现已有 `.home-sticky-slice` 与 `pinned` 离散开关） | 旧口径残留 **0**（**未改动任何设计或实现**） |
| ③ 定档判定 | 剩余圆角裸值 `8rpx`（`ReviewComposer:213`）/ `12rpx`（`ReportModal:115` 选项行）：标度现有 0/16/24/32/48/999/50%，**无 8/12 档** ⇒ 补档会造成档位增殖，归档又会改视觉 ⇒ **暂不动，转 UI 决策** | 留池 |

**闸门**：vue-tsc 0 / build DONE / lint 0 / 主包 **0.41MB** / 旧口径注释残留 0

**Round 9 改动文件**：`pages/home/index.vue`（**仅注释**：记录层叠结论 + 修正矛盾口径，零代码行为变更）。

### Round 10（2026-09-27）—— 文档与代码对齐收口（切片口径冲突正式关闭）

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① 背景确认 | 复查发现 **UI 文档 §0 / §11 主体已被另一会话同步到切片口径**（"只有吸顶容器需要表面：未吸顶透明、吸顶后背景图切片"），P0 冲突的**方向已定** ⇒ 本轮只做收尾：清掉残留的旧口径句子 | 冲突降级为纯收尾 |
| ② 文档对账 | 修正 **11 处**与代码矛盾的旧口径：滚动阈值（吸顶语义恢复）、壁纸层"切片已全部取消"、层级（旧 z-index 补丁描述）、接入范围（`<TabBar wallpaper />` 已删 / 吸顶切片）、横条口径（工具栏吸顶态有切片）、组件表（`absolute` 模式已有消费方）、§13 第 20 条（切片复用同 `src`）、§1 行为（标题带无遮盖）、组件用量表、历史留痕结论句、§15 控件类型表（标题带切片已不存在） | 旧口径残留 **0** |
| ③ 层级核对 | R5 层级改动 ↔ 新切片：**无冲突**（R9 已核，结论已写入代码注释） | ✅ |

**闸门**：文档旧口径残留 **0** / vue-tsc 0 / 主包 0.41MB

**Round 10 改动文件**：`docs/ui/client-首页菜品浏览.md`（**仅文档**，11 处同步）。

### Round 12（2026-09-27）—— **壁纸可见性根因修复** + 用户裁决批（导航统一 / 纱值 / 清理）

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① 🔥 **根因修复** | 用户反馈「壁纸没到全局、各页仍是奶黄底」。查明是 **CSS 绘制顺序**：根层叠上下文里「流内块背景（②）」晚于「负层级子层（①）」绘制 ⇒ **页面根自己的 `background: var(--bg-page)` 把 `z-index: -1` 的壁纸层整块盖住**。修法：**底色下沉到小程序最低层 `page{}`**（天然在壁纸与所有内容之下，仍兜底防白屏），11 页页面根一律去掉底色 | 产物实证：`page{background:var(--bg-page);…}` ✅；**页面根底色残留 0** ✅ |
| ② 裁决 1A | `AppHeader` 重写：实心主色 → **透明**；箭头 icon → **文字「返回」**；**有返回 ⇒ 标题绝对居中 / 无返回 ⇒ 标题居左**（与 `AppTitleBand` 同位置同档）；**补齐缺失的 `<slot>`**（通知页「全部已读」原本塞在组件标签里、无落位）+ 右操作自动避让胶囊。**两组件按需显示、不合并**（用户裁定） | 产物含「返回」✅ 含 slot ✅ |
| ③ 裁决 8 | `useDishPage` 删除自持 `rightPad` + 派生 computed → 直接用 `useNavMetrics().navPadRight`（度量真源**全程单源** ✅） | 源码核对模板仍以 `--nav-pad-right` 消费 ✅ |
| ④ 裁决 3A | `--page-wash` 以代码 **0.6** 为唯一真源：改代码注释（原 0.8）+ UI 文档两处（原 0.4） | 三处一致 ✅ |
| ⑤ 裁决 10 | 删除 `.home-page-bg`（无规则的死 class）+ 同步注释与文档 | 残留 **0** ✅ |
| ⑥ 裁决 A | `我的主页 / 我的` 改「顶栏 + `scroll-view`」结构（`flex: 1` + `height: 100vh`）⇒ 内容**永不进入**透明标题带背后（与首页 §11 同一结构性原则、零表面） | 产物两页 `scroll-view` 各 1 ✅ |
| ⑦ ⚠️ **更正（重要）** | 用户裁决 4B＝「删 14 个零引用 token」。**复核发现原扫描方法有缺陷**：只统计了 `var(--x)` 消费，**漏了 JS 侧 `COLOR_MAP['x']` 消费**。重扫（区分两种消费）⇒ **14 个 token 全部仍在使用（`COLOR_MAP[…]` 1–2 处）**，**一个都不能删** ⇒ 4B **撤回**，未执行删除 | 见候选池更正说明 |

**闸门**：vue-tsc 0 / build DONE / lint 0 / 主包 **0.41MB** / 页面根底色残留 0

**Round 12 改动文件**：`App.vue`（`page{}` 兜底底色 + 删 `.page` 底色 + 纱注释）、`components/AppHeader.vue`（重写）、
`pages/{notifications,find,my-reviews,mine,auth,profile,home,detail/dish,privacy/DocPage}.vue`（页面根去底色）、
`pages/detail/dish/useDishPage.ts`（避让并入）、`docs/ui/client-首页菜品浏览.md`（纱值 / class 引用）。

### Round 13（2026-09-27）—— 裁决批（卡壳归一 / 阴影 / 圆角 / 未读指示）

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① 裁决 2B | `CardSection` 新增 **`flush` 变体**（外边距归零，块间距由父级统管）；`DishReviewSection` 评价卡由手写三件套改为 **`<CardSection flush>`** ⇒ 同页三卡内距统一到 `--spacing-md`（原先本卡 16/24rpx 与另两卡 24rpx 不同轴） | `review-card` 残留 0；`CardSection` 消费点 5 |
| ② 裁决 5A | 卡片阴影一律 `shadow-card`：`feedback .q-card` 由 `shadow-warm` 改 `shadow-card`；**`shadow-warm` 收窄为「选中 / 强调」专用**（保留 `notifications 未读卡`、`feedback 段控件选中项`），语义写入 token 真源注释 | 规则单点可查 |
| ③ 裁决 6A | 新增 **`--radius-2xs: 8rpx`**；`ReviewComposer` 裸 `8rpx` → 归档（**同值零变化**）；`ReportModal` 选项行裸 `12rpx` → `--radius-btn`；通知未读点 `margin-top` 裸 `10rpx` → `--spacing-xs`（8rpx） | 裸 8/12rpx 圆角 **0**、裸 10rpx **0**（仅剩注释说明） |
| ④ 裁决 7A | 通知页删除 `::before` **左侧主色竖条**（与红点语义重复）⇒ 保留右上红点；清理删除时残留的孤立 CSS 片段 | `unread::before` 实际规则 **0** |

| ⑤ 裁决 9B | **`RetryBlock` 扩展**：新增 `loading`（纯 CSS 旋转环 + 副文案切「正在重新加载…」+ 忽略重复点击）、`strong`（整屏居中主失败态语气）、`primaryText` / `secondaryText`（**双 CTA 形态**，块本身自动不可点）、`hint`；既有 4 处消费方行为**零变化**（默认仍是整块可点）。详情页 `.detail-fail` 自绘失败态**并入**该组件（`detailReloading` 驱动转圈；`useDishPage.onRetryDetail` 改为**返回 Promise** 以便等待真实落地）。§4.8 / spec §4.8 / 详情页 UI 文档三处口径同步「用户主动重试可给转圈」 | `RetryBlock` 消费点 **6**；详情页失败态自绘样式全删 |

**闸门**：vue-tsc 0 / build DONE / lint 0 / 主包 **0.41MB** ✅（Round 13 全部完成，无遗留）

**Round 13 改动文件**：`components/CardSection.vue`（新增 flush）、`pages/detail/dish/DishReviewSection.vue`、
`pages/feedback/index.vue`、`pages/notifications/index.vue`、`pages/detail/dish/ReportModal.vue`、
`pages/detail/dish/ReviewComposer.vue`、`theme/tokens.ts`、`App.vue`（补 `--radius-2xs`）。

### Round 14（2026-09-27）—— 卡壳收敛（2B-A）+ **WXSS 选择器红线**

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① 裁决 2B-A | 量化剩余手写卡壳后发现**各自盒模型不同**（padding `md`/`lg`/`sm+md`、margin 各异，`FindResults`/`auth` 甚至无阴影）⇒ 只收敛与卡片档最接近的两处：`my-reviews .profile-strip`（内距 `sm md` → `md`）、`notifications .msg-item`（内距 `lg` → `md`），其余保留（大内距/无阴影是有意版面，清单见候选池） | 两处产物均渲染 `card-section` ✅ |
| ② 消跨边界样式 | `find` 两张卡改传 `flush`，删掉 `:deep(.card-section)` 反向覆写（R4 已判定该机制在小程序端不可靠） | `:deep(.card-section)` 残留 0 ✅ |
| ③ 圆角归档 | `profile .info-card` 由 `--radius-modal`(48rpx) → `--radius-card`(32rpx)（裁决 5A） | 残留 0 ✅ |
| ④ 核实 | `ReviewItem` 两支形态**均有消费方**（`DishReviewSection` 用 `flat`、`my-reviews` 用非 flat）⇒ 不删除；修正其"唯一消费方"过时注释 | 注释已更正 |

> ### 🔴 红线（Round 14 实测教训）：**小程序 WXSS 选择器白名单**
> **白名单只有**：`.class` / `#id` / `element` / `element,element` / `::after` / `::before`（另加 `element element` 后代与 `element > element` 子代在实践中可用）。
> **禁止**：通配符 **`*`**、以及**不要依赖** `+` / `~` 兄弟选择器、属性选择器、`:not()` 等。
> · 实测：`.discover-body > * + *` 触发 `[ WXSS 文件编译错误] error at token '*'`（**只有微信开发者工具会拦，uni 本地 `npm run build:mp-weixin` 不校验**）；
> · 影响面：`.discover-body` 的块间距已改为**卡自身 `margin-bottom`**（零特殊选择器）；
> · **新增闸门（Gate ⑤）**：每次构建后扫描全部产物 wxss，确认无 `*` / `+` / `~` 选择器（当前命中 **0**）。

**闸门**：vue-tsc 0 / build DONE / lint 0 / 主包 **0.41MB** / **WXSS 兼容扫描 0 命中** ✅

**Round 14 改动文件**：`pages/find/index.vue`、`pages/profile/index.vue`、`pages/my-reviews/index.vue`、
`pages/notifications/index.vue`、`components/ReviewItem.vue`（注释）。

### Round 15（2026-09-27）—— `AppTitleBand` 两区化（左区 + 居中区，字号可传参）

| 需求（用户原话） | 实现 |
|---|---|
| 「有两个区域」 | 左区 `.band-left` + 居中区 `.band-title-center`（后者 `position: absolute; left: 50%` ⇒ 相对**整条带**真正居中） |
| 「两个区域的字号可以通过参数控制」 | 新增 **`leftSize` / `centerSize`**（CSS 长度，可传 `'44rpx'` 或 `'var(--font-title)'`）；不传则按有无返回自动取档 |
| 「左侧要和左边缘有 padding」 | `.band-left { padding-left: var(--spacing-md) }`（页面级 gutter 同轴）；⚠️ **带层自身不设左右内距**，否则会平移"绝对居中"的基准 |
| 「无返回时左侧显示页面名称」 | `back=false` ⇒ 左区渲染页面名（默认 `--font-title` 粗体大号） |
| 「有返回时左侧显示"返回"二字」 | `back=true` ⇒ 左区渲染**文字「返回」**（默认 `--font-body` / medium；命中区 ≥88rpx；**替代原箭头 icon**，故移除 `IconSvg` / `COLOR_MAP` 依赖） |
| 「居中区域显示页面名称」 | `back=true` ⇒ 居中区渲染页面名（默认 `--font-h3` / semibold；`max-width: 56%` 不侵入微信原生胶囊） |

- 消费方：`home`（`<AppTitleBand title="知行食记" />`，无返回 ⇒ 左区标题）、`find`（`<AppTitleBand back title="搜索" @back="onBack" />`）。
- **闸门**：vue-tsc 0 / build DONE / lint 0 / 主包 **0.41MB** / 产物两区落盘 ✅ / **WXSS 兼容扫描 0 命中**（Gate ⑤）✅

**Round 15 改动文件**：`components/AppTitleBand.vue`（重写）、`pages/find/index.vue`（补页面名称）。

> ⚠️ 待确认两点（见对话）：① `find` 的页面名暂定「搜索」；② 是否要「‹返回」带小箭头（现为纯文字「返回」）。
> ℹ️ 提示：`AppHeader`（7 个二级页，透明 + 左「返回」文字 + 居中标题 + 右操作槽）已与本组件能力重合，
> 若想**真正单一实现**，可把二级页切到 `AppTitleBand`（需评估 sticky↔fixed 的差异与右操作槽）。

### Round 17（2026-09-27）—— 客户端代码质量专项（清理 / 重复逻辑 / 结构 / 类型 / 边界）

> 承接新任务：**全量客户端代码质量优化**（不限于 UI），约束为「**保持功能不变 + UI 扫除不受影响**」。
> 先由子代理做五维审计（覆盖 86 个源文件），再按「零行为变化」优先执行。

| 批次 | 内容 | 结果 |
|---|---|---|
| **a. 平台取值集中化** | `getWindowInfo → getSystemInfoSync` 兼容回退原先 **4 处各写一份**（`useNavMetrics` / `PageWallpaper` / `home` / `useDishPage`），每处都要 `@ts-ignore` 触碰未声明的全局 `wx` → 新增 **`utils/device.ts`**（`getWindowInfo` / `getMenuButtonRect`，带类型），4 处改为消费 | 消灭 **5 处 `@ts-ignore` + 1 处 `any`+`eslint-disable`**；`wx` 触碰点全仓只剩该文件 |
| **b. 公共展示派生值** | 新增 **`utils/dish.ts`**：`joinLocation`（3 处逐字重复）/ `hasDiscount`（2 处）/ `formatRating`（3 处）；`utils/image.ts` 补 `getThumbImageUrl`（`getImageUrl(getThumbUrl(x))` 原先 2 处各写一份，**顺序与空值守卫不一致** —— C14 类问题根因）；`utils/guest.ts` 补 `deriveGuestLabel`（2 页各写一份且**回退分支不一致**，改为兜底由调用方传入 ⇒ 行为不变） | 9 个消费点收敛；**未合并**的近似逻辑（详情页三段位置 + 「未知位置」、评分 `-` 占位）已在注释登记，防后续误并 |
| **c. 死代码清除** | `useReport.title/placeholder/reportTargetId`；`EmptyState.margin`（prop+样式）；`SectionTitle.extraText`（prop+样式+过时注释）；`tokens.IconColorName`；`user` store 的 `loading` 导出；`useDishPage.myReview` / `currentUserId` 导出；菜品详情页未用 import ×2；**`ReviewItem` 整条死事件链**（`delete`/`report` 事件从未被触发 ⇒ 连带 `isOwn`/`canDelete`/`onDelete`/`onReport`/`currentUserId` prop、`DishReviewSection` 转发事件、`dish/index` 模板绑定） | 净删 ≈120 行 |
| **d. 类型合并** | `find.MixedResult` ↔ `FindResults.MixedResultItem`（逐字段重复）→ 公共 `types/dish.MixedResultItem`；`types/dish.RatingDistribution` 补 `export` 并替换 `DishSummaryCard.RatingDistItem`；`find` 的 `filteredMixed` 冗余别名删除（直接消费源状态） | 2 组重复类型归一 |
| **e. 边界 / 错误处理** | ⚠️ **修复真实缺陷**：`my-reviews` 自 R12-A 改为 `scroll-view`（页面 `height:100vh`）后，页面级 `onReachBottom` **永不触发** ⇒ 触底分页实际失效 → 改由滚动区 `@scrolltolower` 触发；`useReport` 的 `catch (e: any)` 类型安全化；`home` 浮空 Promise 显式 `void` | 见下「踩坑」 |
| **f. 注释对账** | `home` onShow 注释称「store 内自带守卫」与实现不符（`fetchMealTypes` 确无 `loaded` 守卫）→ 修正注释并登记为待评估项 | 闸门④ 通过 |

| **g. 错误处理 / 平台边界统一** | 新增 **`utils/error.ts`**（`errorMessage` / `toastError`）：`catch (e: any) { toast(e?.message \|\| 'xx失败') }` 样板原先在 认证 / 资料 / 我的评价 / 菜品详情 / 评价编辑器 / 图片上传 **6 处各写一份**（口径不一，且 `e` 为 `null` 时会抛未捕获异常）⇒ 全站 `catch (x: any)` **归零**；`utils/device.ts` 增 **`getWxApi()`**：`(globalThis as any).wx` 原先在 `App.vue` / `http.ts`×2 / `upload.ts` / `ImagePicker`×3 各写一份 ⇒ **平台 `wx` 触碰点全仓只剩 `device.ts` 一处** | 类型安全化 + 单一平台边界 |

| **h. 分页样板抽取** | 新增 **`composables/usePagedList.ts`**：`notifications` 与 `my-reviews` 原先各写一套**几乎逐字相同**的分页样板（`loading` 重入守卫 / 第 1 页重拉 / `finished` 到底判定 / 触底 `page += 1` → 去重 concat → 失败 `page -= 1` 回退 / 首屏失败置 `loadFailed`）⇒ 收敛为一份语义，页面只注入差异（游客跳过 / 成功副作用 / 失败日志标签）；模板用的 `list`·`loading`·`loadFailed`·`finished` 名称保持不变 ⇒ **模板零改动** | 两页合计 **−102 行** |
| **i. 请求去重与竞态** | `notifications.load()` 补重入守卫（原先 `onShow` 与重试块可并发两次「第 1 页」并互相覆盖列表）；`stores/dish.fetchMealTypes` 补「已成功 + 在途」双条件去重（字典为静态字典，成功即不再请求；**失败不置位** ⇒ `onShow` 兜底重试语义不变）；`ImagePicker` 上传在途时**不回灌** `props.modelValue`（避免丢掉「已上传完成、尚未随父级值回来」的图） | 3 处重复请求 / 竞态收敛 |

**闸门**：vue-tsc 0 / build DONE / lint 0 / 主包 **0.41MB** / WXSS 兼容扫描 0 / 死代码残留 0（残留计数均为 ActionSheet 合法用法与说明注释）

> ### ⚠️ 踩坑记录（Round 17，**重要教训**）
> **纯 UI 轮次可能引入功能回归，需由代码质量轮兜底覆盖。** 实例：R12-A 为让内容避开透明标题带，
> 把 `my-reviews` 的滚动容器改成 `scroll-view`（页面 `height: 100vh`），但分页仍挂在**页面级** `onReachBottom`
> —— 页面自身已不再滚动，回调永不触发，**触底分页静默失效**：tsc / lint / 构建全绿、UI 完全正常，
> 只有真机「滚到底」才暴露。
> **结论（定为判据）**：把页面滚动容器换成 `scroll-view` / 固定高度布局时，必须同步核对**所有依赖页面滚动的事件**
> （`onReachBottom` / `onPageScroll` / 吸顶 / 回到顶部）。

**Round 17 改动文件（7 次提交）**：`utils/device.ts`（新增）、`utils/dish.ts`（新增）、`utils/error.ts`（新增）、
`composables/usePagedList.ts`（新增）、`utils/image.ts`、`utils/guest.ts`、`utils/useNavMetrics.ts`、
`theme/tokens.ts`、`types/dish.ts`、`App.vue`、`api/{http,upload}.ts`、`stores/{user,dish}.ts`、
`components/{PageWallpaper,EmptyState,SectionTitle,ReviewItem,ImagePicker}.vue`、
`pages/home/{index,DishCard}.vue`、`pages/find/{index,FindResults}.vue`、`pages/my-reviews/index.vue`、
`pages/mine/index.vue`、`pages/notifications/index.vue`、`pages/auth/index.vue`、`pages/profile/index.vue`、
`pages/detail/dish/{index,useDishPage,useReport,DishReviewSection,DishSummaryCard,DishInfoCard,ReviewComposer}.vue`、
`pages/feedback/{index,useFeedback,UpdateForm,ListPickerSheet}.vue`。

### Round 18（2026-09-27）—— 搜索组件视觉口径对齐（`SearchBar`）

> 来源：用户提供豆包设计稿 + 「最终设计口径」（结构不变 / 视觉色值表 / 四态表）。
> **结构（左胶囊 + 右独立按钮）与四态（默认 / 输入中 / 空词禁用 / 提交中）原本已达标** ——
> `SearchBar` 本就是首页（`entry`）与搜索页（`input`）共用组件。本轮只落「不冲突」的视觉项，**全部落 token、零裸值**。

| 项 | 原 | 现 |
|---|---|---|
| 胶囊阴影（"极轻"） | `--shadow-float`（0 6rpx 16rpx / 12% 黑，偏"浮起"、抢输入区） | **`--shadow-card`**（4% 黑）⇒ 与底色只做最小分离，输入区（白底 + 文字）成为视觉主体 |
| 占位文字 / 放大镜 / 清除图标色 | `--text-tertiary`（#7F6A55） | **`--text-placeholder`**（#B5A594）⇒ 该 token **首次被消费**（原为零引用） |
| 输入文字 | `--text-primary`（#2D1F14） | 不变 —— 「输入中文字加深」由它与占位色的**色差**直接表达 |
| 空词禁用态 | 仅 `opacity: 0.5`（暖底上仍像"橙色半透明"，与"主色未点"易混） | **中性灰底 `--bg-input` + 灰字 `--text-tertiary`** ⇒「不可执行」一眼可辨（skill §8 submit-feedback） |
| 结构 / 胶囊底色 / 圆角 / 按钮主色 / 提交中态 | — | **均不变**：左胶囊 + 右按钮；`--bg-card` / `--radius-pill` / `--color-primary-fill` / `opacity: 0.6` 已完成原口径 |

**闸门**：vue-tsc 0 / build DONE / 主包 **0.41MB**

> ### 🚨 事故与修复（Round 18，**流程教训**）
> 本轮提交时 `git add -A` **一并收进了一处来源不明的目录移动**：`src/pages/detail/dish/*` → `src/pages/dish/*`
> （9 个文件，内容 100% 未变）。该移动**未同步 `pages.json` 的分包声明**（仍为 root `pages/detail/` + page `dish/index`）
> ⇒ **菜品详情页从构建产物中整体消失**（`dist/build/mp-weixin/pages/detail/dish/index.js` 不存在），
> 而 `vue-tsc` 与 `vite build` **都不会报错**（构建照样输出 DONE），主包体积反而「变小」（0.41MB → 0.37MB）——
> 这正是页面被丢弃的症状。
> **修复**：9 个文件已移回 `src/pages/detail/dish/`；复验 `pages/detail/dish/index.js` 存在、`pages/dish/index.js` 不存在、主包回到 0.41MB。
> **结论（定为流程判据）**：
> ① 提交前必须审 `git status` 里**非本轮的 rename / delete**（尤其 "100% rename"）；
> ② **页面级改动后要核对 `dist/build/mp-weixin/pages/**` 中对应产物是否存在** —— 分包路径错配不会让构建失败。

> ### ⚠️ 待拍板（挂起，未擅改）
> **① 按钮底色 `#E67E22` 与本项目无障碍取色边界冲突**：代码内已实测记录「白字 on `#E67E22` = **2.85:1**，不达 WCAG AA 4.5:1」，
> 故现按钮用 `--color-primary-fill`（白字 **5.01:1** ✅，视觉同为暖橙，仅明度略深）。可选：
> **A** 保持现状（全站主色一致 + 达标）；**B** 改 `--color-primary-orange`（#E67E22）+ 按钮文字改深色（`--text-primary` on #E67E22 ≈ **5.6:1** ✅），
> 但与你给的"按钮文字 白色"口径冲突（白字在该底色下**不可达标**，即便按大字 3:1 也不够）。
> **② 豆包稿胶囊右侧的 `⌄`**：现实现为「有输入才出现的 ✕ 清除」；若意图是「搜索历史 / 热词下拉入口」属**新功能**，需单独规格（存哪、点击行为、空态）。
> **③ 首页 vs 搜索页观感差异的根因**：两页共用同一组件，差异来自**胶囊周围底色** —— 首页搜索行坐在吸顶容器的暖底切片上
> （切片有功能职责：挡卡片从透明标题带背后滚过），搜索页直接坐在壁纸上。可选：保持（差异小、各自成立）／给搜索页也套同款切片（观感一致，多一层拷贝）。

### Round 19（2026-09-27）—— 菜品详情去「综合评分」独立卡（评分并入信息卡一行）

> 来源：用户判断「业务上无需为综合评分单独设板块」，拍板 **A 案**（删卡 + 均分与人数进信息卡 + 分布降级为一行迷你条）。
> 用户原话的落点建议是 hero；实施时按 UI 判断改为**信息卡内评分行**（hero 是图片轮播，叠字需蒙版且会随滚动被裁掉；
> 信息卡已有名称 / 价格行，评分紧邻更省版面，也与 Round 18「贴图只做氛围」一致）。

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① UI | 信息卡由四段变**五段**：① 名称 + 价格 → **①b 评分行（`★ 均分` + `N 人评` + 一行迷你分布条）** → ② 位置 → ③ 描述 → ④ 四维。分布条 5 段按条数比例分宽（`flexGrow = count`），5★→1★ 由**透明度**分档（0.3~1.0，星色恒为 `--color-star`，零裸值） | 少一块版面、少一次信息重复 |
| ② 前端 | 删 `DishSummaryCard.vue`（页内私有、零消费即删）+ 页面调用与 import + 连带注释 6 处（`index` / `useDishPage` / `DishReviewSection` / `EmptyState` / `utils/dish`） | 同页**三卡 → 两卡** |
| ③ 口径 | **同源同刻不放松**：均分 / 人数 / 分布三者仍统一取实时聚合；零评价退化为轻文案「**暂无评分**」（不是 `0.0`，也不占位成块） | 不再有 `-` 占位与整块空态 |
| ④ 文档 | `docs/ui/client-菜品详情.md`（**唯一真源**：信息卡序列 / 评分行 / 组件清单重新编号 / 数据映射）+ `docs/feature/client-菜品详情.md`（`ratingDistribution` 用途、零评价表述、介绍段） | 闸门④ 通过 |

**闸门**：vue-tsc 0 / build DONE / 主包 0.41MB / 产物核对（详情页产物存在 + `rate-bar` 样式已落盘）/ `DishSummaryCard` 残留 0（仅说明性注释）

**Round 19 改动文件**：`pages/detail/dish/{DishInfoCard.vue,index.vue,DishReviewSection.vue,useDishPage.ts}`、
`DishSummaryCard.vue`（**删除**）、`components/EmptyState.vue`、`utils/dish.ts`、`docs/{ui,feature}/client-菜品详情.md`。

### Round 20（2026-09-27）—— 搜索栏结构升级（单胶囊）+ `IconSvg` 契约修复

> 来源：用户提供新设计稿（单胶囊、内嵌搜索按钮、**不要下拉箭头**）并指出图标未垂直居中。
> **结构级变更**（与此前「左胶囊 + 右独立按钮」口径相反，用户拍板）。

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① 结构 | `SearchBar` 由「左胶囊 + 右独立按钮」改为**单个搜索胶囊**：① 放大镜 40rpx（左侧垂直居中）② 输入 / 占位 ③ 有值时清除 ✕ ④ **右端内嵌「搜索」按钮**（`align-self: stretch` 等高、四周留 `--spacing-xs`；`@tap.stop` 防 entry 双跳）；**不加下拉箭头** | 对外 API（6 props / 4 emits）**一字未变** ⇒ 首页 / 搜索页调用点零改动 |
| ② 高度 | 新增 token **`--search-bar-height: 96rpx`**（≈48px，≥ Apple 44pt），不再与微信原生胶囊等高 ⇒ 旧 `::after` 热区扩展删除；`useNavMetrics.capsuleHeightPx` / `navMetrics.getCapsuleHeight` 随之**零消费移除** | 首页吸顶容器 100 → ≈116rpx（首屏网格起排 419 → ≈435px，仍安全） |
| ③ IconSvg | **两处真缺陷修复**：① 宿主补 `flex: none`（flex 行里不再被压缩 —— "size 传了却变小"的根因）+ `vertical-align: middle`；② `color` 契约明确：`var()`/`currentColor` 因 SVG data-uri 是独立文档而无法解析/继承 ⇒ 统一回退兜底色 + `var()` 误用 DEV 告警 | 全站 41 处消费受益 |
| ④ 居中（20b） | **根因（产物实证）**：`IconSvg` 编译为自定义组件（无 virtualHost）⇒ flex 子项是**宿主节点**，内部图标按「文本行盒 + 基线」排布 ⇒ 垂直偏移。修复：`.search-bar-icon` 宿主显式 40rpx flex 盒；`search` 图形 **r=7→8**（含描边包围盒 2→22，几何中心回到网格正中；旧版偏右下） | 搜索图标精确居中 |

**闸门**：vue-tsc 0 / build DONE / 主包 0.41MB / 首页与搜索页调用点零改动

**用户已确认（4 项）**：高度 96rpx ✅ / 内嵌按钮宽度 ✅ / 清除 ✕ 保留 ✅ / entry 模式整条胶囊与内嵌按钮同为入口 ✅。

> ### Round 20c（同日）—— `virtualHost` 实验失败回退 + 占位色拍板
> **① 占位色**：用户拍板 **A** —— 两页均用 `--text-placeholder`（`#B5A594`，与设计稿一致，维持现状零改码）。⚠️ 知悉：对白 2.39:1 不达 AA；首页文档 §8 偏差登记 #1 已随之失效，待 patch 首页文档时一并改写。
> **② `virtualHost`**：**实验失败，已回退** —— 两种写法均不生效：`defineOptions({ options })` 不写入产物 json；显式 `<script>` 块会使 SFC 降级、触发 `@/` 别名解析失败（TS2307）。⇒ 维持「消费方 class 把宿主定为 flex 盒」的确定性方案（`SearchBar .search-bar-icon`，产物已核对）；`IconSvg` 头注释已登记实验结论，**勿盲目重试**，待 uni-app 升级支持后再评估。
> **③ 首页文档 ~10 处失效**：仍挂起（另一会话编辑中）。

**Round 20 改动文件**：`components/{SearchBar,IconSvg}.vue`、`App.vue`（`--search-bar-height`）、
`utils/{useNavMetrics,navMetrics}.ts`（零消费移除）、`docs/ui/client-搜索.md`（结构 / 失效 token 修正）。

### Round 21（2026-09-27）—— 搜索结果卡抽出为页内组件 + 按用户规格重设计

> 来源：用户问「为什么不抽出作为页面组件」并给出完整新规格。**事实澄清**：该卡曾是独立组件 `DishResultRow`，
> 在更早的 find-result-card-polish 轮被合并回 `FindResults` 内联（当时的单文件内聚取舍）；本轮按新规格**重新抽出**。

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① 抽出 | 新建页内私有 **`pages/find/DishResultRow.vue`**（就近组织，与 `feedback/ListPickerSheet` 同款）；`FindResults` 收敛为列表编排 + 单结果居中（`splitHighlight` / `loadedSet` / 行样式随卡内聚） | `FindResults` 净 −60 行 |
| ② 重设计 | 横向 flex：左 **160rpx 正方形图**（`aspectFill`，无图 → `dish` 餐具占位）+ 右**纵向三行信息**（`align-self:stretch` 与左图等高对齐、内容垂直居中，行距 `--spacing-sm`）：① 标题行（菜名两行省略、命中**加粗**；★评分紧贴菜名后方，无评分不渲染不占位）② 价格行（**整行靠右**；仅 `originalPrice > price` 追加灰色删除线原价）③ 位置行（靠左、单行省略）；卡片内边距**统一 `--spacing-md`**；按压反馈 `bg-soft` | 完全按用户规格 |
| ③ 契约 | `id` 仅 key / 跳转零渲染；**不渲染**标签、描述、评价数等详情页字段；命中只**加粗**不上主色（主色是价格专用强调色） | 与取色边界一致 |
| ④ 文档 | `docs/ui/client-搜索.md`：新增「结果行布局」规格块（§2 后）+ 组件清单登记 `DishResultRow` | 闸门④ 通过 |

**一处规格歧义及处理**：规格同时写了「菜名 `flex:1`」与「评分紧贴菜名后方、不做 space-between 拉开到右端」——二者互斥（`flex:1` 必然把评分推到右端）；按**后者**（显式禁令）实现：菜名 `flex: 0 1 auto` 弹性收缩、评分紧随其后同行左排。若你本意是「菜名占满、评分靠右」，回我一句即改。

**闸门**：vue-tsc 0 / build DONE / 主包 0.41MB / 产物核对（`DishResultRow` 产物 + 新样式落盘 + `FindResults` 行样式残留 0）

**Round 21 改动文件**：`pages/find/DishResultRow.vue`（新增）、`pages/find/FindResults.vue`、`docs/ui/client-搜索.md`。

**Round 21b（同日，用户指示）**：① 组件更名 `DishResultRow` → **`DishResultCard`**；② **`FindResults` 并入 `find/index` 并删除** —— 抽出结果卡后其职责只剩「滚动容器 + 列表编排 + props 透传」，单独成件无意义；③ 卡间纵向间距由列表容器 **flex gap** 承担（替换 `.row + .row` 兄弟选择器 —— mp-weixin WXSS 不保证支持，本项目早有登记）；④ `select` 事件携带 id 载荷（判空留在卡内，模板零断言）。产物核对：`FindResults.vue` 与其产物均已消失、`DishResultCard` 产物存在、主包 **0.40MB**。

**Round 21c（同日，用户指示）**：结果卡信息区**重新布局** —— ① 菜名（两行省略、命中加粗）② 档口位置（`--font-aux` 弱灰、单行省略、命中加粗）③ **底行「左评分 / 右价格」**，且 ★ 与数字**与价格同字号**（`--font-h3` 36rpx；星画布 36rpx）；无评分时评分组不渲染、价格仍靠右（`margin-left:auto`）。文档「结果行布局」已同步。

**Round 21d（同日）**：结果卡星图标与数字垂直对齐修复 —— 根因同 Round 20b（`IconSvg` 宿主节点行盒/基线偏移）；星图标加 `.rating-star` 宿主 flex 盒（36rpx 方形）。

**Round 21e（同日，用户拍板）**：结果态**顶部对齐** —— **推翻「单条结果垂直居中」规则**（`.mixed-list.single` 已移除）。理由：搜索结果的自然阅读顺序自上而下，垂直居中会打断该顺序。原规则针对的「卡片悬顶、下方大片空白读作没加载完」改由「搜索行常驻 + 四态互斥」的存在感缓解；`docs/ui/client-搜索.md` §4 状态表与硬性约束 #10 已同步改写。

### Round 22（2026-09-27）—— 菜品信息卡按用户规格重排（全卡单分隔线 / 属性标签容器 / 去分布条与人数）

> 来源：用户给出完整规格（含 ASCII 排版参考）。核心变化：**全卡只保留 1 条分隔线**、四维改为「属性标签容器」、
> **移除 Round 19 引入的迷你评分分布条与「N 人评」**。

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① 结构 | ① 名称 + 价格组 → ② 评分（左）/ 位置（右）**同行** → ③ 简介（空则整块隐藏）→ ④ **唯一一条浅灰分隔线**（仅简介存在时渲染）→ ⑤ **属性标签容器**（浅米底 `--bg-page` + 4 列均分 + 每列「标签在上、值在下」+ 右上角纯文字「信息有误？›」） | 与用户 ASCII 参考一致 |
| ② 间距 | 模块垂直间距统一 `--spacing-md`；简介 ↔ 分隔线 `--spacing-sm`；**除第 ④ 条外零分割线**（模块靠垂直留白区分） | 推翻旧 `.card-block { border-top }` 每块一条线的做法 |
| ③ 减法 | **移除评分分布条与「N 人评」** ⇒ `DishInfoCard.distribution` prop、页面传参、`useDishPage.ratingDistribution` 派生值按「零消费即删」全部移除；接口字段 `ratingDistribution` 保留在契约中但端上不消费 | 用户禁止项 #3 / #4 |
| ④ 交互 | 展开 / 信息有误？**只用文字配色**（禁止实色填充按钮）；纠错入口从位置行**移到属性容器右上角**；展开与纠错保留 ≥88rpx 触达；星图标加宿主 flex 盒（同 20b/21d 方案） | 与贡献入口红线一致 |
| ⑤ 文档 | `docs/ui/client-菜品详情.md`（信息卡序列 / 评分口径 / 四维容器 / 组件清单 / 数据映射）+ `docs/feature/client-菜品详情.md`（介绍段 / `ratingCount` / `ratingDistribution` 标注） | 闸门④ 通过 |

**闸门**：vue-tsc 0 / build DONE / 主包 0.40MB / 产物核对（`divider`·`attrs`·`dim-label` 落盘、旧 `card-block`·`rate-bar` 残留 0）

**Round 22 改动文件**：`pages/detail/dish/{DishInfoCard.vue,index.vue,useDishPage.ts}`、`docs/{ui,feature}/client-菜品详情.md`。

### Round 23（2026-09-27）—— 菜品信息卡属性块**还原** + 纠错入口迁入写评价抽屉

> 来源：用户指示（Round 22 的**部分回退**）。两条：① 四维内容「还原到原来的样式」；
> ② 卡片右上「信息有误？›」**从信息卡移除**，改由写评价抽屉（`ReviewComposer`）承载。

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① 样式还原 | 四维块从 Round 22 的「浅米色底标签容器（`--bg-page` + 4 列 + 标签在上 / 值在下）」**逐字还原为 Round 22 之前**的样式：`class="attrs"` → `class="dims"`，**无底色 / 无边框 / 无内边距**、4 列等分居中、**值在上（`--font-subtitle` 500 档）、标签在下（`--font-aux` 三级灰）**、标签贴底对齐（`justify-content: flex-end`，长值换行时各列标签同基线） | 排版回到 R22 之前；产物核对 `dim-val` 先于 `dim-label` ✅ |
| ② 间距自持 | 分隔线去掉下侧留白（`.divider` margin 由 `sm 0 md` → `sm 0 0`），**`--spacing-md` 间距改由 `.dims` 自持** ⇒ 简介缺失（分隔线同步消失）时留白仍然成立（R22 是借分隔线的下外边距，存在「无简介即少一段留白」的隐患） | 用户规格「模块垂直留白 md」全场景成立 |
| ③ 纠错入口迁移 | 信息卡内 `correct-link` 整块 + `goCorrect()` + `feedbackUrl` 导入全部移除（零残留）；入口落到 `ReviewComposer` **菜名行右侧**（`.rc-head-row` = 左菜名省略 + 右「信息有误？›」），复用同款视觉（三级灰纯文字 + `arrow`、`::after` 扩至 88rpx、按压 opacity），行为 = **先关抽屉、再跳**反馈页 `update` 模式并预选本菜品（`feedbackUrl('update', dishId)`） | 入口可达性不降级；卡片达成「卡内零交互文字，仅展开 / 收起」 |
| ④ 为何不落 `BaseSheet` 头部 | 用户引用的是抽屉头部（`:title`）区间，但 `BaseSheet` 头部只有「左标题 + 右 X」、**无插槽** —— 为一条入口改动**全体弹层共享的骨架**风险不成比例，故落在内容区首行（菜名即「这条菜品信息」的主语），语义最近、改动局部 | 共享骨架零改动 |
| ⑤ 文档 | `docs/ui/client-菜品详情.md`：信息卡条目顺序 / 四维容器呈现 / 底栏写评价段（新增纠错入口口径）/ 组件清单（`DishInfoCard`·`ReviewComposer`）/ 数据表 #7 位置行，共 6 处同步 | 闸门④ 通过 |

**闸门**：vue-tsc 0 / build DONE / lint 0 / 主包 0.40MB / 卡片内 `correct-link` 0 · 抽屉 `rc-correct` 落盘 ✅

> ⚠️ **待权威文档同步（不由本循环代改）**：`project_spec.md` §UGC 反馈类第 4 条仍写「菜品详情页**信息卡**「信息有误？」入口」，
> 与 Round 23 后的实现（入口在写评价抽屉内）不一致 —— 按角色分工 `project_spec.md` 只由**技术负责人**修改，此处登记待其同步。

**Round 23 改动文件**：`pages/detail/dish/{DishInfoCard.vue,ReviewComposer.vue}`、`docs/ui/client-菜品详情.md`。

### Round 24（2026-09-27）—— 评价区块头部层级重建（两段式胶囊筛选）+ 条目排版收紧

> 来源：用户给出「评价卡片（ReviewSection）重新设计方案」。核心：**头部信息层级混乱 + 筛选控件语义不清** ——
> ① 评价数字是灰字附属、且随筛选变形；② 自定义 switch + 文字挤在一起，既不像系统开关也不像筛选 tab。

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① 标题块合并 | `SectionTitle` 新增 **`count?: number \| null`**：渲染为「标题 + N」在**左侧标题块内**（`.section-head` 承担增长位，保证数字紧跟标题不跑到右端）；数字**与标题同色**（SHALL NOT 灰字）、小半号（h2 40 → h3 36rpx）、等宽数字、`baseline` 对齐；传 `null` 不渲染 ⇒ 在途 / 失败态**不显示数字** | 扫一眼即知「这道菜几条评价」 |
| ② 口径收敛 | 删除 `totalLabel` 与「有图 N」变体：**数字恒为 total**（随筛选变形的数字会让总数读作跳变）；`hasImage` 只在请求层表达筛选 | 与用户规格一致 |
| ③ 筛选控件 | 自定义 switch → **两段式胶囊「全部 / 有图」**：槽 `--bg-soft` + 选中项**白底 / 深色字 / `--shadow-card` 浮起**、未选中透明底 + 次级灰字；`role="tablist"` / `role="tab"` + `aria-selected`；**仅点击未选中项才上抛**（重复点不触发无谓重拉）；命中区经 `::after` **仅纵向**扩至 88rpx（横向不扩 ⇒ 左右两项热区不互相窃取） | **语义依据**：「只看有图」是视图过滤（看完即切回），不是常驻偏好 ⇒ tab 比 switch 准确 |
| ④ 条目排版 | `ReviewItem--flat` 去掉 `border-bottom` 与上下 padding ⇒ 条目间由列表容器 **`gap: var(--spacing-lg)` 纯留白**分隔（用户口径「不加分割线」）；时间 `formatDateTime` → 新增 **`utils/time.formatDate`（仅 `YYYY-MM-DD`）** —— 菜品评价时效性弱，第二行同时容纳「星级 + 分值 + 时间」需降噪 | `ReviewItem` 全站口径统一（含「我的主页」复用） |
| ⑤ 空态文案 | 「有图」无结果 → 副文案**「切换到「全部」查看所有评价」**（入口由开关变胶囊，文案随之改口径）；零评价 → 副文案**「你的第一条评价，能帮同学避雷」** | 与用户规格逐字一致 |
| ⑥ 文档 | `docs/ui/client-菜品详情.md`（评价区标题行 / 条目结构 / 无障碍 / 组件清单 ×2 / 数据表 #16·#20 / 三态文案 / 入参 / 控件类型 = 8 处）、`docs/ui/client-我的主页.md`（时间口径）、`docs/feature/client-菜品详情.md`（筛选形态注记） | 闸门④ 通过 |

**闸门**：vue-tsc 0 / build DONE / lint 0 / 主包 **0.40MB** / 旧实现残留 0（`totalLabel`·`review-count`·`review-head-right`·switch 样式全清；`image-only` 残留 4 处均为**事件名/prop 名**，有意保留）

> #### ⚠️ 两处「规格里有、项目里没有」的取舍（已按红线处理，如要改一句话即可）
> 1. **`--radius-sm` 不存在**：本仓半径档位只有 `--radius-2xs 8rpx` / `--radius-icon 24rpx` / `--radius-card 32rpx` / `--radius-pill`。
>    评价配图沿用 **`--radius-card`**（与**同页** `ImagePicker` 同一网格语言）；若要更「方」可换 `--radius-2xs`。
> 2. **星级仍为「只画实心星、不补空槽」**：设计稿 ASCII 出现 `★★★☆☆`（5 槽式），但文案只写「黄色实心五星」；
>    项目既有口径是「最低渲染 1 颗星、不补空槽」⇒ 本轮**不改**，若需 5 槽式（`star` 空星占位）说一声即可。

**Round 24 改动文件**：`components/{SectionTitle,ReviewItem}.vue`、`pages/detail/dish/{DishReviewSection.vue,useDishPage.ts}`、`utils/time.ts`、`docs/ui/client-{菜品详情,我的主页}.md`、`docs/feature/client-菜品详情.md`。

### Round 25（2026-09-27）—— 纠错入口落定底栏「反馈错误」（替换「去分享」）+ 入口唯一化

> 来源：用户追问「**为什么底部还是去分享而不是已经换成「反馈错误」**」。
> 复盘：R23 的指令引用的是 `@ReviewComposer.vue:8-15`，我按行号字面把入口放进了**写评价抽屉**（菜名行右侧）
> —— 真实意图是**底栏右钮替换**。R25 按真实意图落定，并把抽屉里那个撤掉（同一纠错表单不留两个入口）。

| 步骤 | 内容 | 结果 |
|---|---|---|
| ① 底栏换钮 | `.bar-btn--share`（`open-type="share"`「去分享」）→ **`.bar-btn--correct`「反馈错误」**；点击跳反馈页「更新信息」模式并**预选本菜品** | 与用户预期一致 |
| ② 跳转归属 | 新增 `useDishPage.onCorrectDishInfo()`（页面编排统一持有导航，落点仍为唯一构造函数 `feedbackUrl('update', dishId)`）；**免认证** —— `POST /dishes/{id}/correction` 属公开写，游客可直达（不经 `requireAuth`） | 编排层单一职责 |
| ③ 入口唯一化 | 撤销 R23 放进写评价抽屉的「信息有误？›」：模板块 + `.rc-head-row`/`.rc-correct*` 样式 + `goCorrect()` + `feedbackUrl` 导入 + 组件头注释**全部还原**（零残留） | 全页纠错入口 = 1（底栏） |
| ④ 分享不弱化 | `onShareAppMessage` **保留**：分享改由**微信右上角原生菜单**承担（与 R22 口径「卡片内不做分享按钮」一致）—— 底栏这一位让给纠错 | 能力不减，只是换入口 |
| ⑤ 文档 | `docs/ui/client-菜品详情.md`（信息卡段 / 底栏段 / 组件清单 #4 / 控件类型）、`docs/ui/client-意见反馈.md` + `docs/feature/client-意见反馈.md`（入口描述）、`client/src/utils/routes.ts` + `feedback/index.vue` + `DishInfoCard.vue` 注释 | 闸门④ 通过 |

**闸门**：vue-tsc 0 / build DONE / lint 0 / 主包 **0.40MB** / 产物：底栏含「反馈错误」·**不含「去分享」**·`bar-btn--correct` 落盘；抽屉与信息卡内「信息有误」= 0

> #### ⚠️ 教训（判据）：用户给「文件 + 行号」时，先回述**界面落点**再动手
> 同一个「纠错入口」，落点可在 **信息卡 / 写评价抽屉 / 底栏** 三处；R23 我按引用行号字面执行（落到抽屉），
> R25 才对上真实意图（底栏换钮）。**判据**：凡「入口迁移 / 入口替换」类需求，动手前先一句话回述
> 「入口最终出现在哪个界面位置、原位置那个元素怎么处理」，避免按行号误读。

> ⚠️ **待权威文档同步（不由本循环代改）**：`project_spec.md` §UGC 反馈类第 4 条仍写「菜品详情页**信息卡**「信息有误？」入口」，
> 与现状（**底栏「反馈错误」**，Round 25）不一致 —— 按角色分工该文件只由**技术负责人**修改，此处登记待其同步。

**Round 25 改动文件**：`pages/detail/dish/{index.vue,useDishPage.ts,ReviewComposer.vue,DishInfoCard.vue}`、`utils/routes.ts`、`pages/feedback/index.vue`、`docs/ui/client-{菜品详情,意见反馈}.md`、`docs/feature/client-意见反馈.md`。

### Round 26（2026-09-27）—— 全站滚动区域尺寸复核（短内容不产生滚动条 / 无空白可滚区）

> 来源：用户要求「检测所有具有滚动区域的页面，重新判定内容区域的尺寸计算方式；确保内容未超过可视高时
> 不激活或显示滚动条，并防止多余滚动机制导致的空白滚动区域与设备兼容问题」。

**审计范围**：11 个页面壳 + 2 个弹层滚动区（共 13 处）。判定四要素 = ① 页根是否**显式定高**；
② 滚动容器 `flex: 1`；③ 容器 `min-height: 0`；④ 是否存在**多余滚动机制**（冗余 `overflow` / 非 `scroll-view` 自滚）。

| 页面 | 页根高度（修复前） | 滚动容器 | 修复前问题 |
|---|---|---|---|
| home | 仅靠全局 `.page` 兜底 | `.scroll-wrap`（flex+min0 ✓） | 依赖全局兜底、无显式定高 |
| mine | `100vh` | `.mine-content.mine-scroll`（flex+min0 ✓） | 无 dvh；**容器 + 页脚双重让出 tabbar** ⇒ 末尾 ≈100rpx 死空白 |
| my-reviews | `100vh` | `.scroll-wrap` ✓ | 无 dvh |
| notifications | `100vh + 100dvh` | `.scroll-wrap`（**多余 `overflow-y`**） | dvh 被全局 min-height 顶回；冗余 overflow |
| auth | `100vh + 100dvh` | `.scroll-wrap`（**多余 `overflow-y`**） | 同上 |
| feedback | `100vh + 100dvh` | `.scroll-wrap`（**多余 `overflow-y`**） | 同上 |
| privacy/DocPage | `100vh + 100dvh` | `.scroll-wrap`（**多余 `overflow-y`**） | 同上 |
| profile | `100vh` | `.scroll-wrap`（**缺 `min-height: 0`** + 多余 `overflow-y`） | **flex 子项不收缩 ⇒ 与页根双层滚动** |
| find | `100vh` | `.discover-body`（**`<view>` 自滚**）/ `.results-host` ✓ | view 自滚设备兼容差（内容超高会被 `overflow:hidden` 页根裁掉不可达） |
| detail/dish | `100vh` | `.dish-scroll` ✓ | 无 dvh |
| BaseSheet | `max-height: 88vh` | `.bs-body--scroll` ✓ | 无 dvh |
| ListPickerSheet | `height: 70vh / max 82vh` | `.lp-list` ✓ | 无 dvh |

**修复（5 类）**
1. **全局 `.page` 不再覆写页根的 dvh**：`min-height: 100vh` → **`100vh; 100dvh` 双声明**。修复前
   `min-height: 100vh` > 页根 `height: 100dvh` ⇒ **min 获胜 ⇒ dvh 修复静默失效** ⇒ 移动端 H5 地址栏伸缩时
   页根比可视区高 ⇒ 页面自身多出一段可滚区 + 底部露白（本轮最关键的根因）。
2. **删除全局 `.scroll-wrap` 的 tabbar 兜底 padding**：只有 home / mine（自绘 TabBar）需要让出菜单栏，且均已自行声明
   （home = 页根 `padding-bottom`、mine = 页脚）⇒ 全局兜底只会让**非 Tab 页**凭空多出 ≈50px + 安全区(≈34px) 死留白，
   短内容还会被这层 padding **顶出滚动条**。保留 `min-height: 0` 作为通用兜底。
3. **补齐/统一滚动容器三要素**：`profile` 补 `min-height: 0`（flex 子项默认 `min-height: auto` ⇒ 不收缩 ⇒ 双层滚动）；
   `home` 页根改为**显式** `height: 100vh; height: 100dvh`；`mine / my-reviews / profile / find / detail·dish` 补 `dvh`。
4. **清理多余滚动机制**：删除 5 处 `scroll-view` 上的 `overflow-y: auto`（滚动由组件内部实现，外挂 CSS 在 H5 会叠出
   **第二根滚动条**）；`find` 发现态 `.discover-body` 由 `<view>` 改为 **`scroll-view`**（与结果态同款容器）。
5. **弹层同口径**：`BaseSheet` `max-height: 88vh → +88dvh`；`ListPickerSheet` `.lp-wrap` `height/max-height` 补 dvh
   ⇒ 弹层不会超出（H5 地址栏伸缩时）真实可视高。

**判定口径（此后新增页面照此自检）**
| 要素 | 口径 |
|---|---|
| 页根 | `display: flex; flex-direction: column;` + **`height: 100vh; height: 100dvh;`**（显式；`box-sizing: border-box` 由全局重置保证） |
| 滚动容器 | **`scroll-view`**（不使用 `<view>` + `overflow` 自滚）+ `flex: 1; min-height: 0;` |
| overflow | `scroll-view` 上 **SHALL NOT** 写 `overflow-y`（H5 会叠出第二根滚动条） |
| 底部留白 | **谁需要谁声明**：TabBar 页（home/mine）自行让出；固定底栏页（profile/detail·dish）让出 `--action-bar-height`；其余页只留视觉呼吸留白 —— **不再有全局兜底** |
| 短内容 | 上述即保证「内容 ≤ 容器高 ⇒ 不可滚动、不出现滚动条、无空白可滚区」 |

**闸门**：vue-tsc 0 / build DONE / lint 0 / 主包 **0.40MB** / 产物核对：`app.wxss` 的 `.page` 双声明 ✅、
全局 `.scroll-wrap` 已无 tabbar padding ✅、10 个页面 wxss `dvh` ✅ 且 `overflow-y:auto` = 0 ✅、
`find` 发现态落 `scroll-view` ✅

> #### ⚠️ 遗留观察项（未改，等拍板）
> **iOS 回弹（`bounces`）**：`scroll-view` 在 iOS 上默认允许橡皮筋回弹 —— 内容短时也能拖出一段空白（松手自动回位）。
> 这是 iOS 原生交互语言，本轮**未**关闭（关闭会牺牲长列表的手感）。若要「短内容也完全不可拖」，
> 给各滚动区加 `:bounces="false"` 即可（一句话，我来加）。

**Round 26b（同日，用户反馈「搜索页卡片间距和左右间距有问题」）**：
`find` 发现态两张分组卡传了 `flush`（卡壳**全部**外边距归零，含左右），R14 只补回纵向 `.discover-card { margin-bottom }`
⇒ **卡片左右贴屏幕边**（结果态是 `.mixed-list` 的 24rpx gutter）⇒ 已补 `margin: 0 var(--spacing-md) var(--spacing-lg)`
（UI 文档 §2「左右 gutter = 24rpx，与首页同轴」+ 纵向块间 32rpx）。
> 排查中另发现 **文档滞后于实现**（非代码缺陷）：`docs/ui/client-搜索.md` 间距表仍写「结果卡内边距 = 上下 md / 左右 lg」，
> 而 Round 21 已**有意**规格化为「四边统一 md」（代码注释为证）⇒ **保留实现、更正文档**；同时把「分组卡左右 gutter」
> 与「分组卡内 chips 的实际 inset（卡 gutter + 卡内距 = 48rpx，与全站卡片正文同轴）」补进表内，消除歧义。

**Round 27（同日，用户报缺陷）** —— 搜索页「点 X 回发现态看不到搜索记录，返回首页重进才显示」。
两条根因都已修（互为兜底）：
1. **`scroll-view` 被重建 ⇒ 测量为 0 ⇒ 发现态整块不可见**（最贴合现象）：原实现两态都用 `v-if`，
   点「清空 X」时发现态的 `scroll-view` 被**销毁再新建**；小程序下新实例的测量可能早于父级布局完成
   ⇒ 高度按 0 计 ⇒ 内容不可见，直到重新挂载（返回首页重进）才恢复。
   ⇒ 发现态容器改 **`v-show`**（编译为 `hidden="{{!inFilter}}"`，等价 display 切换）**常驻复用**已测量实例；
   结果态仍用 `v-if`（其数据到达后会再渲染一次，天然规避该时序）。
   *（该容器由 `<view>` 改为 scroll-view 发生在 Round 26 ⇒ 本缺陷随之显性化。）*
2. **内存副本滞后于存储**：历史原先只在 `onMounted` 读一次，而本页会被页面栈缓存（返回再进不重新挂载）
   ⇒ 以存储为唯一真源，在 **`exitFilter()`（退出结果态）** 与 **`onShow`** 两处补 `loadHistory()`；
   并让读取异常时**保留内存副本**（原先 `catch` 会把列表清空 —— 多次调用后风险被放大）。

### Round 27c（同日，用户第二次指出「搜索页左右间距仍然是 0」）—— 找到真因并修正

> **教训（前两版都改错了地方）**：我先前把间距写成「页面 scoped 类挂在 `CardSection` 上」
> （`.discover-card { margin: … }` + `<CardSection class="discover-card" flush>`），编译产物看似正确
> （`.discover-card.data-v-x{margin:…}` 与 `card-section class="discover-card data-v-x"` 都在），
> **但小程序的组件宿主节点默认不是块级盒 ⇒ `margin` 被静默忽略**（横向纵向一起丢）——
> 表现就是「卡片左右贴屏幕边 + 两张卡相贴」，即用户连报两次的现象。

**修复**：间距改由**页面自己的节点**承担 —— 外层 `<view class="discover-card">` 包裹 `<CardSection flush>`；
`.discover-card { display: block; margin: 0 var(--spacing-md) var(--spacing-lg); }`。
产物核对：`<view class="discover-card" …>` ✅、组件宿主承载该类 **0 处** ✅、规则 `display:block;margin:…` ✅。

> #### ⚠️ 判据（新增，写进本项目布局红线）
> **布局属性（`margin` / `width` / `height` 等）SHALL NOT 只声明在「传给自定义组件的 class」上** ——
> 小程序端该类落在**组件宿主节点**，其盒模型不受页面常规假设约束，属性可能被静默忽略
> （`display` 类声明因会改变盒类型而"看起来有效"，更易掩盖问题）。
> 需要外部间距 / 尺寸时：**用外层自有 `<view>` 承担**（或经内联 `:style` 显式声明 `display`）。
> 现状排查：`find` 分组卡（本次已修）、`my-reviews` 的 `.profile-strip` 与若干 `IconSvg` 宿主类
> （它们已声明 `display` / 或尺寸由组件内联自持 ⇒ 不受影响，未改动）。

### Round 28（2026-09-27）—— 首页 / 搜索 / 菜品详情三块重构 + 文档纠偏

> 用户诉求：对近期改动界面做**可读性 / 可维护性 / 运行效率**优化，减少冗余逻辑、统一风格（「相同字段考虑共用与模块化」），
> 并同步文档（**简洁、结构清晰、便于查阅**）。先由子代理做五维审计（同字段重复 / 冗余代码 / 风格 / 效率 / 文档偏离）。

**执行（全部零行为、零视觉变化）**

| 类别 | 内容 |
|---|---|
| 冗余 API 清除 | `AppTitleBand` 的 `leftSize` / `centerSize`（全仓零传入）→ 删除 props + 两个 computed + 三处内联 style，字号改由一条 `element,element` 规则统一声明（`.band-back-text, .band-title-left, .band-title-center`）；`HomeMealTabs` 的 `MealTab` 取消导出；`useDishPage` 的 `onDeleteReview` / `onReviewReport` 从返回面与页面解构中移除（仅内部使用） |
| 重复逻辑 | `find` 的 `.find-empty-host` / `.find-retry-host`**两条 CSS 逐字重复** → 合并为 `.state-host`；`openDishDetail` 仅单点调用且与 `goToMixed` 重复判空 → 内联；`guessLikeList` 原是「对 store getter 再包一层 computed」→ 改 `storeToRefs` |
| 运行效率 | `DishResultCard` 的 `splitHighlight(item.name/sub)` 原在**模板内调用**（每次渲染重跑拆段 + `toLowerCase`）→ 改 `computed` 缓存；`ReviewItem` 的星级颗数表达式 → `computed` |
| 统一 token | `DishResultCard` 淡入 `0.32s`（裸值）→ `--duration-slow`（与 `DishCard` 缩略图同档）；`DishReviewSection` 胶囊槽内距 `4rpx` → `--spacing-2xs` |
| 注释口径对账 | `home/index.vue` 两处「不需要任何表面 / 切片 / 材质、`@scroll` 已删除」与实现（吸顶容器 + `pinned` 开关 + 切片）**直接矛盾** → 改为正确口径 |

**评估后主动不做（重要判断，避免为「统一」而制造坏抽象）**
- ~~抽 `<PriceGroup>` / `<RatingStars>` 共用件~~：三处差异是**刻意视觉层级**（首页卡 = regular + `--text-body`；结果卡 = semibold + `--font-h3`；详情卡 = semibold + `--text-primary`；价格组仅 2 处且一处带独立 `¥` 符号）
  ⇒ 抽件只能变成「5 个 props 的开关组件」或强行改视觉 ⇒ **不做**；已共用的部分（`formatPrice` / `hasDiscount` / `formatRating` / `joinLocation` / `getThumbImageUrl`）本就在 `utils/` 单点。
- 三页页根壳统一、`DishCard`/`DishResultCard` 复用 `CardSection` 外壳：触碰滚动与外边距语义 ⇒ 高风险，留待真机专项。

**文档纠偏**（`docs/ui/client-首页菜品浏览.md`，7 处与实现冲突/滞后）
- 「首页**不再有吸顶容器** / `@scroll` 已删除 / 全页不需要表面」→ 与同文档 §11.1/§13 及实现**自相矛盾** ⇒ 按实现改写（吸顶容器 + 原生粘性定位 + 离散 `pinned` 开关 + 背景图切片）；
- 搜索行「左胶囊 + 右独立按钮」→ **单胶囊内嵌搜索按钮**（Round 20 起；搜索页文档早已更新，首页文档滞后）⇒ 结构表 / 理由 / 组件清单 ×2 / 硬性条款第 14 条 一并改正；
- 胶囊视觉与占位色：`--shadow-float`→`--shadow-card`、`--text-tertiary`→`--text-placeholder`（Round 18 口径）。

**闸门**：vue-tsc 0 / build DONE / lint 0 / 主包 **0.40MB** / 产物：`state-host` 落盘、`discover-card` 在页面 `view` 上（2 处）、`AppTitleBand` 字号规则落盘

### Round 29（2026-09-27）—— 首页 UI 文档重写（只留当前口径）+ 老账结清

| 项 | 内容 |
|---|---|
| **文档重写** | `docs/ui/client-首页菜品浏览.md`：**735 → 477 行**。原则 = **本文件只写「当前口径」，逐轮变更 / 被推翻方案 / 失败尝试 / 历史决议一律归口本 loop 文档**（文首已写明）。删除：§0「原页面主要问题」历史盘点、§11.1 六种失败表面方案的完整记录、§11.2「旧方案 / 时序表」（其内容与吸顶实现**直接矛盾**：原文称「只有网格在滚、搜索区 Banner 常驻不动」）、§13 第 4/5 条（禁止 `sticky`）等 → 压缩为速查表 + 一句归口说明。**规格零丢失**（16:10 公式 / 常量 / 排序公式 / 分页上限 / 星色 / 纱 α / 色板与对比度表 / 21 条硬性约束全部保留），并新增文首「**一页速查（6 条）**」 |
| **权威文档同步** | `project_spec.md` §UGC 反馈类第 4 条：「菜品详情页**信息卡**「信息有误？」入口」→ **底栏「反馈错误」**入口（Round 25 落点，含 `feedbackUrl('update', dishId)` 与「免认证 / 全页唯一」）—— 该条自 Round 25 起挂账，本轮结清 |
| **loop 文档入库** | 本文件（此前被 `.gitignore:66 docs/*` 命中而未跟踪，R17–R29 记录只在工作区）→ 以 `git add -f` 纳入版本库，历史可追溯 |

**Round 29 改动文件**：`docs/ui/client-首页菜品浏览.md`（重写）、`docs/project_spec.md`、`docs/UI_FRONTEND_LOOP.md`（入库）。

### Round 30（2026-09-27）—— 新建 `docs/ui/README.md`（UI 设计稿索引 + 修正状态总表）

> 来源：用户指出「`docs/ui` 应该也有一个 README 用于标记哪些页面的修正完成了，类似 `docs/feature/README.md`」。
> 此前 `docs/ui/` 有 25 份文档却**零索引** —— 「哪页改过、改到第几轮、还差哪页」只能靠翻本文档轮次记录反推。

| 项 | 内容 |
|---|---|
| ① 新增索引 | `docs/ui/README.md`：板块与命名规则 → **修正状态标记（✅ 已完成 · ⚠️ 待重设计 · ▫️ 未细化 · ⛔ 已下线）** → client 18 份 + web 7 份总表（编号沿用功能编号 `A-xx`/`B-xx`，含**代码落点**与**最近修订轮次**）→ 与功能文档的对应例外 → 待办清单 → 与其它文档的关系 |
| ② 状态来源 | 逐条取自本文档 §4 轮次记录（R1–R29），**无自创状态**。两处纠正了初判：`评价有用`（A-07）是**已下线留痕**（非「未修订」）；`client-注销账号` 文档其实**内容完整**（组件清单 / 数据映射 / 错误码齐全），只是入口**待重设计**（走 `docs/feature/README.md` 待办 #5） |
| ③ 登记发现 | ① **web 端 7 份为要点级骨架**（仅落点 + 页面要点，无交互 / 三态 / 组件清单 / 数据映射）⇒ **管理端视觉与交互当前无唯一真源**；② `评价有用` 未出现在 `docs/feature/README.md` 索引表（该表 A-06 直达 A-08，**待核是否有意省略**）；③ 功能 ↔ UI 文档的 5 类命名例外已登记（1:2 / 名称不同 / 承载 / 无功能文档 / 已下线） |
| ④ 入库 | `docs/ui/README.md` 被 `.gitignore:66 docs/*` 命中 ⇒ 以 `git add -f` 纳入版本库（与 R29 的 loop 文档同法） |

**闸门**：索引与 §4 轮次记录**逐条对账一致**；`docs/ui/` 全部文档均在表内（18 client + 7 web + 1 png 说明）；新增文件已入库 ✅

**Round 30 改动文件**：`docs/ui/README.md`（新增）、`docs/UI_FRONTEND_LOOP.md`（本轮记录）。

### Round 31（2026-09-27）—— 用户四问：搜索历史补记 / 统一图片占位 / 文档收口 / client 全面清扫

> 来源：用户一次性提四个问题（搜索记录、图片占位、UI 文档与进度、代码清扫）。先由子代理对 `client/src` 做**只读五维审计**（无用代码 / 重复逻辑 / 结构规范 / 已知缺陷），再按「缺陷优先、零行为变化优先」执行。

| 任务 | 内容 | 结果 |
|---|---|---|
| **① 搜索记录补记**（缺陷） | 根因：`goKeyword()` 对「搜索记录」与「猜你喜欢」两类 chip **共用一个不写入分支**（原注释理由：4 条上限、随机词会挤掉真实词）⇒ 点「猜你喜欢」搜完**没有记录**，用户读作「搜过却没留下」。修法：`goKeyword(kw, record = false)` —— **猜你喜欢传 `true`（写入）**、历史词条仍不写（已在记录内，重写只会打乱顺序） | 猜你喜欢点击可回溯；文档 6 处口径 + 功能文档同步改为「用户主动发起的搜索都写入」 |
| **② 统一图片占位** | 此前 **7 处各自实现、3 套图标（`empty`/`dish`/`user`）、4 种底色（`--bg-page`/`--bg-card`/`--bg-soft`/`--bg-placeholder`）**。新建 **`components/ImagePlaceholder.vue`**（灰底 `--bg-placeholder` + 居中图标）+ `IconSvg` 新增 **`image-broken`**（画框 + 山线 + 对角断线）；7 处消费方全部改为 `<ImagePlaceholder>`；退役 `ImageSwiper.placeholderBackground` prop（详情页原传白卡色）与 3 处自绘占位样式 | 占位视觉**单点真源**；消费点 **9 处**；产物实测旧图标残留 **0** |
| **③ 文档收口 + 进度重记** | 删除 **3 份无界面设计稿**（`client-微信静默登录与游客态`＝全局态 / `client-浏览计数`＝静默上报 / `client-评价有用`＝已下线），并同步三份功能文档的 `## UI` 指针为「无独立 UI 界面」；`docs/ui/README.md` 按**用户验收口径**重记：仅 **首页 / 搜索 / 菜品详情 = ✅ 已完成**，其余 12 份 ⛔ 未完成（loop 已改轮次保留在「loop 轮次」事实列）；新增「跨页通用口径 → 图片占位」章节 | 索引不再把「loop 改过」等同于「修正完成」 |
| **④ 代码清扫** | **P0 缺陷 ×2**：① `onReviewsReachBottom` 补 `reviewPending` 门控 —— 重置式请求在途时 append 会推进 `reviewFetchSeq`，使 reset 响应被丢弃 ⇒ **列表只剩第 2 页**；② `loadMoreHomeDishes` 补 `isLoading(LOADING_KEY_HOME/_SWAP)` 门控 —— 与首刷/切大类共用序号，翻页会让 reset 响应作废、第 2 页按**新筛选拼到旧列表** ⇒ 内容错乱。**清理**：零消费 token ×6（`--icon-2xl/3xl`、`--z-detail-bar/nav`、`--z-modal`、`--z-auth`，经 var() 全仓复核为 0）、死 class ×2（`mine-content`、`home-search` 类）、死样式 ×1（`.bar-btn-icon`）、重复注释 ×1、恒真 `v-if` ×1、`.js` 后缀 import ×1、随占位改造失效的 import ×4 | `vue-tsc 0 / build DONE / 主包 0.40MB`；`empty` 图标残留 0 |

**闸门**：vue-tsc 0 / build DONE / lint 0 / 主包 **0.40MB** / 产物核对：`ImagePlaceholder` 消费 9 处、旧占位图标与死 class 残留均 0、6 个 token `var()` 消费 0

> #### 📋 审计产出但**本轮未做**（已入候选池，避免一次动太多）
> P1：`ratingDistribution` 端上零消费（**但 R22 明确「契约保留、端上不消费」** ⇒ 不动，仅在类型注释标注）；`useBrokenImages`（`ReviewItem` ↔ `ImagePicker` 逐字重复的破图集合）抽 composable；删除评价二次确认（详情页 ↔ 我的主页逐字重复）抽公共函数；`SegmentedControl`（详情页分段 ↔ 反馈页分段）抽公共组件 —— 三者皆**近似重复**，按 R28 口径「不为统一制造坏抽象」先评估再动。
> P2：`ActionSheet` / `CardSection` 使用 `:first-child` / `> `（未登记选择器）；9 处页面根壳 `display:flex + height:100vh/100dvh` 逐字重复（可收 `.page--fill` 工具类）；`auth` 的 `setTimeout` 与 `ListPickerSheet` 的 `searchTimer` 未清理；`http.ts` 超时回调引用后置 `const task`（平台同步抛错时 TDZ 风险）；`stores/dish-attribute.ensureLoaded` 无在途去重。

### Round 32（2026-09-27）—— 首页大类标签栏「端上零文案」（移除「为你推荐」端上兜底）

> 来源：用户指出「**为你推荐也不能写在客户端，应该写在服务端；整个 HomeMealTabs 都应该来自服务端才合理，方便后期随时拓展**」。

| 项 | 内容 |
|---|---|
| 事实核对 | **服务端本来就直出**：`DishServiceImpl.listMealTypes()` 在出口拼 `new MealTypeVO(null, "为你推荐", 0)` + `MealTypeConst.ALL`（当前有在售菜的大类）⇒ 标签集合与文案已是服务端资产（虚拟项**不进** `MealTypeConst.ALL` / 不参与白名单，见 D5 第 8 条） |
| 端上残留（本轮消除） | 2 处**兜底硬编码**：① `stores/dish.ts` 字典失败时 `mealTypeList = [{ value: null, label: '为你推荐' }]`；② `HomeMealTabs.vue` 的 `tabs` 在 `items` 为空时回退同一单项 ⇒ 「改文案 / 加虚拟项（如折扣菜品）」又要**端上发版**，与「服务端可随时拓展」冲突 |
| 改法 | ① store 失败路径**不写任何兜底项**（仅记录日志、保留上一次结果）；② `tabs` 直出 `items`（空 ⇒ 空数组）；③ `.mt-bar` 加 `v-if="tabs.length > 0"` —— 字典未到位 / 失败 ⇒ **整体不渲染**（不留空栏、不占位）；列表仍按「不传 `mealType`」的默认流加载，功能不受影响 |
| 效果 | 端上「为你推荐」**字符串字面量 0 处**（仅注释中作为服务端契约说明）；将来新增虚拟项只在 `listMealTypes()` 出口拼装，端上零改动 |
| 顺手 | 清掉 `index.vue` 两个无规则死 class（`home-search` / `home-tabs`，Round 31 审计项） |
| 文档 | `docs/feature/client-首页菜品浏览.md` D5 第 5 条 + `docs/ui/client-首页菜品浏览.md`「字典可用性」两处，由「兜底回退单项」改为「**端上零兜底 + 判空不渲染**」 |

**闸门**：vue-tsc 0 / build DONE / lint 0 / 主包 **0.40MB** / 产物 `<view wx:if="{{a}}" class="mt-bar">` 判空分支已编译、wxml 内无硬编码文案、端上字面量 0

**Round 32 改动文件**：`pages/home/{HomeMealTabs.vue,index.vue}`、`stores/dish.ts`、`docs/{feature,ui}/client-首页菜品浏览.md`。

**Round 31 改动文件**：`components/{ImagePlaceholder.vue(新增),IconSvg.vue,ImageFallback.vue,ImagePicker.vue,ReviewItem.vue}`、
`pages/home/{index.vue(DishCard),HomeBanner.vue}`（占位）、`pages/find/{index.vue,DishResultCard.vue}`、
`pages/detail/dish/{index.vue,ImageSwiper.vue,useDishPage.ts}`、`stores/dish.ts`、`pages/mine/index.vue`、`App.vue`、
`docs/ui/{README.md,client-搜索.md,client-首页菜品浏览.md,client-菜品详情.md,client-我的主页.md,client-我的页.md}`、
`docs/feature/client-{搜索,微信静默登录与游客态,浏览计数,评价有用}.md`、删除 `docs/ui/client-{微信静默登录与游客态,浏览计数,评价有用}.md`。

**Round 28 改动文件**：`components/{AppTitleBand,ReviewItem}.vue`、`pages/home/{index,HomeMealTabs}.vue`、
`pages/find/{index,DishResultCard}.vue`、`pages/detail/dish/{index.vue,useDishPage.ts,DishReviewSection.vue}`、
`docs/ui/client-首页菜品浏览.md`。

**Round 26 改动文件**：`App.vue`、`pages/{home,mine,my-reviews,notifications,profile,auth,feedback,find}/index.vue`、
`pages/detail/dish/index.vue`、`pages/privacy/DocPage.vue`、`pages/feedback/ListPickerSheet.vue`、
`components/BaseSheet.vue`、`docs/ui/client-{首页菜品浏览,搜索}.md`。

---

## 5. 候选池（下一轮待办，按优先级；需讨论同意后才执行）

| 优先级 | 项 | 来源 | 需谁拍板 |
|---|---|---|---|
| **P1（A 阶段，需逐项圈定）** | **白卡壳收敛**：`CardSection` 已补齐 `flush`；**已完成** = `DishReviewSection`（同页归一，R13）、`find` 两卡改 `flush` + 消 `:deep` 覆写（R14）、`profile .info-card` 圆角归卡片档（R14）。**剩余手写壳及与 `CardSection` 的差异**（迁移会带来视觉微差，需你圈定）：`mine .user-card`（padding `lg` + `border-top 6rpx`）/ `mine .grid-cell`（网格项，非卡片）/ `mine .more-group`（margin `lg md 0`）/ `my-reviews .profile-strip`（padding `sm md` + `margin-bottom md`）/ `notifications .msg-item`（padding `lg`）/ `feedback .q-card`（padding `lg` + margin `xs md 0`）/ `FindResults .mixed-item`（padding `md lg` 且**无阴影**）/ `auth .input-field`（**无阴影**的输入壳）/ `ReviewItem .review-item`（含 `--flat` 变体，非 flat 分支疑无消费方） | R6 审计 / R13-14 | UI + 前端：**是否把这些 padding/margin 统一到卡片档**（会成片改变观感） |
| **P2** | `ReviewItem .review-item` 非 flat 分支疑**无消费方**（注释称「我的评价页自持卡片」，而详情页只用 `flat`）—— 建议按「零消费即删」核实后收敛 | R14 观察 | 前端 |
| ~~P1~~ | ~~隐私政策 / 用户协议两页样式逐字节重复~~ | 审计⑥ | **已于 Round 4 完成**（抽 `pages/privacy/DocPage.vue`）✅ |
| ✅ **已完成（原 P0）** | ~~顶部导航断层~~ —— 用户裁决 **1A**：Round 12 已把 `AppHeader` 改为**透明 + 左「返回」文字 + 有返回居中/无返回居左**，并补齐 `<slot>`；度量**三源已全部收口**（R7 `AppHeader` / R8 `useDishPage` 状态栏导航行 / R12 胶囊避让）；两组件**按需显示、不合并**（用户裁定） | Round 6 / 12 | 待真机回归 7 个二级页 |
| P1 | **`--page-wash` 值与注释不一致**：值 `rgba(255,248,239,0.6)`，注释写"0.8 为当前平衡点"（另一会话改动） | 审计② | 产品/UI |
| P2 | **加载态不统一**：各页多为「静默空白」（§4.8 红线：不设骨架屏）；仅个别处有「加载中…」文案 —— 是否给**触底加载更多**外的场景统一一个极简指示 | Round 3 观察 | UI |
| ~~P2~~ | ~~`ListPickerSheet` 内置 fallback 空态（零消费）~~ | 审计⑤ | **已于 Round 4 完成**（按零消费即删移除）✅ |
| P1 | **剩余间距 / 圆角裸值**：`10rpx` ×1（`notifications:231`，不在 4pt 栅格）、圆角 `12rpx` ×1（`ReportModal` 选项行 —— 提交钮已归 `--radius-btn`）、`8rpx` ×1（`ReviewComposer:213`） | Round 6 审计 | UI（定档；~~`2rpx` ×7~~ 已于 Round 7 补 `--spacing-3xs` 完成 ✅） |
| P1 | **导航度量第二源**：`pages/detail/dish/useDishPage.ts:30,220` 仍自算 `getNavBarHeight` + 胶囊避让（含自有的 `+8px` 与 `spacingSmPx`）—— 需核对其与 `useNavMetrics().navPadRight` 是否等价后再收口 | Round 6/7 审计 | 前端（`AppHeader` 已于 Round 7 完成 ✅） |
| **P1** | **阴影三选一**：`shadow-card` vs `shadow-warm`（`feedback .q-card` / `notifications msg-item.unread` / `feedback seg-item.active`）；另 `profile .info-card` 用 `--radius-modal`(48rpx) 而非 `--radius-card`(32rpx) | Round 6 审计 | UI |
| ~~P2~~ | ~~裸 `z-index` / `ImageFallback` 的 `64rpx !important`~~ | 审计④ | **已于 Round 5 完成**（全局层级 token 化；局部层叠已标注为"故意裸值"）✅ |
| ✅ **已更正（原 P1）** | ~~14 个「零引用」色值 token~~ —— **原扫描只查了 `var(--x)`，漏了 JS 侧 `COLOR_MAP['x']`**。Round 12 重扫（两种消费都查）：14 个 token **全部在用**（`COLOR_MAP[…]` 各 1–2 处，如 `TabBar` 的 `primary-bright`、图标色 `success`/`info`/`warning` 等）⇒ **结论：全部保留，4B 撤回**。若日后仍要瘦身，只能删「CSS_VARS + generated-colors.css 里的 14 条 `--x`」（JS 侧 COLOR_MAP 必须保留），但会打破「CSS_VARS ↔ COLOR_MAP 同键同值」约定 ⇒ 需重新裁定 | Round 5 / Round 12 | 无遗留（暂不动） |
| P2 | 首页 `.home-page-bg` class 现仅有注释引用、无 CSS 规则（保留作语义钩子 vs 删除） | Round 5 观察 | 前端 |
| P2 | **通知页"未读"双指示器并存**（圆点 + 左侧竖条） | 审计⑥ | UI（二选一） |
| P3 | `AppTitleBand` 仅 2 个消费者、`AppHeader` 8 个 —— 是否存在合并空间 | 审计⑥ | UI + 前端 |
| ✅ **已关闭（原 P0）** | ~~首页「切片」口径的跨会话冲突~~ —— 另一会话已将文档 §0/§11 主口径同步为切片方案；Loop 侧 R9 清零代码内矛盾注释并核对层级无冲突，R10 清零文档残留旧口径（11 处）。**现行口径：只有吸顶容器需要表面（未吸顶透明、吸顶态背景图切片）；标题带 / TabBar 恒透明** | Round 8–10 | 无遗留 |
| ~~P0~~ | ~~统一空态组件~~ | — | **已于 Round 2/3 完成（7 套 → 1 个组件）** ✅ |

---

## 6. 停止条件

- 候选池清空；或
- 连续两轮无 P0/P1 项产生；或
- 剩余项均需外部决策（产品/后端）且已挂起。

---

## 7. 与既有文档的关系

- `docs/ui/client-*.md`：各页 UI 口径真源（本 loop 的改动必须回流到对应文档）。
- `docs/PRODUCT_LOOP.md`：产品优化 loop（业务侧）；本 loop 只负责 **UI 一致性 + 前端代码质量 + 文档同步**。
- `CODEBUDDY.md`：仓库约定与红线。
