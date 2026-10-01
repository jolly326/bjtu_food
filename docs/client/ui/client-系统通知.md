# 系统通知 — 页面 UI 设计稿

> 所属端：学生端（微信小程序）
> **口径分工**：**页面 UI 设计口径以本文件为唯一真源**，功能流程 / 接口 / 字段 / 数据落库口径以功能文档为准。


- 入口：「我的」页宫格「系统通知」（带未读红点）。
- 页面：`pages/notifications/index`——列表（标题、正文摘要、时间、未读标记）；右上「全部已读」。
- 未读红点由 `unread-count` 驱动。

## 接口数据字段（UI 精修用）

**页面**：`pages/notifications/index`（分包 `pages/notifications/`；二级页，**无 TabBar**）

### 组件清单（本界面需要哪些组件）

> 组件自身规格（尺寸 / 圆角 / 颜色 / 状态 / 交互）以 [client-公共组件与形态基线.md](./client-公共组件与形态基线.md) §二 为唯一真源；**本表只写「本页用法」**。

| # | 组件 | 在本页做什么 |
|---|---|---|
| 1 | `AppHeader` | 页头：居中标题「系统通知」+ 返回；**右侧动作区承载「全部已读」胶囊**（无未读时置灰常驻不可点，有未读时暖橙描边 + 暖橙文字） |
| 2 | `IconSvg` | 「全部已读」勾选图标 `check` |
| 3 | `RetryBlock` | 首屏加载失败的「加载失败 · 点击重试」块 |
| 4 | `scroll-view` | 通知列表滚动容器 + 触底分页 |
| 5 | 通知行 `.msg-item`（页内内联结构） | **单张白色列表卡**内多行；未读 = 左侧暖橙细竖条 + 圆点 + 淡主色标题；正文 2 行截断；整行热区 |
| 6 | `EmptyState` | 铃铛 `bell` 图标 + 标题「暂无通知」+ 说明「反馈处理结果会在这里通知你」（**两态同一句**）；`RetryBlock` 先于空态 |
| 7 | `useNotifyStore` | 未读总数与红点（本页与「我的」页共用同一真源） |

### 有哪些数据要显示、显示在哪个组件

| # | 数据（字段） | 来源 | 中文含义 | 显示在哪个组件 | 呈现位置 / 形式 |
|---|---|---|---|---|---|
| 1 | `records[].id` | `GET /my/notifications`（`PageResult<NotificationVO>`） | 通知 ID | 无界面（列表 `key` + 单条已读 `PUT /my/notifications/{id}/read` 目标） | 零可见 UI |
| 2 | `records[].title` | 同上 | 通知标题 | 通知行 `.msg-title` | 未读时取淡主色字 |
| 3 | `records[].createdAt` | 同上 | 生成时间 | 通知行 `.msg-time`（标题行右侧） | `formatDateTime` 格式化，次级灰小字 |
| 4 | `records[].content` | 同上 | 正文（处理回执内容） | 通知行 `.msg-content` | 2 行截断 |
| 5 | `records[].isRead` | 同上 | 是否已读（`true` 已读 / `false` 未读） | 通知行未读态：左侧暖橙细竖条 + 圆点 + 标题淡主色 | `false` → 未读视觉（竖条 + 圆点 + 淡主色标题）；`true` → 竖条 / 圆点隐藏、标题恢复主文本色 |
| 6 | `records` / `total` | 分页壳 | 当前页行数组 / 总条数 | 列表渲染 + 触底结束判定 | 端上以 `records` 为准 |
| 7 | `count` | `GET /my/notifications/unread-count` | 未读总数 | 「全部已读」可用性（`hasUnread`）+「我的」页宫格红点 | `> 0` 才可点 |
| 8 | — | `PUT /my/notifications/read-all` | 无载荷（`data = null`） | 成功即整列表转已读态 | 幂等 |
| 9 | 空态 / 失败态 | 端上 `loaded` / `loadFailed` | 无通知 / 加载失败 | 空态 `EmptyState` / `RetryBlock` | 失败**先于**空态渲染 |

> **不出参字段（零消费即删）**：`type`（通知类型键，服务端内部使用）与 `relatedId`（关联反馈 ID，当前无落地页）——端上从不读取，故不下发。

**入参提交**
| 接口 | 字段 |
|---|---|
| `GET /my/notifications` | `page` / `pageSize=20`（端上**不传 `isRead`**，恒取全部） |
| `PUT /my/notifications/{id}/read` | 无请求体；非本人或不存在**静默成功** |
| `PUT /my/notifications/read-all` | 无请求体；**出参 `data = null`**（成功即 200，幂等） |

**错误码**：`401` 未登录｜`403` 账号已禁用 / 无权限（本页为**登录级**能力，不涉及 `4031`）
**控件类型**：`scroll-view`(scroll-y + scrolltolower)、列表行（整行热区）、空态、失败重试块、页头胶囊按钮

## 视觉规范（列表 / 空态 / 色彩 / 间距）

### 列表卡
- **单张白色轻量列表卡**：圆角 `16rpx`（`--radius-btn`）+ 轻量柔阴影 `--shadow-card`，页面左右 `--spacing-md`；全部通知行收纳于同一张卡内。
- 行高 ≥ `88rpx`；**行间以 `1rpx` 浅分隔线 `--border-color` 分隔**（最上 / 最下无线）。
- 未读行：左侧**暖橙细竖条**（`--color-primary`）+ 未读**圆点**（`--color-primary`）+ 标题**淡主色**（`--color-primary-text`）；已读行：竖条 / 圆点隐藏，标题恢复主文本色。

### 空态（`EmptyState` 公共组件）
- 触发：首屏 `loaded` + 无数据；`RetryBlock` **先于**空态渲染。
- 结构：铃铛图标 `bell`（`48rpx`、浅灰 `--text-tertiary`）+ 主标题「暂无通知」+ 说明；图标→标题 `--spacing-sm`、标题→说明 `--spacing-xs`；列表区域内垂直居中。
- 文案：恒「反馈处理结果会在这里通知你」（**两态同一句** —— 消息中心为登录级能力，游客提交的反馈 / 举报 / 纠错同样收到回执）。

### 色彩与线条
- 主色 `--color-primary` 仅用于：未读竖条 / 未读圆点 / 未读标题强调 / 「全部已读」胶囊（有未读态）。
- 其余文字灰度：标题主文本色、正文二级文本色、时间浅灰。
- 线条统一 `--border-color` `1rpx`；卡片圆角 `16rpx`、轻量柔阴影。

### 间距
- 页头底部 → 列表卡顶部：`--spacing-lg`。
- 列表卡内部：行高 ≥ `88rpx`；标题与正文 `--spacing-2xs`；行间分隔线 `1rpx`。
- 空态：图标→标题 `--spacing-sm`、标题→说明 `--spacing-xs`。

> **与当前代码的差异**：**无（文档与代码一致）** —— `components/AppHeader.vue` 已提供 `action` 具名插槽（`<slot name="action" />`），本页 `<template #action>` 内的「全部已读」正常渲染；数据链路 `hasUnread` / `readAllBusy` / `PUT /my/notifications/read-all` 均已就绪。
