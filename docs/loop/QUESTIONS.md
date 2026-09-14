# 问题登记表（唯一渠道 · QUESTION REGISTRY）

> 机制见 `docs/PRODUCT_LOOP.md` §8。**本文件是「产品定型模糊 / 规则冲突 / 契约缺口」的唯一登记渠道**，对话里的结论一律不算数。
> 字段：ID | 提出角色 | 时间 | 类型 | 问题 | 证据 | 影响面 | 候选方案 | 优先级 | 决策人 | 状态 | 决策结论 | 闭环证据

## 开放中（Open）

### Q-023 · P1 · 管理端口令体系下是否还需要「操作人身份」
- 提出：ITER-003 排查（后端 #3 / 前端 #2）｜2026-09-14｜类型：T4 实现约束冲突
- 问题：`/admin/**` 改为共享口令后不经 JWT，`FeedbackAdminController.java:62` 的 `handler_id` 与操作日志 `admin_id` 可能恒为 NULL（审计链断裂）；web `userStore.adminId` 唯一赋值点 `loadProfile()` 零调用，`UserView` 的「禁止操作自己/批量自保护」成死分支。
- 候选：① 认定单人口令即单人操作，**删掉操作人字段与自保护分支**，`handler_id` 落固定标识（推荐，零维护）；② 保留身份概念，在口令校验处注入固定操作人标识（如 admin=1）；
- 影响：审计可追溯性 vs 实现复杂度｜决策人：用户 + 技术负责人｜状态：`open`

### Q-024 · P2 · 用户菜单「账号设置」入口去留
- 提出：ITER-003（前端 #7）｜2026-09-14｜类型：T1
- 问题：`AdminLayout.goAccount()` 跳 `/dashboard/account`，该路由**未注册**（点击空白）；实际学生账号管理在 `/dashboard/system?tab=account`。
- 候选：① 改跳 `/dashboard/system?tab=account` 并清理死分支（推荐）；② 删除该菜单入口
- 决策人：用户｜状态：`open`

### Q-025 · P2 · 美团式写评是否彻底不做
- 提出：ITER-003（后端 #7）｜2026-09-14｜类型：T1
- 问题：`review.tags` 列存在于线上库与 `schema.sql:152`，但实体 `Review` 无该字段（脚本与代码反向漂移）。
- 候选：① 彻底不做，删除该列（推荐，需幂等迁移脚本）；② 保留待恢复，在实体与 VO 登记该字段
- 决策人：用户｜状态：`open`

### Q-026 · P1 · 上新 / 促销 / 新晋黑马 / 猜你喜欢 / 档口菜品 板块是否保留
- 提出：ITER-003（前端 #6 / 后端 #12）｜2026-09-14｜类型：T1 需求模糊
- 问题：`stores/dish.ts` 有 16 个零消费成员，连带 `/dishes/new`、`/dishes/promotions`、`/dishes/rising`、`/dishes/hot`、`/dishes/recommend`、`?stallId=` 成「死 store + 死 api」链；后端 `promotion` 标签查询线上恒返回空（`schema.sql:119` 自认技术债）。
- 候选：① 确认不做，删除死 store/api 与后端 promotion 链路（推荐，与「零消费即下线」一致）；② 保留接口待接回页面
- 决策人：用户｜状态：`open`

### Q-027 · P2 · 分页契约：改后端还是改文档
- 提出：ITER-003（后端 #4）｜2026-09-14｜类型：T2 规则冲突
- 问题：spec §5 规定分页统一 `PageResult{records,total,page,pageSize}`，实际 `PageResult` 仅 `{list,total}`，靠前后端各自兜底才不炸。
- 候选：① 后端补 `records/page/pageSize`（保留 `list` 过渡），前端收敛兜底（推荐）；② 修订 spec 承认 `{list,total}`
- 决策人：技术负责人｜状态：`open`

### Q-028 · P2 · 注销确认色语义
- 提出：ITER-003（前端 #8）｜2026-09-14｜类型：T1
- 问题：`pages/mine/index.vue:181` 内联裸 hex `#C45549`（主色），同类另外 3 处用危险色 token `#FF3B30`。注销属破坏性操作，是否统一为危险色？
- 候选：① 统一危险色 token（推荐）；② 保留主色，仅登记常量
- 决策人：UI-UX 设计师｜状态：`open`

### Q-013 · P1 · 首版功能边界：「找吃的」之外还要哪些入口
- 提出：主 agent｜2026-09-14｜类型：T1（功能边界 / 暂不实现范围）
- 问题：§7.2 定「首版定位找吃的」，但未明确下列入口是否需要：随机推荐（今天吃什么）、榜单、分享、搜索历史、热门搜索词
- 候选：① 全部不做，仅保留搜索/筛选/热度与推荐列表（**默认建议**）；② 只做「随机推荐」一枚入口；③ 做热门搜索词
- 状态：`open` — 等用户确认默认假设

### Q-014 · P2 · 菜品数据来源与状态变化
- 提出：主 agent｜2026-09-14｜类型：T6（数据来源与状态变化）
- 问题：① 价格变更是否需要留痕（谁改的/何时）？② `serve_period`（供应时段）与 `region`（校区）是否需要你逐条维护，还是留空？③ 菜品下架（`status=off`）后，其已有评价是否仍可查看？
- 候选：① 价格不留痕（`operation_log` 已有操作日志可查，**默认建议**）；② `serve_period`/`region` 首版留空不展示；③ 下架菜品保留评价但端上不可达（**默认建议**）
- 状态：`open`

### Q-015 · P1 · 举报「48 小时」承诺的异常兜底
- 提出：主 agent｜2026-09-14｜类型：T6（异常与边界）
- 问题：§7.8 已承诺 48h 处理，但未定义：① 超时未处理的兜底（是否自动隐藏/自动回复）？② 同一学生对同一内容重复举报如何计数？
- 候选：① 超时不自动处置，仅记录超时并在后台置顶提醒（**默认建议**，与你选的「纯人工巡查」一致）；② 同人同内容重复举报去重，仅计一条（**默认建议**）
- 状态：`open`

### Q-016 · P2 · 是否需要「只读管理员」凭证
- 提出：主 agent｜2026-09-14｜类型：T1（用户角色与权限）
- 问题：当前管理端为单一口令（无角色）。若未来交给食堂方查看数据，是否需要只读口令（只能看不能改）？
- 候选：① 首版不做，单口令（**默认建议**）；② 增加第二个只读口令环境变量（小改动）
- 状态：`open`

### Q-017 · P2 · 上线验收标准以什么为准
- 提出：主 agent｜2026-09-14｜类型：T1（验收标准）
- 问题：上线放行以「三端构建通过 + 你手工走查清单（打勾归档）」为准是否足够？是否需要补充线上冒烟清单（我自动跑）？
- 候选：① 构建门禁 + 手工走查清单 + 线上冒烟三项并行（**默认建议**）；② 仅构建 + 手工走查
- 状态：`open`

### Q-018 · P2 · 现有 31 道菜的数据校对责任
- 提出：主 agent｜2026-09-14｜类型：T5（数据来源）
- 问题：现有 31 条菜品的 `description`/`tags`/`price`/`alias` 是否需要逐条校对（种子数据可能不准）？还是只补图即可？
- 候选：① 只补图，文案后续迭代（**默认建议**）；② 首版逐条校对（工作量大但数据可信）
- 状态：`open`

---

## 已关闭（Closed）

### Q-022 · P0 · 管理后台图片上传必然 401（阻塞 31 道菜传图）
- 提出：主 agent（线上实测）｜2026-09-14｜类型：T4 实现约束冲突
- 问题：`/upload/image` 不在任何放行清单中 → 落到 `anyRequest().authenticated()` 要求学生 JWT；而 web 管理后台只带 `X-Admin-Token`。实测 `POST /api/upload/image`（带正确口令）返回 401「请先登录或重新登录」→ **后台上传图片必然失败**，直接阻塞 Q-005（31 道菜首图）。
- 证据：线上实测 401；`SecurityConfig.PUBLIC_ANY_METHOD/PUBLIC_GET_PREFIXES` 均无 `/upload`；`AdminTokenFilter.shouldNotFilter` 仅匹配 `/admin/`
- 决策：把 `/upload/image`（管理端 multipart 上传）与 `/admin/**` 同源处理 —— 口令过滤器把关 + SecurityConfig 放行；学生端 `/upload/images` 保持不变仍走 JWT
- 实现：`AdminTokenFilter.shouldNotFilter` 增加 `uri.endsWith("/upload/image")`（**必须 endsWith**：`/upload/image` 是 `/upload/images` 的子串，用 contains 会误伤小程序上传链路）；`SecurityConfig` 增加 `.requestMatchers("/upload/image", "/api/upload/image").permitAll()`
- 验证：`mvn compile` EXIT=0；线上待部署后复测
- 状态：`closed` ✅（代码已修，**待用户重新部署生效**）

### Q-009 · P0 · 管理端新增菜品落 `pending` → 小程序不显示
- 提出：主 agent｜2026-09-14｜类型：T7 决议疑似被推翻
- 问题：`dish.audit_status` 建表默认 `pending`，`DishServiceImpl.addDish` 未设该字段；小程序仅展示 `approved` → 后台新录入菜品全部不可见（现有 31 条为种子脚本写入 approved，故此前未暴露）
- 证据：`SHOW COLUMNS FROM dish`（默认 pending）；`DishServiceImpl.addDish` 仅设 `status`；小程序过滤 `audit_status='approved'`
- 决策：**管理员即权威**——后台新增/编辑直接置 `approved`；审核中心保留给学生投稿链路
- 实现：`DishServiceImpl.addDish/updateDish` 置 `DishConst.AUDIT_APPROVED`；`mvn compile` EXIT=0
- 闭环证据：spec §7.8 第 1 条 + 代码 + 编译通过
- 状态：`closed` ✅

### Q-010 · P1 · 学生投稿表单形态
- 提出：主 agent｜2026-09-14｜类型：T1
- 决策：沿用现有反馈表单（自由文本 + 配图 ≤3 张），不新增结构化字段；管理员人工解析后录入
- 待办：小程序端投稿类目文案「提交后由管理员审核后上架」（§7.7 上线前必做第 2 条）
- 闭环证据：spec §7.8 第 2 条｜状态：`closed` ✅（文案随部署上线）

### Q-011 · P1 · 评价「有用」点赞是否双约束
- 提出：主 agent｜2026-09-14｜类型：T4 实现约束冲突
- 决策：点赞仅需登录（STUDENT），**不适用 §7.5 双约束**；§7.5 仅约束 `POST /reviews`
- 闭环证据：spec §7.5 准入范围收窄 + §7.8 第 3 条｜状态：`closed` ✅（零开发，现状即符合）

### Q-012 · P2 · 举报/反馈处理是否通知提交人
- 提出：主 agent｜2026-09-14｜类型：T1
- 决策：通知提交人（复用既有 `TYPE_FEEDBACK_HANDLE`）+ 对外承诺 48 小时内处理
- 待办：小程序端反馈/举报说明文案体现 48h 承诺
- 闭环证据：spec §7.8 第 4 条｜状态：`closed` ✅（通知机制已有，文案待改）

### Q-001 · P0 · 管理后台打开即请求已下线接口（404）
- 提出：质量审计（ITER-001）｜2026-09-14｜类型：T7
- 问题：`adminUserStore` 初始化即请求 `/admin/admins`、`/admin/admins/me`，`userStore` 仍调 `/auth/admin/login`；三接口在「管理端去登录与角色体系」后已不存在
- 决策：按「零消费即下线」清理
- 实现：`api/admin.ts`/`AdminManageView.vue` 改占位、`adminUserStore` 停止发请求、`AccountView` 移除管理员区块、`userStore`/`api.user` 删除 `login`、`types.AdminUser` 与 `adapter.adminUserToLegacy` 移除
- 闭环证据：`web npm run build` EXIT=0；见 ITER-001｜状态：`closed` ✅（物理删除待用户批准）

### Q-002 · P1 · 文档与提问中出现「收藏」措辞
- 提出：用户质疑｜2026-09-14｜类型：T1
- 决策：产品确认无收藏功能、不做；措辞勘误入库
- 闭环证据：spec §7.6；commit `61959c2`｜状态：`closed` ✅

### Q-003 · P0 · 无 openid 账号发 UGC 绕过机检
- 提出：主 agent｜2026-09-14｜类型：T4 + T6
- 决策：评价须 `verified=1 且 openid 非空`（403）；反馈免认证但无 openid 时落 `sec_state=review`
- 闭环证据：commit `8372caa`；spec §7.5/§7.7；`mvn compile` EXIT=0｜状态：`closed` ✅

### Q-004 · P0 · 线上 ADMIN_TOKEN 未生效，后台全量 403
- 状态：`open` — 等用户重新部署（云托管改环境变量须重启实例才注入）
- 证据：线上 `GET /api/admin/dishes` → 403「管理端未配置 ADMIN_TOKEN（fail-closed）」

### Q-005 · P0 · 31 道菜首图缺失
- 状态：`open` — 等用户后台上传（**注意**：Q-009 已修，部署后新增/编辑菜品即刻可见）

### Q-006 · P1 · 小程序 downloadFile 合法域名未含 COS 域名
- 状态：`open` — 等用户在微信后台加 `https://bjtu-food-image-1408890131.cos.ap-shanghai.myqcloud.com`

### Q-007 · P2 · 反馈「一键转菜品」何时做
- 状态：`open` — 已记为二期（spec §7.3/§7.7）

### Q-008 · P2 · 「喜欢 / 有用」计数口径未定型
- 提出：主 agent｜2026-09-14｜类型：T1
- 问题：`DishServiceImpl.java:419` 注释写「喜欢计数存储方案待架构师评估」；评价侧存在 `useful_count`/`review_useful`。菜品是否还需要独立「喜欢」计数？
- 候选：① 明确不做「喜欢」（**默认建议**，与「收藏已移除」口径一致），清理误导性注释；② 恢复「喜欢」功能（新功能，须用户拍板）
- 状态：`open`
