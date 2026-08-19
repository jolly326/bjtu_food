# 「食在交大」接口测试文档

> 版本：2026-08-19（收尾复核）
> 适用范围：server（Spring Boot 后端，`context-path=/api`）、client（微信小程序）、web（管理后台）
> 目的：为接口功能正确性、边界条件、异常容错提供可执行的测试清单与记录模板。

---

## 1. 测试环境说明

| 项 | 说明 |
|----|------|
| 服务地址 | 生产部署于**微信云托管**（服务 `bjtu-food`），默认仅微信内部链路可达；测试需公网域名/API 网关或本地 `localhost:8080` |
| 根路径 | 所有接口前缀 `/api`（云托管 callContainer 已带 path） |
| 认证 | JWT Bearer Token；微信登录 `/auth/wechat-login` 换取 |
| 错误码 | `200` 成功 / `400` 参数 / `401` 未登录 / **`4031` 邮箱未认证** / `403` 无权限 / `500` 服务器错误 |
| 测试数据 | 使用现有库，测试后清理（风险项：残留脏数据，注意按 §5 记录） |

> ⚠️ **自动化前提**：需提供可访问的 base URL + 有效 token；否则以下清单按「人工测试」执行，将每个接口的实际返回反馈给开发判定。

---

## 2. 测试清单

### 2.1 公开接口（无需登录）

| # | 方法 | 路径 | 参数 | 预期 | 测试要点 |
|---|---|------|------|------|----------|
| A1 | GET | `/api/canteens` | `lat`/`lng`(可选) | open 食堂列表 | 无参不报错；带经纬度按距离排序 |
| A2 | GET | `/api/canteens/images` | 无 | `{食堂名:[url]}` | 字段结构正确 |
| A3 | GET | `/api/canteens/all` | 无 | 食堂含档口树 | 嵌套结构完整 |
| A4 | GET | `/api/dishes/hot` | `lat`/`lng`/`limit`(可选) | 热门菜品 TOP | limit 上限；非法 limit 容错 |
| A5 | GET | `/api/dishes/new` | 无 | 上新菜品 | 空库返回空数组不报错 |
| A6 | GET | `/api/dishes/promotions` | 无 | 促销菜品 | **FIND_IN_SET 精确匹配**，不误匹配 promotion_xxx |
| A7 | GET | `/api/dishes/hot-search` | 无 | 热搜 TOP10 | 字段结构 |
| A8 | GET | `/api/dishes/rising` | 无 | 新晋黑马 TOP10 | 空库容错 |
| A9 | GET | `/api/dishes` | keyword/canteenId/stallId/tag/minPrice/maxPrice/sortBy/sortOrder/page/pageSize | 分页菜品 | 分页越界（pageSize=99999 被 PageUtil 截断）；**tag 精确匹配**；价格区间 |
| A10 | GET | `/api/dishes/recommend` | page/pageSize/excludeIds | 猜你喜欢 | excludeIds 逗号解析；**超长数字串容错（不 500）**；未登录也返回 |
| A11 | GET | `/api/dishes/{id}` | id | 菜品详情 | 不存在 id 的返回；未上架菜品访问 |
| A12 | GET | `/api/moments` | tab/dishId/stallId/canteenId/page/pageSize | 社区广场 | **过滤正确性**：未审核动态不出现；分页 |
| A13 | GET | `/api/moments/{id}` | id | 动态详情 | 作者可见 rejectReason；他人不可见 |
| A14 | GET | `/api/moments/{id}/comments` | page/pageSize | 评论列表 | 扁平化 parentId；越界分页 |
| A15 | GET | `/api/reviews` | dishId/stallId/canteenId/page/pageSize/sort/isWithImage | 评价列表 | 三参至多一；都不传报错；sort=useful；isWithImage 过滤 |
| A16 | GET | `/api/dishes/{dishId}/reviews` | page/pageSize/sort | 菜品评价 | 同 A15 简化版 |
| A17 | GET | `/api/categories` | 无 | 品类列表 | enabled 过滤 |
| A18 | GET | `/api/broadcasts` | 无 | 广播条 | enabled 过滤、排序 |
| A19 | GET | `/api/activities` | 无 | 活动列表 | **返回裸 List**（非分页结构）；空库空数组 |
| A20 | POST | `/api/feedback` | FeedbackReq | 提交反馈 | 游客无 token 可提交 |
| A21 | GET | `/api/images/**` | — | 静态图片 | 图可访问；越权目录（`../`）应 404 |

### 2.2 需登录接口（学生态）

> 需先经微信登录 + 邮箱认证（`verified=true`）获得 token。

| # | 方法 | 路径 | 认证 | 参数 | 预期 | 测试要点 |
|---|---|------|------|------|------|----------|
| B1 | GET | `/api/auth/profile` | 登录 | 无 | 用户资料 | **不含 openid**；返回 nickname/avatar/verified/bindEmail/guestShortId |
| B2 | PUT | `/api/auth/profile` | 登录 | `{nickname, avatar}` | 更新资料 | **avatar 校验**：外链 http 被拒；仅 `/images/`、`/uploads/`、`cloud://` 通过 |
| B3 | PUT | `/api/auth/password` | 登录 | `{oldPassword, newPassword}` | 改密 | 旧密码错返回；新密码 6-64 位校验 |
| B4 | POST | `/api/dishes/{id}/view` | 登录 | id | 浏览量+1 | **同步写 view_log 足迹**（同 user+dish 去重 upsert） |
| B5 | POST | `/api/dishes` | 邮箱认证 | DishPublishReq | 发布菜品(pending) | 未认证返回 **4031**；**价格 0~999900 分校验** |
| B6 | PUT | `/api/dishes/{id}` | 邮箱认证 | id + req | 编辑重提 | 非本人 403/404；重提重置 audit_status |
| B7 | DELETE | `/api/dishes/{id}` | 邮箱认证 | id | 删本人菜品 | 非本人 403；级联清理评价 |
| B8 | POST | `/api/reviews` | 邮箱认证 | ReviewReq | 提交评价 | **rating 越界(0/6) 被 @Valid 拒**；每菜一人一评；content≤500 |
| B9 | PUT | `/api/reviews/{id}` | 邮箱认证 | id + ReviewReq | 修改评价 | **rating 越界被拒**（@Valid 已补）；非本人 403 |
| B10 | DELETE | `/api/reviews/{id}` | 邮箱认证 | id | 删评价 | 级联清理 useful |
| B11 | DELETE | `/api/my/reviews/{id}` | 邮箱认证 | id | 契约路径删 | 同 B10 |
| B12 | POST | `/api/reviews/{id}/useful` | 邮箱认证 | id | 有用切换 | 幂等；一人一票；useful_count 增减；**并发取消不漂移** |
| B13 | GET | `/api/my/reviews` | 登录 | page/pageSize | 我的评价 | 仅本人；不含被隐藏 |
| B14 | POST | `/api/moments` | 邮箱认证 | MomentPublishReq | 发动态(pending) | 未认证 4031；含评分走评价逻辑 |
| B15 | PUT | `/api/my/moments/{id}` | 邮箱认证 | id + req | 编辑动态 | 评分回填不丢；非本人 403 |
| B16 | DELETE | `/api/my/moments/{id}` | 邮箱认证 | id | 删动态 | 级联评论/通知 |
| B17 | GET | `/api/my/moments` | 登录 | auditStatus | 我的动态 | 状态过滤 |
| B18 | POST | `/api/moments/{id}/useful` | 邮箱认证 | id | 有用切换 | 幂等计数；并发取消不漂移 |
| B19 | POST | `/api/moments/{id}/comments` | 邮箱认证 | MomentCommentReq | 发评论 | **parent 归属校验**（跨动态回复被拒）；commentCount+1 |
| B20 | DELETE | `/api/my/moments/{id}/comments/{cid}` | 邮箱认证 | id,cid | 删评论 | 仅作者；子回复连带；**并发删除计数不漂移** |
| B21 | GET | `/api/my/notifications` | 邮箱认证 | isRead/page/pageSize | 消息列表 | **未认证返回 4031**（已补 @RequireVerified） |
| B22 | GET | `/api/my/notifications/unread-count` | 邮箱认证 | 无 | 未读总数 | 驱动红点；未认证 4031 |
| B23 | PUT | `/api/my/notifications/{id}/read` | 邮箱认证 | id | 单条已读 | 非本人 id 幂等返回成功 |
| B24 | PUT | `/api/my/notifications/read-all` | 邮箱认证 | 无 | 全部已读 | 单条原子 UPDATE 性能 |
| B25 | GET | `/api/feedback/my` | 登录 | 无 | 我的反馈 | 含管理员回复 |
| B26 | POST | `/api/my/apply` | 邮箱认证 | ApplyReq | 提交申请 | 同(entityType,entityId,applyType) pending 返 409 |
| B27 | GET | `/api/my/apply` | 登录 | status | 我的申请 | 状态过滤 |
| B28 | GET | `/api/my/submissions` | 登录 | 无 | 我的提交聚合 | apply+moment 聚合 |

### 2.3 需人工操作的接口（无法自动，需真实外部依赖）

| # | 接口 | 需人工原因 | 人工步骤 | 验证点 |
|---|------|------------|----------|--------|
| C1 | POST `/api/auth/wechat-login` | 需真实小程序 wx.login code + AppID/Secret | 微信开发者工具打开小程序，触发 wx.login | 返回 token；新 openid 建号 verified=false；旧 openid 返回原账号 |
| C2 | POST `/api/auth/email-code` | 需向真实 @bjtu.edu.cn 邮箱发验证码（SMTP） | 填学号 → 发送验证码 → 查收邮件 | 60s 限频；6 位码 10 分钟有效；**验证码不随响应返回** |
| C3 | POST `/api/auth/verify-email` | 需邮箱收到的验证码 | 输入验证码 → 提交认证 | verified 变 true；返回新 token；**4031→200**；**验证码只能消费一次** |
| C4 | POST `/api/upload/image` | 需真实 multipart 文件上传 | 小程序选择图片上传 | 返回 url+relativeUrl；**超大图/非图文件拒绝**；透明 PNG 缩略图非黑底 |
| C5 | POST `/api/auth/admin/login` | 需管理后台账号密码 | 管理后台登录页输入 admin 账号 | 返回 token；错误密码提示 |
| C6 | 全部 `/admin/**` | 需 ADMIN 角色 token + 破坏性写操作 | 用 admin 登录后在后台逐个操作 | 非 admin 访问 403；CRUD 正确性 |

### 2.4 管理后台接口（需 ADMIN 角色，人工验证）

| # | 方法 | 路径 | 测试要点 |
|---|------|------|----------|
| D1 | GET | `/api/admin/dashboard` | range=week/month/all；**all=90 天不被吞**；表缺失容错 |
| D2 | GET | `/api/admin/dishes` | **分页 IPage**（{records,total}）；page/pageSize 生效 |
| D3-D5 | POST/PUT/DELETE | `/api/admin/dishes*` | 校验；删除级联清评价 |
| D6 | GET | `/api/admin/users` | role/status 过滤；分页结构 |
| D7-D8 | PUT | `/api/admin/users/{id}/status`、`/role` | 启停用户 disabled 不可登录；角色互转 |
| D9-D14 | GET/POST/PUT/DELETE | `/api/admin/admins*` | 仅 SUPER_ADMIN；超管专属 |
| D15 | GET | `/api/admin/reviews` | **无 isDeleted 参数**；isHidden/userId 过滤 |
| D16-D17 | PUT/DELETE | `/api/admin/reviews/{id}/hide`、`/{id}` | 隐藏后前端不显示；删除清理 useful 孤儿 |
| D18-D20 | GET/POST | `/api/admin/moments*` | hide/delete/approve；方法级 @PreAuthorize |
| D21-D23 | GET/POST | `/api/admin/apply*` | approve/reject；rejectReason 必填；**价格 0~999900 校验** |
| D24 | GET | `/api/admin/feedbacks*` | 回复 |
| D25-D28 | CRUD | `/api/admin/canteens*` | 食堂/档口增删改 |
| D29-D32 | CRUD | `/api/admin/categories` | code 唯一；**sortOrder 非数字返回 400** |
| D33-D36 | CRUD | `/api/admin/broadcasts` | broadcastType 校验 |
| D37 | GET | `/api/admin/stats*` | 容错 |
| D38 | GET | `/api/admin/operation-logs` | 分页 |
| D39 | GET | `/api/admin/audit*` | 待审列表 |

---

## 3. 测试记录模板

每个接口测试后按以下格式记录：

```
### [A9] GET /api/dishes
- 测试时间：____
- 前置：____（token / 参数）
- 用例 1：正常分页 → 实际返回：____ → ✅/❌
- 用例 2：pageSize=99999 → 实际返回：____ → ✅/❌（预期：被截断到上限）
- 用例 3：tag=signature 精确匹配 → 实际返回：____ → ✅/❌
- 结论：通过 / 未通过（缺陷见 §4）
```

---

## 4. 缺陷分级与登记

| 级别 | 定义 | 示例 |
|------|------|------|
| **P0 阻断** | 接口崩溃、数据错乱、越权、核心流程不可用 | 未授权访问他人数据、删除导致计数漂移 |
| **P1 严重** | 边界条件异常、校验缺失导致脏数据 | rating 越界入库、价格负值 |
| **P2 一般** | 错误提示不友好、性能隐患、字段缺失 | 分页无上限、N 次往返 |
| **P3 建议** | 契约不一致、文档缺失、代码异味 | 死代码、注释过时 |

缺陷登记：

```
- 编号：BUG-001
- 级别：P1
- 接口：____
- 现象：____
- 复现步骤：1)____ 2)____ 3)____
- 预期 vs 实际：____
- 状态：待修复 / 已修复(commit) / 人工复测
```

---

## 5. 已知契约要点（测试时注意）

1. **4031 vs 403**：`4031`=邮箱未认证（前端弹认证引导）；`403`=普通无权限（前端提示"无权限"）。不要混淆。
2. **分页**：后端统一 `PageUtil.normalize`（page≥1、pageSize 上限）；返回 `IPage{records,total}`（部分老接口返回裸 List，如 `/activities`）。
3. **金额单位**：后端 price 以**分**存储；前端 money.ts 在 API 层转换元/分，页面不裸换算。
4. **UGC 过滤**：未审核（audit_status=pending）与已隐藏（is_hidden=1）内容**后端过滤**，前端仅展示。
5. **活动入口**：首页/我的页活动入口点击提示"功能暂未实现"，活动列表页与 `/activities` 接口保留待开放。
6. **通知接口**：已补 `@RequireVerified`，未认证（游客）访问返回 4031。
7. **验证码**：60s 限频；10 分钟有效；消费为原子 `UPDATE ... WHERE used_at IS NULL`，同一验证码只能使用一次。

---

## 6. 回归测试建议

| 场景 | 覆盖接口 | 说明 |
|------|----------|------|
| 登录链路 | C1→C2→C3 | 微信登录→发码→认证→verified=true |
| 发布链路 | B5/B8/B14→D18-D23 | 学生提交→后台审核→前端可见 |
| 互动链路 | B12/B18/B19 | 点赞/有用/评论计数一致 |
| 评分聚合 | B8→A11 | 提交评价后菜品 avg_rating 重算 |
| 越权防护 | B6/B16/D2 | 非本人操作、非 admin 访问 admin 接口 |
| 分页边界 | A9/A12/B21/D2 | pageSize 越界、末页判断、触底加载 |
| 并发计数 | B12/B18/B20 | 快速连点/并发删除计数不漂移 |
