## Purpose

为小程序提供常驻底部菜单栏，清晰区分「首页 / 社区 / 我的」三个主区，并定义其导航与视觉规范。

## ADDED Requirements

### Requirement: 主区底部菜单栏常驻
应用 SHALL 在 home、community、profile 三个主根页常驻渲染底部菜单栏，且恰好包含「首页 / 社区 / 我的」三项。

#### Scenario: 首页进入时菜单栏可见且高亮正确
- **WHEN** 用户进入首页（或任意主根页）
- **THEN** 屏幕底部出现三项目录栏，当前所在主根页对应的项处于高亮（active）态

### Requirement: 切换主区导航
点击某一项 SHALL 跳转到对应主根页，并将该项标记为 active；当前路由路径决定 active 态（而非仅组件内 local state）。

#### Scenario: 从首页点「我的」
- **WHEN** 用户在首页点击「我的」项
- **THEN** 跳转至 profile 根页，菜单栏「我的」项高亮，且原导航栈被重置为该主根页（不再叠加多层历史）

#### Scenario: 从社区页点「首页」
- **WHEN** 用户在 community 页点击「首页」项
- **THEN** 跳转至 home 根页，「首页」项高亮

### Requirement: 视觉与交互规范
菜单栏 SHALL 使用 `--bg-card` 表面色（白底卡片）；每项由 `IconSvg`（home / comment / profile）图标 + 文字标签组成，active 项以对比色 `--color-primary` 区分；交互使用 `@tap`、按压反馈 `scale(var(--press-scale))`，颜色一律走设计 token（禁裸 hex），并支持 `prefers-reduced-motion` 降级（关闭按压位移/弹性）。

#### Scenario: 减少动效偏好开启
- **WHEN** 系统开启 `prefers-reduced-motion`
- **THEN** 菜单栏项按压时不产生 `scale` 位移过冲，仅保留必要透明度过渡

### Requirement: 不干扰二级页
菜单栏 SHALL 不出现在任何 `navigateTo` 进入的二级页（如详情、活动、反馈、个人资料编辑等）；既有二级页 `navigateTo` 跳转流程保持不变。

#### Scenario: 进入菜品详情页
- **WHEN** 用户从首页进入菜品详情（`navigateTo` 到 `/pages/detail/dish`）
- **THEN** 详情页底部不显示该菜单栏，返回后首页仍正常显示且高亮态正确
