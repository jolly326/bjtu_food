# 系统通知 — 页面 UI 设计稿

> 所属端：学生端（微信小程序）
> **口径分工**：**页面 UI 设计口径以本文件为唯一真源**，功能流程 / 接口 / 字段 / 数据落库口径以功能文档为准。


- 入口：「我的」页宫格「系统通知」（带未读红点）。
- 页面：`pages/notifications/index`——列表（标题、正文摘要、时间、未读标记）；右上「全部已读」。
- 未读红点由 `unread-count` 驱动。

## 接口数据字段（UI 精修用）

**页面**：`pages/notifications/index`（分包 `pages/notifications/`；二级页，**无 TabBar**）

### 组件清单（本界面需要哪些组件）

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `AppHeader` | 公共 `components/AppHeader.vue` | 页头：居中标题「系统通知」+ **返回**（文字）；**右侧动作区承载「全部已读」胶囊**（`check` 图标 + 文字，无未读时置灰常驻）⚠️ 见文末差异 |
| 2 | `IconSvg` | 公共 `components/IconSvg.vue` | **返回**（文字） / 「全部已读」勾选图标 `check` |
| 3 | `RetryBlock` | 公共 `components/RetryBlock.vue` | 首屏加载失败的「加载失败 · 点击重试」块（**仅认证态渲染**） |
| 4 | `scroll-view`(scroll-y + `@scrolltolower`) | uni 内置控件 | 通知列表滚动容器 + 触底分页 |
| 5 | 通知行 `.msg-item`（页内内联结构） | `pages/notifications/index.vue` 内联 | 未读竖条 / 圆点 + 标题 + 时间 + 正文（2 行截断）；整行热区 |
| 6 | 空态 `.empty-tip`（页内内联结构） | 页内内联 | 「暂无通知 / 反馈处理结果会在这里通知你」（**仅认证态**） |
| 7 | `useNotifyStore` | `stores/notify` | 未读总数与红点（本页与「我的」页共用同一真源） |

### 有哪些数据要显示、显示在哪个组件

| # | 数据（字段） | 来源 | 中文含义 | 显示在哪个组件 | 呈现位置 / 形式 |
|---|---|---|---|---|---|
| 1 | `records[].id` | `GET /my/notifications`（`PageResult<NotificationVO>`） | 通知 ID | 无界面（列表 `key` + 单条已读 `PUT /my/notifications/{id}/read` 目标） | 零可见 UI |
| 2 | `records[].title` | 同上 | 通知标题 | 通知行 `.msg-title` | 未读时取淡主色字 |
| 3 | `records[].createdAt` | 同上 | 生成时间 | 通知行 `.msg-time`（标题行右侧） | `formatDateTime` 格式化，次级灰小字 |
| 4 | `records[].content` | 同上 | 正文（处理回执内容） | 通知行 `.msg-content` | 2 行截断 |
| 5 | `records[].isRead` | 同上 | 是否已读（`true` 已读 / `false` 未读） | 通知行未读态：圆点 + 左侧竖条 + 标题色 | `false` → 未读视觉；`true` → 圆点透明 |
| 6 | `records` / `total` | 分页壳 | 当前页行数组 / 总条数 | 列表渲染 + 触底结束判定 | 端上以 `records` 为准 |
| 7 | `count` | `GET /my/notifications/unread-count` | 未读总数 | 「全部已读」可用性（`hasUnread`）+「我的」页宫格红点 | `> 0` 才可点 |
| 8 | — | `PUT /my/notifications/read-all` | 无载荷（`data = null`） | 成功即整列表转已读态 | 幂等 |
| 9 | 空态 / 失败态 | 端上 `loaded` / `loadFailed` | 无通知 / 加载失败 | 空态 `.empty-tip` / `RetryBlock` | 失败**先于**空态渲染；游客（4031/403）**静默无态** |

> **不出参字段（零消费即删）**：`type`（通知类型键，服务端内部使用）与 `relatedId`（关联反馈 ID，当前无落地页）——端上从不读取，故不下发。上一版本 UI 稿曾登记二者为「无界面」，现已按契约彻底移出出参。

**入参提交**
| 接口 | 字段 |
|---|---|
| `GET /my/notifications` | `page` / `pageSize=20`（端上**不传 `isRead`**，恒取全部） |
| `PUT /my/notifications/{id}/read` | 无请求体；非本人或不存在**静默成功** |
| `PUT /my/notifications/read-all` | 无请求体；**出参 `data = null`**（成功即 200，幂等） |

**错误码**：`401` 未登录｜`403` 账号已禁用 / 无权限｜`4031` 未完成学号邮箱认证（**游客静默**：不渲染失败态、不渲染空态、不渲染重试块）
**控件类型**：`scroll-view`(scroll-y + scrolltolower)、列表行（整行热区）、空态、失败重试块、页头胶囊按钮

> **⚠️ 与当前代码的差异（待修）**：`components/AppHeader.vue` 当前**未定义 `action` 具名插槽**，本页 `<template #action>` 内的「全部已读」按现行代码**不会渲染**（页面数据链路 `hasUnread` / `readAllBusy` / `PUT /my/notifications/read-all` 均已就绪）。需给 `AppHeader` 补右侧动作插槽（或把该动作改为页内固定位）后本条 UI 才成立。
