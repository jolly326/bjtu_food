# OPTIMIZATION_CHANGELOG.md（优化 Loop）

- 起始：develop @ `68e76ee`（回滚分支 `backup/optimize-loop-20260915`）
- 日期：2026-09-15
- 轮次：Round 1
- 总原则：不扩大功能 / 不破坏业务 / 最小改动 / 可回滚 / 可验证
- 角色文档：docs/ 下 7 个角色 .md 缺失，按 Loop 协议记入 QA.md，以用户指令中的角色职责定义为准。

---

## Round 1 — 执行项（已落地，低风险）

| 编号 | 角色 | 目标 | 涉及文件 | 风险 | 验证 |
|---|---|---|---|---|---|
| BE-OPT-01 | 后端 | 删除零调用死方法 `getByUsername` | `auth/service/UserService.java`、`auth/service/impl/UserServiceImpl.java` | 低 | `mvn compile` 0 |
| BE-OPT-02 | 后端 | 删除恒为 null 的 `DishVO.distance` 字段 + `DishMapper.xml` resultMap（前端零消费） | `dish/dto/DishVO.java`、`src/main/resources/mapper/DishMapper.xml` | 低 | `mvn compile` 0 |
| BE-OPT-05 | 后端 | 合并 `enrichImages`/`enrichDishAdminImages` 为重载（去重，行为不变） | `dish/service/impl/DishServiceImpl.java` | 低 | `mvn compile` 0 |
| BE-OPT-07 | 后端 | 统一重复评价文案 EN→中文（同错误码，仅口径） | `review/service/impl/ReviewServiceImpl.java` | 低 | `mvn compile` 0 |
| MP-OPT-01 | 小程序 | 移除零消费依赖 `vue-i18n` | `client/package.json` | 低 | `npm run type-check` 0 |
| MP-OPT-02 | 小程序 | 删除根级重复 uni 类型声明 `shims-uni.d.ts`（tsconfig 仅含 src，0 引用） | `client/shims-uni.d.ts` | 低 | `npm run type-check` 0 |
| MP-OPT-04 | 小程序 | 确认 `dist/`、`node_modules/` 已被根 `.gitignore` 覆盖（无需改动） | `.gitignore` | 低 | 不适用（跳过） |
| UI-OPT-04 | 小程序 | `ReportModal` 补焦点捕获 + ESC 关闭（a11y 增强，非新入口） | `client/src/pages/detail/dish/ReportModal.vue` | 低 | `npm run type-check` 0 |
| WEB-OPT-01 | Web | 删除零调用 `adminStore.loadAll()` | `web/src/stores/adminStore.ts` | 低 | `npm run build` 0 |
| WEB-OPT-02 | Web | 删除未使用重导出 `uploadApi`/`dashboardApi` | `web/src/api/index.ts` | 低 | `npm run build` 0 |
| WEB-OPT-03 | Web | 删除零引用常量 `STATUS_ACTIVE`/`STATUS_INACTIVE` | `web/src/constants/index.ts` | 低 | `npm run build` 0 |
| WEB-OPT-04 | Web | 删除全仓 0 引用死资源 `image.png` | `web/src/static/images/image.png` | 低 | `npm run build` 0 |
| UI-OPT-02 | Web | `ImageUpload` hover 遮罩裸 rgba→语义 token `--el-mask-color` | `web/src/components/ImageUpload.vue` | 低 | `npm run build` 0 |
| TL-OPT-01 | 技术负责人 | 过程性审计报告归档（非删除） | `docs/loop/audit/2026-09-14-*.md`（7）→ `docs/loop/_archive/audit/` | 低 | `git mv` |
| TL-OPT-02 | 技术负责人 | 历史轮次报告归档（非删除） | `docs/loop/ITER-001.md` → `docs/loop/_archive/` | 低 | `git mv` |

## Round 1 — 跳过（低风险但前置已满足 / 收益极小 / 保留意图）
- **MP-OPT-04**：`.gitignore` 已覆盖 dist/node_modules，目标达成。
- **WEB-OPT-05**：api 列表归一重复 `pageRecords` 调用，收益极小。
- **MP-OPT-03（uni.scss）/ MP-OPT-05（manifest 多端）**：保留（框架入口 / 多端预留）。

## Round 1 — 未执行（中高风险，已入 QA.md 待用户决策）
TL-OPT-03 / TL-OPT-04 / TL-OPT-05 / BE-OPT-03 / BE-OPT-04 / BE-OPT-06 / UI-OPT-01 / MP-OPT-03 / MP-OPT-05 —— 详见 QA.md §2。

## Round 2 — 用户拍板后执行（2026-09-15）

| 编号 | 角色 | 目标 | 涉及文件 | 风险 | 验证 |
|---|---|---|---|---|---|
| BE-OPT-04 | 后端+小程序+Web | `GET /canteens` 精简 `lat/lng` 契约（距离由前端本地 Haversine 计算） | server `CanteenController/CanteenService/CanteenServiceImpl`；client `api/canteen.ts`、`types/canteen.ts`（死读死字段清理）；web 经 grep 确认本就未传参 | 中（契约变更，用户已拍板） | server `mvn compile` 0；client `type-check`/`build:mp-weixin` 0 |
| UI-OPT-01 | Web+小程序 | 弹层 Tab 焦点循环陷阱（a11y，焦点不逃逸到背景） | `web/src/components/Modal.vue`、`client/src/composables/useSheetFocus.ts`（BaseSheet/ReportModal 自动获益） | 中（纯 a11y，用户已拍板） | web `build` 0；client `type-check` 0 |
| TL-OPT-03/04/05 | 技术负责人 | 文档/db 收口：launch-checklist 历史标注+D5 修正；QUESTIONS.md 真值收敛；`business_hours` 代码零引用确认 + D6 关闭 | `docs/loop/launch-checklist.md`、`docs/loop/QUESTIONS.md`、`docs/loop/STATUS.md` | 低 | grep 复核（server .java/.xml 零引用） |
| 蓝图/契约同步 | 技术负责人 | spec/product-blueprint/api-design/architecture 对齐已执行事实 | `project_spec.md`、`product-blueprint.md`(v1.2)、`api-design.md`、`architecture.md` | 低 | 文档对账 |
| BE-OPT-03/06 | — | 用户拍板**暂缓**（待补集成测试后再评估） | — | — | — |

## 停止判定（Round 2 后）
- 低风险项已全部完成；用户拍板项已执行完毕；唯一遗留 BE-OPT-03/06 为用户明示暂缓项（挂起而非空转）。
- **Loop 停止**：继续执行只剩暂缓项与运行时冒烟（需环境），无新的低风险优化项。

## 下一轮建议
1. 补齐评价列表三维度（菜品/档口/食堂）集成测试后，再评估 BE-OPT-03/06 去重重构。
2. 部署窗口执行 `schema.sql` 幂等 DROP（`stall.business_hours`），完成 D6 的线上收尾。
3. 运行时冒烟（游客 token / 受保护接口 / 4031 / /admin 口令）需具备环境后补做。
