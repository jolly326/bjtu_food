# report_reason — 举报原因字典

**用途**：client **举报弹层的「原因」单选字典**（`GET /report-reasons` 的数据源）—— 中文标签 + 顺序 + 启停。
**对应实体**：`com.bjtufood.feedback.entity.ReportReason`
**值域**：公开字典（`GET /report-reasons`）与举报提交白名单均查本表（仅启用项）。设计真源见 [A7 举报原因管理](../func/web/A-主数据维护/A7-举报原因管理.md)。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 原因 ID；**数据锚点**（举报记录按它落库到 `user_feedback.sub_reason_id`） |
| `label` | VARCHAR(32) | NO | `''` | | 中文标签（如「垃圾广告 / 营销刷屏」；**可改，改名免费**） |
| `sort_order` | INT | NO | 0 | | 展示顺序（升序） |
| `status` | VARCHAR(10) | NO | `'on'` | KEY | `on` 启用 / `off` 停用 |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间（管理端列表出参 `updatedAt`） |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | |
| `idx_reason_status_order` | `status`, `sort_order` | 复合 | 公开查询 `WHERE status='on' ORDER BY sort_order` 走索引 |

> `label` **不加唯一索引** —— 「标签唯一」由**应用层**保证（与 `dish_attribute_value` 同口径）。

## 关键设计

### 数据锚在 ID，改名免费
`user_feedback.sub_reason_id`（仅 `type='report'`）存的就是**原因 ID**，因此：

- 改 `label` → **全站生效**（含历史举报的中文翻译），零数据迁移；
- 管理端新增原因只需填 `label`，ID 由后端生成。

### 删除受引用约束
被任一举报记录引用（`sub_reason_id = id`）时**禁止删除**（应用层计数 `feedbackCount`，`> 0` 即拦截），避免历史举报翻不出中文；**下线一律用 `status='off'`**。

### 至少 1 条启用
应用层校验：**停用最后一条启用 → `400`** —— 举报是 UGC 治理入口，不接受"把入口配空"。

### 启用上限 8 条
单选弹层的**可用性约束**（非技术约束），应用层校验。

## 关联

| 方向 | 目标 | 关系 |
|---|---|---|
| 1→n | `user_feedback.sub_reason_id`（`type='report'`） | 非外键 |

> **不设外键** —— 与全库约定一致，关系由应用层维护。

## 注意事项

- **初始种子 6 条**：`spam` / `abuse` / `porn` / `illegal` / `fake` / `other`（标签见 A7；`INSERT IGNORE` 幂等种子以 `label` 判重）。
- 真源为本表：`GET /report-reasons`（公开字典）与举报提交白名单均查表（仅启用项）；出参恰 `id` + `label` 两字段。
- **出参恰 2 字段**（`id` / `label`）—— 客户端按数组顺序渲染、提交时上送 `reasonId`。
