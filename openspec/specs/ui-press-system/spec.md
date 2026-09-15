# ui-press-system Specification

## Purpose
提供统一的按压反馈基础件（`<Pressable>` + `usePress`），消除散落在卡片/按钮中的触摸样板，使全端按压体感与可访问性语义一致。

## Requirements

### Requirement: 统一按压态承载
`<Pressable>` 组件 SHALL 在用户按下时应用 `transform: scale(var(--press-scale))` 的按压缩放，并在 ≤100ms 内完成视觉响应；抬起或指针离开后恢复原始态。

#### Scenario: 按下即时缩放
- **WHEN** 用户在可点卡片上按下手指（或鼠标按下）
- **THEN** 该元素在 100ms 内进入按下态（缩放至 `--press-scale`）

#### Scenario: 抬起恢复
- **WHEN** 用户抬起手指或指针移出元素
- **THEN** 元素恢复原始尺寸，无残留缩放

### Requirement: 覆盖触摸/鼠标/取消三种中断态

按压机制 SHALL 同时处理 touch 与 mouse 输入及 `touchcancel`/`mouseleave` 等中断态，业务组件不得再各自声明 `@touchstart`/`@touchend`/`@touchcancel`/`@mousedown`/`@mouseup`/`@mouseleave` 与 `pressed` ref。

#### Scenario: 触摸被系统取消
- **WHEN** 滑动穿透或来电打断导致 `touchcancel`
- **THEN** 元素恢复非按下态，不卡在缩放中

#### Scenario: 业务组件零样板
- **WHEN** `DishCard` / `AppButton` / `ReviewItem` / 评价卡接入 `<Pressable>`
- **THEN** 上述组件各自移除原有 `pressed` ref 与 6 个 `@touch*` / `@mouse*` handler 样板（约 200 行）

### Requirement: 可访问性语义透传
`<Pressable>` SHALL 透传 `role` 与 `aria-label`，使纯图标可点元素对外暴露语义。

#### Scenario: 图标按钮暴露语义
- **WHEN** 使用 `<Pressable aria-label="关闭">` 包裹关闭图标
- **THEN** 该元素在辅助模式下可识别为「关闭」操作
