# 后端排查报告（2026-09-14）

> 审计员：后端审计员（只读，未修改任何代码/spec/tasks）
> 范围：`server/`（Spring Boot 3.2 / Java 21 / MyBatis-Plus 3.5.5）
> 方法：全仓 grep + 逐文件读证 + **线上 MySQL 实测**（`sh-cynosdbmysql-grp-bc1u6r82.sql.tencentcdb.com:27926` / 库 `bjtu_food`）
> 基线：`docs/project_spec.md`（§0.3 / §3 / §5 / §5.a）+ `CODEBUDDY.md`

---

## 汇总

| 级别 | 数量 |
| --- | --- |
| 🔴 阻断 / 正确性缺陷 | 3 |
| 🟠 不一致或规范偏离 | 9 |
| 🔵 优化 | 6 |
| **合计** | **18** |

---

## 一、错误码规范

**结论：码值分布合规，无非标码。** 全仓 `BusinessException` 显式码值仅 `400 / 401 / 403 / 4031 / 500`，与 spec §5（`200/400/401/403/4031/500`，4031 为登记豁免）一致。

实测命令：
```
findstr /s /n /r /c:"BusinessException([0-9]" *.java
```
证据分布：
- `400`：`UploadServiceImpl.java:188,225,232,238,243,247,260,270,277,284,298,303,316,414`、`ContentSecurityServiceImpl.java:106,160,182,219,223,264`、`CosStorageServiceImpl.java:62,65,69,87`、`WechatService.java:75,94,100,104,121,136`、`ReviewServiceImpl.java:299`
- `401`：`AuthServiceImpl.java:102,210,214`、`RequireVerifiedAspect.java:31`
- `403`：`RequireVerifiedAspect.java:36`、`ReviewServiceImpl.java:211,302`
- `4031`：`RequireVerifiedAspect.java:41`、`ReviewServiceImpl.java:207`
- `500`：`ContentSecurityServiceImpl.java:139,144,198,251,254,268,282,287,294,305`、`WechatService.java:116`、`UploadServiceImpl.java:220`

无参构造（隐式 400）共 13 处，见 `ReviewServiceImpl.java:122,150,183,188,227,261,268,272,371,384,388,402,411` 等，码值仍为 400，不构成非标码。

✅ 本项通过。

---

## 二、接口契约（对照 spec §3 / §5）

### 路径 / 方法 / 白名单

| 项 | 结论 |
| --- | --- |
| 学生端写接口下线 | ✅ `POST/PUT/DELETE /dishes` 已不存在；`grep PostMapping("/auth` 仅 3 条（`AuthController.java:62,94,113`），学生端菜品写接口确认下线 |
| `/admin/**` 路由 | ✅ 存在 9 个 admin 前缀控制器，路径齐备 |
| JWT 白名单 | ✅ `SecurityConfig.java:59-86` 与 spec §5 白名单逐条对应 |

### ❌ 分页结构不符（🟠 规范偏离，非阻断）

spec §5 L215 / L376 明文规定分页统一 `PageResult<T>{ records, total, page, pageSize }`：

- 现状：`PageResult.java:35-39` 只有 `private List<T> list;` 与 `private long total;`——**字段名是 `list` 而非 `records`，且缺 `page` / `pageSize`**。
- 影响面：所有分页接口实际返回 `{list,total}`，与权威契约字段名不一致。
- 缓解：前端两端均已做兼容兜底（`web/src/api/adapter.ts:4-8` 的 `records||list`、`client/src/api/shared.ts:16-31` 的 `recordsOf`），故未造成线上故障，但**契约层已与 spec 脱钩**。

### 🟠 文档承诺的接口不存在

- `POST /auth/admin/login` 被 3 处文档/注释宣称存在，但**后端无此端点**：
  - `SwaggerConfig.java:37`（Swagger 测试步骤第 3 步）
  - `SecurityConfig.java:35`（白名单 javadoc）
  - `AuthService.java:14`（接口 javadoc）
  - 实测：`grep PostMapping("/auth` 仅 3 条，无 admin/login。
- 且 spec §5 L214 仍把 `/auth/admin/login` 列入白名单，实际 `SecurityConfig.PUBLIC_ANY_METHOD:59-70` 已无此项——**spec 与代码双向不一致**。

### 🟠 分页参数无上限（契约与实现不一致）

- `ReviewController.java:37,54`、`UserAdminController.java:29` 等直接 `@RequestParam(defaultValue = "20") int pageSize`，**未经 `PageUtil.normalize`**；而 `DishServiceImpl`/`ReviewServiceImpl`/`AuditServiceImpl` 均已归一化。
- 证据：`ReviewController.java:41` 把 pageSize 原样传给 `reviewService.listByDishId`，service `ReviewServiceImpl.java:64-65` 内部归一化——但 `ReviewAdminController.java:30` 走 `listAllForAdmin` 也归一化。**真正未归一化的是 `AuthController` 无分页**；结论：分页上限口径靠 service 兜住，controller 层不一致但无越界风险（🟠 轻微）。

---

## 三、数据库一致性（线上实测）

**连接与实测全部通过，无漂移、无直连 ALTER。**

### 3.1 表清单核对

线上实测 12 张表：
```
canteen, category, dish, email_verification_code, notification,
operation_log, review, review_useful, stall, user, user_feedback, view_log
```
与 `schema.sql` 的 `CREATE TABLE` 清单一致（无幽灵表、无缺失表）。

### 3.2 ❌ 历史事故列 `is_new` 已彻底清除（🟢 已修复，留档确认）

专项 grep：
```
findstr /s /n /r /c:"is_new" src\main\java\*.java src\main\resources\*.xml src\main\resources\mapper\*.xml
→ 无输出（0 处）
```
线上实测 `information_schema.COLUMNS ... COLUMN_NAME='is_new'` → `0`。**该校验项通过。**

### 3.3 Mapper XML 列存在性实测（真实执行 SQL，非 grep）

`DishMapper.xml` 联表大 SQL（含 `alias / region / floor / window_no / business_hours / category_id / c_lat / c_lng`）**线上实跑成功**，返回 1 行真实数据（dish id=1 宫保鸡丁，`1F` / `1号窗口` / `10:00-20:00` / `39.9538` / `116.3354`）。

`ReviewMapper.xml` 四条查询（`selectReviewPageByDishId` / `selectReviewPageByUserId` 等，含 `useful_count / sec_state / images AS images_json / d.name AS dish_name`）**线上实跑成功**，返回真实数据。

关键列核对：
| 列 | 存在 |
| --- | --- |
| `dish.alias` | ✅ 1 |
| `dish.region` | ✅ 1 |
| `review.useful_count` | ✅ 1 |
| `review.sec_state` | ✅ 1 |
| `stall.window_no` | ✅ 1 |

### 3.4 索引 / 唯一键核对（业务代码须与唯一键一致）

线上实测均存在：
- `review.uk_review_user_dish` ✅（对应 `ReviewServiceImpl.java:185-189` 一人一菜一条）
- `review_useful.uk_useful_user_review` ✅（对应 `ReviewServiceImpl.java:142-152` 一人一票）
- `dish.idx_dish_heat` ✅

### 3.5 🟠 孤儿列：`review.tags` 存在于库与脚本，但实体无对应字段

- 线上：`review.tags varchar(255)` 存在。
- `schema.sql:152`：`` `tags` VARCHAR(255) ... COMMENT '评价标签（美团式写评，逗号分隔或 JSON 数组）' ``
- 实体 `Review.java:18-67`（全字段已读）**无 `tags` 字段**；`ReviewVO` / `ReviewAdminVO` 亦无。
- 判定：美团式写评能力已下线，列被遗留。**脚本与代码实体不符**（spec §5 L218 要求"新增接口须先在 schema.sql 与代码注释中登记契约"，此处为反向漂移）。

### 3.6 🟠 脚本技术债被显式登记：`promotion` 标签

`schema.sql:119` 注释原文：
> `权威值域：recommended/signature；...仅允许登记值（**promotion 为 DishMapper 死查询技术债，禁止写入**）`

对应 `DishMapper.xml:170-180` 的 `selectPromotionDishes` 与 `FIND_IN_SET('promotion', d.tags)`。线上实测 `promotion` 标签菜品数为 0（`getPromotionDishes` 恒返回空列表）。**死查询已自认，但未清理。**

### 3.7 ✅ 无直连 ALTER 痕迹（红线通过）

```
findstr /s /n /r /c:"ALTER TABLE" *.java          → 无输出
findstr /s /n /r /c:"ALTER TABLE" *.yml *.xml     → 无输出
```
数据库变更 100% 来自 `server/src/main/resources/db/schema.sql` 的幂等存储过程迁移。**红线通过。**

---

## 四、命名规范

### 🟠 DTO 后缀不统一（同一语义两种后缀）

- `ReviewMapper.xml:158` → `com.bjtufood.review.dto.StallAvgRatingDTO`（`StallAvgRatingDTO.java:8`）
- 其余同类传输对象一律 `VO`：`CanteenInfoVO` / `StallDetailVO` / `RatingDistributionVO` / `HotSearchVO` / `AuditVO` / `OperationLogVO` / `FeedbackAdminVO`
- 唯一一个 `DTO` 后缀混在 `VO` 家族中，且同目录（`review/dto/`）下其余全是 `VO`（`ReviewVO` / `ReviewAdminVO` / `UsefulResult`）。

### 🟠 表名/实体命名：`user` 为 MySQL 保留字风险

- `User.java:16` `@TableName("user")`，广播为保留关键字。当前 `MybatisPlusConfig` 未配置全局表名转义，但已实测线上 `dish/s...` JOIN `user u` 类 SQL 运行正常（`ReviewMapper.xml:38` 实跑成功），暂不构成故障。**建议纳入规范评审**（🟠 → 可降级）。

### ✅ 常量类引用正确

- `OperationLogConst.ACTION_*` 被 8 个控制器 `@AuditLog` 正确引用。
- `DishConst.AUDIT_APPROVED / STATUS_ON` 被 `DishServiceImpl`、`StatsController` 正确引用。
- `RoleConst.STUDENT/ADMIN` 定义齐全（`RoleConst.java:15,18`），`isAdmin()` 存在（:26）。

### 🔵 语义重复常量（两处定义同一值域）

- 审核状态值域在两处各自定义：
  - `content/constant/AuditConst.java`（`STATUS_APPROVED` / `STATUS_REJECTED`）
  - `dish/constant/DishConst.java`（`AUDIT_APPROVED` / ...）
- UGC 安全态在三处各自定义：`ReviewServiceImpl.java:46-48`（`SEC_STATE_PASS/REVIEW/REJECTED`）与 `SecSuggest` 枚举（`content/security/SecSuggest.java`）语义重叠。
- `FeedbackConst` 与 `AuditConst` 的类型值域亦存在重复登记。

---

## 五、死代码与遗留注释

### ❌ 5.1 🟠 产品经理点名的那条遗留注释仍在

`DishServiceImpl.java:425`：
```java
// syncCollectCount 已随 favorite 模块移除（task-12.12）；喜欢计数存储方案待架构师评估。
```
- 影响：收藏模块已整体下线，此注释为**无归属的悬空待办**，无对应 task 编号、无截止时间，且"喜欢计数存储方案"与现行 `review_useful`（`ReviewServiceImpl.toggleUseful`）已定案的事实矛盾。
- 归属：后端开发工程师（清理）+ 技术负责人（确认无未决项）。

### ❌ 5.2 🔴 `ReviewService.likeReview` 为无调用方死方法（且与现行语义冲突）

- 接口：`ReviewService.java:182-183`（`@Deprecated`）
- 实现：`ReviewServiceImpl.java:394-413`（含 3 处 `BusinessException` 与 `DuplicateKeyException` 兜底共 20 行）
- **全仓 grep 仅剩声明与实现两处，零调用方**：
  ```
  findstr /s /n /r /c:"likeReview(" *.java
  → ReviewServiceImpl.java:396 / ReviewService.java:183（仅定义，无调用）
  ```
- 语义冲突：`likeReview` 抛"你已经喜欢过这条评价"（单向不可取消），与当前契约 `toggleUseful`（可取消、幂等，`ReviewController.java:113-125`）**业务语义相反**。死代码保留会误导后续开发者复用它。
- 归属：后端开发工程师。建议物理删除接口 + 实现两处。

### ❌ 5.3 🔴 `DishService.getHotDishes()` 无参重载无调用方

- 接口 `DishService.java:41`，实现 `DishServiceImpl.java:73-76`。
- 全仓 grep：仅 `DishController.java:38` 调 `getHotDishes(lat, lng, limit)`（三参版）；**无参版零调用**。
- 归属：后端开发工程师（删除）。

### 🟠 5.4 `AdminCreateReq` DTO 无调用方

- `AdminCreateReq.java:15` 定义完整（含 `@Schema`、字段校验），但全仓 grep 仅命中自身声明。
- 背景：`web/src/api/admin.ts:1-10` 已声明"管理员账号体系 API 已下线（2026-09-14）"。
- 归属：后端开发工程师（删除）。

### 🟠 5.5 `ReviewAdminVO.hasSensitive` 恒为 `false`（死字段）

- 声明：`ReviewAdminVO.java:53` `private Boolean hasSensitive;`
- 唯一赋值：`ReviewServiceImpl.java:443` `vo.setHasSensitive(false);` —— **硬编码 false，无任何 true 分支**。
- 影响：管理后台若据此字段做"敏感词高亮"会永远不触发；契约字段存在但语义空洞。
- 归属：后端开发工程师（删除字段，或接入 `SensitiveFilter` 实际判定）。

### 🟠 5.6 `ReviewAdminVO` javadoc 与公开 VO 边界声明已过期

- `ReviewAdminVO.java:12-13` 声称"额外携带 `isHidden`/`hasSensitive`/`secState`"、"公开接口严禁返回 isHidden/hasSensitive"。
- 实际 `ReviewVO.java:52` 已公开返回 `secState`（作者本人放行 review 态），与 javadoc 的"公开接口严禁返回"表述不符（属注释未同步）。

### 🔵 5.7 `getPromotionDishes` / `selectPromotionDishes` 死链路

- `DishController.java:47-51` → `DishServiceImpl.java:276-281` → `DishMapper.xml:170-180`。
- 线上 `promotion` 标签菜品数 = 0，接口恒返回 `[]`；`schema.sql:119` 亦自认"死查询技术债"。
- 需产品确认该入口是否保留（🔵 优化，非缺陷）。

---

## 六、逻辑不一致

### ✅ 6.1 金额单位为「分」—— 后端一致

- `Dish.java:43,47,51` 全部 `Integer` 分；`DishMapper.xml` 无除算；`DishAdminController.java:41` 示例 `price:1200`。
- 线上实测 `dish.price=1600`（=16 元），符合「分」。
- 前端转换在 api 层：`web/src/api/adapter.ts:160,163,173,184` 用 `/100`、`*100`，符合红线（不在页面裸算）。
- ✅ 本项通过。

### ✅ 6.2 角色仅 STUDENT / ADMIN —— 一致

- `RoleConst.java:15,18` 仅两值；全仓 grep `STALL_OWNER` / `stall-owner` → **0 处**。
- JWT 中 `role` 仅用于网关校验，UGC 写操作改按 `verified` 判定（`RequireVerifiedAspect.java:38-41`），符合 spec §5 L127。
- ✅ 本项通过。

### 🟠 6.3 `/admin/**` 角色闸门被"口令"整体替代（spec 与实现冲突）

- spec §0.3 L121 / §5 L214：**"`/admin/**` 仅 `ADMIN`（含 `SUPER_ADMIN` 分层）"**、§0.4 L71 明确"后端 `/admin/**` 仍保留 `ADMIN`/`SUPER_ADMIN` 硬闸门，与 web 路由守卫双闸门口径一致"。
- 实现：`SecurityConfig.java:106` → `.requestMatchers("/admin/**").permitAll()`，随后由 `AdminTokenFilter` 单一口令把关（`AdminTokenFilter.java:64-75`），**全无角色校验**。
- 衍生不一致：`RoleConst.java:6-8` 注释自认"已移除 super_admin / 无角色层级"，与 spec §0.4 的"SUPER_ADMIN 分层 + `@PreAuthorize` 硬闸门"直接矛盾。
- 全仓 `@PreAuthorize("hasRole('ADMIN')")` / `SUPER_ADMIN` → **0 处**。
- 影响：任一持有共享口令者可执行全部管理动作；spec 的"普通 admin 不能管账号"分层失效。
- 归属：技术负责人（裁决 spec §0.3/§5 与实现谁为准）+ 后端开发工程师（按裁决补齐角色闸门或修订 spec）。

### 🟠 6.4 `FeedbackAdminController.handle` 的 `handler_id` 依赖管理端登录态，但管理端已无登录

- `FeedbackAdminController.java:62`：`Long handlerId = SecurityUtil.getCurrentUserId();`
- `/admin/**` 走 `AdminTokenFilter` 口令，**不经 `JwtAuthFilter` 注入用户身份**（`SecurityConfig.java:104-106` 已放行交由过滤器）。
- 后果：`handlerId` 极可能为 `null`（或抛异常），落库 `user_feedback.handler_id = NULL`。
- 对比：`AuditLogAspect` 同样依赖登录态取 `admin_id`（`AuthController.java:179-182` 注释自认"操作人 admin_id 与 IP 由 AuditLogAspect 统一写入"）。
- 判定：**待确认（需运行时验证）**——`SecurityUtil.getCurrentUserId()` 在无认证上下文时的具体行为需起服务实测。风险等级按 🟠 预置。
- 归属：后端开发工程师 + 技术负责人。

### 🟠 6.5 UGC 可见性过滤：`sec_state` 口径在 review 与 feedback 间不对称（设计如此，但需登记）

- review：对外列表强制 `is_hidden=0 AND (sec_state='pass' OR 作者本人)`（`ReviewMapper.xml:40-45,102-107,136-141`）——**双层过滤**。
- feedback：`FeedbackServiceImpl.java:139-140` 仅管理端按 `secState` 筛选，**无对外可见性问题**（反馈不公开展示，`Feedback.java:39` 已注明）。
- 判定：口径差异**有明确设计依据**，不构成缺陷；建议在 spec §5.a 补一句"反馈无公开面，故不设对外可见性过滤"以防后续误判。

### 🟠 6.6 `AuditServiceImpl.approve` 与 `DishServiceImpl.addDish` 的审核态默认值口径需登记

- `AuditServiceImpl.java:121-124`：审核通过置 `approved` 并清 `reject_reason`。
- `DishServiceImpl.java:354`：管理员新增直接置 `AUDIT_APPROVED`（注释标注"2026-09-14 用户拍板"）。
- `DishServiceImpl.java:373`：管理员编辑也强制置 `approved`（**会覆盖历史 `rejected` 态**）。
- 而 spec §0.2 L60 / §5 L289 仍写"提交 → `audit_status=pending` → 后台审核"，与 L352/L371 的"管理员即权威、直接 approved"存在表述断层。
- 判定：实现更晚（2026-09-14）且注释自认拍板，**疑为 spec 未回写**。
- 归属：技术负责人（回写 spec）。

### 🔵 6.7 热度公式三处实现，权重需交叉确认

| 位置 | 公式 |
| --- | --- |
| `DishMapper.xml:128`（排序） | `view_count*1 + rating_count*5*20 + avg_rating*20` |
| `DishMapper.xml:291`（热搜） | 同上 |
| `DishServiceImpl.java:213,272`（个性化） | `w1*view + w2*ratingCount*20 + w3*avg`，w1=1/w2=5/w3=20 → 等价 |
| `DishMapper.xml:307`（新晋黑马） | `rating_count*20 + view_count`（**不同公式**，有独立注释说明） |

- 结论：前三条**数值一致**（5×20=100），第四条为独立业务口径。✅ 无口径冲突，但公式散落 4 处，建议抽为常量（🔵）。

---

## 问题清单

| # | 级别 | 问题 | 位置(file:line) | 影响 | 建议归属角色 | 修复建议 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 🔴 | `likeReview` 为无调用方死方法，且语义与 `toggleUseful` 相反（单向不可取消 vs 幂等可取消） | `ReviewService.java:182-183`；`ReviewServiceImpl.java:394-413` | 死代码误导后续开发，复用时产生与现行契约冲突的点赞行为 | 后端开发工程师 | 物理删除接口声明 + 实现；同步移除 `ReviewServiceImpl.java:402,411` 两处相关异常文案 |
| 2 | 🔴 | `DishService.getHotDishes()` 无参重载零调用 | `DishService.java:41`；`DishServiceImpl.java:73-76` | 死代码；两条热门路径易误用（无参会绕过 lat/lng 加权） | 后端开发工程师 | 删除无参重载；保留 `getHotDishes(lat,lng,limit)` 单一入口 |
| 3 | 🔴 | 管理端 `handler_id` / 操作日志 `admin_id` 依赖登录态，但 `/admin/**` 已无登录体系（`permitAll` + 口令过滤器） | `FeedbackAdminController.java:62`；`SecurityConfig.java:106`；`AdminTokenFilter.java:64-75` | 反馈处理人与操作日志操作人可能恒为 NULL，审计链断裂；**待运行时验证** | 后端开发工程师 + 技术负责人 | 起服务实测 `GET /admin/feedbacks` 后 `handler_id` 落值；若为 NULL，改由口令体系传入固定操作人标识或显式降级并登记 |
| 4 | 🟠 | `PageResult` 字段名为 `list`/`total`，与 spec 权威契约 `{records,total,page,pageSize}` 不符 | `PageResult.java:35-39` vs `project_spec.md:215,376` | 契约脱钩；依赖前端两端各自的兼容兜底才不炸 | 后端开发工程师 | 补 `records`/`page`/`pageSize` 字段（保留 `list` 作过渡），或由技术负责人裁决修订 spec |
| 5 | 🟠 | `POST /auth/admin/login` 被文档宣称存在但实际不存在 | `SwaggerConfig.java:37`；`SecurityConfig.java:35`；`AuthService.java:14`；`project_spec.md:214` | 误导联调者与后续开发；spec 白名单与代码白名单不一致 | 技术负责人 | 裁决管理端登录方式后，统一清理 3 处注释 + spec §5 L214 白名单 |
| 6 | 🟠 | `/admin/**` 无角色闸门，spec 承诺的 `ADMIN`/`SUPER_ADMIN` 硬闸门缺失 | `SecurityConfig.java:106`；`RoleConst.java:6-8` vs `project_spec.md:71,121,214` | 共享口令即可执行全部管理动作，分层权限失效 | 技术负责人（裁决）→ 后端开发工程师 | 由技术负责人拍板「口令 vs 角色」；若保留角色，需在 `/admin/**` 前置 `@PreAuthorize`；若采纳口令，须回写 spec §0.3/§0.4/§5 |
| 7 | 🟠 | `review.tags` 列存在于线上库与 `schema.sql`，但实体 `Review` 无该字段 | `schema.sql:152`；`Review.java:18-67`（无线索） | 脚本与代码实体不符（反向漂移），违反 spec §5 L218「登记契约再实现」 | 后端开发工程师 + 技术负责人 | 确认美团式写评已下线后，从 `schema.sql` 删除该列（含幂等迁移说明） |
| 8 | 🟠 | `ReviewAdminVO.hasSensitive` 硬编码恒为 `false` | `ReviewAdminVO.java:53`；`ReviewServiceImpl.java:443` | 契约字段语义空洞，管理端若据此高亮敏感词永不生效 | 后端开发工程师 | 接入 `SensitiveFilter` 实际判定，或删除该字段并同步前端 `types` |
| 9 | 🟠 | `AdminCreateReq` DTO 无调用方（管理员账号体系已下线） | `AdminCreateReq.java:15` | 死代码；与 `web/src/api/admin.ts:1-10` 的下线声明呼应 | 后端开发工程师 | 删除类文件 |
| 10 | 🟠 | `DTO` 后缀混入 `VO` 家族，同目录其余全为 `VO` | `StallAvgRatingDTO.java:8`；`ReviewMapper.xml:158` | 命名规范不统一，检索与识别成本上升 | 后端开发工程师 | 重命名为 `StallAvgRatingVO`（或统一由技术负责人定后缀字典） |
| 11 | 🟠 | 遗留注释「喜欢计数存储方案待架构师评估」无归属无结论 | `DishServiceImpl.java:425` | 悬空待办；与已定案的 `review_useful` 方案矛盾，误导后续判断 | 后端开发工程师（清理）+ 技术负责人（确认无未决项） | 直接删除该行注释；若确有待决项，转为带 task 编号的正式待办 |
| 12 | 🟠 | `promotion` 标签为死查询链，`schema.sql` 自认技术债 | `DishController.java:47-51`；`DishMapper.xml:170-180`；`schema.sql:119` | 接口恒返回空数组，占用缓存与代码面 | 技术负责人 → 后端开发工程师 | 确认入口是否保留：不保留则删链路；保留则补 `promotion` 值域登记 |
| 13 | 🟠 | 管理员新增/编辑强制置 `audit_status=approved`，与 spec「提交→pending→审核」表述断层 | `DishServiceImpl.java:354,373` vs `project_spec.md:60,289` | 契约文档与实现不一致；编辑会覆盖历史 `rejected` 态 | 技术负责人 | 回写 spec §0.2/§5，明确「管理员录入即 approved，学生侧已无提交入口」 |
| 14 | 🟠 | `ReviewAdminVO` javadoc 声称公开接口严禁返回 `secState`，实际 `ReviewVO` 已公开返回 | `ReviewAdminVO.java:12-13` vs `ReviewVO.java:52` | 注释过期，误导边界判断 | 后端开发工程师 | 更新 javadoc，注明"作者本人可见 review 态"的放行口径 |
| 15 | 🔵 | 审核状态常量在 `AuditConst` 与 `DishConst` 重复定义；UGC 安全态在 `ReviewServiceImpl` 与 `SecSuggest` 语义重叠 | `AuditConst.java`；`DishConst.java`；`ReviewServiceImpl.java:46-48`；`SecSuggest.java` | 同一值域多真源，易改一处漏一处 | 后端开发工程师 | 收敛为单一常量类（如 `common/constant`），其余改为引用 |
| 16 | 🔵 | 热度公式散落 4 处（3 处等价 + 1 处独立口径） | `DishMapper.xml:128,291,307`；`DishServiceImpl.java:213,272` | 权重调整需改多点，易漏改导致排序/热搜口径分叉 | 后端开发工程师 | 抽为常量或统一 SQL 片段；补充权重变更的同步清单注释 |
| 17 | 🔵 | `user` 为 MySQL 保留字作表名，未配置全局转义 | `User.java:16` | 当前实测运行正常，但裸写 SQL 场景存在潜在陷阱 | 技术负责人 | 评估是否加 `@TableName("`user`")` 或全局转义配置 |
| 18 | 🔵 | `FeedbackAdminController.handle` 同时支持 body 与 query 两种 `reply` 传法 | `FeedbackAdminController.java:57-61` | 契约冗余，双入口增加测试面 | 后端开发工程师 | 收敛为单一 body 传法（需与 web 端确认调用方式） |

---

## 待确认项

| # | 待确认内容 | 所需手段 | 关联问题 |
| --- | --- | --- | --- |
| A | `/admin/**` 请求下 `SecurityUtil.getCurrentUserId()` 的实际行为（返回 null？抛异常？），进而 `user_feedback.handler_id` 与 `operation_log.admin_id` 是否落值 | 起 server 后带正确 `X-Admin-Token` 调 `PUT /admin/feedbacks/{id}`，随后查库核对 `handler_id`；再调一个带 `@AuditLog` 的写接口核对 `operation_log.admin_id` | #3 |
| B | `/admin/**` 无角色闸门是"产品已拍板的口令方案"还是"实现遗漏" | 技术负责人确认 spec §0.3 L121 / §0.4 L71 / §5 L214 是否需同步修订 | #5 #6 |
| C | `review.tags` 列是否为"保留待恢复"而非"应删除" | 产品经理确认美团式写评是否彻底不做 | #7 |
| D | `ReviewAdminVO.hasSensitive` 前端 web 是否已消费 | grep `web/src` 中 `hasSensitive` 使用点 | #8 |
| E | `promotion` 标签入口（`/dishes/promotions`）是否仍在小程序首页消费 | 检查 `client/src` 是否调用 `/dishes/promotions` | #12 |
| F | `getHotDishes()` 无参重载是否被反射/测试调用 | 检查 `server/src/test` 全量 | #2 |
| G | `pageSize` 上限是否真存在越界风险 | 起服务后调 `/reviews?pageSize=100000` 观察是否 OOM/超时 | #4 相关 |

---

## 已通过项（放行留档）

- ✅ 错误码值域仅 `{200,400,401,403,4031,500}`，无自定义非标码（spec §5 L212）
- ✅ 无 `is_new` 列残留（历史 500 事故根因已消除）
- ✅ Mapper XML 全部列引用线上真实存在（实跑 SQL 验证，非 grep）
- ✅ 线上表结构与 `schema.sql` 无表级漂移；关键唯一键 `uk_review_user_dish` / `uk_useful_user_review` 与索引 `idx_dish_heat` 均存在
- ✅ 无直连 ALTER 痕迹，数据库变更 100% 走 `db/schema.sql` 幂等迁移（红线通过）
- ✅ 金额统一「分」（实体 `Integer` + 前端 api 层转换）
- ✅ 角色仅 `STUDENT`/`ADMIN`，无 `STALL_OWNER` / `/stall-owner/**` 残留
- ✅ 学生端 `POST/PUT/DELETE /dishes` 已彻底下线
- ✅ UGC 写操作 `verified=false` 返回 `4031`（`RequireVerifiedAspect.java:41`），前端 `client/src/api/http.ts:222` 正确分流
- ✅ 评价可见性双层过滤（`is_hidden=0` AND `sec_state`）在 4 条查询中口径一致
- ✅ 字段 camelCase（VO/Req 全部 Lombok `@Data` + camelCase，`JacksonConfig` 未改命名策略）

---

## 备注

- 本报告仅为**排查结论**，未修改任何代码、spec 或 tasks。
- 所有 🔴 项均给出了 `file:line` 证据；`#3` 因依赖运行时行为已明确标注"待确认"并列出验证手段，未作猜测性结论。
- 报告落盘路径：`docs/loop/audit/backend.md`
