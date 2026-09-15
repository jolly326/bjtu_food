# Client UI Polish

## Purpose

收敛小程序端全局视觉与交互一致性：设计令牌卫生、统一按压/动效原语、可访问性（触控目标/语义角色/对比度）、响应式与安全区适配、动效遵从 reduced-motion——在不新增功能、不改后端/接口、不违反 §4.9 红线的前提下提升观感与可用性。

## Requirements

### Requirement: 设计令牌卫生（无裸 hex、新增颜色走令牌）

所有组件样式 SHALL 使用语义令牌 `var(--xxx)`；任何无法避免的裸色值（原生 API 不接受 `var()`，如 `scroll-view` 背景、`swiper` indicator、`uni.showModal` confirmColor）SHALL 集中登记于 `uni.scss` 并以注释标注用途与色值。`client/src` 全仓 SHALL 通过令牌审计（`grep -rnP '#[0-9a-fA-F]{3,6}'` 排除已登记例外后应为 0 处），遗留裸 hex 清除或登记。新增/调整颜色 SHALL 仅改 `App.vue`（`:root`/`.theme-dark`）与 `theme/tokens.ts` 两处。

#### Scenario: 审计仅余已登记例外

- **WHEN** 运行令牌审计（排除 `uni.scss` 登记清单）
- **THEN** 组件 `.vue`/`.scss` 中无未登记裸 hex

#### Scenario: 新增颜色走令牌

- **WHEN** 需要新语义色
- **THEN** 仅在 `App.vue` 与 `theme/tokens.ts` 两处定义，组件引用 `var(--新令牌)`，不出现裸值

### Requirement: 统一按压原语覆盖全站可点元素

所有可点卡片/按钮/链接 SHALL 使用统一的按压反馈原语（整卡 `transform: scale(var(--press-scale))`、单一来源），不得保留分散的 inline 按压样板。按压 SHALL 仅缩放、不换背景色，以避免微信合成层下 `border-radius` 对自身背景裁剪失效导致的左上角色块。

#### Scenario: 无分散 inline 按压样板

- **WHEN** 审计 `client/src` 页面
- **THEN** 按压反馈经统一原语（全局 `.pressed` 或 `<Pressable>`）承载，无各自为政的换色型按压逻辑

#### Scenario: 按压不换背景色

- **WHEN** 卡片被按下
- **THEN** 根元素仅 `transform: scale` 反馈，无 `.pressed` 换背景色规则

### Requirement: 可访问性——触控目标与语义角色

所有可交互控件 SHALL 在至少一个维度满足最小触控目标 88rpx（≈44pt）；视觉尺寸过小者（图标按钮、关闭叉等）SHALL 通过命中区域扩展（如 `::after` 扩大点击区）补足，不改变视觉尺寸。所有可点元素 SHALL 暴露 `role` 与 `aria-label`；纯图标按钮 MUST 有非空的 `aria-label`。文本令牌对比度 SHALL 维持 `ui-design.md` §1.3 标准（浅色主文 ≥7:1、次文 ≥4.5:1）；新增文本色 SHALL 满足该标准。

#### Scenario: 最小触控目标达标

- **WHEN** 审查所有可点控件
- **THEN** 每个控件命中区至少一维 ≥88rpx（含扩展命中区）

#### Scenario: 图标按钮有可读标签

- **WHEN** 存在纯图标按钮（如筛选图标、关闭叉）
- **THEN** 其具备 `aria-label` 且该标签非空

### Requirement: 响应式与安全区适配

所有带固定底栏的可滚动页 SHALL 使用 `.scroll-wrap` 并在其底部留白 `padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom))`，避免内容被底栏遮挡（延续 §4.9）。布局 SHALL 以 `rpx` 做跨设备缩放；含截断文本的 flex 子项 SHALL 设 `min-width:0` 以防溢出/挤压同级元素。顶部 header、底部 tab bar、底部 Sheet 的贴合边缘区域 SHALL 应用 `env(safe-area-inset-top/bottom/left/right)` 适配刘海/Home 指示条设备。

#### Scenario: 内容不被底栏遮挡

- **WHEN** 打开带固定底栏的页（如发布、详情）
- **THEN** 列表末项完整可见，未被底栏裁切

#### Scenario: 长文本在 flex 内截断不破版

- **WHEN** 食堂名/标题较长
- **THEN** 文本 `overflow:hidden` 省略，不撑破胶囊/卡片、不挤出相邻控件

### Requirement: 动效遵从 reduced-motion

所有入场/列表 stagger 动画（`enter-up`）与底部 Sheet 过渡 SHALL 遵从减动效偏好（系统 `prefers-reduced-motion` 或统一 `useReducedMotion()` 组合式）：开启时降级为瞬时显示或仅 `opacity` 淡入，不进行位移动画。底部 Sheet SHALL 支持可中断的下拉关闭手势（阈值 ~120px），并在 reduced-motion 下保留手势但去除弹性过冲。

#### Scenario: 减动效下降级

- **WHEN** 减动效偏好开启
- **THEN** `enter-up` 列表项无位移、仅（可选）淡入或瞬时出现

#### Scenario: Sheet 可中断下拉关闭

- **WHEN** 用户下拉 Sheet 超过阈值
- **THEN** Sheet 关闭；未过阈值回弹；reduced-motion 下去除弹性过冲但仍可关闭

### Requirement: 组件视觉一致性（圆角/间距/层级语言）

卡片/胶囊/按钮/弹层 SHALL 使用既有圆角令牌（`--radius-card` 16px、`--radius-pill`、`--radius-modal` 等）与 4pt 间距刻度（`--spacing-2xs`…`--spacing-xl`），禁止任意 rpx 字面量（间距须为 4 的倍数）。同色于页面的卡片 SHALL 以发丝边（`--border-card`）而非投影表达层级与边界（延续 `ui-surface-consistency`）；需强调的表面（如认证态用户卡、主色 header）例外并集中登记。正确抬升于页面的卡片（如 CardSection、动态卡）可保留 `--shadow-card` 投影。

#### Scenario: 无任意圆角/间距字面量

- **WHEN** 审查组件样式
- **THEN** 圆角/间距均为令牌引用，无裸 `16rpx`/`12rpx` 等字面量（原生 API 例外已登记）

#### Scenario: 同色卡以发丝边区分

- **WHEN** 卡片背景与页面同色
- **THEN** 其边界由 `1rpx solid var(--border-card)` 表达，无 `--shadow-card` 投影
