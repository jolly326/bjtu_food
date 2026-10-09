# dish_correction — 菜品问题反馈

> 依据：菜品问题反馈流程（内容供给与维护闭环）。

**用途**：用户对菜品提出的**问题反馈**，由管理员核实后处置。**两类**：

| `type` | 语义 | 表单 |
|---|---|---|
| **`field`**（默认） | **信息有误** | 有表单：仅提交**改动项**快照 |
| **`gone`** | **已经下架** | **一键提交即可成立**；说明与图片为**可选补充** |

**对应实体**：`com.bjtufood.correction.entity.DishCorrection`

---

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | 反馈 ID |
| `type` | VARCHAR(16) | NO | `'field'` | KEY | **`field`=信息有误 / `gone`=已经下架** |
| `dish_id` | BIGINT | NO | — | KEY | 被反馈的菜品；**不设外键**（见下） |
| `user_id` | BIGINT | NO | 0 | | 提交人；**`0` = 匿名提交**（无 token、无归属；不设外键） |
| `name` | VARCHAR(64) | YES | NULL | | 改动后的菜名；**未改动则 NULL**。`gone` 型恒 NULL |
| `price` | INT | YES | NULL | | 改动后的现价（**单位分**）；未改动为 NULL。`gone` 型恒 NULL |
| `canteen_name` | VARCHAR(64) | YES | NULL | | 改动后的食堂名；未改动为 NULL。`gone` 型恒 NULL |
| `stall_name` | VARCHAR(64) | YES | NULL | | 改动后的档口名；未改动为 NULL。`gone` 型恒 NULL |
| `floor` | VARCHAR(16) | YES | NULL | | 改动后的楼层；未改动为 NULL。**归属档口**，采纳时写回 `stall.floor`。`gone` 型恒 NULL |
| `attributes` | JSON | YES | NULL | | 改动的描述属性（**仅改动的维度**）；未改动为 NULL。`gone` 型恒 NULL |
| `images` | JSON | YES | NULL | | 图片数组。**`field` 型**：改动后的图片，**≤5 张**；**`gone` 型**：**可选补充**，**≤3 张** |
| `note` | VARCHAR(200) | YES | NULL | | **补充说明**（≤200 字）。**仅 `gone` 型的选填补充**，其余 NULL |
| `status` | VARCHAR(16) | NO | `'pending'` | KEY | `pending` 待处理 / `adopted` 已采纳 / `rejected` 已拒绝 |
| `reply` | VARCHAR(1024) | YES | NULL | | 处理回复（**可空、≤600 字**）：采纳时**可传**（缺省用固定文案），不采纳时作为「处理说明」随回执下发（见 [B4](../func/web/B-UGC治理/B4-菜品问题反馈管理.md)） |
| `reject_reason` | VARCHAR(200) | YES | NULL | | 不采纳原因；不采纳时**必填** |
| `handled_at` | DATETIME | YES | NULL | | 处理时间 |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | |

---

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | |
| `idx_correction_dish` | `dish_id` | 普通 | 按菜品查反馈 |
| `idx_correction_status_created` | `status`, `created_at` | 复合 | **管理端筛选**（`WHERE status=? ORDER BY created_at` 无 filesort） |
| `idx_correction_type_dish` | `type`, `dish_id` | 复合 | **管理端「疑似下架」**：`WHERE type='gone' AND status='pending' GROUP BY dish_id` |

> 索引设计说明：**单列 `idx_correction_status` 已由复合索引 `idx_correction_status_created` 取代**（前缀即 `status`）。


## 关键设计

### 两类反馈的语义差别（`type` 存在的理由）

| | `field`（信息有误） | `gone`（已经下架） |
|---|---|---|
| 前提假设 | **这道菜存在，只是信息写错了** | **这道菜消失了** |
| 差异项列（name/price/…） | 存改动项，未改动为 NULL | **恒全 NULL**（无可对照的原值） |
| 承载信息 | `images`（≤5）+ `attributes` | **`note`（≤200 字，选填）+ `images`（≤3，选填）** |
| 处置动作 | 采纳写回 `dish` / `stall` / `dish_attribute_*` | **仅「下架该菜品」**（`dish.status='off'`），见下方红线 |
| 处置可逆性 | 采纳本身可人工修正 | **下架可重新上架**（评价完整保留） |

> ⚠️ **`gone` 型必须承载「选填补充」**（`note` / `images`）：单靠「一键提交」会抹平两类**对管理员完全不同**的判断：
> - 「**变成了别的菜**」⇒ 信息可补录，无需下架
> - 「**换窗口了**」（菜还在，只是位置变了）⇒ 属 `field` 型，改档口即可
> - 「**今天临时没供**」⇒ 也许明天就恢复，**是否该下架**需要判断
>
> ⇒ 口径为「**一键提交 + 选填补充**」：**提交成本仍为 0**（不填也能提交），同时让上述场景在处置时可见。

### 🔴 红线：`gone` 反馈的处置只能是「下架」，绝不能是「删除」

| 操作 | 语义 | 数据层 | 评价 | 可逆 |
|---|---|---|---|---|
| **下架** | 暂时不在售 | `status='off'` | **保留** | ✅ **可重新上架** |
| **删除** | 彻底移除 | **物理删除** | 🔴 **级联删除**（`ON DELETE CASCADE`） | ❌ **不可逆** |

> **为何写死**：`gone` 反馈**天然含误报**（用户可能看错窗口、菜品只是临时售罄）。若允许在该流程中删除，则「**一次误报 + 一次点错 = 该菜全部历史评价永久消失**」，而评价是**不可再生资产**。
>
> ⇒ **删除只允许在 [A3 菜品管理](../func/web/A-主数据维护/A3-菜品管理.md) 由管理员主动执行**（带二次确认，明示「将一并删除 N 条评价」）；**处置 `gone` 反馈的界面不得出现「删除」按钮**。

### ⚠️ 无「下架阈值」：`≥1 条即进待办，是否下架由人工决定

| 环节 | 规则 |
|---|---|
| **进入待办队列** | **≥1 条即进**（按 `user_id + dish_id` 去重后）—— 去重是**防单人刷队列**，**不是决策门槛** |
| **是否下架** | **管理员人工决定**；系统**不自动下架** |
| **驳回** | 必填 `reject_reason`，回执告知用户「经核实仍在售」 |

> **本表不设任何「阈值」列** —— 「≥N 独立用户」属**活跃度阈值**（判断信息新鲜度，与是否下架无关），与本表的处置流程**无关**。
> **依据**：进队列 ≠ 自动下架；管理员看到的是「有人反馈 + 可选说明/图片」，判断成本本就很低。设门槛只会让**用户的反馈石沉大海**。

### 局部提交 = 只存改动项
请求体**只传用户改过的字段**，未改动列在库中**留 NULL**。

因此本表**不是菜品副本**，而是**「改动项快照」**——管理端采纳时只需把非 NULL 列写回 `dish`，无需 diff 比对。

⚠️ 代价：**无法区分「用户把某字段清空」与「用户没改该字段」**。当前所有字段均不接受空值提交，故不构成问题；若将来允许清空某字段，需引入显式的变更标记。

### `dish_id` 不设外键 —— 菜品删除后反馈记录有意保留
| 若设 | 后果 |
|---|---|
| `ON DELETE CASCADE` | 删除菜品会**连带删掉反馈记录** —— 丢失「用户反馈过什么」的历史痕迹 |
| `ON DELETE RESTRICT` | **菜品永远删不掉** —— 只要有过反馈记录就被卡住 |

反馈记录是**独立的历史数据**（用户诉求的留痕），不是菜品的附属数据，因此两个选项都不可接受。

### 楼层归属档口，不归属菜品
`floor` 改动采纳时写回**目标档口**的 `stall.floor`，因此：

> ⚠️ **改动楼层会同时影响该档口下的所有菜品。**

这是刻意设计（楼层是场所属性），但采纳时须知晓连带影响。目标档口的解析规则：`stall_name` 改动则用新档口，未改动则用该菜当前所属档口。

### 不纠正原价
**无 `original_price` 快照列** —— 原价随促销波动、属运营口径，短期打折不构成稳定事实，因此不纳入菜品问题反馈范围。`price` 是唯一可菜品问题反馈的价格字段。

### 匿名提交以 `user_id = 0` 表示
与 [user_feedback](./user_feedback.md) 口径**统一**：`0` = **匿名提交**（无 token ⇒ 无归属），后台显示为「游客」。**不设外键、不用 NULL**（列 `NOT NULL DEFAULT 0`）；静默登录的游客**有 userId**，可收回执。

## 关联

| 方向 | 目标表 | 关系 |
|---|---|---|
| N→1 | `dish.id` | 被反馈菜品（**无外键**，见上） |
| N→1 | `user.id` | 提交人（**无外键**） |

`field` 型采纳时写入 `dish` / `stall` / `dish_attribute_*`，由应用层 `CorrectionServiceImpl` 编排；`gone` 型则调 `PUT /admin/dishes/{id}/status` 置 `off`（**不可走删除**）。

## 注意事项

- `attributes` 为 `JSON`（**与 `dish.attributes` 同类型**）；`images` 为 `JSON`（**与 `dish.images` 的 `VARCHAR(1024)` 不同** —— 反馈快照是短期数据，JSON 更直观）；其余改动项为标量列。
- `attributes` 快照**仅含改动的维度**，不是菜品属性的完整副本。
- ⚠️ **`attributes` 快照键 = 维度 ID**（JSON 内为字符串形态的 `dish_attribute_dimension.id`，与 `dish.attributes` 键形态一致），**值是用户提交的「中文」，不是取值 ID**：用户可自由输入字典之外的新值，而**提交反馈不写库**；采纳时由服务端按「维度 + 中文」查取值字典（命中即用、**未命中新建**）解析为取值 ID，再写回 `dish.attributes`（值域不同：中文 vs 取值 ID）。登记入口见 [dish_attribute_value](./dish_attribute_value.md)。
- ⚠️ **采纳写回必须「按维度合并」**（因本列只含**改动的维度**）：`dish.attributes` 的**未提交维度一律保留原值**，只覆盖本次采纳的维度；用户**清空某维度**（提交空数组）⇒ **删除该键**，不落空数组。整体覆盖会造成**其他维度静默丢失**。
- 采纳后**重算评分聚合**（若改动项涉及评分相关字段）。
- ⚠️ **`type` 存量数据一律为 `'field'`**（`NOT NULL DEFAULT 'field'`）⇒ 升级后既有反馈记录语义**不变**，**无需回填**。
- ⚠️ **`note` / `images` 对 `type=gone` 均为「可选补充」** ⇒ 库层**不设 NOT NULL**；`gone` 行的各差异项快照列**恒 NULL**（语义是「无可对照的原值」，**不是**「未改动」）。

---
