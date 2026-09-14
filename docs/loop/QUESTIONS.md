# 问题登记表（唯一渠道 · QUESTION REGISTRY）

> 机制见 `docs/PRODUCT_LOOP.md` §8。**本文件是「产品定型模糊 / 规则冲突 / 契约缺口」的唯一登记渠道**，对话里的结论一律不算数。
> 字段：ID | 提出角色 | 时间 | 类型 | 问题 | 证据 | 影响面 | 候选方案 | 优先级 | 决策人 | 状态 | 决策结论 | 闭环证据

## 开放中（Open）

### Q-004 · P0 · 线上 ADMIN_TOKEN 未生效，管理后台全量 403
- 提出角色：主 agent（编排）｜时间：2026-09-14｜类型：T5 环境/数据缺口
- 问题：云托管已配置变量后未重新部署，线上 `GET /api/admin/dishes` 返回 403「管理端未配置 ADMIN_TOKEN，已拒绝访问（fail-closed）」。
- 证据：线上实测返回体（2026-09-14）；`AdminTokenFilter` fail-closed 设计。
- 影响面：web 管理后台全部功能不可用（含你自行录入 31 道菜）
- 候选方案：① 控制台确认变量已保存后**重新部署**（推荐）；② 检查变量名拼写是否恰为 `ADMIN_TOKEN`；③ 检查是否配在了错误环境/服务。
- 优先级：P0｜决策人：用户（运维动作）｜状态：`open` — 等用户重新部署后复测

### Q-005 · P0 · 31 道菜首图缺失，小程序端不展示
- 提出角色：主 agent｜时间：2026-09-14｜类型：T5
- 问题：`dish` 31 条中首图（`images` 首项）多数缺失；小程序展示依赖首图。
- 影响面：上线后首页瀑布流大面积空图
- 候选方案：① 用户在后台上传实拍图（已确认由用户自行完成，推荐）；② 先用统一占位图兜底（体验差，不建议）
- 优先级：P0｜决策人：用户｜状态：`open` — 等用户后台上传

### Q-006 · P1 · 小程序 downloadFile 合法域名未含 COS 域名
- 提出角色：主 agent｜时间：2026-09-14｜类型：T5
- 问题：图片为 COS 绝对地址，正式版小程序需在 downloadFile 合法域名加入 `https://bjtu-food-image-1408890131.cos.ap-shanghai.myqcloud.com`，否则图片全部白块。
- 优先级：P1（开发版勾选"不校验域名"可绕过，正式版必炸）｜决策人：用户｜状态：`open` — 等用户在微信后台配置

### Q-007 · P2 · 反馈「一键转菜品」何时做
- 提出角色：主 agent｜时间：2026-09-14｜类型：T1
- 问题：§7.3 定「投稿合并进 feedback」，首版由管理员手动搬运；二期是否做「一键转菜品」（自动带出名称/图/档口）？
- 候选方案：① 二期做（推荐，先上线）；② 首版做（推迟上线）；③ 永久手工（不一定需要）
- 优先级：P2｜决策人：用户｜状态：`open` — 已在 §7.7 记为二期候选

### Q-008 · P2 · 「喜欢 / 有用」计数口径未定型
- 提出角色：主 agent｜时间：2026-09-14｜类型：T1 需求模糊
- 问题：`DishServiceImpl.java:419` 注释写「喜欢计数存储方案待架构师评估」；评价侧存在 `useful_count`（有用）、`review_useful` 表。菜品是否还需要独立「喜欢」计数？若无，注释应清理，避免后来者以为有待办功能。
- 证据：`server/.../DishServiceImpl.java:419`；`review` 表 `useful_count` 字段
- 候选方案：① 明确不做「喜欢」（推荐，与「收藏已移除」一致），清理注释；② 恢复「喜欢」功能（需新增表/接口，属新功能，须用户拍板）
- 优先级：P2｜决策人：用户（若涉及新功能）/ 技术负责人（若仅清理注释）｜状态：`open`

## 已关闭（Closed）

### Q-001 · P0 · 管理后台打开即请求已下线接口（404）
- 提出角色：质量审计（ITER-001）｜时间：2026-09-14｜类型：T7 决议疑似被推翻
- 问题：`adminUserStore` 在 store 初始化即请求 `/admin/admins`、`/admin/admins/me`，`AccountView` 还内嵌「管理员账号」区块，而管理端已于 2026-09-13 去登录与角色体系，后端无这些接口。
- 证据：契约比对缺失项 `/admin/admins`、`/admin/admins/me`、`/auth/admin/login`；`stores/adminUserStore.ts` 顶层裸发请求
- 决策：按「零消费即下线」清理（编排 agent 代行，P0 未决先行）
- 实现：`api/admin.ts` 与 `AdminManageView.vue` 改占位、`adminUserStore` 停止发请求、`AccountView` 移除管理员区块、`userStore.userApi.login` 删除、`types/AdminUser` 与 `adapter.adminUserToLegacy` 移除
- 闭环证据：`web npm run build` EXIT=0（type-check 通过）；见 ITER-001
- 状态：`closed` ✅（物理删除占位文件待用户批准后执行）

### Q-002 · P1 · 文档与提问中出现「收藏」措辞，疑似功能存在
- 提出角色：用户质疑｜时间：2026-09-14｜类型：T1
- 问题：用户在需求确认时质疑「怎么还有收藏？我早说了没有这个功能」。
- 证据：三端代码零功能残留，仅 3 处说明性注释；spec 已载「收藏全量移除」
- 决策：产品确认**无收藏功能且不做**；提问用词系失误，已勘误入库（spec §7.6）
- 闭环证据：`docs/project_spec.md` §7.6；commit `61959c2`
- 状态：`closed` ✅

### Q-003 · P0 · 无 openid 账号发 UGC 绕过内容机检
- 提出角色：主 agent（审计）｜时间：2026-09-14｜类型：T4 实现约束冲突 + T6 边界未定
- 问题：`msgSecCheck v2` 必填 openid，而邮箱验证码登录账号无 openid，机检被"跳过放行"；若只强制邮箱认证，恰好放行这批账号。
- 证据：`ReviewServiceImpl`（openid 为 NULL 跳过机审）；登录方式仅 `wechat-login` 与 `email-code`
- 决策：**评价**须 `verified=1 且 openid 非空`（403）；**反馈/投稿/举报免认证**但无 openid 时落库 `sec_state=review` 进人工队列
- 实现：commit `8372caa`
- 闭环证据：`mvn compile` EXIT=0；spec §7.5/§7.7
- 状态：`closed` ✅
