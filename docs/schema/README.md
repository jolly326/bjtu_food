# schema — 数据库设计

**设计真源**：本目录（`schema/*.md`）—— **DDL 结构以各表文档的「列定义 / 索引」为准**
**建库方式**：按各表文档的列定义**手工建表**；本仓库**不维护 `schema.sql` 初始化脚本**
**引擎**：InnoDB ｜ **字符集**：utf8mb4 / utf8mb4_general_ci ｜ **生产**：MySQL 5.7（TDSQL-C）

> 本目录是**库结构的唯一真源** —— 表 / 列 / 类型 / 约束 / 索引 / 表间关系均以各表文档为准。
> **变更流程**：先改对应表文档 → 再在数据库执行 `ALTER` / `CREATE` → 两者必须一致。

> **设计注记 / 已知事项**：
> - **管理端列表统一带 `updated_at`**：凡管理端出参 `updatedAt` 的表（`canteen` / `stall` / `dish_attribute_dimension` / `dish_attribute_value` / `dish_filter_view` / `report_reason`）均有 `updated_at` 列（`DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`）。`created_at` 只在确有「创建时间」语义的表保留：`dish` / `banner` / `user` / `review` / `user_feedback` / `dish_correction` / `notification` / `email_verification_code`。
> - **匿名提交 `user_id = 0`**：`dish_correction.user_id` 为 `BIGINT NOT NULL DEFAULT 0`（与 `user_feedback` 同口径），列注释写明「匿名提交为 0」。
> - **DDL 注释口径**：`user_feedback.sub_reason_id`（仅 `type='report'` 有效：举报原因 ID）、`user_feedback.reject_reason`（`status='handled'` 且结论不采纳时必填）、`stall.floor`（受控字典，值即汉字）。
> - **库表实况**：`dish.meal_type_id` 已建列与索引（`idx_dish_meal_type`）；`canteen` / `stall` 两表的实际列与其列定义一致（只含 `updated_at`，不含 `created_at`）。
> - ⚠️ **MySQL 版本**：生产为 MySQL 5.7（TDSQL-C）；变更脚本须用 5.7 可执行语法（避免 `JSON_TABLE` 等 8.0 特性）。
> - **`dish.images VARCHAR(1024)` 超长**：管理端 ≤5 张为硬校验；序列化后长度 > 1024 → `400`，禁止静默截断。
> - **名称唯一性并发口径**：`canteen.name` / `stall.name` 仅靠应用层查重，并发建档可写入重名 → 查重与写入须在同一事务；如需强约束可加唯一索引（先核查存量数据）。

## 表清单

| 表 | 用途 | 文档 |
|---|---|---|
| `dish` | 菜品主体 | [dish.md](./dish.md) |
| `dish_view_log` | 菜品浏览明细日志（30 天滚动窗口，**不参与排序**） | [dish_view_log.md](./dish_view_log.md) |
| `stall` | 档口（菜品的位置归属） | [stall.md](./stall.md) |
| `canteen` | 食堂（档口的上级归属） | [canteen.md](./canteen.md) |
| `dish_attribute_dimension` | 属性维度定义（表驱动；含**系统维度**「菜品种类」） | [dish_attribute_dimension.md](./dish_attribute_dimension.md) |
| `dish_attribute_value` | 属性取值字典（维度下的可选值；系统维度下 = **菜品种类字典**） | [dish_attribute_value.md](./dish_attribute_value.md) |
| `dish_filter_view` | 首页筛选视图（文案 / 顺序 / 显隐 + **筛选条件** + **排序口径**） | [dish_filter_view.md](./dish_filter_view.md) |
| `report_reason` | 举报原因字典（`GET /report-reasons` 的数据源） | [report_reason.md](./report_reason.md) |
| `banner` | 首页轮播运营位 | [banner.md](./banner.md) |
| `user` | 用户账号（学生） | [user.md](./user.md) |
| `user_violation` | 学生账号违规与处置留痕（**只追加**） | [user_violation.md](./user_violation.md) |
| `admin_account` | 管理端账号（与学生 `user` 身份体系**完全隔离**） | [admin_account.md](./admin_account.md) |
| `email_verification_code` | 邮箱验证码 | [email_verification_code.md](./email_verification_code.md) |
| `review` | 评价 | [review.md](./review.md) |
| `user_feedback` | 用户反馈与举报 | [user_feedback.md](./user_feedback.md) |
| `dish_correction` | 菜品问题反馈（`type`=field 信息有误 / gone 已经下架） | [dish_correction.md](./dish_correction.md) |
| `notification` | 站内通知 | [notification.md](./notification.md) |
| `admin_audit_log` | 管理端操作审计日志（**只追加**，覆盖全部管理端写操作） | [admin_audit_log.md](./admin_audit_log.md) |
| `security_alert` | 安全告警记录（**只追加**，推送的同时落库供面板回看） | [security_alert.md](./security_alert.md) |

**表清单以本目录为准（当前 **19 张**）** —— 新增表时同步补一份 `schema/<表名>.md` 并登记于此。

## 实体关系

```
canteen 1 ── n stall 1 ── n dish ── n review
                                    │     │
                                    │     └── n dish_correction
                                    └──── n user_feedback (related_type='review')

user 1 ── n review            (user_id)
user 1 ── n user_feedback     (user_id，**匿名提交为 `0`**，不设外键)
user 1 ── n notification      (user_id)
user 1 ── n user_violation    (user_id)
user 1 ─── email_verification_code (按 email 关联，非 user_id)


dish ── dish_attribute_dimension   经 attributes JSON 的键（= 描述维度 ID）关联（非外键）
dish_attribute_dimension 1 ── n dish_attribute_value   维度下的取值字典
dish.attributes ── dish_attribute_value   经 JSON 内的取值 ID 引用（描述维度取值，非外键）
dish.meal_type_id ── dish_attribute_value   经取值 ID 引用（**系统维度（菜品种类）** 的取值，非外键）
dish_filter_view.conditions ── dish_attribute_value   种类条件经取值 ID 引用（非外键）
user_feedback.sub_reason_id ── report_reason   举报原因 ID 引用（非外键）
```

## 库设计约定

### 管理端字段可达性（逐表核对）

> **口径**：除 `id`（内部标识）/ `created_at` / `updated_at`（**只读展示**，由 DB 时钟写入）/ 口令与身份凭据类（**不展示**）外，**每一列都必须在管理端可查看，且业务列可编辑** —— 不允许出现「库里有 `name` / `location`，管理端 UI 完全访达不到」的情况。核对日期见本节末。

| 表 | 管理端可达 | 刻意不可达（列 + 原因） |
|---|---|---|
| `canteen` | 名称 / 图片 / 地点 / 描述 / 排序位（A1 **详情抽屉**内可编辑）；档口数、更新时间为只读 | — |
| `stall` | 所属食堂 / 名称 / 楼层 / 窗口号 / 图片 / 地点 / 描述 / 排序位（A2 详情抽屉内可编辑）；菜品数、均分、更新时间为只读 | — |
| `dish` | 归属档口 / 名称 / 现价 / 原价 / 菜品种类 / 描述 / 图片 / 描述属性（A3 详情抽屉内可编辑）；评分数、浏览量为只读 | `view_count` —— **停写的只读历史列**（浏览量由 `dish_view_log` 的 30 天窗口承载），无消费方；`avg_rating` / `rating_count` —— **派生缓存列**，可编辑会与重算链路互相覆盖（见 [dish.md 派生与缓存列](./dish.md)） |
| `dish_attribute_dimension` | 维度名 / 取值类型（A4 详情抽屉内可编辑）；顺序位（拖拽）、取值数、关联菜品数、时间为只读 | 系统维度（`system = 1`，菜品种类）：**取值类型不可改、不可删** —— 其取值被 `dish.meal_type_id` 引用（保护规则见 [dish_attribute_dimension.md](./dish_attribute_dimension.md)） |
| `dish_attribute_value` | 取值名（A4「管理取值」抽屉内可编辑）；顺序位（拖拽）、引用菜品数、时间为只读 | `dimension_id` 由**所在维度**决定（改归属 = 换维度，非本行字段） |
| `dish_filter_view` | tab 文案 / 筛选条件 / 排序口径 / 启停 / 顺序位（全部可改） | — |
| `report_reason` | 中文标签 / 启停 / 顺序位 | — |
| `banner` | Banner 图 / 启停 / 顺序位；创建与更新时间为只读 | — |
| `user` | 账号 / 昵称 / 头像 / 状态 / 认证邮箱 / 时间（C2 详情抽屉内只读 + 启停 / 解绑 / 注销） | `openid` —— **身份凭据**，仅以布尔 `wechatBound` 出参；`email` —— 历史「邮箱注册」列，微信体系**无写入点**（恒 NULL）；口令类列本表不存在（管理端口令在 `admin_account`） |
| `review` | 作者 / 菜品 / 评分 / 正文 / 配图 / 隐藏态 / 隐藏附注 / 时间（B1 详情抽屉 + 隐藏处置） | — |
| `user_feedback` | 类型 / 正文 / 配图 / 状态 / 回复 / 不采纳原因 / 举报原因 / 关联对象 / 处理与提交时间 | `sub` —— 仅历史 `suggestion` 存量的二级分类，**无现行写入点** |
| `dish_correction` | 类型 / 目标菜品 / 提交人 / **改动项快照**（经 `differences` 逐项对照）/ 补充说明 / 配图 / 状态 / 回复 / 不采纳原因 / 时间 | 快照列（`name` / `price` / `canteen_name` / `stall_name` / `floor` / `attributes` / `images`）以**差异对照**形态呈现，不单独作为可编辑字段（它们表达的是「用户提交了什么」而不是「菜品当前值」） |
| `security_alert` | 全列只读可达（C3 安全告警面板：时间 / 级别 / 类型 / 标题 / 来源 IP + 详情抽屉看明细与类型键） | —（**只追加表，面板不提供任何写入口** —— 能改告警的人就能抹掉自己的痕迹） |

**系统维护列**（无人工编辑入口，不属「管理端可达」口径）：

| 表.列 | 维护方 |
|---|---|
| `user.violation_count` / `user.muted_until` | 违规累积处置链路（机审命中 / 举报成立时自动写入；解封由处置到期自动恢复，人工介入用 `PUT /admin/users/{id}/status`） |
| `admin_account.role` / `credential_version` / `password_changed_at` | 直连库维护（角色）/ 账号自助端点（改密令、改密） |

**管理端无 CRUD 页面的表**（不属「字段可达」口径）：`notification`（由 client 只读）、`dish_view_log`（仅供 30 天窗口统计）、`email_verification_code`（短生命周期校验件）、`admin_account`（账号行由数据库与自助端点维护，页面不暴露其字段）、`user_violation`（只追加留痕，排查时按 `user_id` 直查）。

### 命名
| 对象 | 规则 | 例 |
|---|---|---|
| 表名 | 小写下划线，**单数** | `review`（非 `reviews`）、`user_feedback` |
| 列名 | 小写下划线 | `avg_rating`、`created_at` |
| 索引 | `idx_<表>_<用途>` / `uk_<表>_<列>` | `idx_dish_status`、`uk_user_openid` |
| 枚举列 | 存**可读字符串**而非 TINYINT | `status='on'`（非 `status=1`） |

### 类型约定
| 场景 | 约定 | 理由 |
|---|---|---|
| **金额** | `INT`，**单位「分」** | 避免浮点误差；12.00 元存 `1200` |
| **评分** | `DECIMAL(3,2)` | 保留两位小数 |
| 计数 | `INT NOT NULL DEFAULT 0` | — |
| 图片 / 数组 | `VARCHAR` 或 `JSON` 存序列化字符串 | 见下 |
| 布尔 | `TINYINT` | 全库仅四处：`review.is_hidden`、`notification.is_read`、`dish_filter_view.enabled`、`dish_attribute_dimension.system`；其余状态一律字符串枚举 |
| 时间 | `DATETIME`，`NOT NULL DEFAULT CURRENT_TIMESTAMP` | 时区 Asia/Shanghai |
| 更新时间 | `ON UPDATE CURRENT_TIMESTAMP` | 免去应用层维护 |

### 时间戳写入来源：唯一来源 = **DB 时钟**

> **裁决（技术负责人拍板）**：`created_at` / `updated_at` **一律由 MySQL 写入** —— INSERT 走 `DEFAULT CURRENT_TIMESTAMP`，UPDATE 走 `ON UPDATE CURRENT_TIMESTAMP`。**应用层（Java）不写这两列**，也不用 MyBatis-Plus 自动填充。

**为什么是 DB 时钟**（三选一，理由按权重排序）：

1. **现状即约定**：全库 14 张含 `updated_at` 的表**全部**已带 `ON UPDATE CURRENT_TIMESTAMP`（2026-10-06 直连生产库 `information_schema.COLUMNS.EXTRA` 逐表核对），与上表「免去应用层维护」同一口径；改走应用时钟等于推翻既有列定义。
2. **应用时钟无护栏、必漂**：应用侧写作有 3 种形态，且**同一种调用形态会落到不同来源** —— `updateById(局部实体)`（实体 `updatedAt` 为 null）触发 MP 填充 = JVM 时钟；`updateById(已加载实体)`（`updatedAt` 非 null，`strictFill` 只补 null）**不触发**填充 = 落回 DB 时钟；`update(null, wrapper)` 无实体参数、**根本不触发**填充 = DB 时钟。要靠「记得写 `.set(…UpdatedAt, now())`」维持一致，未来新增一条 wrapper 更新即静默分叉。
3. **漏写即正确**：DB 时钟是兜底即正确；应用时钟漏写一处就退化为 DB 时钟（不报错、不告警），属于最坏的一类不一致。

**⚠️ 自动填充与 DB `ON UPDATE` 并存会发生什么**：UPDATE 语句中**显式 SET 该列时 `ON UPDATE` 失效**（显式赋值优先），故填充一旦触发，实际来源即翻转为 JVM 时钟。两者**不会冲突或报错**，但会让「同一列两个来源」静默成立 —— 这正是必须把填充一并移除、而不是只移除 `UserServiceImpl` 那一行的原因。

**因此移除的应用层写入点**（本节只记口径）：`common/persistence/MybatisMetaObjectHandler` 的 `insertFill` / `updateFill`；`auth/service/impl/UserServiceImpl#updateStatus` 的 `.set(User::getUpdatedAt, …)`。

**⚠️ 硬约束：实体时间字段不得带 `fill` 注解**（`@TableField(fill = FieldFill.INSERT / INSERT_UPDATE)`，或任何使 `TableFieldInfo#isWithUpdateFill()` 为真的配置）。

带上 `fill` 后 MyBatis-Plus 会**无条件**把该列写进 UPDATE 的 `SET` 子句（跳过 `NOT_NULL` 判断）：填充为空 ⇒ 显式 `SET <时间列> = NULL` ⇒ 写库报 `Column 'updated_at' cannot be null`（管理端表现为所有写操作 `400`「数据冲突」），同时**显式赋值会覆盖 DB 的 `ON UPDATE` 并使其失效** —— 列的两套来源瞬时成立。

正确形态 = 时间字段**只声明类型**，null 时不参与 `SET`，由库时钟兜底：

```java
/** 更新时间（DB 时钟：UPDATE 由 ON UPDATE CURRENT_TIMESTAMP 维护，应用层不写、不填充） */
@Schema(description = "更新时间")
private LocalDateTime updatedAt;
```

护栏：`server/src/test/java/com/bjtufood/common/persistence/EntityTimeFieldContractTest`（注解层扫描全部 `@TableName` 实体 + 运行时断言时间属性的 `SET` 片段仍被 `<if test="… != null">` 包裹）。

**语义边界（写下来避免误读）**：

- `ON UPDATE` 仅在**行确实发生变化**时触发 ⇒ 幂等写（如把 `status` 设为原值）**不刷新** `updated_at`。这正是「更新时间」应有语义，管理端「最近改动」排序据此才准确。
- 任何列的变化都会刷新它，**含派生缓存列**：`DishMapper.recalcRatingBySubquery` 重算 `avg_rating` / `rating_count` 会刷新 `dish.updated_at` ⇒ 管理端菜品列表会被评价活动重排（见 [dish.md](./dish.md)）。
- **前提是 JVM 与 DB 时钟同源**：容器 `TZ=Asia/Shanghai`（`server/Dockerfile`）+ JDBC `serverTimezone=Asia/Shanghai`。若更换基础镜像或去掉该 `ENV`，`created_at`（DB 时钟）与 `DateTimeUtil.now()`（`Asia/Shanghai` 硬编码，用于验证码等业务字段）会再度分叉。
- **未证明项**：本仓单测无真库（未引入 H2 / Testcontainers），`ON UPDATE` 属 DB 层行为**测试无法断言**。本约定由「唯一写入方是 DB」保证，而**不是**由断言保证 —— 真库并发 / 刷新行为的人工验证仍需执行。

### JSON 列的取舍
**能用 JSON 就不建关联表**，但需满足两条：

1. 该数据**只整体读写**，不需要按其内部元素检索或聚合
2. 元素之间**无独立生命周期**（不单独被引用、被统计）

符合的两处：
- `dish.images` —— 图片只是有序列表，无单张语义
- `dish.attributes` —— 属性值 = **取值 ID**（引用 `dish_attribute_value`）；只整体读写、不按内部元素检索 ⇒ 仍用 JSON 列

> 说明：取值本身有独立生命周期（取值字典），但「菜品 ↔ 取值」的**指派**由菜品整体持有、不单独检索；按取值计数属低频管理操作（扫描可接受）。若将来需**按属性筛选菜品**，应改用关联表（登记为演进预留）。

**不符合而建表**的对照：`stall` / `canteen` 虽只被引用，但它们有独立的录入与排序语义，因此是独立表。

### 索引策略
| 类型 | 用于 |
|---|---|
| 复合索引 | 覆盖「过滤列 + 排序列」，如 `idx_review_dish_visible(dish_id, is_hidden, created_at)` |
| 唯一索引 | 业务唯一性：`uk_user_openid` / `uk_user_username` / `uk_user_email`（`user`）、`uk_review_user_dish`（`review`）。⚠️ `dish_attribute_value` 的「同维度下 `label` 唯一」由**应用层**保证（**不建唯一索引**，见 [dish_attribute_value.md](./dish_attribute_value.md)）；`dish_filter_view` **无唯一约束**（视图由 ID 标识，文案可重复） |

> ⚠️ **计算表达式的 `ORDER BY` 无法用索引** —— 复合索引只能减少参与排序的行数，不能消除排序（必然 filesort）。
> 首页主列表 / 猜你喜欢的 `ORDER BY CRC32(CONCAT(seed,'-',id)), d.id` 即此类：现状为**已核实的已知取舍**，触发条件见 [README「已知取舍」](../README.md)。
> 同理，**前缀通配 `%kw%` 模糊匹配无法用索引**。这两项是各自独立的性能约束，登记在对应功能文档。
> 浏览量由 [`dish_view_log.md`](./dish_view_log.md) 的 **30 天滚动窗口**承载，**不参与排序**；各视图排序口径见 [`dish_filter_view.sort_kind`](./dish_filter_view.md)。

### 软状态
状态一律用 `VARCHAR` 而非枚举类型或 TINYINT：

| 表.列 | 取值 | 含义 |
|---|---|---|
| `dish.status` | `on` / `off` | 在售 / 已下架 |
| `user.status` | `active` / `disabled` / `deleted` | 正常 / 禁用 / 已注销 |
| `banner.status` | `on` / `off` | 启用 / 停用 |
| `report_reason.status` | `on` / `off` | 启用 / 停用 |
| `user_feedback.status` | `pending` / `handled` | 待处理 / 已处理（**「不采纳」不是状态**：由 `status='handled'` + `reject_reason` 非空派生） |
| `dish_correction.status` | `pending` / `adopted` / `rejected` | 待处理 / 已采纳 / 已拒绝 |
| `user_violation.action` | `counted` / `warn` / `mute_24h` / `mute_7d` / `ban` | 违规处置动作（**是「本次触发的动作」而非账号状态**；账号当前是否限言看 `user.muted_until`） |
| `security_alert.severity` | `info` / `warn` / `critical` | 告警级别（由 `alert_type` 决定，非调用方自选；中文标签另有快照列） |

> `dish.status = 'off'`（下架）对公开接口**等价于不存在**（返回 `4001`）。

### 外键与级联

**全库仅两条外键**，其余关系由应用层维护：

| 关系 | 级联方式 | 维护方 |
|---|---|---|
| `review.dish_id` → `dish.id` | `ON DELETE CASCADE` | 应用层 `ReviewDishCascadeListener` 先删（同步事务内），外键兜底 |
| `notification.user_id` → `user.id` | `ON DELETE CASCADE` | 应用层 `NotificationAccountCleanListener` 硬删，外键兜底 |

**刻意不设外键的三处**：

| 关系 | 原因 |
|---|---|
| `dish_correction.dish_id` | 菜品删除后反馈记录**有意保留**（历史痕迹，非附属数据）。CASCADE 会删掉它们；RESTRICT 会让菜品删不掉 |
| `review.user_id` 等指向 `user` | `user` 行**永不物理删除**（注销为匿名化），加约束无实际收益 |
| `dish.stall_id` | `stall` 是**管理端维护的实体**，有独立的合并 / 清理语义，约束化会干扰管理操作 |

> 两级方向一致（都删）时，应用层 + 外键是**冗余防护**：正常路径下应用层已删完，外键命中 0 行；只有应用层失效时外键才兜底。

## 变更流程

1. **先改本目录对应表文档**（列 / 索引 / 约束）—— 文档是唯一真源
2. **再在数据库执行**对应 `ALTER` / `CREATE`（表结构变更须直连远程库执行，凭据取 `server/.env`）
3. 两边必须一致；**发现不一致时以数据库实况为准并立即回写文档**
4. 若涉及出参字段 → 同步 [api/](../api/) 并刷新 `client/openapi.json`

> ⚠️ **禁止直连 `ALTER` 而不同步文档** —— 文档是唯一真源，否则新环境无法重建出相同结构。
>
> ⚠️ **本仓库不维护 `schema.sql` 初始化脚本** —— 新环境建库须按各表文档手工建表。


