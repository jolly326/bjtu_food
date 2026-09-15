# ui-motion Specification

## Purpose
建立动效设计 tokens（时长/缓动/非按压缩放）与全局 `prefers-reduced-motion` 降级基线，统一底部 Sheet 与顶部下拉的开合动画，提升体感一致性与低敏用户可达性。

## Requirements

### Requirement: 动效基础 tokens
`tokens.ts`（及 `uni.scss` 注册）SHALL 定义时长（`--duration-fast`/`--duration-base`/`--duration-slow`）、缓动（`--ease-out`/`--ease-in`/`--ease-out-soft`）与非按压强调缩放（如 `--tab-active-scale`）；任何位移/缩放动画 MUST 引用这些 token，禁止在组件内硬编码时长或缓动值。

#### Scenario: 时长统一引用
- **WHEN** 任意组件需要进入/退出动画
- **THEN** 使用 `var(--duration-base)` 而非裸 `0.2s` 等字面量

### Requirement: Sheet / 下拉开合统一
底部 Sheet（`HomeSortSheet`）与顶部下拉（`HomePriceSheet` / `CanteenFilter`）SHALL 使用「opacity 交叉淡入 + ≤8px 轻位移」的开合动画，动画可中途中断（点击遮罩即停），无弹性过冲。

#### Scenario: 顶部下拉展开
- **WHEN** 用户点开价格筛选
- **THEN** 面板从筛选条下沿淡入并下移 ≤8px，遮罩同步淡入

#### Scenario: 中断关闭
- **WHEN** 用户在动画进行中点遮罩关闭
- **THEN** 动画立即反向收起，不出现跳变或残留

### Requirement: reduced-motion 全局降级
当系统开启 `prefers-reduced-motion: reduce` 时，所有非必要位移与缩放 SHALL 退化为仅 opacity（≤0.2s）过渡，按压 `scale` 取消。

#### Scenario: 低敏模式
- **WHEN** 用户在系统设置开启「减少动态效果」
- **THEN** 卡片按压不缩放，Sheet/下拉仅淡入淡出，无位移
