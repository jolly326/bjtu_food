# DELETED_FILES.md（优化 Loop · 删除/归档清单）

> 基线：develop @ `68e76ee`。原则：删除前确认「未被引用、未被构建使用、未被文档索引、未被业务依赖」；不确定则归档而非删除。

## 一、物理删除的文件
| 文件 | 类型 | 删除理由 | 引用复核 |
|---|---|---|---|
| `client/shims-uni.d.ts` | 类型声明 | 根级重复 uni hooks 声明；`tsconfig.json` include 仅 `src/**/*`，该文件未被编译纳入，全仓 0 引用 | grep `shims-uni` 仅定义处命中 |
| `web/src/static/images/image.png` | 静态资源 | 全仓 0 引用死资源 | grep `image.png|/images/|static/` 0 命中 |

## 二、删除的依赖 / 代码（非文件删除）
| 项 | 位置 | 理由 | 复核 |
|---|---|---|---|
| `vue-i18n` 依赖行 | `client/package.json` | 全仓 0 消费（`vue-i18n|useI18n|$t|createI18n` 0 命中） | type-check 0 |
| `UserService.getByUsername` | `auth/service/UserService.java` + impl | 0 调用方，登录走 openid/email | grep 0 |
| `DishVO.distance` 字段 + resultMap | `dish/dto/DishVO.java` + `DishMapper.xml` | 列随 §7.10 下线恒为 null，前端零消费 | grep 前端 0 |
| `enrichDishAdminImages` | `DishServiceImpl.java` | 与 `enrichImages` 逻辑一致，合并为重载 | grep 0 |
| 重复评价文案（EN） | `ReviewServiceImpl.java:205` | 统一为中文口径 | compile 0 |
| `adminStore.loadAll()` | `web/src/stores/adminStore.ts` | 0 调用方 | grep 0 |
| `uploadApi`/`dashboardApi` 重导出 | `web/src/api/index.ts` | 调用方均走直接导入，无引用 | grep 0 |
| `STATUS_ACTIVE`/`STATUS_INACTIVE` | `web/src/constants/index.ts` | 0 引用，adapter 用字面量 | grep 0 |

## 三、归档（移入 `_archive/`，非物理删除）
> 保留于仓库内，仅 relocate，满足「过程性文档清理」且零引用断裂风险。

| 原路径 | 新路径 |
|---|---|
| `docs/loop/audit/2026-09-14-backend.md` | `docs/loop/_archive/audit/` |
| `docs/loop/audit/2026-09-14-client.md` | `docs/loop/_archive/audit/` |
| `docs/loop/audit/2026-09-14-CONSOLIDATED.md` | `docs/loop/_archive/audit/` |
| `docs/loop/audit/2026-09-14-qa.md` | `docs/loop/_archive/audit/` |
| `docs/loop/audit/2026-09-14-tech-lead.md` | `docs/loop/_archive/audit/` |
| `docs/loop/audit/2026-09-14-uiux.md` | `docs/loop/_archive/audit/` |
| `docs/loop/audit/2026-09-14-web.md` | `docs/loop/_archive/audit/` |
| `docs/loop/ITER-001.md` | `docs/loop/_archive/` |

## 四、明确未删除（避免误判）
- 核心文档：`project_spec.md` / `architecture.md` / `api-design.md` / `database.md` / `product-blueprint.md` / `PRODUCT_LOOP.md` / `ui-audit-prelaunch.md` —— 保留。
- `docs/loop/dish-proofread-checklist.md`、`STATUS.md`、`QUESTIONS.md`、`launch-checklist.md`、`audit/backend.md`、`audit/frontend.md`、`ITER-003-AUDIT.md` —— 仍被引用或具在办价值，保留（其中 TL-OPT-03/04 的整理待用户决策，见 QA.md）。
- `client/uni.scss` —— uni-app 全局 SCSS 入口，保留（MP-OPT-03）。
- `client/src/manifest.json` 多端配置 —— 保留（多端预留，MP-OPT-05）。
- 业务配置 / CI-CD / 密钥 / 环境变量文件 —— 未触碰。

## 五、性质确认
全部删除/归档项均为**死代码、冗余资源、过程性文档**；**无任何核心文档、业务配置、CI/CD、密钥、环境变量被删除**。
