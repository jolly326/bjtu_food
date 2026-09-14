# ACCEPTANCE_CHECKLIST.md（优化 Loop · 验收清单）

> 对应协议「七、停止条件」「九、最终输出」。基线：develop @ `68e76ee`。

## A. 原则符合性
- [x] **不扩大功能**：无新增页面/路由/API/功能入口/UI 库；`client/pages.json`、`web router` 未变；ReportModal 仅为无障碍增强。
- [x] **不破坏业务**：本地距离（Haversine）、UGC 闭环、反馈处理、审核归口均未被改动；删除项经 grep 0 引用复核。
- [x] **最小改动**：均为单文件/单字段删除或等价重构，无大规模重写、无框架迁移、无 UI 库替换。
- [x] **可回滚**：每项 git 可回滚；整轮可 `git reset --hard 68e76ee` 或 `backup/optimize-loop-20260915`。
- [x] **可验证**：三端门禁均报绿（server `mvn compile` 0 / client `npm run type-check` 0 / web `npm run build` 0）。

## B. 可运行 / 可构建 / 可测试
- [x] server：`mvn -q -DskipTests compile` EXIT=0
- [x] client：`npm run type-check`（vue-tsc --noEmit）EXIT=0
- [x] web：`npm run build`（含 vue-tsc）EXIT=0
- [ ] 运行时冒烟（游客 token / 受保护接口 / /dishes SQL 实跑 / 4031 实响应 / /admin 口令）——需具备环境，列为部署前风险项（非本轮阻塞）。

## C. 流程合规
- [x] 每轮有记录：OPTIMIZATION_CHANGELOG.md / DELETED_FILES.md / QA.md / ACCEPTANCE_CHECKLIST.md 已建立。
- [x] 中高风险项已入 QA.md §2 待用户决策，未擅自执行。
- [x] 角色文档缺失已记入 QA.md §0（未擅自补全职责）。
- [x] 删除前确认引用关系（无引用 / 死资源 / 过程文档）。

## D. 待用户决策（不视为完成）
- [ ] QA.md §2 全部中高风险项（TL-OPT-03/04/05、BE-OPT-03/04/06、UI-OPT-01、MP-OPT-03/05）拍板后执行。
- [ ] 用户验收本轮提交并决定是否 PR 至 main。

## E. 停止判定
- [x] 低风险优化项已全部完成（本轮 14 执行项 + 3 跳过项均为低/无风险）。
- [x] 剩余项均为中高风险，且已写入 QA.md 等待决策 → **符合停止条件 2**。
- [x] 无连续两轮空转 → 非空转停止。

## F. 回滚方式
- 单文件：`git checkout -- <file>` 或 `git revert <commit>`。
- 整轮：`git reset --hard 68e76ee` / `git checkout backup/optimize-loop-20260915`。
- 4 份交付文档随本轮提交，回滚时一并移除。
