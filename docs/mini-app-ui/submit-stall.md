# 提交档口·食堂 (submit-stall)
- 路由: /pages/profile/submit-stall
- 分包: 主包（profile 子目录）
- 源文件: src/pages/profile/submit-stall.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
学生提交档口或补充食堂信息：类型切换（提交档口/补充食堂）+ 基础信息（名称/所属食堂/位置/营业时间）+ 图片（≤9）+ 描述，提交 status=pending 待审核。支持 `?type=canteen` 预选。

## 2. 布局结构
- 顶部：`Header title="提交档口·食堂" showBack`（含状态栏占位）。
- 滚动区 `scroll-view`。
- 类型切换 `.type-switch`：两个 `type-btn`（提交档口/补充食堂，激活主色填充，图标+文字）。
- 多个 `CardSection`：
  1. 基础信息：名称(input, req, 可见 label)、所属食堂(picker，仅 stall)、位置(input)、营业时间(input，仅 stall)。
  2. 图片（≤9，`type` 决定文案）：`ImageUploader :max="9"`（180rpx 单元格）。
  3. 描述：`textarea`（maxlength 500）。
- 底部 `.submit-wrap`：`AppButton text="提交审核"`（loading，单主 CTA）。

## 3. 核心组件与用法
- `CardSection`：分区卡片（title accent 标题）。
- `ImageUploader`：图片上传（`:deep` 180rpx，移除/加号图标）。
- `AppButton`：提交主按钮。
- `picker`：原生选食堂。
- `type-btn`：类型切换（激活态主色填充，按压 scale）。
- `IconSvg`：未直接使用（picker 箭头未用 SVG；ImageUploader 内 plus/close 注册 key）。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--text-primary/secondary/tertiary`。
- 强调/状态：`--color-primary`(激活 type-btn/选中)、`--color-error`(必填 `*`)。
- 边框：`--border-color`(字段分割/输入框)。
- 圆角：`--radius-card`、`--radius-btn`(type-btn)、`--radius-icon`(图/输入框)。
- 间距：`--spacing-md/sm/xs`（4/8pt 节奏）。
- 字号：`--font-body`。
- 动效：`--press-scale`(`.type-btn:active`)、`--ease-out`。
- 布局：scroll-wrap `padding: var(--spacing-md) 0`。

## 5. 交互与动效
- 类型切换显隐字段；选食堂 picker 级联。
- 提交校验（名称/档口需食堂）→ `post('/my/stalls', payload)` → toast「提交成功，待审核」→ navigateBack。
- type-btn 激活态主色填充，按下 scale 0.97（即时 `--press-transition`）。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：➖ 本页无图标需求（picker 箭头未用 SVG）；ImageUploader 内 plus/close 注册 key；无 emoji。类型切换为文字按钮。
- ②金额 api 层：✅ 无金额。
- ③WaterfallList 禁 slot：➖ 无列表，不适用。
- ④三态齐备：⚠️ 表单页，无空态需求；提交 loading。可接受。
- ⑤Sheet 规范：➖ 无底部 Sheet（原生 picker），不适用。
- ⑥按压 0.97：✅ `.type-btn:active scale(var(--press-scale))`；AppButton 内部同。
- ⑦颜色 token：✅ 无裸 hex（激活 `--color-primary`；必填 `--color-error`）。
- ⑧SectionTitle：✅ 分区走 `CardSection title=`（accent 标题）。
- ⑨底部避让：➖ 提交按钮在滚动内（submit-wrap），无 fixed 底栏；scroll-wrap 无 tabbar 需求，合规。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：类型切换是否补 IconSvg 图标（提交档口/补充食堂）
- 计划调整：采纳你的建议

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | type-btn/input/picker/提交按钮 ≥44pt；间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | `.type-btn:active scale(var(--press-scale))`；AppButton 内部 0.97 |
| 3 | 固定栏/导航预留安全区 | 合规 | 无固定底栏（按钮在滚动内）；Header 含状态栏 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | 无 emoji；ImageUploader 内 plus/close 注册 key；类型切换为文字（非 emoji） |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；必填 `*`/激活态对比未单独暗色实测 |
| 7 | 不靠颜色 alone 传意 | 合规 | 必填 `*` 标记；类型切换有文字+填充态 |
| 8 | prefers-reduced-motion 处理 | 合规 | 无位移动画 |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | press 120ms；无高频动画 |
| 11 | 自定义缓动，禁 ease-in | 合规 | 无 ease-in |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | 无 scale(0)/弹层 |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 无 `transition:all` |
| 14 | 可中断动效 | 合规 | 无 keyframes 重播 |
| 15 | 数字 tabular-nums | 部分 | 营业时间等数字建议 `tabular-nums` |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 部分 | input/textarea 建议 ≥32rpx；当前 `--font-body`(28rpx) 略低，建议复核 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 唯一主 CTA「提交审核」；无破坏性 |
| 18 | loading/empty/error 三态 | 部分 | 提交 loading 即加载态；表单无空态（合理）；必填校验即错误态 |
| 19 | 表单无障碍（label/必填/校验/键盘） | 合规 | 字段可见 label；必填 `*`；picker/input 触发对应键盘；blur/提交校验 |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 表单页无底部导航 |
| 21 | 一致性打磨 | 合规 | 按压统一 0.97；type-btn 激活态同源 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：类型切换两按钮不挤；字段不溢出。
- [ ] reduced-motion：无位移动画。
- [ ] 动态字号：标签/输入不截断。
- [ ] 暗色对比：必填 `*`/激活 type-btn 文字对比达标。
- [ ] 44pt：type-btn/input/picker/提交 ≥44pt。
- [ ] 安全区：Header 不被刘海遮挡。
