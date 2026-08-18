# 全局页面审查报告：Bug / 性能 / UIUX

> 审查对象：「食在交大」三端（client 微信小程序、web 管理后台、server 接口在页面消费侧）
> 审查方式：静态走查 + 双子代理遍历（code-explorer ×2）+ 逐文件人工核验
> 运行时实证：playwright 因本机未安装浏览器且 dev server 未启动，**未能实跑**（web 端结论以静态走查为准，已注明）
> 审查日期：2026-08-17
> 前提：client 当前 `vue-tsc --noEmit` 通过；`dev_err.txt`/`dev_log.txt` 为全量优化中途残留，已排除误报

---

## 0. 优先级矩阵

| 优先级 | 数量 | 说明 |
|--------|------|------|
| **P0 阻断** | 2 | 漏审导致审核遗漏 / 小程序轮播切后台停摆 |
| **P1 重要** | 7 | 漏搜、重复审核、并发、N+1 退化、UI 漏审隐患、性能内嵌 filter、登录态兜底 |
| **P2 体验** | 6 | 列表无虚拟滚动、定时器清理、视觉一致性、a11y、骨架屏缺口、解锁失败文案 |

> 三端整体质量较高（骨架屏、空态引导、reduced-motion、ECharts dispose、401 区分、引用计数均已落地）。以下问题为可改进点，非全量故障。

---

## 1. 小程序 client 端

### 1.1 功能性 Bug

| # | 页面/组件 | 文件:行 | 现象 | 优先级 | 建议 |
|---|-----------|---------|------|--------|------|
| C-01 | BroadcastBar.vue | `onHide(stop)` 后无 `onShow(start)` | 用户离开首页再返回，`onHide` 已 `stop()`，但 `onShow` 未重新 `start()`，轮播**永久停止**直到触摸 | **P0** | 增加 `onShow(() => { if (!store.paused) start() })`，与 `onHide(stop)` 配对 |
| C-02 | pages-detail/review.vue | `searchDishList` 已加 `searchSeq` 守卫 | 竞态已修，但首字符输入即发请求，无 300ms 防抖，弱网抖动 | P2 | 加 `debounce(300)` 后再触发搜索 |
| C-03 | pages-detail/moment.vue | `applyReq` 表单 | 申请转发为动态时，未校验 `selectedDish` 必选（仅前端禁用按钮，接口侧未兜底） | P1 | `applyReq` 服务端校验 `dishId` 非空，否则 400 |
| C-04 | feedback/index.vue | 提交反馈 | 图片上传失败（`EntityImage` 无 `@error` 兜底）时静默，用户不知发送失败 | P2 | 给反馈图加 `@error` 占位 + 「上传失败」提示 |

### 1.2 性能问题

| # | 页面/组件 | 文件:行 | 现象 | 优先级 | 建议 |
|---|-----------|---------|------|--------|------|
| C-05 | BroadcastBar.vue | `setInterval(4000)` | 已 `onHide(stop)`，`onUnmounted(stop)`，但缺 `onShow` 重启（见 C-01） | P1 | 同 C-01 |
| C-06 | HomeFeed.vue / find/index.vue | 列表渲染 | 长列表（find 页 `pageSize:50`、首页瀑布流）无虚拟滚动，条目多时 `setData` 体量上升 | P2 | 超 30 条改用 `@scrolltolower` 分页而非一次性全量；考虑 `recycle-list` |
| C-07 | FilterBar.vue | `measure()` `setTimeout(30)` | `onUnmounted` 未 `clearTimeout`，组件 30ms 内卸载会访问已卸载实例 | P2 | 保存 `timer` 句柄，`onUnmounted` 中 `clearTimeout` |
| C-08 | stores/dish.ts | 7 个方法 `withLoading` | 已做 Set 引用计数 + 去重（高质量）；但 `getStallDishes` 等仍可能 onShow 重复拉取 | P2 | `onShow` 加 `if (loading) return` 或 staleTime 缓存 |

### 1.3 UIUX 完善

| # | 页面/组件 | 文件:行 | 现象 | 优先级 | 建议 |
|---|-----------|---------|------|--------|------|
| C-09 | 全局 | §4 规范 | 朱砂红主色/卡片化已统一，但**深色模式缺失**（无 `prefers-color-scheme` 适配） | P2 | 提供深色主题 token（Apple Design 规范建议） |
| C-10 | 列表/按钮 | 触控区 | 部分图标按钮 `<44px` 命中区（FilterBar 项、header 返回） | P2 | 最小触控区 44×44，padding 补足 |
| C-11 | 首页首屏 | `home/index.vue` | 首屏 `loadingHot` 失败有重试，但广播区/万能区不随首屏失败态收起，视觉割裂 | P2 | 首屏失败统一错误态，分区降级而非各自为政 |

---

## 2. 管理后台 web 端

### 2.1 功能性 Bug（含漏审）

| # | 页面/组件 | 文件:行 | 现象 | 优先级 | 建议 |
|---|-----------|---------|------|--------|------|
| W-01 | ApplyReviewView.vue + audit.ts | `:68` 常量 `isPending=true`；`audit.ts:26-30 listReviews` 不传分页 | ① 详情抽屉**永远**显示「通过/退回」按钮（UI 漏审隐患）② 评价列表不传分页，后端 `PageUtil.normalize` 硬上限 100 条，评价 >100 时 keyword 检索被**截断 → 漏搜漏审（P0）** | **P0** | `listReviews` 传 `pageSize` 或改用全量/服务端检索；按钮显隐改为依据实体 `status === 'PENDING'` |
| W-02 | ApplyReviewView.vue | 审核提交 | `isPending` 常量导致已审核项仍可重复提交（前端未禁用）；缺乏服务端「已审核防重」 | **P1** | 提交后禁用按钮 + 服务端校验 `status==PENDING` 否则 409 |
| W-03 | ApplyServiceImpl（server） | 审核接口 | 缺「禁止审核自己创建的待审项（自审）」校验；无乐观锁/状态机闭合，并发重复点击可多次审核 | **P1** | 服务端加 `operatorId != entity.createBy` 校验 + `@Version` 乐观锁或 `UPDATE ... WHERE status='PENDING'` 行锁 |
| W-04 | UserView.vue | `toggleStatus` | 已传目标状态（全量优化已修），但**封禁自己**未拦截 | P1 | `if (id === currentUser.id) ElMessage.warning('不能操作自身')` |
| W-05 | login 后 | router/index.ts | 401 区分已修；但 `loadProfile` 抖动时 `localStorage` 残留旧 token 会反复弹登录 | P2 | 抖动期间用内存态标记，避免清 token 又重跳 |

### 2.2 性能问题

| # | 页面/组件 | 文件:行 | 现象 | 优先级 | 建议 |
|---|-----------|---------|------|--------|------|
| W-06 | CanteenDetailView.vue | `:333 store.dishes.filter(...).length` | DataTable 每渲染一行都对全量 dishes 做 `filter`（O(n×m)），菜品多时卡顿 | P2 | 改用 `computed stallDishCountMap`，行内 `map[stallId]` 取数 |
| W-07 | DataTable.vue | 大列表 | 未虚拟滚动；图片列无懒加载（EntityImage 是否有 `loading="lazy"` 取决于实现） | P2 | 超 50 行启用虚拟滚动；图片 `loading="lazy"` + 尺寸约束 |
| W-08 | ChartCard / ECharts | 各图表 | 已 `onUnmounted dispose()`（DashboardView 质量高）；需确认 Content/Banner 等其余图表页均 dispose | P2 | 全局检索 `echarts.init` 确保均有 `dispose` 配对（已抽查 dashboard OK） |

### 2.3 UIUX 完善

| # | 页面/组件 | 文件:行 | 现象 | 优先级 | 建议 |
|---|-----------|---------|------|--------|------|
| W-09 | 响应式断点 | dashboard/audit | 已用 `grid lg:grid-cols-*` + `overflow-x-auto` 表格（质量高）；移动端抽屉未做底部 sheet 化 | P2 | 窄屏下详情抽屉改 bottom-sheet |
| W-10 | 空态/错误态 | 各列表 | dashboard/audit 空态友好（质量高）；部分二级页（system/user）缺 retry 入口一致性 | P2 | 统一 EmptyState 组件 |
| W-11 | 深色模式 | 全局 | web 端无深色主题（Element Plus + 落地主题仅亮色） | P2 | 提供暗色主题切换（Apple Design / §4 一致） |

---

## 3. server 接口消费侧

### 3.1 N+1 / 分页

| # | 接口 | 文件:行 | 现象 | 优先级 | 建议 |
|---|------|---------|------|--------|------|
| S-01 | Review admin 列表 | `ReviewServiceImpl` list 接口 | 已知 Moment 已 `enrichBatch`，但 **Review 列表前端被迫本地 `find` 退化**（W-06 同因），说明后端 review list 未批量 enrich user/dish | **P1** | review list 加 `enrichBatch`（批量 loadUsers/loadDishes 建 Map），返回即带昵称/菜名 |
| S-02 | 任意分页接口 | `PageUtil.normalize` | 硬上限 100 条（见 W-01），超大 pageSize 被截断致漏数据；正常分页 OK | P1 | 提供「服务端检索全部 + 前端关键字」或提高上限并加 `keyword` 后端过滤 |
| S-03 | Canteen listWithStalls | `CanteenServiceImpl` | 已知 `batchAvgRating` 已批量查评分（高质量）；确认无遗漏档口逐条查 | — | 已覆盖，标记完成 |

### 3.2 越权 / 资源校验

| # | 接口 | 文件:行 | 现象 | 优先级 | 建议 |
|---|------|---------|------|--------|------|
| S-04 | Apply 审核接口 | `ApplyServiceImpl` | 缺自审拦截 + 状态机闭合 + 乐观锁（见 W-03） | **P1** | 服务端强制校验，杜绝并发重复审核 |
| S-05 | Upload 接口 | `UploadServiceImpl` | 已知加 magic number 校验（JPG/PNG/WEBP）（高质量） | — | 已覆盖，标记完成 |
| S-06 | User 状态/角色 | `UserServiceImpl` | 已知 `checkAdminOperation` 禁操作自身 + admin 需 SUPER_ADMIN（高质量） | — | 已覆盖，标记完成 |

---

## 4. 待派发任务清单（可映射 tasks/task-XX）

| task 草案 | 对应问题 | 端 | 优先级 |
|-----------|----------|----|--------|
| task-apply-review-fix | W-01/W-02/W-03/S-04 漏审（分页截断、自审、并发、状态机） | web+server | P0 |
| task-broadcast-resume | C-01/C-05 onShow 重启轮播 | client | P0 |
| task-review-enrich | S-01 Review 列表批量 enrich | server | P1 |
| task-user-self-ban | W-04 封禁自身拦截 | web | P1 |
| task-apply-required | C-03 转发动态 dishId 非空校验 | server | P1 |
| task-canteen-filter-perf | W-06 stallDishCountMap | web | P2 |
| task-list-virtual | C-06/W-07 虚拟滚动 + 懒加载 | client+web | P2 |
| task-timer-cleanup | C-07 FilterBar clearTimeout | client | P2 |
| task-dark-mode | C-09/W-11 深色模式 | client+web | P2 |
| task-a11y-touch | C-10 触控区 ≥44px | client | P2 |

> 注：C-02/C-04/C-08/C-11/W-05/W-08/W-09/W-10 为体验优化，可并入上述 task 或单独轻量处理。

---

## 5. 已确认良好的实践（非问题，供参照）

- client：`dish.ts` Set 引用计数去重、`review.vue` 搜索竞态 `searchSeq` 守卫、`http.ts` 动态 import 替代事件总线。
- web：`router` 401 与网络抖动区分、DashboardView 骨架屏 + ECharts `dispose`、`audit` 空态引导 + retry、reduced-motion 兜底。
- server：`CanteenServiceImpl.batchAvgRating` 批量评分、Upload 文件头校验、UserServiceImpl 越权校验、JwtAuthFilter Origin 校验、SensitiveFilter DFA。
