# ITER-001 · 上线阻塞与正确性清零

- 时间：2026-09-14
- 主导：产品经理（主 agent 代行）｜参与：质量审计、Web 端清理
- 选题依据：`docs/PRODUCT_LOOP.md` §3 价值排序第 1、2 项（上线阻塞清零 + 正确性与合规）

## 目标与验收标准
1. 三端构建门禁全绿（server / client / web）
2. 契约一致性：前端调用的接口后端全部存在（无 404 风险）
3. 一致性红线：无 STALL_OWNER 残留、无收藏功能残留、学生端菜品写接口保持下线
4. §7 决议落地：评价双约束不误伤上传接口、反馈免认证不被破坏

## 执行记录
| 项 | 执行角色 | 结果 | 证据 |
|---|---|---|---|
| server 编译门禁 | 编排 | ✅ EXIT=0 | `mvn -q -DskipTests compile` |
| client 构建门禁 | 编排 | ✅ Build complete | `npm run build:mp-weixin` |
| web 构建门禁 | 编排 | ✅ EXIT=0（修复后） | `npm run build`（含 vue-tsc type-check） |
| 契约一致性比对 | 编排 | ⚠️ 发现 3 条缺失 | 见下 |
| UI 红线 grep | 编排 | ✅ 0 真实违规 | `@click` 1 处为注释、`scale(` 1 处为 `grayscale` 误报 |
| 角色/收藏残留 | 编排 | ✅ 无 | 仅 2 处说明性注释 |
| 学生端菜品写接口 | 编排 | ✅ 保持下线 | `DishController` 仅 GET + `POST /{id}/view` |

## 缺陷与修复
### 🔴→✅ Q-001 管理后台打开即请求已下线接口
- 现象：`adminUserStore` 初始化即请求 `/admin/admins`、`/admin/admins/me`；`AccountView` 内嵌「管理员账号」区块；`userStore` 仍调 `/auth/admin/login`。这三个接口在 2026-09-13「管理端去登录与角色体系」后已不存在 → 每次打开后台产生无效请求。
- 修复（零消费即下线）：
  - `web/src/api/admin.ts` → 占位（停止对外提供已下线接口）
  - `web/src/stores/adminUserStore.ts` → 移除顶层自动请求，保留只读 `myRole` 兼容布局层
  - `web/src/views/admin/AdminManageView.vue` → 占位
  - `web/src/views/system/AccountView.vue` → 移除「管理员账号」区块
  - `web/src/stores/userStore.ts` → 删除 `login()` 与导出
  - `web/src/api/user.ts` → 删除 `login()`（`/auth/admin/login`）与未用的 `post` 导入
  - `web/src/types/index.ts` → 删除 `AdminUser`；`api/adapter.ts` → 删除 `adminUserToLegacy`
- 验证：`web npm run build` EXIT=0（type-check 通过）

## 门禁结果
- 构建：server ✅ / client ✅ / web ✅
- 缺陷：🔴 1（已闭环，Q-001）/ 🟠 0 / 🔵 0
- 遗留：3 个占位文件待物理删除（删除操作需用户批准）

## 本轮结论
- 达成：目标 1~4 全部满足
- **停止条件：S2（无米下锅）** —— 代码侧可达的 P0/P1 已清零，剩余 P0 均为用户在线上环境执行的动作，继续迭代会空转
- 产品经理判断：**本轮到此停止**，转入等待用户动作；恢复后先做「Q-004 部署复测」再开新一轮

## 下一轮候选
1. Q-004 部署后复测（ADMIN_TOKEN 生效 + COS 上传指纹 + 跨域放行）
2. §7.7 上线前必做第 1 条前端侧：无 openid 账号发评价的 403 引导文案
3. §7.7 上线前必做第 2 条：反馈入口投稿类目文案「提交后由管理员审核后上架」
4. Q-007 一键转菜品（二期）；Q-008 计数口径定型

## 待用户决策（阻塞）
1. 重新部署云托管（Q-004）
2. 后台上传 31 道菜首图（Q-005）
3. 微信后台加 downloadFile 合法域名（Q-006）
4. 允许物理删除 3 个占位文件
