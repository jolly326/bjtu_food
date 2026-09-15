# client-ui-token-source Specification

## Purpose
约束客户端设计令牌（值）的唯一来源与声明面：令牌值统一由 `theme/` 目录承载，App.vue 仅作 CSS 变量声明面，令牌集合保持最小可用（零引用令牌删除、同义令牌收敛）。

## Requirements

### Requirement: single-token-source
客户端设计令牌（值）SHALL 统一由 `theme/` 目录承载（`theme/tokens.ts` 为颜色常量唯一事实源；`App.vue` 仅作 CSS 变量声明面，因 WXSS 编译丢弃 `uni.scss` 的 `:root`）。令牌的 CSS 变量声明面 SHALL **仅保留 `page` 一条规则**：SHALL NOT 保留 H5 `:root` 兜底块（H5 非交付目标），SHALL NOT 保留深色模式 `.theme-dark` 令牌块或任何深色令牌。禁止在第四处新增或保留令牌定义；删除后其「原生属性例外登记」等说明迁入 `theme/tokens.ts` 注释。

#### Scenario: 删除 uni.scss
- **WHEN** 删除 `uni.scss` 文件
- **THEN** `$uni-*` 全站 0 引用，例外说明迁至 `theme/tokens.ts`

#### Scenario: 合并 constants/ui.ts
- **WHEN** 删除 `constants/ui.ts`
- **THEN** 仅有的原生属性例外常量（SWIPER_INDICATOR_*）并入 `theme/tokens.ts`，引用方改从 `theme/tokens`

#### Scenario: 令牌引用
- **WHEN** 组件/页面引用设计令牌
- **THEN** 一律 `var(--xxx)` 引用 CSS 变量，不新增散落令牌；删除的动效令牌（--press-scale / --scale-* / --duration-drawer / --ease-* / --tab-active-scale）全站 0 引用

#### Scenario: 移除 H5 兜底声明面
- **WHEN** 收敛令牌声明面后检查 `App.vue`
- **THEN** 令牌仅由 `page` 规则声明，不存在 `:root` 令牌块；`package.json` 不含任何 H5 构建脚本

#### Scenario: 移除深色令牌体系
- **WHEN** 收敛主题后检查代码库
- **THEN** `App.vue` 无 `.theme-dark` 令牌块，全仓 0 处 `.theme-dark` 类绑定，且不存在主题 store 与深色取色分支

### Requirement: 令牌冗余修剪
令牌集合 SHALL 保持最小可用：零引用令牌 SHALL 被删除；取值完全相同的同义令牌 SHALL 收敛为单一规范令牌（其余以 `var()` 别名保留语义命名）。修剪 SHALL NOT 改变浅色模式下任一页面的既有取色、字号、圆角与间距。

#### Scenario: 删除零引用令牌
- **WHEN** 统计 `client/src` 下某令牌的引用次数为 0
- **THEN** 该令牌从 `App.vue` 声明中移除（原生属性例外登记除外）

#### Scenario: 收敛重复取值令牌
- **WHEN** 多个令牌取值完全相同
- **THEN** 收敛为单一规范令牌，其余改以 `var(规范令牌)` 声明，视觉输出不变

#### Scenario: 修剪后视觉不变
- **WHEN** 令牌修剪完成后对比修剪前后各页面渲染结果
- **THEN** 颜色、字号、圆角、间距均保持一致
