# Loop 状态看板（STATUS）

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

## 待用户执行（4 项 · launch-checklist D 段）

| # | 事项 | 影响 |
|---|---|---|
| D1 | 云托管按 `main`（`6a61b7d`）**重新部署** | 后台可用；`ADMIN_TOKEN`/COS/上传鉴权/文案/双约束才生效 |
| D2 | 上传 **31 张菜品首图**（当前 31/31 缺失）＋按 `dish-proofread-checklist.md` 逐条校对 | 小程序菜品图与数据质量 |
| D3 | 小程序 **downloadFile 域名**加 COS 域名 | 正式版图片显示 |
| D4 | 执行 **DB 迁移**（`serve_period`/`limited`/`review.tags` 三列删除，脚本幂等） | 清除孤儿列 |

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
