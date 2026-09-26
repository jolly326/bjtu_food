# 写评价 — 页面 UI 设计稿

> 所属端：学生端（微信小程序） ｜ 归属功能文档：[client-写评价.md](../feature/client-写评价.md)
> **口径分工**：**页面 UI 设计口径以本文件为唯一真源**，功能流程 / 接口 / 字段 / 数据落库口径以功能文档为准。
> 落点：**无独立页面** —— 撰写弹层 `ReviewComposer`；入口 = 菜品详情底栏主按钮（「写评价」/「重新评价」双态）。


- 入口：菜品详情评价区底栏「写评价」按钮。
- **判定时机**：点击入口后、弹层打开前调「我的评价（按菜过滤）」——已评价以「重新评价」模式打开（**预填本人旧值**，弹层内菜名下方显示覆盖提示「你已评价过此菜，本次提交将覆盖原评价」，浅底圆角提示条）；未评价打开空表单。
- 弹层：`ReviewComposer` 底部弹层（统一下拉关闭手势，阈值约 120px），内容为：星级选择 + 文字输入框 + 配图选择器（`ImagePicker`，自动压缩 ≤1MB、≤750×1334）。
- 未认证用户点入口 → 跳转身份认证页 `pages/auth/index`（入口不置灰；认证成功返回本页后由 onShow 续接，自动重新打开写评价表单）。

## 接口数据字段（UI 精修用）

**页面**：无独立页面（`ReviewComposer` 底部弹层，宿主 = 菜品详情页 `pages/detail/dish/index`）

### 组件清单（本界面需要哪些组件）

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `ReviewComposer` | 页内私有 `pages/detail/dish/ReviewComposer.vue` | 弹层本体：菜名 + 重评提示 + 星级 + 正文 + 配图 + 提交（承载表单语义） |
| 2 | `BaseSheet` | 公共 `components/BaseSheet.vue` | 弹层骨架：遮罩 / grabber / 下滑关闭 / 安全区 / 标题（「写评价」/「重新评价」按模式切换）/ 右上关闭钮 / `scroll-body`（小屏内部滚动） |
| 3 | `ImagePicker` | 公共 `components/ImagePicker.vue` | 配图选择：≤3 张、压缩 ≤1MB 且 ≤750×1334、安检上传后回传 COS URL；提交中禁选 |
| 4 | `IconSvg` | 公共 `components/IconSvg.vue` | 星级图标：未选 `star`（线性浅灰）/ 已选 `star-filled`（实心黄） |
| 5 | `textarea`（`maxlength=500` + `n/500` 计数） | uni 内置控件 | 正文录入（选填，`auto-height`，上限 320rpx 后由弹层滚动承接） |
| 6 | 提交钮（页内 `view`） | `ReviewComposer.vue` 内联 | 主色实底；未选星 / 提交中禁用（文案「提交中…」） |

### 有哪些数据要显示、显示在哪个组件

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
