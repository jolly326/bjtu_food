# 系统通知 — 页面 UI 设计稿

> 所属端：学生端（微信小程序） ｜ 归属功能文档：[client-系统通知.md](../feature/client-系统通知.md)
> **口径分工**：**页面 UI 设计口径以本文件为唯一真源**，功能流程 / 接口 / 字段 / 数据落库口径以功能文档为准。


- 入口：「我的」页宫格「系统通知」（带未读红点）。
- 页面：`pages/notifications/index`——列表（标题、正文摘要、时间、未读标记）；右上「全部已读」。
- 未读红点由 `unread-count` 驱动。

## 接口数据字段（UI 精修用）

**页面**：`pages/notifications/index`

**出参消费**
| 接口 | 字段 | 端上用途 |
|---|---|---|
| `GET /my/notifications`（`PageResult<NotificationVO>`） | `records[].id` | 列表 key / 单条已读目标 |
| | `records[].title` / `content` / `createdAt` | 标题 / 正文摘要 / 时间 |
| | `records[].isRead` | 未读标记与已读态 |
| | `records[].type` | 仅容错（未知类型不处理） |
| | `records[].relatedId` | **零消费**（端上不做跳转） |
| | `total` | 触底判断（分页壳仅 `records` / `total`；页码由请求侧掌握） |
| `GET /my/notifications/unread-count` | `count` | 红点（本页与「我的」页共用） |

**入参提交**
| 接口 | 字段 |
|---|---|
| `GET /my/notifications` | `page` / `pageSize=20`（端上不传 `isRead`，恒取全部） |
| `PUT /my/notifications/read-all` | 无请求体；出参 `number` = 本次置读条数 |
| `PUT /my/notifications/{id}/read` | 无请求体；非本人或不存在**静默成功** |

**错误码**：`401` 未登录｜`403` 账号已禁用 / 无权限｜`4031` 未完成学号邮箱认证

**UI 组件**：公共 `AppHeader`（右侧 action 槽「全部已读」）/ `IconSvg` / `RetryBlock`
**控件类型**：`scroll-view`(scroll-y + scrolltolower)、列表行（整行热区）、空态、失败重试块
