# 功能总览（按板块 + 功能拆分）

> 本目录是「功能文档」的唯一真源：**每个功能一份独立文档**，文件名以板块前缀开头。
> 原来的单文件 `docs/功能文档.md` 已按功能拆分收敛为本目录（不再保留跳转页）。

## 板块与命名规则

| 文件名前缀 | 板块 | 代码目录 | 说明 |
|---|---|---|---|
| `client-` | **学生端（微信小程序）** | `client/` | 面向学生的 16 个功能；业务数据的唯一产生源 |
| `web-` | **管理端（Web 后台）** | `web/` | 面向管理员的 6 个功能；只经 `/admin/**` 读取与管理，不产生业务数据 |

> 阅读约定：每份文档固定为 **干什么 → UI → 操作 → 接口 → 字段（字段名 + 中文解释）→ 数据** 六段；讨论期可临时带「答疑」段，**拍板后结论并入正文、答疑清空**（QA 工作流见 `.codebuddy/rules/feature-doc-qa-workflow.md`）。
> 字段口径：字段名以**接口实况**为准（后端 Java DTO/VO 出参，统一 camelCase）；类型列的 `number / string / boolean / array / object / null` 为 JSON 侧类型。

## 鉴权标记

| 标记 | 含义 | 适用 |
|---|---|---|
| 🔓 公开 | 免登录即可调用 | 学生端浏览、搜索、反馈提交等 |
| 🔐 认证 | 需学号邮箱认证（`verified=true`），未认证返回 **4031** | 学生端 UGC 写操作（评价、评价点赞） |
| 🔑 口令 | 管理端 `X-Admin-Token` == 环境变量 `ADMIN_TOKEN`（未配置即 fail-closed 403） | 全部 `/admin/**` 与 `/upload/image` |

---

## client- · 学生端（微信小程序，16 个）

| 编号 | 功能 | 文档 | 鉴权 |
|---|---|---|---|
| A-01 | 微信静默登录 / 游客态 | [client-微信静默登录与游客态.md](./client-微信静默登录与游客态.md) | 🔓 |
| A-02 | 首页菜品浏览 | [client-首页菜品浏览.md](./client-首页菜品浏览.md) | 🔓 |
| A-03 | 搜索 | [client-搜索.md](./client-搜索.md) | 🔓 |
| A-04 | 菜品详情 | [client-菜品详情.md](./client-菜品详情.md) | 🔓 |
| A-05 | 浏览计数 | [client-浏览计数.md](./client-浏览计数.md) | 自动（接口需登录） |
| A-06 | 写评价 | [client-写评价.md](./client-写评价.md) | 🔐 |
| ~~A-07~~ | ~~评价「有用」~~ **⛔ 已于 2026-09-20 全链下线**（spec §7.30 清单 #1） | [client-评价有用.md](./client-评价有用.md)（历史留痕） | — |
| A-08 | 删除本人评价 | [client-删除本人评价.md](./client-删除本人评价.md) | 🔐 |
| A-09 | 举报评价 | [client-举报评价.md](./client-举报评价.md) | 🔓 |
| A-10 | 我的评价 | [client-我的评价.md](./client-我的评价.md) | 🔐 |
| A-11 | 意见反馈 | [client-意见反馈.md](./client-意见反馈.md) | 🔓 |
| A-12 | 系统通知（处理回执） | [client-系统通知.md](./client-系统通知.md) | 🔐 |
| A-13 | 个人资料 | [client-个人资料.md](./client-个人资料.md) | 🔓 |
| A-14 | 邮箱认证 | [client-邮箱认证.md](./client-邮箱认证.md) | 发码 🔓 / 核验需登录 |
| A-15 | 注销账号 | [client-注销账号.md](./client-注销账号.md) | 🔓 |
| A-16 | 隐私政策与用户协议 | [client-隐私政策与用户协议.md](./client-隐私政策与用户协议.md) | 🔓 |

## web- · 管理端（Web 后台，6 个）

| 编号 | 功能 | 文档 | 鉴权 |
|---|---|---|---|
| B-01 | 菜品管理（核心） | [web-菜品管理.md](./web-菜品管理.md) | 🔑 |
| B-02 | 菜品详情查看 | [web-菜品详情查看.md](./web-菜品详情查看.md) | 🔑 |
| B-03 | 评价管理（事后处置） | [web-评价管理.md](./web-评价管理.md) | 🔑 |
| B-04 | 反馈处理（唯一运营闭环） | [web-反馈处理.md](./web-反馈处理.md) | 🔑 |
| B-05 | 学生账号管理 | [web-学生账号管理.md](./web-学生账号管理.md) | 🔑 |
| B-06 | 图片上传（表单内） | [web-图片上传.md](./web-图片上传.md) | 🔑 / 需登录 |

---

## 通用结构（跨功能复用，只在此处定义一次）

### 统一响应信封（`Result<T>`，所有接口）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `code` | number | 业务状态码：`200` 成功；`400` 参数/业务校验失败（含内容安检违规）；`401` 未登录/token 失效；`403` 无权限；**`4031` 邮箱未认证**（前端据此弹 `AuthSheet`）；`500` 服务器异常 |
| `message` | string | 提示信息（失败时给用户看的文案） |
| `data` | T \| null | 业务载荷；写操作类接口通常为 `null` |

### 分页结构（`PageResult<T>`）

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `records` | T[] | **当前页数据行**（消费方以此为准） |
| `total` | number | 符合条件总条数 |
| `page` | number | 实际生效页码（服务端归一化后的值，非原始入参） |
| `pageSize` | number | 实际生效每页条数（同上） |
| `list` | T[] | 过渡期兼容字段，**恒等于 `records`**，新代码勿用 |

### 金额约定

**所有金额一律以「分」为单位传输**（`number`）；分↔元转换只允许在 `utils/money`（`fenToYuan / yuanToFen`），**禁止在页面/组件里裸算**。

---

## 字段名词典（结构 → 完整字段表所在文档）

| 结构名 | 用于哪些接口 | 完整字段表 |
|---|---|---|
| `DishVO` | `GET /dishes` | [client-首页菜品浏览](./client-首页菜品浏览.md#响应--dishvo单行菜品) |
| `DishDetailVO` | `GET /dishes/{id}` | [client-菜品详情](./client-菜品详情.md#响应--get-dishesid-data--dishdetailvo) |
| `RatingDistributionVO` | 同上（评分分布项） | [client-菜品详情](./client-菜品详情.md#响应--get-dishesid-data--dishdetailvo) |
| `HotSearchVO` | `GET /dishes/hot-search` | [client-搜索](./client-搜索.md#响应--get-disheshot-searchlisthotsearchvo) |
| `ReviewVO` | `GET /reviews`、`GET /my/reviews` | [client-菜品详情](./client-菜品详情.md#响应--get-reviews-data--page-resultreviewvo) ／ [client-我的评价](./client-我的评价.md) |
| `UsefulResult` | `POST /reviews/{id}/useful` | [client-评价有用](./client-评价有用.md) |
| `NotificationVO` | `/my/notifications*` | [client-系统通知](./client-系统通知.md) |
| `UserInfoVO` | `POST /auth/wechat-login`、`POST /auth/verify-email`、`GET|PUT /auth/profile` | [client-微信静默登录与游客态](./client-微信静默登录与游客态.md) |
| `LoginResp` | 登录 / 认证响应（`token` + `userInfo`） | [client-微信静默登录与游客态](./client-微信静默登录与游客态.md) |
| `CanteenInfoVO` | `GET /canteens` | [client-首页菜品浏览](./client-首页菜品浏览.md) |
| `CanteenWithStallsVO` / `StallDetailVO` | `GET /canteens?include=stalls`（原 `GET /canteens/all` 已删除） | [client-意见反馈](./client-意见反馈.md) |
| `FeedbackReq` | `POST /feedback` | [client-意见反馈](./client-意见反馈.md) |
| `DishAdminVO` / `DishAdminReq` | `/admin/dishes*` | [web-菜品管理](./web-菜品管理.md) |
| `ReviewAdminVO` | `GET /admin/reviews` | [web-评价管理](./web-评价管理.md) |
| `FeedbackAdminVO` / `FeedbackHandleReq` | `/admin/feedbacks*` | [web-反馈处理](./web-反馈处理.md) |
| `UserVO` | `GET /admin/users` | [web-学生账号管理](./web-学生账号管理.md) |

---

## QA 工作流（提问 → 答复 → 拍板 → 更新）

> 规则已固化为 `.codebuddy/rules/feature-doc-qa-workflow.md`，对 `docs/feature/` 下所有功能文档生效。

1. **提问**：在对应功能文档末尾追加一行 `Q:你的问题`（无需管格式）。
2. **答复**：我把 `Q:` 归一为 `### Q：` 标题、紧随补 `**A：**` 答复（先核实代码实况 / 平台规则再作答），并同步更新本 README 的「需拍板的待办清单」。
3. **拍板**：你在后续 `Q:` 中确认结论（如「确定下来了」）。
4. **更新**：我把拍板结论写回该文档正文六段，**并清空答疑段**——文档只保留当前有效口径；未落地的进「已拍板待实现清单」；涉及接口 / 库表的，先由技术负责人修订 `project_spec.md` / `api-design.md` 再动代码。
5. 全部功能核验通过 = 产品验收完成。

---

## 需拍板的待办清单（答疑产出的未决项）

| # | 事项 | 建议 | 影响面 | 详见 |
|---|---|---|---|---|
| 1 | 删除评价「有用」 | **已拍板：删除**（2026-09-17）；**先由技术负责人改 spec §7.14/§7.18 与 api-design §2.3 的默认排序口径，再动代码** | 跨三端 + `review_useful` 表 + `review.useful_count` + 评价默认排序改为 `created_at DESC` | [client-评价有用](./client-评价有用.md) |
| 2 | 删除系统通知 | **建议保留**（可降级为「我的」页内列表） | 跨三端 + `notification` 表 + 4 个端点 | [client-系统通知](./client-系统通知.md) |
| 3 | 浏览计数去掉当日去重 | **建议不改**（改前须定：含游客？风控？`view_log` 存废？） | 后端 + `view_log` + 热度排序口径（须先改 spec） | [client-浏览计数](./client-浏览计数.md) |
| 4 | 头像送 `imgSecCheck` | **建议补**（后端 `cloud://` 校验链路内送检，复用 `stable_token`） | 后端一处 + spec §5.a 增补 | [client-个人资料](./client-个人资料.md) |
| 5 | 注销入口 UI 重设计 | **同意重设计**，转 UI/UX 设计师出稿（不动接口） | 小程序页面内布局 / 交互 | [client-注销账号](./client-注销账号.md) |
| 6 | 隐私政策是否保留 | **建议保留**（改善呈现 + 文案与数据面对齐） | 无（静态文案） | [client-隐私政策与用户协议](./client-隐私政策与用户协议.md) |
| 7 | `/admin/dishes` 增加筛选/搜索参数 | **建议做**（keyword 含 alias + 服务端分页），并默认按更新时间倒序 | 后端既有端点加参数 + Web 对接 | [web-菜品管理](./web-菜品管理.md) |
| 8 | 学生行为弹窗是否展示浏览记录 | 需先新增管理端端点（当前 `view_log` 无查询接口），否则只展示评价 / 反馈 | 后端新增端点（须技术负责人登记） | [web-学生账号管理](./web-学生账号管理.md) |
| 9 | 评价「只看有图」筛选（`GET /reviews?hasImage=1`） | **已拍板：做**（2026-09-17）；曾登记「`isWithImage` 不恢复」，属推翻既有口径，**须技术负责人登记后再开发** | 后端 mapper 一条筛选条件 + 端上一个开关（切换须重置分页） | [client-菜品详情](./client-菜品详情.md#已拍板待实现清单) |
| 10 | 评价更新机制（重新评价） | **已拍板（最终口径）**：不做追评；评论区按**时间倒序**（`created_at DESC`，新评价在前）；重新评价 = `PUT /reviews/{id}` 覆盖 `rating/content/images` 并**刷新 `created_at`**（重评即新发布）、`is_hidden` 重置 0；一人一菜一评不变；不限次数 | 仅 1 个端点 + 端上「修改评价」入口（`ReviewComposer` 预填）；评分聚合重算一次；**聚合口径不改** | [client-菜品详情](./client-菜品详情.md#已拍板待实现清单) |
| 11 | 低样本均分失真 | **建议采纳**：`ratingCount < 3` 不展示均分，显示「暂无评分 · N 条评价」（大众点评「达 10 条才计算星级」的思路） | 端上按 `ratingCount` 分支，后端不改 | [client-菜品详情](./client-菜品详情.md#已拍板待实现清单) |
