# web-admin-feedback-loop Specification

## Purpose
为管理后台建立反馈驱动的治理闭环：反馈列表可直达其关联对象的编辑位置、处理动作触发用户回执，使管理员能「看到问题 → 修正数据 → 回传结果」一气呵成；页面归属为**「反馈」与「评价」两个一级页**（2026-09-15 由原「内容审核」聚合页拆分，**一级导航 4 项：菜品 / 评价 / 反馈 / 学生账号**，与路由 1:1；命名口径 2026-09-15 由「信息管理 / 评价管理 / 反馈处理 / 用户与系统」扁平化重写，见下），评价侧只做**事后处置**（隐藏 / 显示 / 删除），并承载 UGC 配图展示（配图链路与判定口径见 `ugc-media-security`）。

> **2026-09-15 变更（权威 `project_spec.md` §7.24「取消人工复核」）**：内容安全检测 `pass` / `review` 一律放行、仅 `risky` 拒绝 ⇒ 管理后台**不再承担内容复核职责**。原「评价安检复核队列（放行 / 驳回，`PUT /admin/reviews/{id}/sec-state`）」Requirement **整体废止**；原「内容审核页」Requirement 改注为新的页面划分；原「工作台最小口径」Requirement 随 2026-09-15 工作台下线一并废止（`GET /admin/dashboard` 已删除）。恢复任一已废止能力须**重新拍板**。

> **2026-09-15 追加变更（权威 `project_spec.md` §7.25，用户拍板两项）**：① **管理端「操作日志」全链删除**——`operation_log` 表、`OperationLog*` 后端类族、`@AuditLog` 注解与 `AuditLogAspect` 切面、`GET /admin/operation-logs` 端点、Web `OperationLogView.vue` 与 `api/operationLog.ts` **全部删除（2026-09-15）**；本 spec 内凡引用「操作日志页 `/dashboard/system`」或操作留痕的表述**一律失效**，管理端 SHALL NOT 提供任何操作留痕 / 审计追溯能力。② **Web IA 扁平化**——删中间聚合层（`ContentManageView` 信息管理聚合壳 / `SystemManageView` 用户与系统分类卡层 / `AccountView` 透传壳），页面层级统一 ≤2，全站 SHALL NOT 出现 `.stat-inline` 只读统计块、页头解释句与只读提示块。

## Requirements

### Requirement: 反馈关联对象可直达

反馈详情中，当反馈携带关联对象（`relatedType` 为 `review` 评价或 `dish` 菜品）时，关联对象 SHALL 以可点击形式呈现，点击 SHALL 跳转至该对象的可达位置：菜品 SHALL 跳转菜品编辑详情，评价 SHALL 给出可定位的标识（评价无独立页面）。无关联对象时 SHALL 以占位符表示，不出现可点样式。

（**2026-09-15 呈现定型**：举报类（`relatedType=review`）关联评价 SHALL 以**主色文本链接「评价 #id →」**呈现（**不再使用红色 pill**），点击 SHALL 跳转「评价」页并携带 `?rid=<id>` 深链定位；见 `project_spec.md` §7.25 第 2 条与 §7.23 第 5 条。）

#### Scenario: 纠错反馈直达菜品编辑

- **WHEN** 管理员查看一条「信息不对」反馈（关联菜品）详情并点击关联项
- **THEN** 跳转到该菜品的后台编辑详情页

#### Scenario: 举报反馈标识评价

- **WHEN** 管理员查看一条举报类反馈（关联评价）详情
- **THEN** 关联项以「评价 #ID」可识别标识呈现，可复制该标识

#### Scenario: 无关联对象

- **WHEN** 管理员查看一条未携带关联对象的反馈
- **THEN** 关联项显示占位符且不可点击

### Requirement: 反馈配图展示（2026-09-13）

反馈列表与详情 SHALL 展示反馈携带的配图：详情中配图 SHALL 以缩略图网格呈现（≤3 张，与小程序提交上限一致），点击 SHALL 可放大查看原图；无配图反馈维持既有布局不出现空占位。配图来源为 COS URL（`user_feedback.images`），加载失败 SHALL 呈现占位而不破坏详情布局。

#### Scenario: 带图反馈可查看

- **WHEN** 管理员打开一条携带 2 张配图的反馈详情
- **THEN** 详情展示 2 张配图缩略，点击可放大查看，处理动作（回复 / 标记处理）不受影响

#### Scenario: 无图反馈布局不变

- **WHEN** 管理员打开一条无配图的反馈详情
- **THEN** 不出现配图占位区，详情布局与配图能力上线前一致

### Requirement: ~~评价安检复核队列~~（已废止：2026-09-15）

> **已废止（2026-09-15 用户拍板「取消人工复核」，权威 `project_spec.md` §7.24）**：原 Requirement「评价安检复核队列（2026-09-13）」整体作废——复核队列、列表 `secState` 筛选、**放行**（`sec_state='pass'`）/ **驳回**（`sec_state='rejected'`）两个动作、端点 `PUT /admin/reviews/{id}/sec-state`（入参 `{ state: "pass" | "rejected" }`）**均已删除**；`sec_state` 列亦已全链退役（不落库、不参与可见性）。
>
> **现口径（SHALL）**：管理后台 SHALL NOT 提供任何内容复核队列 / 安检筛选 / 放行·驳回动作，SHALL NOT 呈现「待复核」统计。评价治理收敛为**事后处置**：`PUT /admin/reviews/{id}/hide`（隐藏 / 显示）与 `DELETE /admin/reviews/{id}`（删除），位于**「评价」页** `/dashboard/reviews`（`ReviewManageView`，2026-09-15 由 `ReviewAuditView` 改名；导航名于 2026-09-15 IA 扁平化后由「评价管理」改为「评价」）；评价列表 SHALL NOT 返回 / 展示安检态字段（原 `secState` 已删除）。

### Requirement: 处理动作触发用户回执

管理员将反馈标记为已处理（含回复内容）时，后台 SHALL 一并触发面向提交人的回执投递（细则见 `feedback-receipt`）。处理结果 SHALL 以管理员回复为回执正文；无回复时 SHALL 仍可标记处理并给出通用回执文案。
（**2026-09-15 收紧**：`reply` 恒必填——1~1000 字、纯空白视为未填写 → `400`；「不采纳 / 退回」`outcome='rejected'` 时另需必填 `reject_reason`，见 `project_spec.md` §7.23 第 5 条与 `docs/api-design.md` §5.4。）

#### Scenario: 处理后回执已投递

- **WHEN** 管理员填写回复并标记某条反馈为已处理
- **THEN** 该处理成功落库，且对应提交人（若可归属）收到处理结果通知

#### Scenario: 缺回复拒绝提交

- **WHEN** 管理员未填写回复直接提交处理
- **THEN** 服务端返回 `400` 拒绝（纯空白同样视为未填写），反馈状态不变

### Requirement: 管理端页面划分（2026-09-15 修订；原「审核页仅含反馈与评价分区」）

管理端一级导航 SHALL 为 **4 项，与路由 1:1**：**菜品** `/dashboard/content`（`DishManageView`，默认落地）/ **评价** `/dashboard/reviews`（`ReviewManageView`，隐藏 / 显示 / 删除 = 事后处置）/ **反馈** `/dashboard/feedback`（`FeedbackView`，处理闭环）/ **学生账号** `/dashboard/system`（`UserView`）。**（2026-09-15 扁平化重写：原命名「信息管理 / 评价管理 / 反馈处理 / 用户与系统」不再使用；路径不变。）** 原「内容审核」聚合页（`AuditManageView`）SHALL NOT 存在，其职责 SHALL 拆为上述「评价」「反馈」两页。**SHALL NOT 存在的中间层（已删除 2026-09-15，不得重建）**：`ContentManageView`（信息管理聚合壳）、`SystemManageView`（用户与系统分类卡层）、`AccountView`（透传壳）、`OperationLogView`（操作日志页）。**页面层级 SHALL ≤2 层**（页头 H1 + 主操作 → 主体 筛选 + 列表 / 表单），SHALL NOT 出现 `.stat-inline` 只读统计块（数量只由表格 footer 出现一次）、页头解释句与只读提示块。后台 SHALL NOT 呈现「UGC 申请」分区或任何实体贡献申请相关列表、待办计数与筛选；原申请链路（提交申请 → 管理员审核 → 自动建实体）SHALL NOT 存在于用户端与管理后台。

#### Scenario: 旧深链兜底可达

- **WHEN** 管理员打开旧深链 `/dashboard/audit`（可带查询参数）
- **THEN** 前端兜底重定向：`tab=feedback*` 或 `apply*` → `/dashboard/feedback`，其余 → `/dashboard/reviews`，**整份保留查询参数**（如 `fid` 深链仍可达）

#### Scenario: 无申请链路

- **WHEN** 检索管理后台路由、接口调用与用户端入口
- **THEN** 不存在申请提交、申请列表、申请审核的可用入口与调用，亦不存在「内容审核」聚合页

### Requirement: ~~工作台最小口径~~（已废止：2026-09-15）

> **已废止（2026-09-15 用户拍板「去工作台」，权威 `project_spec.md` §0.4.1 / §5.z D-工作台）**：原 Requirement「工作台最小口径」整体作废——工作台页面 `DashboardView` 与 `GET /admin/dashboard` 接口**已删除**，`dashboard` 业务域不再存在。
>
> **现口径（SHALL）**：管理后台 SHALL NOT 提供任何全局聚合看板 / 总览页 / ECharts 图表 / 跨模块聚合统计，亦 SHALL NOT 设任何分类卡 / 聚合壳中间层。待办可见性 SHALL 由「**反馈**」入口徽标（待处理反馈数，数据源 `feedback.status='pending'`）＋ 各业务页**表格 footer 统计**承担；~~近期操作由操作日志页 `/dashboard/system` 承载~~ —— **已删除（2026-09-15，见本文件抬头追加变更与 `project_spec.md` §7.25 第 1 条）**，后台 SHALL NOT 提供近期操作列表或任何操作留痕；默认落地页为**菜品页** `/dashboard/content`（带 `?tab=dish` 的历史写法作废）。恢复聚合看板须重新拍板（PR-04）。
