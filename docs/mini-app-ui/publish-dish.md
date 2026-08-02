# 发布菜品 (publish-dish)
- 路由: /pages/profile/publish-dish
- 分包: 主包（profile 子目录）
- 源文件: src/pages/profile/publish-dish.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
学生发布/编辑菜品：基本信息（名称/价格·元）、所属食堂+档口（picker）、口味标签、图片（≤9）、描述，提交后 status=pending 待审核。编辑态 `?id` 预填。

## 2. 布局结构
- 顶部：`Header :title="编辑菜品/发布菜品" showBack`（含状态栏占位）。
- 滚动区 `scroll-view`（`padding: var(--spacing-md) 0`）。
- 多个 `CardSection`：
  1. 基本信息：名称 input（req，可见 label）、价格 input（type=digit，元，可见 label）。
  2. 所属食堂/档口：两个 picker（食堂/档口，档口依赖食堂）。
  3. 口味标签：`tag-grid` 多选 chip（必吃推荐/招牌菜/辣味/素食/面食/清真/西餐/甜品）。
  4. 菜品图片（≤9）：`ImageUploader :max="9"`（复用 180rpx 单元格）。
  5. 描述：`textarea`（maxlength 500）+ 字数计数。
- 底部 `.submit-wrap`：`AppButton`（发布菜品/保存并重新提交，loading，单主 CTA）。

## 3. 核心组件与用法
- `CardSection`：各分区卡片（title 渲染 accent 标题）。
- `ImageUploader`：图片上传（`:deep` 覆写 180rpx 单元格，移除/加号图标）。
- `AppButton`：提交主按钮。
- `picker`：原生 picker 选食堂/档口（系统控件）。
- `IconSvg`：`arrow`(picker 右箭头，注册 key)；ImageUploader 内 plus/close 注册 key。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--bg-soft`(未用)、`--text-primary/secondary/tertiary`。
- 强调/状态：`--color-primary`(选中标签/激活)、`--color-primary-bg`(选中标签底)、`--color-error`(必填 `*`)。
- 边框：`--border-color`(字段分割/输入框边)。
- 圆角：`--radius-card`、`--radius-icon`(图/输入框)、`--radius-tag`(标签)、`--radius-btn`。
- 间距：`--spacing-md/sm/xs`（4/8pt 节奏）。
- 字号：`--font-body`、`--font-aux`、`--font-tiny`(计数)。
- 动效：未用显式 press（原生 picker / input 无 scale；提交 AppButton 内部 scale）。
- 布局：scroll-wrap `padding: var(--spacing-md) 0`（无固定底栏，submit-wrap 在滚动内）。

## 5. 交互与动效
- 价格以「元」输入（页面仅展示/提交，分↔元由 api 层 `yuanToFen` 处理，页面无 `/100` 裸算）。
- 选食堂后级联加载档口；标签多选 toggle（底色+文字高亮反馈）。
- 提交校验（名称/价格>0/食堂档口）→ `publishDish`/`updateMyDish` → toast → navigateBack。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ arrow 走 IconSvg；ImageUploader 内 plus/close 注册 key；无 emoji。
- ②金额 api 层：✅ 价格以「元」填写，提交由 api 层 `yuanToFen` 转分；页面无 `/100` 裸算。合规。
- ③WaterfallList 禁 slot：➖ 无列表，不适用。
- ④三态齐备：⚠️ 表单页，无空态需求；提交 loading。可接受。
- ⑤Sheet 规范：➖ 无底部 Sheet（用原生 picker + AppButton），不适用。
- ⑥按压 0.97：⚠️ 本页未对 tag-chip 等加 `:active scale`；提交 AppButton 内部用 `--press-scale`。tag 选中态视觉反馈靠底色变化。可接受但非强制按压（建议补 tag press 反馈）。
- ⑦颜色 token：✅ 无裸 hex（选中 `--color-primary`/`--color-primary-bg`；必填 `--color-error`）。
- ⑧SectionTitle：✅ 分区标题走 `CardSection title=`（accent 条标题）。
- ⑨底部避让：➖ 提交按钮在滚动区内（submit-wrap），无独立 fixed 底栏，scroll-wrap 无 tabbar 避让需求，合规。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：~~标签 chip 是否补按压 scale 反馈~~（已整改：tag-chip 加按压反馈，见 §8②）
- 计划调整：

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | 标签 chip/input/picker/提交按钮 ≥44pt；间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | 已整改：tag-chip 加按压反馈（`.tag-chip:active scale(var(--press-scale))`）；提交 AppButton 内部 0.97 |
| 3 | 固定栏/导航预留安全区 | 合规 | 无固定底栏；Header 含状态栏 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | arrow/plus/close 均注册 key；无 emoji |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；必填 `*` `--color-error` 与 tertiary 对比未单独暗色实测 |
| 7 | 不靠颜色 alone 传意 | 合规 | 必填有 `*` 标记文字；标签选中有底色+文字 |
| 8 | prefers-reduced-motion 处理 | 合规 | 无位移动画 |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | 无高频动画；AppButton press 120ms |
| 11 | 自定义缓动，禁 ease-in | 合规 | 无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | 无 scale(0)/弹层 |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 无 `transition:all` |
| 14 | 可中断动效 | 合规 | 无 keyframes 重播 |
| 15 | 数字 tabular-nums | 部分 | 价格输入/字数计数建议 `tabular-nums` 防位移 |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 部分 | input/textarea 建议 ≥32rpx；当前 `--font-body`(28rpx) 略低，建议复核 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 唯一主 CTA「发布/保存」；无破坏性 |
| 18 | loading/empty/error 三态 | 部分 | 提交 loading 即加载态；表单无空态（合理）；必填校验即错误态 |
| 19 | 表单无障碍（label/必填/校验/键盘） | 合规 | 字段可见 label（名称/价格/食堂/档口/描述）；必填 `*` 标记；价格 `type=digit` 触发数字键盘；blur/提交校验 |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 表单页无底部导航 |
| 21 | 一致性打磨 | 部分 | AppButton 按压统一；tag-chip 缺按压反馈（见 §8②）；disabled=opacity 未涉及 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：5 个分区不溢出；标签 chip 8 列/换行整齐。
- [ ] reduced-motion：无位移动画。
- [ ] 动态字号：标签/输入不截断。
- [ ] 暗色对比：必填 `*`/标签选中态文字对比达标。
- [ ] 44pt：chip/input/picker/提交 ≥44pt。
- [ ] 安全区：Header 不被刘海遮挡。
