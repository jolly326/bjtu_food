-- =============================================================
-- 食在交大 建立数据库（建表）脚本（MySQL 8）
-- =============================================================
-- 用途：从零创建数据库与全部表结构、最终字段（含 region 列、折扣价等扩展字段）。
-- 本脚本自包含：自动建库并切换 USE bjtu_food，不依赖工具/命令行预先选中库。
-- 重置服务器：先 DROP DATABASE bjtu_food 再执行本文件即可还原表结构（或直接执行本文件覆盖）。
-- 配合 seed_data.sql 使用：本文件只建表不插数据。
--
-- 说明：
--   1. 角色三种：student（学生）/ admin（普通管理员）/ super_admin（超级管理员，可管理管理员账号）。user.role 默认值 'student'。
--   2. 金额类字段（dish.price）以「分」为单位存储（如 12.00 元 = 1200）。
--   3. 图片/多图类字段使用 JSON 字符串存储（如 ["url1","url2"]）。
--   4. 审核字段 audit_status（pending/approved/rejected）、reject_reason、created_by
--      用于 UGC 内容（dish / stall / canteen）的审核流；后台录入默认 approved。
--   5. UGC 内容安全（2026-09-13 产品定稿）：review / user_feedback 支持配图（images JSON），
--      sec_state 记录微信内容安全检测结果：pass（通过）/ review（待人工复核，对他端不可见，作者本人可见）/
--      rejected（管理端人工复核不通过，对他端不可见）。配图经 COS 转存后以 COS 绝对 URL 存库。
-- =============================================================

-- 自包含建库选库：避免在未选中库时建表语句落入默认库（如 mysql 系统库）触发 1044 权限错误
CREATE DATABASE IF NOT EXISTS `bjtu_food` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `bjtu_food`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------- 用户 --------------------
-- 认证模型（2026-08 微信登录体系，spec §5.y）：
--   · 微信自动静默登录为游客态（verified=0），openid 为登录取号依据（唯一）。
--   · @bjtu.edu.cn 邮箱验证码认证（purpose=verify）→ verified=1、写 bind_email/verified_at，解锁 UGC 写操作。
--   · username 语义：游客建号 'wx_'+openid 尾 16 位；旧邮箱注册用户保留学号。
--   · email 列保留作为历史迁移凭证；password 列仅管理员（后台）保留使用，学生侧不再校验。
CREATE TABLE IF NOT EXISTS `user`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '学号/工号（游客建号为 wx_+openid 尾 16 位，唯一）',
    `email`        VARCHAR(128) NULL    DEFAULT NULL COMMENT '校园邮箱（历史迁移凭证；微信游客为 NULL，多游客 NULL 不冲突唯一索引 uk_user_email）',
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
    `alias`          VARCHAR(255) NULL     DEFAULT NULL COMMENT '搜索别名（逗号分隔，管理员配置）',
    `price`          INT          NOT NULL DEFAULT 0 COMMENT '价格（单位：分）',
    `original_price` INT          NULL     DEFAULT NULL COMMENT '原价（折扣前，单位：分）；promo_price 非空视为有折扣',
    `promo_price`    INT          NULL     DEFAULT NULL COMMENT '促销价（单位：分，可空）；非空视为有折扣',
    `description`    VARCHAR(512) NULL     DEFAULT NULL COMMENT '菜品描述',
    `images`         VARCHAR(1024) NULL    DEFAULT NULL COMMENT '菜品多图JSON',
    `tags`           VARCHAR(128) NULL     DEFAULT NULL COMMENT '标签，逗号分隔；权威值域：recommended(必吃推荐)/signature(招牌菜)；web 管理端写入以 web/src/api/tags.ts TAG_OPTIONS 为准，仅允许登记值',
    `spice_level`    INT          NOT NULL DEFAULT 0 COMMENT '辣度枚举：0=不辣 1=微辣 2=中辣 3=重辣',
    `portion`        INT          NOT NULL DEFAULT 1 COMMENT '分量枚举：0=小 1=中 2=大',
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
    `images`     VARCHAR(1024) NULL    DEFAULT NULL COMMENT '评价配图URL列表JSON（COS 绝对地址，≤3 张）',
    `sec_state`  VARCHAR(16)  NOT NULL DEFAULT 'pass' COMMENT '内容安全状态：pass/review/rejected（review=机检待人工复核，rejected=人工复核不通过；review/rejected 对他端不可见，作者本人可见）',
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
-- /favorites 端点与 favorite 表本期彻底删除。产品定稿：不做「收藏/喜欢」功能，
-- UGC 互动仅保留「有用」（review_useful 表 + useful_count 计数），故不另建喜欢计数存储。

-- -------------------- 消息通知（账号注销级联清理依赖，A.15） --------------------
CREATE TABLE IF NOT EXISTS `notification`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    `user_id`    BIGINT       NOT NULL DEFAULT 0 COMMENT '接收用户ID',
    `type`       VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '通知类型：dish_audit / feedback_handle',
    `title`      VARCHAR(128) NOT NULL DEFAULT '' COMMENT '通知标题',
    `content`    VARCHAR(512) NULL     DEFAULT NULL COMMENT '通知正文',
    `related_id` BIGINT       NULL     DEFAULT NULL COMMENT '关联对象ID（菜品/反馈ID，按 type 解释）',
    `is_read`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读：0=未读 1=已读',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_notification_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='消息通知';

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

-- -------------------- 用户反馈 --------------------
CREATE TABLE IF NOT EXISTS `user_feedback`
(
    `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '反馈ID',
    `user_id`      BIGINT   NOT NULL DEFAULT 0 COMMENT '用户ID',
    `type`         VARCHAR(32) NOT NULL DEFAULT 'suggestion' COMMENT '反馈类型：suggestion/error/add/bug/other/report',
    `content`      VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '反馈内容',
    `images`       VARCHAR(1024) NULL    DEFAULT NULL COMMENT '反馈配图URL列表JSON（COS 绝对地址，≤3 张）',
    `sec_state`    VARCHAR(16)  NOT NULL DEFAULT 'pass' COMMENT '内容安全状态：pass/review/rejected（review=机检待人工复核，rejected=人工复核不通过；仅管理端复核标记，无公开展示）',
    `contact`      VARCHAR(128)  NULL    DEFAULT NULL COMMENT '联系方式',
    `status`       VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '处理状态：pending/handled',
    `reply`        VARCHAR(1024) NULL    DEFAULT NULL COMMENT '管理员回复',
    `related_type` VARCHAR(32)   NULL    DEFAULT NULL COMMENT '关联类型：举报为 review；信息纠错为 dish；其他为 null',
    `related_id`   BIGINT        NULL    DEFAULT NULL COMMENT '关联对象ID：举报为评价ID；信息纠错为菜品ID；其他为 null',
    `handled_at`   DATETIME      NULL    DEFAULT NULL COMMENT '处理时间',
    `handler_id`   BIGINT        NULL    DEFAULT NULL COMMENT '处理人管理员ID',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_feedback_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='用户反馈';

-- =============================================================
-- 一期扩展字段（追加，不改动既有列）
-- 来源：tasks/ARCH_DECISIONS_PHASE1.md §1.2
-- =============================================================

-- 档口：楼层 / 窗口号 / 营业时间（CREATE TABLE 已含；旧库幂等补齐，列定义与 CREATE 保持一致）
DROP PROCEDURE IF EXISTS `add_stall_phase1_fields`;
DELIMITER $$
CREATE PROCEDURE `add_stall_phase1_fields`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stall' AND COLUMN_NAME = 'floor'
    ) THEN
        ALTER TABLE `stall` ADD COLUMN `floor` VARCHAR(16) NOT NULL DEFAULT '' COMMENT '楼层（如 1F/2F）';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stall' AND COLUMN_NAME = 'window_no'
    ) THEN
        ALTER TABLE `stall` ADD COLUMN `window_no` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '窗口号';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stall' AND COLUMN_NAME = 'business_hours'
    ) THEN
        ALTER TABLE `stall` ADD COLUMN `business_hours` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '营业时间，如 10:00-20:00';
    END IF;
END$$
DELIMITER ;
CALL `add_stall_phase1_fields`();
DROP PROCEDURE IF EXISTS `add_stall_phase1_fields`;

-- 菜品：辣度 / 分量 / 风味菜系（spice_level 等 CREATE 已含；region 仅此处补充；旧库幂等补齐）
-- 注：供应时段 serve_period 与限量 limited 已于 2026-09-14 整体下线（见文件末尾 drop_dish_unused_fields 迁移）
DROP PROCEDURE IF EXISTS `add_dish_phase1_fields`;
DELIMITER $$
CREATE PROCEDURE `add_dish_phase1_fields`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'spice_level'
    ) THEN
        ALTER TABLE `dish` ADD COLUMN `spice_level` INT NOT NULL DEFAULT 0 COMMENT '辣度枚举：0=不辣 1=微辣 2=中辣 3=重辣';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'portion'
    ) THEN
        ALTER TABLE `dish` ADD COLUMN `portion` INT NOT NULL DEFAULT 1 COMMENT '分量枚举：0=小 1=中 2=大';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'region'
    ) THEN
        ALTER TABLE `dish` ADD COLUMN `region` VARCHAR(32) NULL DEFAULT NULL COMMENT '风味/菜系：东北/川湘/粤式/西北/清真/其他';
    END IF;
END$$
DELIMITER ;
CALL `add_dish_phase1_fields`();
DROP PROCEDURE IF EXISTS `add_dish_phase1_fields`;

-- 评价：有用计数（冗余列，由 review_useful 聚合维护；幂等补齐）
DROP PROCEDURE IF EXISTS `add_review_useful_count`;
DELIMITER $$
CREATE PROCEDURE `add_review_useful_count`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND COLUMN_NAME = 'useful_count'
    ) THEN
        ALTER TABLE `review`
            ADD COLUMN `useful_count` INT NOT NULL DEFAULT 0 COMMENT '「有用」标记数（一人一票，uk_useful_user_review）';
    END IF;
END$$
DELIMITER ;
CALL `add_review_useful_count`();
DROP PROCEDURE IF EXISTS `add_review_useful_count`;

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

-- 菜品：折扣价（task-12.9；CREATE TABLE 已含，列定义以 CREATE 为准：original_price/promo_price 均允许 NULL；旧库幂等补齐）
DROP PROCEDURE IF EXISTS `add_dish_promo_fields`;
DELIMITER $$
CREATE PROCEDURE `add_dish_promo_fields`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'original_price'
    ) THEN
        ALTER TABLE `dish` ADD COLUMN `original_price` INT NULL DEFAULT NULL COMMENT '原价（单位：分，折扣前）；promo_price 非空视为有折扣';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'promo_price'
    ) THEN
        ALTER TABLE `dish` ADD COLUMN `promo_price` INT NULL DEFAULT NULL COMMENT '促销价（单位：分，可空；非空视为有折扣）';
    END IF;
END$$
DELIMITER ;
CALL `add_dish_promo_fields`();
DROP PROCEDURE IF EXISTS `add_dish_promo_fields`;

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
    `target_type` VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '浏览对象类型：dish / stall / canteen',
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
    `action`      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '动作标识：audit_approve / audit_reject / review_hide / review_delete / feedback_handle / ...',
    `target_type` VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '操作对象类型：dish / stall / canteen / feedback / review',
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

    -- 旧库 email 列修正：NOT NULL DEFAULT '' → 允许 NULL（微信游客 email=NULL 不冲突 uk_user_email 唯一索引；
    -- 若仍为 NOT NULL 且默认空串，多个游客建号会撞唯一索引导致第二个游客起登录失败）
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'email' AND IS_NULLABLE = 'NO'
    ) THEN
        ALTER TABLE `user`
            MODIFY COLUMN `email` VARCHAR(128) NULL DEFAULT NULL COMMENT '校园邮箱（历史迁移凭证；微信游客为 NULL，多游客 NULL 不冲突唯一索引 uk_user_email）';
    END IF;
END$$
DELIMITER ;
CALL `add_user_wechat_auth`();
DROP PROCEDURE IF EXISTS `add_user_wechat_auth`;

-- UGC 内容安全列幂等迁移（2026-09-13 产品定稿：评价/反馈支持配图，全部 UGC 过微信内容安全检测）：
-- review / user_feedback 补齐 images（配图 URL 列表 JSON）与 sec_state（内容安全状态），
-- 新库 CREATE TABLE 已含该列；旧库幂等补齐，列定义与 CREATE 保持一致，不破坏既有数据。
DROP PROCEDURE IF EXISTS `add_review_ugc_sec_fields`;
DELIMITER $$
CREATE PROCEDURE `add_review_ugc_sec_fields`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND COLUMN_NAME = 'images'
    ) THEN
        ALTER TABLE `review`
            ADD COLUMN `images` VARCHAR(1024) NULL DEFAULT NULL COMMENT '评价配图URL列表JSON（COS 绝对地址，≤3 张）';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND COLUMN_NAME = 'sec_state'
    ) THEN
        ALTER TABLE `review`
            ADD COLUMN `sec_state` VARCHAR(16) NOT NULL DEFAULT 'pass' COMMENT '内容安全状态：pass/review/rejected（review=机检待人工复核，rejected=人工复核不通过；review/rejected 对他端不可见，作者本人可见）';
    END IF;
END$$
DELIMITER ;
CALL `add_review_ugc_sec_fields`();
DROP PROCEDURE IF EXISTS `add_review_ugc_sec_fields`;

DROP PROCEDURE IF EXISTS `add_feedback_ugc_sec_fields`;
DELIMITER $$
CREATE PROCEDURE `add_feedback_ugc_sec_fields`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_feedback' AND COLUMN_NAME = 'images'
    ) THEN
        ALTER TABLE `user_feedback`
            ADD COLUMN `images` VARCHAR(1024) NULL DEFAULT NULL COMMENT '反馈配图URL列表JSON（COS 绝对地址，≤3 张）';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_feedback' AND COLUMN_NAME = 'sec_state'
    ) THEN
        ALTER TABLE `user_feedback`
            ADD COLUMN `sec_state` VARCHAR(16) NOT NULL DEFAULT 'pass' COMMENT '内容安全状态：pass/review/rejected（review=机检待人工复核，rejected=人工复核不通过；仅管理端复核标记，无公开展示）';
    END IF;
END$$
DELIMITER ;
CALL `add_feedback_ugc_sec_fields`();
DROP PROCEDURE IF EXISTS `add_feedback_ugc_sec_fields`;

-- 菜品搜索别名（2026-09-13 需求：搜索命中别名也能找到菜品；管理员经后台配置）：
-- dish 补齐 alias 列（CREATE TABLE 已含，列定义以 CREATE 为准：alias VARCHAR(255) NULL）；
-- 旧库幂等补齐（MySQL 不支持 ADD COLUMN IF NOT EXISTS，用存储过程防护，与上方迁移惯例一致）。
DROP PROCEDURE IF EXISTS `add_dish_alias`;
DELIMITER $$
CREATE PROCEDURE `add_dish_alias`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'alias'
    ) THEN
        ALTER TABLE `dish`
            ADD COLUMN `alias` VARCHAR(255) NULL DEFAULT NULL COMMENT '搜索别名（逗号分隔，管理员配置）';
    END IF;
END$$
DELIMITER ;
CALL `add_dish_alias`();
DROP PROCEDURE IF EXISTS `add_dish_alias`;

-- 字段下线（2026-09-14 §7.9 用户拍板）：
--   serve_period（餐段）与 limited（限量）在端上/后台/代码中均为零消费，整体下线；
--   region 语义定型为「风味/菜系」（非校区），同步列注释，避免后续维护者误读。
-- 幂等：先做存在性判断再 DROP，重复执行安全；两列无任何代码/数据引用。
DROP PROCEDURE IF EXISTS `drop_dish_unused_fields`;
DELIMITER $$
CREATE PROCEDURE `drop_dish_unused_fields`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'serve_period'
    ) THEN
        ALTER TABLE `dish` DROP COLUMN `serve_period`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'limited'
    ) THEN
        ALTER TABLE `dish` DROP COLUMN `limited`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'region'
    ) THEN
        ALTER TABLE `dish`
            MODIFY COLUMN `region` VARCHAR(32) NULL DEFAULT NULL COMMENT '风味/菜系：东北/川湘/粤式/西北/清真/其他';
    END IF;
END$$
DELIMITER ;
CALL `drop_dish_unused_fields`();
DROP PROCEDURE IF EXISTS `drop_dish_unused_fields`;

-- 字段下线（2026-09-14 §7.10 用户拍板）：
--   review.tags（评价标签）随「美团式写评」确认不做而下线：
--   写入侧无任何入口（ReviewReq / 小程序端均无该字段），读取侧实体/VO 零引用（已复核），
--   属纯零消费列。CREATE TABLE 已同步移除该列定义；旧库在此幂等 DROP，保证重复执行安全、不影响既有数据。
--   同批 §7.10 决定保留（不删）两个 retired 列，仅停写、不再追究身份，此处不处理：
--     - user_feedback.handler_id（管理端操作人身份降级：单口令即单人，handle 不再写入，保持 NULL）
--     - operation_log.admin_id（同上，切面不再取当前管理员 ID，显式写 0）
DROP PROCEDURE IF EXISTS `drop_review_tags_column`;
DELIMITER $$
CREATE PROCEDURE `drop_review_tags_column`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND COLUMN_NAME = 'tags'
    ) THEN
        ALTER TABLE `review` DROP COLUMN `tags`;
    END IF;
END$$
DELIMITER ;
CALL `drop_review_tags_column`();
DROP PROCEDURE IF EXISTS `drop_review_tags_column`;

SET FOREIGN_KEY_CHECKS = 1;
