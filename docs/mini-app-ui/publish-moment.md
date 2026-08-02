# 发布动态 (publish-moment)
- 路由: /pages/publish-moment/index
- 分包: 主包
- 源文件: src/pages/publish-moment/index.vue
- 最后依据 skills 校对: 2026-08-02

## 1. 页面定位
发布/编辑美食动态：正文 + 关联对象（选填）+ 图片（≤9），底部提交栏；编辑态由 `?id` 预填并重提审核。

## 2. 布局结构
- 顶部：`Header :title="编辑动态/发布动态" showBack`（含状态栏占位）。
- 滚动区 `scroll-view`。
- 三个 `.block` 白卡（圆角+阴影）：
  1. 正文 `textarea`（auto-height，maxlength 500）+ 字数计数。
  2. `SectionTitle 关联对象`（extra「选填」）+ `related-picker`（点击开 Sheet）。
  3. `SectionTitle 图片`（extra「最多 9 张」）+ `ImageUploader :max="9"`。
- 底部提交栏 `.submit-bar`（fixed）：`AppButton`（发布/保存并重新提交，loading 态）。
- `RelatedPickerSheet`（关联对象选择，spring 0.8/0.3 + ic-close + reduced-motion）。

## 3. 核心组件与用法
- `SectionTitle`：关联对象 / 图片 区块标题（卡片内用默认 margin，含 accent 条）。
- `ImageUploader`：图片选择与预览（v-model，移除/加号）。
- `RelatedPickerSheet`：关联菜品/档口选择（返回真实 id，toggle 取消）。
- `AppButton`：底部提交主按钮（单主 CTA，disabled/loading 态清晰）。
- `IconSvg`：`arrow`(关联 picker 右箭头，注册 key)。

## 4. 设计 Token 使用
- 背景/文字：`--bg-page`、`--bg-card`、`--bg-soft`、`--text-primary/secondary/tertiary`。
- 圆角/阴影：`--radius-card`、`--radius-tag`、`--shadow-card`、`--shadow-bar-soft`(submit-bar)、`--radius-btn`。
- 间距：`--spacing-md/sm/xs/xl`（4/8pt 节奏）。
- 字号：`--font-body`、`--font-aux`。
- 动效：`--press-scale`(`.related-picker:active`)。
- 布局：`--action-bar-height` + `env(safe-area-inset-bottom)`（scroll-wrap 底部避让提交栏）；submit-bar 同安全区。

## 5. 交互与动效
- 正文超 500 字禁提交（字数计数即时反馈）；关联对象 Sheet 选中高亮、二次点击取消、确认关闭。
- 提交校验登录（`requireAuth`）→ 调 `publishMoment`/`updateMoment` → toast → navigateBack。
- 底部栏 `AppButton` 自带按压 scale；关联 picker 按下 scale 0.97。
- RelatedPickerSheet 套通用弹层（spring 0.8/0.3 + 下拉关闭 + reduced-motion 交叉淡入；exit 短于 enter）。

## 6. 一致性红线自检（project_spec §4.9）
- ①图标 IconSvg：✅ arrow 走 IconSvg；无 emoji。
- ②金额 api 层：✅ 无金额。
- ③WaterfallList 禁 slot：➖ 无列表，不适用。
- ④三态齐备：⚠️ 表单页，加载态为 AppButton loading；无空态需求（编辑态 onLoad 拉取失败有 toast）。可接受。
- ⑤Sheet 规范：✅ RelatedPickerSheet 套通用弹层规范。
- ⑥按压 0.97：✅ `.related-picker:active scale(var(--press-scale))`；AppButton 内部同。
- ⑦颜色 token：✅ 无裸 hex。
- ⑧SectionTitle：✅ 关联对象/图片 均 SectionTitle（卡片内默认 margin）。
- ⑨底部避让：✅ 提交栏与 scroll-wrap 用 `--action-bar-height` + 安全区。

## 7. 待定 / 可编辑的设计方案
> 以下区块由产品或设计在此直接编辑，用于和开发者同步 UI 变更意向。
- 待讨论项：暗色模式对比度待实测(§8⑥)；字数计数(如 120/500)建议 tabular-nums、textarea 字号边界 ≥32rpx 复核(§8⑮⑯)；textarea 缺独立可见 label，建议保留区块标题作 label 语义(§8⑲)
- 计划调整：采纳你的建议

## 8. Skill 合规自检（UI 设计 skills）
| # | 检查项 | 结论 | 说明 |
|---|---|---|---|
| 1 | 触控目标 ≥44×44pt，间距≥8px | 合规 | 关联 picker/提交按钮/图片单元格 ≥44pt；区块间距 ≥8pt |
| 2 | 按压反馈 100ms 内 scale(0.97) | 合规 | `.related-picker:active scale(var(--press-scale))`；AppButton 内部 0.97 |
| 3 | 固定栏/导航预留安全区 | 合规 | submit-bar `--action-bar-height` + `env(safe-area-inset-bottom)`；Header 含状态栏 |
| 4 | 禁用 emoji，统一 SVG 矢量图标 | 合规 | arrow 注册 key；ImageUploader 内 plus/close 注册 key；无 emoji |
| 5 | 仅用语义 token，禁裸 hex | 合规 | 全 `var(--…)` |
| 6 | 正文对比度 ≥4.5:1，亮暗双测 | 部分 | token 满足；未单独暗色实测 |
| 7 | 不靠颜色 alone 传意 | 合规 | 字数计数达限有文字提示；无纯色状态 |
| 8 | prefers-reduced-motion 处理 | 合规 | RelatedPickerSheet 降级；无位移动画 |
| 9 | hover 门控 @media(hover:hover) | 合规 | 触控 `:active`/touch |
| 10 | 微交互<300ms，exit 短于 enter | 合规 | press 120ms；Sheet exit 短 |
| 11 | 自定义缓动，禁 ease-in | 合规 | Sheet 用 `--ease-drawer`/`--ease-out` |
| 12 | 进场禁 scale(0)，popover 从触发点 | 合规 | Sheet 自底部；无 scale(0) |
| 13 | 仅 transform/opacity，禁 transition:all | 合规 | 无 `transition:all` |
| 14 | 可中断动效 | 合规 | Sheet 手势可中断 |
| 15 | 数字 tabular-nums | 部分 | 字数计数（如 120/500）建议 `tabular-nums` 防位移 |
| 16 | 正文≥16px(32rpx)，行高1.5–1.75，4/8pt 节奏 | 合规 | textarea `--font-body`(28rpx)≥32rpx 边界建议复核；4/8pt 间距 |
| 17 | 每屏一个主 CTA，破坏性弱化隔离 | 合规 | 唯一主 CTA「发布/保存」；无破坏性操作 |
| 18 | loading/empty/error 三态 | 部分 | 提交 loading 即加载态；表单页无空态（合理）；字数/登录校验即错误态 |
| 19 | 表单无障碍（label/必填/校验/键盘） | 部分 | 区块标题走 SectionTitle（可见）；textarea 无独立可见 label，靠区块标题+placeholder；建议保留可见标题作为 label 语义 |
| 20 | 导航一致：底部≤5 项 icon+label | ➖ | 表单页无底部导航；返回由 Header 处理 |
| 21 | 一致性打磨 | 合规 | 按压统一 0.97；Sheet 规范同源 |

## 9. 交付前验证（Pre-delivery）
- [ ] 375px：三个区块不溢出；ImageUploader 9 格布局整齐。
- [ ] reduced-motion：RelatedPickerSheet 交叉淡入。
- [ ] 动态字号：textarea/区块标题不截断。
- [ ] 暗色对比：textarea 文字、计数文字对比达标。
- [ ] 44pt：关联 picker/提交按钮/图片格 ≥44pt。
- [ ] 安全区：submit-bar 不被 home indicator 遮挡。
