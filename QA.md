# QA.md（优化 Loop）

> 记录者：产品经理（兼任）。创建日期：2026-09-15。基线：develop @ `68e76ee`（回滚分支 `backup/optimize-loop-20260915`）。
> 原则：不扩大功能 / 不破坏业务 / 最小改动 / 可回滚 / 可验证。

## 0. 角色文档缺失声明（必记录项）
`docs/` 下**未发现** `产品经理.md / 后端开发工程师.md / 技术负责人.md / 小程序开发工程师.md / 质量把控工程师.md / UI-UX设计师.md / Web管理后台开发工程师.md` 等 7 个角色文档（仅 `docs/loop/audit/` 下存在历史审计 markdown）。
- 处理：按优化 Loop 协议「角色文档缺失时在 QA.md 记录、不擅自补全职责」。
- 本轮以**用户本轮回合指令中定义的各角色职责** + 项目 agent 角色定义为准执行，未自行发明职责边界。
- 若用户后续补齐独立角色文档，以文档为准重新对齐。

## 1. 本轮已执行但需登记的中/低风险项
以下项执行前已 grep 验证「前端零消费 / 纯口径统一 / 行为不变」，编译/类型门禁通过，属预批准低风险，记录于此供验收复核：

| 编号 | 项 | 验证 | 状态 |
|---|---|---|---|
| BE-OPT-02 | 删除 `DishVO.distance` 死字段 + `DishMapper.xml` 对应 resultMap（该列随 §7.10 下线后恒为 null，前端 `client/api/dish.ts:54`、`api/canteen.ts:14` 的 `raw.distance` 读为无害死读，已由 `stores/dish.ts:319 withLocalDistance` 本地 Haversine 覆盖） | 前端零消费；`mvn compile` 0 | 已执行 |
| BE-OPT-05 | 合并 `enrichImages`/`enrichDishAdminImages` 为重载（纯重构，签名/行为不变） | `mvn compile` 0 | 已执行 |
| BE-OPT-07 | `ReviewServiceImpl` 重复评价文案 EN→中文统一（同错误码，仅文案口径） | `mvn compile` 0 | 已执行 |
| UI-OPT-04 | `ReportModal` 补焦点捕获 + ESC 关闭（无障碍增强，非新入口） | 小程序 type-check 0 | 已执行 |

## 2. 待用户决策（中高风险，本轮未执行，不阻塞低风险项）

| 编号 | 问题 | 选项 | 影响范围 | 阻塞其他任务 |
|---|---|---|---|---|
| TL-OPT-03 | `docs/loop/launch-checklist.md` D5 仍列 `api/admin.ts`/`stores/adminUserStore.ts`/`views/admin/AdminManageView.vue` 等「待删除⬜」，与 `STATUS.md`「已物理删除 ✅」冲突。是否确认这些文件确已删除并将该文档整体标记为历史？ | A 确认已删、标记历史 / B 复核删除真实性 | 仅文档一致性 | 否 |
| TL-OPT-04 | `docs/loop/QUESTIONS.md` 内 Open 段与新闭环条目重复登记，是否整理为单一真值？ | A 整理 / B 保留 | 仅文档 | 否 |
| TL-OPT-05 | `stall.business_hours`（D6）列删除的部署顺序：`server` 当前代码是否已无 `SELECT s.business_hours`？确认后再执行 DROP，避免线上 500。 | A 代码已无引用即 DROP / B 等下次发布窗口 | db 脚本（幂等） | 否 |
| BE-OPT-03 | `ReviewMapper.xml` 三分页 SQL 公共片段抽取去重（行为不变，需回归测试）。 | A 抽取 / B 维持现状 | mapper SQL | 否 |
| BE-OPT-04 | `CanteenService.listCanteens(lat,lng)` 冗余入参（`lat/lng` 已不参与计算，距离改前端本地算）。改动接口签名=契约变更。 | A 精简签名 / B 保留 | service 接口契约 | 否 |
| BE-OPT-06 | `ReviewServiceImpl` 三列表方法（`listByDishId/StallId/CanteenId`）结构雷同，抽模板去重。 | A 抽取 / B 维持 | service | 否 |
| UI-OPT-01 | `Modal`/`BaseSheet` 缺 Tab 焦点循环陷阱（WCAG 2.4.3/2.1.2）。纯 a11y 加固。 | A 本轮补焦点陷阱 / B 延后 | web 弹层组件 | 否 |
| MP-OPT-03 | `client/uni.scss` 当前仅注释（13 个 token 已删），是否删除？它是 uni-app 全局 SCSS 入口，删除可能破坏构建。 | A 保留（框架入口）/ B 删除（需验证构建） | 小程序构建 | 否 |
| MP-OPT-05 | `client/src/manifest.json` 含 `app-plus`/`quickapp`/`ios` 多端模板配置，当前仅 mp-weixin。是否收敛？ | A 保留（多端预留）/ B 收敛 | 配置 | 否 |

> 说明：以上项若执行，须先确认不扩大功能、可回滚，并补回归验证；建议用户在醒来后逐条拍板。

## 2.1 拍板结果（2026-09-15，用户已答复）
| 编号 | 决定 | 执行情况 |
|---|---|---|
| TL-OPT-03 / TL-OPT-04 | 一并收口 | ✅ 已执行：`launch-checklist.md` 顶部加「历史文档」标注并修正 D5 与现状冲突条目；`QUESTIONS.md` 收敛为单一真值 |
| TL-OPT-05 | 一并收口 | ✅ 已执行：grep 确认 server 代码（.java/.xml）零引用；`schema.sql` 幂等 DROP 已就位，随下次部署生效；`STATUS.md` D6 关闭 |
| BE-OPT-03 / BE-OPT-06 | **暂缓** | 按用户决定暂缓（当前缺充分集成测试覆盖，SQL/模板重构待补测试后再评估）；未执行 |
| BE-OPT-04 | 精简 | ✅ 已执行：`GET /canteens` 去 `lat/lng`（server Controller/Service/Impl 三层 + client `api/canteen.ts`/`types/canteen.ts` 死读死字段清理；web 经 grep 确认本就未传参，无改动） |
| UI-OPT-01 | 补 | ✅ 已执行：web `Modal.vue` + client `useSheetFocus.ts` Tab 焦点循环陷阱（对称注册/卸载，既有 ESC/聚焦行为不变） |
| MP-OPT-03 / MP-OPT-05 | 保留 | PM 按「最小改动」自行决策保留（uni.scss 为 uni-app 全局 SCSS 入口；manifest 多端配置为多端预留） |

## 3. 本轮明确跳过（低风险但前置已满足 / 收益极小 / 保留意图）
- **MP-OPT-04**：根 `.gitignore` 已覆盖 `dist/`、`node_modules/`，且二者均未跟踪，目标已达成，无需改动。
- **WEB-OPT-05**：api 列表归一重复 `pageRecords` 调用，收益极小，本轮不做。
- **MP-OPT-03（uni.scss）/ MP-OPT-05（manifest 多端）**：保留（框架入口 / 多端预留意图）。

## 4. 回滚方式
- 单文件回滚：`git checkout -- <file>` 或 `git revert <commit>`。
- 整轮回滚：`git reset --hard 68e76ee`（基线），或 `git checkout backup/optimize-loop-20260915`。
- 4 份交付文档（OPTIMIZATION_CHANGELOG.md / DELETED_FILES.md / QA.md / ACCEPTANCE_CHECKLIST.md）随本轮提交，回滚时一并移除。
