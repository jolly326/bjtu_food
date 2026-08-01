-- =============================================================
-- 食在交大 数据库建表脚本（MySQL 8）
-- =============================================================
-- 说明：
--   1. 角色仅两种：student（学生）/ admin（管理员）。user.role 默认值 'student'。
--   2. 金额类字段（dish.price）以「分」为单位存储（如 12.00 元 = 1200）。
--   3. 图片/多图类字段使用 JSON 字符串存储（如 ["url1","url2"]）。
--   4. 审核字段 audit_status（pending/approved/rejected）、reject_reason、created_by
--      用于 UGC 内容（dish / stall / canteen）的审核流；后台录入默认 approved。
--
-- 可选迁移（仅当从旧库升级、已存在 user 表且 role 存 'user' 时执行；
-- 本次为干净重做，主路径是新建库，无需执行此句）：
--   UPDATE user SET role='student' WHERE role='user';
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------- 用户 --------------------
CREATE TABLE IF NOT EXISTS `user`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '学号/工号（登录用，唯一）',
    `email`        VARCHAR(128) NOT NULL DEFAULT '' COMMENT '校园邮箱',
    `password`     VARCHAR(128) NULL     DEFAULT NULL COMMENT '密码哈希（验证码登录可为空）',
    `nickname`     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '昵称',
    `avatar`       VARCHAR(512) NULL     DEFAULT NULL COMMENT '头像URL',
    `role`         VARCHAR(32)  NOT NULL DEFAULT 'student' COMMENT '角色：student / admin',
    `status`       VARCHAR(32)  NOT NULL DEFAULT 'active' COMMENT '状态：active / disabled',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `last_login_at` DATETIME     NULL     DEFAULT NULL COMMENT '最近登录时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    UNIQUE KEY `uk_user_email` (`email`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='用户';

-- -------------------- 食堂 --------------------
CREATE TABLE IF NOT EXISTS `canteen`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '食堂ID',
    `name`          VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '食堂名称',
    `images`        VARCHAR(1024) NULL    DEFAULT NULL COMMENT '食堂图片URL列表JSON',
    `location`      VARCHAR(128) NULL    DEFAULT NULL COMMENT '食堂位置',
    `description`   VARCHAR(512) NULL    DEFAULT NULL COMMENT '食堂描述',
    `status`        VARCHAR(32)  NOT NULL DEFAULT 'open' COMMENT '状态：open / closed',
    `sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '排序权重（越小越靠前）',
    `audit_status`  VARCHAR(32)  NOT NULL DEFAULT 'approved' COMMENT '审核状态：pending/approved/rejected',
    `reject_reason` VARCHAR(255) NULL    DEFAULT NULL COMMENT '退回原因（rejected 时填写）',
    `created_by`    BIGINT       NULL    DEFAULT NULL COMMENT '提交人用户ID',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='食堂';

-- -------------------- 档口 --------------------
CREATE TABLE IF NOT EXISTS `stall`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '档口ID',
    `canteen_id`    BIGINT       NOT NULL DEFAULT 0 COMMENT '所属食堂ID',
    `name`          VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '档口名称',
    `images`        VARCHAR(1024) NULL    DEFAULT NULL COMMENT '档口多图JSON',
    `location`      VARCHAR(128) NULL    DEFAULT NULL COMMENT '档口位置',
    `description`   VARCHAR(512) NULL    DEFAULT NULL COMMENT '档口描述',
    `avg_rating`    DECIMAL(3, 2) NULL    DEFAULT NULL COMMENT '平均评分',
    `sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '排序权重',
    `status`        VARCHAR(32)  NOT NULL DEFAULT 'open' COMMENT '状态：open / closed',
    `audit_status`  VARCHAR(32)  NOT NULL DEFAULT 'approved' COMMENT '审核状态：pending/approved/rejected',
    `reject_reason` VARCHAR(255) NULL    DEFAULT NULL COMMENT '退回原因（rejected 时填写）',
    `created_by`    BIGINT       NULL    DEFAULT NULL COMMENT '提交人用户ID',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_stall_canteen` (`canteen_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='档口';

-- -------------------- 菜品 --------------------
CREATE TABLE IF NOT EXISTS `dish`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜品ID',
    `stall_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '所属档口ID',
    `name`          VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '菜品名称',
    `price`         INT          NOT NULL DEFAULT 0 COMMENT '价格（单位：分）',
    `description`   VARCHAR(512) NULL    DEFAULT NULL COMMENT '菜品描述',
    `images`        VARCHAR(1024) NULL    DEFAULT NULL COMMENT '菜品多图JSON',
    `tags`          VARCHAR(128) NULL    DEFAULT NULL COMMENT '标签，逗号分隔（recommended/signature）',
    `status`        VARCHAR(32)  NOT NULL DEFAULT 'on' COMMENT '上架状态：on / off',
    `audit_status`  VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT '审核状态：pending/approved/rejected',
    `reject_reason` VARCHAR(255) NULL    DEFAULT NULL COMMENT '退回原因（rejected 时填写）',
    `created_by`    BIGINT       NULL    DEFAULT NULL COMMENT '提交人用户ID',
    `view_count`    INT          NOT NULL DEFAULT 0 COMMENT '浏览量',
    `favorite_count` INT         NOT NULL DEFAULT 0 COMMENT '收藏量',
    `avg_rating`    DECIMAL(3, 2) NULL    DEFAULT NULL COMMENT '平均评分',
    `rating_count`  INT          NOT NULL DEFAULT 0 COMMENT '评价数',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_dish_stall` (`stall_id`),
    KEY `idx_dish_audit` (`audit_status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='菜品';

-- -------------------- 评价 --------------------
CREATE TABLE IF NOT EXISTS `review`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评价ID',
    `user_id`    BIGINT       NOT NULL DEFAULT 0 COMMENT '评价者用户ID',
    `dish_id`    BIGINT       NOT NULL DEFAULT 0 COMMENT '被评价菜品ID',
    `rating`     INT          NOT NULL DEFAULT 0 COMMENT '评分（1-5星）',
    `content`    VARCHAR(512) NULL    DEFAULT NULL COMMENT '评价内容',
    `images`     VARCHAR(1024) NULL    DEFAULT NULL COMMENT '评价图片URL数组JSON',
    `is_hidden`  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否隐藏（0=正常, 1=管理员隐藏）',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_review_dish` (`dish_id`),
    KEY `idx_review_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='评价';

-- -------------------- 评价「有用」 --------------------
CREATE TABLE IF NOT EXISTS `review_useful`
(
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `user_id`    BIGINT   NOT NULL DEFAULT 0 COMMENT '用户ID',
    `review_id`  BIGINT   NOT NULL DEFAULT 0 COMMENT '评价ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_useful_user_review` (`user_id`, `review_id`),
    KEY `idx_useful_review` (`review_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='评价有用标记';

-- -------------------- 收藏（本期整体移除，见 task-12.12） --------------------
-- /favorites 端点与 favorite 表本期彻底删除；喜欢(❤️)语义保留，存储方案待架构师评估。

-- -------------------- 消息通知（账号注销级联清理依赖，A.15） --------------------
CREATE TABLE IF NOT EXISTS `notification`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    `user_id`    BIGINT       NOT NULL DEFAULT 0 COMMENT '接收用户ID',
    `type`       VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '通知类型：moment_audit/dish_audit/comment/useful/activity',
    `title`      VARCHAR(128) NOT NULL DEFAULT '' COMMENT '通知标题',
    `content`    VARCHAR(512) NULL     DEFAULT NULL COMMENT '通知正文',
    `related_id` BIGINT       NULL     DEFAULT NULL COMMENT '关联对象ID（动态/菜品/活动ID，按 type 解释）',
    `is_read`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读：0=未读 1=已读',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_notification_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='消息通知';

-- -------------------- 轮播图 --------------------
CREATE TABLE IF NOT EXISTS `banner`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '轮播图ID',
    `title`      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '标题',
    `subtitle`   VARCHAR(128) NULL    DEFAULT NULL COMMENT '副标题',
    `images`     VARCHAR(1024) NULL    DEFAULT NULL COMMENT '背景图片URL列表JSON',
    `type`       VARCHAR(32)  NOT NULL DEFAULT 'dish' COMMENT '跳转类型（历史字段）：dish / url',
    `target_type` VARCHAR(32) NOT NULL DEFAULT 'DISH' COMMENT '跳转类型枚举：DISH/URL/NONE（ACTIVITY 已废弃，活动统一经 Banner URL 外链承载，见 task-12.10）',
    `target_id`  BIGINT       NULL    DEFAULT NULL COMMENT '跳转目标ID（target_type=DISH/ACTIVITY）',
    `target_url` VARCHAR(512) NULL    DEFAULT NULL COMMENT '跳转目标URL（target_type=URL）',
    `canteen_id` BIGINT       NULL    DEFAULT NULL COMMENT '关联食堂ID',
    `sort_order` INT          NOT NULL DEFAULT 0 COMMENT '排序权重',
    `status`     VARCHAR(32)  NOT NULL DEFAULT 'enabled' COMMENT '状态：enabled / disabled',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='轮播图';

-- -------------------- 菜品分类（find 宫格，A.17） --------------------
CREATE TABLE IF NOT EXISTS `category`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `name`       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '分类名称（如 早餐/午餐/晚餐/夜宵/面食/米饭/麻辣/清淡）',
    `sort_order` INT          NOT NULL DEFAULT 0 COMMENT '排序权重（越小越靠前，对应 find 宫格顺序）',
    `status`     VARCHAR(32)  NOT NULL DEFAULT 'enabled' COMMENT '状态：enabled / disabled',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category_status_sort` (`status`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='菜品分类（find 宫格）';

-- -------------------- 首页广播通知条（A.14） --------------------
CREATE TABLE IF NOT EXISTS `broadcast`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '广播ID',
    `title`          VARCHAR(128) NOT NULL DEFAULT '' COMMENT '广播标题',
    `content`        VARCHAR(512) NOT NULL DEFAULT '' COMMENT '广播正文（首页 ticker 展示文本）',
    `broadcast_type` VARCHAR(32)  NOT NULL DEFAULT 'NOTICE' COMMENT '广播类型：NOTICE/ACTIVITY/DISH/URL/NONE（首页按类型分发跳转）',
    `target_id`      BIGINT       NULL     DEFAULT NULL COMMENT '跳转目标ID（broadcast_type=DISH 时填菜品ID）',
    `target_url`     VARCHAR(512) NULL     DEFAULT NULL COMMENT '跳转目标URL（broadcast_type=URL 时填外链）',
    `sort_order`     INT          NOT NULL DEFAULT 0 COMMENT '排序权重（越小越靠前）',
    `status`         VARCHAR(32)  NOT NULL DEFAULT 'enabled' COMMENT '状态：enabled / disabled',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_broadcast_status_sort` (`status`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='首页广播通知条';

-- -------------------- 美食清单 --------------------
CREATE TABLE IF NOT EXISTS `item_list`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '清单ID',
    `user_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '创建者用户ID',
    `name`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '清单名称',
    `description` VARCHAR(512) NULL    DEFAULT NULL COMMENT '清单描述',
    `share_token` VARCHAR(64)  NULL    DEFAULT NULL COMMENT '分享token',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_list_token` (`share_token`),
    KEY `idx_list_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='美食清单';

-- -------------------- 清单项 --------------------
CREATE TABLE IF NOT EXISTS `list_item`
(
    `id`      BIGINT NOT NULL AUTO_INCREMENT COMMENT '清单项ID',
    `list_id` BIGINT NOT NULL DEFAULT 0 COMMENT '所属清单ID',
    `dish_id` BIGINT NOT NULL DEFAULT 0 COMMENT '菜品ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_list_item` (`list_id`, `dish_id`),
    KEY `idx_item_dish` (`dish_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='清单项';

-- -------------------- 用户反馈 --------------------
CREATE TABLE IF NOT EXISTS `user_feedback`
(
    `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '反馈ID',
    `user_id`      BIGINT   NOT NULL DEFAULT 0 COMMENT '用户ID',
    `type`         VARCHAR(32) NOT NULL DEFAULT 'suggestion' COMMENT '反馈类型：suggestion/error/other/report',
    `content`      VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '反馈内容',
    `contact`      VARCHAR(128)  NULL    DEFAULT NULL COMMENT '联系方式',
    `status`       VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '处理状态：pending/handled',
    `reply`        VARCHAR(1024) NULL    DEFAULT NULL COMMENT '管理员回复',
    `related_type` VARCHAR(32)   NULL    DEFAULT NULL COMMENT '关联类型（举报场景）：moment；其他为 null（task-12.7）',
    `related_id`   BIGINT        NULL    DEFAULT NULL COMMENT '关联对象ID（举报场景：动态ID）；其他为 null（task-12.7）',
    `handled_at`   DATETIME      NULL    DEFAULT NULL COMMENT '处理时间',
    `handler_id`   BIGINT        NULL    DEFAULT NULL COMMENT '处理人管理员ID',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_feedback_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='用户反馈';

-- -------------------- 活动（本期整体移除，并入 Banner） --------------------
-- 活动模块本期整体移除（后端 activity 模块/路由/表一并清理），活动统一经 Banner 触达。
-- 详见 project_spec.md §0.3.1 / §3.x.6.5 / tasks/task-12-miniapp-web-scope.md task-12.10。

-- =============================================================
-- 一期扩展字段（追加，不改动既有列）
-- 来源：tasks/ARCH_DECISIONS_PHASE1.md §1.2
-- =============================================================

-- 档口：楼层 / 窗口号 / 营业时间
ALTER TABLE `stall`
    ADD COLUMN `floor`          VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '楼层（如 1F/2F）',
    ADD COLUMN `window_no`      VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '窗口号',
    ADD COLUMN `business_hours` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '营业时间，如 10:00-20:00';

-- 菜品：辣度 / 分量 / 供应时段 / 是否限量
ALTER TABLE `dish`
    ADD COLUMN `spice_level` TINYINT NOT NULL DEFAULT 0 COMMENT '辣度枚举：0=不辣 1=微辣 2=中辣 3=重辣',
    ADD COLUMN `portion`     TINYINT NOT NULL DEFAULT 0 COMMENT '分量枚举：0=小 1=中 2=大',
    ADD COLUMN `serve_period` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '供应时段 tag，逗号分隔：breakfast/lunch/dinner/midnight',
    ADD COLUMN `limited`     TINYINT NOT NULL DEFAULT 0 COMMENT '是否限量（0=否 1=是）';

-- 评价：有用计数（冗余列，由 review_useful 聚合维护）
ALTER TABLE `review`
    ADD COLUMN `useful_count` INT NOT NULL DEFAULT 0 COMMENT '「有用」标记数（一人一票，uk_useful_user_review）';

-- 动态评论：👍 有用计数（task-12.4）
ALTER TABLE `moment_comment`
    ADD COLUMN `useful_count` INT NOT NULL DEFAULT 0 COMMENT '👍 有用计数（一人一票，uk_useful_user_comment）';

-- 动态评论「有用」关系表（task-12.4）
CREATE TABLE IF NOT EXISTS `moment_comment_useful`
(
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '标记ID',
    `user_id`    BIGINT   NOT NULL DEFAULT 0 COMMENT '用户ID',
    `comment_id` BIGINT   NOT NULL DEFAULT 0 COMMENT '评论ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_useful_user_comment` (`user_id`, `comment_id`),
    KEY `idx_useful_comment` (`comment_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='动态评论有用标记';


-- 菜品：折扣价（task-12.9）
ALTER TABLE `dish`
    ADD COLUMN `original_price` INT NOT NULL DEFAULT 0 COMMENT '原价（单位：分，折扣前）；promo_price 非空视为有折扣',
    ADD COLUMN `promo_price`   INT NULL     DEFAULT NULL COMMENT '促销价（单位：分，可空；非空视为有折扣）';

-- -------------------- 实体贡献统一申请（task-12.1） --------------------
CREATE TABLE IF NOT EXISTS `apply_action`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '申请ID',
    `applicant_id` BIGINT       NOT NULL DEFAULT 0 COMMENT '申请人用户ID（学生）',
    `entity_type`  VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '实体类型：DISH/STALL/CANTEEN',
    `entity_id`    BIGINT       NULL     DEFAULT NULL COMMENT '关联实体ID（新增类可空，审核通过后回填）',
    `apply_type`   VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '申请类型：NEW/CLOSE/CHANGE',
    `status`       VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT '审核状态：pending/approved/rejected',
    `payload`      TEXT         NULL     DEFAULT NULL COMMENT '申请字段快照（JSON）',
    `reject_reason` VARCHAR(255) NULL    DEFAULT NULL COMMENT '退回原因（rejected 时填写）',
    `handled_by`   BIGINT       NULL     DEFAULT NULL COMMENT '处理人管理员ID',
    `handled_at`   DATETIME     NULL     DEFAULT NULL COMMENT '处理时间',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_entity_applytype_pending` (`entity_type`, `entity_id`, `apply_type`, `status`),
    KEY `idx_applicant` (`applicant_id`),
    KEY `idx_status` (`status`),
    KEY `idx_entity` (`entity_type`, `entity_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='实体贡献统一申请';

SET FOREIGN_KEY_CHECKS = 1;
