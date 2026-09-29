# 菜品详情 · 页内承载物 — 页面 UI 设计稿

> 所属端：学生端（微信小程序）
> 落点：**无独立页面** —— 菜品详情页（[`client-菜品详情.md`](./client-菜品详情.md)）内的三个承载物：**写评价 / 重新评价**（底部弹层 `ReviewComposer`）、**删除本人评价**（评价卡三点菜单 + `ActionSheet` + 二次确认）、**举报评价**（底部弹层 `ReportModal`）。
> **合并说明（2026-09-27）**：原 `client-写评价.md` / `client-删除本人评价.md` / `client-举报评价.md` 三份稿合并为本文件 —— 三者**同属菜品详情页**，只是该页上的三个交互承载物，拆三份粒度偏细。功能侧文档仍按其自身切分独立存在（**两套文档体系各按自身标准分层，不强制对应**）。

---

## 一、写评价 / 重新评价（`ReviewComposer` 底部弹层）

入口 = 菜品详情评价区底栏主按钮（「写评价」/「重新评价」双态）。

- **判定时机**：点击入口后、弹层打开前调「我的评价（按菜过滤）」——已评价以「重新评价」模式打开（**预填本人旧值**，弹层内菜名下方显示覆盖提示「你已评价过此菜，本次提交将覆盖原评价」，浅底圆角提示条）；未评价打开空表单。
- 弹层：`ReviewComposer` 底部弹层（统一下拉关闭手势，阈值约 120px），内容为：星级选择 + 文字输入框 + 配图选择器（`ImagePicker`，自动压缩 ≤1MB、≤750×1334）。
- 未认证用户点入口 → 跳转身份认证页 `pages/auth/index`（入口不置灰；认证成功返回本页后由 onShow 续接，自动重新打开写评价表单）。

### 组件清单

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `ReviewComposer` | 页内私有 `pages/detail/dish/ReviewComposer.vue` | 弹层本体：菜名 + 重评提示 + 星级 + 正文 + 配图 + 提交（承载表单语义） |
| 2 | `BaseSheet` | 公共 `components/BaseSheet.vue` | 弹层骨架：遮罩 / grabber / 下滑关闭 / 安全区 / 标题（「写评价」/「重新评价」按模式切换）/ 右上关闭钮 / `scroll-body`（小屏内部滚动） |
| 3 | `ImagePicker` | 公共 `components/ImagePicker.vue` | 配图选择：≤3 张、压缩 ≤1MB 且 ≤750×1334、安检上传后回传 COS URL；提交中禁选 |
| 4 | `IconSvg` | 公共 `components/IconSvg.vue` | 星级图标：未选 `star`（线性浅灰）/ 已选 `star-filled`（实心黄） |
| 5 | `textarea`（`maxlength=500` + `n/500` 计数） | uni 内置控件 | 正文录入（选填，`auto-height`，上限 320rpx 后由弹层滚动承接） |
| 6 | 提交钮（页内 `view`） | `ReviewComposer.vue` 内联 | 主色实底；未选星 / 提交中禁用（文案「提交中…」） |

### 数据映射

| # | 数据（字段） | 来源 | 中文含义 | 显示在哪个组件 | 呈现位置 / 形式 |
|---|---|---|---|---|---|
| 1 | `dishName`（宿主页 prop） | `DishDetailVO.name` | 菜名 | `ReviewComposer` 顶部 `.rc-dish` | 次级灰小字，单行省略 |
| 2 | `records[0].rating` | `GET /my/reviews?dishId=&page=1&pageSize=1` | 本人旧评分 | `ReviewComposer` 星级行（**预填**） | 重评模式：星星亮起 + 提示「已评 N 星」 |
| 3 | `records[0].content` | 同上 | 本人旧正文 | `ReviewComposer` textarea（**预填**） | 重评模式回填 |
| 4 | `records[0].images` | 同上 | 本人旧配图（≤3） | `ImagePicker` 缩略图行（**预填**） | 重评模式回填，可删可增 |
| 5 | `records[0].id` → `reviewId`（prop） | 同上 | 本人评价 ID | 无界面（决定走 `PUT /reviews/{id}` 与标题「重新评价」） | 零可见 UI |
| 6 | `rating`（本地表单态） | 用户点选（1~5 星） | 评分 | 星级行 + 提示 `.rc-star-tip` | 未选时「点击星星打分（必填）」；已选「已评 N 星」 |
| 7 | `content`（本地表单态） | 用户输入 | 正文（≤500） | textarea + 右下 `.rc-count` | 实时 `n/500` 计数 |
| 8 | `images`（本地表单态） | `ImagePicker` v-model | 配图（≤3） | `ImagePicker` 网格 | 上传中 loading / 满额 `3/3` 计数格 |
| 9 | `submitting`（本地态） | 提交在途 | 提交状态 | 提交钮文案与禁用态 + `ImagePicker` 禁用 | 「提交中…」/ 半透明 |
| 10 | 提交结果 `{ id }` | `POST /dishes/{id}/reviews`（首次提交） | 新评价 ID | 无界面（随 `submitted` 事件上抛，宿主页本地写回底栏 → 切「重新评价」） | — |
| 11 | 覆盖提示文案 | 端内静态文案 | 重评语义 | `ReviewComposer` `.rc-overwrite-tip` | 「你已评价过此菜，本次提交将覆盖原评价」（浅底圆角条，仅重评模式） |
| 12 | 失败提示 | `400` / `4031` / `403` 响应 `message` | 未通过原因 | 无界面（Toast 直透，兜底「发布失败，请稍后重试」） | — |

**入参提交**：`POST /dishes/{id}/reviews`（首次，路径 `{id}` = 菜品 ID，body 不收 `dishId`）或 `PUT /reviews/{id}`（重评，路径 `{id}` = 本人评价 ID）→ `rating`（必填 1~5）/ `content`（选填 ≤500）/ `images`（选填 ≤3，COS 绝对地址）；配图**前置**经 `POST /upload/cloud-image`（入参 `fileId` → 出参 `url`）

**错误码**

| code | 含义 | 端上处置 |
|---|---|---|
| 400 | 评分越界 / 正文或配图超限 / 文本或图片内容安检违规 | Toast 直透 `message`，弹层不关闭 |
| **4031** | 邮箱未认证 | 跳身份认证页 `pages/auth/index`（认证成功返回后由宿主页 onShow 续接并重开弹层） |
| 403 | 非本人评价（重评） | Toast 提示 |

**控件类型**：`BaseSheet` 底部弹层（统一下拉关闭手势，阈值 ≈120px；`prefers-reduced-motion` 降级）、星级单选、`textarea`、图片选择网格

---

## 二、删除本人评价（三点菜单 + `ActionSheet` + 二次确认）

**两处宿主入口**（同一链路，共用公共组件）：

1. 菜品详情页：**每条评价卡右上角常驻竖三点**（更多操作，触控目标 ≥ 88rpx）→ 点击**底部弹出动作菜单**（`ActionSheet`，页面级通用组件）→ 本人的评价显示「删除评价」动作项（**危险红色态**：图标与文字同为错误色；他人的评价同位置为「举报评价」）→ 点击后进入二次确认。
2. 「我的主页」（[`client-我的主页.md`](./client-我的主页.md)）评价区：评价卡右上角三点 → 「删除评价」。

删除前有**二次确认**弹窗（不可恢复，故必须确认）：确认按钮取危险色实值（`MODAL_CONFIRM_DANGER_COLOR`），文案「确定删除这条评价吗？删除后不可恢复。」

### 组件清单

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `ReviewItem` | 公共 `components/ReviewItem.vue` | 评价卡本体 + **右上角常驻竖三点**（`IconSvg name="more-v"`，触控目标 ≥88rpx）→ `@more` 上抛被点评价 |
| 2 | `ActionSheet` | 公共 `components/ActionSheet.vue`（骨架 = `BaseSheet`） | 底部动作菜单，「删除评价」动作项（**危险红**：`icon` + `text` 同取错误色） |
| 3 | `uni.showModal` | uni 内置控件 | 二次确认弹窗：标题「删除评价」/ 正文「确定删除这条评价吗？删除后不可恢复。」/ 确认钮取危险色实值 |
| 4 | 列表容器（`.review-list` / `.list`） | 宿主页内联 | 删除成功后**本地移除该条**（`list.filter`），不整页重拉 |
| 5 | `RetryBlock`（仅「我的主页」） | 公共 | 删除后 / 首屏刷新失败的「加载失败 · 点击重试」兜底 |

> **无专属页面与专属弹层组件**：全部落在公共 `ReviewItem` + 公共 `ActionSheet` 上，两个宿主页共用同一链路。

### 数据映射

| # | 数据（字段） | 来源 | 中文含义 | 显示在哪个组件 | 呈现位置 / 形式 |
|---|---|---|---|---|---|
| 1 | `id` | `ReviewVO.id`（详情页）/ `MyReviewVO.id`（我的主页） | 评价 ID | 无界面（作 `DELETE /reviews/{id}` 路径参数） | 零可见 UI |
| 2 | `userId` | 同上 | 评价者用户 ID | `ReviewItem` 的 `current-user-id` 比对 → **决定三点菜单给谁** | 本人 → 「删除评价」；他人 → 「举报评价」 |
| 3 | 当前用户 `id` | `useUserStore().userInfo.id` | 本人身份 | 同上（页面侧传入 `:current-user-id`） | 零可见 UI |
| 4 | 删除结果 | `DELETE /reviews/{id}` 成功（`data` = null） | 成功 | 宿主评价列表 | 该卡片就地消失 + Toast「评价已删除」；删空后区块标题下线（我的主页） |
| 5 | 失败提示 | `400` / `403` / `4031` / `401` 响应 `message` | 失败原因 | 无界面（Toast 直透，兜底「删除失败」） | `4031` 需先跳身份认证页 |

**出参消费**：无（`Result<Void>`，成功即 `code=200`）
**入参提交**：`DELETE /reviews/{id}` → **无请求体、无参数**（归属由服务端按 token 判定，端上**不传 userId**）

**错误码**

| code | 含义 | 端上处置 |
|---|---|---|
| 400 | 评价不存在 | Toast 提示，本地不移除 |
| 403 | 只能删除自己的评价 | Toast 提示（正常不可达：入口已按 `userId` 收口） |
| **4031** | 邮箱未完成认证 | 跳身份认证页 `pages/auth/index` |
| 401 | 未登录 | 请求层静默重登并重试一次 |

**控件类型**：`ActionSheet` 底部动作菜单（整行热区，危险红动作项）+ `uni.showModal` 二次确认（破坏性操作必备）

---

## 三、举报评价（`ReportModal` 底部弹层）

入口 = 菜品详情页评价卡片右上角**三点菜单** → 「举报评价」（危险红色态）。

- 弹层：`ReportModal` **底部弹层**（BaseSheet 统一骨架：遮罩 / grabber / 下滑关闭手势 / 安全区），内容自上而下：
  1. 处理承诺行：「请选择举报原因，举报将在 48 小时内处理」（次级浅灰小字）；
  2. **原因单选列表**：选项来自后端字典 `GET /feedback/report-reasons`（弹层打开时实时拉取，端上零硬编码；展示顺序 = 后端 `order`）；每行整行热区（浅底圆角），**单选**——选中行主色文字加粗 + 主色浅底；**无文本输入框**；
  3. 提交钮：主色实底「提交举报」，未选中原因 / 提交中禁用（半透明）。
- 字典加载失败：列表区显示「举报原因加载失败，请关闭后重试」（次级浅灰，重开弹层重拉）。
- 提交成功 → Toast「举报已提交」并关闭弹层。

### 组件清单

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `ReportModal` | 页内私有 `pages/detail/dish/ReportModal.vue` | 弹层本体：处理承诺行 + 原因单选列表 + 提交钮（含字典拉取与选中态） |
| 2 | `BaseSheet` | 公共 `components/BaseSheet.vue` | 弹层骨架：遮罩 / grabber / 下滑关闭手势 / 安全区 / 标题「举报评价」/ 右上关闭钮 |
| 3 | 提交钮（页内 `view`） | `ReportModal.vue` 内联 | 主色实底「提交举报」，未选中原因 / 提交中禁用（半透明） |
| 4 | `ReviewItem`（宿主侧） | 公共 `components/ReviewItem.vue` | 入口所在：评价卡右上角常驻三点（`@more`） |
| 5 | `ActionSheet`（宿主侧） | 公共 `components/ActionSheet.vue` | 三点动作菜单，「举报评价」动作项（危险红：图标 + 文字） |
| 6 | `useReport.ts`（宿主侧编排） | 页内私有 | `openReport(reviewId)` / `submitReport(reasonValue)` 与提交中状态 |

### 数据映射

| # | 数据（字段） | 来源 | 中文含义 | 显示在哪个组件 | 呈现位置 / 形式 |
|---|---|---|---|---|---|
| 1 | `label` | `GET /feedback/report-reasons` | 原因中文标签 | `ReportModal` 原因单选行 `.rp-option-text` | 每行一个原因，整行热区、单选；选中 = 主色文字加粗 + 浅主色底 |
| 2 | `value` | 同上 | 原因机器值 | `ReportModal` 选中态判定 + 提交入参 `sub` | **端上零硬编码**（不落屏展示） |
| 3 | `order` | 同上 | 展示顺序 | `ReportModal` 列表顺序 | 按后端返回顺序渲染，**端上不排序** |
| 4 | 被举报评价 `id` | `ReviewVO.id`（宿主页经 `openReport(rv.id)` 注入） | 被举报评价 ID | 无界面（提交时作 `relatedId`） | 零可见 UI |
| 5 | 字典加载态 | 端上 `reasons` / `reasonsFailed` | 加载中 / 失败 | `ReportModal` `.rp-empty` | 「加载中…」/ 失败「举报原因加载失败，请关闭后重试」（重开弹层重拉） |
| 6 | 承诺行 | 端内静态文案 | 处理预期 | `ReportModal` `.rp-note` | 「请选择举报原因，举报将在 48 小时内处理」（次级灰小字） |
| 7 | 提交中态 | 端上 `submitting` | 提交在途 | 提交钮文案与禁用态 | 「提交中…」+ 半透明，防重复提交 |

**出参消费**：`GET /feedback/report-reasons` → `value` / `label` / `order`（三项全消费，见上表）
**入参提交**：`POST /feedback` → `type='report'` / `sub`（选中原因 `value`）/ `relatedType='review'` / `relatedId`（被举报评价 `id`）
**错误码**：`400` 举报必须指定关联对象 / 举报原因非法 / 你已举报过该内容 / 二级分类仅 `report` 有效 / 补充文本安检违规｜IP 限频 `400`「提交过于频繁」
**控件类型**：`BaseSheet` 底部弹层（遮罩 / grabber / 下滑关闭 / 安全区）、单选项行（`role="radio"`，整行热区）、禁用态提交钮
