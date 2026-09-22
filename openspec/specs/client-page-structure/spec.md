# client-page-structure Specification

## Purpose
约束小程序页面结构与顶部导航的组织方式：全站 header 统一由 AppHeader 承载，分包按页面划分（一页一包、扁平），同一页面的目录名、页面标题、入口文案三者一致。防止页面结构持续发散。

## Requirements

### Requirement: 全站页面顶部导航统一使用 AppHeader

所有页面的顶部导航栏 SHALL 由 `AppHeader` 组件渲染。页面 SHALL NOT 自绘导航栏（自行计算状态栏高度、胶囊对齐、返回按钮）。当现有 variant 无法满足页面形态时，SHALL 扩展 `AppHeader` 的 variant，而非在页面内自绘。

#### Scenario: 新增页面需要顶部导航

- **WHEN** 新增一个需要顶部导航的页面
- **THEN** 该页面复用 `AppHeader`；若形态不匹配既有 variant，则在组件内新增 variant 后使用

#### Scenario: 页面需要带返回键的搜索框

- **WHEN** 页面顶部需要「返回箭头 + 可输入搜索框」的组合
- **THEN** 使用 `AppHeader` 的 `search` variant，不在页面内自绘该组合

### Requirement: 分包按页面划分（一页一包、扁平）

小程序分包 SHALL 按**页面**划分：每个可跳转页面在 `client/src/pages/` 下拥有独立分包目录 `pages/<kebab-page-name>/`，页面文件统一为 `index.vue`，`pages.json` 中该分包 `root` 为 `pages/<kebab-page-name>/` 且 `pages` 为 `["index"]`。SHALL NOT 再出现业务域或功能**分组**目录（如 `pages/detail/`、旧式 `pages/mine/about/index` 这类「页面再套子页」的分组）；其中 `pages/mine/` 作为「我的」Tab 根页目录（位于主包 `pages[]`）属合法页面目录，SHALL NOT 被视作分组禁例。分包目录名 SHALL 与其路由路径末段一致且语义清晰。

#### Scenario: 二级页面目录平铺

- **WHEN** 页面从旧式业务分组目录（如历史 `pages/mine/about/index`）扁平化为独立页面
- **THEN** 其文件位于 `pages/<页面名>/index.vue`、`pages.json` 对应 `root: "pages/<页面名>/"`、`pages: ["index"]`；业务分组目录不再存在，`pages/mine/` 仅作为「我的」根页目录存在

#### Scenario: 跳转路径随分包同步

- **WHEN** 代码中任一处跳转至某个二级页面
- **THEN** 使用与目录一致的 `/pages/<页面名>/index`，且不残留 `/pages/detail/`、`/pages/mine/about/`、`/pages/activity/webview/` 等非「页面末段」形式的 URL

#### Scenario: 新增可跳转页面

- **WHEN** 新增一个非 TabBar 的可跳转页面
- **THEN** 在 `pages/` 下新建以该页面名命名的分包目录并含 `index.vue`，不归入任何分组目录

#### Scenario: TabBar 页面放置

- **WHEN** 页面是 TabBar 页面
- **THEN** 该页面留在主包 `pages[]` 中（平台要求），不拆为独立分包

### Requirement: 首页与搜索页搜索框右缘对齐
首页（`AppHeader` 的 `home` variant）与搜索页（`search` variant）的搜索框**右缘** SHALL 像素对齐：两 variant SHALL 共用同一右侧留白计算式（`env(safe-area-inset-right)` 与胶囊避让量），搜索页仅因左侧返回箭头而**左侧**收窄，其右侧偏移 SHALL NOT 与首页不同。

#### Scenario: 两页搜索框右缘对齐
- **WHEN** 在同一机型分别查看首页与搜索页
- **THEN** 两页搜索框的右边缘横坐标相同

#### Scenario: 搜索页左侧收窄
- **WHEN** 搜索页渲染「返回箭头 + 搜索框」
- **THEN** 搜索框左缘自返回箭头与间距之后开始，右缘位置与首页一致

### Requirement: 同一页面的三处命名保持一致

同一页面的目录名、页面标题、以及指向它的入口文案 SHALL 表达同一语义。三者出现分歧时 SHALL 收敛为一致。

#### Scenario: 入口文案与页面标题不一致

- **WHEN** 某页面的入口文案与其自身页面标题表述不同
- **THEN** 统一为同一语义表述（目录名同步调整）

#### Scenario: 新增页面

- **WHEN** 新增页面并在某处放置入口
- **THEN** 目录名、页面标题、入口文案三者语义一致

### Requirement: 页面内容区收敛为页面级内容组件

每个页面的主内容区（瀑布流/结果列表）SHALL 收敛为页面级内容组件，置于 `pages/<page>/` 下与 `index.vue` 同级（colocation），不在 `index.vue` 内联大段列表逻辑。`index.vue` 仅负责页面编排（滚动容器、回到顶部、筛选状态、首拉引导；**页面级下拉刷新已于 2026-09-22 全局下线，change `remove-pull-to-refresh`**）。内容组件 SHALL NOT 承载加载中骨架与空态占位（此类占位已整体移除），仅呈现真实内容。

**失败态例外（MP-012，2026-09 登记）**：数据型列表的**首屏请求失败** SHALL 与「请求成功但无数据」严格区分，SHALL 呈现极简「加载失败 · 点击重试」行内块替代空态（失败 ≠ 无数据）；该块 SHALL 由内容组件经 props/事件接收失败态、由 `index.vue` 承接重拉（与首屏同一条重拉路径），SHALL NOT 回加骨架屏、loading 指示或独立错误组件。**分页（触底加载更多）失败 SHALL 保持静默**（可再触底重试），不打断当前列表。本条与「空态两处例外」并列为内容组件状态口径的显式登记。

**空态例外登记**：全站内容组件的「空态静默」口径 SHALL 仅有两处显式例外——① **首页内容流末尾贡献卡片**（瀑布流内容为空时仍展示，见 `contribution-entry`）；② **搜索（find）结果区无结果引导**（见 `find-page-layout` 与 `search-result-presentation`）。除这两处外，内容组件 SHALL NOT 承载任何形式的空态占位或引导。

内容组件命名 SHALL 表达其内容语义且跨页对称（如首页内容组件与搜索结果组件分别命名为 `HomeContent` 与 `FindResults`）。承载菜品瀑布流/结果列表的内容组件 SHALL NOT 使用易与动态信息流混淆的 `Feed` 命名。

#### Scenario: 首页内容组件

- **WHEN** 首页需要渲染菜品瀑布流
- **THEN** 由 `pages/home/HomeContent.vue` 承载列表本身，`index.vue` 仅做编排（滚动/筛选状态），且原 `HomeFeed.vue` 已重命名为 `HomeContent.vue`

#### Scenario: 搜索结果组件

- **WHEN** 搜索页需要渲染结果列表
- **THEN** 由 `pages/find/FindResults.vue` 承载列表本身，`index.vue` 保留搜索数据与竞态守卫，并以 props/events 驱动组件

#### Scenario: 命名对称

- **WHEN** 为某页面新增内容组件
- **THEN** 组件名表达「页面+内容」语义，且与既有页面内容组件命名风格一致（如 `<Page>Content` / `<Page>Results`），不使用 `Feed` 承载菜品流

#### Scenario: 空态例外仅两处

- **WHEN** 全仓审查各页内容组件的空态处理
- **THEN** 仅首页内容流末尾贡献卡片与搜索无结果引导两处呈现空态内容，其余内容组件（含评价区、通知列表、我的评价列表）均保持空态静默

#### Scenario: 首屏失败与空数据可区分

- **WHEN** 数据型列表首屏请求失败
- **THEN** 内容区呈现「加载失败 · 点击重试」行内块（不呈现空态文案），点击后按同一路径重拉；请求成功但无数据时才走空态口径

#### Scenario: 分页失败不打断列表

- **WHEN** 用户触底加载下一页时请求失败
- **THEN** 当前列表保持原样静默，不呈现错误块，用户可再次触底重试

### Requirement: 目录与路由收敛目标

为消除「目录名 / 页面标题 / 入口文案」的语义错位，SHALL 将下列既有页面目录收敛到目标路径，并同步 `pages.json`、TabBar、路由锚定与全部跳转引用：

- Tab「我的」根页：`pages/profile` → `pages/mine`（路径 `/pages/mine/index`，页面标题与 Tab 标签仍为「我的」）；
- 个人信息编辑页：`pages/profile-edit` → `pages/profile`（路径 `/pages/profile/index`）；
- 独立「关于我们」页 `pages/about` SHALL 删除，其路由与跳转一并移除；
- 发表 / 编辑动态页 `pages/publish-moment`（原 `pages/publish-content`）SHALL 删除，其路由与跳转一并移除；动态页 `pages/dynamic`、动态详情 `pages/detail/moment`、`pages/me/publish-mine` 亦 SHALL 删除，TabBar 收敛为「首页 / 我的」两项（详见 `tab-bar`）；
- **新增「我的评价」页**（路径 `/pages/my-reviews/index`，**独立分包 root** `pages/my-reviews/`），由「我的」页宫格进入（详见 `my-reviews` 与 `profile-restructure`）；
- **新增「隐私政策 / 用户协议」页**（路径 `/pages/privacy/index`，**独立分包 root** `pages/privacy/`），由「我的」页底部信息区入口进入（详见 `privacy-compliance`）。
- **2026-09-15 结构变更**：个人中心域聚合分包 `pages/me/` 已拆分，其 5 页（`feedback` / `notifications` / `profile` / `my-reviews` / `privacy`）各自成为**独立分包 root**，路径形如 `pages/<name>/index`；`pages/me/` 聚合分包 SHALL NOT 恢复。`pages/profile/` 为「个人信息」分包页，与已退役的「我的」Tab 根页语义（现为 `pages/mine`）无关。

收敛后：小程序注册页面数 SHALL 为 **9**（主包 3 + 分包 root **6 个各 1 页**：`pages/detail/`、`pages/feedback/`、`pages/notifications/`、`pages/profile/`、`pages/my-reviews/`、`pages/privacy/`；`pages/activity/` 分包两页已于 2026-09-13 随活动全链路下线删除）。SHALL NOT 存在对旧发布/编辑页路径（`pages/publish-content`、`pages/publish-moment`、`/pages/profile-edit`）或「关于我们」页（`/pages/about`）的任何引用；SHALL NOT 存在 `pages/dynamic`、`pages/detail/moment`、`pages/me/publish-mine` 的目录、`pages.json` 注册与跳转；SHALL NOT 存在 `pages/activity/` 的目录、`pages.json` 注册与跳转；「我的」Tab 的 url 与 active 锚定 SHALL 指向 `pages/mine`，而非旧 `pages/profile` 根页语义。

#### Scenario: Tab「我的」指向新目录

- **WHEN** 查看 TabBar 配置、`route` store 锚定与「我的」根页
- **THEN** 「我的」项 url 为 `/pages/mine/index`，active 锚定语义与之一致，不再指向旧 `pages/profile` 根页

#### Scenario: 旧发布页路径无残留

- **WHEN** 在 `client/src` 内检索 `pages/publish-content`、`pages/publish-moment` 与 `publishMoment`
- **THEN** 无任何命中，发布动态页目录、路由与入口均已删除

#### Scenario: 旧编辑页路径无残留

- **WHEN** 在 `client/src` 内检索 `pages/profile-edit` 与 `/pages/profile-edit`
- **THEN** 无任何命中；个人信息编辑页位于 `pages/profile`

#### Scenario: 关于我们页面不可达

- **WHEN** 在 `pages.json`、路由或跳转代码中检索 `pages/about` 与「关于我们」入口
- **THEN** 无该路由与入口，页面不可达

#### Scenario: 动态相关页面不可达

- **WHEN** 在 `pages.json`、`utils/routes.ts` 与跳转代码中检索 `pages/dynamic`、`pages/detail/moment`、`pages/me/publish-mine`
- **THEN** 无注册、无路径常量、无跳转，三页均不可达

#### Scenario: 新增页面已注册且可达

- **WHEN** 在 `pages.json` 检索「我的评价」与「隐私政策」页
- **THEN** 二者各自注册于**独立分包**（`pages/my-reviews/`、`pages/privacy/`）且由「我的」页对应入口可达，注册页面总数为 9

#### Scenario: 活动页面不可达

- **WHEN** 在 `pages.json`、路由或跳转代码中检索 `pages/activity` 与「最新活动」入口
- **THEN** 无该分包注册、无路径常量与跳转，活动列表页与 web-view 页均不可达（随 2026-09-13 活动全链路下线删除）
