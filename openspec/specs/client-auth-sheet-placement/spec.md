# client-auth-sheet-placement Specification

## Purpose

约束认证弹层（AuthSheet）的挂载位置：仅在存在需认证入口的页面或组件内挂载，避免无认证入口的页面常驻一个永不触发的弹层实例。

## Requirements

### Requirement: 认证弹层仅挂载于存在认证入口的页面

`AuthSheet` SHALL 仅在其所在页面（或组件）存在「需认证才能继续」的入口时挂载。页面无任何需认证入口时 SHALL NOT 挂载 `AuthSheet`，亦 SHALL NOT 仅为「预防将来需要」而挂载。「需认证入口」SHALL 指参与型行为入口（发表动态 / 评价、查看「我发布的」、点赞 / 有用等）；举报、意见反馈、查看系统通知等免认证行为 SHALL NOT 构成挂载理由。

#### Scenario: 首页不挂载认证弹层
- **WHEN** 检查首页 `pages/home/index.vue`
- **THEN** 模板中无 `<AuthSheet />`、脚本中无 `AuthSheet` 导入，且首页所有交互均不触发认证引导

#### Scenario: 存在需认证入口的页面保留挂载
- **WHEN** 页面提供需 `verified=true` 才能执行的入口（如「我的」页的『我发布的』与『去认证』、发布内容页的提交）
- **THEN** 该页面保留 `<AuthSheet />` 挂载，`requireAuth` 流程照常弹出

#### Scenario: 仅免认证入口的页面不挂载
- **WHEN** 页面仅有举报 / 意见反馈 / 查看系统通知等免认证入口（如系统通知页）
- **THEN** 该页面不挂载 `<AuthSheet />`，入口点击不触发认证

#### Scenario: 组件内触发认证
- **WHEN** 组件自身触发需认证动作（如发布评价、点赞）
- **THEN** 由该组件内部挂载 `AuthSheet`，而非要求其宿主页面代为挂载
