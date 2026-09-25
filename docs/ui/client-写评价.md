# 写评价 — 页面 UI 设计稿

> 所属端：学生端（微信小程序） ｜ 归属功能文档：[client-写评价.md](../feature/client-写评价.md)
> **口径分工**：**页面 UI 设计口径以本文件为唯一真源**，功能流程 / 接口 / 字段 / 数据落库口径以功能文档为准。
> 落点：**无独立页面** —— 撰写弹层 `ReviewComposer`；入口 = 菜品详情底栏主按钮（「写评价」/「重新评价」双态）。


- 入口：菜品详情评价区底栏「写评价」按钮。
- **判定时机**：点击入口后、弹层打开前调「我的评价（按菜过滤）」——已评价以「重新评价」模式打开（**预填本人旧值**，弹层内菜名下方显示覆盖提示「你已评价过此菜，本次提交将覆盖原评价」，浅底圆角提示条）；未评价打开空表单。
- 弹层：`ReviewComposer` 底部弹层（统一下拉关闭手势，阈值约 120px），内容为：星级选择 + 文字输入框 + 配图选择器（`ImagePicker`，自动压缩 ≤1MB、≤750×1334）。
- 未认证用户点入口 → 跳转身份认证页 `pages/auth/index`（入口不置灰；认证成功返回本页后由 onShow 续接，自动重新打开写评价表单）。

## 接口数据字段（UI 精修用）

**页面**：无独立页面（`ReviewComposer` 底部弹层，宿主 = 菜品详情页）

**出参消费**
| 接口 | 字段 | 端上用途 |
|---|---|---|
| `GET /my/reviews?dishId=&page=1&pageSize=1` | `records[0].id` | 判定已评价 → 切换「重新评价」并作为 `PUT` 目标 |
| | `records[0].rating` / `content` / `images` | 弹层**预填旧值** |

**入参提交**：`POST /dishes/{id}/reviews`（首次）或 `PUT /reviews/{id}`（覆盖）→ `rating` / `content` / `images`（配图先经 `POST /upload/cloud-image` 取回地址）

**UI 组件**：`BaseSheet`（经 ReviewComposer）/ `ImagePicker` / `IconSvg`
**控件类型**：底部弹层（下滑关闭，阈值 ≈120px）、星级选择、`textarea`、图片选择器（自动压缩 ≤1MB / ≤750×1334）
