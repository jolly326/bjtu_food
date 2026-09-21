# client-ui-interaction-a11y Specification

## Purpose
补齐小程序端统一的交互状态语言（加载/禁用/错误/悬停/点击）与系统级可访问性（键盘导航、焦点管理、ARIA、对比度），并清理冗余、约束抽象粒度，使客户端在移动/平板/桌面多端一致、可用、可维护。

## Requirements

### Requirement: 统一交互状态样式（loading / disabled / error / hover）

所有组件 SHALL 复用统一的交互状态样式语言，新增以下语义令牌（定义于 `theme/tokens.ts` + `App.vue`）：`--state-disabled`（禁用态底色/文字降饱和）、`--state-error`（错误提示主色，就近复用 `--color-error`）。SHALL 提供：
- **加载态**：列表/详情/信息流 SHALL NOT 呈现加载占位（无骨架屏、无 loading 文本指示、无 spinner），数据未返回时内容区保持空白静默；`aria-busy` 不再作为列表/详情的硬性要求。按钮等单控件的提交中态 SHALL 仍可用 disabled 表达。
- **禁用态**：`disabled` 元素 SHALL 以降透明度/降饱和 + 禁点（`:disabled` 或等价 `pointer-events/disabled` 属性），视觉与可点态明显区分。
- **错误提示**：表单/输入错误 SHALL 以 inline 文本（红色，就近复用 `--color-error`）+ `aria-invalid` 暴露；全局错误以统一 Toast 兜底（不新增独立错误组件）。**数据型列表首屏 / 下拉刷新请求失败 SHALL 呈现极简「加载失败 · 点击重试」块（MP-012）**——该块为可恢复路径的轻量行内块（非骨架、非独立错误组件），使「失败」不与「无数据」混淆；**分页（触底加载更多）失败 SHALL 静默（仅 `console` 记录）**，不打断列表。明确要求静默的场景为 **find 发现态、游客通知**；**菜品详情评价区不在静默例外内——其首屏 / 刷新失败 SHALL 呈现可重试块**（与 `content-flow-visual` 的详情页三态规范一致）。
- **悬停态（桌面/平板）**：可点元素在支持 hover 的设备 SHALL 有 hover 反馈（背景微变或边框高亮），移动端不呈现。
- **点击态**：沿用已统一的 `<Pressable>` 缩放（`transform: scale`），不新增换色型点击态（规避合成层圆角裁切失效色块）。

#### Scenario: 加载态不阻塞感知
- **WHEN** 列表/详情进入加载
- **THEN** 内容区保持空白，不出现骨架屏或 loading 指示，不标记 `aria-busy`

#### Scenario: 禁用态可辨认
- **WHEN** 控件处于 disabled
- **THEN** 视觉降饱和/降透明且不可触发，与可点态明显区分

#### Scenario: 错误以 ARIA 暴露
- **WHEN** 输入校验失败
- **THEN** 错误文本就近以 `--color-error` 呈现，输入元素标记 `aria-invalid="true"`

#### Scenario: 列表首屏失败给出可重试路径
- **WHEN** 数据型列表首屏或下拉刷新请求失败
- **THEN** 呈现极简「加载失败 · 点击重试」块（`role="button"` 且有 `aria-label`），点击后重拉；不呈现空态文案

#### Scenario: 列表分页失败保持静默
- **WHEN** 数据型列表触底加载下一页失败
- **THEN** 列表保持原样静默，不呈现错误块或 Toast，用户可再次触底重试

#### Scenario: 详情评价区首屏失败呈现可重试块
- **WHEN** 菜品详情页评价列表首屏或刷新请求失败
- **THEN** 评价区呈现「加载失败 · 点击重试」块（失败 ≠ 零评价），点击后按同一路径重拉

#### Scenario: 点击态仅缩放不换色
- **WHEN** 元素被点击
- **THEN** 仅有 `transform: scale` 反馈，无背景色切换（与既有 `<Pressable>` 一致）

### Requirement: 键盘导航与焦点管理

所有可交互元素 SHALL 可聚焦并以键盘（Enter/Space）触发等效动作；可点元素 SHALL 暴露清晰的焦点指示。弹层（Sheet/Modal/Dialog）打开时焦点 SHALL 落入首可交互控件、关闭时归还至触发源；路由/视图切换后焦点 SHALL 落到新视图主区域或首控件，避免焦点丢失在卸载节点。

#### Scenario: 键盘可操作

- **WHEN** 用户用 Tab/方向键聚焦按钮并按键
- **THEN** 触发与点击等效的动作（Enter/Space）

#### Scenario: 弹层焦点落入与归还

- **WHEN** 打开 Sheet/Modal
- **THEN** 焦点进入首控件；关闭后焦点回到触发该弹层的元素

#### Scenario: 视图切换焦点不丢失

- **WHEN** 页面/视图切换
- **THEN** 焦点落于新视图主区域或首可交互控件，不滞留于已卸载节点

### Requirement: ARIA 属性全覆盖

所有可点元素 SHALL 具备 `role` 与 `aria-label`；纯图标按钮 MUST 有非空 `aria-label`。状态变更 SHALL 以 ARIA 暴露：加载 `aria-busy`、输入错误 `aria-invalid`、动态提示（Toast/inline 错误/结果区）SHALL 置于 `aria-live="polite"` 区域以便读屏播报。装饰性元素 SHALL 标记 `aria-hidden="true"` 或 `role="presentation"` 以免干扰读屏。

#### Scenario: 图标按钮有可读标签

- **WHEN** 存在纯图标按钮（筛选/关闭/点赞）
- **THEN** 其具备非空 `aria-label`

#### Scenario: 动态提示可被读屏感知

- **WHEN** 提交结果/错误以 Toast 或 inline 提示出现
- **THEN** 该提示位于 `aria-live="polite"` 区域，读屏可播报

### Requirement: 多端对比度与一致体验

文本与控件对比度 SHALL 在浅色与深色主题、移动/平板/桌面（H5 宽屏）三端均满足 `ui-design.md` §1.3 标准（主文 ≥7:1、次文 ≥4.5:1）；新增/调整文本色 SHALL 达标。宽屏（桌面/平板）下内容 SHALL 以 `max-width` 容器居中、保留 `rpx` 跨端缩放，触控目标仍 ≥88rpx，交互态跨端一致。

#### Scenario: 对比度达标

- **WHEN** 审核任意文本色（含新增）
- **THEN** 浅色/深色下主文 ≥7:1、次文 ≥4.5:1

#### Scenario: 宽屏布局不破版

- **WHEN** 在桌面/平板 H5 宽屏打开
- **THEN** 内容以 `max-width` 容器居中，触控目标 ≥88rpx，交互态与移动端一致

### Requirement: 冗余清理与抽象粒度约束

SHALL 删除未使用的 CSS 变量、死分支与近似重复的样式/逻辑。抽象须遵循**频率阈值**：仅当某片段在 ≥3 处被复用时，才抽取为独立组件/混入/工具函数；使用频率低（<3 处）的片段 SHALL 保持内联或就近维护，不因「统一」而过度抽象。抽取的新组件 SHALL 在 `components/` 下登记并复用于既有调用点，不得新增仅 1–2 处使用的包装层。

#### Scenario: 死代码被清除

- **WHEN** 审计 `client/src`
- **THEN** 无未引用的 CSS 变量、无不可达分支、无注释掉的重复样式块

#### Scenario: 仅高频片段被抽取

- **WHEN** 评估是否抽取某片段为组件
- **THEN** 仅当复用点 ≥3 才抽取；低频片段保持内联/就近，不新增薄包装组件
