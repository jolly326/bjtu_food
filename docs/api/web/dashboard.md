# web/dashboard — 运营看板

**归属**：`DashboardController`（只读编排层）
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 登录后首屏的**统一待办入口**。只读聚合页，**不含任何写操作**；所有处置都在各自页面。

---

## GET /admin/dashboard

**用途**：看板全量数据（**只读、无参数、不分页**）

### 请求参数
无。

> **一次请求返回全量**：首屏不该为了三个分区发三次请求。

### 响应 `data` = `DashboardVO`

**① `todo`（待办）**
| 字段 | 类型 | 说明 |
|---|---|---|
| `pendingFeedbackCount` | number | 待处理**意见反馈**数（`user_feedback`：非举报且 `status='pending'`） |
| `pendingReportCount` | number | 待处理**举报**数（`type='report'` 且 `status='pending'`） |
| `pendingCorrectionCount` | number | 待处理**问题反馈**数（`dish_correction`：`status='pending'`，含 `field` + `gone`） |
| `recent` | object[] | **最近 5 条待办**（跨三类合并，按提交时间倒序） |

**`recent[]` 单项结构**
| 字段 | 类型 | 说明 |
|---|---|---|
| `kind` | string | `feedback` / `report` / `correction`（决定跳转哪个处置页） |
| `id` | number | 对应记录 ID（跳转定位） |
| `title` | string | 摘要：反馈 = 正文摘要；举报 = 被举报评价摘要；菜品问题反馈 = 菜品名 + 改动项数 |
| `submittedAt` | string | 提交时间（`yyyy-MM-dd HH:mm:ss`） |

**② `health`（主数据健康度）** —— 只收**管理员当场能修**的项
| 字段 | 类型 | 说明 |
|---|---|---|
| `dishesWithoutImage` | number | **无图片**的菜品数 |
| `dishesWithoutStall` | number | **未归入档口**的菜品数（`stall_id = 0`） |
| `dishesWithoutCategory` | number | **种类为空**的菜品数（`meal_type_id` 为空） |
| `stallsWithoutDish` | number | **无菜品**的档口数（空档口） |

**③ `overview`（概况，只读）**
| 字段 | 类型 | 说明 |
|---|---|---|
| `userCount` | number | 用户总数（**不含已注销**） |
| `verifiedUserCount` | number | 已认证用户数（`bind_email` 非空） |
| `onSaleDishCount` | number | 在售菜品数（`status='on'`） |
| `reviewCount` | number | 评价总数（**含已隐藏**） |

### 错误码
| code | 条件 |
|---|---|
| `401` | 未带 / token 失效 |

### 备注
- **全部只读**：本页不写任何表；计数口径与各自列表页同一判据。
- **一次请求完成全部计数 + 1 次最近列表**：计数走已有索引；`recent` 跨三类数据源取固定条数后应用层归并。
- **不做缓存**：首屏要准，且单管理员访问频率极低；计数是**瞬时快照**，与列表页可能存在秒级偏差（可接受）。
- **概况只给规模、不给时间序列**；**健康度只挑「可执行的」**（不列「描述为空」这类允许为空的项）。
