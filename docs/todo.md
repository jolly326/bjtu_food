# 待决事项清单

> **用途**：集中登记需要项目所有者拍板的开放议题，便于逐条过。
> **本文件不是设计真源** —— 每条议题一经拍板，结论写入对应真源（[`schema/`](./schema/README.md) · [`api/`](./api/README.md) · `func/client/` · `func/web/` · `ui/client/` · `ui/web/` · [`secur/`](./secur/README.md) · 本目录 [`README.md`](./README.md) 的「已知取舍」），并从本文件**移除该条目**（不留「已完成」痕迹）。
> **安全专项**待决项另有 [`测试/安全待决策项.md`](./测试/安全待决策项.md)，不在本文件重复登记。
> 每条给出：议题 · 现状与依据 · 选项 · 我的建议（可直接回复编号 + 选项编号）。

---

## 1. `report` 是否加 `(user_id, review_id)` 唯一索引（防重复举报）

- **现状**：举报去重只在应用层 `selectCount`（`ReportServiceImpl` 提交前断言），**无 DB 唯一约束**；并发双击 / 重试可写入重复行。管理端列表会因此出现「同一人对同一评价的多条举报」，待处置数与处置动作都被放大。
- **选项**：**A** 加 `uk_report_user_review (user_id, review_id)` 唯一索引（应用层须捕获冲突并转既有文案）｜ **B** 维持应用层去重。
- **我的建议**：**A**。与「一人一菜一条评价」同款思路——唯一键是并发下唯一可靠的判据；代码侧冲突处理成本很低。

## 2. `correction` 的 `gone` 型是否加唯一索引（防重复提交）

- **现状**：`gone` 型（菜品已下架）去重同样是「先 `exists` 后 `insert`」（`CorrectionServiceImpl`），无 DB 约束；`field` 型允许重复提交（不限次），只有 `gone` 型需要一菜一人一条。
- **选项**：**A** 加 `gone` 型限定唯一索引（如 `(user_id, dish_id)` 上带 `type='gone'` 的表达式/前缀索引，MySQL 5.7 无部分索引 ⇒ 需借生成列或冗余列实现）｜ **B** 维持现状（接受极低概率重复）。
- **我的建议**：**B（暂缓）**。MySQL 5.7 无部分索引，为这一条规则引入生成列/冗余列的代价高于收益；真出现重复行时管理端驳回即可，且 `gone` 型有 `goneUserCount` 聚合展示，重复不会放大处置动作。

## 3. `moderation_task` 是否需要乐观锁（版本号列）

- **现状**：处置并发已由**条件 UPDATE** 兜住（`WHERE status='pending'` + 判影响行数，落空即「该记录已处理」），两个管理员同时处置同一条不会双双成功。
- **选项**：**A** 维持条件 UPDATE（不加列）｜ **B** 再加 `@Version` 版本号列做乐观锁。
- **我的建议**：**A**。条件 UPDATE 已覆盖「重复处置」这一真实风险；版本号列只在「同一条被并发写入且语义不同」时才更有价值，本表的写只有「处置」一种。

## 4. `dish_view_log` 是否记录 `user_id`（浏览量 PV → UV）

- **现状**：浏览明细只按 IP 落库（`dish_view_log` 无 `user_id`），菜品热度按**次数**计；同一用户反复刷新会计多次。属**产品口径**问题，不是实现缺陷。
- **选项**：**A** 维持 PV ｜ **B** 加 `user_id` 列 + 唯一约束改按人去重（需连登录态口径一起定：游客是否计）。
- **我的建议**：**A（暂缓）**。`dish.view_count` 已停写、热度仅作内部参考，且 UV 口径要先定「游客算不算」——收益不明确前不加列。

## 5. `stall` 是否加 `(name, canteen_id)` 唯一索引（防同名档口重复行）

- **现状**：`StallServiceImpl#upsertStallByName` 是「SELECT 未命中 → INSERT」的 TOCTOU（`name` 无唯一键），两名管理员同时录入同名档口会产出两行，菜品随之分散挂到两个同名档口；同名食堂（`upsertCanteenIdByName`）同理。
- **选项**：**A** 加 `uk_stall_canteen_name (canteen_id, name)` + `uk_canteen_name`（并在 Service 层捕获冲突转既有文案）｜ **B** 维持应用层（仅把方法标上 `@Transactional`，**不解决** RR 快照读看不到并发插入的问题）。
- **我的建议**：**A**。与「一人一菜一条评价」同款思路；应用层标注事务是安慰剂，真正可靠的判据只有唯一键。

## 6. 删除档口的「其下仍有菜品」判据是否下沉进事务

- **现状**：判据（`countByStallId > 0 ⇒ 400`）写在 `CanteenAdminController`，与 `StallServiceImpl#deleteStall` 的 `@Transactional` **不在同一事务**；检查与删除之间可插入新菜品，产出 `stall_id` 指向已删档口的孤儿菜品（公开列表走 INNER JOIN ⇒ 这批菜永久不可见）。对照 `CanteenServiceImpl#deleteCanteen` 已把同类判据放在事务内，两处口径不一致。
- **阻塞点（非 DB）**：下沉需要 canteen 域拿到「某档口下菜品数」，而 `dish → canteen` 已存在依赖（`DishServiceImpl` 调 `StallService`），反向直接依赖会成环，且 ArchTests 禁跨域直连他域 Mapper。
- **选项**：**A** 维持现状（Controller 编排，接受极小窗口）｜ **B** 判据下沉为 dish 域的只读契约（如 `DishService#countByStallId`），在 controller 编排层调用，但把「检查 + 删除」收进一个新的事务入口｜ **C** 改由数据库外键 `dish.stall_id → stall.id`（RESTRICT）兜底。
- **我的建议**：**B**（与「填充档口均分」同一编排层思路，不引入环）；若认为该窗口可接受则 **A**。

## 7. `review` 是否补 `updated_at`（处置留痕的排序维度）

- **现状**：`review` 表**无 `updated_at`**（`docs/schema/review.md` 登记为明确例外：`created_at` 只表达首次发表时间，重评不刷新）。代价是「最近一次可见性处置」只能按 `moderated_at` 排，而**重评覆盖**这类行内变更没有任何时间痕迹。
- **选项**：**A** 维持现状（无该列）｜ **B** 补 `updated_at` 列（并同步实体时间字段口径与 `EntityTimeFieldContractTest` 的例外清单）。
- **我的建议**：**A（暂缓）**。补列会让「评价时间」出现两种语义（首次发表 / 最后活动），反而更容易被误用；若将来确实要按「最后活动」排序，优先用 `moderated_at` 或另加语义明确的列。
