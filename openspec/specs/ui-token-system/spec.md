# UI Token System Specification

## Purpose

建立小程序端设计令牌的统一取值规则：统一间距/圆角/字号/阴影/颜色的单位与取值，消除多套别名并存与组件层裸值，并约束令牌双份声明的同步，保证视觉一致性可持续维护。

## Requirements

### Requirement: 间距使用统一栅格令牌
所有组件的 `padding`、`margin`、`gap` SHALL 引用 `--spacing-*` 令牌（4rpx 基准栅格：2xs/xs/sm/md/lg/xl），不得使用裸数值。

#### Scenario: 组件无裸间距值
- **WHEN** 审查任意组件的样式声明
- **THEN** 其 `padding`/`margin`/`gap` 均引用 `--spacing-*` 令牌，无裸 rpx/px 数值

### Requirement: 圆角单位与取值统一
所有 `border-radius` SHALL 引用 `--radius-*` 令牌，且令牌单位统一为 `rpx`（与间距同单位）。

#### Scenario: 组件无裸圆角值
- **WHEN** 审查任意组件的样式声明
- **THEN** 其 `border-radius` 均引用 `--radius-tag`/`--radius-card`/`--radius-btn`/`--radius-icon`/`--radius-pill`/`--radius-modal`/`--radius-sheet` 之一，无裸值

#### Scenario: 圆角令牌单位为 rpx
- **WHEN** 审查令牌声明
- **THEN** 所有 `--radius-*` 令牌取值以 `rpx` 为单位，与 `--spacing-*` 单位一致

### Requirement: 字号别名收敛
字号令牌 SHALL 保持单一语义单一名称，同值不得存在多个别名。

#### Scenario: 无重复字号别名
- **WHEN** 审查 `--font-*` 令牌集合
- **THEN** 不存在两个及以上令牌取相同数值且语义重复的情况

#### Scenario: 组件无裸字号
- **WHEN** 审查任意组件的样式声明
- **THEN** 其 `font-size` 均引用 `--font-*` 令牌，无裸值

### Requirement: 颜色与阴影走令牌
组件的颜色与阴影 SHALL 引用设计令牌，禁止裸 hex 与裸 rgba。

#### Scenario: 无裸颜色值
- **WHEN** 审查令牌真源文件以外的组件样式
- **THEN** 其中不出现裸 hex 颜色值

#### Scenario: 原生属性例外受控
- **WHEN** 因原生属性不接受 `var()` 而必须使用真实色值（如 `<swiper>` indicator 色、`uni.showModal` confirmColor、`scroll-view` 背景兜底）
- **THEN** 该例外在样式真源文件注释中显式登记并说明原因，且不得扩散到其他位置

### Requirement: 令牌双份声明同步
`App.vue` 中 `page` 块（小程序）与 `:root` 块（H5 兜底）的令牌 SHALL 保持一一对应、取值一致。

#### Scenario: 新增或修改令牌
- **WHEN** 新增或修改任一设计令牌
- **THEN** `page` 块与 `:root` 块同步更新，两处令牌集合与取值一致，不存在仅单侧声明的令牌

### Requirement: 按压动效统一
可点元素的按压反馈 SHALL 统一使用 `transform: scale(var(--press-scale))`，不得出现裸 `scale()` 数值；非按压的缩放须使用独立令牌并在样式真源文件登记。

#### Scenario: 无裸缩放值
- **WHEN** 审查任意样式声明
- **THEN** 按压相关缩放均引用 `--press-scale`，无裸 `scale(...)` 数值

### Requirement: 交互事件统一
可点元素 SHALL 统一使用 `@tap` 事件，不得使用 `@click`。

#### Scenario: 无 click 事件
- **WHEN** 审查任意页面与组件模板
- **THEN** 可点元素均绑定 `@tap`，不出现 `@click`
