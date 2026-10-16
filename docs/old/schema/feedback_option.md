# feedback_option — 反馈类型字典

**用途**：client 意见反馈页的「反馈类型」单选字典（`GET /feedback-options` 的数据源）—— 中文名 + 启停。
**对应实体**：`com.bjtufood.feedback.entity.FeedbackOption`
**值域**：公开字典（`GET /feedback-options`）与反馈提交白名单均查本表（**仅启用项**）。管理端维护入口见 [api/web/feedback-options.md](../api/web/feedback-options.md)。

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 类型 ID；**数据锚点**（历史反馈按它落库到 `feedback.option_id`） |
| `name` | VARCHAR(32) | NO | `''` | | 类型中文名（1~32 字；**唯一由应用层校验**） |
| `status` | VARCHAR(10) | NO | `'on'` | KEY | `on` 启用 / `off` 停用 |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | 更新时间（管理端列表出参 `updatedAt`） |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | |
| `idx_feedback_option_status` | `status` | 普通 | 公开查询 `WHERE status='on'` 走索引（顺序由 `id` 升序给出） |

> `name` **不加唯一索引** —— 「名称唯一」由**应用层**保证（与 `dish_attribute_value` 同口径）。
> **无排序列** —— 公开字典与管理端列表一律按 `id` 升序。

## 关键设计

### 数据锚在 ID，改名免费
`feedback.option_id` 存的就是**类型 ID**，因此：

- 改 `name` → **全站生效**（含历史反馈的中文翻译），零数据迁移；
- 管理端新增类型只需填 `name`，ID 由后端生成。

### 删除受引用约束
被任一反馈记录引用（`option_id = id`）时**禁止删除**（应用层计数 `usageCount`，`> 0` 即拦截），避免历史反馈翻不出中文；**下线一律用 `status='off'`**。

### 至少 1 条启用
应用层校验：**停用最后一条启用 → `400`** —— 反馈是 UGC 表达入口，不接受「把入口配空」（配空即用户无法提交）。

### 启用上限 8 条
单选表单的**可用性约束**（非技术约束），应用层校验。

## 关联

| 方向 | 目标表 | 关系 |
|---|---|---|
| 1→n | [`feedback.option_id`](./feedback.md) | **外键 `fk_feedback_option`（无级联）**：类型字典有独立生命周期，被引用项不得删除；`feedback` 行不因字典项停用而失效（历史仍译出中文） |

## 注意事项

- **初始种子 3 条**（`id` 1~3）：`程序功能Bug` / `产品功能建议` / `其他相关问题`，均 `status='on'`。
- 真源为本表：`GET /feedback-options`（公开字典）与反馈提交白名单均查表（仅启用项）；出参恰 `id` + `name` 两字段。
- **出参恰 2 字段**（`id` / `name`）—— 客户端按数组顺序（`id` 升序）渲染、提交时上送 `optionId`。
