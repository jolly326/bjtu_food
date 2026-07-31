# 二期技术评审 · 架构决策与接口契约（ARCH_DECISIONS_PHASE2）

> 文档性质：技术架构师定稿。覆盖 task-06 ~ task-10（二期，含部分三期占位）。
> 配套文档：`project_spec.md`（唯一权威约束）、`tasks/ARCH_DECISIONS_PHASE1.md`（一期契约，本文为其续篇）、`tasks/PAGE_PLAN_PROPOSAL.md`（界面清单）、`tasks/task-06/07/08-*`（概要）。
> 本文所有契约 / DDL / 白名单为强制结论，供需求梳理师落成 task 文档、再派开发工程师实现。
> 强制原则（沿用一期红线）：角色仅 `STUDENT` / `ADMIN`，无 `STALL_OWNER`；金额分/元（本期均不涉及金额）；对外字段 camelCase、DB 下划线；emoji 语义唯一 ❤️=收藏、👍=有用；小程序 750rpx、动效从简。

---

## 0. 二期限定范围（Scope）

二期**交付 task-06 ~ task-10**，涉及：

| task | 主题 | 本期动作 |
| --- | --- | --- |
| task-06 | 轻量化社区（含评论，方案 B） | 新 `moment` + `moment_comment` 表 + 接口；社区四界面（广场/详情/发布/我的）；Web 审核台 `type=moment` + 动态管理/下架（W2/W5） |
| task-07 | 浏览足迹 | 新 `view_log` 表 + `GET /my/history` 系列；反哺首页「猜你喜欢」 |
| task-08 | 成就系统 | **本期彻底冻结/不占位**：用户拍板（Q1）不建积分/等级/成就，**不做占位卡、不留任何预留字段/接口**（见 §5 决策点 D9）。task-08 文件降级为冻结说明 |
| task-09 | 消息/通知中心 + 设置页 | 新 `notification` 表 + `GET /my/notifications` 系列；设置页（轻量，纯前端为主）；**新增 `DELETE /my/account` 账号注销（Q4-①）** |
| task-10 | 反馈闭环 + Web 附加（W1/W3/W4） | 升级 `feedback` 表（含举报 `related_type`/`related_id`，Q4-②）+ admin 反馈接口；Web 报表导出（CSV 文件流 + 派生指标，D-C/D3 报表口径）；操作日志 `operation_log` 表 + AOP |

**明确不交付本期（已拍板封口）**：
- 用户成长/贡献体系（Q1）：**积分 / 等级 / 成就展示 / 勋章全部不做**，task-08 彻底冻结、不占位、不留余量（见 §5 D9）。
- 关注/粉丝关系（Q5）：**不碰、不建用户关系表、不留任何余量**，社区广场「关注流」本期不做（见 §5 D3）。
- 运营置顶/话题/精选（Q2）：**轻量级社区，不设计置顶/话题/精选**，社区排序不干预，流量靠热度 + 关联动态反哺（见 §5 D10）。
-「评论 👍」：从简，本期**不引入**评论有用计数（见 §5 决策点 D1）。
- 美食清单（`/lists`）后端**已完整存在**，本期仅前端页（L4），不补任何后端接口（见 §3.7 复用结论）。
- 报表导出**不引 POI / 不返 xlsx**（D-C）：后端返回 **CSV 文件流**（零依赖）；xlsx 留作可选增强，本期不做。

---

## 1. 现状核对结论（避免重复造轮子）

| 需求方/PAGE_PLAN 声称 | 后端实际 | 结论 |
| --- | --- | --- |
| `POST /feedback` STU 接口「已存在」 | **存在** `auth/controller/FeedbackController.java`，写 `user_feedback` 表 | 接口端点存在但**实现不规范**：① 入参 `Map<String,String>` 裸接、无 `@Validated`、未校验 `type`；② 表 `user_feedback` 缺 `type`/`status`/`reply`/`handled_at` 等闭环字段。→ **保留端点路径，升级表结构 + 规范化 DTO**，不改变 `POST /feedback` 路径（前端契约稳定） |
| `/lists`、`/lists/share/{token}`「已存在」 | **存在** `list/controller/ListController.java`：`POST /lists`、`GET /lists`、`GET /lists/{id}`、`DELETE /lists/{id}`、`GET /lists/share/{token}`、`POST /lists/{id}/collect-all` | **完全复用，本期不补任何 `/lists` 后端接口**，仅前端清单页（L4）消费既有契约 |
| `/admin/audit?type=moment`、`/admin/audit/moment/{id}/approve`、`/admin/audit/moment/{id}/reject` | `content/controller/AuditController.java` 端点为 `/admin/audit/{type}/{id}/approve`（`type` 是路径变量）+ `/admin/audit/{type}/{id}/reject` | **无需新端点**，仅 `AuditService` 增加 `moment` 分支（复用 §3.x.1 状态机）。路径即 `.../moment/{id}/approve`，与 dish/stall/canteen 同款 |
| `moment` / `moment_comment` / `view_log` / `notification` / `operation_log` | **均不存在**（已用文件搜索确认无对应 entity/controller/mapper） | 需新建表 + 实体 + DTO/VO + Mapper（见 §1.2 / §2） |
| `GET /dishes/recommend` 个性化浏览记录 | spec §3.x.4 已定义「最近 N 条浏览记录」 | `view_log` 即为该个性化数据的**唯一存储**，避免双写（见 §1.2 注释） |

> 红线提醒：`FeedbackController` 现位于 `auth` 包下、用 `JdbcTemplate` 裸写。二期**不迁移包**，仅改为调用新建的 feedback 实体/Mapper（或保留 JdbcTemplate 但读规范表）；为契合分层（Controller 不直连 Mapper/JdbcTemplate），建议抽 `feedback` 模块（entity/mapper/service/controller），`POST /feedback` 端点的包以最小改动原则定（见 §5 影响面）。

---

## 2. 实体 / 表结构 DDL（phase2 迁移脚本）

> 交付形式：新建 `backend/src/main/resources/db/migration_phase2.sql`（不改动 `schema.sql` 既有表；增量 ALTER/建表在此）。
> 字段约定：DB 下划线、camelCase 由 MP 自动映射；枚举用常量类，不引新字典表；图片一律逗号分隔 URL 字符串（与 dish.images/stall.images 存储风格一致，最小代价）；时间 `DATETIME DEFAULT CURRENT_TIMESTAMP`。
> 金额：本期所有表均不涉及金额字段。

### 2.1 新建表 DDL

```sql
-- =============================================================
-- 二期迁移脚本（追加，不改动 schema.sql 既有表）
-- 来源：tasks/ARCH_DECISIONS_PHASE2.md §2
-- 角色仅 student/admin；无金额字段；图片逗号分隔 URL 字符串
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------- 社区动态 moment --------------------
CREATE TABLE IF NOT EXISTS `moment`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '动态ID',
    `user_id`       BIGINT       NOT NULL DEFAULT 0 COMMENT '发布者用户ID',
    `content`       VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '动态正文（上限建议 500 字，DB 留余量）',
    `images`        VARCHAR(2048) NULL    DEFAULT NULL COMMENT '动态图片URL列表，逗号分隔（≤9张）',
    `related_type`  VARCHAR(32)  NOT NULL DEFAULT 'none' COMMENT '关联对象类型：dish / stall / none',
    `related_id`    BIGINT       NULL    DEFAULT NULL COMMENT '关联对象ID（dish_id 或 stall_id），related_type=none 时为NULL',
    `audit_status`  VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT '审核状态：pending/approved/rejected（复用 §3.x.1）',
    `reject_reason` VARCHAR(255) NULL    DEFAULT NULL COMMENT '退回原因（rejected 时由后台填写）',
    `useful_count`  INT          NOT NULL DEFAULT 0 COMMENT '「有用 👍」标记数（一人一票，uk_useful_user_moment）',
    `comment_count` INT          NOT NULL DEFAULT 0 COMMENT '评论数（冗余计数，由 moment_comment 聚合维护）',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '下架状态：0=正常 1=管理员强制下架（区别于审核态，W5）',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_moment_user` (`user_id`),
    KEY `idx_moment_audit` (`audit_status`),
    KEY `idx_moment_related` (`related_type`, `related_id`),
    KEY `idx_moment_created` (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='社区动态';

-- -------------------- 动态评论 moment_comment --------------------
CREATE TABLE IF NOT EXISTS `moment_comment`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `moment_id`  BIGINT       NOT NULL DEFAULT 0 COMMENT '所属动态ID',
    `user_id`    BIGINT       NOT NULL DEFAULT 0 COMMENT '评论者用户ID',
    `parent_id`  BIGINT       NULL    DEFAULT NULL COMMENT '父评论ID（一层回复：NULL=顶级评论，非NULL=对某评论的回复）',
    `content`    VARCHAR(500) NOT NULL DEFAULT '' COMMENT '评论正文',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_comment_moment` (`moment_id`),
    KEY `idx_comment_parent` (`parent_id`),
    KEY `idx_comment_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='动态评论（含一层回复）';

-- -------------------- 浏览足迹 view_log --------------------
CREATE TABLE IF NOT EXISTS `view_log`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '足迹ID',
    `user_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '浏览者用户ID',
    `target_type` VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '浏览对象类型：dish / stall / canteen / moment',
    `target_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '浏览对象ID',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '浏览时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_view_user_time` (`user_id`, `created_at`),
    KEY `idx_view_target` (`target_type`, `target_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='浏览足迹（同时供猜你喜欢个性化读取，唯一存储）';

-- -------------------- 消息通知 notification --------------------
CREATE TABLE IF NOT EXISTS `notification`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    `user_id`    BIGINT       NOT NULL DEFAULT 0 COMMENT '接收用户ID',
    `type`       VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '通知类型：moment_audit / dish_audit / comment / useful / activity',
    `title`      VARCHAR(128) NOT NULL DEFAULT '' COMMENT '通知标题',
    `content`    VARCHAR(512) NOT NULL DEFAULT '' COMMENT '通知正文',
    `related_id` BIGINT       NULL    DEFAULT NULL COMMENT '关联对象ID（动态/菜品/活动ID，按 type 解释）',
    `is_read`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读：0=未读 1=已读',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_notify_user_read` (`user_id`, `is_read`),
    KEY `idx_notify_user_created` (`user_id`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='消息通知';

-- -------------------- 操作日志 operation_log --------------------
CREATE TABLE IF NOT EXISTS `operation_log`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `admin_id`    BIGINT       NOT NULL DEFAULT 0 COMMENT '操作管理员ID',
    `action`      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '动作标识：audit_approve / audit_reject / moment_hide / moment_delete / feedback_handle / ...',
    `target_type` VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '操作对象类型：moment / dish / stall / canteen / feedback / review',
    `target_id`   BIGINT       NULL    DEFAULT NULL COMMENT '操作对象ID',
    `ip`          VARCHAR(64)  NULL    DEFAULT NULL COMMENT '操作来源IP',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_op_admin_time` (`admin_id`, `created_at`),
    KEY `idx_op_target` (`target_type`, `target_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='操作日志（AOP 埋点，Web 只读查询）';
```

### 2.2 升级既有 `user_feedback` 表（反馈闭环 W1 + 社区举报 Q4-②）

> 现状表缺 `type`/`status`/`reply`/`handled_at`，无法支撑 Web 处理闭环。本期**升级既有表**（保留历史数据），并规范化 DTO。
> 注意：`POST /feedback` 端点路径不变（前端契约稳定），仅替换为规范实体/字段。
> **Q4-② 社区举报**：**复用 `user_feedback` 表**，新增 `related_type`/`related_id` 两列关联被举报动态（不新建举报表）。举报即一条 `type='report'` 的反馈记录，由 Web 反馈台（原 `GET /admin/feedbacks`）统一处理闭环。

```sql
-- -------------------- 升级用户反馈表（保留历史数据） --------------------
ALTER TABLE `user_feedback`
    ADD COLUMN `type`          VARCHAR(32)  NOT NULL DEFAULT 'other' COMMENT '反馈类型：suggestion(功能建议)/error(内容纠错)/report(社区举报)/other(其他)',
    ADD COLUMN `status`        VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT '处理状态：pending(待处理)/handled(已处理)',
    ADD COLUMN `reply`         VARCHAR(512) NULL    DEFAULT NULL COMMENT '管理员回复/处理说明',
    ADD COLUMN `handled_at`    DATETIME     NULL    DEFAULT NULL COMMENT '处理时间',
    ADD COLUMN `handler_id`    BIGINT       NULL    DEFAULT NULL COMMENT '处理人管理员ID',
    ADD COLUMN `related_type`  VARCHAR(32)  NULL    DEFAULT NULL COMMENT 'Q4-② 关联对象类型：moment(被举报动态)/none；复用 feedback 表承接举报，不新建举报表',
    ADD COLUMN `related_id`    BIGINT       NULL    DEFAULT NULL COMMENT 'Q4-② 关联对象ID（被举报 moment_id），related_type=none 时为NULL';

-- 历史数据置为 other/pending，避免 NULL 歧义
UPDATE `user_feedback` SET `type` = 'other', `status` = 'pending' WHERE `type` = '';
```

> **举报与反馈复用同一张表的红线**：`type` 枚举扩展 `report`；小程序举报入口走 `POST /feedback`（入参加 `relatedType='moment'`+`relatedId`），**不改端点路径**；Web 反馈台 `GET /admin/feedbacks?type=report` 可单独筛选举报件。

### 2.3 实体 / DTO / VO 影响面清单（后端）

**Backend 新实体（entity/）**
- `moment/entity/Moment.java`：`id`(Long)、`userId`(Long)、`content`(String)、`images`(String,逗号分隔)、`relatedType`(String)、`relatedId`(Long?)、`auditStatus`(String)、`rejectReason`(String?)、`usefulCount`(Integer)、`commentCount`(Integer)、`status`(Integer,0/1)、`createdAt`、`updatedAt`。
- `moment/entity/MomentComment.java`：`id`、`momentId`、`userId`、`parentId`(Long?)、`content`、`createdAt`、`updatedAt`。
- `history/entity/ViewLog.java`（或 `user/entity/ViewLog.java`）：`id`、`userId`、`targetType`、`targetId`、`createdAt`、`updatedAt`。
- `notify/entity/Notification.java`：`id`、`userId`、`type`、`title`、`content`、`relatedId`(Long?)、`isRead`(Integer)、`createdAt`、`updatedAt`。
- `common/entity/OperationLog.java`（AOP 埋点写入，建议放 `common` 或独立 `log` 模块）：`id`、`adminId`、`action`、`targetType`、`targetId`、`ip`、`createdAt`。
- `feedback/entity/Feedback.java`（升级）：原 `user_feedback` 映射，新增 `type`/`status`/`reply`/`handledAt`/`handlerId`/`relatedType`(String?)/`relatedId`(Long?) 字段（`relatedType`/`relatedId` 为 Q4-② 举报关联）。
- **账号注销（Q4-①）**：**不新建表**。`DELETE /my/account` 物理或逻辑删除 `user` 行，并级联清理本人 `dish`/`moment`(+`moment_comment`)/`review`/`favorite`/`view_log`/`notification`（同 `user_id`）。`user` 实体本身已存在，仅新增注销 service 方法；若选逻辑删除则 `user.status` 追加 `deleted` 枚举（需确认现有 `active/disabled` 是否兼容，见 §3.7 契约）。

**Backend 新 VO（dto/）**
- `moment/dto/MomentVO.java`（公开列表/详情）：`id`、`userId`、`userNickname`、`userAvatar`、`content`、`images`(**List<String>**，由逗号字符串解析，DB 列 `@JsonIgnore` 或仅内部)、`relatedType`、`relatedId`、`relatedName`(联表名，如菜品名/档口名，可选)、`auditStatus`、`rejectReason`(仅作者本人/Admin 可见)、`usefulCount`、`commentCount`、`status`、`createdAt`(可输出相对时间由前端算)。
  - **对外 camelCase 红线**：`images` 出参为 `List<String>`；`userNickname`/`userAvatar` 与 ReviewVO 一致；**不输出** `is_hidden` 风格隐藏字段。
  - 列表只返 `audit_status=approved` 且 `status=0`。
- `moment/dto/MomentCommentVO.java`：`id`、`momentId`、`userId`、`userNickname`、`userAvatar`、`parentId`、`replyToNickname`(父评论昵称，一层回复展示用)、`content`、`createdAt`。
- `moment/dto/MomentPublishReq.java`（STU，@Validated）：`content`(非空,@Size max 500)、`images`(List<String>?，≤9)、`relatedType`(默认 none)、`relatedId`(Long?，relatedType≠none 时必填)。
- `moment/dto/MomentUsefulResult.java`：`useful`(Boolean)、`usefulCount`(int)。
- `history/dto/ViewLogVO.java`：`id`、`targetType`、`targetId`、`targetName`、`targetImage`、`createdAt`。
- `notify/dto/NotificationVO.java`：`id`、`type`、`title`、`content`、`relatedId`、`isRead`、`createdAt`。
- `feedback/dto/FeedbackReq.java`（STU，@Validated，替代 Map）：`type`(枚举 suggestion/error/other，非空)、`content`(非空,@Size max 1000)、`contact`(String?，@Size max 128)。
- `feedback/dto/FeedbackAdminVO.java`（ADM）：`id`、`userId`、`userNickname`、`type`、`content`、`contact`、`status`、`reply`、`createdAt`、`handledAt`。
- `log/dto/OperationLogVO.java`（ADM）：`id`、`adminId`、`adminNickname`、`action`、`targetType`、`targetId`、`ip`、`createdAt`。

> 枚举值域常量类（不引新字典表）：
> - `MomentConst.AUDIT_*` / `MomentConst.RELATED_*` / `MomentConst.STATUS_*`。
> - `ViewLogConst.TARGET_*`(dish/stall/canteen/moment)。
> - `NotificationConst.TYPE_*`(moment_audit/dish_audit/comment/useful/activity)。
> - `FeedbackConst.TYPE_*`(suggestion/error/report/other) / `FeedbackConst.STATUS_*`(pending/handled)。
> - `OperationLogConst.ACTION_*`(audit_approve/audit_reject/moment_hide/moment_delete/feedback_handle/...)。

### 2.4 补充 DDL：view_log 去重唯一键（D-B）+ 注销级联清理（Q4-①）

> **D-B view_log 去重**：在既有 `migration_phase3.sql` 末尾追加唯一键 `uk_view_user_target(user_id, target_type, target_id)`；`HistoryServiceImpl.record()` 改为 upsert（`INSERT ... ON DUPLICATE KEY UPDATE created_at = NOW()`），同一对象只留最新一行。不新建表。
>
> **Q4-① 账号注销**：不新建表，注销即 `DELETE /my/account` 物理/逻辑删除 `user` 并级联清理下列同 `user_id` 数据（见 §3.7）。若选逻辑删除，需为 `user.status` 增加 `deleted` 枚举（与现有 `active/disabled` 并存）。

```sql
-- =============================================================
-- 二期补充迁移（追加到 migration_phase3.sql 末尾，幂等）
-- 来源：tasks/ARCH_DECISIONS_PHASE2.md §2.4（D-B 去重 + Q4-① 注销）
-- =============================================================

-- D-B：view_log 去重唯一键（同一 user+target 仅留一行）
DROP PROCEDURE IF EXISTS add_viewlog_uk;
DELIMITER $$
CREATE PROCEDURE add_viewlog_uk()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'view_log'
                     AND INDEX_NAME = 'uk_view_user_target') THEN
        ALTER TABLE `view_log`
            ADD UNIQUE KEY `uk_view_user_target` (`user_id`, `target_type`, `target_id`);
    END IF;
END$$
DELIMITER ;
CALL add_viewlog_uk();
DROP PROCEDURE IF EXISTS add_viewlog_uk;

-- Q4-① 账号注销级联清理：删除本人各类数据（逻辑删除则用 UPDATE user SET status='deleted'）
-- 物理删除示例（按依赖顺序，先子后父，避免 FK 冲突；当前表无 FK 约束，按业务归属清理即可）
-- DELETE FROM moment_comment WHERE moment_id IN (SELECT id FROM moment WHERE user_id = ?);
-- DELETE FROM moment        WHERE user_id = ?;
-- DELETE FROM review         WHERE user_id = ?;
-- DELETE FROM favorite       WHERE user_id = ?;
-- DELETE FROM view_log       WHERE user_id = ?;
-- DELETE FROM notification   WHERE user_id = ?;
-- DELETE FROM dish           WHERE created_by = ?;   -- 学生发布菜品归属 created_by
-- DELETE FROM user_feedback  WHERE user_id = ?;       -- 本人反馈/举报
-- DELETE FROM user           WHERE id = ?;
```


---

## 3. 接口契约总表（二期新增/扩展，权威登记）

> 鉴权列：`PUB`=公开白名单、`STU`=需 STUDENT、`ADM`=仅 ADMIN。
> 路径风格 kebab-case；分页统一 `PageResult<T>{ list, total, page, pageSize }`（与一期一致，spec §5.x 分页结构红线）。
> 下列契约须同步回 `project_spec.md §3.x.5`（条目见 §6）。

### 3.1 社区动态 moment（task-06）

| Method | Path | 鉴权 | 入参要点 | 出参要点 | 动作 |
| --- | --- | --- | --- | --- | --- |
| GET | `/moments` | PUB | `tab`(recommend/latest，默认 latest)、`dishId`?、`stallId`?、`page`、`pageSize` | `PageResult<MomentVO>`（仅 `audit_status=approved` 且 `status=0`） | 社区广场列表；`recommend` 暂等价 `latest` 按 `created_at desc`（关注流本期不做，见 §5 D3）；`dishId/stallId` 关联过滤供 task-03「关联动态」 |
| GET | `/moments/{id}` | PUB | — | `MomentVO`（含 `commentCount`；作者本人可见 `rejectReason`） | 动态详情 |
| POST | `/moments` | STU | `MomentPublishReq` | `{ id }` | 发布，`audit_status=pending`、`useful_count=0`、`comment_count=0`、`status=0`；触发 `notification` 由审核结果异步写（见 §3.5） |
| PUT | `/my/moments/{id}` | STU | `MomentPublishReq`（同发布） | void | 编辑重提：仅作者本人（`SecurityUtil.getCurrentUserId()` 校验归属）；`audit_status→pending`、`reject_reason` 清空（**复用原记录**，§3.x.1 闭环） |
| DELETE | `/my/moments/{id}` | STU | — | void | 作者删除自己动态（物理删除，连带 `moment_comment`、清理 `notification`） |
| POST | `/moments/{id}/useful` | STU | — | `{ useful: boolean, usefulCount: int }` | **切换幂等**（一人一票 `uk_useful_user_moment`）：未标记→插入+`useful_count+1` 返 `true`；已标记→删除-1 返 `false`；重复点即取消不抛错（语义对齐 `POST /reviews/{id}/useful`） |
| POST | `/moments/{id}/comments` | STU | `{ content, parentId? }` | `{ id }` | 发评论（一层回复：`parentId` 非空表示回复某评论）；`comment_count+1`；若回复他人→给被回复者发 `comment` 通知 |
| GET | `/moments/{id}/comments` | PUB | `page`、`pageSize` | `PageResult<MomentCommentVO>`（按 `created_at asc`，顶层+其回复扁平化带 `parentId`/`replyToNickname`） | 评论列表 |
| DELETE | `/my/moments/{id}/comments/{cid}` | STU | — | void | 删除自己评论（仅作者本人；作者删→`comment_count-1`，连带其子回复删除） |

> **emoji 语义红线**：动态用 👍=有用（`useful_count`），**不引入 ❤️ 喜欢到动态**（❤️ 仅收藏语义，spec §0.6）。
> **评论 👍**：本期**不做**（§5 D1），`moment_comment` 无 `useful_count` 列。
> **唯一键**：`uk_useful_user_moment(user_id, moment_id)`（新建，复刻 `uk_useful_user_review` 同构）。

### 3.2 浏览足迹 history（task-07）

| Method | Path | 鉴权 | 入参要点 | 出参要点 | 动作 |
| --- | --- | --- | --- | --- | --- |
| GET | `/my/history` | STU | `targetType`?(dish/stall/canteen/moment)、`page`、`pageSize` | `PageResult<ViewLogVO>`（按 `created_at desc`） | 我的足迹；反哺首页「猜你喜欢」读同表 |
| DELETE | `/my/history/{id}` | STU | — | void | 删单条（归属校验） |
| DELETE | `/my/history` | STU | — | void | 清空本人全部足迹 |

> **写入点**：浏览菜品详情 → `POST /dishes/{id}/view`（一期已存在，STU）现有逻辑**扩展**：在记 `dish.view_count` 同时写 `view_log(user_id, target_type='dish', target_id)`；档口/食堂/动态详情浏览同理补写 `view_log`（前端在对应详情页触发，或后端在详情查询接口内埋，建议前端显式调用统一 `POST /my/history` 写入，见 §5 D4）。
> **推荐复用**：`GET /dishes/recommend` 个性化读取即查 `view_log`（spec §3.x.4 已约定），**唯一存储**，不双写。

### 3.3 反馈 feedback（复用既有端点 + 新增 admin 闭环，W1）

| Method | Path | 鉴权 | 入参要点 | 出参要点 | 动作 |
| --- | --- | --- | --- | --- | --- |
| POST | `/feedback` | STU | `FeedbackReq{ type, content, contact?, relatedType?, relatedId? }`（**替代原 Map 裸参**，@Validated；`type='report'` 时 `relatedType`/`relatedId` 必填，见 Q4-②） | void | 写入 `user_feedback`（升级表），`status=pending`；举报(`type=report`) 落 `related_type='moment'`+`related_id` |
| GET | `/admin/feedbacks` | ADM | `status`?(pending/handled)、`type`?(suggestion/error/report/other)、`page`、`pageSize` | `PageResult<FeedbackAdminVO>`（含 `relatedType`/`relatedId`） | 反馈列表（状态/类型过滤，可单独筛选举报件） |
| PUT | `/admin/feedbacks/{id}` | ADM | `{ status?(handled), reply? }` | void | 标记处理/回复；`status→handled`、`handled_at=now`、`handler_id=当前admin`；写 `operation_log(action=feedback_handle)` |

> **复用结论**：`POST /feedback` 端点**路径不变**（前端契约稳定），仅替换实现为规范实体/DTO。admin 侧为**新增**两个接口。
> **Q4-② 举报契约**：小程序举报某动态 → `POST /feedback`（`type=report`, `relatedType='moment'`, `relatedId={momentId}`, `content=举报理由`）。后端校验 `relatedType='moment'` 时 `relatedId` 必填（@Validated 分组或 service 内校验），否则 400。不新建举报表。

### 3.4 消息通知 notification（task-09）

| Method | Path | 鉴权 | 入参要点 | 出参要点 | 动作 |
| --- | --- | --- | --- | --- | --- |
| GET | `/my/notifications` | STU | `isRead`?(0/1)、`page`、`pageSize` | `PageResult<NotificationVO>`（按 `created_at desc`） | 我的消息列表 |
| GET | `/my/notifications/unread-count` | STU | — | `{ count: int }` | 未读总数（首页红点） |
| PUT | `/my/notifications/{id}/read` | STU | — | void | 单条已读（`is_read=1`，归属校验） |
| PUT | `/my/notifications/read-all` | STU | — | void | 全部已读 |

> **触发来源**（由对应业务异步写 `notification`，不暴露给前端）：
> - 动态/菜品审核结果（approved/rejected）→ `moment_audit`/`dish_audit` 通知作者。
> - 动态被评论（尤其被回复）→ `comment` 通知被评论者。
> - 动态被 👍 → `useful` 通知作者（可选，低频防打扰，见 §5 D5）。
> - 活动上线推送 → `activity` 通知全体/关注者。

### 3.5 Web 报表导出 / 操作日志 / 动态管理（W3/W4/W5）

| Method | Path | 鉴权 | 入参要点 | 出参要点 | 动作 |
| --- | --- | --- | --- | --- | --- |
| GET | `/admin/reports/dishes/export` | ADM | `startAt?`、`endAt?` | **CSV 文件流**（`Content-Type: text/csv` + `Content-Disposition: attachment`，零依赖，D-C） | 菜品报表导出（复用 dashboard 数据 + §3.5.1 派生指标） |
| GET | `/admin/reports/reviews/export` | ADM | `startAt?`、`endAt?` | 同上 | 评价报表导出 |
| GET | `/admin/reports/users/export` | ADM | `startAt?`、`endAt?` | 同上 | 用户报表导出 |
| GET | `/admin/reports/moments/export` | ADM | `startAt?`、`endAt?` | 同上 | 动态报表导出 |
| GET | `/admin/reports/summary/export` | ADM | `startAt?`、`endAt?` | **CSV 文件流：汇总表**（UGC 审核通过率/平均审核时长/动态互动率/Top 贡献学生，见 §3.5.1 D3） | 报表汇总导出（双输出：原始表 + 汇总） |
| GET | `/admin/operation-logs` | ADM | `adminId`?、`action`?、`targetType`?、`startAt?`、`endAt?`、`page`、`pageSize` | `PageResult<OperationLogVO>` | 操作日志只读查询 |
| PUT | `/admin/moments/{id}/hide` | ADM | — | void | 强制下架 `status=1`（区别于审核态，应对已 approved 违规内容）；写 `operation_log(action=moment_hide)` |
| DELETE | `/admin/moments/{id}` | ADM | — | void | 物理删除（二次确认）；写 `operation_log(action=moment_delete)`；连带 `moment_comment`/`notification` |

> **D-C 报表导出实现路径（已拍板）**：后端**返回 CSV 文件流**（零依赖，不引 POI / Apache POI）；xlsx 留作**可选增强（本期不做）**。每个 `export` 端点返回该主题的「原始表」CSV；另提供 `/admin/reports/summary/export` 返回「汇总」CSV（含 §3.5.1 四个派生指标）。前端/web 触发下载即可，不引入导出库。
> **D3 报表口径（已拍板，按推荐方案补 4 个派生指标）**：① UGC 审核通过率 = approved 数 / (approved+rejected) 数；② 平均审核时长 = avg(审核完成时刻 − 提交 `created_at`)；③ 动态互动率 = (👍总数 + 评论总数) / 动态数；④ Top 贡献学生 = 按本人发布的 dish+moment+review 数量排序取 Top N。汇总 CSV「原始表 + 汇总」双输出。

#### 3.5.1 报表派生指标计算口径（D3，供 dashboard 与 summary 复用）

| 指标 | 计算口径 | 数据源 |
| --- | --- | --- |
| UGC 审核通过率 | `approved / (approved + rejected)`（百分比，待审 pending 不计入分母） | `dish`/`stall`/`canteen`/`moment` 的 `audit_status` |
| 平均审核时长 | `avg(审核完成时刻 − created_at)`，按审核通过或退回时刻计 | `audit_status` 变更时间点（建议 audit 完成写 `handled_at`/更新 `updated_at`，复用 `created_at` 作起点） |
| 动态互动率 | `(sum(useful_count) + sum(comment_count)) / count(moment)` | `moment.useful_count` / `moment.comment_count` |
| Top 贡献学生 | 按 `user_id` 聚合本人 `dish(created_by)`+`moment(user_id)`+`review(user_id)` 计数，降序取 Top N | 三表 `user_id`/`created_by` 归属 |

> **社区审核复用一期端点**（无需新端点，`type` 路径变量扩展）：
> - `GET /admin/audit?type=moment&status=pending`（ADM）
> - `POST /admin/audit/moment/{id}/approve`（ADM）→ `audit_status=approved` + 写 `moment_audit` 通知
> - `POST /admin/audit/moment/{id}/reject`（ADM，`rejectReason` 必填）→ `audit_status=rejected` + `reject_reason` + 写 `moment_audit` 通知

### 3.6 美食清单（复用既有 `/lists`，本期不补后端接口）

> **复用结论（核实）**：`list/controller/ListController.java` 已完整提供：
> - `POST /lists`（STU，创建，返回 `{id}`）— L4 创建清单
> - `GET /lists`（STU，我的清单）
> - `GET /lists/{id}`（STU，详情）
> - `DELETE /lists/{id}`（STU，删自己）
> - `GET /lists/share/{token}`（PUB，分享查看）
> - `POST /lists/{id}/collect-all`（STU，一键收藏）
>
> **本期动作**：**不新增任何 `/lists` 后端接口**，仅前端 `pages/lists`（L4）消费既有契约（见 §6 条目，spec §3.x.5 已登记）。如未来需后台「清单管理下架」（W6），再立项，不在本期。

### 3.7 设置页（task-09，轻量）+ 账号注销（Q4-①，本期必须实现）

> 设置页（`pages/settings`）本期不引入新后端表/接口（除下方账号注销）。

#### 3.7.1 账号注销契约（Q4-①，本期强制交付）

| Method | Path | 鉴权 | 入参要点 | 出参要点 | 动作 |
| --- | --- | --- | --- | --- | --- |
| DELETE | `/my/account` | STU | 可选 `{ confirm: boolean }`（二次确认，防误触） | void（注销成功后前端清 token 跳登录） | **物理或逻辑删除**本人账号，并**级联清理**本人 `dish`(created_by)/`moment`(+`moment_comment`)/`review`/`favorite`/`view_log`/`notification`/`user_feedback`；注销即失效当前 token（JWT 黑名单或短失效策略） |

> **Q4-① 红线**：
> - **不新建用户关系表**（与 Q5 关注封口一致）。
> - 级联清理范围严格限定本人数据，禁止误删他人；归属校验 `SecurityUtil.getCurrentUserId()`。
> - 若选逻辑删除：`user.status` 增加 `deleted` 枚举（与 `active/disabled` 并存），小程序/profile 查询需过滤 `deleted`。
> - 注销为破坏性操作，前端二次确认 + 风险提示文案（§4.9 UI）。

#### 3.7.2 其余设置项（轻量）

> - 关于/隐私政策：纯前端静态页（内容由运营维护，可后续接 CMS，本期硬编码文案）。
> - 清除缓存：纯前端（uni 缓存清理），无后端接口。
> - 通知开关：本期**不做后端订阅/推送通道**，仅前端开关 UI 占位（开关状态本地存储）；真推送（微信订阅消息）留三期（与通知体系解耦，避免 scope 蔓延）。

---

## 4. 权限与白名单更新（SecurityConfig 扩展）

### 4.1 二期新增端点鉴权归类

| 端点 | 鉴权 | 说明 |
| --- | --- | --- |
| `GET /moments`、`GET /moments/{id}`、`GET /moments/{id}/comments` | **PUB** | 社区广场/详情/评论浏览公开（后端已过滤 `audit_status=approved` 且 `status=0`） |
| `POST /moments`、`PUT /my/moments/{id}`、`DELETE /my/moments/{id}`、`POST /moments/{id}/useful`、`POST /moments/{id}/comments`、`DELETE /my/moments/{id}/comments/{cid}` | STU | 写操作需登录 |
| `GET /my/history`、`DELETE /my/history`、`DELETE /my/history/{id}` | STU | 个人足迹 |
| `POST /feedback` | STU | 沿用现状 |
| `GET /my/notifications`、`GET /my/notifications/unread-count`、`PUT /my/notifications/{id}/read`、`PUT /my/notifications/read-all` | STU | 个人消息 |
| `GET /admin/feedbacks`、`PUT /admin/feedbacks/{id}`、`GET /admin/reports/*/export`、`GET /admin/operation-logs`、`PUT /admin/moments/{id}/hide`、`DELETE /admin/moments/{id}` | ADM | 仅管理员 |
| `GET /admin/audit?type=moment` 等 | ADM | 复用一期，仅扩展 type |

### 4.2 SecurityConfig 白名单变更（append to `PUBLIC_GET_PREFIXES`）

一期白名单 `PUBLIC_GET_PREFIXES` 现有 `/dishes/**`、`/canteens/**`、`/stalls/**`、`/reviews`、`/activities/**`、`/lists/share/**`、`/images/**`。
**二期需追加**：

```java
// 二期新增（ARCH_DECISIONS_PHASE2 §4.2）
"/moments/**",   // 仅 GET 放行：/moments、/moments/{id}、/moments/{id}/comments（写操作 POST/PUT/DELETE 不在 GET 前缀内，仍须登录）
```

> **安全约束**：`/moments/**` 仅以 `HttpMethod.GET` 放行（白名单机制同 `/dishes/**`），POST/PUT/DELETE 动态写操作不在此列，仍走 `anyRequest().authenticated()` → STU。
> 其余二期 STU/ADM 端点无需改白名单（默认登录即通，ADM 由 `/admin/**` 统一管控）。
> `POST /feedback` 现状已受登录保护（Controller 取 `SecurityUtil.getCurrentUserId()`，未登录返回 401），无需额外改动。

---

## 5. 关键机制裁决（决策点）

### D1 评论是否支持回复 / 评论👍
- **评论支持一层回复**：`moment_comment.parent_id`（NULL=顶级，非NULL=回复）。列表扁平化返回，前端按 `parentId` 渲染「回复 @昵称」。理由：方案 B 已含评论，一层回复成本可控，满足「被评论提醒」需求。
- **评论 👍 不做**：`moment_comment` 无 `useful_count` 列，不引入评论点赞。理由：与菜品「有用」解耦，克制原则（§3.x.3），降低 MVP 复杂度。若三期需要再立项。

### D2 `related_type` / `related_id` 设计
- 复用 spec §3.x.2 `target_type` 思路但独立命名 `related_type`（dish/stall/none），避免与 Banner `target_type` 语义混淆；关联仅菜品/档口（社区→实体反哺），`none` 为纯自由动态。
- 菜品详情「关联动态」区 → `GET /moments?dishId=`；档口/食堂详情 → `GET /moments?stallId=`（复用同一列表端点过滤）。
- 动态详情反向 📍 chip 跳菜品/档口详情（双向导流，task-06 §2.6 核心）。

### D3 关注流是否本期做
- **不做**。用户拍板本期不做关注/粉丝（L2），故社区广场「关注」Tab **本期隐藏/不实现**，仅「推荐/最新」两 Tab（`tab=recommend` 暂等价 `latest`，为三期关注流/算法预留参数位）。不建用户关系表。

### D4 足迹写入点
- 推荐：前端在菜品/档口/食堂/动态**详情页**浏览时显式调用统一写入端点 `POST /my/history`（STU，入参 `targetType`+`targetId`）。
- 备选（不推荐）：后端在 `GET /dishes/{id}` 等详情接口内埋点——但会污染只读接口、且游客态需跳过。统一前端调用更清晰，且复用 `view_log` 唯一存储。
- 注意：`POST /dishes/{id}/view`（一期已存在，记 `view_count`）**保留**，仅在前端调用 `/view` 的同时调用 `/my/history`（或后端在 `/view` 内顺带写 `view_log`，二选一，建议前端双调用，分层更干净）。

### D5 通知触发与防打扰
- 审核结果（approved/rejected）、被评论/被回复 → **必发**通知。
- 被 👍（useful）→ **可选低频**：默认发，但若单动态被多人连赞易刷屏，建议合并/限频（如同一动态 1 分钟内多次 👍 仅 1 条）；本期可先简单每条都发，三期优化限频。决策：**先每条都发**，低成本。
- 活动推送 → 复用活动发布动作（task-09 活动上线时写 `notification type=activity`）。

### D6 报表导出返回形式（已拍板：CSV 文件流，D-C）
- **最终决策（用户拍板 + 架构师建议）**：后端**返回 CSV 文件流**，零依赖、**不引入 Apache POI**；xlsx 留作可选增强（本期不做）。
- 四个主题 `export` 端点各返回该主题「原始表」CSV；新增 `/admin/reports/summary/export` 返回「汇总」CSV（含 D3 四个派生指标）。统一 `Content-Type: text/csv` + `Content-Disposition: attachment; filename=...csv`。前端/web 触发下载，不引导出库。
- 数据复用 dashboard 聚合逻辑（`/admin/dashboard` 已存在），不重复计算。

### D7 操作日志 AOP 埋点范围
- 切面拦截 `com.bjtufood` 下标注 `@AuditLog(action=..., targetType=...)` 的 admin 写方法（审核通过/退回、动态下架/删除、反馈处理），从 `SecurityContext` 取 `adminId`、从 `HttpServletRequest` 取 `ip`，入库 `operation_log`。
- 不埋点查询类（GET），仅写操作；`operation_log` 仅供 `GET /admin/operation-logs` 只读。

### D8 社区审核与动态管理的边界
- **审核态（audit_status）**：pending→approved/rejected，走 `type=moment` 审核台（内容是否合规准入）。
- **下架态（status 0/1）**：已 approved 后仍可被管理员强制下架/删除（W5），应对已准入但后续违规。两者解耦（对齐 §3.x.1 status 与 audit_status 解耦思想）。
- 小程序只展示 `audit_status=approved` 且 `status=0`。

---

## 5.x 二期已拍板决策总登记（需求侧 Q1~Q5 + 技术侧 D-A~D-F）

> 以下 11 条为用户在讨论组最终拍板结论，全部为「已拍板」，供派工强制遵循；无待确认项。技术侧 D-A~D-F 全部按架构师建议落地。

### 需求侧

**Q1 用户成长/贡献体系（已拍板：不做）** — 积分、等级、成就展示**全部不建**；task-08 成就系统**彻底冻结/不占位**（见 §0 Scope 表、`task-08` 文件降级、D9）。小程序「我的」页不出现成就/等级入口，不留任何预留字段/接口。

**Q2 运营边界（已拍板：轻量社区，不干预排序）** — 不设计置顶/话题/精选；社区排序不干预，流量靠热度 + 关联动态反哺（见 D10）。

**Q3 数据看板/报表指标口径（已拍板：按推荐方案）** — 补 4 个派生指标：① UGC 审核通过率 ② 平均审核时长 ③ 动态互动率(=(👍+评论)/动态数) ④ Top 贡献学生；报表导出「原始表 + 汇总」双输出（见 §3.5.1 D3、D-C）。

**Q4 本期必须补充的两项（已拍板：强制交付）**
- **Q4-① 账号注销**：`DELETE /my/account`（STU），物理或逻辑删除 + 级联清理本人 `dish/moment/moment_comment/review/favorite/view_log/notification/user_feedback`；不新建用户关系表（见 §3.7.1）。
- **Q4-② 社区举报**：复用 `user_feedback` 表，给 feedback 加 `related_type`/`related_id` 两列关联被举报动态（`related_type='moment'`），**不新建举报表**（见 §2.2、§3.3）。

**Q5 关注/粉丝流（已拍板：不碰）** — 不建用户关系表，不留任何余量；社区广场无关注流（与 D3 一致，§0 封口）。

### 技术侧（全部按架构师建议）

**D-A 通知/审核结果写入解耦（已拍板）** — 用 Spring `@Async` + 有界线程池异步写 `notification`，**不引入 MQ**。审核/评论/👍 等触发点调用 `AsyncNotificationService` 异步落库，主流程不阻塞。

**D-B view_log 去重（已拍板）** — 加唯一键 `uk_view_user_target(user_id, target_type, target_id)`（追加到 `migration_phase3.sql`，见 §2.4）；`HistoryServiceImpl.record()` 改为 upsert（`INSERT ... ON DUPLICATE KEY UPDATE created_at = NOW()`），同一对象只留最新一行。

**D-C 报表导出实现路径（已拍板）** — 后端返回 **CSV 文件流（零依赖）**，**不引 POI**；xlsx 留作可选增强（见 D6、§3.5）。

**D-D 推荐/热门/广场缓存 + 推荐改 SQL 分页（已拍板）** — 应用内 **Caffeine 短 TTL 缓存（60s）+ 写失效**，覆盖「猜你喜欢 / 热门 / 广场」列表；同时把 `DishServiceImpl.recommendDishes()` 的**全表内存排序改为 SQL 分页**（必须修的性能债，见 §3.x.4 备注与 §7 影响面）。

**D-E schema 漂移根因治理（已拍板）** — 把 `check_schema` 升级为**启动时 fail-fast 校验或 CI 步骤**，防回归（见 §7 影响面）。

> 注：原 §5 D1~D8 为既有机制裁决，与 Q1~Q5 / D-A~D-F 不冲突，并行有效。

---

## 6. 一致性红线落实（二期专项）

1. **角色**：仅 `STUDENT` / `ADMIN`，全程无 `STALL_OWNER`；`/admin/**` 仅 ADMIN（403）；动态写操作 STU（401 未登录）。
2. **金额**：本期全部表/接口均不涉及金额，无分/元转换（spec §3 金额红线自然满足）。
3. **字段命名**：对外 JSON 一律 camelCase（`userNickname`/`userAvatar`/`isRead`/`targetType`/`relatedType`/`usefulCount`/`commentCount`/`createdAt` 等）；DB 下划线，MP 自动映射；禁止 snake 泄漏 VO/小程序 types（web 经 `adapter.ts`，本期 web 新增 VO 亦需保证 adapter 无 snake）。
4. **emoji 语义**：动态/评论用 👍=有用（与菜品评价「有用」统一）；❤️ 仅收藏语义，动态不引入 ❤️；💬 评论、📍 关联、➕ 发布沿用一期映射。
5. **状态码/错误码**：成功 200；参数错误 400（`@Validated` 校验 `FeedbackReq`/`MomentPublishReq` 失败）；未登录 401（白名单外访问/过期 token）；无权限 403（非 ADMIN 访问 `/admin/**`）；服务器 500。禁止非标错误码（spec §5.x）。
6. **分页结构**：列表接口统一 `PageResult<T>{ list, total, page, pageSize }`；非分页（如 `unread-count`）返回 `{ count }` 或裸值。
7. **布局/动效（小程序）**：社区四界面根容器 750rpx；动效从简（`<transition>` + CSS transition，位移≤8rpx）；Sheet 发布走 spring 0.8/0.3（spec §4.4/§0.6）。
8. **数据隔离**：所有 `/my/*` 与写操作取 `SecurityUtil.getCurrentUserId()`，禁止信任前端 userId；动态删除/编辑/评论删除校验归属。

---

## 7. 依赖拓扑与实施顺序

```
[后端底座] 建表(migration_phase2.sql + migration_phase3.sql 追加 D-B 唯一键) → 实体/DTO/VO/Mapper → 枚举常量类
   │
   ├─► moment 模块：发布/列表/详情/有用/评论 + AuditService.moment 分支 + 动态管理/下架
   ├─► history 模块：view_log + /my/history 系列（D-B upsert 去重）
   ├─► feedback 升级：表 ALTER(+举报 related_type/related_id) + FeedbackReq(含举报入参) + admin 反馈接口(可筛 report)
   ├─► notify 模块：notification 表 + **触发(D-A @Async 有界线程池)** + /my/notifications 系列
   ├─► account 注销(Q4-①)：DELETE /my/account + 级联清理本人数据（不建新表/关系表）
   ├─► log 模块：operation_log 表 + @AuditLog 切面 + /admin/operation-logs
   ├─► web 报表导出：/admin/reports/*/export（D-C CSV 文件流）+ summary 派生指标(D3)
   ├─► 性能债(D-D)：DishServiceImpl.recommendDishes() 全表内存排序 → SQL 分页 + Caffeine 60s 缓存(写失效)
   └─► 工程治理(D-E)：check_schema 升级为启动 fail-fast / CI 步骤
        │
[前端/小程序] 社区四界面(广场/详情/发布/我的，无关注流 Q5) + 首页动态入口卡 + 关联动态(task-03填充)
        + 足迹页 + 消息中心 + 设置页(含账号注销二次确认 Q4-①) + 反馈入口 Sheet(含举报) + 清单页(L4，复用/lists)
        │
[Web 后台] 审核台 type=moment(W2) + 反馈处理(W1，含举报件) + 报表导出(W3，CSV) + 操作日志(W4) + 动态管理(W5)
        │
[联调] 三端 camelCase 对齐 + 401/403 拦截 + 通知触发链路(D-A) + 足迹反哺推荐(D-D)
```

**建议派工顺序（task 文档拆分依据）**：
1. task-06 后端（moment/moment_comment + 审核分支 + 动态管理）→ 前端社区四界面 + Web 审核/管理。
2. task-07 后端（view_log + /my/history，D-B upsert）→ 前端足迹页 + 首页猜你喜欢接入（D-D 缓存）。
3. task-09（前半）后端 notify(D-A 异步) + 账号注销(Q4-①) + 前端消息中心/设置/反馈入口(含举报 Q4-②) → Web 反馈处理(W1)。
4. task-10 Web 报表导出(W3，D-C CSV + D3 派生指标) + 操作日志(W4) + 全链路联调。
5. task-08：**彻底冻结，不派工、不占位**（Q1）；关注/粉丝流不派工（Q5）。
6. 性能债与工程治理（D-D 推荐改 SQL 分页 + Caffeine、D-E fail-fast）随后端底座一并落地，不单独成 task。

---

## 8. 需同步回 project_spec.md 的条目清单（本次已执行回填，见下方 ✓）

> 本节条目已在本次修订中**同步回填至 `project_spec.md`**（详见 project_spec.md §0.4 / §3.x.4 / §3.x.5 / §5 等对应改动）。下列为落地映射：

- **✓ §0.3 小程序页面**：底部 TabBar 4 Tab；「我的」页补充足迹/消息/设置/我的动态/清单；新增社区广场/详情/发布/足迹/消息/设置/清单界面（设置页含账号注销 Q4-① 入口）。
- **✓ §0.4 实体**：新增 `moment`/`moment_comment`/`view_log`/`notification`/`operation_log`；`user_feedback` 补 `type`/`status`/`reply`/`handled_at`/`handler_id`/**`related_type`/`related_id`**（Q4-② 举报）。
- **✓ §3.x.1 审核状态机**：补充 `moment` 第四类 UGC。
- **✓ §3.x.4 猜你喜欢**：注明读取 `view_log`（task-07 唯一存储）；**补充备注 D-D**：`recommendDishes()` 全表内存排序改为 SQL 分页 + 应用内 Caffeine 60s 缓存(写失效)，覆盖猜你喜欢/热门/广场。
- **✓ §3.x.5 契约总表**：登记 `DELETE /my/account`（Q4-①，STU，级联清理）、`POST /feedback` 举报入参 `relatedType`/`relatedId`（Q4-②）、`/admin/reports/*/export`（D-C CSV）+ `/admin/reports/summary/export`（D3 派生指标）。
- **✓ §5 红线 / 工程治理**：补充 **D-E schema 漂移治理**（check_schema 升级为启动 fail-fast / CI 步骤）；**D-A 通知异步**（@Async 有界线程池，不引 MQ）作为 notify 模块实现备注。
- **✓ 封口登记**：Q1 成就彻底冻结（不占位）、Q2 轻量社区不干预排序、Q5 关注/粉丝不碰（不建关系表）—— 均在 spec 红线与 §0.4 实体段明示「无成就/等级/用户关系表」。

---

## 9. 开放待确认项（已全部拍板，本节清空）

> 截至本修订，原 §9 待确认项均已由用户拍板，无遗留阻塞项：
> - ~~报表导出返回形式~~ → **已拍板 D-C：CSV 文件流，不引 POI**（§3.5 / §5.x D-C）。
> - ~~被 👍 通知是否限频~~ → **已拍板 D5：本期每条都发，三期优化限频**（§5 D5）。
> - ~~账号注销是否本期实现~~ → **已拍板 Q4-①：本期强制 `DELETE /my/account` + 级联清理**（§3.7.1）。
> - ~~feedback 表改名~~ → **维持 `user_feedback` 表名 + ALTER 升级**（含举报两列），避免迁移成本（§2.2）。
> - ~~活动推送触达范围~~ → **已拍板：本期无关注关系，默认全体学生**（异步批量写，D-A 线程池兜底，§5 D5）。

### 9.1 仍建议三期立项的延伸（非本期）

- 成就/等级/勋章体系（Q1 明确本期不做，三期再做）。
- 关注/粉丝关系与关注流（Q5 明确本期不碰，三期若做需新建用户关系表）。
- 报表导出 xlsx 格式（D-C 留作可选增强）。
- 被 👍 通知限频合并（D5 三期优化）。
