# ui-theme-shadows Specification

## Purpose
用主题感知的阴影 token 替换硬编码 `rgba(0,0,0,...)` 阴影，使卡片/筛选条/浮层在 light 与 dark 模式下都拥有协调且对比恰当的投影。

## Requirements

### Requirement: 阴影语义 token（卡片中性投影）

`tokens.ts`（及 `uni.scss` 注册）SHALL 提供阴影 token：`--shadow-card`（卡片静止投影）、`--shadow-bar-primary`（筛选条/顶栏投影）、`--shadow-float`/`--shadow-press`（浮层/按下微投影）等；light 与 dark 主题分别给出取值。

`--shadow-card` SHALL 使用中性淡投影：light 主题为 `0 2px 12px rgba(0, 0, 0, 0.04)`，不随主色色相变化。顶栏/头部等含品牌色通道的阴影（含 `--shadow-bar-primary`）SHALL 与新主色 `#C45549`（RGB `196, 85, 73`）保持一致；主色变更时 SHALL 同步更新，避免品牌通道与主色脱节。

#### Scenario: 双主题取值
- **WHEN** 主题在 light / dark 间切换
- **THEN** 同一 `--shadow-card` token 在暗色下按主题语义降低不透明度/调整色相以适配深底

#### Scenario: 卡片投影为中性淡色
- **WHEN** 审查 `--shadow-card` 在 light 主题下的取值
- **THEN** 其颜色通道为中性 `rgba(0, 0, 0, 0.04)`，而非品牌色 `rgba(196, 85, 73, 0.05)` 或其它旧值

#### Scenario: 顶栏品牌阴影随主色同步
- **WHEN** `--color-primary` 取值变更
- **THEN** `--shadow-bar-primary` 等含品牌色通道的顶栏阴影同步为新主色 RGB `196, 85, 73`

### Requirement: 组件禁用硬编码阴影

任何组件 SHALL 使用 `--shadow-*` token 而非裸 `rgba(0,0,0,.35)` 等字面量；全仓 grep 裸 `rgba(0,0,0` 作用于 `box-shadow` 的项 MUST 为 0。

#### Scenario: 卡片阴影迁移
- **WHEN** `DishCard` / `CardSection` / 评价卡渲染投影
- **THEN** 引用 `var(--shadow-card)` / `var(--shadow-float)`，无裸 rgba 阴影
