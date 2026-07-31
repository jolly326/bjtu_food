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

-- -------------------- 动态「有用 👍」标记（一人一票） --------------------
CREATE TABLE IF NOT EXISTS `moment_useful`
(
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id`    BIGINT   NOT NULL DEFAULT 0 COMMENT '用户ID',
    `moment_id`  BIGINT   NOT NULL DEFAULT 0 COMMENT '动态ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_useful_user_moment` (`user_id`, `moment_id`),
    KEY `idx_useful_moment` (`moment_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='动态有用标记（一人一票）';

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

-- -------------------- 升级用户反馈表（保留历史数据） --------------------
ALTER TABLE `user_feedback`
    ADD COLUMN `type`        VARCHAR(32)  NOT NULL DEFAULT 'other' COMMENT '反馈类型：suggestion(功能建议)/error(内容纠错)/other(其他)',
    ADD COLUMN `status`      VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT '处理状态：pending(待处理)/handled(已处理)',
    ADD COLUMN `reply`       VARCHAR(512) NULL    DEFAULT NULL COMMENT '管理员回复/处理说明',
    ADD COLUMN `handled_at`  DATETIME     NULL    DEFAULT NULL COMMENT '处理时间',
    ADD COLUMN `handler_id`  BIGINT       NULL    DEFAULT NULL COMMENT '处理人管理员ID';

-- 历史数据置为 other/pending，避免 NULL 歧义
UPDATE `user_feedback` SET `type` = 'other', `status` = 'pending' WHERE `type` = '' OR `type` IS NULL;

SET FOREIGN_KEY_CHECKS = 1;
