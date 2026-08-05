# 列表/设置页合集（lists）

> **页组合并文档（2026-08-02）**：由 `community.md` / `my-moments.md` / `moment.md` / `messages-services.md` / `settings.md` 五份页面文档合并而来（方案 A：按页组合并，只保留每页差异化内容）。
> 前四页为「动态/列表类」（共用 MomentCard / SegmentTabs / EmptyState / 触底加载模式），settings 为静态分组设置页。各自仅列表项构成/空态/交互不同。

## 通用列表模式（community / my-moments / messages-services 共用）

- **页面壳**：`Header`（主包页含状态栏）+ `scroll-view`（refresher + 触底）。
- **列表项**：动态用 `MomentCard`（头像兜底 user、关联对象跳转、审核态徽标，`:show-audit` 控制）；发布/贡献用自绘 item（`StatusBadge` + 图标）。
- **分段**：`SegmentTabs`（滑块 spring 1.0/0.3，从当前值重定向可中断，非按压强调）。
- **三态**：加载中骨架（`skeleton-list` 3 张 sk-card）/ 空态 `EmptyState`（图标+文字）/ 正常态。
- **动效**：卡片/项按压 `scale(var(--press-scale))`（组件或 `.active`）；`prefers-reduced-motion` 关动画。
- **Token**：`--bg-page/--bg-card/--text-*`、`--color-primary`(激活/链接)、状态色走 `--color-*-soft`；4/8pt 间距。
- **红线**：审核态徽标图标+文字（非纯色）；计数 `tabular-nums`；无 emoji（图标全 IconSvg）。

---

## 1. 动态（community）

- 路由：`/pages/community/index`；源文件 `src/pages/community/index.vue`（主包）
- 定位：校园美食动态流（**最新单流，无分段 Tab**）；右下悬浮发布 FAB 直达发布页。
- **种子数据（2026-08-03 补齐）**：`seed_data.sql` 原无 moment/comment 数据，已追加 8 条动态 + 12 条评论 + 15 条有用标记（关联 dish/stall/none 三类，覆盖列表流/详情页/评论区/互动计数）。**需重新执行 seed_data.sql 或手动插入后重启后端。**

### 布局要点
- 滚动区：骨架 / `EmptyState text="还没有动态，快去发布第一条吧" icon="comment"` + action「发布第一条动态」(plus) / `moment-list`（MomentCard，`:key=m.id`）。
- 触底 footer（spinner / 已经到底）。
- 悬浮 `fab`（fixed 右下，按下 scale(0.97)，避让 TabBar + 安全区）。
- 底部 `CustomTabBar`。

### 关键交互
- 单流始终按 latest 倒序（无推荐 Tab，已决议）；触底无限加载 + 下拉刷新。
- FAB → `publish-moment`；卡片 → moment 详情；关联对象按 relatedType 跳 dish/stall。
- ✅ 计数 tabular-nums 已由 `MomentCard.vue` `.m-action-count` 落实。

---

## 2. 我的动态（my-moments）

- 路由：`pages/pages-user/my-moments/index`；源文件 `src/pages/pages-user/my-moments/index.vue`（pages-user 分包，无底部 tab）
- 定位：当前用户发布的动态管理：分段「全部 / 审核中 / 已退回」。

### 布局要点
- `SegmentTabs`（全部/审核中/已退回）+ 滚动区（骨架 / `EmptyState` 按分段文案 icon="comment" / `MomentCard :show-audit="true"`）。
- 无底部 tab 栏；scroll-wrap `padding:0`（非 tab 页无避让）。

### 关键交互
- 分段切换 `onSegChange` 重拉对应 auditStatus；全部态额外统计 pending/rejected 徽标。
- 卡片：已退回 → `publish-moment?id=` 编辑；其他 → moment 详情。
- ✅ 计数 tabular-nums 由 MomentCard 组件层落实。

---

## 3. 动态详情（moment）

- 路由：`/pages/pages-detail/moment`；源文件 `src/pages/pages-detail/moment.vue`（pages-detail 分包）
- 定位：动态详情：发布者 + 正文 + 九宫格 + 关联卡 + 退回原因/编辑重提（作者）+ 互动 + 评论。

### 布局要点
- 正常态自上而下：**`.m-card` 动态主卡（2026-08-03 打磨：`.m-head` 头像/昵称/时间/审核徽标 + `.m-content` 正文 + `.m-images` 九宫格，合一卡）** → `.related-card`(关联对象) → `.reject-box`(退回原因+编辑重提，作者) → `InteractBar`(有用/评论/举报) → `.comment-section`(评论列表+展开)。
- 卡片统一（2026-08-03）：`.m-card`/`.related-card`/`InteractBar`/`.comment-section` 均 `--bg-card` + `--radius-card` + `margin: var(--spacing-md)` + `--shadow-card`——消除原 `m-head`/`m-content` 无圆角贴边白条的割裂。
- 底部 `.comment-bar`（fixed，`--action-bar-height` + 安全区）：`comment-input`（`:focus` 支持自动聚焦）+ `comment-send`。
- 三态：骨架（3×`.sk-block`）/ `EmptyState text="动态加载失败或不存在" :retry` / 正常。

### 关键交互
- 有用：乐观切换 `usefulActive` + count → `toggleUseful` 校正。
- 退回态（作者）：原因 + 「编辑重提」→ publish-moment?id。
- 评论：发送 / 楼中楼回复 / 长按删（仅本人）；>5 条折叠「共 N 条，点击展开」。
- **评论自动聚焦（2026-08-03 打磨）**：点 `InteractBar` 评论 → `pageScrollTo` 到底 + `commentFocus=true`（input `:focus`）弹键盘，替代原 toast。
- 举报：`openReport`(requireAuth) → `ReportModal`（spring 0.8/0.3 + ic-close + reduced-motion，type=report 走 submitFeedback）。
- 裁定：评论输入为即时通讯式输入框，「无独立可见 label」为常见例外（placeholder + 发送按钮语义）。

### 特有整改
- 🔧 **关联档口跳转（2026-08-03 修复）**：动态关联档口跳转原用 `?id=`（档口页不支持，空白）；现后端 `MomentVO` 新增 `relatedCanteen`（enrich 联食堂表取食堂名），前端 `toMoment` 透传，`goRelated` 用 `navParams={stallName, canteen}` 跳档口详情。
- ✅ `InteractBar.vue` `.interact-count` `tabular-nums` 已补；时间文本已 `tabular-nums`。

---

## 4. 我的发布与贡献（messages-services）

- 路由：`/pages/profile/messages-services/index`；源文件 `src/pages/profile/messages-services/index.vue`（主包，profile 子目录）
- 定位：个人中心「我的发布 + 我的贡献」**唯一聚合页**（吸收已移除的 my-publish / my-submissions；notify 消息并入「我的」区块）。

### 布局要点
- `Header title="我的发布与贡献"`（**已更名，原「消息与服务」废弃**）。
- 一级 `SegmentTabs`：我的发布 / 我的贡献（滑块 spring 1.0/0.3）。
- **我的发布**：sub-tabs（菜品/档口·食堂）+ `.publish-item` 列表 + `StatusBadge` + 空态 + 底部发布按钮（AppButton 主「发布新菜品」+ outline「提交档口·食堂」）。
- **我的贡献**：sub-tabs（实体/动态）+ `.sub-item` 列表 + `StatusBadge`/下架锁 + 空态。

### 关键交互
- 一级切换重载对应 group；二级 sub-tab 切换（发布即时重载，贡献仅切显）。
- 菜品项 → publish-dish 编辑；动态项 → moment 详情。
- ✅ 裁定：标题更名已落地；本页为聚合页唯一落点。

### 特有整改
- 🔧 **开发侧整改**：菜品价格 `.item-meta` 补 `font-variant-numeric: tabular-nums`（messages-services/index.vue）。

---

## 5. 设置（settings，已移除路由）

> ⚠️ **2026-08-03 定调：`settings` 独立路由已移除**，设置项（消息/通用/账号分组）内嵌 `profile`（`src/pages/profile/index.vue`）。本段作为该设置块的设计参考保留，路由/源文件字段仅作历史记录。

- ~~路由：`/pages/settings/index`；源文件 `src/pages/settings/index.vue`（主包）~~（已移除）
- 定位：静态分组设置块（**无加载/空态需求**），现内嵌于 profile。

### 布局要点
- 三个 `SettingGroup`：
  1. 通知：cell（bell 图标 + 标签 + 自绘 `switch` 开关）。
  2. 通用：关于食在交大(logo) / 隐私政策(lock) / 清除缓存(delete)。
  3. 账号：退出登录(profile) / 账号注销(delete, **danger**，独立分组末项二次确认)。
- 版本行「食在交大 v1.0.0」。

### 关键交互
- 通知开关：toggle 写 `uni.setStorageSync('setting_notify')`（前端占位，真推送留三期）。
- 清除缓存：showModal 确认 → `clearStorageSync` + `restoreFromCache` 保留登录态。
- 退出登录：showModal → logout + reLaunch profile。
- 账号注销：showModal 二次确认（`confirmColor: '#e54d42'` **原生 API 例外已登记**）→ deleteAccount + 清 token + reLaunch。
- 开关 knob 用 `--ease-out` transform（<300ms）；`.cell:active scale(var(--press-scale))`。

### 特有裁定
- ✅ **裁定（通知开关）**：MVP 范围外，**保留本地存储占位合理**，不得做成虚假「已开启推送」误导；三期接微信 `requestSubscribeMessage` + 后端 `notification` 异步写，届时 `toggleNotify` 升级为调用订阅接口并回显状态。

---

## 6. 我的评价（my-reviews）

- 路由：`/pages/pages-user/my-reviews/index`；源文件 `src/pages/pages-user/my-reviews/index.vue`（分包 pages-user）
- 定位：我的历史评价列表（含删除入口，delete 二次确认）。
- 布局要点：复用通用列表模式（加载/空态/正常三态）+ 评价卡片（评分/内容/时间/所属档口）。
- 关键交互：列表 `getMyReviews`（`GET /my/reviews`）；删除评价 `showModal` 二次确认 → `deleteReview`（`DELETE /reviews/{id}`，STU 仅本人）→ 列表刷新 + toast。
- 特有裁定/整改：删除按钮 danger 态用 `--color-error-soft` 底；无评价空态用 `EmptyState empty`。

---

## 交付前验证（列表/设置页通用）

- [ ] 375px：列表项/分段不溢出；FAB/评论栏不与固定元素重叠。
- [ ] 暗色对比：审核态 soft 底文字 ≥3:1、正文 ≥4.5:1（token 满足，交付前统一真机实测）。
- [ ] 44pt：列表项/FAB/分段/开关命中区 ≥44pt。
- [ ] 安全区：固定栏（FAB/comment-bar）不被 home indicator 遮挡。
- [ ] 动态字号：正文/时间/标签不截断。
