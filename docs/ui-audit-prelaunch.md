# 「食在交大」上架前全站界面审计（UI/UX 只读审计报告）

> 审计角色：UI/UX 设计师（只读，未改动任何源码）
> 审计日期：上架前
> 判据：`docs/project_spec.md` §4（含 §4.9 BLOCKER 红线）、`client/src/App.vue` 令牌真源、`client/src/theme/tokens.ts`、`client/src/components/IconSvg.vue` 键表、skill `apple-design`
> 范围：`client/src/pages/**` + `client/src/components/**`（全量）+ `web/src/views/**`（抽查）
> **判据缺口登记**：`docs/ui-design.md` **不存在**（`docs/` 实际仅 `api-design.md` / `architecture.md` / `database.md` / `NEED.md` / `project_spec.md`）。此前若有引用 `docs/ui-design.md` 的流程步骤，应改判据为 §4 + `App.vue`。同理 `docs/web-ui.md` / `docs/pages/TEMPLATE.md` 已按 spec §4.2 声明删除，本次审计未引用。
> **时效标注（2026-09-13）**：活动 + 公告（broadcast）已全链路下线，`pages/activity/*` 两页（活动列表 / web-view）及其后端接口、库表已删除。本报告中涉及 `pages/activity/index.vue`、`pages/activity/webview.vue`、`broadcast`/`broadcast-fill` 图标键的条目（§0 键注册、§1.1/1.4/1.5/1.6 相关行、§3 PASS 清单）均**随页面删除而失效**，仅作历史审计快照保留，相关整改项一律不再执行；「我的」页宫格相关描述按 2026-09-13 一行 3 列口径复核。

---

## 0. 全局结论（先看这里）

| 维度 | 结论 | 证据 |
|---|---|---|
| **颜色 token 化** | ✅ **PASS（零违规）** | 全量 grep `#hex` / `rgba()`：`client/src/**/*.vue` 命中仅 2 处——`App.vue`（token 声明本身）+ `pages/detail/dish/index.vue:216`（纯注释）。业务样式零裸 hex。 |
| **`@tap` 统一** | ✅ **PASS** | grep `@click` 全 `client/src` 仅 1 处命中，且是 `DishCard.vue:58` 的注释说明，非真实绑定。 |
| **裸 `scale()` 红线（§4.9）** | ✅ **PASS** | grep `scale(` 仅命中 `App.vue:243` 的 `grayscale(0.2)`（非 transform scale）。小程序端 grep-zero 达成。 |
| **IconSvg 键注册** | ✅ **PASS** | 逐文件 diff 字符串字面量 icon（`broadcast`/`bell`/`star`/`report`/`check`/`arrow`/`arrow-left`/`arrow-up`/`arrow-down`/`arrow-fat`/`close`/`canteen`/`price`/`filter`/`dish`/`dish-fill`/`search`/`search-fill`/`sent`/`plus`/`location`/`user`/`lock`/`more-v`/`thumb`/`heart*`/`star-filled`/`home`/`profile`/`home-filled`/`profile-filled`/`lightbulb-fill`/`report-fill`/`broadcast-fill`/`empty`/`edit`/`delete`/`share`/`clock`/`comment`/`empty`）全部已在 `IconSvg.vue` 的 `ICONS` 键表中注册，**无未注册键**。回退目标为 `empty` 中性占位（非 `dish`），符合 §4.9 第十轮补强。 |
| **中性占位语义** | ✅ **PASS** | `ImageFallback.vue:5` 硬编码 `name="empty"`（正确）；`DishCard.vue:15` 用 `dish`（菜品图语义，合法）；`FindResults.vue:31` 菜品缩略图 `dish`（合法）；`mine/index.vue:19` 头像 `user`（合法）。无「破图显示碗」盲区。 |
| **`aria-hidden` 内嵌可点元素** | ✅ **PASS（已修复）** | `mine/index.vue:75` 的 `aria-hidden="true"` 只包 `.app-footer-lines`（版本/学校两行纯展示），合规入口 `.app-footer-link`（79-84）是其**兄弟节点**，对辅助技术可见。历史缺陷已闭环。 |
| **`--spacing-2xl` 悬空 token** | ❌ **P0 缺陷** | 2 处使用，但 `App.vue` 只定义到 `--spacing-xl`（`App.vue:91-96`），**声明无效被丢弃 → padding 上边距塌成 0**。 |
| **弹层 dialog 语义** | ⚠️ 部分达成 | `BaseSheet.vue:10-12` 有 `role="dialog"` + `aria-modal="true"` + `tabindex="-1"`（✅）；`ReportModal.vue:2` 遮罩层**缺** dialog/aria-modal（❌）。 |
| **Web 端一致性** | ✅ **PASS** | `variables.css` 三层 token + `prefers-color-scheme` 双主题 + `--press-scale`（登记豁免）+ `prefers-reduced-motion`/`prefers-reduced-transparency` 降级齐全；`AdminLayout` 顶部四入口扁平命名。 |

**总计**：P0 缺陷 1 项、P1 缺陷 5 项、P2 缺陷 8 项、P3 建议 5 项。**上架前必修 = P0 + P1（共 6 项）**。

---

## 1. 审计明细表

> 格式：`页面/组件 | 现状(file:line) | 问题 | 期望 | 优先级 | 上架前必修?`

### 1.1 令牌 / 视觉一致性 / 8 基网格

| 页面/组件 | 现状(file:line) | 问题 | 期望（具体到 token/间距/字号/交互） | 优先级 | 上架前必修? |
|---|---|---|---|---|---|
| `App.vue`（令牌真源） | `App.vue:91-96` 只定义 `--spacing-2xs/xs/sm/md/lg/xl` | 缺 `--spacing-2xl` 定义 | **补 `--spacing-2xl: 64rpx;`**（对齐 4pt 栅格 ×16；或改两处使用点为 `--spacing-xl` 48rpx） | **P0** | ✅ 必修 |
| `pages/me/notifications/index.vue` | `:211` `padding: var(--spacing-2xl) var(--spacing-lg)` | 悬空 token → 上 padding 塌为 0，空态贴顶 | 同上一并修复；空态期望上间距 `var(--spacing-xl)`(48rpx) 且视觉居中 | **P0** | ✅ 必修 |
| `pages/me/my-reviews/index.vue` | `:211` `padding: var(--spacing-2xl) var(--spacing-lg)` | 同上 | 同上 | **P0** | ✅ 必修 |
| `pages/find/FindResults.vue` | `:180` `padding: 28rpx var(--spacing-lg)`；`:229` `padding: 2rpx 12rpx`；`:216` `margin-top: 2rpx`；`:241` `gap: 2rpx` | 裸 28rpx/12rpx/2rpx 非 8 基网格值（28 vs 24/32；12 vs 16；2 vs 4） | 统一走 token：`padding: var(--spacing-md) var(--spacing-lg)`（24/32）；`padding: var(--spacing-2xs) var(--spacing-xs)`（4/8）；`gap: var(--spacing-2xs)`（4）。**不得新增裸值** | P2 | 否 |
| `components/CardSection.vue` | `:21` `margin: var(--spacing-sm) var(--spacing-md)`（16/24） | 与 `HomeContent .feed-wrap`（`:120` 横向 `--spacing-md`）、`DishReviewSection`（`:44` `margin: var(--spacing-md) var(--spacing-md) 0`）卡片外距**口径不一**（16 vs 24 上边距） | 统一卡片外距为 `margin: var(--spacing-md) var(--spacing-md)`（24 四边），卡片串行间距一致 | P2 | 否 |
| `pages/detail/dish/DishReviewSection.vue` | `:46-50` 评价卡 `padding: var(--spacing-sm) var(--spacing-md)`（16/24） | 与 DishInfoCard 的 `CardSection` 内边距（`CardSection.vue:22` = `--spacing-md` 24 四边）不一致，同页两卡内缘不齐 | 评价卡内边距改 `var(--spacing-md)` 四边，或显式声明「卡内列表卡缩进」并同步 DishInfoCard | P3 | 否 |
| 全站卡片 | `DishCard:95-106` / `HomeContent .contribute-card:164-174` / `notifications .msg-item:155-167` / `my-reviews .review-card:164-174` / `activity .activity-card:173-183` / `privacy .doc:108-117` | 卡片圆角/阴影均走 `var(--radius-card)` + `var(--shadow-card\|shadow-warm)` ✅ | **一致，无需改**（暖调页用 `--shadow-warm` 属已登记 Q 版语言，非漂移） | PASS | — |
| Web `variables.css` / `AdminLayout` / `DashboardView` | `variables.css` 三层 token；`DashboardView.vue:124-209` 全 token | 无裸值、无裸 hex ✅ | **PASS** | PASS | — |
| `pages/activity/index.vue` | `:286` `padding: 180rpx var(--spacing-lg)` 空态；`:251` `height: 56rpx` 胶囊 | 180rpx 裸值（非 token）；56rpx 小于 88rpx 触达下限 | 空态改 `padding: var(--spacing-4xl?) → 用 var(--icon-4xl)` 派生或 `padding: 160rpx var(--spacing-lg)`；胶囊高改 ≥88rpx（见 §1.3）。**【已失效】页面已随 2026-09-13 活动全链路下线删除，本条不再执行** | P2 | 否 |

### 1.2 可读性与层级（字号 / 字重 / 对比度）

| 页面/组件 | 现状(file:line) | 问题 | 期望 | 优先级 | 上架前必修? |
|---|---|---|---|---|---|
| `pages/home/DishCard.vue` | `:200-202` 食堂·档口 `--font-small`(24rpx) `--text-tertiary`；`:231-236` 距离同档 | 三级灰承载「食堂·档口」——**这是一个菜品的核心定位信息**（用户选菜主要依据），用三级灰偏弱 | 定位信息升为 `--text-secondary`（#595959，对比 4.5:1+）保留辅助档字号；距离可留三级灰 | P2 | 否 |
| `pages/home/DishCard.vue` | `:158-161` 评分徽标 `--font-tiny`(20rpx) `--text-white` on `--overlay-dark-strong` | 白字 on rgba(0,0,0,0.6) 叠加在图片上，对比度随图片亮度波动 | 保持现状（徽标用途：一眼看分）；如要更稳，`--overlay-dark-deep`(0.65) 提对比 | P3 | 否 |
| `pages/find/FindResults.vue` | `:244-247` 位置/距你 `--font-aux`(22rpx) `--text-tertiary` | 同 DishCard 问题（位置信息用三级灰） | 同上下调：位置升 `--text-secondary` | P3 | 否 |
| `pages/me/my-reviews/index.vue` | `:190-200` 评价正文 `--font-small`(24rpx) `--text-primary`；`:203` 时间 `--font-tiny` | 正文 24rpx 小于详情页评价正文（`ReviewItem:264` = `--font-body` 28rpx），两处同名「评价正文」字号不一 | 统一 `--font-body`(28rpx)。**同级语义同字号**（Apple §Craft） | P2 | 否 |
| 全站三级灰使用 | `ReviewItem:236` 时间 `--font-small`+tertiary；`notifications:193` 时间 `--font-tiny`+tertiary；`find:497` 清空 tertiary | 辅助信息（时间/清空）走三级灰 ✅ 正确未承载主信息 | **PASS**（三级灰仅用于时间/次要动作/占位，均非主信息） | PASS | — |

### 1.3 可点性与热区（≥44px / 88rpx）

| 页面/组件 | 现状(file:line) | 问题 | 期望 | 优先级 | 上架前必修? |
|---|---|---|---|---|---|
| `pages/me/my-reviews/index.vue` | `:25-30` `.delete-link` `padding: var(--spacing-2xs) var(--spacing-xs)`（4/8rpx）+ `--font-aux`(22rpx) → 实测热区 ≈ 41×22rpx，**远小于 88rpx** | 破坏性操作「删除」热区过小，难命中且易误触邻区 | 加 `min-height: 88rpx; min-width: 88rpx; display:inline-flex; align-items:center; justify-content:flex-end;` 或 `padding: var(--spacing-sm) var(--spacing-md)`（撑到 ≥88rpx） | **P1** | ✅ 必修 |
| `pages/find/index.vue` | `:497-498` `.history-clear`（清空历史）`padding: var(--spacing-xs) var(--spacing-sm)`（8/16rpx）+ 字 22rpx，热区高 ≈ 38rpx | 破坏性「清空」热区 <88rpx | `padding: var(--spacing-md) var(--spacing-md)` 或 `min-height:88rpx` | **P1** | ✅ 必修 |
| `components/AppHeader.vue` | `:231` `.search-nav .back-area { width: 32px }`（`--nav-h` 行高 ≈56px，故高 56px ✅） | 宽 32px < 44px（注释已声明「主动收窄让位搜索框」） | 可保留（行高已 ≥44px 且属有意权衡），但建议 `width: 40px` + `padding` 内扩命中区 | P3 | 否 |
| `components/TabBar.vue` | `:71-81` `.tab-item` 高 `--tabbar-height`(100rpx)、`flex:1` 宽 ≈375rpx | **PASS**（两项均远超 88rpx） | **PASS** | PASS | — |
| `pages/mine/index.vue` | `:251-265` `.grid-cell` 整格热区（`flex:1`+padding） | **PASS**（宫格整格热区成立） | **PASS** | PASS | — |
| `pages/detail/dish/ReviewItem.vue` | `:246-259` `.review-more` 64×64rpx | 64rpx < 88rpx（三点菜单） | 扩到 88×88rpx（`width/height: 88rpx`），视觉图标仍 28rpx | P2 | 否 |
| `pages/detail/dish/ReviewComposer.vue` | `:152` `.rc-star { padding: 0 var(--spacing-2xs) }` + IconSvg 56rpx → 热区 ≈72×56rpx | 星级热区 <88rpx 且横排 5 颗易误触邻星 | `.rc-star { padding: var(--spacing-xs) var(--spacing-sm) }` 使单星热区 ≥88rpx | P2 | 否 |
| `pages/me/feedback/index.vue` | `:132-139` `.pick-goto-add { height: 68rpx }` | 68rpx < 88rpx | 改 `height: 88rpx` | P2 | 否 |
| `components/FilterBar.vue` | `:372` `.fb-chip { height: var(--capsule-h, 32px) }`（≈64rpx） | 64rpx < 88rpx；但属顶部工具条胶囊（iOS 工具条 32pt 惯例），且有横向 padding | 保留（工具条惯例豁免），或 `min-height: 72rpx` 折中 | P3 | 否 |
| 按压态 token | 全站 `:active`/`.pressed` 均走 `opacity` 或 `var(--bg-soft)`；无裸 `scale` ✅ | **PASS**（§4.4/§4.9 双合规） | **PASS** | PASS | — |
| `prefers-reduced-motion` | `mine:317`、`notifications:234`、`HomeContent:198`、`ReportModal:69`、`web AdminLayout:221/262` 均有降级块 | 部分组件有，但 `DishCard`（图片淡入 `:124`）、`FindResults:195`（缩略图淡入 0.32s）、`BaseSheet` 未显式声明 reduced-motion | 补：图片淡入在 `prefers-reduced-motion: reduce` 下 `transition: none`；BaseSheet 已瞬开瞬关（天然满足） | P2 | 否 |

### 1.4 可访问性（role / aria）

| 页面/组件 | 现状(file:line) | 问题 | 期望 | 优先级 | 上架前必修? |
|---|---|---|---|---|---|
| `components/ReportModal.vue` | `:2` `.modal-mask` 仅 `v-if + @tap`，`:3` `.report-modal` 无 `role`/`aria-modal` | **居中弹窗缺 `role="dialog"` + `aria-modal="true"`**（BaseSheet 有，本组件缺失） | `<view class="report-modal" role="dialog" aria-modal="true" aria-label="举报评价" tabindex="-1">` | **P1** | ✅ 必修 |
| `components/BaseSheet.vue` | `:10-12` `role="dialog" aria-modal="true" tabindex="-1"` | **PASS** | **PASS** | PASS | — |
| `components/ReportModal.vue` | `:5-8` textarea/取消/确认 均无 `aria-label`；`:8` 确认无 `aria-disabled` | 表单控件无标签、提交中态未暴露 | textarea 加 `aria-label="举报原因"`；确认钮加 `role="button"` + `:aria-disabled` + `:aria-busy` | P2 | 否 |
| `pages/mine/index.vue` | `:8-14` 用户卡、`:32-40` 去认证、`:48-56` 宫格、`:79-84` 合规入口 均有 `role`+`aria-label` | **PASS**（覆盖完整） | **PASS** | PASS | — |
| `components/TabBar.vue` | `:10` 有 `:aria-label`，但**无 `role`**；容器无 `role="tablist"` | TabBar 项缺 `role="tab"`/`aria-selected`；切换用 `reLaunch` 语义上非 tab | 加 `role="tab"` + `:aria-selected="item.key===activeTab"`，容器 `role="tablist"`；或保持现状（小程序 a11y 支持有限）——**建议至少补 `role`** | P2 | 否 |
| `components/ActionSheet.vue` | `:21` 非 share 项有 `role="button"`；`:8` share 项为 `<button open-type="share">` | **PASS**（share 走原生 button 正确） | **PASS** | PASS | — |
| `pages/detail/dish/ReviewItem.vue` | `:28` 星级 `role="img"` + `aria-label` ✅；`:46` 有用 `role="button"`+label ✅；`:22` 三点 `role`+label ✅ | **PASS** | **PASS** | PASS | — |
| `pages/me/feedback/index.vue` | `:16-18` 类型卡 `role="radio"` + `:aria-checked` + `aria-label` ✅ | 但**未包在 `role="radiogroup"`** 中，单选中语义不完整 | 外层 `.type-row` 加 `role="radiogroup" aria-label="反馈类型"` | P2 | 否 |
| `pages/activity/webview.vue` | `:9` 返回钮 `role="button" aria-label="返回"` ✅ | **PASS** | **PASS** | PASS | — |
| `pages/find/index.vue` | `:103` `推荐这道菜` `role="button"` ✅；`:35` 清空历史 无 role（见 §1.3） | 清空历史缺 role + 热区不足 | 补 `role="button" aria-label="清空搜索历史"`（与 §1.3 合并整改） | P1 | ✅ 必修 |
| `pages/me/privacy/index.vue` | `:7-15` Tab 无 `role="tab"`/`aria-selected` | 文本切换 Tab 语义缺失 | `:class` 行加 `role="tab"` + `:aria-selected`，容器 `role="tablist"` | P3 | 否 |

### 1.5 状态呈现口径（**MP-012 重试块 vs spec「失败态静默」——待登记 spec 例外**）

> 现状：MP-012 已在 **5 处**引入「加载失败 · 点击重试」块：
> `home/HomeContent.vue:5-16`、`find/index.vue:83-94`、`activity/index.vue:47-58`、`me/notifications/index.vue:28-39`、`me/my-reviews/index.vue:37-48`
> 而既有 spec 条目仍写「失败态静默」：`§4.2`（`EmptyState`/`StateView` 已删、异常静默）、`§4.8`（列表/信息流不再设三态占位）、`my-reviews` / `client-page-structure` / `find-page-layout` spec 均写「失败静默」。

**我的 UX 裁定（建议登记为 spec 例外，须交技术负责人落文档）：**

| 场景 | 裁定 | 理由 |
|---|---|---|
| ① **首屏 / 下拉刷新失败且当前无数据** | **必须显示「加载失败 · 点击重试」块**（5 处保留） | 「静默空态」会把「网络失败」伪装成「这里没有内容」，是**误导性状态**（Apple §16 Feedback：错误态必须暴露）。用户无从知晓该刷新还是该离开。**失败 ≠ 无数据**，必须可区分、可恢复。 |
| ② **分页（触底加载更多）失败** | **保持静默**，靠再次触底或下拉刷新恢复 | 已有内容在屏，加错误块会打断阅读流；这是**非破坏性、可再触发**的失败。5 处实现均已按此（`my-reviews:111-125` / `notifications:106-121` / `activity:121-123`）。**保留现状，写进 spec**。 |
| ③ **搜索失败（find 结果态）** | **必须显示重试块**（`find/index.vue:84-94` 保留） | 与「没搜到」空态（`:97-106` 有 CTA 引导去补录）是**语义完全相反**的两种结果，静默会把用户导向错误的「推荐菜品」动作。 |
| ④ **游客访问系统通知** | **严格静默**（不显示空态/错误/认证引导） | 依 `§5.y.4` 认证边界：游客无个人通知来源，属**正常业务态**而非失败。`notifications:42` 已按 `isVerified()` 门控空态 ✅。**保留现状**，与 ① 不冲突（① 仅 `loadFailed`，`verified` 且请求真失败时才显示）。 |
| ⑤ **详情页评价区无评价** | **静默**（`DishReviewSection:9` `v-if="reviews.length>0"`） | 评价区是详情页附属区块，无评价时整块收起、不占位，符合 §2.1「信息架构收敛」。**保留现状**。 |
| ⑥ **发现态（find 未搜索）热词/历史加载失败** | **静默**（`find/index.vue:380-390`） | 无历史/无热词时区块整体不渲染（`v-if="historyList.length>0"`），失败与「无记录」视觉一致且用户无损失感知。**保留现状**。 |

**结论**：应为 **5 处重试块登记 spec 例外**（§4.2 / §4.8 表述需追加「首屏/刷新失败例外：渲染失败重试块，见 MP-012」），同时把「分页失败静默」明确写入 spec 以便后续不再反复。**本项不改代码，只裁定 + 待登记。**

| 页面/组件 | 现状(file:line) | 问题 | 期望 | 优先级 | 上架前必修? |
|---|---|---|---|---|---|
| `home/HomeContent.vue` | `:5-16` 重试块（`bg-soft` + `report` 图标 + 失败/提示双行） | ✅ 语义正确、与空态可区分 | 文案口径统一为「加载失败 / 网络似乎不太顺畅 · 点击重试」 | PASS | — |
| `find/index.vue` | `:83-94` 同族重试块 | ✅ 先于空态渲染（`v-else-if` 链顺序正确） | **PASS** | PASS | — |
| `activity/index.vue` | `:47-58` 同族重试块 | ✅ | **PASS** | PASS | — |
| `me/notifications/index.vue` | `:28-39` 同族重试块 + `:42` 空态受 `isVerified()` 门控 | ✅ 游客静默口径正确 | **PASS** | PASS | — |
| `me/my-reviews/index.vue` | `:37-48` 同族重试块 + `:50` `emptiedByDelete` 空态双口径 | ✅ 首次进入静默 / 删空给轻提示（避免被误读为异常）——**设计判断优秀** | **PASS** | PASS | — |
| `detail/dish/DishReviewSection.vue` | `:2` 注释「无评价时卡内静默」 | ✅ | **PASS** | PASS | — |

### 1.6 底部安全区 / 固定底栏 / TabBar / 宫格

| 页面/组件 | 现状(file:line) | 问题 | 期望 | 优先级 | 上架前必修? |
|---|---|---|---|---|---|
| `components/TabBar.vue` | `:56-70` 两项（首页/我的）、`height: calc(--tabbar-height + safe-area)`、无中心按钮 | **PASS**（符合 2026-09-12 收敛后两页口径） | **PASS** | PASS | — |
| `pages/home/index.vue` | `:212-219` `.scroll-wrap` `padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom))` | **PASS**（避让正确） | **PASS** | PASS | — |
| `pages/mine/index.vue` | `:200` `.mine-content` `padding-bottom: calc(--tabbar-height + safe-area)` | **PASS** | **PASS** | PASS | — |
| `pages/me/profile/index.vue` | `:138` `.scroll-wrap` `padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom) + var(--spacing-lg))` | **PASS**（底栏避让含 safe-area） | **PASS** | PASS | — |
| `pages/detail/dish/index.vue` | `:184` `.dish-page { padding-bottom: calc(160rpx + env(safe-area-inset-bottom)) }` | 用裸 `160rpx` 而非 `--action-bar-height`(120rpx)/`--tabbar-height`(100rpx)。虽然 > action-bar 高度（有余量），但**未走 token**，与 §4.9「底栏避让用 calc(var(--action-bar-height)+env())」口径不符 | 改 `padding-bottom: calc(var(--action-bar-height) + var(--spacing-lg) + env(safe-area-inset-bottom))`（120+32+env，与现 160rpx 视觉接近且 token 化） | **P1** | ✅ 必修 |
| `pages/me/feedback/index.vue` | `:216-223` `.scroll-wrap` 仅 `padding-bottom: env(safe-area-inset-bottom)` | 该页提交按钮**随内容滚动**（非固定底栏，`:316` `.submit-area` 在流内），故无需 action-bar 高度避让 ✅；但缺底部呼吸留白 | 建议 `padding-bottom: calc(var(--spacing-lg) + env(safe-area-inset-bottom))`（补 32rpx 呼吸） | P2 | 否 |
| `pages/activity/index.vue` | `:160-165` `.scroll-wrap` `padding-bottom: env(safe-area-inset-bottom)`；`:66` 尾部显式注入 `calc(--spacing-lg + env)` 占位 | **PASS**（尾部占位块补齐避让） | **PASS** | PASS | — |
| `pages/me/my-reviews` / `notifications` | `:161`/`:152` `.scroll-wrap` `padding: var(--spacing-md) var(--spacing-md) calc(var(--spacing-md) + var(--spacing-lg))`，**无 safe-area** | 二级页无固定底栏，但**竖屏底部 home indicator 区未避让**，末条卡片可能贴底 | 加 `env(safe-area-inset-bottom)`：`calc(var(--spacing-md) + var(--spacing-lg) + env(safe-area-inset-bottom))` | P2 | 否 |
| `pages/privacy` | `:106` 同上无 safe-area | 同 | 同上 | P2 | 否 |
| `pages/mine/index.vue` 宫格标题 | `:278` `.grid-cell-label { white-space: nowrap }` + 字 `--font-subtitle`(32rpx) | **窄屏单行不折行**：一行 3 列每格宽 ≈ (750-48-24)/3 ≈ 226rpx（2026-09-13 口径）；「意见反馈」「系统通知」4 字 ×32rpx=128rpx ✅ 不折行（原 2×2 四格口径下「最新活动」计算随该格下线作废） | **PASS**（已 `nowrap` 且宽度充足），建议补 `overflow: hidden; text-overflow: ellipsis` 兜底超大字号 | PASS | — |

### 1.7 动效克制一致性（§4.3 capability：弹层瞬开瞬关、无入场动效、按压仅 opacity/bg-soft）

| 页面/组件 | 现状(file:line) | 问题 | 期望 | 优先级 | 上架前必修? |
|---|---|---|---|---|---|
| `components/BaseSheet.vue` | `:108` `transition: 'none'`；`:181-189` `translateY` 开合无 transition | **PASS**（瞬开瞬关；下拉手势 1:1 跟手 + 速度投影保留，符合 §4.4 例外） | **PASS** | PASS | — |
| `components/ReportModal.vue` | `:57-60` `.modal-mask`/`.report-modal` 有 `opacity` 但**无 transition 声明** | 观察：无 transition → 瞬开瞬关（与 §4.3 一致）✅ 但 `transform: translate(-50%,-46%→-50%)` 跳变 | **PASS**（无过渡即瞬开瞬关，符合 capability）；`transform` 初值差异属定位而非动画 | PASS | — |
| `pages/home/DishCard.vue` | `:106` `transition: opacity var(--duration-base)`；`:124` 图片淡入 `--duration-slow`(300ms) | 图片淡入 300ms 属**加载态过渡**（非装饰入场），Apple §1「渐进呈现」允许 ✅ | **PASS**（保留；reduced-motion 下建议 none，见 §1.3） | PASS | — |
| 列表进场 | 全站无 `.enter-up`/交错延迟/滑入；`App.vue:220-221` 明确「入场动效已剥离」 | **PASS**（无装饰入场动效） | **PASS** | PASS | — |
| `web AdminLayout` | `:250-261` 路由切换 `opacity + translateY(8px)`；`:262-271` reduced-motion 降级为纯 opacity | **PASS**（≤8px 位移 + 降级正确，符合 §4.3/§4.7） | **PASS** | PASS | — |
| Web `DashboardView` | `:157-159` `.spin` 旋转动画 + `reduced-motion` 下 0.7s→1.4s | 加载 spinner 属**状态指示**非装饰动效；降级为减速而非停止（可接受） | **PASS** | PASS | — |

### 1.8 Web 后台抽查（快速一致性）

| 页面/组件 | 现状(file:line) | 问题 | 期望 | 优先级 | 上架前必修? |
|---|---|---|---|---|---|
| `web/src/styles/variables.css` | 全三层 token + `prefers-color-scheme` 双主题 + `--press-scale:0.97`（登记豁免）+ 焦点环 `--focus-ring` | **PASS** | **PASS** | PASS | — |
| `AdminLayout.vue` | `:20-25` 顶部四入口：工作台 / 信息管理 / 内容审核 / 用户与系统（**名词、无括号废话**） | ✅ 符合「心智模型优先」；与 spec §0.4 能力全景对齐 | 建议：可考虑「概览 / 内容 / 运营」三词范式，但「信息管理/内容审核」已比「含档口」类表述好，**不强制改** | P3 | 否 |
| `DashboardView.vue` | `:124-158` 分区标题带**品牌竖条** `::before` | ⚠️ 小程序端 `SectionTitle.vue:9` 明确「不再渲染左侧品牌色竖条（旧 bar 装饰已移除）」；**Web 端仍保留竖条** → 两端分区标题语言不一致 | 二选一：Web 同步去竖条（与小程序统一），或明确「Web 允许不同语言」并登记。**建议统一为无竖条**（spec §4 要求两端观感一致） | P2 | 否 |
| `DashboardView.vue` | `:162` `.todo-grid` 3 列 / `:189` `.metric-grid` 5 列；`:213-219` 响应式降列 | ✅ 响应式完整 | **PASS** | PASS | — |
| `DashboardView.vue` | `:40-51` 待办 1 项 + 指标 5 项（均为对象**名词**） | ✅ 符合 §0.4.1 收敛后口径（动态/申请已移除） | **PASS** | PASS | — |
| `web` 全局 | `App.vue:1` 137B（极简）；`Toast.vue`/`ConfirmDialog.vue` 全局挂载 | **PASS** | **PASS** | PASS | — |

---

## 2. 上架前必修 Top 清单（P0 / P1）

> 共 6 项，全部可执行、可验收。**执行方：小程序开发工程师（前 5 项）+ 技术负责人（第 6 项落文档）**。

| # | 优先级 | file:line | 必修内容 | 验收标准 |
|---|---|---|---|---|
| **1** | **P0** | `client/src/App.vue:96`（在 `--spacing-xl` 后） | 补 `--spacing-2xl: 64rpx;` | grep `--spacing-2xl` 有定义；两处 `padding` 生效（DevTools 不再显示 invalid） |
| **2** | **P0** | `client/src/pages/me/notifications/index.vue:211` | 空态 padding 上边距恢复正常（依赖 #1，或直接改用 `var(--spacing-xl)`） | 空态「暂无通知」上方留白 ≥48rpx，视觉居中不贴顶 |
| **3** | **P0** | `client/src/pages/me/my-reviews/index.vue:211` | 同上 | 同 #2 |
| **4** | **P1** | `client/src/components/ReportModal.vue:3` | `.report-modal` 加 `role="dialog" aria-modal="true" aria-label="举报评价" tabindex="-1"` | 静态检查 role/aria-modal 存在；与 BaseSheet 口径一致 |
| **5** | **P1** | `client/src/pages/detail/dish/index.vue:184` | `.dish-page` 的 `padding-bottom` 由裸 `160rpx` 改 `calc(var(--action-bar-height) + var(--spacing-lg) + env(safe-area-inset-bottom))` | 真机 750rpx：末条评价不被底栏遮挡；grep 不再出现裸 160rpx 避让 |
| **6** | **P1** | `client/src/pages/me/my-reviews/index.vue:25-30` + `client/src/pages/find/index.vue:35,497-498` | 热区扩至 ≥88rpx：删除链 + 清空历史（含 `role="button" aria-label="清空搜索历史"`） | 真机实测点击热区 ≥88rpx；清空历史有可访问角色与标签 |
| **7** | **P1（文档）** | spec §4.2 / §4.8 / `my-reviews` / `my-reviews` / `client-page-structure` / `find-page-layout` | 登记 **MP-012 例外**：①首屏/刷新失败 → 渲染「加载失败·点击重试」块（5 处）；②分页失败 → 静默；③游客通知 → 静默。见 §1.5 裁定表 | **由技术负责人落 `docs/project_spec.md`**（我不改文档，仅裁定）；登记后 spec 与代码口径一致 |

**放行判定**：以上 1–6 项（代码侧）由对应开发工程师返工 → 我复审 → 再进质量把控工程师门禁。第 7 项为文档登记，不阻塞代码但须在门禁前完成，否则 spec 与实现存在**已知未登记冲突**（§0.3 一致性红线要求文档为唯一权威）。

---

## 3. 明确 PASS 的页面/组件（无问题，逐条登记）

- `client/src/App.vue`（令牌真源：圆角 `--radius-card:32rpx` / 间距 / 字号 / 色 / 阴影 / z-index 标度 / 时长，结构完整；仅缺 `--spacing-2xl`）
- `client/src/components/TabBar.vue`（两页、无中心按钮、避让正确、选中态填充变体）
- `client/src/components/BaseSheet.vue`（dialog+aria-modal、瞬开瞬关、120px 下拉关闭、safe-area、z-token）
- `client/src/components/AuthSheet.vue`（表单 aria 完整：aria-invalid/aria-busy/aria-disabled、错误 `role="alert"` + `aria-live`）
- `client/src/components/ActionSheet.vue`（整行 104rpx 热区、share 走原生 button）
- `client/src/components/ListPickerSheet.vue`（选中圆点、空态槽、plain/card 双行式）
- `client/src/components/IconSvg.vue`（`empty` 中性占位回退正确、dev 告警、data-uri 缓存）
- `client/src/components/FilterBar.vue`（全 token、两胶囊 + 常驻筛选、互斥面板）
- `client/src/pages/home/index.vue` + `HomeContent.vue` + `DishCard.vue`（瀑布流双列 `flex:1 1 0; width:0; min-width:0` 兜底、DishCard 根 `width:100%;min-width:0`、**无具名 slot 分发**、3:2 固定比例 CLS=0）
- `client/src/pages/mine/index.vue`（一行 3 列宫格整格热区、footer aria-hidden 修复闭环、认证态二分正确；2026-09-13 口径）
- `client/src/pages/me/notifications/index.vue`（重试块 + 游客静默 + 未读竖条）
- `client/src/pages/me/my-reviews/index.vue`（重试块 + 删空轻提示双口径）
- `client/src/pages/me/privacy/index.vue`（合规文本随包、无外链依赖）
- ~~`client/src/pages/activity/index.vue`（Q 版卡片、重试块、空态、尾部避让占位）~~（已随 2026-09-13 活动全链路下线删除）
- ~~`client/src/pages/activity/webview.vue`（返回条浮于原生层、协议白名单校验、progressbar token 登记）~~（已随 2026-09-13 活动全链路下线删除）
- `web/src/styles/variables.css` + `web/src/views/layout/AdminLayout.vue` + `web/src/views/dashboard/DashboardView.vue`（三层 token、双主题、reduced-motion/transparency 降级、响应式列）

---

## 4. 给技术负责人的 3 项待办（非代码）

1. **登记 spec 例外**：§4.2 / §4.8 追加 MP-012 失败重试块例外（见 §1.5 裁定表），并明确「分页失败静默」以终结反复。
2. **补 `--spacing-2xl` 到令牌真源**时，同步核对 `tokens.ts` 是否需要镜像（`COLOR_MAP` 只管色值，间距无须镜像，仅 `App.vue` 声明面）。
3. **Web 分区标题竖条决策**（§1.8）：与小程序统一去竖条，或登记「两端语言差异」例外——建议统一，避免两端观感漂移。

---

**审计签章**：UI/UX 设计师（只读审计，未修改任何源码文件）。本报告为设计侧裁定与返工依据，代码改动交对应开发工程师执行。
