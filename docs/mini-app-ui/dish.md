# 菜品详情 (dish)
- 路由: /pages/pages-detail/dish
- 分包: pages-detail 分包（实际 src 路径 pages/pages-detail/dish.vue）
- 源文件: src/pages/pages-detail/dish.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
菜品详情：图集 + 名称/价格（限时促销）/标签/评分 + 「菜品信息」合并卡（位置与营业 / 菜品属性 / 菜品介绍 三分区同卡）+ 用户评价预览 + 弱化反馈入口；底部固定操作栏（喜欢/写评价/去档口）；分享面板。

## 2. 布局结构
- 顶部：`Header title="菜品详情" showBack`，action 槽放 `share-btn`(share 图标，≥44pt 命中区)。
- 滚动区 `scroll-view`（refresher）。
- 三态：
  1. 加载中：`.dish-skeleton`（swiper/两张卡片骨架，pulse 动画）。
  2. 正常：`ImageSwiper` → `CardSection`(名称+价格+标签+评分) → `CardSection title="菜品信息"`(合并三分区) → `CardSection`(用户评价 SectionTitle+前 3 ReviewItem+查看全部) → 弱化 `.apply-link`(反馈/申请下架)。
  3. 失败：`EmptyState text="菜品不存在或已下架"`。
- 底部 `.action-bar`（fixed）：`fav-btn`(heart) + `AppButton` 写评价(outline) / 去档口（单主 CTA「去档口」）。
- `ApplySheet`（DISH）、分享面板（mask + sheet，下拉关闭手势 + ic-close）。
- 信息架构：相关信息合并为单张「菜品信息」卡（位置与营业 / 菜品属性 / 菜品介绍），不拆多个独立卡；反馈入口弱化为底部小文字链接。

## 3. 核心组件与用法
- `ImageSwiper`：菜品图集。
- `TagLabel`：口味标签。
- `CardSection` / `SectionTitle`：分区卡片与标题（accent 条）。
- `ReviewItem`：评价项（前 3）。
- `AppButton`：写评价 / 去档口（主+outline 次级）。
- `ApplySheet`：申请下架/纠错弹层（spring 0.8/0.3 + ic-close + reduced-motion）。
- 分享面板：自绘 `.share-sheet` + `.sheet-mask`（下拉关闭手势，阈值 120px，从底部进入）。
- `IconSvg`：`share`(顶/分享项)、`clock`(限时/营业/供应)、`star-filled`(评分)、`heart`/`heart-filled`(喜欢)、`location`/`arrow`(信息行)、`chili`/`portion`(属性)、`close`(分享关闭)——均注册 key。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--text-primary/secondary/tertiary`、`--text-white`(分享图标)。
- 强调/状态：`--color-price`(正常价)、`--color-error`(促销价/限时 tag/申请链接未用)、`--color-hot`(限时图标)、`--color-like`(已喜欢)、`--color-primary`(查看全部/位置图标/去档口)、`--color-star`(评分星)、`--color-star-empty`(未用)、`--text-tertiary`(弱化申请链接)。
- 边框/圆角：`--border-color`、`--radius-card`、`--radius-modal`(分享顶)、`--radius-icon`、`--radius-tag`(促销 tag)、`--glass-highlight-soft`(操作栏顶边)。
- 阴影：`--shadow-card`、`--shadow-bar-soft`(操作栏)、`--shadow-modal`(分享)。
- 间距：`--spacing-xs/sm/md/lg`（4/8pt 节奏）。
- 字号：`--font-h1`(菜名)、`--font-h2`(价格)、`--font-body`、`--font-caption`、`--font-aux`、`--font-tiny`(申请链接/喜欢文字)。
- 动效：`--press-scale`(信息行 tap/fav/分享项/action-bar)、`--ease-out`、`--ease-drawer`(分享 `cubic-bezier(0.32,0.72,0,1)`)、`--press-transition`、字距 `--tracking-h3`(菜名，标题负字距)。
- 布局：`--action-bar-height` + `env(safe-area-inset-bottom)`（scroll-wrap 与 action-bar 避让）；分享 `padding-bottom` 安全区。

## 5. 交互与动效
- 限时促销：promoPrice 非空显示促销价+划线原价+「限时」tag（clock 图标，color `--color-hot`）。
- 信息行：位置/营业可点跳 canteen/stall；弱化为底部小文字链接「反馈/申请下架」开 ApplySheet。
- 喜欢：乐观切换 `liked`（heart↔heart-filled，color `--color-like`，即时反馈）。
- 分享面板：下拉关闭手势（仅向下、阈值 120px、回弹）、ic-close、mask 点击关闭；reduced-motion 降级（去 transform，交叉淡入）。
- 底部栏：写评价(requireAuth)→review；去档口→stall。
- 长按菜名：仅作者本人可删（showModal）。
- 进场：区块 `enter-up` 错峰（30–80ms），仅 transform/opacity，无 scale(0)。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ share/clock/star-filled/heart/heart-filled/location/arrow/chili/portion/close 均 IconSvg；无 emoji。
- ②金额 api 层：✅ 价格以「元」展示（promoPrice/price 已 api 层转元），页面无 `/100`。
- ③WaterfallList 禁 slot：➖ 详情页无瀑布流（用 CardSection + ReviewItem），不适用。
- ④三态齐备：✅ 骨架 / EmptyState / 正常态；评价区空有 EmptyState。
- ⑤Sheet 规范：✅ ApplySheet 套通用弹层；分享面板自绘但遵循 spring(0.8/0.3 抽屉缓动)+ic-close+下拉手势+reduced-motion 降级，同源规范。
- ⑥按压 0.97：✅ 信息行 tap/fav/分享项/action-bar 均 `scale(var(--press-scale))`；弱化为 opacity（合规）。
- ⑦颜色 token：✅ 无裸 hex（促销/限时 `--color-error`/`--color-hot`；喜欢 `--color-like`；评分 `--color-star`）。
- ⑧SectionTitle：✅ 用户评价区用 SectionTitle（noMargin）；菜品信息卡用 CardSection title（accent 标题）；合并三分区同卡，信息架构合规（未拆多个独立卡）。
- ⑨底部避让：✅ action-bar 与 scroll-wrap 用 `--action-bar-height` + 安全区；分享面板含安全区 padding。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：弱化「申请上架」链接用 --text-tertiary/--font-tiny，建议验证其对比度与最小字号(§8⑥⑯)；价格/优惠价/评分数字建议 tabular-nums 防位移(§8⑮)
- 计划调整：

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | fav/写评价/去档口/share/信息行 ≥44pt；间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | 信息行/fav/分享项/action-bar `scale(var(--press-scale))` + `--press-transition` |
| 3 | 固定栏/导航预留安全区 | 合规 | action-bar `--action-bar-height` + `env(safe-area-inset-bottom)`；Header 含状态栏；分享面板安全区 padding |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | share/clock/star-filled/heart/heart-filled/location/arrow/chili/portion/close 均注册 key；无 emoji |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)`；促销/限时/喜欢/评分均语义 token |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；弱化申请链接 `--text-tertiary` 对比建议验证（见 §8⑯） |
| 7 | 不靠颜色 alone 传意 | 合规 | 喜欢 heart 图标+（可选文字）；促销有「限时」文字+tag；状态均含文字 |
| 8 | prefers-reduced-motion 处理 | 合规 | ApplySheet/分享面板降级交叉淡入；骨架 pulse 关动画；保留 opacity |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch；无裸 hover |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | Sheet 抽屉 enter~300ms、exit 短；press 120ms；喜欢即时 |
| 11 | 自定义缓动，禁 ease-in | 合规 | Sheet/分享 `--ease-drawer`/`--ease-out`；无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | 区块 enter-up 用 translateY+opacity（非 scale(0)）；Sheet 自底部 |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 无 `transition:all`；动画均 transform/opacity |
| 14 | 可中断动效 | 合规 | Sheet/分享手势可中断；transition 可重定向 |
| 15 | 数字 tabular-nums | 部分 | 价格/promoPrice/评分值建议 `tabular-nums` 防位移 |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 部分 | 弱化申请链接 `--font-tiny`(20rpx) 仅作辅助；主文 ≥32rpx；4/8pt 间距 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 主「去档口」+ outline「写评价」；申请下架弱化为小文字链接（非大板块，符合信息架构） |
| 18 | loading/empty/error 三态 | 合规 | 骨架 / EmptyState / 正常态；评价区空有 EmptyState |
| 19 | 表单无障碍（label/必填/校验/键盘） | ➖ | 无表单 |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 详情页无 tab 栏；返回由 Header |
| 21 | 一致性打磨 | 合规 | 信息架构合并卡（§信息架构要求）；按压统一 0.97；Sheet/分享同源；进场 stagger 不挡交互 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：图集/合并信息卡不溢出；底部操作栏三按钮不挤。
- [ ] reduced-motion：ApplySheet/分享面板交叉淡入；骨架 pulse 停。
- [ ] 动态字号：菜名/价格/介绍不截断。
- [ ] 暗色对比：弱化申请链接 `--text-tertiary` 对比（建议改 `--text-secondary`）；价格/评分文字达标。
- [ ] 44pt：fav/写评价/去档口/share/信息行 ≥44pt。
- [ ] 安全区：action-bar 与底部内容不被 home indicator 遮挡；分享面板安全区 padding。
