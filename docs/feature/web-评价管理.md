# 评价管理（事后处置）（B-03）

> 所属板块：**web（管理后台）** ｜ 鉴权：**🔑 口令**（`X-Admin-Token`）
> 返回：[功能总览](./README.md)

## 干什么

处理违规评价：**隐藏 / 显示 / 删除**。**无审核概念**——内容安全检测通过即公开（`pass`/`review` 放行、仅 `risky` 拒绝），管理端只做事后处置。

## UI

> 📐 页面 UI 设计稿已拆出 → [web-评价管理.md（docs/ui）](../ui/web-评价管理.md)（2026-09-22 拆分；**UI 口径以该文件为唯一真源**）

## 操作

1. 进页 → 默认列出全部评价（分页）。
2. 切显隐 switch → 二次确认 → 隐藏或恢复显示（公开端立即生效）。
3. 勾选多行 → 批量隐藏 / 显示 / 删除。
4. 行「查看」→ 抽屉看配图与正文。
5. **深链**：支持 `?rid=<评价id>` 自动定位并高亮该条（由反馈处理页跳入）。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/admin/reviews` | 🔑 | 评价列表（分页；支持按显隐、用户、关键词过滤） |
| PUT | `/admin/reviews/{id}/hide` | 🔑 | 隐藏 / 显示评价（显式设置，非 toggle） |
| DELETE | `/admin/reviews/{id}` | 🔑 | 删除评价（物理删除 + 触发菜品评分重算） |

## 字段

### 请求 · `GET /admin/reviews`（query）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `page` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数，默认 10（上限 100） |
| `isHidden` | number | 否 | 显隐过滤：**必须传数值** `0`=正常 / `1`=已隐藏（传 boolean 会因类型转换 400）；不传 = 全部 |
| `userId` | number | 否 | 按提交用户过滤（用户行为聚合用） |
| `keyword` | string | 否 | 评价正文关键词（模糊匹配） |

### 响应 · `GET /admin/reviews`（`data` = `PageResult<ReviewAdminVO>`）

**分页壳字段**：

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `records` | ReviewAdminVO[] | 当前页评价行数组（端上以它为准） |
| `total` | number | 符合条件的评价总条数 |
| `page` | number | 服务端归一化后的实际页码 |
| `pageSize` | number | 服务端归一化后的实际每页条数 |
| `list` | ReviewAdminVO[] | 过渡期兼容字段，恒等于 `records`，新代码勿用 |

### 响应 · `ReviewAdminVO` 单行字段

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | 评价 ID |
| `userId` | number | 评价者用户 ID |
| `dishId` | number | 关联菜品 ID |
| `dishName` | string | 关联菜品名称（联表补齐） |
| `userNickname` | string | 评价者昵称 |
| `userAvatar` | string | 评价者头像 URL |
| `rating` | number | 评分（1~5 星） |
| `content` | string | 评价正文 |
| `images` | string[] | 评价配图 URL 数组（≤3 张 COS 地址） |
| `createdAt` | string | 评价时间 |
| `isHidden` | number | 是否被隐藏：`0`=正常 / `1`=已隐藏 |
| `usefulCount` | number | 「有用」标记总数（**若 A-07 删除「有用」则此字段下线**） |

> **无 `secState`**（内容安全态列已全链退役，不再有复核队列）。

### 请求 · `PUT /admin/reviews/{id}/hide`

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `id` | number | **是** | 评价 ID（路径参数） |
| `hidden` | boolean | 否 | `true`=隐藏 / `false`=恢复显示。**body 缺失时默认 `false`（=显示）**——前端务必显式传 `hidden`，否则「隐藏」会被还原 |

### 响应 · `PUT .../hide` 与 `DELETE`

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `data` | null | 无载荷；成功即 `code=200` |

### 副作用（写库 / 触发）

| 目标 | 变化 | 中文解释 |
|---|---|---|
| `review.is_hidden` | 0 ↔ 1 | 公开评价列表只展示 `is_hidden=0` 的评价 |
| `review` | DELETE 行 | 删除评价（管理端） |
| `review_useful` | DELETE 关联 | 清孤儿行，避免有用计数长期不一致 |
| `dish.avg_rating` / `dish.rating_count` | **异步重算** | 隐藏 / 显示 / 删除均会发 `ReviewSubmittedEvent`，由监听器异步重算（口径 = 仅 `is_hidden=0`） |

## 数据（读写）

| 表 | 变化 | 中文解释 |
|---|---|---|
| `review` | UPDATE `is_hidden` / DELETE | 事后处置 |
| `review_useful` | DELETE | 删除评价时清理关联投票 |
| `dish` | 异步 UPDATE 两列 | 平均评分、评价数 |
