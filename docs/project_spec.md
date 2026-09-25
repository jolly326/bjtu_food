# 知行食记 · 核心准则（project_spec.md）

> **最高权威基础规范**：所有 agent 与协作者必须服从本文件；与其他文档冲突时以本文件为准。
> **文档为唯一权威**：不得据代码现状反向推翻文档；代码未实现的部分按本文件补齐，而非删改文档。开发实现只须消除静态错误，编译与运行由用户执行。
> **唯一可修改者：技术负责人**。其余角色发现与本文件或代码冲突时，须提技术负责人裁定，不得自行绕过。
> **修改本文件任何口径须重新拍板**；被否决的能力以 §1.4「明确不做清单」留痕，恢复的唯一路径 = 重新拍板，禁止以任何形式重提或变相保留。
> 本文件是**纯粹的当前口径说明**：不含变更记录、修订痕迹与历史叙述；历史追溯由 git 历史承担。
> **真源分工**：原则与红线 = 本文件；功能细节 = `docs/feature/`（按**功能**拆分，每份独立自洽，五段：介绍 → UI → 接口 → 字段 → 数据）；页面 UI = `docs/ui/`（按**页面/组件**拆分，UI 设计唯一真源）；协作流程与角色 = `.codebuddy/agents/`、`.codebuddy/rules/`。

---

## 1. 产品定位与定型边界

### 1.1 一句话定位

交大人的「吃什么不踩雷」——**以菜品为唯一核心实体**的校园菜品信息展示与检索平台：信息展示 + 搜索 + 认证评价 + 反馈驱动的轻 UGC 信息共建。

### 1.2 五个定型支柱

1. **信息展示优先**：名称 / 价格 / 档口 / 楼层 / 营业信息 / 描述四维 / 图片做扎实；「售罄 / 今日供应」即时状态一期不做。
2. **轻社区边界**：UGC 只有两种形态——**评价**（1-5 星 + 文字 + 配图 ≤3 张，需学号邮箱认证）与**反馈**（公开提交、无需登录：「我要反馈问题」文字 + 配图 ≤3 张、「我要更新信息」结构化菜品快照含图片 ≤9 张）。不做动态 / 关注 / 私聊 / 收藏。
3. **学生诉求通道唯一 = 反馈**：意见反馈页双模式（「我要反馈问题」`POST /feedback` /「我要更新信息」菜品信息纠错 `POST /dishes/{id}/correction`，独立表 `dish_correction`，经管理员两段式档口确认采纳写回菜品）+ 评价卡「举报」入口；菜品信息发布源唯一 = 管理员；不恢复学生直建菜卡接口。
4. **合规底线**：全部 UGC 过**微信内容安全检测**（`msgSecCheck` / `imgSecCheck`）——`pass` 与 `review`（疑似）一律放行，`risky`（含未知 / 缺失态，fail-closed）拦截；无任何人工环节；评价对他端可见的唯一判据 = `is_hidden=0`。
5. **轻运营**：无运营位（Banner 首页轮播除外，见 §4.7）；菜品数据管理员手动维护；Excel 批量导入推二期。

### 1.3 四大核心板块（唯一投入方向）

1. **菜品信息展示**：浏览 / 筛选 / 排序 / 详情；食堂与档口降级为菜品属性，无独立路由。
2. **搜索与查找**：二级搜索页 `find` + 结果列表，属核心主线，不得降级或移除。
3. **UGC 评价类**：菜品详情页评价区 + 底栏「写评价」；一人一菜一评，支持覆盖式重新评价；配图 ≤3 张，全部过内容安检。
4. **UGC 反馈类**：反馈与评价同等重要、不得弱化——意见反馈页双模式（「我要反馈问题」文本 + 配图 /「我要更新信息」菜品信息纠错七字段快照，管理员两段式档口确认采纳写回）+ 菜品详情页信息卡「信息有误？」入口；价值锚点 = 实时发现信息错误与程序问题、驱动菜品信息与程序优化。

### 1.4 明确不做清单（恢复须重新拍板）

社区/动态、活动/公告、收藏、品类维度、评价「有用」点赞、菜品独立审核、人工复核、操作日志、工作台/聚合看板/统计报表、学生菜品写接口、食堂/档口实体语义（停业/营业时间/删除）、`portion` / `spice_level` / `region` / `tags` / `alias` 字段、坐标与距离概念、随机推荐 / 榜单 / 分享、「一键转菜品」、第三个价格字段、`isMine` 替代 `userId`、页面级下拉刷新、`promo_price`。

### 1.5 演进预留（做之前须重新拍板）

Excel 批量导入（二期）→ OCR 菜单识别（同一导入通道）；微信订阅消息推送；售罄 / 今日供应即时状态；推荐算法演进；多校区扩展。

---

## 2. 角色与认证

### 2.1 角色模型（仅两种）

- **`STUDENT`**：微信打开小程序即静默登录为**游客态**（`bind_email` 为 NULL）；`@bjtu.edu.cn` 邮箱验证码认证后解锁 UGC 写操作（写评价 / 删本人评价）。学生端**无任何菜品写接口**。
- **`ADMIN`**：反馈处理 + 信息纠错处理（运营闭环）+ 评价事后处置（隐藏 / 显示 / 删除）+ 菜品 CRUD 与上下架 + 学生账号管理。**禁止 `STALL_OWNER` 角色与 `/stall-owner/**` 路由**。

### 2.2 认证体系（微信登录体系，无账号密码）

- **无账号密码登录**：无登录页 / 登录按钮 / 注册页 / 密码修改。`POST /auth/wechat-login` 静默登录（`code2Session` 按 `openid` 取号，不存在自动建号）；游客昵称默认「食客 + ID 尾 4 位」（端上按 `id` 派生，接口不出参）。
- **认证判据唯一真源 = `user.bind_email` 非空**：服务端收敛为 `AuthStateUtil#isVerified`；小程序端收敛为 `useUserStore().isVerified()`（`!!bindEmail`）；管理端按 `bindEmail` 派生。
- **认证态不进 JWT**：JWT claims 仅含 `userId`（7 天），后端按 `bind_email` 实时判定。
- **绑定与迁移**：邮箱是唯一迁移 / 绑定凭证；同一邮箱被新微信认证时直接替换旧绑定（历史数据归属随迁）；不设解绑入口。
- **权限矩阵**：游客可浏览全部公开数据 + `POST /feedback` / `POST /dishes/{id}/correction`（公开免认证）；UGC 写操作需已认证（`4031` 跳转身份认证页 `pages/auth/index`，**入口不置灰**）；已认证但缺 openid → `403` 提示微信登录（**禁止自动换号**，须用户确认）；系统通知 `/my/notifications/*` 服务端认证专属、前端游客直达且静默处理。
- **注销**：`DELETE /auth/account` 匿名化（nickname→「已注销用户」、openid 解绑、bind_email 清空、status=deleted），评价 / 反馈保留去身份化；token 立即失效；幂等。

### 2.3 管理端鉴权（无登录体系）

`/admin/**` 由 `AdminTokenFilter` 校验请求头 `X-Admin-Token` == 环境变量 `ADMIN_TOKEN`，未配置 fail-closed 403；与小程序微信登录完全解耦；无 `/auth/admin/login`、无 BCrypt、无 JWT、无角色分层；`user` 表仅承载学生（无 `role` / `password` 列）；「单口令即单人」，不追究操作人身份，无任何操作留痕 / 审计能力。

---

## 3. 三端架构与技术栈

### 3.1 三端定位

| 端 | 目录 | 定位 |
|---|---|---|
| 小程序 | `client/` | 学生使用，**业务数据的唯一产生源头** |
| 后端 | `server/` | 唯一数据存储与业务规则；三端共用同一套 API 契约 |
| Web 管理端 | `web/` | 辅助管理数据的 UI 工具（非用户端），只经 `/admin/**` 消费与管理 |

数据链路：小程序产生数据 → 后端落库（MySQL）→ Web 经 `/admin/**` 读取与管理 → 小程序即时反映。**Web 端任何新增管理能力，必须以小程序已存在的数据对象为前提。**

### 3.2 技术栈

- 后端：Spring Boot 3.2 + Java 21 + MyBatis-Plus 3.5.5（BaseMapper + XML）；API 文档 SpringDoc OpenAPI（不使用 Knife4j）。
- 小程序：uni-app + Vue 3（`<script setup>`）+ TypeScript + Pinia。
- Web：Vue 3 + Vite + TypeScript + Element Plus，无 Pinia；`snake_case` 仅允许存在于 `api/adapter.ts` 内部。
- 数据库：MySQL 8.0，库 `bjtu_food`，utf8mb4；建表脚本唯一权威 = `server/src/main/resources/db/schema.sql`。

### 3.3 后端分包与分层

按业务分包 `com.bjtufood.{auth|canteen|dish|review|feedback|notify|history|upload|banner|common}`，每模块 `controller/service(+impl)/mapper/entity/dto` 四层，**禁止跨层调用**（Controller 不得直接调 Mapper）。Controller 入参 DTO + `@Validated`；Service 写操作 `@Transactional`；评分 / 计数聚合走 Spring 事件异步维护，禁止主流程内联重算。

### 3.4 小程序分层与页面架构

- 分层职责：`api/`（HTTP 契约薄层）、`types/`（共享 DTO）、`stores/`（全局状态）、`composables/`（跨页逻辑编排，页面一次性编排就近放页面包）、`utils/`（纯函数）、`theme/`（设计令牌）、`components/`（仅 ≥2 页使用的公用组件）、`pages/`（分包根按功能域组织；页面内复用模块抽**包内私有组件**且只做一级拆分）。
- 组件组织：components 与 pages 双向定期治理；迁移不改变行为，type-check + mp-weixin 构建全绿（细则见 `.codebuddy/rules/client-components-org.md`）。
- import 风格：同目录 `./X`、跨层 `@/…`，**禁止 `../` 跨层逃逸**；不引入 barrel 重导出。
- 页面共 **10 个**——主包 3：`home`（首页）/ `mine`（我的）/ `find`（搜索，首页搜索框进入的二级页，留主包）；分包 7：`detail/dish`（菜品详情，唯一预载分包）/ `auth`（身份认证）/ `notifications`（系统通知）/ `feedback`（意见反馈）/ `my-reviews`（**我的主页**：用户信息卡 + 名下评价列表）/ `profile`（个人信息编辑）/ `privacy`（隐私政策与用户协议）。TabBar 固定 2 页（home / mine）。
- 公共组件 11 个：`AppButton / AppHeader / AppTitleBand / BaseSheet / CardSection / IconSvg / ImagePicker / RetryBlock / SearchBar / SectionTitle / TabBar`；其余为页包内私有组件。
- 认证承载于**独立认证页** `pages/auth/index`（页头「身份认证」+ 学号 / 邮箱验证码表单 + 主按钮「认证」+ 隐私说明，复用全站表单字段语言：浅底无边框输入项、主色实底主按钮）；所有需认证入口（「我的」页身份认证格、详情页写评价 / 删除评价守卫、请求层 `4031`）经 `uni.navigateTo` 跳转认证页，认证成功返回原页后由原页 `onShow` 续接待办动作，未完成认证离开认证页即清除待办；发码 60s 冷却由 `stores/auth` 持有（跨进出页面持久）；软键盘弹起时输入区须上推且提交按钮不被遮挡。

### 3.5 Web 管理端 IA（一级导航 5 项，路由 1:1，层级 ≤2）

| 导航 | 路由 | 职责 |
|---|---|---|
| 菜品 | `/dashboard/content`（默认落地页） | 菜品 CRUD / 上下架；详情页 `/dashboard/content/dishes/:dishId` |
| 评价 | `/dashboard/reviews` | 事后处置（隐藏 / 显示 / 删除） |
| 反馈 | `/dashboard/feedback` | 问题反馈处理（含存量类型回看） |
| 信息纠错 | `/dashboard/corrections` | 菜品信息纠错处理（字段对比 / 两段式档口确认采纳 / 拒绝） |
| 学生账号 | `/dashboard/system` | 学生账号管理 |

页面结构：页头（H1 + 主操作）→ 主体（筛选 + 列表/表单）；禁止 `.stat-inline` 只读统计块（数量只在表格 footer 出现一次）、禁止聚合壳 / 分类卡中间层 / 全局聚合看板；公共组件 `DataTable / FormDialog / ConfirmDialog / StatusTag / ImageUpload / ReviewDetailDialog`；搜索统一 `el-input`。

---

## 4. 接口契约

### 4.1 响应信封与错误码

- 统一信封 `{ code, message, data }`；异常由 `GlobalExceptionHandler` 统一包装，Controller 不得裸抛。
- 错误码：`200` 成功 / `400` 参数与业务校验 / `401` 未登录 / `403` 无权限 / `500` 服务器错误；受控例外：**`4031`** = 邮箱未认证（端上跳转身份认证页 `pages/auth/index`）、**`4001`** = 资源不存在（含已下架菜品，端上给「不存在」文案 + 仅返回路径，与网络故障的可重试态区别对待）。**禁止自定义其余非标错误码。**
- 401 处理：小程序先静默登录再自动重试一次，仍失败走 `handleUnauthorized`（并发去重防登录风暴）；Web 端统一提示口令无效，无登录页。

### 4.2 RESTful 原则（强制）

路径以资源为中心、HTTP 方法表达动作（GET 读 / POST 建 / PUT 全量改 / DELETE 删）；**子资源用嵌套路径**（`GET /dishes/{id}/reviews`）；**禁止**查询参数表达归属、路径塞动作动词。

### 4.3 端点清单（当前全集）

**公开读**：`GET /dishes`（keyword / mealType，恒热度降序）、`GET /dishes/{id}`（**副作用：每次成功响应浏览量 +1 并写入访问日志**，IP 限频 30/分 + 300/时）、`GET /dishes/{id}/reviews`（pageSize 归一 20）、`GET /dishes/meal-types`（在售大类字典）、`GET /dishes/attributes`（描述四维字典）、`GET /dishes/for-you`（随机在售菜品名 ≤6）、`GET /banners`、`GET /images/**`。
**公开写**：`POST /feedback`（免认证，问题反馈 `issue`）；`POST /dishes/{id}/correction`（免认证，菜品信息纠错七字段快照，菜品不存在 / 未上架 → `4001`，IP 限频 2/分 + 10/时）。
**认证域**：`POST /auth/wechat-login` / `email-code`（60s 限频、10min 有效）/ `verify-email`；`GET|PUT /auth/profile`；`DELETE /auth/account`。
**UGC（需认证）**：`POST /dishes/{id}/reviews`、`PUT /reviews/{id}`（覆盖重评，刷新 `created_at`）、`DELETE /reviews/{id}`；`GET /my/reviews`（本人视角，`dishId` 可选过滤）。
**通知（需认证）**：`GET /my/notifications` / `unread-count`；`PUT /my/notifications/read-all` / `{id}/read`。
**上传**：`POST /upload/images`（云存储 fileID → `imgSecCheck` → COS 转存，单张契约，需登录）；`POST /upload/image`（multipart，管理端口令守卫）；小程序头像走微信云存储 `cloud://` 直存。
**管理端（口令）**：`GET|PUT /admin/canteens`、`GET|PUT /admin/stalls`（字典仅新增[随菜品 upsert]/改名/查看，**无删除**）、`GET|POST|PUT|DELETE /admin/dishes`、`PUT /admin/reviews/{id}/hide`、`DELETE /admin/reviews/{id}`、`GET /admin/feedbacks`、`PUT /admin/feedbacks/{id}`、`GET /admin/corrections`、`POST /admin/corrections/{id}/adopt`（**两段式档口确认**：带 `stallId` ＞ `stallName` 精确匹配 ＞ `createIfMissing` 按名新建；均未命中返回候选 `{needStallConfirm, candidates}` 不执行采纳；采纳 = 七字段写回目标菜品，可空快照字段不覆盖既有值）、`PUT /admin/corrections/{id}`（拒绝：回复 + 不采纳原因必填）、`PUT /admin/users/{id}/status`。

### 4.4 通用规范

- **分页**：`PageResult<T>{ records, total, page, pageSize }`（无 `list` 字段）；归一化 `page<1→1`、`pageSize<1→10`、`pageSize>100→100`；单页非分页返回 `List<T>`。结束判据：首页 / 搜索 = 本页返回条数 < pageSize；评价区 = 已加载条数 ≥ total；分页请求失败静默且不得推进页码。
- **金额**：存储与传输一律「分」，分↔元转换只在 api 层（`utils/money`），禁止页面裸算。
- **命名**：对外 JSON 一律 camelCase；跳转字段统一 `targetType/targetId/targetUrl`；UGC 配图字段统一 `images`（string[]，≤3 项 COS URL）；评价状态 `isHidden`(0/1)；Web `snake_case` 仅限 `api/adapter.ts`。
- **枚举与字典（PR-12）**：枚举 / 常量展示与后端常量表同源，唯一合规模式 = **后端下发字典 `{value, label, order}`，前端零硬编码映射**；判定同源须三端同时盘点硬编码点。
- **数据隔离**：从 `SecurityUtil.getCurrentUserId()` 取用户，禁止信任前端 userId；小程序请求超时 8s、管理端 5s；API 基地址集中 `api/config.ts`。

### 4.5 UGC 内容安检

- 覆盖评价与反馈的文本与配图；文本 `msgSecCheck` v2（scene：昵称=1、评价/反馈=2；判定以 `result.suggest` 为准）；图片 `imgSecCheck`（违规 code 87014）。
- 判定二态：`pass` 与 `review` 一律放行；`risky` 与未知 / 缺失态（fail-closed）拦截，一律 HTTP 400 不新增错误码。**无安检态落库**——内容安全检测只作提交闸门，不参与可见性。
- 准入双约束（仅写评价）：`bind_email` 非空 **且** openid 非空（`msgSecCheck` v2 必填）；失败分码 `4031` / `403`。反馈免认证；无 openid 时跳过文本安检放行（反馈不公开展示），反馈正文经服务端敏感词过滤（命中词替换后入库）。菜品信息纠错（`POST /dishes/{id}/correction`）不携带正文：`name` 字段经服务端敏感词过滤（命中 400），结构化字段不走微信文本安检。
- 配图规格：评价与反馈配图 ≤3 张、菜品纠错快照图片 ≤9 张（同一安检转存链路），`wx.compressImage` 压缩至最长边 ≤1334 且 ≤1MB，格式 jpg/jpeg/png/webp；链路 = 微信云存储中转 → 后端拉取 → 送检 → 转存 COS；单张失败提示后跳过、不中断其余。
- `access_token` 统一使用 `stable_token` 并缓存；重评（PUT）内文本重复送检、图片不重复复检（进入系统的图均已过检）。

### 4.6 评价与评分口径

- **先发后审**：无前置审核态；公开展示唯一判据 `is_hidden=0`；`/my/reviews` 与公开列表**同口径**过滤 `is_hidden=0`——被隐藏评价对作者本人亦不返回，故本人列表无隐藏标记字段。菜品下架不影响本人查看自己的评价。
- **一人一菜一评**（`uk_review_user_dish`），同时是重评覆盖更新的并发保障。
- **评分聚合**：只计入 `is_hidden=0` 的评价；`avg_rating` / `rating_count` 为异步事件维护的计数列。
- **详情页评分摘要卡**：分布 / 「N 人评分」/ 均分三者**同源同刻**（一次实时聚合），不得混用缓存列；`ratingDistribution` 由后端按 **5→1 降序**下发，排序由后端承担、端上直接透传。
- **VO 分型**：公开评价 `ReviewVO` 8 字段 / 本人视角 `MyReviewVO` 10 字段（+dishId/dishName）；端上须定义两个类型；时间字段统一 `createdAt`。
- **作者标识**：公开评价出参 `userId`，端上比对决定入口显隐；**不引入 `isMine`**（会让公开接口变半用户态）。

### 4.7 菜品域口径

- **描述四维**：`diet_type`（meat/half/veg/halal，单选）、`ingredients`（12 值，多选）、`flavor_tags`（8 值，多选）、`serve_temp`（hot/room/ice，单选）——多值两列以 **JSON 数组**存储，出参 `string[]`；机器值→中文的唯一真源 = 后端字典端点 `GET /dishes/attributes`（`field` 恒等于 VO 字段名），**三端零硬编码映射**。
- **菜品大类**：`meal_type` 单值枚举（set_meal/stir_fry/noodle/dry_pot/snack/soup_drink），1:N（一菜品恰属一大类）；筛选参数白名单校验非法 400；字典端点只含在售大类；大类与四维不得混用；判定按菜名与做法形态。
- **价格两态**：`price`（现价，已含折扣）+ `originalPrice`（原价，可空）；折扣判据 `originalPrice > price`（端上判定为经拍板例外）；禁止双源取价。
- **公开 VO 精简**：列表 `DishListItemVO` 8 字段 / 详情 `DishDetailVO` 16 字段（15 基础字段 + `ratingDistribution`；仅 `originalPrice` 可为 null；位置仅出参 `canteenName` / `stallName` 名称文本，无 `stallId`）；`viewCount` 只为热度排序服务、不出参、一直累计；`status` 不出参（公开恒在售）。
- **浏览计数（PV 口径）**：计数内聚于 `GET /dishes/{id}` 的成功响应路径——每次成功获取详情 `view_count +1` 并**写入一条访问日志**（`view_log`，append-only、游客亦记 `user_id=0`）；无幂等语义；`4001` 与请求失败不计数；`view_log` 兼作时间窗口聚合（近一个月等最热菜品）的数据基础，窗口查询端点另行拍板；`dish.view_count`（全历史累计）与窗口聚合并存不混用；IP 限频（30/分 + 300/时）为唯一防刷兜底；热度权重 `heatScoreExpr = view_count×1 + rating_count×100 + avg_rating×20`（唯一真源 = `DishMapper.xml` 的 SQL 片段）。
- **Banner**：首页顶部 16:10 多图轮播；`GET /banners` 公开出参仅 `id`/`imageUrl`（启用项按 `sort_order` 升序）；无跳转能力、无管理端录入入口（素材走种子脚本）。
- **搜索**：关键词硬匹配菜名 / 档口名 / 食堂名；搜索历史为端上本地存储（可清空 / 单条删除）；「猜你喜欢」= 随机在售菜品名 ≤6 条。

---

## 5. 数据模型

### 5.1 表基线（11 张）

`user`、`canteen`、`stall`、`dish`、`review`、`notification`、`user_feedback`、`dish_correction`、`email_verification_code`、`view_log`、`banner`。

- `user`：openid（唯一索引、可空）、username、nickname、avatar、bind_email（认证唯一判据）、status；无 role / password / unionid / verified 列。
- `dish`：归属 stall_id（外键必填）；price / original_price；四维（diet_type 单值、ingredients / flavor_tags JSON 数组、serve_temp 单值）；meal_type；view_count / avg_rating / rating_count（异步维护）；status(on/off) 为唯一运营开关；无 audit_status / reject_reason / created_by。
- `canteen` / `stall`：**筛选属性字典**，非业务实体——生命周期仅「新增（随菜品 upsert）/ 改名 / 列表查看」，无删除、无停业 / 营业时间 / 审核语义；食堂档口名不存在时后端按名 upsert 自动建档。
- `review`：唯一键 `uk_review_user_dish`；is_hidden 唯一可见性开关；images JSON 数组；无 updated_at（重评刷新 created_at）；无 sec_state。
- `user_feedback`：type 写入白名单 `issue`（我要反馈问题），存量旧值 `suggestion` / `add` / `error` / `bug` / `report` / `other` 仅管理端查询筛选、禁止新增；`issue` = content（≤1000 字，敏感词替换后入库）+ images（≤3 张）；处理 = status(pending → handled) + reply 必填 + 回执通知（`feedback_handle`），不采纳必填 reject_reason（`outcome=rejected` 派生，非物理列）；无 payload / contact / handler_id / sec_state。
- `dish_correction`：**菜品信息纠错独立表**——`dish_id`（外键）+ 七字段拆列快照（`name` / `price`（分）/ `canteen_name` / `stall_name` / `flavor_tags` JSON / `ingredients` JSON / `images` JSON ≤9 张）+ status(pending / adopted / rejected) + reply / reject_reason / handled_at + user_id（可空，游客匿名提交）；索引 `idx_correction_dish` / `idx_correction_status`；处理 = 采纳（两段式档口确认后七字段写回 `dish`，可空快照字段不覆盖既有值）/ 拒绝（reply 必填 ≤1000 + reject_reason 必填 1~200）；回执通知 `correction_handle`「菜品信息更新」，采纳 / 拒绝均投递、仅已认证提交人。
- `view_log`：**访问日志**（append-only，每次浏览 INSERT 一行；`user_id=0` 表示游客；按 `target_type + target_id + created_at` 索引支撑时间窗口聚合）；无更新语义。
- 库设计满足 BCNF；唯一反规范化 = dish 三个聚合计数列（异步事件维护）。

### 5.2 库表变更纪律

结构变更统一走 `schema.sql` 幂等段（存储过程先判存在、可重复执行）与 `seed_data.sql`，禁止直连 ALTER；涉及远程库的执行方式见 `.codebuddy/rules/docs-first-sync-and-db-direct.md`（凭据只从 `server/.env` 读取，破坏性操作先告知并核对）。新增接口须先在 schema.sql 与代码注释登记契约再实现。

---

## 6. UI 设计规范（Apple Design 风格）

### 6.1 原则与动效

- 八原则：Purpose / Agency / Responsibility / Familiarity / Flexibility / Simplicity / Craft / Delight；流体交互四要素：即时响应、1:1 直接操控、可中断、动量接力。
- **MVP 动效边界**：内容立即可见不依赖动画；不引入装饰性入场动效；弹层/弹窗开合瞬开瞬关（Web DOM 弹窗豁免：保留 220ms scale+opacity）；按压反馈仅 `:active` opacity / bg-soft 弱化、不缩放（Web 端 `scale(var(--press-scale))` 为登记豁免）；`prefers-reduced-motion` 降级。
- 列表不设加载骨架；失败态 = `RetryBlock`「加载失败 · 点击重试」；空态仅首页贡献卡与搜索无结果两处例外；长耗时操作（上传 / 提交）必须给进行时反馈，禁止静默等待。

### 6.2 视觉 Token

- **色板唯一真源 = `docs/ui/client-首页菜品浏览.md` §4.1「全站色板（暖橙黄）」**：主色填充/文字档 `#B4531A`、图形档 `#F5A623`、关键图标 `#E67E22`、浅黄 `#FFD166`、深橙 `#D35400`、页底 `#FFF8EF`、顶部渐变 `#FFE8D1 → #FFF8EF`、文字四档 `#2D1F14` / `#4A3520` / `#7F6A55` / `#B5A594`、星色 `#FBBF24`（独立语义色）。Web 端仅同步主色系（深色控制台体系与其提亮文字档保持）。
- **色值唯一事实源 = `client/src/theme/tokens.ts`**（`generated-colors.css` 为其一致快照，手工维护、禁止只改生成物）；裸 hex 例外（`<swiper>` 指示点等原生属性）须在 tokens.ts 登记。
- 圆角 / 阴影一律引用 token（卡片 `--radius-card`、弹层 `--radius-modal: 48rpx`）；材质模糊 `blur(20px) saturate(180%)`（Web 为主，小程序仅局部弹层）。

### 6.3 小程序红线（BLOCKER 级）

- **布局**：750rpx 视口，flex + 防溢出；每页须真机无横向滚动 / 无裁切；固定底栏页面滚动区必须 `padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom))`。
- **事件**：统一 `@tap`，禁止 `@click`。
- **图标**：统一 `IconSvg`（SVG 内联注册于 `ICONS` 键表，无本地 assets 目录，禁止 emoji 当图标）；兜底渲染 `empty` 中性占位键（禁止静默回退到语义图标）；dev 环境对未知键 `console.warn`；图片裂图占位一律 `empty`（仅明确为菜品的语义场景可用 `dish`）；新增语义须先登记键。
- **IconSvg 颜色**：`color` SHALL NOT 传 `var(...)`（SVG data-uri 内为字面量，传 var() 恒落近黑兜底）——须传 `COLOR_MAP['x']` 实色；组件入参类型应限定为 `(typeof COLOR_MAP)[keyof typeof COLOR_MAP]` 使编译期报错；CSS 属性中的 `var()` 不受此限。
- **按压**：小程序端禁裸 `scale(...)`（非按压强调 scale 须登记独立 token）；grep 范围 `client/` 期望 0 处（`web/` 豁免）。
- **Sheet**：底部弹层须含安全区避让、支持下拉关闭手势（向下拖拽 ~120px 或速度 >480px/s 关闭，否则回弹）；禁止仅 mask 关闭。
- **列表渲染**：禁止父组件向子组件同名 slot 分发内容（uni-app 编译 mp-weixin 具名 slot 塌陷）——列表类组件一律数据驱动渲染。
- **分区标题**：统一 `SectionTitle`，`CardSection` 内不另起标题语言。
- **审计口径**：先查本节已登记例外清单，确认未登记才计违规。
- 页面级 / 组件级细则（三态强制、表单 scroll-view、金额 api 层、负向操作弱化等）由小程序开发工程师按优先级落地；本文件仅定最高红线。

---

## 7. 业务硬原则（PR-01 ~ PR-14，长期判据）

任何「新入口 / 新维度 / 新字段」必须先过本节自检，未过不得开发；违反即缺陷。

| 编号 | 原则 |
|---|---|
| **PR-01**（硬） | 任何面向用户的筛选 / 展示维度，必须先在后台有录入入口且能真正落库，否则不得放出。 |
| **PR-02**（硬） | 同一业务口径（排序 / 分页 / 鉴权 / 错误码）的权威方固定为后端，端上只消费不覆写；文档与代码冲突以最新拍板决议为准，不得据代码反推文档。 |
| **PR-03** | 新功能准入须同时具备「入口 + 出口 + 空态 + 失败态 + 数据来源」五要素，缺一不得上线。 |
| **PR-04** | 被否决项须在「明确不做清单」留痕；恢复的唯一路径 = 重新拍板；禁止重提或变相保留。 |
| **PR-05** | 凡「对外暴露但零消费」的接口 / 函数 / 字段 / 常量 / 图标 / 类型一律删除；保留须显式登记理由（拍板预留或存量兼容）。 |
| **PR-06** | 参数非法即报错：枚举 / 标签 / 排序 / 筛选 / 金额入参必须经单一真源白名单校验，非法返回 400，绝不静默降级。 |
| **PR-07** | 字段生命周期成对处置：引入与下线一一对应（DTO / 实体 / VO / Mapper / 表单 / 端上映射 / 建表脚本）；零消费字段显式下线或登记为存量兼容。 |
| **PR-08** | 聚合与冗余计数必须声明「谁是真源、何时重算、失败如何补偿」；异步聚合失败不得只有一行告警。 |
| **PR-09** | 删除 / 合并操作必须成对定义级联范围（评价 / 足迹 / 通知），各处口径一致。 |
| **PR-10** | 产生公开可见内容的图片上传必须唯一走安检链路；无安检上传仅限已登记例外（如头像），命名自证用途。 |
| **PR-11** | 具备可点外观或可点语义的控件必须绑定真实动作；占位 / 装饰元素不得使用可点语义交付。 |
| **PR-12** | 枚举 / 常量展示与后端常量表同源，前端零硬编码；唯一合规模式 = 后端下发字典；判定同源须三端同时盘点；跨端 DTO 显式定型（平台句柄外禁止 `any`）。 |
| **PR-13** | 管理后台是「单人运营工具」：以最少点击完成运营动作为准，不引入多角色 / 权限矩阵 / 审批流 / 乐观锁等多人协作复杂度。 |
| **PR-14** | 食堂 / 档口是筛选属性字典而非业务实体：仅「新增 / 改名 / 查看」，不设停业 / 营业时间 / 审核语义 / 删除。 |

---

## 8. 协作纪律与交付门禁

- **权威与修改权**：本文件为唯一权威基础规范；仅技术负责人可修改；发现冲突提技术负责人裁定。实测证伪的方案由技术负责人提炼进红线。
- **冻结规则**：任何改动（含 AI 协作）必须先修订本文件相应口径再动代码，违反即流程违规。
- **文档同步**：改代码必须同步 `docs/`（project_spec / feature / ui / openspec 中的现行能力规格）；改文档不必同步代码（文档可先于代码描述目标形态，差异登记在各功能文档文末「与当前代码的差异」板块）；库表变更执行后回写 schema.sql / seed_data.sql。
- **上线放行 = 三项并行**：① 构建门禁（`mvn clean compile`（必须 clean）、client `vue-tsc --noEmit` + `build:mp-weixin`、web 构建三端全绿）；② 手工走查清单（用户真机与后台逐项打勾）；③ 线上接口级自动冒烟（读接口、鉴权、准入错误码）。
- **开发红线**：agent 不代跑编译 / 构建 / 真机运行；agent 不代跑存量数据 UPDATE（部署前由用户决定并执行）。
