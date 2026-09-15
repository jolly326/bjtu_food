# Loop 状态看板（STATUS）

- **分支策略（2026-09-14 变更）**：**工作分支统一为 `develop`**；`feature/client-ui` 已快进合入 `develop` 并删除（本地 + 远端）；`main` 仍按既有规范由 `develop` 经 PR 更新。
- **业务定型进度**：spec §7.1 ~ §7.17（17 组决议）全部落地或有明确归属；最新批次含「食堂档口仅作筛选条件、菜品列表+详情为主体」「反馈回复必填」「首页一次性定位提示」「后台上传自动压缩（宽 1200px / ≤1MB）」。
- **当前轮次**：ITER-003（团队缺陷与不一致排查）——**A/B/C/D 四批次全部闭环**
- **报告**：`docs/loop/audit/ITER-003-AUDIT.md`＋`backend.md`/`frontend.md`（明细）
- **验收标准**：`docs/loop/launch-checklist.md`（三项并行）
- **问题渠道**：`docs/loop/QUESTIONS.md`
- **当前 main**：`6a61b7d`

## 停机判定（本 loop）

**停止条件：S2（无米下锅）+ 剩余阻塞均在用户侧**——代码侧可达的 P0/P1 与 🔴/🟠 缺陷已清零，D 批次蓝色优化项亦已完成；继续迭代会空转。恢复触发：用户完成部署后复测（见下）。

## ITER-003 批次终态

| 批次 | 内容 | 状态 |
|---|---|---|
| **A** | 死方法 `likeReview`/`getHotDishes()`、web 401 死跳 `/login`、悬空注释 | ✅ |
| **B** | 命名统一、删死字段 `hasSensitive`、常量单一真源、4 个组件/composable 下沉、`PUT /admin/feedbacks/{id}` 收敛为 body、删中转与孤立 store、物理删除 5 个死文件、修 `HistoryService` 缺失 import | ✅ |
| **C** | 需用户拍板项（§7.9~§7.13） | ✅ 全部拍板并落地 |
| **D** | 热度公式抽 `heatScoreExpr`＋`DishHeatWeights`、`user` 保留字评估（保持现状＋注释）、反馈/举报文案落位 | ✅ |

## 上线阻塞项进度

| # | 事项 | 状态 |
|---|---|---|
| D1 | 云托管重新部署 | ✅ **已完成**（2026-09-14 实测：线上已含分页契约与 B 批次代码；`ADMIN_TOKEN`/COS/上传鉴权均已生效） |
| D4 | 执行 DB 迁移（删 3 列） | ✅ **已完成**（agent 执行：`serve_period`/`limited`/`review.tags` 三列已删除，`region` 注释已同步；迁移后全部接口复测 200） |
| D2 | 上传 **31 张菜品首图**（当前 31/31 缺失）＋按 `dish-proofread-checklist.md` 逐条校对 | ⏳ 待用户（上传链路已实测打通：`POST /upload/image` → COS 直链 200） |
| D3 | 小程序 **downloadFile 域名**加 COS 域名 | ⏳ 待用户（COS 图片已验证公有读可访问） |
| D5 | 死文件删除授权 | ✅ 已完成（5 个文件已物理删除） |
| **D6** | **删列迁移 `stall.business_hours`** | ✅ **关闭（2026-09-15）**：代码零引用（grep `business_hours` 于 `server/src` 含 xml，仅命中 `db/schema.sql`/`seed_data.sql` 中的说明性注释，无任何 .java/.xml 读取或映射）；`schema.sql`（约 699-718 行）幂等 DROP（先判存在再 DROP，可重跑）已就位，**随下次部署生效** |
| D7 | 小程序端图片修复需**重新编译预览/上传新版本**（非云托管部署） | ⏳ 待用户 |

## 下一轮候选（部署完成后）

1. **部署后复测**：按 `launch-checklist.md` B 段跑线上自动冒烟（9 项），确认鉴权/COS/契约/下线接口 404
2. **手工走查**：C 段 25 项（小程序真机 + 后台）
3. **二期**：投稿「预填到菜品表单」（**非一键转正**，须编辑后保存；见 §7.13 第 1 条，需重新拍板才开发）
4. 剩余蓝色项（如有）与 UI 文案的设计评审归档

## 流程改进（已固化）

- 后端门禁一律 `mvn -q clean compile -DskipTests`（增量编译会掩盖缺失 import；2026-09-14 实际发生）
- 子 agent 交付必须**落盘到指定文件**，主 agent 以读文件验收（回报可能被截断）
- 小程序门禁必须**同时**跑 `npm run type-check` **与** `npm run build:mp-weixin`：`build:mp-weixin` **不做类型检查**，模板里引用了不存在的标识符（如 `selectedSpiceLevel` 未定义）也能"Build complete"（2026-09-14 实际发生）

## 优化 Loop 登记（2026-09-15，详见根目录 QA.md）

- **当前轮次**：优化 Loop（基线 develop @ `68e76ee`，回滚分支 `backup/optimize-loop-20260915`）——QA.md §1 低风险项已执行并复核：BE-OPT-02（删 `DishVO.distance` 死字段）、BE-OPT-05（`enrichImages` 重载合并）、BE-OPT-07（评价重复文案统一中文）、UI-OPT-04（`ReportModal` 焦点捕获 + ESC）；连同既有清理项（`getByUsername`、`enrichDishAdminImages`、`adminStore.loadAll()`、`STATUS_ACTIVE/INACTIVE`、vue-i18n、`shims-uni.d.ts`、`image.png`）；`docs/loop/audit/` 7 份日期报告与 `ITER-001.md` 已归档至 `docs/loop/_archive/`。
- **契约文档同步（本轮）**：spec §2.1.4 后端距离字段口径（已删除）、§5.z D-D 缓存收窄（仅剩热搜）、§8 `DataInitializer` 转已完成；api-design.md §8 对账表 4 行（`reject_reason` / 食堂档口 upsert / 菜品无独立审核 / 管理端无密码体系）刷新为「已实现并对齐」；architecture.md §4.3 管理后台无登录说明；product-blueprint.md 升版 v1.2 对齐 spec §7.23。

## 中高风险项拍板结果（2026-09-15，详见根目录 QA.md §2）

以下 9 项为中高风险优化项，已于 2026-09-15 由用户拍板：

| 项 | 结论 |
|---|---|
| TL-OPT-03 / TL-OPT-04 / TL-OPT-05 | ✅ **已拍板执行**（launch-checklist 历史标注、QUESTIONS.md 真值收敛、D6 business_hours 收口） |
| BE-OPT-04、UI-OPT-01 | ✅ **已拍板执行** |
| BE-OPT-03、BE-OPT-06 | ⏸ **暂缓**，待补集成测试后再执行 |
| MP-OPT-03、MP-OPT-05 | 维持保留（此前 Round 1 已定跳过） |

## 角色文档缺失（QA.md §0）

`docs/` 下 7 个角色文档（产品经理 / 后端开发工程师 / 技术负责人 / 小程序开发工程师 / 质量把控工程师 / UI-UX设计师 / Web管理后台开发工程师）缺失。本轮以用户指令中的角色职责定义执行，**不擅自补全职责**；若用户后续补齐独立角色文档，以文档为准重新对齐。

## 循环触发
- 手动：用户说「继续下一轮」→ 读本文件接续
- 自动（可选）：`FREQ=DAILY;BYHOUR=2;BYMINUTE=0`
