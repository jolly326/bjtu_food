# 食在交大小程序端 UI 设计方案（定稿）

小程序端（frontend/src，uni-app）全部核心页面的最终 UI 设计方案，经用户与 UI-UX 设计师讨论定样。

- 执行依据：`tasks/task-13-miniapp-ui-design.md`（含逐页整改清单 + 验收门禁）
- 最高红线：`project_spec.md` §4（尤其 §4.9 小程序 slot 红线、§3 金额 api 层统一）

---

## 0. 设计基线

### 0.1 实际页面目录
- **TabBar**：`home/index.vue`、`find/index.vue`、`community/index.vue`、`profile/index.vue`
- **详情**（pages-detail/）：`dish.vue`、`canteen.vue`、`stall.vue`、`moment.vue`、`review.vue`、`review-list.vue`、`contact.vue`
- **社区**：`publish-moment/index.vue`、`my-moments/index.vue`
- **我的子页**（profile/）：`publish-dish.vue`、`submit-stall.vue`、`my-publish.vue`、`my-submissions.vue`；另有 `settings/index.vue`、`feedback/index.vue`
- **工具**：`webview/index.vue`

### 0.2 基础组件（现存清单，已与代码库对齐）

> **校准说明（重点）**：上一轮死代码清理提交 `f9560c6` 已**删除** `CategoryTabs.vue`、`Loading.vue`、`StallCard.vue`（457 行）、`StatsCard.vue` 四个文件。本文档以 `frontend/src/components/` **当前真实现存**组件为准，不再把已删组件当作"已有组件"引用。被删组件若业务仍需，列为"待重新抽组件实现"（见各页组件拆分与文末待裁定项），不臆测其已存在。
>
> **两个易混概念必须区分**：
> - **「加载态」是全局强制三态之一**（全局统一约束 §0.4）：列表/详情页必须齐备 **Loading 态（加载中）/ EmptyState（空态）/ 正常态**。这与"Loading 组件"是两回事。
> - **「Loading 组件」已被删除**：原 `Loading.vue` 不复存在，页面不可再 `import Loading`。加载态改用以下任一方式落地：① 页面/组件内联 loading 骨架（`<view v-if="loading">…</view>`）；② 复用 `EmptyState` 之外的轻量 inline 占位（图标 `ic-loading` 若已登记）；③ 列表触底 footer「加载中/没有更多」文案态。具体实现方式由开发工程师按页选定，**但"加载中三态"这一要求不随组件删除而取消**。

**现存组件（20，可直接复用）**：
AppButton / CardSection / CustomTabBar / DishCard / EmptyState / header / IconSvg / ImageFallback / ImageSwiper / ImageUploader / MomentCard / Rating / RelatedPickerSheet / SearchBar / **SectionTitle** / **StallCardSingle**(单列档口卡，原 `StallCard` 已删，canteen 用此组件) / StatusBadge / TagLabel / UsefulButton / WaterfallList。

**已删组件（f9560c6，禁止再引用）**：CategoryTabs、Loading、StallCard、StatsCard。
- `CategoryTabs` → 分段/分类选择改用现存的 `SegmentTabs`（社区分段已抽）或内联 `SectionTitle`+宫格；find 分类宫格不抽 `CategoryTabs`，直接内联实现（§1.2）。
- `Loading`（组件）→ 见上方"加载态 vs Loading 组件"说明，改用内联态。
- `StallCard`（双列版）→ 已删，**档口卡统一用 `StallCardSingle`（单列）**，canteen/stall 均复用它（§2.2/§2.3）。
- `StatsCard` → profile 统计改为内联 `StatsRow` 或直接在用户卡内呈现（§1.4），不再抽 `StatsCard`。

> **与 project_spec.md §4.2 的对齐**：§4.2 第 61 行仍将 `CategoryTabs/EmptyState/Loading` 列为"新页面必须复用"，属旧清单。**以本文件 §0.2 校准版为准**（已向技术负责人报备，待 project_spec 同步删除已删组件名）。自研组件应以代码库现存为准，新页面须复用本清单组件，禁止重复造轮子。

### 0.3 全局视觉 Token（可核对清单，单位 rpx；与 project_spec §4.2 一致）

> 所有数值以 §4.2 为唯一红线。小程序端一律用 **rpx**（750rpx 视口），不使用 px。以下为可直接 QA 核对的清单。

| Token | 值 | 用途 / 备注 |
|---|---|---|
| 主色（品牌深红） | `#6B1010` | 同 Web 侧栏；小程序用于 accent 条、关键强调 |
| `--radius-card` | `16rpx` | 卡片圆角（§4.2 卡片 16px，小程序转 rpx） |
| `--radius-modal` | 顶部 `20rpx` 圆角（`20rpx 20rpx 0 0`） | 底部弹层 / Sheet（§4.2） |
| `--radius-tag` | `8rpx` | 标签圆角 |
| `--radius-btn` | `12rpx` | 按钮圆角 |
| `--press-scale` | `0.97` | 卡片/按钮按下即时缩放（§4.4） |
| 入场位移 | `≤8rpx`（§4.9 动效从简，禁大位移/scale>1） | `enter-up` 轻量位移，配合 `--enter-i` 错峰 |
| 弹层阴影 | `0 -8rpx 30rpx rgba(0,0,0,0.12)` | 底弹层阴影（§4.2） |
| 半透材质 | `backdrop-filter: blur(20px) saturate(180%)` + 半透底 | CustomTabBar/底栏/弹层；真机不支持降级纯色半透+阴影（§4.7） |
| 字体 tracking | 大标题 `-0.02em`、正文 `0` | 系统字体优先（§4.6） |
| 分区标题 accent 条 | 宽 `8rpx` × 高 `32rpx` 品牌色、圆角 `999rpx` | 由 `SectionTitle` 组件渲染（见 §0.2），全端一致 |
| 触控命中区 | 最小 `44×44rpx` 等效（约 88rpx@2x 建议≥80rpx） | 配合按下 +~10rpx 滞回（§4.4） |
| 对比度 | 正文 ≥ `4.5:1` | 次级灰文字不得过浅（参考 ui-ux-pro-max P1） |
| 降级 | `prefers-reduced-motion` → 交叉淡入去弹性；`prefers-reduced-transparency` → 去模糊纯色 | 仅真机/小程序支持项，不支持则降级（§4.7） |

- **情感语义唯一**：喜欢=`ic-heart`、有用/点赞=`ic-thumb`（均引用 `assets/icons` 下 SVG，经 `IconSvg` 统一组件渲染，见 §0.5）。两者**互不混用**。
- **分区标题**：一律用 `SectionTitle` 组件（左竖 accent 条），禁止手写 div 模拟；首页食堂入口/热门菜品已有同款，其余页面（find/community/profile/canteen/stall/dish/review-list 等）的分区标题必须补齐同款。
  - **【定稿细则 · 禁止手写分区标题】** 任何分区/区块标题**必须**渲染 `<SectionTitle title="…" />`，**禁止**用 `<text class="section-title">…</text>` 或 `.section-head`+`.section-title` 手写竖条/纯文字标题（此类写法无 accent 条、与全端视觉不一致）。表单页（feedback / contact / publish-moment / review 等）的"区块小标题"同样走 `SectionTitle`（可用 `noMargin` 适配卡片内边距）；表单字段内的行内 label（如 review 评分/内容 label、CardSection 内部小标题）属字段语义，允许纯 text，不强制 accent 条。**当前违规点（须按本细则修）**：`pages/feedback/index.vue`（反馈类型/内容/联系方式三处）、`components/FeedbackForm.vue`（同三处）、`pages/publish-moment/index.vue`（图片/关联对象两处）仍手写无 accent 条标题，须改为 `SectionTitle`。
- **按压缩放统一值**：可点元素按下缩放**一律** `scale(var(--press-scale))`，`--press-scale` 固定 `0.97`（§0.3）。**禁止**出现 `scale(0.95)` / `scale(0.985)` / `scale(0.99)` 等非 0.97 值（这些散落值导致观感不一致，须统一为 `--press-scale` 变量）。**当前违规点**：`components/CommentItem.vue:115`（`.c-useful:active scale(0.95)`）、`frontend/src/pages/home/index.vue:387`（`.broadcast-bar.pressed scale(0.985)`）、`pages/settings/index.vue:134`（`.cell:active scale(0.99)`）、`components/ContributeSheet.vue:156`（`.sheet-option.pressed scale(0.99)`）。`CustomTabBar` 选中态 `scale(1.05)` 为"放大高亮"语义、非按压缩放，属例外允许。

### 0.4 全局统一约束
- **列表/详情页必须齐备三态**：① 加载中态（Loading 态，见 §0.2 说明，用内联态而非已删的 Loading 组件）② `EmptyState`（空态，复用 `EmptyState` 组件）③ 正常态。三者缺一即阻断级缺陷。
- 禁止 `wx:for` 内具名 slot 分发（§4.9）；列表 `v-for` 用稳定 id 作 `:key`，禁用 `index`。`WaterfallList` 已内部 `import DishCard` 直接渲染，**禁止再向其传具名 slot**，统一 `<WaterfallList :list @card-click="goToDetail"/>` 经事件上抛父级。
- 详情页底部操作栏避让须用统一 `--action-bar-height` 变量联动（含 review 提交页底部栏）。
- 金额分↔元转换仅在 api 层（`fenToYuan`/`yuanToFen`，见 `utils/money`），页面/组件禁止裸算；模板展示已为元的 `price`（不得 `/100`）。
- 分区标题统一使用 `SectionTitle` 组件（左侧竖线 accent bar），全端一致；首页食堂入口/热门菜品已有，其余页面（find/community/profile/canteen/stall/dish/review-list 等）的分区标题须补齐同款。
- **【强制红线 · 全量禁用 emoji】图标统一经 `IconSvg` 组件渲染 `frontend/src/assets/icons` 下 SVG；新增语义先登记 §0.5 表再下载 SVG。home 及全小程序**禁止** `emoji.ts` 与任何 Unicode emoji 字符当图标/语义**（§4.2/§4.9 强化为全局强制项）。缺失图标一律经 **Iconfont MCP** 拉取阿里云矢量库 SVG 补齐，不回退到 emoji。

### 0.5 图标资源规范
- **【全局强制红线 · 全量禁用 emoji】** 所有功能图标与情感图标一律使用 **SVG 矢量图标**；home 及全小程序**禁止 `emoji.ts` 与任何 Unicode emoji 字符当图标/语义**（原 §4.2/§4.9「禁止 emoji」强化为全局强制项，无例外）。缺失图标一律经 **Iconfont MCP** 拉取阿里云矢量库 SVG 补齐，不得回退到 emoji。
- **统一来源（矢量图标）**：来源优先级：① 本地 `Assets`（项目内已有图标优先复用）② 阿里云矢量库（Iconfont）通过 **Iconfont MCP** 拉取下载。设计师可经 MCP 直接拉取缺失图标。
- **视觉一致性（参考 apple-design + ui-ux-pro-max）**：图标统一线性/面性风格、2px 描边、24px 网格、圆角端点一致；语义清晰、克制，不做无意义装饰。
- **落地约束**：图标随下载落统一图标目录（如 `frontend/src/assets/icons`）经统一图标组件/字体类引用；新语义图标先登记后使用。`frontend/src/assets/icons` 已有 26 个线性 SVG（含本次经 Iconfont MCP 补充的 ic-lightbulb），其余为手写线性风格，统一 `fill:currentColor`、2px 描边、24px 网格、圆角端点一致。

**图标映射表（emoji → 语义 → 图标文件）**

| 原 emoji | 语义 | 图标文件 | 来源 |
|---|---|---|---|
| 🔔 | 广播通知 | ic-broadcast.svg | 本地（已登记） |
| 🔍 | 搜索 | ic-search.svg | 本地 |
| 📍 | 位置 | ic-location.svg | 本地 |
| ❤️ | 喜欢/收藏 | ic-heart.svg | 本地 |
| 👍 | 有用/点赞 | ic-thumb.svg | 本地 |
| 🔥 | 热门 | ic-fire.svg | 本地 |
| ⏰ | 限时 | ic-clock.svg | 本地 |
| 💡 | 猜你喜欢 | ic-lightbulb.svg | Iconfont MCP（本次新增）|
| 📤 | 分享 | ic-share.svg | 本地 |
| 💬 | 评价/评论 | ic-comment.svg | 本地 |
| ➕ | 发布 | ic-plus.svg | 本地 |
| ⚠️ | 举报 | ic-report.svg | 本地 |
| 👤 | 默认头像/无头像兜底 | ic-user.svg | 新增登记（community `MomentCard` / profile 用户卡无头像兜底用，Iconfont MCP 补齐）|
| 📭 | 空态兜底 | ic-empty.svg | 本地（已存在，`EmptyState` 复用）|
| 🔒 | 密码 / 验证码输入 | ic-lock.svg | Iconfont MCP 补齐（登记；`AuthForm` 密码/验证码字段，§1.4）|
| ✉️ | 邮箱输入 | ic-mail.svg | Iconfont MCP 补齐（登记；`AuthForm` 邮箱验证码路，§1.4）|
| ✕ | 弹层关闭 | ic-close.svg | Iconfont MCP 补齐（登记；各 Sheet/Modal 统一关闭按钮，§1.4 弹层规范）|
| 🏷️ | 品牌 logo | ic-logo.svg | Iconfont MCP 补齐（登记；`AuthForm` 未登录态品牌区，§1.4）|
| 📋 | 我的发布 / 列表 | ic-list.svg | Iconfont MCP 补齐（登记；profile 菜单「我的发布」等，§1.4 菜单组）|
| ✏️ | 编辑 / 我的提交 | ic-edit.svg | Iconfont MCP 补齐（登记；profile 菜单「我的提交/昵称编辑」等）|
| 🔔 | 通知 / 设置项 | ic-bell.svg | Iconfont MCP 补齐（登记；profile / settings 通知相关菜单，与广播 `ic-broadcast` 区分语义）|
| 💬 | 联系开发者 | ic-contact.svg | Iconfont MCP 补齐（登记；profile 菜单「联系开发者」）|
| ⚙️ | 设置 | ic-settings.svg | Iconfont MCP 补齐（登记；profile / settings「设置」入口）|
| ⭐ | 收藏 | ic-star.svg | Iconfont MCP 补齐（登记；`StatsRow` 收藏数 / 设置项）|
| 🏠 | 主页 / 我的主页 | ic-home.svg | Iconfont MCP 补齐（登记；菜单返回主页或主页类入口）|
| 🌶️ | 菜品属性·辣度 | ic-chili.svg | Iconfont MCP 补齐（登记；dish `TagLabel` 属性图标，§2.1）|
| 🍽️ | 菜品属性·份量 | ic-portion.svg | Iconfont MCP 补齐（登记；dish `TagLabel` 属性图标，§2.1）|
| 🍲 | 菜品/档口图兜底 | ic-dish.svg | Iconfont MCP 补齐（登记；dish/stall/canteen 食堂·档口图兜底，§2.1/§2.2/§2.3；语义＝「此图是菜品/档口」）|
| 🖼️ | 通用图片兜底 | ic-image.svg | Iconfont MCP 补齐（登记；图加载失败兜底；与 `ic-dish` 区分语义：`ic-image`＝通用/非菜品类图片失败，`ic-dish`＝菜品/档口图失败）|
| 🏪 | 档口数 / 档口标识 | ic-stall.svg | Iconfont MCP 补齐（**登记但 SVG 尚未拉取**，待经 Iconfont MCP 下载落 `assets/icons`；canteen 基础信息「档口数」行图标 §2.2，及 moment §2.4 关联对象卡 `ic-stall` 区分 dish/stall；若 MCP 无合适矢量则复用 `ic-list` 登记，二选一）|
| 🔙 | 返回（左箭头） | ic-arrow-left.svg | 本地（已登记；find 页返回键，区别于 `ic-back` 右箭头）|
| ➡️ | 右箭头（跳转/关联卡去往） | ic-arrow.svg | 本地（已登记；关联对象卡 `ic-arrow` 跳转指示，区别于 `ic-arrow-left` 返回；moment §2.4 关联卡、通用跳转行、各 Sheet 选项行右指示**一律用 `ic-arrow`**，**禁止**用 `ic-arrow-left` 旋转 180° 模拟右箭头——`ContributeSheet` / profile 贡献入口当前误用 `ic-arrow-left` 旋转，须改为 `ic-arrow`）|
| ↩️ | 后退（通用返回，区别于左箭头语义） | ic-back.svg | 本地（已登记；通用返回，语义区别于 `ic-arrow-left` 返回键）|
| 🔥 | 热门 | ic-fire.svg | 本地（已登记；首页热门菜品区/排序高热度标识）|
| 🏠 | 主页 / 我的主页 | ic-home.svg | 本地（已登记；菜单返回主页或主页类入口）|
| 💰 | 价格 / 人均 | ic-price.svg | 本地（已登记；stall/canteen 人均、FilterSheet 价格区标题图标）|
| ✅ | 勾选 / 确认 | ic-check.svg | 本地（已登记；选择态确认、Modal 确认）|
| 🗑️ | 删除 / 清除缓存 | ic-delete.svg | 本地（已登记；settings「清除缓存/账号注销」、列表删除）|
| 👤 | 账号 / 我的（设置项语义） | ic-profile.svg | 本地（已登记；settings「退出登录/账号」项，区别于 `ic-user` 头像兜底）|
| 🔍 | 筛选 | ic-filter.svg | 本地（已登记；find 排序/筛选触发图标）|
| 🍜 | 分类·面条 | ic-noodle.svg | 本地（已登记；find 分类宫格，禁止回退 `ic-dish`）|
| 🍚 | 分类·米饭 | ic-rice.svg | 本地（已登记；find 分类宫格）|
| 🌶️🍲 | 分类·麻辣烫 | ic-malatang.svg | 本地（已登记；find 分类宫格）|
| 🍳 | 分类·早餐 | ic-breakfast.svg | 本地（已登记；find 分类宫格）|
| 🌙 | 分类·夜宵 | ic-midnight.svg | 本地（已登记；find 分类宫格）|
| 🍔 | 分类·快餐 | ic-fastfood.svg | 本地（已登记；find 分类宫格）|
| 🍢 | 分类·小吃 | ic-snack.svg | 本地（已登记；find 分类宫格）|
| 🥤 | 分类·饮品 | ic-drink.svg | 本地（已登记；find 分类宫格）|

> 新增语义图标须先在此表登记，再将 SVG 下载至 `assets/icons`，禁止 emoji 字符当图标。全量禁 emoji 为强制红线，缺失图标经 Iconfont MCP 补齐。chili/portion 等属性图标若 §0.5 原无对应矢量，经 Iconfont MCP 拉取补齐并登记（ic-clock 已登记复用；chili/portion 本期新增登记；ic-dish/ic-image 作图兜底登记）。

---

# 一、TabBar 页面

## 1.1 home（首页 / 推荐流） `pages/home/index.vue`
**定位**：App 门面与默认落地页，让用户一进入即触达 Banner 活动 / 食堂入口 / 热门菜品，1 次点击进任意食堂或菜品详情。

**信息架构（自上而下区块清单）**
1. **Header**「食在交大」（复用 `header`）—— 固定顶栏，不随内容滚动。
2. **Banner 轮播**（复用 `ImageSwiper` 或内联 swiper）：按 `target_type` 分发跳转；**无数据时保留轻量占位（不整块消失）**，避免首屏突兀空洞。
3. **广播通知条**（细长 ticker，**竖直方向轮播翻滚**）：仅「通知图标 `ic-broadcast`（IconSvg 渲染，非 emoji bell）+ 文本内容」，无「查看全部」；**定时竖直翻滚替换内容（竖直 ticker，定时切换不同广播条目，节奏合理）**；按广播类型分发跳转，预留多种广播信息，不写死社区；**无数据时保留轻量占位（不隐藏）**；条体按下 `scale(0.97)`。
4. **食堂入口**（横滑卡片区，标题用 `SectionTitle`「食堂」）：每张卡 = 图（圆角）+ 名，**不含营业状态**；横滑 `scroll-view scroll-x`，不触发页面横向滚动（§4.9）。
5. **热门菜品**（标题用 `SectionTitle`「热门菜品」）：双列瀑布流（复用 `WaterfallList`）+ 无限加载；**移除右侧「上拉加载更多」提示，加载态仅以底部 footer「加载中/没有更多」无感呈现**。
6. **CustomTabBar**（固定底部，半透材质）。

**ASCII 线框（首页竖向滚动）**
```
┌─────────────────────────────┐
│ Header 食在交大              │
├─────────────────────────────┤
│ [ Banner 轮播 swiper ]       │ ← 无数据保留占位（不隐藏）
├─────────────────────────────┤
│ 🔔 广播竖直翻滚文本…（ticker）│ ← 定时竖直轮播；无数据保留占位
├─────────────────────────────┤
│ ▏食堂                      │ ← SectionTitle
│ [卡][卡][卡]→（横滑）        │
├─────────────────────────────┤
│ ▏热门菜品                  │
│ [菜品][菜品]                 │ ← WaterfallList 双列
│ [菜品][菜品]                 │
│  加载中 / 没有更多（footer）  │
└─────────────────────────────┘
 [ CustomTabBar 半透固定底栏 ]
```

**关键交互**：Banner 点击按 `targetType` 分发（DISH→dish 详情 / URL→webview / 复制链接 / NONE）；食堂卡/菜品卡 tap 直接导航；下拉刷新 + 触底加载更多；卡片按下 `scale(0.97)`；**广播条定时竖直翻滚切换条目，条体按下 `scale(0.97)`**；**首页须齐备三态**——加载中（内联态）/ EmptyState（**区分「无数据」与「加载失败」文案**）/ 正常态，由开发工程师按页落地。

**组件拆分**：复用 `WaterfallList`、`CustomTabBar`、`EmptyState`、`Header`、`ImageSwiper`、`SectionTitle`、`IconSvg`（广播 `ic-broadcast` 经其渲染）；**加载态用内联态（非已删 Loading 组件，见 §0.2）**；横滑食堂卡 **待抽 `CanteenCard` 组件（当前代码库未抽，列为待实现，实现前内联于 home）**；广播条无数据时保留轻量占位（不隐藏），按真实数据渲染；**emoji 全量清理**：home 及全小程序禁用 `emoji.ts`，图标统一走 `IconSvg` + 阿里云矢量库（Iconfont MCP 补齐，见 §0.4/§0.5）。

**视觉规范**：圆角/阴影/材质 ✓；按下 `scale(0.97)` ✓；层次清晰、留白一致、可读性良好；**广播条 `ic-broadcast` 为线性 SVG（2px 描边/24px 网格，与 §0.5 一致），竖直翻滚动画用 spring 可中断、节奏合理，不闪跳**；**空态（Banner/广播）以轻量占位呈现，不整块消失**。

---

## 1.2 find（发现 / 搜索 / 筛选） `pages/find/index.vue`
**定位**：主动探索与精准检索，通过搜索、分类、热搜、多维筛选快速定位菜品。

**信息架构（区块清单）**
- **顶部固定区**：`Header` + 内联 `SearchBar`（联想 debounce 300ms；**搜索框宽度放宽至接近内容区满宽，左右留白与首页一致**）+ 筛选态专用的「返回」入口（**图表箭头形态 `ic-arrow-left`，点击退出筛选态回非筛选首屏**）。
- **主区（非筛选态，首屏）**：
  - 历史搜索（`HistoryChips` 内联 chips，点击回填搜索）
  - 分类宫格（**去除「分类」标题文字，8 个网格直接置于搜索框下方**；**网格须配 8 个矢量分类图标（经 Iconfont MCP 拉取补齐，禁止回退 `ic-dish` 通用图标，提升辨识度）**；网格点击=按该分类进筛选态，不抽 `CategoryTabs`，直接内联 `SectionTitle`+宫格）
  - 本周热搜（`HotSearchList`：每条左配图（圆角正方形）+ 文案 + 热度值/关联数；高度加大）
  - **首屏骨架屏**：非筛选态三区块（历史搜索/分类宫格/本周热搜）在首屏 loading 时显示**内联骨架屏占位**（三区块各自 shimmer 占位，非整块空白），数据就绪后切换正常态（见 §0.4 三态强制）。
- **筛选结果态**：排序条（**滑块式 `SegmentTabs`：白底滑块随选项左右 spring 1.0/0.3 滑动，比胶囊更 Apple；排序条采用此组件**）+ `WaterfallList` + 触底 footer（加载中/没有更多）。
- **筛选 Sheet**（食堂/价格/口味）：底部弹层，**抽通用 `FilterSheet` 组件（当前代码库未抽，列为待实现）**；spring `0.8/0.3` + 手势中断 + 遮罩点击关闭。

**ASCII 线框（非筛选态 / 筛选态）**
```
非筛选态（首屏）                筛选态
┌─ Header + SearchBar ─┐      ┌─ ← Header + SearchBar ─┐  ← 返回为箭头图标 ic-arrow-left
│ 历史: [辣][川][素]    │      │ 排序:[▮综合|评分|价格]  │  ← 滑块式 SegmentTabs 白底滑块
│ [ic][ic][ic][ic]    │  ← 8 │  ├──────────────────────┤
│ [ic][ic][ic][ic]    │  矢量 │ │ [菜品][菜品]          │
│  分类SVG图标(Iconfont)│  图标 │ │ [菜品][菜品]          │
│ [热][搜][列][表]      │      │ │  加载中/没有更多       │
│  左图 文案 热度值      │      └──────────────────────┘
└──────────────────────┘        (筛选 Sheet 从底弹出，手势中断/遮罩关)
  ↑ 首屏 loading 时三区块显示内联骨架屏占位
```

**关键交互**：搜索 input debounce → 联想；confirm → 进筛选态；分类宫格/热搜点击 → 进筛选态（带预设条件）；**筛选态「返回」用 `ic-arrow-left` 图标箭头，点击退出筛选态回非筛选首屏**；排序条切换 = 滑块式 SegmentTabs 白底滑块 spring 1.0/0.3 滑动；筛选 Sheet 选择 → 应用筛选（spring 0.8/0.3 + 手势中断 + 遮罩关闭）；下拉刷新 + 触底加载；首屏三态：加载中（内联骨架屏）→ EmptyState（无数据/加载失败区分）→ 正常态。

**组件拆分**：复用 `WaterfallList`（内部已渲染 `DishCard`，**禁止再传具名 slot**，见 §0.4）、`SearchBar`、`EmptyState`、`CustomTabBar`、`Header`、`SectionTitle`、`IconSvg`（`ic-arrow-left` 经其渲染）；加载态用内联态（非已删 Loading）；**8 个分类矢量图标经 Iconfont MCP 拉取补齐，登记入 §0.5 后落 `assets/icons`**；**待抽 `SegmentTabs`（滑块式，五页共用：find / community / my-moments / my-publish / my-submissions）/ `FilterSheet`（通用）/ `HotSearchList` / `HistoryChips`**（当前未抽，内联实现）。

**视觉规范要点**
- **排序条采用滑块式 `SegmentTabs`**：白底滑块随选项左右滑动，spring 1.0/0.3（可中断、速度接力），比胶囊更 Apple；视觉须精致有辨识度（非普通文字 tab）。
- 分类宫格 8 个网格各配矢量分类图标（Iconfont MCP 补齐，禁止回退 `ic-dish`），辨识度优先；图标风格与 §0.5 一致（线性/2px 描边/24px 网格）。
- 首屏非筛选态三区块须加内联骨架屏，首屏 loading 期间以 shimmer 占位呈现，不整块空白。
- 图标均来自 §0.5 映射表（含 `ic-arrow-left`）；筛选 Sheet `cubic-bezier(0.32,0.72,0,1)` 0.3s + 手势中断 ✓。
- 结果列表禁止具名 slot 分发（§4.9）；搜索框一律 `SearchBar`；筛选 Sheet 须抽通用 `FilterSheet`。

---

## 1.3 community（动态 / 广场） `pages/community/index.vue`
**定位**：UGC 社区广场，浏览动态流并一键发布。

**信息架构（已终决）**：`Header`「动态」→ **单一「最新」逆序信息流**（`MomentCard` 流，按发布时间倒序，无分段 Tab，**不渲染 `SegmentTabs`**）→ 触底 footer（加载中/没有更多）→ 悬浮发布 FAB（`ic-plus`，半透固定右下角）。UGC 社区用单一时间序流更简单诚实，「推荐」需排序算法/后端支撑，当前无则不做（用户拍板：社区笔记帖子不需要筛选 tab）。

**关键交互**：下拉刷新；触底无限加载；FAB tap → publish-moment；卡片按下 `scale(0.97)`；FAB 按下 `scale(0.97)`（规范统一值，非 0.94）；首屏三态齐备——加载中（首屏内联骨架屏）→ `EmptyState`（空态文案「还没有动态，快去发布第一条吧」+ 主按钮「发布第一条动态」）→ 正常态。

**组件拆分**：复用 `MomentCard`、`EmptyState`、`CustomTabBar`、`Header`、`IconSvg`；加载态用内联态（非已删 Loading）；**`SegmentTabs` 仅保留组件契约供 find / my-moments / my-publish / my-submissions 复用（五页共用，见 §七 #5），community 页不渲染 Tab**；FAB 半透固定右下角悬浮按钮（内联或半透材质）；**`EmptyState` 须扩展 `action`/`actionText`/`@action` 槽统一承载「发布第一条动态」主按钮**（见 §六 B-15 待开发项）。`MomentCard` 无头像兜底改 `ic-user`（新增登记 §0.5），全量禁 emoji。

**视觉规范**：单一最新流，无分段 Tab，信息架构更直白；FAB（`ic-plus`，半透固定右下，按下 `scale(0.97)`，半透材质见 §0.3 降级规则）；空态 `EmptyState` + 「发布第一条动态」主按钮（扩展 `EmptyState` action 槽）引导发布；首屏 loading 期间骨架屏占位，不整块空白；emoji 全禁（头像兜底 `ic-user`、空态兜底 `ic-empty`）。

---

## 1.4 profile（我的 / 登录注册 / 贡献入口） `pages/profile/index.vue`
**定位**：个人中心 + 鉴权入口 + 贡献统一入口。

**信息架构（区块清单，已终决）**
- **未登录态（居中卡片式）**：`AuthForm`（独立组件，§六 B-9）居中位——顶部品牌区（`ic-logo`）+ 上浮卡片承载「登录/注册/找回」三态同卡切换；登录双路：① 账号密码 ② 邮箱验证码（`ic-user` / `ic-mail` / `ic-lock` 全局图标，全 emoji→IconSvg）。
- **已登录态（卡片 + 菜单结构，主页不渲染 Tab）**：
  1. 用户信息卡：头像（**圆角正方形 `--radius-card` 16rpx**，无头像兜底 `ic-user`）+ 昵称 + `StatusBadge`（学生/管理员，按 `userInfo.role`：`STUDENT`/`ADMIN`；**文案配色待后端 VO 确认**）+ **不展示数据库内部 id**。
  2. 统计行 `StatsRow`（三宫格，**定稿字段契约（A.16）：`我的评价`(reviewCount) / `已发布`(publishedCount) / `待审核`(pendingCount)**；`favoriteCount` 后端暂以 0 占位、不展示；三宫格**必须全量渲染**，publishedCount/pendingCount 字段已在 `types/user.ts` 定义，缺失时以 0 兜底显示，不得只渲染单格 reviewCount——当前实现仅渲染单格属未达标，须补齐为三宫格）。紧凑内联小卡片，每格按下 `scale(var(--press-scale))` 跳对应页（评价→review-list / 已发布→my-publish / 待审核→my-submissions）。
  3. 「我要贡献」入口（与统计/菜单同宽）→ 点击弹 `ContributeSheet`。
  4. 菜单组（我的发布/我的提交/设置/意见反馈/联系开发者等）→ `SettingGroup`+`SettingCell`（§六 B-5）。
  5. 版本号 + 退出（主入口）+ 账号注销（次级弱化文字入口，见下）。
- **弹层**：`ContributeSheet`（我要贡献）/ `ApplySheet`（申请下架·纠错，跨页共用）/ 昵称编辑 Modal（均待抽，弹层统一 spring `0.8/0.3` + `ic-close` 关闭 + `prefers-reduced-motion` 降级交叉淡入）。

**关键交互**
- 未登录：`AuthForm` 内登录/注册/找回同卡切换；账号密码 / 邮箱验证码双路切换；发送验证码 60s 倒计时；内联错误就近展示；表单提交按下 `scale(0.97)`。
- 已登录：用户信息卡加载态用**内联骨架屏**；「我要贡献」tap → `ContributeSheet`；菜单项 tap 进子页；**退出**（`uni.showModal` 二次确认）→ `POST /logout`；**账号注销**（≠退出，次级弱化文字入口）→ `uni.showModal` 二次确认（含「不可恢复」警示）→ `DELETE /my/account`。退出与注销为两个独立入口。
- 全页三态齐备：未登录 `AuthForm` 无加载依赖；已登录数据以**内联骨架**承载加载中态 → `EmptyState` → 正常态。
- 底部弹层统一 `spring 0.8/0.3` 可中断 + `ic-close` 关闭 + `prefers-reduced-motion` 降级为交叉淡入。

**组件拆分**：复用 `AppButton`、`CustomTabBar`、`Header`、`StatusBadge`、`SectionTitle`、`IconSvg`；加载态用内联态（非已删 Loading）；**待抽 `AuthForm`（B-9，居中卡片式，登录/注册/找回同卡 + 账号密码/邮箱验证码双路）/ `ContributeSheet` / `ApplySheet`（B-3，与 dish / stall 跨页共用）/ `StatsRow`（B-10）/ `SettingGroup`+`SettingCell`（B-5）**（当前均未抽，内联实现，其中 `ApplySheet` 跨页共用同一组件）。

**视觉规范要点**
- **头像为圆角正方形（`--radius-card` 16rpx），纠正现有圆形实现**；无头像兜底 `ic-user`（登记 §0.5）。
- 不向用户直接展示数据库内部 id（§1.4 视觉规范）。
- 已登录态须紧凑均衡：用户信息卡 + `StatsRow` 三宫格 + 贡献入口 + 菜单组同宽堆叠，统计/贡献入口不得独占整屏宽。
- 角色徽标 `StatusBadge` 仅已登录态显示（学生/管理员），文案配色**待后端 VO 确认**，落档标注「待后端 VO 确认」。
- 贡献/申请/关联选择等底部弹层必须抽独立组件（待抽项见 §六）；弹层统一 spring `0.8/0.3` + `ic-close` + reduced-motion 降级。
- **emoji 全禁**：所有语义图标经 `IconSvg` 渲染 `assets/icons` 下 SVG；`AuthForm` 内账号 `ic-user` / 邮箱 `ic-mail` / 密码验证码 `ic-lock` / 关闭 `ic-close` / logo `ic-logo`，§0.5 已登记。

---

# 二、详情页（pages-detail/）

## 2.1 dish（菜品详情） `pages/pages-detail/dish.vue`
**定位**：单菜品深度查看 + 评价入口。

**信息架构（区块清单，已终决）**
- `Header`（返回 `ic-arrow-left` + 分享 `ic-share`（IconSvg 渲染））
- `ImageSwiper`（菜品图，允许多图）
- `CardSection`（标题 / 价格（元，api 层已转换，禁裸算）/ `Rating` 评分组件（独立组件，非直接 `ic-star`）/ `TagLabel` 标签行（属性图标 chili/portion 等来自 §0.5，经 IconSvg 渲染））
- `CardSection`「菜品信息」（位置/属性/简介等）
- `CardSection`「用户评价」（**仅标题 + 评价数 + 右上角写评价入口 `AppButton`**；卡内含 `ic-heart` 喜欢乐观更新按钮 + 3 条评价预览 + 查看全部跳 `review-list`；**去除排序/只看有图筛选**，筛选能力整体迁 `review-list`）
- 底部弱链接：反馈 / 申请下架（次级弱化文字入口，非大板块）
- 底部 `action-bar`（`ic-heart` 左侧固定位 + 写评价 `AppButton` + 去档口 `AppButton`，固定底栏；`ic-heart` 与写评价/去档口并列，拇指可达 + 整页喜欢态常驻，用 `--action-bar-height` 避让）

**关键交互**
- 评价区点击爱心图标（`ic-heart`，IconSvg 渲染，非 emoji）表示「喜欢」，**乐观更新**（先本地翻转态 + 整页喜欢态常驻，失败回滚）。
- 长按菜品名（仅作者）删除（二次确认）。
- 底部 action-bar：`ic-heart` 左侧固定位点击切换喜欢（与卡内喜欢态同源）；写评价 / 去档口 `AppButton` 点击导航。
- **整页三态齐备**（本期 W9 验收）：① 加载骨架（首屏内联骨架屏，含 Header 占位 + Swiper 占位 + 卡片占位 shimmer）② 错误态（`EmptyState` 变体 + 重试入口）③ 空态（`EmptyState`）；**不仅评价区，详情页整体三态齐备**。评价区三态同步齐备：加载中（评价骨架）/ `EmptyState`（暂无评价）/ 正常态（3 条预览）。

**组件拆分**：复用 `ImageSwiper`、`CardSection`、`TagLabel`、`EmptyState`、`AppButton`、`Rating`、`Header`、`IconSvg`；加载态用内联态（非已删 Loading）；**待抽 `ReviewItem`（与 review-list 共用）、`ApplySheet`（与 profile 共用）**；底部操作栏 `ic-heart` 与写评价/去档口并列，`--action-bar-height` 避让统一联动（**改现有硬编码 `140rpx` 为 `--action-bar-height`**）。

**视觉规范要点**
- **菜品详情卡片右上角的筛选须去除**，该筛选能力迁移至全部评价页（review-list）；评价区仅保留标题 + 评价数 + 写评价入口 + 3 条预览 + 查看全部。
- 详情页底部操作栏避让须用统一变量 `--action-bar-height` 联动（**已实现**：`dish.vue:350`/`:397` 已用 `var(--action-bar-height)` 联动，本项已落实，无需再改；review/review-list/contact/publish-* 同类底部栏同源引用该变量）。硬查：`grep "140rpx"` 在 action-bar 相关样式中不得出现硬编码。
- 价格展示为元，禁 `/100` 裸算。
- **整页三态齐备**：详情页加载骨架 / 错误态 / 空态（含评价区三态）本期一并做，W9 验收三态齐备。
- **emoji 全禁**：星标评分 → `Rating` 组件（非 `ic-star` 直用）；属性图标 chili/portion → `IconSvg`（§0.5 登记）；头像兜底 → `ic-dish`/`ic-image`；✕✓ → `ic-close`；`ic-heart` 喜欢态经 IconSvg 渲染（语义唯一，不与 `ic-thumb` 混用）。

---

## 2.2 canteen（食堂详情） `pages/pages-detail/canteen.vue`
**定位**：食堂概况 + 档口浏览。

**信息架构（区块清单，与档口详情同构：上介绍信息、下各档口列表）**
1. **① 食堂介绍与信息区块**（`CardSection` / `SectionTitle`「食堂概况」）：图 + 名称 + 简介 + 基础信息，**内容更详细：补充营业时间/地址/档口数/综合评分等**。
2. **② 各档口单列卡片流**（`SectionTitle`「档口」，用 `WaterfallList` 单列模式或 `v-for` 渲染 `StallCardSingle`）：每张卡 = 档口图（圆角正方形）+ 名称 + 简介 + 评分/标签/菜品数/人均；**档口图上去除「新」角标；卡片尺寸保持不变**；点击进档口详情；**不直接显示菜品**。

**关键交互**：档口卡 tap → 档口详情（stall.vue）；卡片按下 `scale(0.97)`。

**组件拆分**：复用 `WaterfallList`（单列模式）、`EmptyState`、`CardSection`、`SectionTitle`、`Header`、`IconSvg`、`StallCardSingle`（**现存单列档口卡组件，原 `StallCard` 双列版已删，与 home / stall 同构复用**）、`Rating`（**综合评分走 `Rating` 组件，非直用 `ic-star`**，与 dish §2.1 同源）；加载态用内联态（非已删 Loading）；**跨页共用 `ApplySheet`（B-3，与 dish / stall / profile 跨页共用同一组件，canteen 改为 `<ApplySheet entity-type="CANTEEN" :entity-id="canteenId"/>`，不再内联）**；禁止向 `WaterfallList` 传具名 slot，统一 `@card-click="goToStall"`。

**关键交互补（整页三态齐备）**：档口流加载中 → 整页加载骨架（Header + 介绍卡 + 档口流骨架 shimmer）→ `EmptyState`（无档口）→ 正常态；档口卡按下 `scale(0.97)`；卡片 tap → 档口详情（stall.vue）。

**视觉规范**
- 单列档口卡类似美团，层次清晰、留白一致。
- **介绍块包 `CardSection` 语义容器 + 顶部补 `SectionTitle「食堂概况」**；基础信息行：档口数补矢量图标（新增 `ic-stall`，或复用 `ic-list`，经 Iconfont MCP 补齐登记 §0.5）、评分走 `Rating` 组件；位置 `ic-location`、营业时间 `ic-clock` 维持复用（§0.5 已登记）。
- **图兜底**：食堂/档口图失败兜底 `ic-dish`；通用图片加载失败兜底 `ic-image`；二者语义区分（维持现状，§0.5 已登记）。
- 卡片按下 `scale(0.97)`（规范统一值）。

---

## 2.3 stall（档口详情） `pages/pages-detail/stall.vue`
**定位**：单档口信息 + 菜品列表。

**信息架构（区块清单，已终决 6 项全部按设计师默认推荐拍板）**
1. **上信息卡**：头部补 `SectionTitle「档口信息」`；信息卡不拆多个小卡，单卡 `CardSection` 内收敛「简介 / 营业时间 / 位置 / 标签」四段。
2. **下菜品列表**：双段单卡收敛——单 `CardSection`（`SectionTitle「菜品」`）内用 `StallDishRow` 行式列表承载全部菜品（不另拆多卡）。
3. **弱化的「反馈 / 申请关闭 / 纠错」入口**：信息卡底部次级弱化文字链接（非大板块），点击弹跨页共用 `ApplySheet`。

**关键交互**：菜品行 tap → 菜品详情（dish.vue）；头部信息卡 / 菜品行按下 `scale(0.97)`；反馈入口点击弹 `ApplySheet`。

**组件拆分（已落档）**：复用 `ImageSwiper`、`ImageFallback`、`TagLabel`、`AppButton`、`Header`、`EmptyState`、`Rating`、`SectionTitle`、`IconSvg`；加载态用内联态（非已删 Loading）。
- **已抽独立组件 `StallDishRow`**（行式菜品卡，图左圆角正方形缩略图 + 名称 + 价格（元，api 层已转换禁裸算）+ `Rating` + 简介一行截断）（复用 `Rating` / `ImageFallback` / `IconSvg`；行按下 `scale(0.97)`；tap → dish.vue；**全量替换 stall 内联实现**）。
- **跨页共用 `ApplySheet`**：`<ApplySheet entity-type="STALL" :entity-id="stallId"/>`（与 dish / stall / canteen / profile 跨页共用同一组件，次级弱化文字入口触发），弹层统一 `spring 0.8/0.3` + `ic-close` + `prefers-reduced-motion` 降级为交叉淡入。
- 须齐备三态（加载中内联 / EmptyState / 正常）。

**整页三态（已终决）**
- ① **加载骨架**：Header + 头图占位 + 信息卡占位（shimmer）+ 菜品列表骨架（Header + 信息卡 + 菜品行骨架 shimmer）。
- ② **EmptyState**：无菜品 → 「该档口还没有菜品」+ 主按钮「去其他档口」（`AppButton`）；错误态 → EmptyState 变体 + 重试入口。
- ③ **正常态**。

**视觉规范要点（已终决）**
- **菜品图片及 `StallDishRow` 缩略图均为圆角正方形（`--radius-card` 16rpx，非圆形）**；图失败兜底复用 `ImageFallback`。
- **头图兜底 `ic-dish`（档口图语义）**，与通用 `ic-image` 区分；位置 `ic-location` / 营业时间 `ic-clock` 复用（§0.5 已登记）。
- 分隔线用 `--border-color`，对比清晰（≥4.5:1）。

---

## 2.4 moment（动态详情） `pages/pages-detail/moment.vue`
**定位**：单条动态全文 + 评论互动。

**信息架构（区块清单，8 项已拍板：第 7 项用户修改，其余按设计师默认推荐）**：`Header`（返回） + 发布者（头像圆角正方形 + 昵称 + **审核态 `StatusBadge`**） → 正文全文 + 九宫格大图（`MomentImageGrid` 抽组件） → 关联对象卡（菜品/档口，点击跳转，含圆角正方形缩略图） → 互动栏 `InteractBar`（`ic-thumb` 有用 / `ic-comment` 评论 / `ic-report` 举报，**举报弱化为次级右对齐低对比入口**）→ 评论区（`CommentItem` 列表，楼中楼扁平化） + 底部评论输入栏（固定底栏避让）。**去除动态详情内的 tab bar / 分段筛选（无意义）**。

**关键交互**
1. 进入详情三态互斥：① loading 态（内联骨架）② 错误态（`EmptyState` 变体 + 重试）③ 正常态，三者互斥且经真机实测覆盖（修复此前进入详情空白问题——数据绑定/空态渲染确保有内容时正常展示）。
2. 九宫格：抽 `MomentImageGrid` 复用 `ImageFallback` + `uni.previewImage` 预览。
3. 互动栏：抽 `InteractBar` 组件；举报弱化为次级右对齐低对比入口（不与正向互动同权并列）。
4. 举报：抽 `ReportModal` 组件套统一弹层（`spring 0.8/0.3` + `ic-close` + `prefers-reduced-motion` 降级为交叉淡入）替代内联弹窗，二次确认。
5. 评论：抽 `CommentItem` 组件，楼中楼扁平化（回复以平铺行呈现，不嵌套折叠），按下统一 `scale(0.97)`。
6. 全量移除 moment 页 emoji，统一走 `IconSvg` / `ImageFallback`（评论/互动/关联卡均无 emoji 字符）。
7. **【用户修改】关联对象卡**：= 圆角正方形缩略图（--radius-card 16rpx）+ 名称 + 类型标签 + 右箭头 `ic-arrow`；缩略图按关联类型取：关联菜品用菜品图、关联档口用档口图（均圆角正方形）；图标按 dish/stall 区分（`ic-dish`/`ic-stall`）；点击跳转（菜品→dish.vue，档口→stall.vue）。
8. 底部评论栏：避让 `--action-bar-height`；评论区三态齐备（加载骨架 / `EmptyState`「还没有评论，来说两句」/ 正常态 + 长评论折叠展开）。

**组件拆分（已终决）**：复用 `EmptyState`、`Header`、`UsefulButton`、`StatusBadge`、`IconSvg`、`ImageFallback`、`Rating`（如需）；加载态用内联态（非已删 Loading）。**已抽独立组件**：
- `MomentImageGrid`（九宫格大图，复用 `ImageFallback` + `previewImage` 预览；全量替换 moment 内联九宫格实现）。
- `InteractBar`（互动栏，`ic-thumb` 有用统一 `UsefulButton` / `ic-comment` 评论 / `ic-report` 举报弱化为次级右对齐低对比入口；抽组件）。
- `ReportModal`（举报弹层，套统一弹层 `spring 0.8/0.3` + `ic-close` + `prefers-reduced-motion` 降级，替代内联弹窗；二次确认）。
- `CommentItem`（评论项，楼中楼扁平化，按下 `scale(0.97)`；抽组件，全量替换内联评论行）。

**视觉规范要点**：① 举报须弱化为次级右对齐低对比入口，不得与正向互动同权并列；② 「有用」统一 `UsefulButton`；③ 发布者头像圆角正方形；④ **关联卡圆角正方形缩略图 --radius-card 16rpx（非圆形），图标 `ic-dish`/`ic-stall` 按类型区分，右箭头 `ic-arrow`**；⑤ 进入详情三态互斥 + 真机实测覆盖，修复空白问题；⑥ 全量禁 emoji，统一 `IconSvg`/`ImageFallback`；⑦ 评论区三态齐备 + 折叠。

---

## 2.5 review（发表评价） `pages/pages-detail/review.vue`
**定位**：提交对菜品的评价。

**信息架构（区块清单，6 项已拍板：全部按设计师默认推荐）**：`Header`（返回 `ic-arrow-left` + 标题「写评价」） + `CardSection`(评分 `Rating`) + `CardSection`(内容 textarea，带计数) + `CardSection`(图片≤3 `ImageUploader`) + 底部提交栏（`AppButton`，固定底栏避让 `--action-bar-height`）；根 `scroll-view` 包裹全部内容。

**关键交互（三态 + 校验状态机）**
1. **三态**：① 提交中态（提交按钮禁用 + 文案「提交中…」spinner）② 提交失败用 **inline 错误条**（非 `EmptyState` 变体，就近展示于提交栏上方）③ 正常态。
2. **评分必填**：`Rating` 必填；0 分时提交按钮置灰禁用 + 红字「请先打分」（用 `--danger` 色，**非 emoji**）提示，待打分后恢复。
3. textarea 计数纯文本（无 emoji）；图片 chooseImage(≤3)；提交校验（评分必填/内容非空）。
4. **底部提交栏 `AppButton` 文案状态机**：默认「提交评价」→ 提交中「提交中…」(禁用 + spinner) → 成功「已提交」(短暂后返回上一页) → 失败回「提交评价」+ inline 错误条；按钮类型 `primary`。
5. **键盘避让**：textarea 聚焦经 `scroll-view` `adjust-position` 上推 + 底栏避让，防键盘/底栏遮挡；清空图片用 `ic-close`（非 emoji）。

**组件拆分（已落档）**：复用 `CardSection`、`Rating`、`AppButton`、`Header`、`ImageUploader`（**现存组件**）、`IconSvg`（`ic-arrow-left` 返回 / `ic-close` 清空，均经其渲染，**无新增图标**）；根 `scroll-view` 包裹全部 `CardSection`；加载/提交中态用内联态（非已删 Loading）。**emoji 全禁**：计数纯文本、清空 `ic-close`、返回 `ic-arrow-left`，无新增图标语义。

**视觉规范要点**：AppButton 类型白名单 primary/outline/text/danger/gradient（本页提交用 `primary`）；多 `CardSection` 表单页强制 `scroll-view`；`ImageUploader` 缩略图圆角正方形 `--radius-card` 16rpx + `ImageFallback` 兜底；提交按钮文案随状态机变化；`--danger` 红字提示评分必填（非 emoji）。

---

## 2.6 review-list（全部评价 / 我的评价） `pages/pages-detail/review-list.vue`
**定位**：某菜品全部评价 或 我的评价列表。

**信息架构（区块清单，已拍板：按设计师默认推荐）**：`Header`（返回 + 标题「全部评价/我的评价」） + 筛选条(`SectionTitle` + 评分/排序/带图 筛选，**承接 dish 迁来的筛选能力，抽通用 `FilterSheet` 承接评分/排序筛选**) + 列表(`ReviewItem` 抽组件，与 dish 共用同一组件) + `EmptyState` + 触底 footer（加载中/没有更多）。

**关键交互**
- 排序切 / 带图切 → 重载列表；评分筛选经 `FilterSheet` 承接。
- 列表项互动统一为爱心「喜欢」`ic-heart` SVG（经 `IconSvg` 渲染）**乐观更新**（先本地翻转，失败回滚）；**长按删本人**（二次确认 `uni.showModal`）。
- 超 50 条分页（触底加载更多，footer 态呈现）；**整页三态齐备**：① 加载骨架（首屏内联骨架屏）② `EmptyState`（无评价）③ 正常态。

**组件拆分（已落档）**：复用 `UsefulButton`、`EmptyState`、`Header`、`Rating`、`SectionTitle`、`IconSvg`、`ReviewItem`（**抽组件，与 dish 共用同一组件，三处评价卡同源一致**）；加载态用内联态（非已删 Loading）；**抽通用 `FilterSheet`（与 find 共用，承接评分/排序/带图筛选）**；须齐备三态，超 50 条分页。**emoji 全禁**：与 dish §2.1 评价区视觉同源，爱心 `ic-heart` 经 `IconSvg` 渲染（语义唯一，不与 `ic-thumb` 混用），禁用 emoji 字符。

**视觉规范**：三处评价卡片必须复用同一 `ReviewItem`；评价互动统一为爱心「喜欢」`ic-heart`（SVG，取自本地 `assets/icons`，经 `IconSvg` 渲染），与 dish §2.1 评价区视觉同源一致；长按删本人二次确认。

---

## 2.7 contact（联系开发者） `pages/pages-detail/contact.vue`
**定位**：用户向开发者反馈。

**信息架构（区块清单，已拍板：按设计师默认推荐）**：`Header`（返回 + 标题「联系开发者」） + 反馈类型(`picker`，走统一类型枚举) + 内容 textarea + 联系方式 + 提交栏(`AppButton`，固定底栏避让 `--action-bar-height`)。

**关键交互**：picker 选类型（统一类型枚举）；提交校验 → 走 `submitFeedback` api 层 → 成功态；**三态齐备**：① 提交中态（提交按钮禁用 + 「提交中…」）② 正常态 ③ 失败 inline 错误条。

**组件拆分（已落档）**：复用 `AppButton`、`Header`、`CardSection`、`EmptyState`、`SectionTitle`、`IconSvg`（图标经 `IconSvg`，**emoji 全禁**）；与 `feedback/index.vue` **共用同一 `FeedbackForm`（待抽组件，§六 B-4，两页必须一致）**；须走 `submitFeedback` api 层 + 统一类型枚举；加载/提交中态用内联态（非已删 Loading）。

**视觉规范**：反馈类页须复用同一 `FeedbackForm` 与同一类型枚举（与 feedback §4.6 同源）；图标经 `IconSvg`，新语义图标先登记后使用（§0.5）；三态齐备（提交中态/正常）。

---

# 三、社区编辑 / 我的动态

## 3.1 publish-moment（发布 / 编辑动态） `pages/publish-moment/index.vue`
**定位**：创作动态（图文 + 可选关联菜品/档口）。

**信息架构（区块清单）**：`Header`（返回 + 标题「发布动态」） + 正文 textarea + 图片网格(≤9 `ImageUploader`) + 关联对象卡（`SectionTitle`+ 已选关联展示） + 底部提交栏（`AppButton`，避让 `--action-bar-height`）；关联对象选择经 `RelatedPickerSheet`。

**关键交互**：图片 chooseMedia(≤9)；关联 Sheet 搜菜品/档口（走正式搜索 API）；提交校验（正文或图非空）。

**组件拆分**：复用 `AppButton`、`Header`、`ImageUploader`、`SearchBar`、`SectionTitle`；`RelatedPickerSheet`（**现存组件**，关联对象卡与 Sheet 内搜索须用 `SearchBar`）；加载/提交态用内联态。

**视觉规范**：关联对象选择必须走各自正式搜索 API，禁止借道伪造 id（档口联想须返回真实 `stallId`）。

---

## 3.2 my-moments（我的动态） `pages/my-moments/index.vue`
**定位**：用户自己动态的审核状态管理。

**信息架构（区块清单）**：`Header`（返回 + 标题「我的动态」） + 分段(全部/审核中/已退回，**待抽 `SegmentTabs`，当前内联**) + 列表(`MomentCard` 带 show-audit 审核态徽标) + `EmptyState` + 触底 footer。

**关键交互**：分段切重载；tap 卡片 → 退回进编辑；卡片按下 `scale(0.97)`。

**组件拆分**：复用 `MomentCard`、`EmptyState`、`Header`、`StatusBadge`；加载态用内联态（非已删 Loading）；**`SegmentTabs` 待抽（当前内联）**；徽标计数须响应式（`ref`/`reactive`）。

---

# 四、我的子页（profile/）

## 4.1 publish-dish（发布 / 编辑菜品） `pages/profile/publish-dish.vue`
**定位**：学生发布/编辑菜品（UGC）。

**信息架构（区块清单）**：`Header`（返回 + 标题「发布菜品/编辑菜品」） + `CardSection`(基本信息：名称/价格（元，api 层已转换）) + `CardSection`(食堂档口 picker 联动) + `CardSection`(口味标签 `TagSelector` 待抽/内联 chips) + `CardSection`(图片 `ImageUploader` ≤9) + `CardSection`(描述 textarea) + 底部提交栏（`AppButton` 避让 `--action-bar-height`）。

**关键交互**：picker 选食堂→联动档口；标签 chips 多选；图片≤9；提交校验。

**组件拆分**：复用 `CardSection`、`AppButton`、`Header`、`ImageUploader`（**现存**）；**`TagSelector` 待抽（当前内联 chips）**；标签来源统一引用常量；金额展示保持 api 层统一（前端 `¥{{price}}` 已是元，禁裸算）；加载/提交态用内联态。

---

## 4.2 submit-stall（提交档口·食堂） `pages/profile/submit-stall.vue`
**定位**：学生补充档口/食堂。

**信息架构（区块清单）**：`Header`（返回 + 标题「提交档口·食堂」） + 类型切换（档口/食堂，**`TypeSwitch` 待抽/内联**） + `CardSection`(名称/校区或所属食堂 picker/封面 `ImageUploader` ≤9/简介/状态) + 底部提交栏。

**关键交互**：类型切换显隐字段；picker 选食堂；图片≤9；提交校验。

**组件拆分**：复用 `CardSection`、`AppButton`、`Header`、`ImageUploader`（**现存**）；**`TypeSwitch` 待抽（当前内联）**；须走 `api/stall.ts` 封装（禁止直连 HTTP），加载/提交中态用内联态（非已删 Loading）。

---

## 4.3 my-publish（我的发布） `pages/profile/my-publish.vue`
**定位**：学生已发布菜品/档口·食堂的审核状态总览。

**信息架构（区块清单）**：`Header`（返回 + 标题「我的发布」） + 分段(菜品/档口·食堂，**`SegmentTabs` 待抽/内联**) + 列表(`PublishItem` 待抽，内联 `MomentCard` 风格行) + `EmptyState` + 底部操作（`AppButton` 避让 `--action-bar-height`）。

**关键交互**：分段切重载；菜品项 tap → 编辑；卡片按下 `scale(0.97)`。

**组件拆分**：复用 `StatusBadge`、`EmptyState`、`AppButton`、`Header`；加载态用内联态（非已删 Loading）；**`SegmentTabs` 待抽、`PublishItem` 待抽（当前内联）**；移除 `as any` 兜底，齐备三态。

---

## 4.4 my-submissions（我的提交） `pages/profile/my-submissions.vue`
**定位**：学生「申请下架/纠错」与动态提交记录聚合查看。

**信息架构（区块清单）**：`Header`（返回 + 标题「我的提交」） + 分段(实体/动态，**`SegmentTabs` 待抽/内联**) + 列表(`SubmissionItem` 待抽，内联) + `EmptyState`。

**关键交互**：分段切；动态项 tap → 详情。

**组件拆分**：复用 `StatusBadge`、`EmptyState`、`Header`；加载态用内联态（非已删 Loading）；**`SegmentTabs` 待抽、`SubmissionItem` 待抽（当前内联）**；统一 `SubmissionItem`，off 态用 `StatusBadge` 扩展状态，齐备三态。

---

## 4.5 settings（设置） `pages/settings/index.vue`
**定位**：应用设置与账号管理。

**信息架构（区块清单）**：`Header`（返回 + 标题「设置」） + 分组(`SectionTitle`「通知」/「通用」/「账号」，组内条目用 `SettingGroup`+`SettingCell` 待抽/内联) + 版本号。

**关键交互**：通知开关（未接后端者标注「即将推出」或隐藏）；关于/隐私 Modal；清缓存；退出/注销 `uni.showModal` 二次确认。

**组件拆分**：复用 `Header`、`SectionTitle`、`StatusBadge`；加载态用内联态；**`SettingGroup`+`SettingCell` 待抽（与 profile 菜单统一，当前内联）**。

**视觉规范**：未生效设置（如暂无后端订阅能力的通知开关）须显式标注「（即将推出）」或默认隐藏，禁止虚假控制（与 §1.4 一致）。

---

## 4.6 feedback（意见反馈） `pages/feedback/index.vue`
**定位**：用户建议/Bug 反馈。

**信息架构**：Header + 类型 chips + 内容 textarea + 联系方式 + 提交栏。

**关键交互**：类型 chip 单选；内容计数；提交校验。

**组件拆分**：复用 `AppButton`、`Header`、`FeedbackForm`（**待抽，与 contact 共用同一组件**）、`CardSection`、`SectionTitle`；contact 与 feedback 复用同一 `FeedbackForm` 与 `CardSection`。

---

# 五、工具

## 5.1 webview（外部链接） `pages/webview/index.vue`
**定位**：承载 Banner URL / 公众号文章等外部 H5。

**信息架构**：Header + web-view(src) 或 无效链接 fallback。

**关键交互**：onLoad 取 src/title；加载失败 → 复制链接 Toast。

**组件拆分**：复用 `Header`；web-view 加载中态用内联（非已删 Loading 组件）；空 src 也提供「复制链接」入口（`AppButton` text 类型）。

---

# 六、组件拆分总览（去重，已与代码库对齐）

> 分类依据：`frontend/src/components/` 当前真实现存（20 个）/ 待开发工程师重新抽组件实现（f9560c6 已删或未曾抽）。

**A. 现存组件（可直接复用，20 个）**
`AppButton` / `CardSection` / `CustomTabBar` / `DishCard` / `EmptyState` / `header` / `IconSvg` / `ImageFallback` / `ImageSwiper` / **`ImageUploader`（已抽，§2.5/§3.1/§4.1/§4.2 复用）** / `MomentCard` / `Rating` / **`RelatedPickerSheet`（已抽，§3.1 复用）** / `SearchBar` / `SectionTitle` / **`StallCardSingle`（单列档口卡，§2.2/§2.3 复用，原 `StallCard` 已删）** / `StatusBadge` / `TagLabel` / `UsefulButton` / `WaterfallList`。

**B. 待抽组件（代码库当前不存在，须 task 重新实现；实现前对应页内联）**
1. `SegmentTabs`（**滑块式，白底滑块 spring 1.0/0.3；五页共用：find / my-moments / my-publish / my-submissions**（find 排序条 + 三个我的子页分段均复用同一数据驱动组件）；**community 页不渲染 Tab（单一最新流，见 §1.3），仅其他三页用**；视觉规范见 §七 #5 已决议）
2. `ReviewItem`（dish / review-list 评价卡共用，三处必须一致）
3. `ApplySheet`（dish / profile / stall 申请下架·纠错共用，**跨页共用**）
4. `FeedbackForm`（contact / feedback 共用，同一类型枚举）
5. `SettingGroup`+`SettingCell`（settings / profile 菜单统一；profile 菜单组落档见 §1.4 已登录态第 4 项）
6. `CanteenCard`（home 横滑食堂卡；原未抽，内联实现）
7. `FilterSheet`（**通用筛选 Sheet：find 食堂/价格/口味；spring 0.8/0.3 + 手势中断 + 遮罩关闭**，抽通用组件供 find 复用）
8. `InteractBar` / `CommentItem` / `ReportModal`（moment 详情互动/评论/举报）
9. `AuthForm`（profile 登录注册；**已决议：居中卡片式**——品牌区 `ic-logo` + 上浮卡片承载登录/注册/找回同卡切换 + 账号密码/邮箱验证码双路，见 §1.4 与 §七 #4）/ `ContributeSheet`（profile 我要贡献，§1.4 第 3 项）
10. `StatsRow`（profile 统计行，原 `StatsCard` 已删，改内联/小卡片；**三宫格定稿字段 `我的评价`(reviewCount)/`已发布`(publishedCount)/`待审核`(pendingCount)，favoriteCount 以 0 占位不展示，见 §1.4 / §七 #3**）
11. `PublishItem` / `SubmissionItem`（my-publish / my-submissions）
12. `StallDishRow`（stall 行式菜品卡）
13. `HotSearchList` / `HistoryChips`（find 热搜/历史）
14. `TagSelector`（publish-dish 口味标签，内联 chips 替代）/ `TypeSwitch`（submit-stall 类型切换）
15. `EmptyState` 扩展 `action`/`actionText`/`@action` 槽（统一承载「发布第一条动态」等主按钮；community 空态用，见 §1.3）

> 注：`ImageUploader`、`RelatedPickerSheet` 已在上一轮实现并存在（§0.2），不再列入"待抽"。`Loading`（组件）已删，**加载态统一用内联态**（见 §0.2/§0.4），不在此列。

**C. 三态强制（加载中/空态/正常）**
- 空态：`EmptyState`（现存）。
- 加载中：内联态（**非 Loading 组件**）。
- 正常：业务内容。

---

# 七、待裁定项（集中在末尾，禁止散落臆测）

以下问题需求未定 / 后端契约缺失 / 需技术负责人拍板，**不臆测实现**：

1. **project_spec §4.2 组件清单过期**：§4.2 第 61 行仍列 `CategoryTabs/Loading` 为"必须复用"自研组件，与 f9560c6 删除事实冲突。已在本文件 §0.2 校准，并**报备技术负责人**：待 project_spec 同步删除 `CategoryTabs`/`Loading`/`StallCard`/`StatsCard` 组件名（UI-UX 不得擅改 project_spec）。
2. **【已决议 · `ic-loading` 登记 + emoji 清理合并】全量禁用 emoji 为强制红线（见 §0.4/§0.5）：home 及全小程序禁用 `emoji.ts`，图标统一走 `IconSvg` + 阿里云矢量库（Iconfont MCP 补齐缺失图标）；加载中内联态不引入 emoji，建议用文案+"转圈"CSS 或骨架，若确需图标再在 §0.5 登记。
3. **「我的」统计口径**：`StatsRow` 三宫格字段已定稿为 **`我的评价`(reviewCount) / `已发布`(publishedCount) / `待审核`(pendingCount)**（A.16 契约，与 `types/user.ts` `UserStats` 对齐）；`favoriteCount` 后端暂以 0 占位、本期不展示（不臆测"收藏"语义）。**【已决议 · 三宫格全量渲染】** 字段已在 `types/user.ts` 定义（`publishedCount?`/`pendingCount?`/`reviewCount`），前端缺失时以 0 兜底显示，**禁止只渲染单格 reviewCount**（原 §1.4 备注"当前仅 reviewCount 先保留"为临时态，已撤销，须改为三宫格齐渲染）。本待裁定项已关闭。
4. **`AuthForm` 重构方案**：**【已决议】居中卡片式 AuthForm**——品牌区 `ic-logo` + 上浮卡片承载登录/注册/找回同卡切换 + 账号密码/邮箱验证码双路，登录/注册/找回三态同卡、账号密码与邮箱验证码双路并存（`ic-user`/`ic-mail`/`ic-lock` 全局图标，全 emoji→IconSvg）。用户拍板：全部按设计师推荐方案确认。`AuthForm` 抽独立组件（§六 B-9）。本待裁定项已关闭。
5. **【已决议 · SegmentTabs 视觉规范】滑块式**：分段选择器采用**滑块式 `SegmentTabs`**——白底滑块随选项左右滑动，spring 1.0/0.3（可中断、速度接力），比胶囊更 Apple；**五页共用同一数据驱动组件**（find 排序条 / my-moments / my-publish / my-submissions 分段，见 §六 B-1）。各页禁止各做一套样式，均复用此组件。**例外一（已决议）：community 页不渲染 Tab**——用户拍板社区笔记帖子不需要筛选 tab，改为单一「最新」逆序信息流（见 §1.3）；`SegmentTabs` 仅保留组件契约供其余三页复用。**例外二（关联说明）：profile 主页不渲染 Tab**——profile 主页为「用户信息卡 + 菜单组」卡片结构，分段选择器仅用于子页（my-moments / my-publish / my-submissions），主页明确不渲染 `SegmentTabs`（见 §1.4 信息架构「已登录态（主页不渲染 Tab）」）。本待裁定项已关闭。
6. **广播通知条数据来源**：`broadcastList` 的接口与广播类型枚举待后端契约确认，首页仅按 `v-if` 接真实数据。
7. **Web 与管理端共用图标语义**：小程序 `ic-*` 与 Element Plus 图标映射在 Web 端如何 1:1 对应（如"食堂"Web 用 `OfficeBuilding`、小程序用 `ic-dish`？），待技术负责人裁定两端语义对齐表。**补充（已决议 D）**：find 分类宫格 8 个分类须配独立矢量图标（经 Iconfont MCP 拉取，禁止回退 `ic-dish` 通用图标），该批图标须同步登记入 §0.5 映射表，并在两端语义对齐表中占一席。
8. **【新增 · 后端需补齐示例数据以验证三态】** 当前数据库可能尚未填充数据（含 Banner / 广播 / 热门菜品等首页数据源）。三态（加载中 / 无数据 EmptyState / 正常）与「无数据」vs「加载失败」文案区分须经真实验证；**待后端补齐首页相关示例数据后，再由开发工程师在真机/IDE 验证首页三态与空态占位（不隐藏）落地**。

---

# 八、UI 细节落实清单（finalize · 2026-07 复审定稿）

> 本轮在 qa-1「无 🔴 可交付」结论后，对 `frontend/src` 全量页面/组件逐文件比对本定稿，沉淀"文档已约定但实现有偏差"的细节问题。每条含 file:line + 阻断级别 + 修复指引，**标 🟠/🔵 的问题由开发按本清单与对应小节细则修代码；标 ✅ 为已落实无需改**。红线（emoji / 金额 / WaterfallList slot / 三态 / Sheet 规范）经核查**本轮未回退**。

| # | 位置 | 问题 | 级别 | 修复指引（对应细则） |
|---|---|---|---|---|
| 1 | `components/StatsRow.vue:1-53` + `pages/profile/index.vue:31` | 统计行仅渲染单格「我的评价」(reviewCount)，§1.4/§七#3/A.16 要求三宫格（评价/已发布/待审核）。`publishedCount`/`pendingCount` 已在 `types/user.ts:13-15` 定义但 `StatsRow` 不接收、不渲染。 | 🟠 | `StatsRow` 改收 `publishedCount`/`pendingCount`/`reviewCount` 三 prop，渲染三格；缺失以 0 兜底；每格 `scale(var(--press-scale))` 跳对应页。细则见 §1.4 第 2 项 / §七 #3。 |
| 2 | `pages/home/index.vue:417` | 死样式类 `.empty-illu`（模板已改用 `<IconSvg name="empty">`，该类未被引用）。 | 🔵 | 删除 `.empty-illu` 死 CSS（`:417` 整行）。 |
| 3 | `pages/home/index.vue:387` | `.broadcast-bar.pressed { transform: scale(0.985) }` 违反 `--press-scale: 0.97` 统一值（§0.3）。 | 🟠 | 改为 `transform: scale(var(--press-scale));`，统一引用 token。 |
| 4 | `components/CommentItem.vue:115` | `.c-useful:active { transform: scale(0.95) }` 违反 0.97 统一值。 | 🟠 | 改为 `scale(var(--press-scale))`。 |
| 5 | `pages/settings/index.vue:134` | `.cell:active { transform: scale(0.99) }` 不一致。 | 🔵 | 改为 `scale(var(--press-scale))`（与 SettingCell 其余按下一致）。 |
| 6 | `components/ContributeSheet.vue:156` | `.sheet-option.pressed { transform: scale(0.99) }` 不一致。 | 🔵 | 改为 `scale(var(--press-scale))`。 |
| 7 | `pages/feedback/index.vue:8,24,37` + `components/FeedbackForm.vue:4,14,27` | 手写 `<text class="section-title">` 无 accent 条，违反 §0.3/§0.4「禁止手写分区标题」。 | 🟠 | 改为 `<SectionTitle title="…" />`（feedback 三处、FeedbackForm 三处）。 |
| 8 | `pages/publish-moment/index.vue:19,28` | 手写 `.section-head`+`.section-title`（图片/关联对象）无 accent 条。 | 🟠 | 改为 `<SectionTitle title="图片" /> / <SectionTitle title="关联对象" />`。 |
| 9 | `components/ContributeSheet.vue:43` + `pages/profile/index.vue:40` | 用 `ic-arrow-left` 旋转 180° 模拟右箭头，应统一 `ic-arrow`（§0.5 已登记右箭头语义）。 | 🔵 | 改 `name="arrow-left"` → `name="arrow"`，移除 `transform: rotate(180deg)`。 |
| 10 | `components/AppButton.vue:43` | 旧注释「MVP 统一用 emoji 占位」与现状（icon 走 `IconSvg` 矢量名，全量禁 emoji）不符，易误导。 | 🔵 | 更新注释为「icon 为 IconSvg 矢量图标名，全量禁 emoji」，删除旧 emoji 占位说明。 |
| 11 | `stores/user.ts:26,84` | `userStats` 默认值不一致：初始 `{reviewCount:0}`；`logout()` 置 `{likeCount:0, reviewCount:0}` 缺 `favoriteCount`/`publishedCount`/`pendingCount`。字段缺省态不统一（非阻断，但致三宫格补齐后 logout 态缺字段）。 | 🔵 | `logout()` 与初始默认统一为 `{ reviewCount:0, publishedCount:0, pendingCount:0, favoriteCount:0 }`。 |
| 12 | `docs/mini-app-ui.md §0.5` | 图标映射表漏登记已实际使用的 `ic-delete`/`ic-profile`/`ic-price`/`ic-check`/`ic-back`/`ic-filter`/`ic-fire`/`ic-home`。 | 🔵 | 本轮已补登（见 §0.5 表）。后续新增图标须先登记后使用。 |
| 13 | `pages/pages-detail/dish.vue` action-bar | 原 §2.1 备注「硬编码 140rpx 须改为变量」——经核查已实现为 `var(--action-bar-height)`，文档与实现不符。 | ✅ | 已落实，原备注撤销（见 §2.1 修订）。 |
| 14 | `components/ApplySheet.vue` / `FilterSheet.vue` / `dish.vue` share-sheet | Sheet 入场 `cubic-bezier(0.32,0.72,0,1) 0.3s` + `ic-close` + `prefers-reduced-motion` 降级均到位。入场用 `requestAnimationFrame` 触发（运行时已 polyfill，非必改）。 | ✅ | 符合红线⑤，无需改；`requestAnimationFrame` 维持。 |
| 15 | 全局 | 红线核查：emoji 全清（grep 仅命中注释，无 Unicode emoji 字符）；金额仅 api 层 `fenToYuan`/`yuanToFen`（页面无 `/100` 裸算，`/100`/`/1000` 命中均为百分比/pagination/计数语义）；`WaterfallList` 禁具名 slot（组件内已注释封禁）；三态（loading/empty/normal）首页/community/dish 等齐备。 | ✅ | 红线均未回退，保持。 |

**落实分工**：第 1/3/4/7/8 项（🟠）须即刻派 `miniapp-dev` 改代码；第 2/5/6/9/10/11/12 项（🔵）由开发按本清单随后续迭代修，不阻断交付验收。

---

# 九、本轮微信用户问题修复方案（2026-07 微信直报 · 可直接派工）

> 来源：用户经微信发来的一批具体 UI 问题，要求逐项修复并定稿。本文档为「设计侧定稿 + 派工依据」，只改本文档，不改业务代码（除文档外）。所有 file:line 均基于 `frontend/src` 当前真实现状。
> 一致性红线（不可违反）：emoji 全用 IconSvg；金额仅 api 层；WaterfallList 禁具名 slot；三态齐备；Sheet 弹簧+ic-close+reduced-motion。
> 标记说明：**【需确认】**= 设计决策须用户拍板；**【直接修复】**= 按本细则直接改，无需再问。

---

## 9.1 统一结论（星色 / 对齐边界 / 渐变）

### 9.1.1 黄色星色（A3 / C12 / D12 / E13 统一结论）
- **建议 `--color-star` 改为明确黄：`#FFB400`**（当前 `#F5A623` 偏橙、在白底/深色角标上显"黑/橙"不干净，用户感知为「黑」）。
  - 改法：`src/App.vue` `page` 块 `:33` 与 `:root` 块 `:143` 两处 `--color-star: #F5A623;` → `--color-star: #FFB400;`。
- **星色语义（Rating 组件已正确）**：`Rating.vue:33-34` 实星 `activeColor: var(--color-star)`、空星 `emptyColor: var(--text-tertiary)`（A89E96 中灰）。即：**实星=黄(#FFB400)，空星=浅灰(tertiary)**。此组合清晰，保留。
- **为何 StallDishRow / DishCard 角标显黑**：
  - `StallDishRow.vue:22` 用的是 `<Rating>`（实星已是 star 色，没问题）；但 `StallDishRow.vue:56` `.dish-row-rating` 文字用 `--color-star`——只要 star 改黄即正常。
  - `DishCard.vue:19` 角标用**白色星 `var(--text-white)` + 深底**（非 Rating 组件），白星在深色角标上是白色、不是黑；用户说的「黑」应是指首页其它处或直接把角标星理解错了。统一改法见 A3。
- **【需确认】** 黄色具体 hex 选 `#FFB400` 还是 `#FFC53D`（更浅亮）。设计师默认推荐 `#FFB400`（对比度更适合白底与深角标），按推荐执行；若用户偏好更亮可换 `#FFC53D`。

### 9.1.2 页面左右对齐边界（A1 / B4 / B7 / C8 / D11 统一约定）
- **统一左右 padding 变量**：所有页面内容区左/右内边距统一为 `var(--spacing-md)`（24rpx），**禁止各自写不同 px/rpx 或满边**。
- 现状核对：
  - home `:317` `.section { padding: 0 var(--spacing-md) }` ✓ 已统一。
  - find `:539` `.block { padding: 0 var(--spacing-md) }` ✓；但 `:6` 搜索栏 `margin: var(--spacing-sm) var(--spacing-md)` ✓ 同边界。
  - canteen `:230` `.stall-stream { padding: 0 var(--spacing-md) }` ✓；但 `:208` `.canteen-hero { margin: var(--spacing-md) }` 是外边距，内部内容左边界 = 卡片内边距，与下方 stall 流左边界**不在同一竖线**（卡片有 24rpx 内边距 + 24rpx 外边距，比 stall 流多一层）。见 D11。
  - profile `:256` `.user-card { margin: var(--spacing-md) }`、`:266` `.contribute-card`、`:277` `.logout-wrap` 均用 `margin: … var(--spacing-md)`，但**页面根 `scroll-wrap` 无统一 padding**，各块靠自身 margin 留白，左右边界一致 ✓；用户说「贴边缝」是因其它页（如 find 直接 padding 在 block 上）观感不同，统一即可。
- **约定**：新增/整改页面一律用「根 `.scroll-wrap` 不加 padding，子区块 `.block { padding: 0 var(--spacing-md) }` 或卡片 `margin: var(--spacing-md)`」二选一并全页统一，左边界全部落在 24rpx 竖线。

### 9.1.3 渐变 → 主题色（F 统一结论）
- **原则**：移除所有品牌渐变，改用主题实色。**主色 `#8B3A2B`（品牌红）/ 深红 `#6B1010`**。保留 `App.vue` 的 `--color-gradient` 变量可删（或直接不再引用）。骨架屏 shimmer 的浅灰渐变**属于加载占位动画，非品牌渐变，保留**（不在此列）。
- 命中文件与改法见 F 各条。

---

## 9.2 逐条修复方案（A1~G18）

> 每条格式：`位置` + `期望样式值/行为` + `是否需改 Token` + `【直接修复/需确认】`。

### A. 首页 home/index.vue
**A1 区块与下方卡片左对齐**【直接修复】
- 现状：`:317` `.section` 已 `padding: 0 var(--spacing-md)`；`:310` `.swiper-section` 已 `padding: var(--spacing-sm) var(--spacing-md) 0`；`:401` `.home-skeleton` 已 `padding: 0 var(--spacing-md)`。
- 结论：食堂入口（`:81` `.section`）、热门菜品（`:110` `.section`）、广播（`:49` `.section`）已与 WaterfallList 同边界（均 24rpx）。**无需改样式**，但需在文档固化「三者同用 `.section` 的 `--spacing-md` 边界，禁止在任一区块加额外左 padding」。
- 是否改 Token：否。

**A2 广播逐条单条滚动 + 无空行**【直接修复】
- 现状：`:166-172` `startBroadcastRotation` 每 1s `broadcastIndex = (idx+1)%len`；`:62-75` track 用 `translateY(-index*100%)` 整体位移，ticker 高 `40rpx`、每条 `40rpx`。逻辑本身是「逐条单条」滚动，**问题在数据源**：`loadBroadcast` 回落默认 3 条均非空，正常情况下多条会一次跳多格？——实际是单步 +100%（单条）。用户感知「一次滚好几条/空白」源于：① 定时器 1000ms 与动画 450ms，若数据更新导致 index 跳变；② `broadcastList` 含空 `text` 时渲染空行（`:72` 未过滤）。
- 修复：
  1. `:62` `v-if="broadcastList.length > 0"` 内改为 `broadcastList.filter(b => b.text && b.text.trim())` 渲染，过滤空文本避免空白行。
  2. `:166` `startBroadcastRotation` 仅当 `length > 1` 才启动（已 `if (length<=1) return`，✓）；把间隔从 1000ms 提到 **3000ms**（`:171` `1000` → `3000`），避免「滚太快像一次好几条」的观感。
  3. `:347` 动画 `0.45s` 保留（单条缓动 OK）。
- 是否改 Token：否。

**A3 热门菜品卡片右上角星应为黄色**【直接修复】
- 现状：`:19` `DishCard.vue` 角标用 `<IconSvg name="star" color="var(--text-white)">` + 深底 `var(--overlay-dark-strong)`。这是「白星+深色角标」设计，并非黑。
- 用户要「黄色星」：角标改 `color="var(--color-star)"`（即 #FFB400，见 9.1.1）。同时角标底建议保留深色 `var(--overlay-dark-strong)`（黄星配深底最清晰），或改浅底+黄星描边。
- 改法：`DishCard.vue:19` `color="var(--text-white)"` → `color="var(--color-star)"`；`:119` `.rating-text { color: var(--text-white) }` 保留（数字在深底为白，OK）。
- 说明：DishCard 当前是「单颗星+数字」角标（非 Rating 组件）。是否换 Rating 组件？**建议保留角标（轻量、不占空间），仅改星色为黄**；不要在卡片角标里嵌完整 Rating（过于重）。
- 是否改 Token：是（依赖 9.1.1 的 `--color-star` 改正）。

### B. 发现页 find/index.vue
**B4 搜索栏与下方网格/热搜左右对齐**【直接修复】
- 现状：`:6` `.search-wrap { margin: var(--spacing-sm) var(--spacing-md) }`，`:539` `.block { padding: 0 var(--spacing-md) }`。搜索栏是 `margin`（外边距 24rpx），block 是 `padding`（内边距 24rpx）——**两者左边界在同一竖线（均 24rpx）**，已对齐 ✓。
- 结论：无需改；固化约定。但需确认 suggest-panel（`:511-514`）`left/right: var(--spacing-lg)` 应随搜索栏边界——当前用 `lg(32rpx)` 比搜索栏宽，视觉上 suggest 比搜索框宽出 8rpx。**改**：`:513-514` `left/right: var(--spacing-md)` 与搜索栏同边界。
- 是否改 Token：否。

**B5 搜索栏吸顶固定**【需确认】
- 现状：`:6` 搜索栏在 `Header` 下方、scroll-view（`:40`）上方，是普通文档流，**不吸顶**（滚动时随内容滚走）。
- 用户质疑「不是固定的吗」→ **结论：发现页搜索栏应吸顶 fixed**。
- 实现方式（二选一）：
  - 方案甲（推荐）：外层 `scroll-view` 不变，把 `.search-wrap`（`:508` `position: relative; z-index:20`）改为 `position: sticky; top: 0; z-index: 20; background: var(--bg-page)`（小程序 scroll-view 内 sticky 支持有限，需实测；若不支持改方案乙）。
  - 方案乙（稳妥）：`.search-wrap` 移到 scroll-view 之外、作为页面固定头部（`position: fixed; top: Header高度; left/right:0`），scroll-view 顶部加 `padding-top` 占位（高度 = 搜索栏高 + spacing）。
- **【需确认】** 采用吸顶（用户已明确要吸顶，默认按方案乙执行，因小程序 sticky 兼容差）。吸顶后搜索栏背景须 `var(--bg-page)` 不透明，避免内容透出。
- 是否改 Token：否。

**B6 搜索框内部上下间距太大**【直接修复】
- 现状：搜索框是 `SearchBar` 组件（`:7`），其上下间距由组件内 `padding` 决定；外层 `.search-wrap` 仅 `margin: var(--spacing-sm) var(--spacing-md)`（上下 16rpx，不大）。
- 改法：缩小 `SearchBar.vue` 组件内部 vertical padding（如 `padding: 12rpx 24rpx` → `padding: 8rpx 24rpx`）或缩小外层卡片（若搜索栏包了卡片）。需查 `SearchBar.vue` 实际 padding（本批未读该组件，**派工时令开发读 `components/SearchBar.vue` 定位 padding 行**并缩小上下内边距至 ≤16rpx）。
- 是否改 Token：否。

**B7 本周热搜标题与内容卡片左对齐**【直接修复】
- 现状：`:111` `<SectionTitle title="本周热搜">` 在 `.block`（`:539` `padding:0 var(--spacing-md)`）内；`:112` `.hotsearch-list` 也在同 `.block` 内。标题与卡片左边界已一致（均 24rpx）✓。
- 结论：已对齐，无需改。固化约定。
- 是否改 Token：否。

### C. 我的 profile/index.vue
**C8 页面内容贴边缝，无左右 gap**【直接修复】
- 现状：根 `.scroll-wrap`（`:253`）无 padding；各块靠自身 `margin: var(--spacing-md)`（`:256` user-card、`:266` contribute-card、`:277` logout-wrap）留白，左右边界 = 24rpx，已不贴边。
- 但用户感知贴边：因 `.version-row`（`:274` `padding: var(--spacing-xl) var(--spacing-lg)`）、`.cancel-account`（`:278`）等无左右 margin，左右比其它块宽（用了 lg 32rpx）。**统一**：这些块也改为 `margin: 0 var(--spacing-md)` 或加 `padding: 0 var(--spacing-md)`。
- 改法：`:274` `.version-row` 改 `padding: var(--spacing-xl) var(--spacing-md) var(--spacing-md)`；`:278` `.cancel-account` 改 `padding: var(--spacing-md) var(--spacing-md) var(--spacing-sm)`。
- 是否改 Token：否。

**C9 StatsRow + 贡献入口尺寸收敛**【直接修复】
- 现状（三宫格已齐，§七#3）：`StatsRow.vue:45` `.stats-row { gap: var(--spacing-sm) }`；`:54` `.stat-cell { padding: var(--spacing-md) 0 }`；`:63` `.stat-value { font-size: var(--font-h2)(40rpx) }`；`:64` `.stat-label { font-size: var(--font-aux)(22rpx) }`。
- 收敛建议（对齐 §0.3 Token）：
  - `StatsRow.vue:54` `.stat-cell` padding `var(--spacing-md) 0` → `var(--spacing-sm) 0`（16rpx，更紧凑）。
  - `StatsRow.vue:63` `.stat-value` 字体 `var(--font-h2)(40rpx)` → `var(--font-h3)(36rpx)`；`:64` `.stat-label` 保持 `var(--font-aux)(22rpx)` 或降 `var(--font-tiny)(20rpx)`。
  - 贡献入口 `profile/index.vue:266` `.contribute-card` padding `var(--spacing-md)` → `var(--spacing-sm)`；`:268` `.contribute-icon` 尺寸 `80rpx` → `64rpx`；`:270` `.contribute-title` 保持 `var(--font-body)`；`:271` `.contribute-sub` 保持 `var(--font-aux)`。
- 是否改 Token：否（用现有 Token 更小档位）。

### D. 食堂详情页 pages-detail/canteen.vue
**D10 移除档口图「新」字组件**【直接修复】
- 现状：canteen 列表用 `WaterfallList single type="stall"`（`:41`）→ 渲染 `StallCardSingle.vue`。该卡 `:19-22` 评分角标在 `rating != null` 时显示；`:91-94` `formatRating` 当 `rating==null || 0` 返回 `'新'`，即 **0 分档口的角标文字是「新」**（用户看到的「左下角新字」实为评分角标显示了「新」）。
- 修复（消除「新」语义）：`StallCardSingle.vue:91-94` 改为——`rating == null` 时不渲染角标（删 `:19` 的 `v-if="stall.rating != null"`，改 `v-if="stall.rating != null && stall.rating > 0"`）；`formatRating` 直接 `return rating.toFixed(1)`（0 分显示 `0.0`，不显示「新」）。彻底移除「新」字逻辑与注释（`:57` 注释删）。
- 注意：§2.2 原「档口图上去除『新』角标」即指此，本次落实。
- 是否改 Token：否。

**D11 全部菜品列表与档口图左沿对齐**【直接修复】
- 现状：`:208` `.canteen-hero { margin: var(--spacing-md); padding: var(--spacing-md) }`（卡片有外边距 24rpx + 内边距 24rpx，内容左边界在 48rpx 处）；`:230` `.stall-stream { padding: 0 var(--spacing-md) }`（左边界 24rpx）。两者左边界差 24rpx。
- 用户指「全部菜品(StallDishRow)」——但 canteen 页用的是 `StallCardSingle` 单列流（非 StallDishRow）；StallDishRow 在 stall.vue。此处应为「档口列表左沿与上方食堂图左沿对齐」。
- 改法：`:230` `.stall-stream` 改 `padding: 0 var(--spacing-md)` 不变，但让 `.canteen-hero`（`:208`）的**内容左边界**与 stall 流一致——最简：把 `.canteen-hero` 的 `margin: var(--spacing-md)` 保留，`.stall-stream` 也包一层 `margin: 0 var(--spacing-md)` 而非 `padding`（即两者都用 margin 外边距，左边界同落 24rpx）。或统一用 `.block { padding:0 var(--spacing-md) }` 包裹两者。
- 是否改 Token：否。

**D12 每个菜品星星黄色**【直接修复】
- canteen 页本身无 StallDishRow（见 D11），其 stall 卡评分角标用 `:20` `color="#FFD166"`（硬编码浅黄）。统一改为 `var(--color-star)`（即 #FFB400，见 9.1.1）。
- 改法：`StallCardSingle.vue:20` `color="#FFD166"` → `color="var(--color-star)"`。
- 是否改 Token：是（依赖 9.1.1）。

### E. 菜品详情 pages-detail/dish.vue
**E13 星星黄色**【直接修复】
- 现状：`:57` `<Rating :model-value="dish.rating" readonly>` 实星 `var(--color-star)`（默认），改 9.1.1 后即黄。无需额外改。
- 是否改 Token：是（依赖 9.1.1）。

**E14 评价数移到「用户评价」标题处**【直接修复】
- 现状：`:56-60` `.rating-row` 同行有 `Rating` + `rating-value` + `rating-count`（「X条评价」）。`:113-117` `<SectionTitle :title="`用户评价 (${reviewTotal})`"` 已含评价数。
- 改法：删 `:59` `<text class="rating-count">{{ dish.ratingCount }}条评价</text>`（星星旁不再显示数字；`rating-value` 数字可选保留或删——用户要「星星旁不显示数字」，故连 `:58` `.rating-value` 一并删）。标题处评价数已由 `:114` `(${reviewTotal})` 承担。若 `reviewTotal` 与 `ratingCount` 语义不同，开发确认取 `reviewTotal`（总评价数）展示在标题。
- 是否改 Token：否。

**E15 菜品信息区重新布局（写入文档）**【直接修复·布局方案】
- 现状（`:39-109`）：`CardSection` 内含 标题行（名+价）/ tag-row / rating-row，再到「菜品信息」CardSection（位置链 / 属性 / 介绍）。信息略散。
- **重新布局方案（层级/对齐/间距）**，写入本细则供开发落地：
  1. **主卡 CardSection（基础信息）**：
     - 第一行：菜名（左，`--font-h1` 700，`--text-primary`）｜价格（右，`--font-h2` 700，`--color-price`），`title-row` 用 `align-items: baseline` 已 OK（`:351`）。
     - 第二行：标签 TagLabel 行（`:52-54`），`margin-top: var(--spacing-sm)`。
     - 第三行：评分行（`:56`）Rating + 数值；**移除 rating-count**（见 E14）。`margin-top: var(--spacing-md)`、`padding-top: var(--spacing-sm)`、上方加 `border-top: 2rpx solid var(--border-color)` 分隔（视觉层次）。
  2. **「菜品信息」CardSection**：标题用 `SectionTitle`（已有 `title="菜品信息"`）。内部三段用 `.info-block` + `.info-block-divider`（`:384-385` 已有分隔线）保持：
     - 位置链（食堂›档口›窗口）`--text-primary` + 可点跳转；
     - 属性行（辣度/分量/限时）`--bg-soft` 浅底 pill（`--radius-tag`）；
     - 介绍 `var(--text-secondary)` 行高 1.6。
  3. **对齐**：所有行左边缘统一（CardSection 内 `padding: var(--spacing-md)` 已统一）；价格右对齐，标签左对齐，评分行左对齐。
  4. **间距梯度**：块间 `var(--spacing-md)`；行内 `var(--spacing-sm)`；分隔线上下 `var(--spacing-md)`。
- 是否改 Token：否。

**E16 喜欢按钮：未点黑、点了填充红**【需确认·图标能力】
- 现状：`:145-147` `fav-btn` 用 `<IconSvg name="heart" :color="liked ? 'var(--color-like)' : 'var(--text-tertiary)'">`；`:400-403` 未点时 `.fav-icon { opacity:0.35 }`，点了 `opacity:1` + 文字 `var(--color-like)`（红 #ff6b6b）。
- 用户要：未点**黑色描边**、点了**填充红**。
- 问题：`ic-heart.svg` 当前是描边 svg（线性），`fill` 由 CSS `color` 经 `currentColor` 控制——多数线性 heart svg 用 `fill:none;stroke:currentColor`，**无法"填充红"**，只能改 stroke 颜色（描边红），不是实心填充。
- 改法（方案）：
  - 方案甲（若 ic-heart 是描边型）：未点 `color: var(--text-primary)`（黑描边，去掉 opacity 0.35）+ `opacity:1`；点了 `color: var(--color-like)`（红描边）。即"黑描边→红描边"，非实心填充。
  - 方案乙（满足"填充红"）：新增 `ic-heart-filled.svg`（实心 heart，登记 §0.5），未点显示描边 `ic-heart`（黑）、点了切 `ic-heart-filled`（红填充）。**需 Iconfont MCP 拉取实心 heart**。
- **【需确认】** 采用方案乙（真填充红）还是方案甲（红描边）？设计师默认推荐**方案乙**（用户明确要"填充成红色"，实心更贴合），需经 Iconfont MCP 补 `ic-heart-filled` 并登记 §0.5。未点黑色：`color: var(--text-primary)`。
- 是否改 Token：否（用现有 `--text-primary`/`--color-like`）。

### F. 全局渐变 → 主题色
**F 逐文件改法**【直接修复】
- `App.vue:28` 与 `:138` `--color-gradient` 变量：保留声明但**不再被品牌区引用**；或直接删除（如删除需同步删 `AppButton.vue:90`、`:312` 引用）。建议：保留变量、把品牌渐变引用点改主题色，避免别处漏改。
- `home/index.vue:312` `.swiper-slide { background: var(--color-gradient) }` → 改 `background: var(--color-primary)`（品牌红 #8B3A2B）或 `var(--color-primary-dark)`（深红 #6B1010）。推荐 `var(--color-primary)`。
- `home/index.vue:314` `.swiper-overlay` 与 `:391` `.canteen-overlay`：**图片叠加暗化层（非品牌渐变，属图片遮罩）**，`linear-gradient(to top, dark…)` 是图片可读性遮罩，**保留**（不在此列——属 overlay 语义，非装饰渐变）。
- `AppButton.vue:89-91` `.btn-gradient { background: var(--color-gradient) }` + `review.vue:41` `type="gradient"`：提交按钮改用 `type="primary"`（红实色）。改 `review.vue:41` `type="gradient"` → `type="primary"`；`.btn-gradient` 样式可保留或删（建议删，避免回退）。
- `AuthForm.vue:282` `.auth-hero { background: var(--color-gradient) }` → `background: var(--color-primary)`（品牌红）。
- `canteen.vue:234-235` / `stall.vue:161` skeleton shimmer 渐变：**加载占位动画，保留**（非品牌渐变）。
- `App.vue:273` skeleton shimmer：**保留**。
- 是否改 Token：否（用现有 `--color-primary`/`--color-primary-dark`）。

### G. 发布动态 publish-moment/index.vue
**G18 统一风格**【直接修复】
- 现状：`:155` `.block { background: var(--bg-card); padding: var(--spacing-md); margin-bottom: var(--spacing-md) }`（白卡块，OK）；`:158-159` `.section-head`/`.section-sub` 是**手写标题无 accent 条**（违反 §0.3 禁止手写分区标题）；`:172` `.submit-bar` 用了 `AppButton`（OK）；`:168` `.related-picker` 用 `var(--bg-soft)` 浅底（OK）。
- 问题：① 手写 `.section-head`（`:158`）应换 `SectionTitle`（模板 `:19`/`:27` 已用 `<SectionTitle>`，但 `:158` `.section-head` 是死样式类，模板未用——实际模板已用 SectionTitle，该类未引用，可删）；② 整体与 find/review 表单页风格需一致：分区标题统一 `SectionTitle`、卡片统一 `CardSection`/白卡、按钮 `AppButton`、padding 统一 `--spacing-md`。
- 改法：
  1. 删 `:158-159` 死样式 `.section-head`/`.section-sub`（模板已用 `SectionTitle` + `#extra` slot，见 `:20`/`:28`，无需手写）。
  2. 确认 `.block` 白卡与 `CardSection` 视觉一致（圆角 `--radius-card`、阴影 `--shadow-card`、padding `--spacing-md`）——当前 `.block` 无 `box-shadow`，补 `box-shadow: var(--shadow-card)` 与 `CardSection` 同源（或改用 `<CardSection>` 包裹）。
  3. 提交栏 `:172` `.submit-bar` 已用 `AppButton`（OK），保留；背景 `var(--bg-card)` + `shadow-bar-soft` OK。
  4. 输入控件（textarea、related-picker）字号/颜色已用 Token（OK）。
- 是否改 Token：否。

---

## 9.3 派工优先级与确认项汇总

**【需用户确认】**
1. 星色具体 hex：`#FFB400`（默认推荐）还是 `#FFC53D`（更亮）。——影响 A3/D12/E13。
2. 搜索栏吸顶实现方式：方案乙（fixed 头部 + 占位，默认推荐，小程序兼容稳）。——B5。
3. 喜欢按钮"填充红"：方案乙（新增 `ic-heart-filled` 实心图标，默认推荐）还是方案甲（红描边）。——E16。

**【直接修复，可立即派工】**
- A1（固化边界，无需改）/ A2（过滤空文本+间隔3s）/ A3（角标星色改黄）
- B4（suggest 边界对齐）/ B6（SearchBar 内 padding 缩小，派工读 SearchBar.vue）/ B7（固化）
- C8（version/cancel 块加左右 margin）/ C9（StatsRow 收敛 + 贡献入口尺寸）
- D10（移除「新」角标逻辑）/ D11（canteen 图与 stall 流左对齐）/ D12（stall 卡星色改黄）
- E13（依赖星色）/ E14（评价数移标题）/ E15（信息区布局，按 9.2 方案落地）
- F（home swiper / AuthForm hero / review 提交按钮 渐变→主题色；overlay/shimmer 渐变保留）
- G18（删死 `.section-head` 样式 + block 补阴影统一 CardSection）

**改动 Token 项**：仅 A3/D12/E13 依赖「`--color-star` 改 `#FFB400`」（在 `App.vue` `page`+`:root` 两处），须先于组件改动落实。

**禁忌（红线）**：本次整改不得引入 emoji（星/心/分享均走 IconSvg）；金额不裸算；WaterfallList 不传 slot；三态不破坏；Sheet 规范不变。

---

## 10. 全局风格统一 + 评价页/子页面边距

> 范围：本次专项治理「全局观感一致性」+「评价页竖向 accent 条去除」+「9 个详情/子页面边距溢出与侧边距错误」。
> 唯一 Token 真相源：`frontend/src/App.vue`（`page` 与 `:root` 两条独立规则，小程序以 `page` 为准）。
> 本次**只做设计决策与规范审查，不改业务代码**；改动清单（§10.B）交给小程序开发工程师落地，不重新拍板。
>
> ⚠️ 路径校准（用户原始清单路径有偏差，以代码库真实路径为准）：
> - 正确：`pages-detail/review-list.vue`、`profile/my-submissions.vue`、`profile/my-publish.vue`、`profile/publish-dish.vue`、`profile/submit-stall.vue`
> - 正确（**不在 profile/ 下**）：`my-moments/index.vue`、`settings/index.vue`、`feedback/index.vue`、`notify/index.vue`（位于 `pages/` 一级目录）
> - 用户写的 `my-moments/notify/settings/feedback` 在 `profile/` 下均不存在，已按一级目录真实文件落档。

### 10.1 现状审计（不一致点清单）

| 维度 | 现状（不一致） | 期望（统一） |
|------|----------------|--------------|
| 横向边距（tabs / 顶部分段 / 顶部 action-row） | `my-submissions.vue:117`、`my-publish.vue:133` 的 `.tabs` 用 `padding: var(--spacing-md) var(--spacing-lg) 0`（左右 32rpx）；`my-moments/index.vue:131` 的 `.segment-wrap` 用 `padding: ... var(--spacing-lg)`；`settings/index.vue:132` 的 `.cell` 用 `padding: var(--spacing-md) var(--spacing-lg)`；`notify/index.vue:169` 的 `.action-row` 用 `padding: ... var(--spacing-lg)` | 所有顶栏 Tab/分段/顶部操作行的**横向边距统一 `var(--spacing-md)`（24rpx）**，与 `.scroll-wrap`、各卡片容器对齐，禁止 `spacing-lg` 作横向边距 |
| 顶层内容块左右边距 | `settings/index.vue` 的 `.cell`/`.version-row`、`notify/index.vue` 的 `.action-row` 本身靠 `scroll-wrap` 无 padding，但 `.cell` 自带 `var(--spacing-lg)` 左右内边距，造成「标题区域 24rpx、单元格文字却 32rpx」的内凹错位 | 单元格/列表项**不单独再用 `spacing-lg` 横向 padding**；容器级 `scroll-wrap` 用 `spacing-md`，内部项仅用 `spacing-md` 纵向 padding |
| 竖向 accent 条（SectionTitle） | `review-list.vue:14` 用 `<SectionTitle :title="..." />`（带 `│` 竖向 accent 条，`SectionTitle.vue:2` 的 `.section-bar`） | 评价页标题改为**纯文字标题（无 accent 条）** |
| border-radius | `publish-dish.vue:209` 的 `.tag-chip` 用 `border-radius: 28rpx`；`submit-stall.vue` 图片移除圆角用 `var(--radius-icon)` OK；`find/index.vue:630` 的 `.filter-trigger` 用 `border-radius: 28rpx`；`feedback/index.vue:99` 的 `.type-chip` 用 `var(--radius-tag)`（OK） | 卡片/图片统一 `var(--radius-card)`(16px) 或 `var(--radius-icon)`(12px)；**胶囊类（chip/标签/按钮药丸）统一 `var(--radius-tag)`(999rpx)**；删除散落的裸 `28rpx`，改用 `radius-tag`（chip）或 `radius-btn` |
| IconSvg 尺寸 | 列表/菜单图标混用 `36`（SettingCell `:size="36"`、`settings/index.vue:9` bell、`notify/index.vue:32` 徽标）、`28`（arrow 行内）、`22`（off 标/star 行内）、`24`（close 删除）、`56`（空态兜底）、`52`（find 分类）、`40`（dish share）、`120`（home 空态 illu）；无统一语义 | 建立图标尺寸阶梯 token：`--icon-xs`(22rpx 行内小图标) / `--icon-sm`(28rpx arrow·行内) / `--icon-md`(36rpx 列表·菜单·徽标) / `--icon-lg`(48rpx，已有) / `--icon-xl`(56rpx 空态兜底·大图标)。**新增 `--icon-xs: 22rpx`** |
| 字体尺寸裸字面量 | `dish.vue:373/407`、多处用裸 `20rpx`/裸 `30rpx`；`find/index.vue:631/636` 用裸 `26rpx`；`my-submissions.vue:130` 用裸 `20rpx`；`settings/index.vue` 用 `font-size: 24rpx`（应为 `--font-small`）；`profile/index.vue:273` 用裸 `24rpx` | 全部改用 Token：`--font-tiny`(20rpx) / `--font-aux`(22rpx) / `--font-small`(24rpx) / `--font-body`(28rpx) / `--font-caption`(30rpx) / `--font-subtitle`(32rpx) / `--font-h3`(36rpx)。**裸 `26rpx`→`--font-body`(28rpx)**（find 筛选触发/返回）；裸 `30rpx`→`--font-caption` |
| 字体字重/行高 | 标题 `font-weight:700/800` 混用；小字 `line-height:1` 裸写 | 标题统一 `700`（H2/H3/大标题），不跨页跳 `800`；`line-height` 仅在特殊行内图标场景裸写，常规用 token 字体自带行高 |

### 10.2 统一规范（设计 Token 落地）

#### 10.2.1 侧边距规则（红线）
- **每个顶层内容块（卡片、列表容器、SectionTitle 区块）**与 **`.scroll-wrap`** 横向一律 `var(--spacing-md)`（24rpx）。
- **Tab 行 / 分段 / 顶部操作行（segment-wrap / tabs / action-row）横向也用 `var(--spacing-md)`**。
- **绝对禁止** 在横向使用 `var(--spacing-lg)`（32rpx）——它只用于纵向区块间距（`margin-bottom`/`gap`），不用于左右边距。
- 全屏卡片（`.sub-item`/`.publish-item`/`.notify-item` 等）靠 `scroll-wrap` 的 `padding: var(--spacing-md)` 留白，自身**不再**加左右 margin，避免 double-padding 或内凹。

#### 10.2.2 圆角规则
- 卡片：`var(--radius-card)`（16px）。
- 图片 / 小图 / 输入框 / 头像方：`var(--radius-icon)`（12px）。
- 弹窗：`var(--radius-modal)`（24px）；按钮：`var(--radius-btn)`（16px）。
- **胶囊（chip / 标签 / 药丸按钮 / 历史 chip / 筛选触发 / 类型 chip）：统一 `var(--radius-tag)`（999rpx）**——原散落裸 `28rpx`（publish-dish `.tag-chip`、find `.filter-trigger`）改 `radius-tag`。
- **仅小号圆形删除徽标（图片移除 ×）用 `border-radius: 50%`**；其余一律不用裸 `50%`。

#### 10.2.3 图标尺寸阶梯（新增 `--icon-xs`）
Token 现状：`--icon-sm:28rpx`、`--icon-lg:48rpx`（仅两个）。
本次**新增** `--icon-xs: 22rpx` 到 `App.vue` 的 `page` + `:root` 两处（与既有两处同步，避免小程序 WXSS 空变量）。
统一语义分配：
- `--icon-xs`（22rpx）：行内极小图标——`off` 标 lock、行内 star、行内 location/clock（dish 详情信息行）。
- `--icon-sm`（28rpx）：arrow 行内箭头、行内小箭头（SettingCell arrow、picker arrow）。
- `--icon-md`（36rpx，**新增语义绑定现有 36 用法**）：列表/菜单/通知徽标主图标（SettingCell icon、settings bell、notify 类型徽标）。
- `--icon-lg`（48rpx）：大区隔图标。
- `--icon-xl`（56rpx，**新增语义绑定现有 56 用法**）：空态兜底大图标、头像空态、find 分类大图标。
> 实现：各页 `IconSvg :size="22|28|36|48|56"` 维持数值即可（与 token 对齐），但**语义上**不得再出现 `:size="26"`、`30`、`32`（除 find 热搜/联想已用 32 待后续统一，本轮先不动 discover 主页以免范围蔓延，仅治理目标 9 页 + review-list）。

#### 10.2.4 字体梯度（全部走 Token，禁裸字面量）
| 用途 | Token | rpx |
|------|-------|-----|
| 超大标题（H1） | `--font-h1` | 48 |
| 大标题（H2/品类头） | `--font-h2` | 40 |
| 标题（H3/SectionTitle） | `--font-h3` | 36 |
| 卡片主标题 / 弹窗标题 | `--font-card` | 32（=subtitle） |
| 副标题 / 昵称 | `--font-subtitle` | 32 |
| 说明/正文大 | `--font-caption` | 30 |
| 正文 / 列表主文字 / 菜单 | `--font-body` | 28 |
| 辅助 / 元信息 | `--font-aux` | 22 |
| 小字 / 计数 / 角标 | `--font-tiny` | 20 |
| 极小（占位） | `--font-small` | 24 |
- 字重：标题 `700`，正文/菜单 `500~600`，强强调（品类头/排名 Top）`800` 仅限 find 已定样语义，不在目标 9 页引入新 `800`。
- 治理：目标页内裸 `20rpx`→`--font-tiny`；裸 `24rpx`→`--font-small`；裸 `26rpx`→`--font-body`；裸 `30rpx`→`--font-caption`。

#### 10.2.5 评价页标题（去 accent 条）
- `review-list.vue:14` 移除 `<SectionTitle :title="..."/>`，改为**纯文字标题**：`<text class="review-title">{{ dishId ? '全部评价' : '我的评价' }}</text>`，样式 `font-size: var(--font-h3); font-weight: 700; color: var(--text-primary); padding: var(--spacing-sm) var(--spacing-md) 0;`（与 `scroll-wrap` 同横向边距 `spacing-md`）。
- 不改动 `SectionTitle` 组件本身（首页/发现/详情等仍依赖其 accent 条，不在此轮去条）。

### 10.3 逐页修复清单（开发工程师直接落地，无需再决策）

> 所有改动均在 `<style scoped>` 内；模板改动仅在 review-list（标题替换）。Token 新增仅改 `App.vue` 两处。
> 约定：`scroll-wrap` 现状已多为 `padding: var(--spacing-md)`（OK），凡标注「改为 spacing-md」指把 `spacing-lg` 横向去掉。

1. **`frontend/src/App.vue`**（Token 真相源，两处同步）
   - 在 `page` 块（约 line 87 `--icon-lg` 后）与 `:root` 块（约 line 192 后）各新增：
     - `--icon-xs: 22rpx;`（行内极小图标）
   - 说明：仅新增 `--icon-xs`，其余沿用；不改动既有 `--icon-sm/--icon-lg`。

2. **`pages-detail/review-list.vue`**
   - 模板 line 14：`<SectionTitle .../>` → 纯文字标题（见 §10.2.5）。
   - 删除 `import SectionTitle`（line 39）。
   - 样式新增 `.review-title`（§10.2.5）；`.scroll-wrap` 已 `padding: 0 var(--spacing-md)`（OK）。

3. **`profile/my-submissions.vue`**
   - line 117 `.tabs`：`padding: var(--spacing-md) var(--spacing-lg) 0` → `padding: 0 var(--spacing-md)`（去掉左右 `spacing-lg`；原顶部无 padding，与 my-publish 对齐）。
   - line 130 `.off-tag`：`font-size: 20rpx` → `font-size: var(--font-tiny)`。

4. **`profile/my-publish.vue`**
   - line 133 `.tabs`：`padding: var(--spacing-md) var(--spacing-lg) 0` → `padding: 0 var(--spacing-md)`。
   - line 17/38 兜底 `IconSvg :size="56"` 维持（= `--icon-xl` 语义），无需改数值。

5. **`profile/publish-dish.vue`**
   - line 209 `.tag-chip`：`border-radius: 28rpx` → `border-radius: var(--radius-tag)`（胶囊统一 pill）。
   - line 22/29 `IconSvg :size="28"`（arrow，=`--icon-sm` OK）；line 50 `:size="24"`（close，维持）；其余 `var(--spacing-*)` 已合规。

6. **`profile/submit-stall.vue`**
   - 横向边距已合规（`scroll-wrap padding: var(--spacing-md)`）。
   - 仅复查：`.type-switch` 圆角 `var(--radius-card)` OK；图片移除 `border-radius: 50%` 属允许例外（删除徽标）OK。无需改。

7. **`my-moments/index.vue`**
   - line 131 `.segment-wrap`：`padding: var(--spacing-sm) var(--spacing-lg)` → `padding: var(--spacing-sm) var(--spacing-md)`（左右改 `spacing-md`）。
   - `.moment-list`/`.skeleton-list` 已 `padding: var(--spacing-md)`（OK）。

8. **`settings/index.vue`**
   - line 132 `.cell`：`padding: var(--spacing-md) var(--spacing-lg)` → `padding: var(--spacing-md)`（去左右 `spacing-lg`，与 SettingCell 内部 `spacing-md` 一致；外层 `scroll-wrap` 无 padding 故单元格自身需左右 `spacing-md` 留白，即改为 `var(--spacing-md)` 单向）。
   - line 9 `IconSvg :size="36"`（= `--icon-md` 语义）OK；若顺手可挂 `--icon-md` 注释，数值不变。
   - line 143 `.version-text`：`font-size: 24rpx` → `font-size: var(--font-small)`。

9. **`feedback/index.vue`**
   - 横向：`.block` 已 `padding: var(--spacing-md)`（OK）；`.submit-bar` 已 `padding: var(--spacing-md)`（OK）。
   - line 99 `.type-chip`：`border-radius: var(--radius-tag)` 已合规（OK）。
   - 无需改（本轮已达标，仅记录为「参考达标页」）。

10. **`notify/index.vue`**
    - line 169 `.action-row`：`padding: var(--spacing-sm) var(--spacing-lg)` → `padding: var(--spacing-sm) var(--spacing-md)`（左右改 `spacing-md`）。
    - line 32 `IconSvg :size="36"`（= `--icon-md` 语义）OK；`.notify-list` 已 `padding: var(--spacing-md)`（OK）。

### 10.4 验收门禁（QA / 设计评审）
- [ ] 全部目标页 `.tabs`/`.segment-wrap`/`.action-row`/`.cell` 横向不再出现 `var(--spacing-lg)`（grep `spacing-lg` 仅剩纵向 `margin-bottom`/`gap`/底部占位）。
- [ ] `review-list.vue` 不再 import/使用 `SectionTitle`，标题为纯文字且无 accent 条；横向边距 `spacing-md`。
- [ ] grep 目标 9 页 + review-list 内 `border-radius: 28rpx` / 裸 `28rpx` 圆角 → 0（已统一 radius-tag/card）。
- [ ] grep 目标页内裸 `font-size: 2[0-9]rpx`（20/24/26/30）除 `--icon` 语义外 → 0（全走 token）。
- [ ] `App.vue` 两处均含 `--icon-xs: 22rpx`。
- [ ] 真机预览：9 页 + review-list 内容与屏幕左右边距视觉一致（无内凹、无溢出、无全屏贴边）。

---

## 状态注记

**全部页面设计已定稿，待小程序开发工程师按本文件实现。**

- 覆盖范围：7 个详情页（dish / canteen / stall / moment / review / review-list / contact）+ 4 个 TabBar 页（home / find / community / profile）+ 社区编辑页（publish-moment / my-moments）+ 我的子页（publish-dish / submit-stall / my-publish / my-submissions / settings / feedback）+ 工具页（webview），共 17 个页面设计已全部落档。
- 本文件 §2.5 / §2.6 / §2.7 三页已按用户「UI 每一页都按推荐」的终决一次性落档（设计师默认推荐全采纳），无需再逐页确认。
- 待抽组件清单见 §六 B（共 15 组），实现前对应页内联；现存 20 个组件见 §六 A 可直接复用。
- 本注记仅作状态记录，不修改任何业务代码、不擅改 project_spec.md。
