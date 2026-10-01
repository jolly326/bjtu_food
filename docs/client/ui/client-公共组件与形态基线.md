# 公共组件与形态基线 — 跨页唯一真源

> 所属端：学生端（微信小程序） ｜ 代码落点：`client/src/components/` · `client/src/theme/` · `client/src/App.vue`
> **口径分工**：**跨页复用的形态口径（形状 / 间距 / 字号 / 色彩 / 状态 / 交互）与公共组件规格以本文件为唯一真源**；页面级布局与页面专属视觉见各页设计稿；功能流程 / 接口 / 字段见 [`docs/client/feature/`](../feature/)。
> **页面设计稿 SHALL NOT 复述本文件已登记的组件规格** —— 各页「组件清单」只写**本页用法**（传什么、在哪、做什么），组件自身的尺寸 / 圆角 / 颜色 / 状态一律引用本文件。
> **本文件只写「当前口径」**：逐轮变更过程不维护（如需回溯，从 git 历史查找）。

---

# 一、全站形态基线

## 1.1 页面骨架

| 项 | 口径 |
|---|---|
| 页面底色 | `page { background: var(--bg-page) }` —— **唯一承载处**（小程序最低层，天然在壁纸层之下）。**页面根 / 组件 SHALL NOT 再声明页面底色** |
| 页面根高度 | `.page { min-height: 100vh; min-height: 100dvh }`；各页根另声明 `height: 100vh; height: 100dvh`（`min-height` 必须双声明，否则会把页根 `dvh` 顶回 `vh`） |
| 主滚动区 | `.scroll-wrap { min-height: 0 }` —— flex 子项默认 `min-height: auto` 不收缩，缺它会出现「页面 + 滚动区」双层滚动 |
| 滚动区底部留白 | **不全局兜底**：仅自带 TabBar 的页（home / mine）由页面自身让出菜单栏；其余页不得凭空多出 TabBar 高度死留白 |
| 固定底栏页 | 滚动区 `padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom))`，禁内容被遮挡 |
| 宽屏（仅 H5 / 桌面） | `@media (min-width: 768px) { .scroll-wrap { max-width: 720px; margin: 0 auto } }` |
| 壁纸 | 全站**页面级一处** `<PageWallpaper fixed />`（`z-index: var(--z-page-bg)` = −1）；壁纸**必须走 `<image>`**（WXSS `background-image: url()` 取不到包内本地路径） |
| 纱（wash） | `--page-wash: rgba(255, 248, 239, 0.7)` —— **α 是唯一旋钮**，铺满整张壁纸、无分段 |

## 1.2 圆角标度（单位统一 rpx）

| token | 值 | 用途 |
|---|---|---|
| `--radius-none` | `0` | 形状重置 |
| `--radius-2xs` | `8rpx` | 极小件 |
| `--radius-xs` | `16rpx` | 焦点环 |
| `--radius-tag` / `--radius-card` / `--radius-btn` | `16rpx` | **卡片 / 按钮 / 标签同值**（跨页白卡一致性） |
| `--radius-icon` | `24rpx` | 图标底 / 输入项容器 / 头像方图 |
| `--radius-modal` | `48rpx` | 底部弹层顶部圆角 |
| `--radius-pill` | `999rpx` | 胶囊（搜索框 / chip / 进度条 / 小标签） |
| `--radius-circle` | `50%` | 正圆（头像 / 圆点 / 指示器） |

**SHALL NOT 裸写圆角值**（全仓 `border-radius: <数字>rpx` 当前 0 处）。

## 1.3 间距标度（4pt 栅格）

`--spacing-3xs` 2 ｜ `--spacing-2xs` 4 ｜ `--spacing-xs` 8 ｜ `--spacing-sm` 16 ｜ `--spacing-md` 24 ｜ `--spacing-lg` 32 ｜ `--spacing-xl` 48 ｜ `--spacing-2xl` 64（单位 rpx）

- `2xs` 为半格，供星标 / 徽标等紧凑布局，避免裸 4rpx。
- 页面级左右 gutter 统一 `--spacing-md`。

## 1.4 字号 / 字重 / 字距标度

| token | 值 | 档位 |
|---|---|---|
| `--font-tiny` | `20rpx` | 最小辅助 |
| `--font-aux` | `22rpx` | 辅助说明 |
| `--font-small` | `24rpx` | 小字 |
| `--font-body` | `28rpx` | 正文 |
| `--font-caption` | `30rpx` | 说明档 |
| `--font-subtitle` | `32rpx` | 副标题 |
| `--font-h3` | `36rpx` | 三级标题 |
| `--font-h2` | `40rpx` | 二级标题（分区标题） |
| `--font-title` | `44rpx` | 标题档（页面名 / 「返回」） |

**字重**：`regular` 400 ｜ `medium` 500 ｜ `semibold` 600 ｜ `bold` 700 ｜ `heavy` 800
**字距**：`--tracking-h2` −0.02em ｜ `--tracking-h3` −0.01em（仅标题收紧，正文不收紧）
**正文级字号下限 12px（24rpx）。**

## 1.5 色彩

- **色板与取色边界（含主色语义分档、文字四档、背景 / 边框 / 语义色 / 星色与对比度口径）的唯一真源 = [client-首页菜品浏览.md](./client-首页菜品浏览.md) §10**（既有裁决，`theme/tokens.ts` 亦以该节为准）。本节**不重复列值**。
- **色值只改 `theme/tokens.ts` 的 `COLOR_MAP` / `CSS_VARS`**（键 = 全局 CSS 变量名）；`theme/generated-colors.css` 由 `App.vue` `@import` 引入，须与 `CSS_VARS` 逐键一致；不接受 `var()` 的原生 API 常量见 §1.16。
- **页面 / 组件 SHALL NOT 裸 hex**（当前全仓自检：0 处违规，命中的均为注释）。
- 星级色为**独立语义色**，**SHALL NOT 随主色换肤**。
- 提示文字 `--text-hint` = `var(--text-tertiary)`（派生引用，非独立色值）。

## 1.6 阴影

| token | 值 | 用途 |
|---|---|---|
| `--shadow-card` | `0 2px 12px rgba(0,0,0,.04)` | 普通卡片（**唯一常规卡阴影**） |
| `--shadow-warm` | `0 4rpx 12rpx rgba(180,140,120,.08)` | **仅「选中 / 强调」态**（段控件选中项、未读通知卡） |
| `--shadow-modal` | `0 18rpx 54rpx rgba(0,0,0,.18)` | 底部弹层 |
| `--shadow-bar` | `0 -4rpx 20rpx rgba(56,42,34,.08)` | 底部菜单栏 |
| `--shadow-bar-soft` | `0 -4rpx 12rpx rgba(0,0,0,.06)` | 底部操作栏 |
| `--shadow-bar-primary` | `0 12rpx 28rpx rgba(180,83,26,.28)` | 主色通道底栏 |
| `--shadow-float` | `0 6rpx 16rpx rgba(0,0,0,.12)` | 浮起件（主 CTA 胶囊） |

## 1.7 输入项（下划线语言，全站统一）

- **形态**：`background: transparent` + `border: none` + **仅底部 `1rpx solid var(--border-color)`**。
- **聚焦**：底线转 `--color-primary`（**无闪烁、不加外描边**）。
- **行内列表用法**（如个人信息编辑）：底线由**所在行的行分隔线**承担，聚焦改该行 `border-bottom-color`（避免「输入底线 + 行分隔线」双线）。
- **输入框聚焦时 SHALL NOT 呈现主色描边**：`input / textarea` 的 `:focus` 与 `:focus-visible` 统一 `outline: none`，输入态由容器自身样式表达。
- 占位文字色 `--text-placeholder`。

## 1.8 卡片

- **唯一实现 = 公共 `CardSection`**；`--bg-card` + `--radius-card`(16rpx) + `--shadow-card`；内距 `--spacing-md`，外距 `--spacing-sm --spacing-md`（首个卡片上边距收紧为 `--spacing-md`）。
- `flush` = 去掉自身外边距（块间距由父级统管）。
- 页面可抬特异度覆盖内距（如前缀页面根改 `--spacing-lg`）；**SHALL NOT 手写卡片三件套另起一套壳**。

## 1.9 分隔线

| 场景 | 口径 |
|---|---|
| 列表行分隔 | `1rpx solid var(--border-color)`；**最上 / 最下无线** |
| 卡内段间分隔 | `1rpx`（`--border-color`） |
| 输入项底线 | `1rpx`（见 §1.7） |
| chip 描边 | `2rpx`（**刻意**：67rpx 等高 chip，描边不得去掉） |
| box 描边 / 弹层头部 / 固定底栏顶边 | `2rpx`（非「输入底线 / 列表分隔线」语义） |
| 虚线框（上传添加格） | `1rpx dashed var(--border-color)` |

## 1.10 状态口径

| 状态 | 口径 |
|---|---|
| 按压 | 事件统一 **`@tap`**（禁 `@click`）；反馈 = `hover-class` 的 **`opacity` 微降**（全局兜底 `.pressed { opacity: 0.7 }`）或 **`--bg-soft` 底色**；**SHALL NOT `transform: scale`**、**SHALL NOT 裸色值**（如 `rgba(0,0,0,.08)`）。不透明度参考档：整卡 / 大面 `.6`~`.7`｜列表行 `.7`｜小图标钮 `.5`~`.6`｜主 CTA 胶囊 `.85` —— **同族元素必须取同值** |
| 禁用 | 全局唯一令牌 **`.is-disabled { opacity: .5; pointer-events: none; filter: grayscale(.2) }`**，组件不得自设弱化档 |
| 键盘焦点环 | `:focus-visible:not(input):not(textarea)` → `outline: 3rpx solid var(--color-primary)` + `outline-offset: 2rpx` + `border-radius: var(--radius-xs)`（仅键盘可达时显示） |
| 触达下限 | 可点元素命中区 **≥88rpx（44pt）**；视觉尺寸可小于命中区，经 `::after` **仅纵向**扩展 |

## 1.11 动效

| token | 值 |
|---|---|
| `--duration-fast` | `120ms` |
| `--duration-base` | `200ms` |
| `--duration-slow` | `300ms` |
| `--ease-out` | `cubic-bezier(0.23, 1, 0.32, 1)` |

- **不新增装饰性入场动效**：内容一律立即可见（可见性不依赖动画）。
- **`prefers-reduced-motion: reduce` 下必须降级**：过渡取消为直接换色；旋转类指示器减速（`RetryBlock` / `ImagePicker` 环：`0.8s` → `1.6s`）。当前落地 7 处（`RetryBlock` / `ImagePicker` / `notifications` / `my-reviews` / `mine` / `HomeMealTabs` / `ImageSwiper`）。

## 1.12 层级（z-index）

| token | 值 | 层 |
|---|---|---|
| `--z-page-bg` | `-1` | 页底壁纸层（**底层**，不参与骨架排序） |
| `--z-action-bar` | `50` | 页面底部固定操作栏 |
| `--z-tabbar` | `100` | 自绘底部菜单栏 |
| `--z-header` | `100` | 吸顶顶栏 / 固定标题带（带体取 `calc(--z-header + 1)`） |
| `--z-sheet` | `2000` | 底部半屏弹层（`BaseSheet` 默认） |
| `--z-actionsheet` | `4000` | 操作菜单 / 写评价表单弹层 |

## 1.13 关键高度 token

| token | 值 | 用途 |
|---|---|---|
| `--tabbar-height` | `100rpx` | 自绘底部菜单栏 |
| `--search-bar-height` | `96rpx` | 搜索胶囊（≥ Apple 44pt 触达下限） |
| `--action-bar-height` | `120rpx` | 表单页底部固定操作栏（各页滚动区避让同源） |

## 1.14 图标

- **唯一实现 = 公共 `IconSvg`**；全部为**线性 SVG**（24px 网格、`2px` 描边、圆角端点）。
- `size`：数字 = **rpx**（`:size="40"` → 40rpx），也可传带单位字符串。
- `color`：**必须事实色**（`COLOR_MAP['xxx']` 或 `#RRGGBB`）—— SVG data-uri 是独立文档，`var(--x)` 与 `currentColor` 都会被回退为兜底近黑 `#1C1C1E`。
- **纯展示组件**：不向外派发任何事件，可点元素由父级绑定 `@tap`。
- 组件**未开 `virtualHost`** ⇒ flex 父级里多一层宿主节点；需竖直居中时由消费方把宿主定为 flex 盒。
- **SHALL NOT** 用 emoji / 文本 / `content:'+'` 当图标；**零消费的图标键不登记**。

## 1.15 图片占位（全站统一）

**唯一实现**：公共 `components/ImagePlaceholder.vue` —— **灰底 `--bg-placeholder` + 居中图标**。

| 场景 | 图标 | 说明 |
|---|---|---|
| **图片缺失 / 加载失败 / 破图**（菜品图、评价配图、Banner、轮播、上传缩略图） | **`image-broken`（图片破损）** | 默认值；全站标准占位 |
| **头像无值 / 加载失败** | `user`（人形） | **唯一语义例外**：「无用户」≠「图片损坏」；**底色仍与全站一致** |

- `size` 默认 `64rpx`；**头像占位图标边长恒取头边长的 50%**（64rpx 圆 → 32rpx；104rpx 方图 → 52rpx；120rpx 圆 → 60rpx），并须由宿主容器裁到自身圆角内（`overflow: hidden`）。
- 消费方：`ImageFallback`（头像）、`DishCard`、`DishResultCard`、`ImageSwiper`、`ImagePicker`、`ReviewItem`、`HomeBanner`。
- **SHALL NOT** 各页自绘占位、**SHALL NOT** 再用 `dish` / `empty` 等图标顶替、**SHALL NOT** 自定义占位底色。

## 1.16 原生 API 例外登记（不接受 `var()` 的常量）

| 常量 | 值 | 场景 |
|---|---|---|
| `SWIPER_INDICATOR_ACTIVE_COLOR` | `#ffffff` | 原生 `<swiper>` 指示点 |
| `SWIPER_INDICATOR_COLOR` | `rgba(255,255,255,0.4)` | 原生 `<swiper>` 指示点 |
| `MODAL_CONFIRM_DANGER_COLOR` | `#C62828` | `uni.showModal` 危险确认钮 |
| `MODAL_CONFIRM_PRIMARY_COLOR` | `#B4531A` | `uni.showModal` 重要确认钮 |
| `ICON_FALLBACK_COLOR` | `#1C1C1E` | `IconSvg` 描边兜底色 |

> 除上表外，**页面 / 组件 SHALL NOT 裸 hex**。

---

# 二、公共组件视觉规格

> 17 个跨页复用组件。**各页设计稿只写「本页用法」**，规格一律以本表为准。

## 2.1 `CardSection` —— 卡片外壳（全站唯一实现）

- **规格**：`--bg-card` + `--radius-card`(16rpx) + `--shadow-card`；内距 `--spacing-md`；外距 `--spacing-sm --spacing-md`（首卡上边距 `--spacing-md`）。
- **变体**：`flush` ⇒ 外边距归零。
- **消费方**：菜品详情（信息 / 评价两卡）、我的主页（身份卡 + 评价列表卡）。

## 2.2 `SectionTitle` —— 分区标题

- **规格**：无竖线纯文本；flex 行 + `gap: --spacing-xs`；默认 `margin-bottom: --spacing-sm`。
- **标题**：`--font-h2`(40rpx) + `--weight-heavy`(800) + `--text-primary` + `--tracking-h2`；单行省略。
- **计数** `count`（可选）：`--font-h3`(36rpx) + `heavy` + **与标题同色** + 等宽数字；`null` 不渲染（在途 / 失败不显示数字）。
- **`#extra` 具名槽**：右侧附加信息（如「写评价」轻量入口 / find 页「清空」）；消费方均为本组件**直接**使用方，无跨层 slot 塌缩风险。
- **`noMargin`**：去掉标题下外边距（间距交由容器自持）。

## 2.3 `AppHeader` —— 二级页顶栏（带返回）

- **规格**：`position: sticky; top: 0` + `--z-header`；**恒透明**（不含底色 / 蒙版）；底部留白 `--spacing-sm`；顶部安全区 `max(statusBar, env(safe-area-inset-top))`。
- **左「返回」**：**文字**（非图标），`--font-title`(44rpx) + `bold`；命中区撑满导航行高（≥88rpx）；按压 `opacity: .6`。
- **标题**：有返回 ⇒ **绝对居中**（`--font-title` + `bold`，`max-width: 56%` 不侵微信胶囊）；`showBack=false` ⇒ **居左**（与 `AppTitleBand` 同位）。
- **右操作**：默认槽 + `#action` 具名槽；右边界 = 胶囊避让量 `--nav-pr`。
- **与 `AppTitleBand` 的分工（用户裁定）**：**两个组件、按需显示、不合并** —— 有返回用本组件；无返回用 `AppTitleBand`。
- **消费方**（7 处）：`profile` / `privacy/DocPage` / `notifications`（带右操作槽）/ `my-reviews` / `feedback` / `auth` / `mine`（`show-back=false`）。

## 2.4 `AppTitleBand` —— 固定标题带（无返回）

- **规格**：`position: fixed; top: 0` + `calc(--z-header + 1)`；**恒透明、不铺任何表面**；`pointer-events: none`（可点件单独抬回）。
- **布局**：本层**不设左右内距**（否则平移「绝对居中」基准）；左间距由 `.band-left` 的 `padding-left: --spacing-md` 承担（= 页面 gutter，与卡片左缘同轴）。
- **三处文字**（左页面名 / 左「返回」/ 居中页面名）**字号唯一来源 `--font-title`(44rpx) + `bold`**，视觉完全同级。
- **`titleOpacity`**：只控**居中标题文字**透明度（供「滚动后才出现标题」），**不是**表面透明度。
- **消费方**：`home`（无返回 ⇒ 左区「知行食记」）、`find`（有返回）。

## 2.5 `AppButton` —— 主按钮（全站唯一实现）

- **规格**：高 `88rpx`；`--radius-btn`(16rpx)；`--color-primary` 实底；文本 `--font-subtitle` + `--weight-medium` + `--color-on-primary`。
- **状态**：`disabled` ⇒ 复用全局 `.is-disabled`（不自设弱化档）；`loading` ⇒ `opacity: .6` + `pointer-events: none`。
- **事件**：`@press`（禁用原生事件名，避免 mp-weixin 编译期丢参）；`disabled` / `loading` 时 `handleTap` 直接 return。
- **无障碍**：`role="button"` + `tabindex="0"` + `aria-label` + `aria-disabled` + `aria-busy`。
- **消费方**：`profile` / `feedback` / `correction` / `auth`。

## 2.6 `EmptyState` —— 空态

- **规格**（默认）：无底色居中列；`gap: --spacing-xs`；`padding: --spacing-xl 0`。
- **`card` 变体**：`padding: --spacing-xl --spacing-lg` + `--bg-card` + `--radius-card` + `--shadow-card`（**仅整屏居中的主空态**用；区块内小空态保持无底色）。
- **文字**：主文案 `--font-body` + `medium` + `--text-secondary`；次文案 `--font-aux` + `--text-tertiary`。
- **图标**：`size` 默认 `44rpx`，色 `--text-tertiary`；不传则不渲染。
- **CTA**（传 `actionText` 才渲染）：`--color-primary` 实底 + `--radius-btn` + `padding: --spacing-2xs --spacing-md`；文本 `--font-small` + `semibold` + `--color-on-primary`；按压 `opacity: .85`。
- **与 `RetryBlock` 的分工**：本组件 = **空态**（无数据但一切正常，通常不可点）；`RetryBlock` = **失败态**（凹陷卡 + 整块可点 + 重试语义）。**渲染优先级：失败态 > 空态 > 列表**。
- **消费方**：`notifications`、`my-reviews`。

## 2.7 `RetryBlock` —— 加载失败块（全站唯一实现）

- **规格**：`--bg-soft` 凹陷面 + `--radius-card`；`padding: --spacing-xl --spacing-lg`；`gap: --spacing-xs`；`margin` 变体 ⇒ `margin-top: --spacing-lg`。
- **文字**：标题 `--font-body` + `semibold`（`--text-secondary`；`strong` 变体升为 `--text-primary`）；副文案 `--font-aux` + `--text-tertiary`。
- **图标**：`report`，`44rpx`（`strong` 变体 `96rpx`、`padding: --spacing-2xl --spacing-xl`、`gap: --spacing-sm`）。
- **`loading`**：以纯 CSS 旋转环（`44rpx`、`border: 4rpx`、`border-top-color: --color-primary`、`0.8s linear infinite`）替换图标，副文案改「正在重新加载…」，**忽略点击**。⚠️ 页面级仍不设骨架屏 / loading 指示 —— 本环属**用户主动点击后的在途反馈**。
- **双 CTA 形态**（传 `primaryText` / `secondaryText`）：块本身不可点，渲染两枚 `88rpx` `--radius-pill` 胶囊（主按钮 `--color-primary` + `--shadow-float`）。
- **按压**：整块 `opacity: .7`；按钮 `opacity: .85`。
- **`prefers-reduced-motion`**：旋转环时长 `0.8s → 1.6s`。
- **消费方**：`my-reviews` / `notifications` / `HomeContent` / `find`（整块可点）；`detail/dish`（双 CTA）。

## 2.8 `BaseSheet` —— 底部弹层骨架（唯一真源）

- **遮罩**：`--overlay-scrim`；`z-index: calc(zToken − 10)`；点击关闭；`touchmove.stop` 防穿透。
- **抽屉**：`--bg-card` + `border-radius: --radius-modal --radius-modal 0 0` + `--shadow-modal`；`max-height: 88vh / 88dvh`；`padding-bottom: env(safe-area-inset-bottom)`；`translateY` 上滑开合。
- **grabber**：`72 × 8rpx`、`--overlay-dark-soft`、`--radius-pill`、`margin: --spacing-sm auto 0`。
- **可选头部**：`padding: --spacing-sm --spacing-md` + `border-bottom: 2rpx`；标题 `--font-subtitle` + `semibold` + `--text-primary`；关闭钮 `close` `36rpx`（`--text-tertiary`）、`active opacity: .5`。
- **内容区**：默认普通容器；`scrollBody` ⇒ `scroll-view` + `padding: --spacing-md --spacing-lg calc(--spacing-lg + safe)`。
- **下拉关闭手势**：1:1 跟随 + 速度投影；**位移 >120rpx 或速度 >480px/s** 关闭。
- **`keyboardLift`**（默认关）：监听 `uni.onKeyboardHeightChange` 抬升抽屉；开启时内部输入框须同时设 `:adjust-position="false"`。
- **无障碍**：`role="dialog"` + `aria-modal="true"`；开合前后自动捕获 / 还原焦点。
- **props**：`zToken` 默认 `--z-sheet`；`closable` 默认 `false`。
- **消费方**：`ActionSheet` / `ReviewComposer` / `ReportModal` / 楼层选择 / 属性选择。

## 2.9 `ActionSheet` —— 动作菜单

- **骨架**：复用 `BaseSheet`，`z-token="--z-actionsheet"`。
- **规格**：选项区 `padding: --spacing-sm 0`；每项高 `104rpx`、`padding: 0 --spacing-lg`、`gap: --spacing-md`；文本 `--font-body` + `--weight-medium`；图标 `34rpx`。
- **分隔线**：**仅相邻项之间** 1rpx `--border-color`（各 row 统一 `border-top`、首行清除）。
- **按压**：`active opacity: .7`。
- **取色契约**：`iconColor` **必须实色**（类型收紧为 `COLOR_MAP` 色值联合，传 `var(...)` 直接编译报错）；`textColor` 走 CSS `var()` 语义 token（**两者口径不同，勿混用**）。
- **事件**：点击 ⇒ `select(key)` + `close()`。
- **消费方**：菜品详情（举报 / 删除评价）、我的主页（删除评价）、菜品纠错（楼层 / 属性弹层为 `BaseSheet` 直用）。

## 2.10 `IconSvg` —— 矢量图标（唯一实现）

见 §1.14。

## 2.11 `ImagePlaceholder` —— 图片占位（唯一实现）

见 §1.15。

## 2.12 `ImageFallback` —— 头像兜底

- **规格**：容器 `100% × 100%` + `overflow: hidden`（圆角由宿主裁切）。
- **行为**：有 `src` 且加载成功 ⇒ `aspectFill` 展示；无值 / `@error` ⇒ `ImagePlaceholder name="user"`（`64rpx`，灰底 `--bg-placeholder`）。**禁裂图**。
- **消费方**：`mine`、`my-reviews`、`profile`、`ReviewItem`。

## 2.13 `ImagePicker` —— UGC 配图

- **规格**：3 列网格，`gap: --spacing-sm`，格宽 `calc((100% - 32rpx) / 3)`；每格为正方等比盒（`padding-bottom: 100%`）+ `--radius-card` + `overflow: hidden` + 底 `--bg-placeholder`。
- **删除钮**：`44rpx` 圆底 `--badge-dark-bg` + 白叉 `22rpx`；右上 `8rpx`；命中区经 `::after` 扩至 `88rpx`；`active opacity: .7`。
- **添加格**：`1rpx dashed --border-color` + `image` 图标 `48rpx`（`--text-tertiary`）+ 文案「添加图片」(`--font-aux`)；按压 `opacity: .6`；上传中 `opacity: .55` + CSS 环 +「上传中…」。
- **满额计数格**：`--bg-input` + `2rpx dashed` + `n/n`（`--font-aux`、等宽数字）。
- **规格约束**：≤ `max` 张（默认 3）；最长边 ≤1334px；文件 ≤1MB（超限逐张 toast 跳过，不中断其余）。
- **`prefers-reduced-motion`**：旋转环 `0.8s → 1.6s`。
- **消费方**：`ReviewComposer`、`IssueForm`、`CorrectionForm`。

## 2.14 `PageWallpaper` —— 页底壁纸 + 纱

- **结构**：`<image>`（`/static/images/home-bg.jpg`、`aspectFill`）+ 纱层 `background-color: var(--page-wash)`。
- **层级**：`z-index: var(--z-page-bg)`（−1）+ `pointer-events: none`；默认 `absolute`（供容器裁切），`fixed` 变体 = 页面级视口锚定。
- **高度**：`heightPx` 未下发时自测 `getWindowInfo().windowHeight`（兜底 `812`）。
- **消费方**：全部页面（页面级 `fixed`）；`home` 另有吸顶容器切片用法。

## 2.15 `ReviewItem` —— 单条评价卡

- **规格**：头像 `64rpx` 圆（`--radius-circle` + `overflow: hidden` + 底 `--bg-soft`）；`flat` 模式无自身内距（行内距 + 1rpx 分隔线由**宿主列表卡**给）。
- **视角变体**：`mine`（本人视角）⇒ 不渲染头像 / 昵称；`dish-name` prop 注入关联菜名行。
- **三点**：`more-v` 常驻右上角，命中区 ≥88rpx，`@more` 上抛。
- **配图**：3 等分小方图；破图走 `ImagePlaceholder`（`image-broken`）。
- **按压**：整卡 `opacity` 微降（**非** scale）。
- **消费方**：菜品详情评价区、我的主页评价区。

## 2.16 `SearchBar` —— 搜索胶囊（跨页唯一实现）

- **规格**：高 `--search-bar-height`(96rpx)；`--bg-card` + `--radius-pill` + `--shadow-card`；内距 `--spacing-xs --spacing-xs --spacing-xs --spacing-md`；`gap: --spacing-sm`；宿主左右 gutter `--spacing-md`。
- **结构**：① 放大镜 `40rpx`（`--text-placeholder`）→ ② 输入框 / 占位文案（`--font-body`；占位 `--text-placeholder`、已输入 `--text-primary`）→ ③ 清除 `✕`（`56rpx` 圆命中盒 + `30rpx` 图标，`active opacity: .55`）→ ④ **内嵌「搜索」按钮**（`align-self: stretch` + `padding: 0 --spacing-lg` + `--radius-pill` + `--color-primary-fill`；文本 `--font-body` + `semibold` + `--color-on-primary`）。
- **状态**：`searching` ⇒ 按钮 `opacity: .6` + 禁点；`disabled`（空词）⇒ 按钮底 `--bg-input` + 文字 `--text-tertiary` + 禁点（**不让点击静默失效**）；`entry` 模式整条胶囊可点（按压 `--bg-soft`），`input` 模式可输入。
- **不做下拉箭头**；**不做**「左胶囊 + 右独立按钮」两颗并列。
- **消费方**：`home`（`mode="entry"`）、`find`（`mode="input"`）。

## 2.17 `TabBar` —— 底部菜单栏

- **规格**：`position: fixed; bottom: 0` + `--z-tabbar`；高 `calc(--tabbar-height + env(safe-area-inset-bottom))`；**恒透明**（背后即页底壁纸）+ `border-top: 1rpx --border-color` + `--shadow-bar`。
- **每项**：`flex: 1` 纵向居中 + `gap: --spacing-xs` + 高 `--tabbar-height`。
- **图标**：`48rpx`；选中 ⇒ `-filled` 变体 + `--color-primary-bright`；未选 ⇒ `--text-tertiary`。
- **标签**：`--font-small`；选中 ⇒ `--color-primary-text` + `--weight-semibold`。
- **显示控制**：仅主根页可见；`reLaunch` 切页 + 路由拦截器（`navigateTo` / `redirectTo` / `reLaunch` / `switchTab` / `navigateBack`）按目标 URL 判定显隐。
- **无 props**（壁纸切片与白底分支均已按「零消费即删」移除）。
- **消费方**：`home`、`mine`。

---

# 三、跨页版面：身份卡（用户信息模块）

「我的」页用户信息模块与「我的主页」用户信息卡是**同一身份版面的两种形态**（前者 = 卡片 A 内第 1 段、整段热区 → 我的主页；后者 = 独立一级卡 + 右侧动作位）。两页**同源同字符串**，各页文档只写自身差异。

**内容口径**（两态同结构：头像 / 主行 / 副行 / 右侧）

| 位置 | 认证态（`bindEmail` 非空） | 游客态（未认证） |
|---|---|---|
| 头像 | 头像；空 / 失败 → 统一占位 | 同左（多为静默登录，无头像） |
| 主行 | `nickname`（空值 → 「食客」） | `nickname`（空值 → 「游客」） |
| 副行 | 校园邮箱 `bindEmail`（空值回落 `--`） | 恒「未完成校园认证」 |
| 右侧 | 「我的」页 = `arrow` 图标；「我的主页」=「编辑个人信息」描边胶囊 | **两页均不渲染动作位** |
| 顶部 6rpx 条纹 | `--color-primary-soft` | transparent（**占位保留**，两态等高） |

- **副行语义**：认证态 = 身份**凭据**（证明「我是谁」）；游客态 = 身份**状态**（说明「我缺什么」）。
- 游客态 **SHALL NOT** 展示端上派生的内部编号（「食客 + `id` 尾 4 位」一类伪标识既不可被用户使用，也不表达其处境）。
- **编辑入口**：编辑身份信息是**认证态才具备的能力** ⇒ 「编辑个人信息」胶囊**仅认证态渲染**，游客态无任何入口（对应 [client-个人信息编辑.md](./client-个人信息编辑.md) 的入口口径为「仅认证态可达」）。
- 认证动作**单一入口** = 「我的」页宫格「身份认证」格；身份卡**不放置**「去认证」入口。

**排版参数**（两页同值）

| 项 | 值 |
|---|---|
| 头像 | 120rpx 圆形（`--radius-circle`） |
| 头像空值 / 失败占位 | 公共 `ImagePlaceholder`（`name="user"`、图标 60rpx、灰底 `--bg-placeholder`，宿主裁圆） |
| 头像 → 文字间距 | `--spacing-md` |
| 主行 | `--font-subtitle` / `--weight-semibold` / `--text-primary`，单行省略 |
| 副行 | `--font-aux` / `--text-tertiary`，单行省略 |
| 主行 ↔ 副行距 | `--spacing-sm` |
| 卡内上下内距 | `--spacing-lg` |
| 顶部条纹 | 高 6rpx，两态恒在（游客态 transparent ⇒ 两态等高），须由卡片圆角裁切 |
| 「编辑个人信息」胶囊 | `--font-tiny`、主色描边、`--radius-pill`；命中区经 `::after` **仅纵向**扩至 ≥88rpx（视觉尺寸不变） |
| 按压反馈 | `--bg-soft` 底色（**非** scale） |

---

# 四、本文件的维护口径

1. **新公共组件**：先进本文件登记规格，再被页面消费。
2. **页面改动**：若某页需要偏离本文件口径，**先修订本文件**（或在本文件登记该页例外）再动代码。
3. **页面设计稿**：不得复述本文件的组件规格；引用写法 = `见 [client-公共组件与形态基线.md](./client-公共组件与形态基线.md) §2.x`。
4. **改视觉值只改 token**：形状 / 间距 / 字号 / 色彩一律改 `theme/tokens.ts` 或 `App.vue` 的 token 块，不在页面里裸写。

---

# 五、已知待办（待用户拍板 / 待验证）

| # | 事项 | 现状 | 影响面 |
|---|---|---|---|
| 1 | **`App.vue` 注释「不提供减少动态效果降级分支」与代码相反** | 实际已有 7 处 `prefers-reduced-motion` 降级（§1.11）；代码注释待更正 | `client/src/App.vue` 注释一处 |
| 2 | **client 源码注释内的旧编号引用残留** | 文档侧已全部改指本文件；`client/src` 注释仍约有 50 处 `spec §4.x` / `project_spec.md`（该文件已不在仓库）需改指本文件对应节 | client 源码注释 |
| 3 | **页面设计稿瘦身** | 各页「组件清单」表改为引用本文件 §二，只保留「本页用法」 | 12 份页面设计稿 |
