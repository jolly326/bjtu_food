# 前端 UI 冗余收敛方案（UI-01 ~ UI-10）

> 来源：2026-09-15 项目联合审阅 · UI-UX 设计师产出。PM 已拍板：UI-06 取 A、动效分处置、UI-03 取 tokens.ts 唯一真源。
> 本文档为 Web/小程序工程师落地依据；spec 口径以 project_spec.md 登记为准。

## 一、收敛契约与迁移

### UI-01 统一统计卡 StatCard（P1）
- 目标：`web/src/components/common/StatCard.vue` 成为唯一统计卡。
- 契约：新增 `variant: 'stack' | 'inline'`（默认 stack）、`size: 'md' | 'lg'`（默认 md）、`interactive?: boolean`（默认 false，替代 clickable 语义）；slots `icon`/`action`/`default`；event `click`。**删除 `delay` prop 与 `@keyframes sc-enter`**（装饰性入场，PM 已拍板删除）。
- 迁移：`DishDetailView.vue:463-464` 保持默认 stack 不变；`DashboardView` 的 `.todo-card`→`variant="inline" size="lg" interactive`，`.metric-card`→`variant="inline" size="md" interactive`；删除 `DashboardView.vue:239-285` 全部私有规则。
- 取值：圆角 `--radius-card`；边框 1px `--border-light`；padding `lg=var(--space-5) var(--space-6)` / `md=var(--space-4) var(--space-5)`；图标容器 48/40px + `--radius-md` + `--color-primary-bg`；数值 `font-4xl`/`font-2xl` + `weight-bold` + `tabular-nums` + `--tracking-tight`。

### UI-02 ConfirmDialog 组合 Modal（P2，等 spec 登记）
- 目标：`Modal.vue` 为唯一弹层基座；`ConfirmDialog.vue` 改为基于 Modal 实现（不再自带 Teleport 与样式副本）。
- Modal 新增：`variant: 'dialog' | 'confirm'`（默认 dialog）、`danger?: boolean`、`confirmText/cancelText`、`confirmLoading`、`onConfirm`；slot `footer`；event `confirm`。`variant='confirm'` 时：`role="alertdialog"`、隐藏右上角 X、默认聚焦「取消」。
- 迁移后 ConfirmDialog 仅保留 confirmStore 绑定与聚焦逻辑。
- 取值：overlay `--el-mask-color` + `--blur-material` + z-index 2000；box 圆角 `--radius-card`、顶部 3px 主色条、padding `var(--space-8) var(--space-8) var(--space-5)`、阴影 `--shadow-pop`。
- **动效口径（已拍板：分处置）**：Web 弹层**保留** 220ms scale+opacity 过渡（spec §4.9 登记 Web 豁免）；卡片类装饰性入场（sc-enter）删除。

### UI-03 小程序色值唯一真源（P2，等 spec 登记）
- 根治：新增 `client/scripts/gen-css-vars.ts` + `client/src/theme/generated-colors.css`（受版本控制）；`tokens.ts` 新增 `export const CSS_VARS: Record<string,string>`；脚本遍历生成 `page{…}` 颜色块；App.vue 只保留**非颜色** token（圆角/间距/字号/高度）并 `@import` 生成物。
- `npm run gen:tokens` 挂到 type-check 前置 + CI diff 校验（生成物与仓库副本不一致即失败）。
- 最低成本先落 CI 守卫，生成脚本可后置。真机需验证 WXSS `@import` 承载 `page{}` 变量块。

### UI-04 三态样式回收（P0）
- 事实修正：`.spin` 与 `@keyframes spin` 并不在 `shared.css:121-129`（该文件只有 `.state-box/.state-err`），实际由 DashboardView 与 DataTable 各持一份 → 本项是「补全局」而非「删重复」。
- 在 `shared.css` 补 `.spin` + `@keyframes spin`；新增 `.state-box--inline`（`display:flex; align-items:center; justify-content:center; gap:var(--space-2); padding:var(--space-6) var(--space-4)`）；可选组件 `common/StateBlock.vue`（props `state:'loading'|'error'`, `message?`, `inline?`; slot=重试按钮; event `retry`）。
- 删除 `DataTable.vue:396-410,459-476` 与 `DashboardView.vue:231-235` 私有副本。
- `.spin` 取值：16px、border 2px `--border-color`、`border-top-color: var(--color-primary)`、50%、`spin .7s linear infinite`；reduced-motion → `animation-duration: 1.4s`（**降速而非停转**，停转会误导为「卡死」）。

### UI-05 竞态守卫收敛 useAsyncGuard（P0）
- 新建 `web/src/composables/useAsyncGuard.ts`（目录需新建）。返回 `{ loading, error, run }`；`run(fn)` 内部 `++token`、置 loading、catch 填 error、finally「仅未过期」置 loading=false；过期响应整份丢弃（不写 loading/error/数据）。**数据赋值留给调用方回调**，避免行为漂移。
- 迁移顺序：`OperationLogView.vue:32-85`（最规整，先立样板）→ `FeedbackView.vue:61-98` → `ApplyReviewView.vue:45-63`。

### UI-06 Web 圆角对齐 spec（P2，已拍板取 A）
- `web/src/styles/variables.css`：`--radius-card: 16px`、`--radius-sheet: 20px 20px 0 0`（对齐 spec §4.2）；同步修正 `shared.css:86-92` 失真注释。

### UI-07 字典表单收敛 useDictForm（P1）
- 新增 `web/src/composables/useDictForm.ts`：`({ initial, validate, submit }) → { form, formErrors, submitting, handleSubmit, reset }`；语义固化「打开即重置 / 校验失败不关窗保留数据 / 失败 toast 取后端 message 优先 / finally 复位 submitting」。
- 共享样式上提 `shared.css`：`.mt`(`margin-top: var(--space-3)`)、`.dict-hint`(`--font-xs` + `--text-light` + `margin-bottom: var(--space-3)`)、`.field-hint`、`.row`/`.flex-1`。
- 两个弹窗（CanteenCreateDialog/StallCreateDialog）保留各自模板（宽度 520/480 与字段不同），仅抽逻辑+样式。

### UI-08 删除悬空 token（P0）
- 删除 `client/src/theme/tokens.ts:25,26,43,44,55,56` 六个零消费键。前置：全仓 grep `--text-white-soft/--text-white-faint/--overlay-dark-deep/--overlay-dark-mid/--glass-highlight/--glass-highlight-soft` 复核为 0。删后留一行删除登记注释。

### UI-09 ApplyReviewView 改名（P3）
- `views/admin/ApplyReviewView.vue` → `views/audit/ReviewAuditView.vue`（与 AuditManageView 同目录）。调用点仅 `AuditManageView.vue:13,73` 两处；同步改文件头注释 `:3-8`。侧栏文案以技术负责人确认为准。

### UI-10 裸字号回落标度（P0）
- `UserView.vue:225` 14px→`--font-base`；`UserActivityModal.vue:146` 18px→`--font-xl`；`DashboardView.vue:255/282` 24/20px→新增组件 token `--stat-icon-lg: 24px` / `--stat-icon-md: 20px`（图标字号非正文标度，需登记）；`:285 margin-top: 2px`→`var(--space-1)`；`DataTable.vue:356` 10px→`--font-xs`(12px)，保留 `opacity: .55` 弱化。

## 二、验收标准（前端自检 + QA 复核）

- `grep -rn "font-size: *[0-9]*px" web/src` = 0（`variables.css` 除外）
- `grep -rn "@keyframes spin" web/src` = 1；`grep -rn "\.spin *{" web/src` = 1
- `grep -rn "reqToken" web/src/views` = 0（除 composable）
- `grep -rn "todo-card\|metric-card\|sc-enter" web/src` = 0
- `grep -rn "ApplyReview" web/src` = 0
- `.state-box`/`.spin` 在 Dashboard / DataTable / 任意列表页视觉一致

## 三、风险与回归面

| 项 | 风险 | 回归必测 |
|---|---|---|
| UI-10/08/04 | 极低 | 纯样式；UI-08 先 grep 复核零消费 |
| UI-05 | 中 | 连续快速切筛选/输入 → 数据不错乱；单页失败后 loading 不复归 true |
| UI-01 | 中 | 键盘 Tab+Enter 直达、done 态图标转绿、hover 抬升、focus-visible |
| UI-02 | 中高 | ESC 关闭、默认聚焦取消、快速关→开不闪烁（hideTimer 清理）、破坏性操作二次确认全链路 |
| UI-07 | 低 | 新建食堂/档口失败后弹窗不关且数据保留；Stall 内嵌「+ 新建食堂」回填仍生效 |
| UI-03 | 中 | 真机首屏全页颜色回归（开发工程师执行） |
| UI-06 | 低 | 深色控制台下 16px 目视 |

## 四、落地批次

- **P0 立即（零 spec 依赖）**：UI-10 → UI-08 → UI-04 → UI-05
- **P1（需 UI 复审视觉，无 spec 依赖）**：UI-01 → UI-07
- **P2（spec 口径落地后动）**：UI-02、UI-06、UI-03
- **P3（需技术负责人确认文案）**：UI-09
