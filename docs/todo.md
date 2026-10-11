# 待决事项清单

> **用途**：集中登记需要项目所有者拍板的开放议题，便于逐条过。
> **本文件不是设计真源** —— 每条议题一经拍板，结论写入对应真源（[`schema/`](./schema/README.md) · [`api/`](./api/README.md) · `func/client/` · `func/web/` · `ui/client/` · `ui/web/` · [`secur/`](./secur/README.md) · 本目录 [`README.md`](./README.md) 的「已知取舍」），并**从本文件移除该条目**（不留「已完成」痕迹）。
> **安全专项**待决项另有 [`测试/安全待决策项.md`](./测试/安全待决策项.md)，不在本文件重复登记。
> 每条给出：议题 · 现状与依据 · 选项 · 我的建议（可直接回复编号 + 选项编号）。

---

## ✅ 1.（已拍板）菜品问题反馈：去掉 `differences` 现算与采纳写回链路

- **拍板结论**：管理端对反馈**只「阅读 + 处置回执」，不提供把用户内容写回主数据的快捷路径**。菜品修改一律由管理员在 [A3 菜品管理](./func/web/A-主数据维护/A3-菜品管理.md) **手动**完成。
- **提交端不变**：`field` 局部快照 / `gone` 一键 + 去重；`attributes` 快照列**保留**，语义由「采纳写回载荷」降为「**用户提交记录（中文原文留痕）**」。
- **管理端处置（统一两个动作，不再按 `type` 分派写动作）**：

  | 动作 | 端点 | `outcome` | 回执（→ 用户 notification） | 改菜品 |
  |---|---|---|---|---|
  | **标记已处理**（接受） | `POST /admin/corrections/{id}/handle`（**新增**） | `handled` | 「已收到你的反馈 / 感谢反馈，已核实处理」 | 否 |
  | **驳回**（拒绝） | `PUT /admin/corrections/{id}`（已有） | `rejected` | 「菜品信息未采纳」/「菜品仍在售」+ **原因（必填）** | 否 |

  详情并排展示「**当前值 / 用户提交值 + 是否一致**」；`gone` 展示 `note` / `images` / `goneUserCount`。`outcome='handled'` 语义由「已写回菜品」改为「**已受理（管理员已手动处理）**」—— 与举报域「举报已受理」同构，`moderation_task` 状态机与通知链路零改动。
- **删除清单**：`adopt` 端点 + `buildDifferences`/`addDiff`/`describe` + `acceptedAttributes`/`resolveAcceptedFields`/`partialNotice`/`labelsOf` + 两段式档口确认（`StallConfirmVO`/`buildCandidates`/`findIdByName`/`upsertStallByName`）+ `DishCorrectionDifferenceVO`/`DishCorrectionAdoptReq`；跨域写契约（无其他消费方时）：`DishService.applyCorrection`+`DishCorrectionCmd`、`StallService.updateFloor`/`listBriefCandidates`/`getFloorById`；web 端采纳抽屉。
- **结论去向（待写入真源）**：[`schema/correction.md`](./schema/correction.md)（attributes 语义 + 删写回叙述）→ [`api/web/corrections.md`](./api/web/corrections.md)（differences 改两值 + 删 adopt + 加 handle + 错误码）→ [`func/web/B-UGC治理/B4`](./func/web/B-UGC治理/B4-菜品问题反馈管理.md)（处置流程重写）→ [`ui/web/菜品问题反馈.md`](./ui/web/菜品问题反馈.md)（抽屉重设计）→ [`func/client/B6`](./func/client/B-UGC表达与治理/B6-菜品问题反馈.md)（「采纳」措辞改「处理后手动更新」）→ 本目录 [`README.md`](./README.md) 已知取舍新增一条。
- **本次范围**：仅在本文件留拍板记录；**未动其它真源与代码**（各真源「与当前代码的差异」节后续随落地如实登记）。

## 2. 限流缺口是否补齐

- **现状真源**：[`secur/server/通用防护机制.md`](./secur/server/通用防护机制.md) §1.4 全量规则清单。已挂频控：意见反馈 / 举报 / 菜品问题反馈 / 评价写、发码 / 验证码 / 静默登录、管理端登录（IP + 账号双维度）。
- **已如实登记的未挂频控项**：
  - 写入口：`POST /upload/cloud-image`、`PUT /auth/profile`、`DELETE /reviews/{id}`、`DELETE /auth/account`、通知已读类；
  - 读侧：`GET /my/reviews`、`GET /auth/profile`、`GET /report-options`、`GET /feedback-options` 无独立频控（有全局与 ID 枚举防护）。
- **选项**：**A** 上述全部加闸 ｜ **B** 只给高成本项（云图上传、删除类）加闸 ｜ **C** 维持现状。
- **我的建议**：**B**。云图上传与删除类有外部成本 / 不可逆，收益明确；读侧维持现状（已有兜底）。

## 3. `review.user_id` 是否收紧为 `NOT NULL` + 外键

- **现状**：列定义 `BIGINT NOT NULL DEFAULT 0`，**无外键**；实测 `user_id = 0` 为 **0 行**，现行写入路径恒为真实用户（提交评价需邮箱认证）。
- **选项**：**A** 去默认值 `0` 并加外键 `→ user(id)`（与 `feedback` / `report` / `correction` 同口径） ｜ **B** 维持现状。
- **我的建议**：**A**。`user` 行永不物理删除 ⇒ 外键不会触发级联，纯引用完整性兜底。

## 4. 管理端 `ImageUpload` 的 `max` 默认值（5 → 3）

- **现状**：组件默认 `max = 5`；当前消费方全是**运营配置**（菜品 / 食堂 / 档口图片，合法 5 张），**无 UGC 消费方**，与「全站 UGC 配图 ≤3 张」不冲突。
- **风险**：将来管理端新增 UGC 类上传位时，不显式传 `:max="3"` 会默认落 5。
- **选项**：**A** 默认值改 3（3 个运营页各显式传 `:max="5"`） ｜ **B** 维持默认 5（新增 UGC 位时显式传 3）。
- **我的建议**：**A**，让「默认值 = 严格值」。

## 5. `docs/测试/上线前验收测试.md` 的体例

- **现状**：该文件为操作型验收清单，**无**「与当前代码的差异」节（其余文档基准节序含此节）。
- **选项**：**A** 补该节（内容「无」） ｜ **B** 文首注明体例豁免。
- **我的建议**：**B**（验收清单不承载「与代码差异」）。

## 6. 端上是否需要「我的菜品问题反馈」提交记录页

- **现状**：纠错提交结果只经系统通知回执；端上无「我提交过的纠错」列表页，管理端有对应列表。
- **选项**：**A** 不做 ｜ **B** 新增（需另立 UI 页面稿 + 接口契约）。
- **我的建议**：**A**（提交频次低，回执已闭环）。

## 7. 第一轮迁移备份的去留

- **现状**：`server/target/dbcheck/backup_20261010_pre_redesign.sql`（第一轮库迁移前快照）位于构建产物目录，`mvn clean` 会清空；第二轮删除的种子数据**不在该快照内**。
- **选项**：**A** 复制出仓库长期保留 ｜ **B** 随构建清理放弃。
- **我的建议**：**B**（两轮迁移均已逐项核验通过，该快照无回滚价值）。
