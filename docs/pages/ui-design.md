# UI 设计文档（食在交大 bjtu_food）

> 本文档基于前端实际实现（`client/` 小程序、`web/` 管理后台）核对生成，与代码严格一致。
> 视觉规范对齐项目 `§4 UI 规范`：Apple 列表分组 + 卡片阴影 + 按压微动效；管理后台采用沉稳砖红 + 毛玻璃顶栏的现代控制台风格。

---

## 1. 视觉规范（设计 Token）

### 1.1 小程序（client）Design Token
来源：`client/src/theme/tokens.ts`（SCSS 变量 + CSS 变量，支持 `theme-dark`）。

| Token | 值 | 含义 |
|-------|-----|------|
| --color-primary | `#C0392B` / 朱砂红 | 主色（按钮、激活态、品牌点缀） |
| --bg-page | `#F7F8FA` | 页面背景 |
| --bg-card | `#FFFFFF` | 卡片背景 |
| --text-primary / secondary / tertiary | `#1F2329` / `#646A73` / `#8F959E` | 文本三级灰阶 |
| --radius-card / --radius-modal | 16rpx / 24rpx | 卡片 / 弹层圆角 |
| --spacing-* | 设计栅格间距（8 倍数） | 统一留白 |
| --press-scale | `0.96` | 按压缩放反馈 |
| --duration-fast / --duration-base | 最短/基础动效时长 | 过渡时长 |
| --ease-out | 缓动曲线 | 统一 easing |

**暗色模式**：根元素加 `.theme-dark` 切换 CSS 变量；主题由 `useThemeStore` 持久化。
**可达性**：`prefers-reduced-motion: reduce` 时关闭位移动效，仅保留 opacity 过渡；表单/卡片 `:focus-visible` 显示 focus ring。

### 1.2 管理后台（web）Design Token
来源：`web/src/styles/main.css`（CSS 变量）。

| Token | 值 | 含义 |
|-------|-----|------|
| --color-primary | `#C0392B` → `#E2583E` → `#F97316` | 主色梯度（砖红→橙） |
| --bg-page / --bg-card | `#F7F8FA` / `#FFFFFF` | 页面 / 卡片 |
| --text-primary / secondary / light | `#1F2329` / `#646A73` / `#8F959E` | 文本 |
| --nav-bg | 白底 + `backdrop-filter: blur` | 顶栏毛玻璃 |
| --nav-item-active-bg / --nav-item-active-color | 浅砖红底 / 砖红字 | 顶栏激活项 |
| --shadow-pop | 浮层阴影 | 弹层/菜单 |
| --press-scale | `0.96` | 按压缩放 |
| --focus-ring | 砖红 focus ring | 键盘可达性 |
| --font-* | PingFang SC，标题 20/600，正文 14/400 | 字体系统 |

---

## 2. 学生端小程序（client）

### 2.1 全局框架
- **自定义 TabBar**：底部 5 个主入口 `首页 / 发现 / 发布 / 社区 / 我的`（实际路由 `pages/home`、`pages/find`、`pages/feedback`、`pages/community`、`pages/profile`；"发布"经中心按钮唤起 `publish-content`）。
- **路由层级**：主包 `pages/*`；分包 `pages/pages-detail/`（菜品/动态/评价列表详情）、`pages/pages-user/`（发布内容、我的动态、资料编辑）。
- **统一头部**：`Header` 组件 variant `home`（头像 + 搜索胶囊，朱砂红底）/`sub`（返回 + 标题）。

### 2.2 核心页面布局

#### 首页（pages/home）
```
┌─────────────────────────────────┐
│ Header(home): [头像]  [搜索框]    │  朱砂红底
├─────────────────────────────────┤
│ BroadcastBar  运营广播（纵向轮播） │
│ UniversalGrid  [最新活动][反馈菜品]│  双卡入口
│ FilterBar     品类滚轮（横滑切换） │
│ loc-hint      开启定位轻提示        │
│ HomeFeed      双列瀑布流菜品卡      │
│   · 骨架屏 loadingHot 期间占位      │
└─────────────────────────────────┘
              ⊕ 回到顶部 FAB（右下）
              ◉ AuthSheet 认证弹层
```
- **瀑布流**：`HomeFeed` 双列（`WaterfallList`），切换品类/横滑仅刷新此区；首屏骨架屏结构贴合真实布局避免跳变。
- **距你 Xm**：依赖 `canteen.latitude/longitude`（GCJ-02），未授权定位时显示 `loc-hint` 轻提示。

#### 菜品详情（pages/pages-detail/dish）
- 头图轮播 `ImageSwiper` → 名称/价格（分→元，`money.ts` 格式化）/辣度/分量/供应时段标签 → 评分摘要 → 评价列表 `ReviewItem` → 底部操作栏 `InteractBar`（点赞/收藏/写评价）。
- 写评价经 `ReviewActionSheet`（底部半屏 Sheet）；未认证触发 `AuthSheet`。

#### 社区动态（pages/community + pages/pages-detail/moment）
- 列表 `MomentCard`（头像/正文/`MomentImageGrid` 九宫格）→ 详情含 `CommentItem` 一层回复。
- 互动：`MomentActionSheet`（点赞/评论/分享/举报）；举报经 `ReportModal`。
- 发布：`publish-content`（`pages/pages-user`）支持图文+关联菜品。

#### 我的（pages/profile）
- 用户信息卡（头像/昵称/`roleLabel` 学生或管理员）→ 设置项分组 `SettingGroup/SettingCell`（已确认无引用，待清理）→ 我的动态 `my-moments`、通知 `notifications`、资料编辑、关于、反馈。
- 通知列表 `notifications`：游客态由 `requireAuth` 弹 `AuthSheet`，认证成功后 `watch(isVerified())` 自动 reload（修复竞态空白）。

### 2.3 交互流程
1. **游客 → 认证**：浏览无需登录；触发写操作（点赞/评价/发布）弹 `AuthSheet` → `@bjtu.edu.cn` 邮箱验证码认证 → `verified=1` → 自动继续原动作。
2. **下拉刷新 / 触底加载**：`scroll-view` `refresher` + `scrolltolower` 驱动 `HomeFeed` 分页。
3. **图片选择**：`ImageUploader` 经微信云存储上传，多实例各自维护 `removedDuringUpload` 避免串扰。
4. **主题切换**：`useThemeStore` 切换 light/dark，根节点 `.theme-dark` 即时响应。

### 2.4 小程序组件说明
| 组件 | 职责 |
|------|------|
| Header | 首页/子页头部（头像+搜索 / 返回+标题） |
| BroadcastBar | 首页运营广播纵向轮播 ticker |
| UniversalGrid | 万能区双卡（最新活动 / 反馈菜品） |
| FilterBar | 品类筛选滚轮（横滑 + 点击切换） |
| HomeFeed | 双列瀑布流菜品流（分页/骨架/错误重试） |
| WaterfallList | 通用瀑布流容器 |
| DishCard / StallCardSingle | 菜品/档口卡片 |
| ImageSwiper / ImageFallback | 图片轮播 / 加载失败兜底 |
| InteractBar | 详情底部互动操作栏 |
| ReviewItem / CommentItem | 评价 / 评论项 |
| MomentCard / MomentImageGrid | 动态卡 / 动态九宫格 |
| AuthSheet | 邮箱认证弹层（游客写操作网关） |
| ReviewActionSheet / MomentActionSheet / ReportModal / RelatedPickerSheet / ApplySheet | 各类底部半屏操作 Sheet |
| Rating / TagLabel / SectionTitle / CardSection | 评分星 / 标签 / 区块标题 / 卡片区块 |
| ImageUploader | 微信云存储图片上传（多实例隔离） |
| IconSvg / EmptyState / AppButton / SearchBar | 基础原子组件 |

---

## 3. 管理后台（web）

### 3.1 全局框架
- **外壳 `AdminLayout`**：无侧边栏，顶部一级导航（白底毛玻璃）+ 全宽内容区。
  - 品牌：`知行食记`（点击回工作台）。
  - 一级导航：`工作台 / 信息管理 / 内容审核 / 用户与系统`（聚合页内的详情/子路由归属对应入口，`isNavActive` 判断）。
  - 右侧用户菜单：仅「账号设置」；系统超时自动登出，无主动退出入口。
- **路由过渡**：`page` 过渡（opacity + 8px 上移，`prefers-reduced-motion` 降级）。
- **响应式**：窄屏（≤767px）顶栏仅显图标、隐藏品牌字与用户名。

### 3.2 核心页面布局

#### 工作台（DashboardView）
- 顶部 4 指标卡（食堂/档口/菜品/学生），点击直达对应子视图 tab（已修复死链：`档口→content?tab=canteen`、`学生→system?tab=account`）。
- 中部聚合：待办审核 + 内容贡献申请 + 最近反馈 + 操作日志（各卡片「查看全部」跳对应模块）。
- 图表：ECharts（评分分布/浏览趋势），实例在卸载时销毁。

#### 信息管理（ContentManageView）— section：canteen / dish / home
- `canteen`：食堂列表（含档口管理入口）、新增/编辑 `FormDialog`。
- `dish`：菜品 CRUD、折扣价、审核态。
- `home`：首页广播/品类/活动配置。

#### 内容审核（AuditView）— section：moment / dish / review / stall / canteen / feedback
- 列表 + 详情抽屉，审核通过/退回（`reject_reason`），操作写 `operation_log`。

#### 用户与系统（SystemManageView）— section：account / log
- `account`：学生列表（脱敏展示 `wechatBound` 布尔 + `bindEmail`，**不再返回 openid 明文**）、管理员账号（超管可读写，普通管理员只读）。
- `log`：操作日志只读查询。

### 3.3 交互流程
1. **登录**：管理员账号密码 → `adminUserStore` 持久化 `myRole`；路由守卫 `M10` 限制 `systemManage` 仅 `super_admin`。
2. **聚合页跳转**：指标卡/待办卡点击带 `?tab=` 直达对应 section；直达路由进入时 `adminStore.loadAll().catch(...)` 容错，避免 store 未初始化空指针。
3. **审核/CRUD**：`FormDialog` 表单提交 → 成功 Toast → 列表刷新；破坏性操作经 `ConfirmDialog`（默认聚焦取消，Esc 取消）。
4. **204 无内容**：视图拉数失败/无数据时给空数组 + 空态提示，不报错。

### 3.4 后台组件说明
| 组件 | 职责 |
|------|------|
| AdminLayout | 顶栏外壳（毛玻璃、聚合路由激活判断） |
| PageContainer | 内容区统一留白/标题 |
| FilterBar | 列表筛选条 |
| DataTable | 通用数据表（分页/排序） |
| FormDialog | 新增/编辑表单弹窗（基于 Modal） |
| ConfirmDialog | 破坏性确认弹窗（Esc 取消、定时器清理） |
| Modal | 通用弹层底座（ConfirmDialog/UserActivityModal 复用） |
| StatCard | 指标卡 |
| StatusTag | 状态标签（审核/上架/认证） |
| EntityImage | 实体图片（多图预览） |
| Toast | 轻提示 |
| UserView / MomentManageView / DishManageView / StallDetailView / CanteenDetailView / DishDetailView / ReviewManageView / FeedbackView / OperationLogView / AccountView | 各业务视图 |

---

## 4. 通用交互与动效规范

- **按压反馈**：所有按钮/可点卡片 `:active { transform: scale(var(--press-scale)) }`，时长 160ms `ease-out`。
- **弹层进场**：底部 Sheet 上滑 240ms `cubic-bezier(.22,1,.36,1)`；确认菜单缩放渐入。
- **路由切换**：opacity + 8px 位移（后台 `page` 过渡）。
- **骨架屏**：首屏/列表加载用结构贴合的骨架，避免加载完成跳变。
- **空态/错误态**：`EmptyState` 统一（图标 + 文案 + 重试入口）；列表失败显示错误态并可重试。
- **可达性**：键盘 `:focus-visible` 焦点环；`prefers-reduced-motion` 全局降级位移动效。

---

## 5. 与实现的差异/待办（清扫结论）

- 已删除小程序死组件 `SettingCell.vue` / `SettingGroup.vue`（0 引用确认）。
- 已修复：`notifications` 认证后竞态空白、`profile-edit` 快照竞态、`DashboardView` 指标卡死链、`UserVO` openid 明文泄露改 `wechatBound` 布尔、`ImageUploader` 多实例串扰。
- `Modal.vue` 被 `FormDialog`/`UserActivityModal` 复用，**保留**（非死代码）。
- 后台图表视图 `onBeforeUnmount` 均清理 ECharts/监听，无资源泄漏。
