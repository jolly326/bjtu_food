# 评价「有用」（A-07）—— ⛔ 已全链下线

> **本功能已于 2026-09-20 全链下线（用户拍板，权威 `project_spec.md` §7.30 清单 #1 与 `review-api-contract` spec）**：
> 端点 `POST /reviews/{id}/useful`、`ReviewVO.useful` / `usefulCount`、`ReviewAdminVO.usefulCount`、
> `review_useful` 表与 `review.useful_count` 列、三端展示（含小程序评价卡按钮与 Web 评价列）**均已删除**；
> 评价排序唯一口径为**时间倒序**。**本文档仅作历史留痕，不得据以实现或反向推导「仍存在该能力」；恢复须重新拍板（PR-04）。**
>
> 所属端：**学生端（微信小程序）** ｜ 鉴权：~~🔐 认证~~（功能已下线）
> 返回：[功能总览](./README.md)

## 干什么

给别人的评价点「有用」，用于**评价列表排序**（默认按有用数置顶，帮后来者先看到高质量口碑）。一人对一条评价只有一票，可取消。

## UI

> 📐 页面 UI 设计稿已拆出 → [client-评价有用.md（docs/ui）](../ui/client-评价有用.md)（2026-09-22 拆分；**UI 口径以该文件为唯一真源**）

## 操作

1. 点「有用」→ 未点过则 +1 并变选中；**再点一次取消**（-1）。
2. 请求期间端上做乐观更新，失败回滚。
3. 未认证 → 不发起请求，直接弹认证引导。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/reviews/{id}/useful` | **🔐 认证**（`@RequireVerified`；未认证 → **4031**） | 「有用」**幂等切换**：未标记→标记返回 `useful=true`；已标记→取消返回 `useful=false`，不抛错。**注（2026-09-20 §7.30）：本端点随「评价有用」下线一并删除（spec §7.14 清单 #1），不做 RESTful 化改造。** |

## 字段

### 请求

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `id` | number | **是** | 评价 ID（路径参数，如 `/reviews/1/useful`）。**无请求体**，用户身份从 JWT 取 |

### 响应（`data` = `UsefulResult`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `useful` | boolean | **本次切换后的状态**：true=当前已标记「有用」/ false=已取消 |
| `usefulCount` | number | 该评价当前的「有用」总数（端上直接覆盖计数展示） |

### 关联出参（读接口里的同名字段）

| 字段名 | 出现位置 | 中文解释 |
|---|---|---|
| `usefulCount` | `ReviewVO`（`GET /reviews`、`GET /my/reviews`） | 该评价有用总数 |
| `useful` | `ReviewVO`（仅登录态） | 当前用户是否已标记；公开列表未登录时为 null |
| `usefulCount` | `ReviewAdminVO`（`GET /admin/reviews`） | 管理端评价列表的有用数 |

### 错误码

| code | 含义 | 中文解释 |
|---|---|---|
| 4031 | 邮箱未认证 | 前端据此弹 `AuthSheet`（与 403 普通无权限分流） |
| 400 | 评价不存在 | 资源缺失已收敛入 400 |

## 数据（落库）

| 表 | 变化 | 中文解释 |
|---|---|---|
| `review_useful` | INSERT / DELETE | 一人一评一票（唯一索引约束），记录「谁给哪条评价点了有用」 |
| `review.useful_count` | 异步 UPDATE | 评价的有用总数（冗余计数，与 `review_useful` 行数同口径） |
