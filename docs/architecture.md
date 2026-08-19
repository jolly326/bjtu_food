# 架构设计与快速上手指南（食在交大 bjtu_food）

> 本文档**以当前代码为准**，描述整体架构、技术栈、部署方式、前端状态管理与本地快速启动步骤。
> 供新成员快速上手。

## 1. 系统架构

```
┌─────────────┐      ┌──────────────────┐      ┌──────────────┐
│  小程序端    │      │   后端服务         │      │   MySQL 8    │
│ (client)    │ ───▶ │ (server, Spring) │ ───▶ │  bjtu_food   │
│ uni-app+Vue3│      │  context-path:/api│      └──────────────┘
└─────────────┘      │  JWT 认证          │
                     └──────────────────┘
┌─────────────┐             ▲
│  管理后台    │  ───────────┘
│ (web)       │  Vue3+Element Plus+ECharts
└─────────────┘
```

### 1.1 端
| 端 | 目录 | 技术栈 | 说明 |
|---|---|---|---|
| 小程序 | `client/` | uni-app + Vue3 + TS + Pinia | 学生端（15 页） |
| 后端 | `server/` | Spring Boot + Java + MyBatis-Plus + JWT | REST API（context-path=/api） |
| 管理后台 | `web/` | Vue3 + Vite + TS + Element Plus + ECharts | 仅 ADMIN |

### 1.2 后端分层（包结构）
```
com.bjtufood/
├── auth/        # 认证：微信登录/邮箱认证/JWT/Security
├── dish/        # 菜品：列表/详情/发布/评分聚合/统计
├── review/      # 评价 + 评分聚合事件
├── moment/      # 社区动态 + 评论
├── canteen/     # 食堂/档口
├── content/     # category 品类 / broadcast 广播
├── activity/    # 最新活动（入口展示，点击提示「功能暂未实现」）
├── apply/       # 实体贡献申请
├── feedback/    # 用户反馈
├── notify/      # 消息通知
├── history/     # 浏览足迹（view_log）
├── upload/      # 图片上传
└── common/      # Result/异常/工具/JWT 切面/操作日志
```

## 2. 认证与安全模型

### 2.1 微信登录（游客态）
- `POST /auth/wechat-login`：`code` → 微信 code2Session → openid 唯一取号
- 新 openid 自动建号（`username=wx_+openid尾16位`，`verified=0`）
- token 长期有效（不设超时），注销/禁用走 `TokenBlacklist`

### 2.2 邮箱认证（解锁写操作）
- `POST /auth/email-code` → 发 `@bjtu.edu.cn` 验证码（60s 限频、6 位、10 分钟有效）
- `POST /auth/verify-email` → 校验验证码、绑定邮箱、`verified→1`
- 写操作接口用 `@RequireVerified` 切面（未认证抛 `4031`）

### 2.3 角色与权限
- `student` / `admin` / `super_admin`（管理管理员）
- Security：URL 白名单 + `/admin/**` 规则 + 方法级 `@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")` 纵深防御

### 2.4 安全加固（已落实）
- JWT 密钥从环境变量注入，启动 fail-fast 拒绝弱密钥
- 上传：扩展名白名单 + magic number + UUID 重命名 + 失败清理 + 缩略图白底
- 敏感信息：VO 不返回 openid；updateProfile 仅更新昵称/头像；selectList 投影必要列
- 分页上限统一 `PageUtil.normalize`

## 3. 部署（微信云托管）

### 3.1 环境信息
| 项 | 值 |
|---|---|
| 云托管环境 | `prod-d7g2z0sge0919e273` |
| 服务名 | `bjtu-food` |
| 小程序访问 | `wx.cloud.callContainer`（`X-WX-SERVICE: bjtu-food`），`context-path=/api` |
| 数据库 | MySQL 8（`bjtu_food`） |

### 3.2 必需环境变量（云托管/生产）
| 变量 | 说明 |
|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | 数据库账号 |
| `SPRING_MAIL_USERNAME` / `PASSWORD` | 网易 163 SMTP 邮箱与授权码 |
| `JWT_SECRET` | ≥32 字节强随机密钥（**禁止默认值**） |
| `WECHAT_APPID` / `WECHAT_SECRET` | 微信小程序凭据 |
| `APP_PUBLIC_BASE_URL` | 图片完整 URL 前缀 |
| `CORS_ALLOWED_ORIGINS` | 管理后台浏览器源（白名单） |

> `spring-dotenv`：本地读 `server/.env`；云托管读同名环境变量。仓库不保留任何明文凭据。

## 4. 本地快速启动

### 4.1 后端（server）
```bash
cd server
# 1. 复制环境变量模板并填真实值
cp .env.example .env
# 2. 建库建表（MySQL 已启动）
mysql -u root -p < src/main/resources/db/schema.sql
# 可选：导入示例数据
mysql -u root -p bjtu_food < src/main/resources/db/seed_data.sql
# 3. 启动
mvn spring-boot:run
# 服务 http://localhost:8080/api
```

### 4.2 小程序（client）
```bash
cd client
npm install
npm run dev:mp-weixin   # 微信开发者工具导入 dist/dev/mp-weixin
# 或 npm run dev:h5
```
- 本地联调：`VITE_API_BASE_URL=http://127.0.0.1:8080/api`
- 真机预览：`VITE_API_BASE_URL=http://<局域网IP>:8080/api`

### 4.3 管理后台（web）
```bash
cd web
npm install
npm run dev   # http://localhost:5173
```
- 需 ADMIN 角色登录（后端 `DataInitializer` 仅在 dev profile 下创建 admin，且不打印口令）

## 5. 前端状态管理（Pinia store）

| Store | 职责 |
|---|---|
| `user` | 登录态、token、profile；`forceLogout` 联动重置各 store |
| `dish` | 菜品列表/详情/筛选/猜你喜欢/评价；竞态守卫（filterFetchSeq） |
| `theme` | 深色模式（手动/跟随系统） |
| `location` | 定位/距离计算 |
| `notify` | 未读红点（`reset` 供登出联动） |
| `review`/`moment` | 评价/动态状态 |

### 5.1 登录态一致性
- `forceLogout` 会联动 `dishStore.resetUserScopedData` + `notifyStore.reset`，避免换用户串数据
- `http.ts` 401 触发静默登录重试；403/4031 分级提示；`handleUnauthorized` 并发去重

## 6. 关键设计决策
1. **评分聚合异步化**：`RatingUpdateListener` 用 `@Async("taskExecutor")` AFTER_COMMIT 重算，不阻塞提交
2. **浏览足迹去重 upsert**：`recordDishView` 存在则更新、不存在则插入，支撑猜你喜欢
3. **tags 精确匹配**：用 `FIND_IN_SET` 替代 `LIKE '%tag%'`，消除子串误匹配（tags 值域固定，未拆表）
4. **分页统一**：`PageUtil.normalize` 上限约束 + `IPage` 返回
5. **activity 接入**：2026-08-19 首页万能区与「我的」页活动入口均恢复展示，但点击提示「功能暂未实现」（不跳转活动页，独立页与 `/activities` 接口保留待开放）

## 7. 已知技术债（见 api-design.md §9）
- 4031 非标码需 spec 豁免登记
- 通知接口 verified 口径待统一
- 验证码 IP 维度限频待补
- `<PressCard>` 按压组件待抽取
- `BroadcastAdminController` 校验待补
