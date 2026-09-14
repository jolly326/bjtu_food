# Loop 状态看板（STATUS）

- **分支策略（2026-09-14 变更）**：**工作分支统一为 `develop`**；`feature/client-ui` 已快进合入 `develop` 并删除（本地 + 远端）；`main` 仍按既有规范由 `develop` 经 PR 更新。
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
| **D6** | **删列迁移 `stall.business_hours`（⚠️ 必须在新代码部署之后再执行）** | ⏳ 挂起中 —— 当前线上代码仍在 `SELECT s.business_hours`，**现在删列会导致线上全部菜品接口 500**；正确顺序：部署新代码 → 我立即执行该列删除（脚本已幂等写入 `schema.sql`） |
| D7 | 小程序端图片修复需**重新编译预览/上传新版本**（非云托管部署） | ⏳ 待用户 |

## 下一轮候选（部署完成后）

1. **部署后复测**：按 `launch-checklist.md` B 段跑线上自动冒烟（9 项），确认鉴权/COS/契约/下线接口 404
2. **手工走查**：C 段 25 项（小程序真机 + 后台）
3. **二期**：投稿「预填到菜品表单」（**非一键转正**，须编辑后保存；见 §7.13 第 1 条，需重新拍板才开发）
4. 剩余蓝色项（如有）与 UI 文案的设计评审归档

## 流程改进（已固化）

- 后端门禁一律 `mvn -q clean compile -DskipTests`（增量编译会掩盖缺失 import；2026-09-14 实际发生）
- 子 agent 交付必须**落盘到指定文件**，主 agent 以读文件验收（回报可能被截断）

## 循环触发
- 手动：用户说「继续下一轮」→ 读本文件接续
- 自动（可选）：`FREQ=DAILY;BYHOUR=2;BYMINUTE=0`
