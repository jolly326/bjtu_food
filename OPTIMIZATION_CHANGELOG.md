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

## 下一轮建议
待用户就 QA.md §2 中高风险项拍板后，可启动 Round 2：
1. 若 BE-OPT-03/06、UI-OPT-01 获准，需补回归测试（mapper SQL / service 列表 / 弹层 a11y）。
2. 文档一致性（TL-OPT-03/04/05）可在用户确认后一次性收口。
3. 若继续空转（无新低风险项且中高风险全在 QA），按协议停止。
