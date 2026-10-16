# 待决事项清单

> **用途**：集中登记需要项目所有者拍板的开放议题，便于逐条过。
> **本文件不是设计真源** —— 每条议题一经拍板，结论写入对应真源（[`schema/`](./schema/README.md) · [`api/`](./api/README.md) · `func/client/` · `func/web/` · `ui/client/` · `ui/web/` · [`secur/`](./secur/README.md) · 本目录 [`README.md`](./README.md) 的「已知取舍」），并先以 `✅（已拍板）` 标注**留存**本条；**待结论已全部写入对应真源、且真源与代码都落地完成后，再从本文件移除该条目**（**拍板 ≠ 完成**：拍板只定方向，真源改完、代码对齐后才清账；严禁「文档未写 / 代码未动就删条目」）。
> **安全专项**待决项另有 [`测试/安全待决策项.md`](./测试/安全待决策项.md)，不在本文件重复登记。
> 每条给出：议题 · 现状与依据 · 选项 · 我的建议（可直接回复编号 + 选项编号）。

---

## 1. `report` 是否加 `(user_id, review_id)` 唯一索引（防重复举报）

- **现状**：举报去重只在应用层（`ReportServiceImpl` 经 `DuplicateGuard` 查重断言），**无 DB 唯一约束**；并发双击 / 重试可写入重复行。管理端列表会因此出现「同一人对同一评价的多条举报」，待处置数与处置动作都被放大。
- **选项**：**A** 加 `uk_report_user_review (user_id, review_id)` 唯一索引（应用层须捕获冲突并转既有文案）｜ **B** 维持应用层去重。
- **与真源现状的关系**：[`schema/report.md`](./schema/report.md) §去重 已登记「不建唯一索引」（理由：`review_id` 可空，唯一索引不约束 NULL 行，会给出「部分成立」的假保证）。本条为**复议**：回「1A」即反转该登记并落库；回「1B」则真源与代码已一致、直接清账。
- **我的建议**：**A**。与「一人一菜一条评价」同款思路——唯一键是并发下唯一可靠的判据；代码侧冲突处理成本很低。

## 2. `correction` 的 `gone` 型是否加唯一索引（防重复提交）

- **现状**：`gone` 型（菜品已下架）去重同样是「先 `exists` 后 `insert`」（`CorrectionServiceImpl`），无 DB 约束；`field` 型允许重复提交（不限次），只有 `gone` 型需要一菜一人一条。
- **联动**：[`schema/correction.md`](./schema/correction.md) 类型模型重构稿中原草拟的 `UNIQUE (type, dish_id)` 已移除（会挡住第二位用户、压平「N 人反馈」聚合），防重复维持应用层（见其「关键设计 §2」）—— 本条决定是否以生成列/冗余列方案升级为 DB 约束。
- **选项**：**A** 加 `gone` 型限定唯一索引（如 `(user_id, dish_id)` 上带 `type='gone'` 的表达式/前缀索引，MySQL 5.7 无部分索引 ⇒ 需借生成列或冗余列实现）｜ **B** 维持现状（接受极低概率重复）。
- **我的建议**：**B（暂缓）**。MySQL 5.7 无部分索引，为这一条规则引入生成列/冗余列的代价高于收益；真出现重复行时管理端驳回即可，且 `gone` 型有 `goneUserCount` 聚合展示，重复不会放大处置动作。

## 3. `dish_view_log` 是否记录 `user_id`（浏览量 PV → UV）

- **现状**：浏览明细只按 IP 落库（`dish_view_log` 无 `user_id`），菜品热度按**次数**计；同一用户反复刷新会计多次。属**产品口径**问题，不是实现缺陷。
- **选项**：**A** 维持 PV ｜ **B** 加 `user_id` 列 + 唯一约束改按人去重（需连登录态口径一起定：游客是否计）。
- **与真源现状的关系**：[`schema/dish_view_log.md`](./schema/dish_view_log.md) 已登记「不去重是刻意取舍」（去重需 `user_id` 列 + 唯一键或 `GROUP BY`，写入成本与锁竞争显著上升，收益当前无消费方）。回「3A」即与真源一致、直接清账；回「3B」则改真源并落库。
- **我的建议**：**A（暂缓）**。`dish.view_count` 已停写、热度仅作内部参考，且 UV 口径要先定「游客算不算」——收益不明确前不加列。

## 4. `stall` 是否加 `(name, canteen_id)` 唯一索引（防同名档口重复行）

- **现状**：`StallServiceImpl#upsertStallByName` 是「SELECT 未命中 → INSERT」的 TOCTOU（`name` 无唯一键），两名管理员同时录入同名档口会产出两行，菜品随之分散挂到两个同名档口；同名食堂（`upsertCanteenIdByName`）同理。
- **选项**：**A** 加 `uk_stall_canteen_name (canteen_id, name)` + `uk_canteen_name`（并在 Service 层捕获冲突转既有文案）｜ **B** 维持应用层（仅把方法标上 `@Transactional`，**不解决** RR 快照读看不到并发插入的问题）。
- **与真源现状的关系**：[`schema/stall.md`](./schema/stall.md) 已登记「`name` 不加唯一索引，同食堂下唯一由应用层保证」。回「4B」即与真源一致、直接清账；回「4A」则改真源并落库（`canteen` 同步）。
- **我的建议**：**A**。与「一人一菜一条评价」同款思路；应用层标注事务是安慰剂，真正可靠的判据只有唯一键。

## 5. `review` 是否补 `updated_at`（处置留痕的排序维度）

- **现状**：`review` 表**无 `updated_at`**（`docs/schema/review.md` 登记为明确例外：`created_at` 只表达首次发表时间，重评不刷新）。代价是「最近一次可见性处置」只能按 `moderated_at` 排，而**重评覆盖**这类行内变更没有任何时间痕迹。
- **选项**：**A** 维持现状（无该列）｜ **B** 补 `updated_at` 列（并同步实体时间字段口径与 `EntityTimeFieldContractTest` 的例外清单）。
- **与真源现状的关系**：[`schema/review.md`](./schema/review.md) 已登记「本表无 `updated_at`」为明确例外。回「5A」即与真源一致、直接清账；回「5B」则改真源、加列并同步例外清单。
- **我的建议**：**A（暂缓）**。补列会让「评价时间」出现两种语义（首次发表 / 最后活动），反而更容易被误用；若将来确实要按「最后活动」排序，优先用 `moderated_at` 或另加语义明确的列。

---

## 6. ✅（已拍板）`correction` 类型模型重构：`field` / `gone` → `dish` / `stall` + `subtype`（新增「档口关闭」）

- **拍板结论**：菜品信息有误 + 菜品已下架归为 `type='dish'`（`subtype='info'` / `'off_shelf'`）；「档口关闭」归为 `type='stall'`（目标 `stall_id`，`subtype` 恒 NULL）。类型模型 / 列定义 / 处置方向已写入 [`schema/correction.md`](./schema/correction.md)（本次已同步修正其中三处矛盾：防重复维持应用层、目标列不设外键、补 stall 型语义）。
- **现状（代码 0 落地）**：`CorrectionConst` 仍是 `field` / `gone`，实体无 `subtype` / `stall_id`；提交入口、管理端列表、端上类型选择、[`api/client/dishes.md`](./api/client/dishes.md)、[`api/web/corrections.md`](./api/web/corrections.md)、`func` / `ui` 文档、openapi.json + api.d.ts、`db/` 迁移脚本**全部未动**（`git status` 中 server / client / web 无一处代码改动即为证）。
- **落地清单（须全部完成方可移除本条）**：① 服务端常量 / 实体 / DTO / Service / Controller / VO + 存量迁移脚本（`field`→`dish/info`、`gone`→`dish/off_shelf`）；② 管理端反馈列表与详情（Tab / 筛选 / 对照出参）；③ 端上类型选择与三种表单；④ API 文档 + `func` / `ui` 文档同步；⑤ 重新生成 `openapi.json` 与 `api.d.ts` 并过 `OpenApiContractSyncTest`；⑥ 全量测试。
- **落地时一并拍板的待定点**：a) 「档口关闭」提交入口 —— 沿用 `POST /dishes/{id}/correction`（服务端解析所属档口）vs 新开 `POST /stalls/{id}/correction`；b) 管理端筛选粒度 —— `type` + `subtype` 双参 vs 单一视图枚举；c) 一键型去重键与「N 人反馈」出参是否随语义更名（`goneUserCount`）；d) `stall` 型驳回缺省标题。
- **我的建议**：a) 新开端点 `POST /stalls/{id}/correction`（路径直接表达目标，且「菜品已下架仍可报档口关闭」不受在售校验阻塞）；b) `type` + `subtype` 双参，与列模型同构；c) 去重键按 `correction.md` §2，出参更名随 ⑤ 一并做（破坏性契约变更）；d) 「档口仍在营业」（对称「菜品仍在售」）。

## 7. ✅（已拍板）`moderation_task.type_id` 补真实外键（落库）

- **拍板结论与三步落库方案已写入** [`schema/moderation_task.md`](./schema/moderation_task.md)：新增 `moderation_type` 类型表 → `type_id` FK + 按 `type` 持有的三目标列 → 存储迁移。
- **现状（DB 未落）**：生产库与 [`db/`](./db/) 迁移脚本均未执行该方案，实体与 Mapper 不感知。
- **落地范围（须全部完成方可移除本条）**：迁移脚本 + 存量回填 + 实体 / Mapper 对齐 + 真源去掉「下一轮落库」标注 + 全量测试。
- **我的建议**：按真源三步顺序执行，迁移与回填同一脚本、失败即停。

## 8. `ingredients`（食材）是否从 `attributes` 拆出独立字段 / 独立取值表

- **现状**：食材是 `dish_attribute_dimension` 的一个维度（多选），取值与其余维度混放在 `dish_attribute_value`；`Dish.attributes` JSON 键 = 维度 ID。管理端新增 / 编辑菜品时，食材在通用「属性」子表单内按维度渲染，没有独立表单位置；四维（dietType / ingredients / flavorTags / serveTemp）刚收敛进 `attributes`（表驱动，新增维度免 ALTER、免发版）。全站目前**无**「按食材搜菜 / 过敏原提示」类消费方。
- **选项**：**A** 仅表单层拆出 —— 新增 / 编辑菜品把「食材」渲染为独立表单项（存储仍走 `attributes`，不动 schema 与取值字典）｜ **B** 拆独立列 `dish.ingredients`（逆转四维收敛）｜ **C** 独立取值表 —— 食材取值与 `dish_attribute_value` 分家（可挂别名 / 元数据，为将来按食材查询铺路）。
- **我的建议**：**A**。录入体验问题（「菜品增加时单独拿出来」）在表单层即可解决，零迁移、不动 A4 维度体系；B 逆转刚完成的收敛、重新引入「加字段要 ALTER」的断裂；C 仅在出现「按食材搜菜 / 过敏原 / 同义词归一」的真实查询需求时才值得 —— 届时食材维度的取值整体迁入独立字典，`attributes` 键位保持兼容。
