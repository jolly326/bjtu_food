# schema — 数据库设计

**设计真源**：本目录（`schema/*.md`）—— **DDL 结构以各表文档的「列定义 / 索引」为准**
**建库方式**：按各表文档的列定义**手工建表**；本仓库**不维护 `schema.sql` 初始化脚本**
**引擎**：InnoDB ｜ **字符集**：utf8mb4 / utf8mb4_general_ci ｜ **生产**：MySQL 5.7（TDSQL-C）

> 本目录是**库结构的唯一真源** —— 表 / 列 / 类型 / 约束 / 索引 / 表间关系均以各表文档为准。
> **变更流程**：先改对应表文档 → 再在数据库执行 `ALTER` / `CREATE` → 两者必须一致。

> **设计注记 / 已知事项**：
> - **管理端统一带 `updated_at`**：凡管理端出参 `updatedAt` 的表（`canteen` / `stall` / `dish_category` / `dish_attribute_dimension` / `dish_attribute_value` / `dish_filter_view` / `feedback_option` / `report_option`；`admin` 经 `GET /admin/auth/me` 出参 `updatedAt`）均有 `updated_at` 列（`DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`）。`created_at` 只在确有「创建时间」语义的表保留：`dish` / `banner` / `user` / `review` / `feedback` / `report` / `correction` / `moderation_task` / `notification` / `email_verification_code`。
> - **三张用户提交表只存提交事实**：`feedback` / `report` / `correction` 均**无状态列、无 `updated_at`**，「是否已处置 + 结论 + 处置文案 + 处置人 + 处置时间」外置在 [moderation_task](./moderation_task.md)（多态键 `(type, type_id)`，1:1 挂靠）。
> - **`feedback.user_id` / `report.user_id` / `correction.user_id` 恒为真实用户**：三列均为 `BIGINT NOT NULL`，各带外键（`fk_feedback_user` / `fk_report_user` / `fk_correction_user` → `user(id)`，**均无级联**）—— 三张工单的提交入口一律要求登录态。
> - **告警不落库**：安全告警经 webhook 推送（`ALERT_WEBHOOK_URL` / `ALERT_WEBHOOK_SECRET`），拦截行为（登录失败锁定 / ID 枚举拦截）保留；库内**无告警表**。
> - **管理端不留操作日志**：`/admin/**` 的写操作不留库内审计记录（库内**无审计表**）；处置留痕落在 [moderation_task](./moderation_task.md) 的 `handled_by` / `handled_at`。
> - **字典表无排序列**：`dish_category` / `dish_attribute_dimension` / `dish_attribute_value` / `feedback_option` / `report_option` / `dish_filter_view` 一律按 `id` 升序（唯一保留排序位的是 `banner.sort_order`）。
> - **`dish_filter_view` 由 `category_id` 驱动**：`NULL` = 「为你推荐」（不按种类筛），非 NULL ⇒ 后端按该种类筛菜品；视图不持有筛选条件语言与排序口径。
> - **DDL 注释口径**：`moderation_task.reply_content`（处置文案与不采纳原因**同列**）、`stall.floor`（受控字典，值即汉字）。
> - **库表实况**：`dish.category_id` 与索引 `idx_dish_category`、`dish_category`（6 条种子）、`dish_filter_view`（**7 行** = 1 推荐 + 6 种类，与 `dish_category` 种子一一对应）均与本文档列定义一致；`canteen` / `stall` 两表的实际列与其列定义一致（只含 `updated_at`，不含 `created_at`）；`user` **25 行**；`feedback` **0 行** / `report` **1 行**（`review_id` 为 `NULL`）/ `correction` **1 行**；`moderation_task` **2 行**（`report/7`、`correction/2`，均 `pending`）；`notification` **0 行**。
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
| `dish_category` | 菜品种类字典（`dish.category_id` 的取值域） | [dish_category.md](./dish_category.md) |
| `dish_attribute_dimension` | 描述属性维度定义（表驱动） | [dish_attribute_dimension.md](./dish_attribute_dimension.md) |
| `dish_attribute_value` | 描述属性取值字典（维度下的可选值） | [dish_attribute_value.md](./dish_attribute_value.md) |
| `dish_filter_view` | 首页筛选视图（文案 / 显隐 + **筛选种类** `category_id`） | [dish_filter_view.md](./dish_filter_view.md) |
| `banner` | 首页轮播运营位 | [banner.md](./banner.md) |
| `user` | 用户账号（学生） | [user.md](./user.md) |
| `admin` | 管理端账号（与学生 `user` 身份体系**完全隔离**） | [admin.md](./admin.md) |
| `email_verification_code` | 邮箱验证码 | [email_verification_code.md](./email_verification_code.md) |
| `review` | 评价 | [review.md](./review.md) |
| `feedback` | 意见反馈（用户提交事实） | [feedback.md](./feedback.md) |
| `report` | 评价举报（用户提交事实） | [report.md](./report.md) |
| `correction` | 菜品问题反馈（`type`=field 信息有误 / gone 已经下架） | [correction.md](./correction.md) |
| `moderation_task` | 管理端处置任务（三类工单的处置状态机与处置文案真源） | [moderation_task.md](./moderation_task.md) |
| `feedback_option` | 反馈类型字典（`GET /feedback-options` 的数据源） | [feedback_option.md](./feedback_option.md) |
| `report_option` | 举报原因字典（`GET /report-options` 的数据源） | [report_option.md](./report_option.md) |
| `notification` | 站内通知 | [notification.md](./notification.md) |

**表清单以本目录为准（当前 **20 张**）** —— 新增表时同步补一份 `schema/<表名>.md` 并登记于此。

## 实体关系

```
canteen 1 ── n stall 1 ── n dish ── n review
                                    │        │
                                    │        └── 1:1 report   (review_id，**外键 `fk_report_review`**，ON DELETE SET NULL)
                                    └── n correction          (dish_id，非外键)

user 1 ── n review            (user_id，无外键)
user 1 ── n feedback          (user_id，**外键 `fk_feedback_user`**，无级联)
user 1 ── n report            (user_id，**外键 `fk_report_user`**，无级联)
user 1 ── n correction        (user_id，**外键 `fk_correction_user`**，无级联)
user 1 ── n notification      (user_id，**外键 `fk_notification_user`**，`ON DELETE CASCADE`)
user 1 ─── email_verification_code (按 email 关联，非 user_id)

admin 1 ── n moderation_task  (handled_by，**外键 `fk_task_handled_by`**，无级联)
admin （独立账号体系，与 user 无关系）

feedback 1 ── 1 moderation_task (type='feedback'，多态键 `(type, type_id)`，非外键)
report   1 ── 1 moderation_task (type='report'，同上)
correction 1 ── 1 moderation_task (type='correction'，同上)

feedback.option_id ── feedback_option   反馈类型经类型 ID 引用（**外键 `fk_feedback_option`**，无级联）
report.option_id ── report_option       举报原因经原因 ID 引用（**外键 `fk_report_option`**，无级联）
notification 与 moderation_task 无结构关联（触发源多域，文案各自快照）

dish ── dish_category   经 dish.category_id 引用种类 ID（N→1 单值，非外键）
dish ── dish_attribute_dimension   经 attributes JSON 的键（= 描述维度 ID）关联（非外键）
dish_attribute_dimension 1 ── n dish_attribute_value   维度下的取值字典
dish.attributes ── dish_attribute_value   经 JSON 内的取值 ID 引用（描述维度取值，非外键）
dish_filter_view.category_id ── dish_category   视图筛选的种类经种类 ID 引用（**外键 `fk_filter_view_category`**，无级联）
```

## 库设计约定

### 管理端字段可达性（逐表核对）

> **口径**：除 `id`（内部标识）/ `created_at` / `updated_at`（**只读展示**，由 DB 时钟写入）/ 口令与身份凭据类（**不展示**）外，**每一列都必须在管理端可查看，且业务列可编辑** —— 不允许出现「库里有 `name` / `location`，管理端 UI 完全访达不到」的情况。核对日期见本节末。

| 表 | 管理端可达 | 刻意不可达（列 + 原因） |
|---|---|---|
| `canteen` | 名称 / 图片 / 地点 / 描述（A1 **详情抽屉**内可编辑）；档口数、更新时间为只读 | — |
| `stall` | 所属食堂 / 名称 / 楼层 / 窗口号 / 图片 / 地点 / 描述（A2 详情抽屉内可编辑）；菜品数、均分、更新时间为只读 | — |
| `dish` | 归属档口 / 名称 / 现价 / 原价 / 菜品种类 / 描述 / 图片 / 描述属性（A3 详情抽屉内可编辑）；评分数、浏览量为只读 | `view_count` —— **停写的只读历史列**（浏览量由 `dish_view_log` 的 30 天窗口承载），无消费方；`avg_rating` / `rating_count` —— **派生缓存列**，可编辑会与重算链路互相覆盖（见 [dish.md 派生与缓存列](./dish.md)） |
| `dish_category` | 种类名（A3 分类抽屉内可编辑）；引用菜品数、时间为只读 | —（**无删除入口**：种类是 `dish.category_id` 的锚点，下线一律「改名 + 把菜品改到别的种类」，见 [dish_category.md](./dish_category.md)） |
| `dish_attribute_dimension` | 维度名 / 取值类型（A4 详情抽屉内可编辑）；取值数、关联菜品数、时间为只读 | — |
| `dish_attribute_value` | 取值名（A4「管理取值」抽屉内可编辑）；引用菜品数、时间为只读 | `dimension_id` 由**所在维度**决定（改归属 = 换维度，非本行字段） |
| `dish_filter_view` | tab 文案 / **筛选种类**（`categoryId`，`null` = 为你推荐）/ 启停（全部可改） | — |
| `feedback_option` | 类型中文名 / 启停；引用次数（删除前判断）、更新时间为只读 | —（**无排序位**） |
| `report_option` | 原因中文名 / 启停；引用次数（删除前判断）、更新时间为只读 | —（**无排序位**） |
| `banner` | Banner 图 / 启停 / 顺序位；创建与更新时间为只读 | — |
| `user` | 账号（`wx_id`）/ 昵称（`name`）/ 头像 / 状态 / 认证邮箱（`email`）/ 时间（C2 详情抽屉内只读 + 启停 / 解绑 / 注销） | `openid` —— **身份凭据**，仅以布尔 `wechatBound` 出参；口令类列本表不存在（管理端口令在 `admin`） |
| `review` | 作者 / 菜品 / 评分 / 正文 / 配图 / 隐藏态 / 隐藏附注 / 时间（B1 详情抽屉 + 隐藏处置） | — |
| `feedback` | 提交人 / 类型（`optionId` + 字典中文名）/ 正文 / 配图 / 提交时间 + 处置结果（B2 反馈列表与处置抽屉） | —（全部列为提交事实或处置结果，均为只读呈现） |
| `report` | 举报人 / 举报原因（字典中文名）/ 被举报评价摘要与当前隐藏态 / 「被举报评价已删除」标记 / 提交时间 + 处置结果 + 是否联动隐藏（B3 举报列表与处置抽屉） | — |
| `correction` | 类型 / 目标菜品 / 提交人 / **改动项快照**（经 `differences` 逐项对照）/ 补充说明 / 配图 / 提交时间 + 处置结果（B4 列表、详情与处置抽屉） | 快照列（`name` / `price` / `canteen_name` / `stall_name` / `floor` / `attributes` / `images`）以**差异对照**形态呈现，不单独作为可编辑字段（它们表达的是「用户提交了什么」而不是「菜品当前值」） |
| `moderation_task` | 处置状态 / 结论 / 处置文案 / 处置人（`handledByName`）/ 处置时间 —— **经三类工单的列表与处置抽屉呈现**，不单独成页 | `type` / `type_id` —— **多态挂靠键**（工单行由它定位，属内部标识；因此 `reply_title` / `reply_content` / `handled_by` / `handled_at` **只在处置时写入，不提供编辑入口**：结论一经归档不可改写） |

> **唯一放宽：UGC 提交事实与已归档结论一律「只读呈现」** —— `feedback` / `report` / `correction` 的列是「用户提交了什么」，`moderation_task` 的 `reply_*` / `handled_*` 是「管理员当时怎么处置的」，业务上**不允许改写**（否则历史不可追溯）。口径要求的「可编辑」指的是「不允许访达不到」，而这三类列在列表与抽屉内**全部可见**。

**系统维护列**（无人工编辑入口，不属「管理端可达」口径）：

| 表.列 | 维护方 |
|---|---|
| `admin.role` / `credential_version` / `password_changed_at` | 直连库维护（角色）/ 账号自助端点（改密码、改密） |

**管理端无 CRUD 页面的表**（不属「字段可达」口径）：`notification`（由 client 只读）、`dish_view_log`（仅供 30 天窗口统计）、`email_verification_code`（短生命周期校验件）、`admin`（账号行由数据库与自助端点维护，页面不暴露其字段）。

### 命名
| 对象 | 规则 | 例 |
|---|---|---|
| 表名 | 小写下划线，**单数** | `review`（非 `reviews`）、`feedback`（非 `feedbacks`） |
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
| 布尔 | `TINYINT` | 全库仅三处：`review.is_hidden`、`notification.is_read`、`dish_filter_view.enabled`；其余状态一律字符串枚举 |
| 时间 | `DATETIME`，`NOT NULL DEFAULT CURRENT_TIMESTAMP` | 时区 Asia/Shanghai |
| 更新时间 | `ON UPDATE CURRENT_TIMESTAMP` | 免去应用层维护 |

### 时间戳写入来源：唯一来源 = **DB 时钟**

> **裁决（技术负责人拍板）**：`created_at` / `updated_at` **一律由 MySQL 写入** —— INSERT 走 `DEFAULT CURRENT_TIMESTAMP`，UPDATE 走 `ON UPDATE CURRENT_TIMESTAMP`。**应用层（Java）不写这两列**，也不用 MyBatis-Plus 自动填充。

**为什么是 DB 时钟**（三选一，理由按权重排序）：

1. **现状即约定**：全库 **12 张**含 `updated_at` 的表**全部**已带 `ON UPDATE CURRENT_TIMESTAMP`（直连生产库 `information_schema.COLUMNS.EXTRA` 逐表核对），与上表「免去应用层维护」同一口径；改走应用时钟等于推翻既有列定义。
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

**不符合而建表**的对照：`stall` / `canteen` 虽只被引用，但它们有独立的录入与维护语义，因此是独立表。

### 索引策略
| 类型 | 用于 |
|---|---|
| 复合索引 | 覆盖「过滤列 + 排序列」，如 `idx_review_dish_visible(dish_id, is_hidden, created_at)` |
| 唯一索引 | 业务唯一性：`uk_user_openid` / `uk_user_wx_id`（`user`）、`uk_review_user_dish`（`review`）、`uk_admin_username`（`admin`）、`uk_task_type_target`（`moderation_task`：多态挂靠键 → 工单 1:1）。⚠️ 字典类的「同表 / 同维度下 `name` 唯一」由**应用层**保证（**不建唯一索引**，见 [dish_attribute_value.md](./dish_attribute_value.md)）；`dish_filter_view` **无唯一约束**（视图由 ID 标识，文案可重复）；举报去重（同一用户对同一评价）亦由**应用层**保证（`review_id` 可空，唯一索引不约束 `NULL` 行） |

> ⚠️ **计算表达式的 `ORDER BY` 无法用索引** —— 复合索引只能减少参与排序的行数，不能消除排序（必然 filesort）。
> 首页主列表 / 猜你喜欢的 `ORDER BY CRC32(CONCAT(seed,'-',id)), d.id` 即此类：现状为**已核实的已知取舍**，触发条件见 [README「已知取舍」](../README.md)。
> 同理，**前缀通配 `%kw%` 模糊匹配无法用索引**。这两项是各自独立的性能约束，登记在对应功能文档。
> 浏览量由 [`dish_view_log.md`](./dish_view_log.md) 的 **30 天滚动窗口**承载，**不参与排序**；筛选视图只决定「看哪些菜品」（[`dish_filter_view.category_id`](./dish_filter_view.md)），视图列表的端上顺序按 `id` 升序。

### 软状态
状态一律用 `VARCHAR` 而非枚举类型或 TINYINT：

| 表.列 | 取值 | 含义 |
|---|---|---|
| `dish.status` | `on` / `off` | 在售 / 已下架 |
| `user.status` | `active` / `disabled` / `deleted` | 正常 / 禁用 / 已注销 |
| `banner.status` | `on` / `off` | 启用 / 停用 |
| `feedback_option.status` | `on` / `off` | 启用 / 停用 |
| `report_option.status` | `on` / `off` | 启用 / 停用 |
| `correction.type` | `field` / `gone` | 信息有误 / 已经下架（**端上行为分支**：决定换哪套表单与哪条处置路径） |
| `moderation_task.status` | `pending` / `handled` | 待处置 / 已处置 |
| `moderation_task.outcome` | `handled` / `rejected` | 通过 / 不采纳（`status='handled'` 时必填，`pending` 恒 NULL；**「不采纳」是结论而非状态**） |

> `dish.status = 'off'`（下架）对公开接口**等价于不存在**（返回 `4001`）。

### 外键与级联

**全库十条外键**，其余关系由应用层维护：

| 关系（外键名） | 级联方式 | 维护方 |
|---|---|---|
| `review.dish_id` → `dish.id`（`fk_review_dish`） | `ON DELETE CASCADE` | 应用层 `ReviewDishCascadeListener` 先删（同步事务内），外键兜底 |
| `notification.user_id` → `user.id`（`fk_notification_user`） | `ON DELETE CASCADE` | 应用层 `NotificationAccountCleanListener` 硬删，外键兜底 |
| `feedback.user_id` → `user.id`（`fk_feedback_user`） | **无级联** | 纯引用完整性兜底：提交入口恒带登录态，`user_id` 必指向真实用户；`user` 行永不物理删除 ⇒ 无级联需求 |
| `report.user_id` → `user.id`（`fk_report_user`） | **无级联** | 同 `feedback.user_id` |
| `correction.user_id` → `user.id`（`fk_correction_user`） | **无级联** | 同 `feedback.user_id` |
| `feedback.option_id` → `feedback_option.id`（`fk_feedback_option`） | **无级联** | 纯引用完整性兜底：字典项有独立生命周期，**被引用项禁止删除**（应用层计数拦截）⇒ 无级联需求 |
| `report.option_id` → `report_option.id`（`fk_report_option`） | **无级联** | 同 `feedback.option_id` |
| `report.review_id` → `review.id`（`fk_report_review`） | **`ON DELETE SET NULL`** | 评价被物理删除时**置 NULL**（举报行保留，显式表达「被举报评价已删除」）—— **刻意不级联**：举报是可追溯的诉求留痕，不随被举报内容消失 |
| `moderation_task.handled_by` → `admin.id`（`fk_task_handled_by`） | **无级联** | 纯引用完整性兜底：`admin` 行不做物理删除（账号行由库与自助端点维护）⇒ 无级联需求 |
| `dish_filter_view.category_id` → `dish_category.id`（`fk_filter_view_category`） | **无级联** | 纯引用完整性兜底：`category_id` 可空（`NULL` = 为你推荐），非 NULL 必须是既有种类；种类无删除入口 ⇒ 无级联需求 |

**刻意不设外键的四处**：

| 关系 | 原因 |
|---|---|
| `correction.dish_id` | 菜品删除后反馈记录**有意保留**（历史痕迹，非附属数据）。CASCADE 会删掉它们；RESTRICT 会让菜品删不掉 |
| `moderation_task.type_id` | **多态键**：目标列随 `type` 在 `feedback` / `report` / `correction` 三表间变化，单列无法指向多个父表 ⇒ 引用完整性由**提交事务**保证（工单行与任务行同时写入） |
| `review.user_id` 指向 `user` | `user` 行**永不物理删除**（注销为匿名化），加约束无实际收益 |
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


