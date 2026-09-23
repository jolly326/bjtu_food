# Tab Bar Specification

## Purpose

为小程序提供常驻底部菜单栏，清晰区分「首页 / 我的」两个主区，并定义其导航与视觉规范。

## Requirements

### Requirement: 主区底部菜单栏常驻

应用 SHALL 在 home、mine 两个主根页常驻渲染底部菜单栏，且恰好包含「首页 / 我的」两项。其中「我的」根页为 `pages/mine`（主包页面，目录由旧 `pages/profile` 收敛而来），Tab 标签仍为「我的」。

#### Scenario: 首页进入时菜单栏可见且高亮正确

- **WHEN** 用户进入首页（或任意主根页）
- **THEN** 屏幕底部出现「首页 / 我的」两项菜单栏，当前所在主根页对应的项处于高亮（active）态

#### Scenario: 我的页高亮

- **WHEN** 用户进入「我的」主根页
- **THEN** 菜单栏「我的」项高亮，「首页」项为未选中态

### Requirement: 首页与我的互切导航

点击某一项 SHALL 跳转到对应主根页，并将该项标记为 active；当前路由路径决定 active 态（而非仅组件内 local state）。「我的」主根页对应路径 SHALL 为 `/pages/mine/index`。

#### Scenario: 从首页点「我的」

- **WHEN** 用户在首页点击「我的」项
- **THEN** 跳转至 `/pages/mine/index`（mine 根页），菜单栏「我的」项高亮，且原导航栈被重置为该主根页（不再叠加多层历史）

#### Scenario: 从我的页点「首页」

- **WHEN** 用户在「我的」页点击「首页」项
- **THEN** 跳转至 home 根页，「首页」项高亮，导航栈同样被重置

### Requirement: 视觉与交互规范
菜单栏 SHALL 使用 `--bg-card` 表面色（白底卡片）；每项由 `IconSvg`（home / profile）图标 + 文字标签组成，active 项以对比色 `--color-primary` 区分，且 active 项图标 SHALL 切换为**填充样式**，与未选中项的线性样式形成区分，图标与文字 SHALL 同步变为主色；交互使用 `@tap`，颜色一律走设计 token（禁裸 hex）。按压反馈 SHALL 走全局 `.pressed` 的 **opacity 弱化**（`hover-class="pressed"`，与 `client-ui-motion`「动效全部删除、按压仅 opacity」一致）；SHALL NOT 使用 `transform: scale()` —— 按压缩放令牌 `--press-scale` 与 `<Pressable>` 组件均已删除、全站 0 引用。

#### Scenario: 按压反馈为 opacity 弱化
- **WHEN** 用户按压任一菜单项
- **THEN** 该项以全局 `.pressed` 的透明度弱化反馈，不产生 `scale` 缩放或位移

#### Scenario: 选中态图标填充
- **WHEN** 用户切换到某一主根页
- **THEN** 该页对应菜单项的图标切换为填充样式，图标与文字同为 `--color-primary`

#### Scenario: 未选中项为线性
- **WHEN** 查看非当前主根页的菜单项
- **THEN** 其图标为线性样式，且颜色非主色

### Requirement: 不干扰二级页
菜单栏 SHALL 不出现在任何 `navigateTo` 进入的二级页（如详情、反馈、个人资料编辑等）；既有二级页 `navigateTo` 跳转流程保持不变。

#### Scenario: 进入菜品详情页
- **WHEN** 用户从首页进入菜品详情（`navigateTo` 到 `/pages/detail/dish`）
- **THEN** 详情页底部不显示该菜单栏，返回后首页仍正常显示且高亮态正确

### Requirement: 底部导航尺寸规范

底部菜单栏 SHALL 落实统一尺寸：各项 tab 图标渲染尺寸为 `48rpx`（≈24px），标签文字 `24rpx`（≈12px），图标与文字间距 `8rpx`（≈4px）。选中态填充图标与未选中态线性图标 SHALL 使用相同渲染尺寸与视觉比例，切换高亮时整体布局高度不发生跳动。

#### Scenario: 尺寸与间距统一
- **WHEN** 审查 `TabBar.vue` 的图标 / 标签 / 间距
- **THEN** 图标 `48rpx`、标签文字 `24rpx`、图标与文字间距 `8rpx`

#### Scenario: 切换不跳动
- **WHEN** 在首页 / 我的之间切换
- **THEN** 选中项为填充图标 + 主色文字、未选中为同尺寸线性图标 + 三级灰文字，切换过程无位置跳动
