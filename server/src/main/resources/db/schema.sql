-- =============================================================
-- 食在交大 建立数据库（建表）脚本（MySQL 8）
-- =============================================================
-- 用途：从零创建数据库与全部表结构、最终字段（含 region 列、折扣价等扩展字段）。
-- 执行前提：已先 CREATE DATABASE bjtu_food 并 USE bjtu_food。
-- 重置服务器：先 DROP DATABASE bjtu_food 再重建库，随后执行本文件即可还原表结构。
-- 配合 seed_data.sql 使用：本文件只建表不插数据。
--
-- 说明：
--   1. 角色三种：student（学生）/ admin（普通管理员）/ super_admin（超级管理员，可管理管理员账号）。user.role 默认值 'student'。
--   2. 金额类字段（dish.price）以「分」为单位存储（如 12.00 元 = 1200）。
--   3. 图片/多图类字段使用 JSON 字符串存储（如 ["url1","url2"]）。
--   4. 审核字段 audit_status（pending/approved/rejected）、reject_reason、created_by
--      用于 UGC 内容（dish / stall / canteen）的审核流；后台录入默认 approved。
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------- 用户 --------------------
-- 认证模型（2026-08 微信登录体系，spec §5.y）：
--   · 微信自动静默登录为游客态（verified=0），openid 为登录取号依据（唯一）。
--   · @bjtu.edu.cn 邮箱验证码认证（purpose=verify）→ verified=1、写 bind_email/verified_at，解锁社区写操作。
--   · username 语义：游客建号 'wx_'+openid 尾 16 位；旧邮箱注册用户保留学号。
--   · email 列保留作为历史迁移凭证；password 列仅管理员（后台）保留使用，学生侧不再校验。
CREATE TABLE IF NOT EXISTS `user`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '学号/工号（游客建号为 wx_+openid 尾 16 位，唯一）',
    `email`        VARCHAR(128) NOT NULL DEFAULT '' COMMENT '校园邮箱（历史迁移凭证；新微信用户可为空）',
    `password`     VARCHAR(128) NULL     DEFAULT NULL COMMENT '密码哈希（仅管理员后台用，学生侧不校验）',
    `nickname`     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '昵称',
    `avatar`       VARCHAR(512) NULL     DEFAULT NULL COMMENT '头像URL',
    `role`         VARCHAR(32)  NOT NULL DEFAULT 'student' COMMENT '角色：student / admin / super_admin',
    `status`       VARCHAR(32)  NOT NULL DEFAULT 'active' COMMENT '状态：active / disabled / deleted',
    `openid`       VARCHAR(64)  NULL     DEFAULT NULL COMMENT '微信 openid（静默登录取号依据，唯一；仅微信游客/已认证账号有值，历史学号账号为 NULL）',
    `unionid`      VARCHAR(64)  NULL     DEFAULT NULL COMMENT '微信 unionid（同主体多应用，可空）',
    `verified`     TINYINT      NOT NULL DEFAULT 0 COMMENT '认证状态：0=游客未认证 / 1=已邮箱认证（不进 JWT，后端实时判定）',
    `bind_email`   VARCHAR(128) NULL     DEFAULT NULL COMMENT '已认证绑定邮箱（仅存认证关系，可空）',
    `verified_at`  DATETIME     NULL     DEFAULT NULL COMMENT '认证时间',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `last_login_at` DATETIME     NULL     DEFAULT NULL COMMENT '最近登录时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    UNIQUE KEY `uk_user_email` (`email`),
    UNIQUE KEY `uk_user_openid` (`openid`)
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
    `latitude`      DECIMAL(10,6) NULL    DEFAULT NULL COMMENT '纬度（GCJ-02，距离排序用）',
    `longitude`     DECIMAL(10,6) NULL    DEFAULT NULL COMMENT '经度（GCJ-02，距离排序用）',
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
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '档口ID',
    `canteen_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '所属食堂ID',
    `name`           VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '档口名称',
    `images`         VARCHAR(1024) NULL    DEFAULT NULL COMMENT '档口多图JSON',
    `location`       VARCHAR(128) NULL    DEFAULT NULL COMMENT '档口位置',
    `floor`          VARCHAR(16)  NULL    DEFAULT NULL COMMENT '楼层（如 1F/2F）',
    `window_no`      VARCHAR(32)  NULL    DEFAULT NULL COMMENT '窗口号（如 3号窗口）',
    `business_hours` VARCHAR(64)  NULL    DEFAULT NULL COMMENT '营业时间，如 10:00-20:00',
    `description`    VARCHAR(512) NULL    DEFAULT NULL COMMENT '档口描述',
    `sort_order`     INT          NOT NULL DEFAULT 0 COMMENT '排序权重',
    `status`         VARCHAR(32)  NOT NULL DEFAULT 'open' COMMENT '状态：open / closed',
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
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜品ID',
    `stall_id`       BIGINT       NOT NULL DEFAULT 0 COMMENT '所属档口ID',
    `category_id`    BIGINT       NULL     DEFAULT NULL COMMENT '所属品类ID（category.id，首页品类滚轮筛选用；可空=未分类）',
    `name`           VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '菜品名称',
    `price`          INT          NOT NULL DEFAULT 0 COMMENT '价格（单位：分）',
    `original_price` INT          NULL     DEFAULT NULL COMMENT '原价（折扣前，单位：分）；promo_price 非空视为有折扣',
    `promo_price`    INT          NULL     DEFAULT NULL COMMENT '促销价（单位：分，可空）；非空视为有折扣',
    `description`    VARCHAR(512) NULL     DEFAULT NULL COMMENT '菜品描述',
    `images`         VARCHAR(1024) NULL    DEFAULT NULL COMMENT '菜品多图JSON',
    `tags`           VARCHAR(128) NULL     DEFAULT NULL COMMENT '标签，逗号分隔（recommended/signature）',
    `spice_level`    INT          NOT NULL DEFAULT 0 COMMENT '辣度枚举：0=不辣 1=微辣 2=中辣 3=重辣',
    `portion`        INT          NOT NULL DEFAULT 1 COMMENT '分量枚举：0=小 1=中 2=大',
    `serve_period`   VARCHAR(64)  NULL     DEFAULT NULL COMMENT '供应时段 tag，逗号分隔：breakfast/lunch/dinner/midnight',
    `limited`        INT          NOT NULL DEFAULT 0 COMMENT '是否限量（0=否 1=是）',
    `status`         VARCHAR(32)  NOT NULL DEFAULT 'on' COMMENT '上架状态：on / off',
    `audit_status`  VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT '审核状态：pending/approved/rejected',
    `reject_reason` VARCHAR(255) NULL    DEFAULT NULL COMMENT '退回原因（rejected 时填写）',
    `created_by`    BIGINT       NULL    DEFAULT NULL COMMENT '提交人用户ID',
    `view_count`    INT          NOT NULL DEFAULT 0 COMMENT '浏览量',
    `avg_rating`    DECIMAL(3, 2) NULL    DEFAULT NULL COMMENT '平均评分',
    `rating_count`  INT          NOT NULL DEFAULT 0 COMMENT '评价数',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_dish_stall` (`stall_id`),
    KEY `idx_dish_category` (`category_id`),
    KEY `idx_dish_audit` (`audit_status`),
    -- 热度/推荐排序（view_count/rating_count/avg_rating 无索引）：组合索引同时覆盖过滤列与排序列，
    -- 支持推荐、榜单、列表 heat 排序走索引扫描（表达式排序本身无法索引，该索引覆盖常用过滤+排序列）
    KEY `idx_dish_heat` (`status`, `audit_status`, `view_count`, `rating_count`, `avg_rating`)
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
    KEY `idx_review_user` (`user_id`),
    UNIQUE KEY `uk_review_user_dish` (`user_id`, `dish_id`)
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
    `code`       VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '品类机器标识（唯一，如 malatang/noodle/rice/home/bbq/porridge/drink/halal；前端滚轮 key 与筛选用）',
    `name`       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '分类名称（如 麻辣烫/面食/盖饭套餐/家常小炒/烧烤炸物/汤粥/饮品甜点/清真）',
    `sort_order` INT          NOT NULL DEFAULT 0 COMMENT '排序权重（越小越靠前，对应首页品类滚轮顺序）',
    `status`     VARCHAR(32)  NOT NULL DEFAULT 'enabled' COMMENT '状态：enabled / disabled',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_code` (`code`),
    KEY `idx_category_status_sort` (`status`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='菜品品类（首页品类滚轮）';

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

-- -------------------- 最新活动（公众号文章卡片） --------------------
CREATE TABLE IF NOT EXISTS `activity`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '活动ID',
    `title`       VARCHAR(100) NOT NULL DEFAULT '' COMMENT '活动/文章标题',
    `description` VARCHAR(500) NULL    DEFAULT NULL COMMENT '摘要（卡片副文案）',
    `image`       VARCHAR(500) NULL    DEFAULT NULL COMMENT '封面图 URL（公众号文章封面，可空）',
    `article_url` VARCHAR(500) NULL    DEFAULT NULL COMMENT '公众号文章链接（小程序 web-view 打开）',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'enabled' COMMENT '展示状态：enabled/disabled',
    `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序权重（越小越靠前）',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_activity_status_sort` (`status`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='最新活动（公众号文章卡片）';

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
    `type`         VARCHAR(32) NOT NULL DEFAULT 'suggestion' COMMENT '反馈类型：suggestion/error/add/bug/other/report',
    `content`      VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '反馈内容',
    `contact`      VARCHAR(128)  NULL    DEFAULT NULL COMMENT '联系方式',
    `images`       VARCHAR(2048) NULL    DEFAULT NULL COMMENT '附图（JSON 数组字符串，绝对URL；截图/作证照片/菜品图）',
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
    ADD COLUMN `limited`     TINYINT NOT NULL DEFAULT 0 COMMENT '是否限量（0=否 1=是）',
    ADD COLUMN `region`      VARCHAR(32) NULL     DEFAULT NULL COMMENT '地域（美食来源地），如 清真/川湘/西北/粤式/东北';

-- 评价：有用计数（冗余列，由 review_useful 聚合维护）
ALTER TABLE `review`
    ADD COLUMN `useful_count` INT NOT NULL DEFAULT 0 COMMENT '「有用」标记数（一人一票，uk_useful_user_review）';

-- 食堂坐标（GCJ-02）：首页瀑布流「距你 Xm」依赖 canteen.latitude/longitude（前端 Haversine 本地计算）。
-- 新库：CREATE TABLE 已含该列；旧库：幂等迁移补齐（MySQL 不支持 ADD COLUMN IF NOT EXISTS，用存储过程防护）。
-- 坐标兜底：旧库可能已有 canteen 行但坐标 NULL，按食堂名回填 seed 默认坐标，保证「距你」始终可算。
DROP PROCEDURE IF EXISTS `add_canteen_location`;
DELIMITER $$
CREATE PROCEDURE `add_canteen_location`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'canteen'
          AND COLUMN_NAME = 'latitude'
    ) THEN
        ALTER TABLE `canteen`
            ADD COLUMN `latitude`  DECIMAL(10,6) NULL DEFAULT NULL COMMENT '纬度（GCJ-02，距离排序用）';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'canteen'
          AND COLUMN_NAME = 'longitude'
    ) THEN
        ALTER TABLE `canteen`
            ADD COLUMN `longitude` DECIMAL(10,6) NULL DEFAULT NULL COMMENT '经度（GCJ-02，距离排序用）';
    END IF;

    -- 旧库坐标 NULL 兜底回填（仅当列已存在且行为 NULL 时执行；新库 seed 已带值，不受影响）
    UPDATE `canteen`
    SET `latitude` = CASE `name`
                         WHEN '学一食堂' THEN 39.953800
                         WHEN '学二食堂' THEN 39.954200
                         WHEN '学三食堂' THEN 39.954600
                         WHEN '明湖餐厅' THEN 39.955800
                         WHEN '嘉园餐厅' THEN 39.953000
                         WHEN '清真食堂' THEN 39.954800
                         WHEN '留园餐厅' THEN 39.957000
                         ELSE `latitude`
        END,
        `longitude` = CASE `name`
                          WHEN '学一食堂' THEN 116.335400
                          WHEN '学二食堂' THEN 116.335800
                          WHEN '学三食堂' THEN 116.336200
                          WHEN '明湖餐厅' THEN 116.331500
                          WHEN '嘉园餐厅' THEN 116.339000
                          WHEN '清真食堂' THEN 116.335000
                          WHEN '留园餐厅' THEN 116.338000
                          ELSE `longitude`
        END
    WHERE `latitude` IS NULL OR `longitude` IS NULL;
END$$
DELIMITER ;
CALL `add_canteen_location`();
DROP PROCEDURE IF EXISTS `add_canteen_location`;

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

-- -------------------- 社区动态（task-12.x 社区广场） --------------------
CREATE TABLE IF NOT EXISTS `moment`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '动态ID',
    `user_id`        BIGINT       NOT NULL DEFAULT 0 COMMENT '发布者用户ID',
    `content`        VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '动态正文',
    `images`         VARCHAR(1024) NULL    DEFAULT NULL COMMENT '动态图片URL列表（逗号分隔，≤9张）',
    `related_type`   VARCHAR(32)  NOT NULL DEFAULT 'none' COMMENT '关联对象类型：dish / stall / none',
    `related_id`     BIGINT       NULL    DEFAULT NULL COMMENT '关联对象ID（related_type=none 时为 NULL）',
    `audit_status`   VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT '审核状态：pending/approved/rejected',
    `reject_reason`  VARCHAR(255) NULL    DEFAULT NULL COMMENT '退回原因（rejected 时填写）',
    `useful_count`   INT          NOT NULL DEFAULT 0 COMMENT '「有用👍」标记数（一人一票）',
    `comment_count`  INT          NOT NULL DEFAULT 0 COMMENT '评论数（冗余计数）',
    `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '下架状态：0=正常 1=管理员强制下架',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_moment_user` (`user_id`),
    KEY `idx_moment_related` (`related_type`, `related_id`),
    KEY `idx_moment_audit` (`audit_status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='社区动态';

-- 动态「有用👍」标记（一人一票）
CREATE TABLE IF NOT EXISTS `moment_useful`
(
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '标记ID',
    `user_id`    BIGINT   NOT NULL DEFAULT 0 COMMENT '用户ID',
    `moment_id`  BIGINT   NOT NULL DEFAULT 0 COMMENT '动态ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_useful_user_moment` (`user_id`, `moment_id`),
    KEY `idx_useful_moment` (`moment_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='动态有用标记';

-- 动态评论（含一层回复）
CREATE TABLE IF NOT EXISTS `moment_comment`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `moment_id`    BIGINT       NOT NULL DEFAULT 0 COMMENT '所属动态ID',
    `user_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '评论者用户ID',
    `parent_id`    BIGINT       NULL    DEFAULT NULL COMMENT '父评论ID（一层回复：NULL=顶级评论）',
    `content`      VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '评论正文',
    `useful_count` INT          NOT NULL DEFAULT 0 COMMENT '「有用👍」计数（一人一票）',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_mc_moment` (`moment_id`),
    KEY `idx_mc_user` (`user_id`),
    KEY `idx_mc_parent` (`parent_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='动态评论';

-- 动态评论图片（task-13：评论支持至多 3 张图，JSON 数组字符串存储）
-- MySQL 不支持 ADD COLUMN IF NOT EXISTS，用存储过程做幂等防护
DROP PROCEDURE IF EXISTS `add_moment_comment_images`;
DELIMITER $$
CREATE PROCEDURE `add_moment_comment_images`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'moment_comment'
          AND COLUMN_NAME = 'images'
    ) THEN
        ALTER TABLE `moment_comment`
            ADD COLUMN `images` VARCHAR(2000) NULL DEFAULT NULL COMMENT '评论图片（JSON 数组字符串，最多 3 张）';
    END IF;
END$$
DELIMITER ;
CALL `add_moment_comment_images`();
DROP PROCEDURE IF EXISTS `add_moment_comment_images`;

-- 邮箱验证码（认证用途 verify；code_hash 存哈希，过期/使用后标记）
CREATE TABLE IF NOT EXISTS `email_verification_code`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `email`      VARCHAR(128) NOT NULL DEFAULT '' COMMENT '邮箱地址',
    `code_hash`  VARCHAR(128) NOT NULL DEFAULT '' COMMENT '验证码哈希（BCrypt）',
    `purpose`    VARCHAR(32)  NOT NULL DEFAULT 'verify' COMMENT '用途：verify（学号邮箱认证，替代旧 login/register/reset）',
    `expires_at` DATETIME     NULL DEFAULT NULL COMMENT '过期时间',
    `used_at`    DATETIME     NULL DEFAULT NULL COMMENT '使用时间（已用则非空）',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_evc_email` (`email`, `purpose`),
    KEY `idx_evc_expires` (`expires_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='邮箱验证码记录';

-- 浏览足迹 view_log（同时供猜你喜欢个性化读取）
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
  COLLATE = utf8mb4_general_ci COMMENT ='浏览足迹（唯一存储，供猜你喜欢个性化读取）';

-- 操作日志 operation_log（AOP 埋点，Web 管理端只读查询）
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

-- 菜品热度/推荐排序索引 idx_dish_heat（M3）：CREATE TABLE 已含该 KEY；
-- 旧库幂等补建（MySQL 8 不支持 CREATE INDEX IF NOT EXISTS，用存储过程防护，与上方迁移惯例一致）
DROP PROCEDURE IF EXISTS `add_dish_heat_index`;
DELIMITER $$
CREATE PROCEDURE `add_dish_heat_index`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'dish'
          AND INDEX_NAME = 'idx_dish_heat'
    ) THEN
        ALTER TABLE `dish`
            ADD INDEX `idx_dish_heat` (`status`, `audit_status`, `view_count`, `rating_count`, `avg_rating`);
    END IF;
END$$
DELIMITER ;
CALL `add_dish_heat_index`();
DROP PROCEDURE IF EXISTS `add_dish_heat_index`;

-- 微信登录体系 user 表新列幂等迁移（task-01，spec §5.y.2）：
-- 旧库（尚无 openid/unionid/verified/bind_email/verified_at）补齐列与唯一索引，不破坏既有数据。
DROP PROCEDURE IF EXISTS `add_user_wechat_auth`;
DELIMITER $$
CREATE PROCEDURE `add_user_wechat_auth`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'openid'
    ) THEN
        ALTER TABLE `user`
            ADD COLUMN `openid`     VARCHAR(64)  NULL DEFAULT NULL COMMENT '微信 openid（静默登录取号依据，唯一；仅微信账号有值，历史学号账号为 NULL）';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'unionid'
    ) THEN
        ALTER TABLE `user`
            ADD COLUMN `unionid`    VARCHAR(64)  NULL DEFAULT NULL COMMENT '微信 unionid（同主体多应用，可空）';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'verified'
    ) THEN
        ALTER TABLE `user`
            ADD COLUMN `verified`   TINYINT      NOT NULL DEFAULT 0 COMMENT '认证状态：0=游客未认证 / 1=已邮箱认证（不进 JWT，后端实时判定）';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'bind_email'
    ) THEN
        ALTER TABLE `user`
            ADD COLUMN `bind_email` VARCHAR(128) NULL DEFAULT NULL COMMENT '已认证绑定邮箱（仅存认证关系，可空）';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'verified_at'
    ) THEN
        ALTER TABLE `user`
            ADD COLUMN `verified_at` DATETIME    NULL DEFAULT NULL COMMENT '认证时间';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'uk_user_openid'
    ) THEN
        ALTER TABLE `user` ADD UNIQUE KEY `uk_user_openid` (`openid`);
    END IF;
END$$
DELIMITER ;
CALL `add_user_wechat_auth`();
DROP PROCEDURE IF EXISTS `add_user_wechat_auth`;

SET FOREIGN_KEY_CHECKS = 1;
