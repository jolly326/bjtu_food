-- =============================================================
-- 食在交大 建立数据库（建表）脚本（MySQL 8）
-- =============================================================
-- 用途：从零创建数据库与全部表结构、最终字段（含 region 列、折扣价等扩展字段）。
-- 本脚本自包含：自动建库并切换 USE bjtu_food，不依赖工具/命令行预先选中库。
-- 重置服务器：先 DROP DATABASE bjtu_food 再执行本文件即可还原表结构（或直接执行本文件覆盖）。
-- 配合 seed_data.sql 使用：本文件只建表不插数据。
--
-- 说明：
--   1. 角色两层：student（学生）/ admin（管理端账号数据标记）。**super_admin 已于 2026-09-14 移除
--      （2026-09-15 蓝图 v1 再确认，见 project_spec.md §7.23）**——管理端无登录与角色体系，
--      /admin/** 统一由环境变量口令（AdminTokenFilter，X-Admin-Token == ADMIN_TOKEN）把关；
--      role 仅保留两层数据语义，用于区分账号归属，不作权限分层。user.role 默认值 'student'。
--   2. 金额类字段（dish.price）以「分」为单位存储（如 12.00 元 = 1200）。
--   3. 图片/多图类字段使用 JSON 字符串存储（如 ["url1","url2"]）。
--   4. 审核字段 audit_status（pending/approved/rejected）、reject_reason、created_by
--      用于 UGC 内容审核流；后台录入默认 approved。
--      注（2026-09-14 用户拍板）：食堂/档口已去实体化，降级为「菜品筛选属性字典」，
--      其 status / audit_status / reject_reason 三列已整体下线（CREATE TABLE 不再创建）。
--      注（2026-09-15 阶段4）：
--        · canteen.created_by / stall.created_by 同批退役（无归属语义、写侧恒占位值、三端零消费）；
--        · dish.audit_status 全量退役（菜品无独立审核：管理员录入/编辑即生效，公开可见性唯一判据为 status='on'），
--          列与相关索引（idx_dish_audit、idx_dish_heat 中的该列）同批移除；
--        · 上述存量库清理均由文件末尾幂等 DROP 段完成（可重跑）；
--        · dish.reject_reason / dish.created_by、review / user_feedback 的审核类列保留（后两者见第 5 条）。
--   5. UGC 内容安全（2026-09-13 产品定稿 + 2026-09-15 用户拍板「取消人工复核」）：
--      review / user_feedback 支持配图（images JSON，配图经 COS 转存后以 COS 绝对 URL 存库）；
--      内容安全机检结果（sec_state：pass/review/rejected）**已全链退役**——机检 pass/review 一律放行、
--      risky 直接拒绝（不入库），无人工复核队列，故 CREATE TABLE 不再创建该列，
--      存量库由文件末尾 drop_sec_state_columns 幂等段清理（可重跑）。
--   6. 菜品品类整链退役（2026-09-15 用户拍板）：category 表与 dish.category_id 列（含单列索引
--      idx_dish_category）不再创建（端上零呈现、仅 Web 自用的不可见第三维度）；
--      存量库由文件末尾 drop_category_chain 幂等段清理（可重跑）。
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
--   · email 列保留作为历史迁移凭证；**password 为历史兼容列——管理端与学生端均已不使用**
--     （管理端为环境变量口令 ADMIN_TOKEN，无账号密码登录；学生端为微信静默登录 + 邮箱验证码，无密码体系）。
--     BCrypt 仅用于邮箱验证码哈希（email_verification_code.code_hash），不用于任何登录口令校验。
--     （2026-09-15 蓝图 v1 / project_spec.md §7.23「管理端无密码体系」，DataInitializer 删除后口径。）
CREATE TABLE IF NOT EXISTS `user`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '学号/工号（游客建号为 wx_+openid 尾 16 位，唯一）',
    `email`        VARCHAR(128) NULL    DEFAULT NULL COMMENT '校园邮箱（历史迁移凭证；微信游客为 NULL，多游客 NULL 不冲突唯一索引 uk_user_email）',
    `password`     VARCHAR(128) NULL     DEFAULT NULL COMMENT '历史兼容列：密码哈希。管理端与学生端均已不使用（管理端为环境变量口令，见 project_spec.md §7.23；BCrypt 仅用于邮箱验证码哈希）',
    `nickname`     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '昵称',
    `avatar`       VARCHAR(512) NULL     DEFAULT NULL COMMENT '头像URL',
    `role`         VARCHAR(32)  NOT NULL DEFAULT 'student' COMMENT '角色：student / admin（两层；super_admin 已移除，2026-09-15 见 project_spec.md §7.23）',
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
    `sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '排序权重（越小越靠前）',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='食堂（created_by 列已退役，2026-09-15 阶段4）';

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
    `description`    VARCHAR(512) NULL    DEFAULT NULL COMMENT '档口描述',
    `sort_order`     INT          NOT NULL DEFAULT 0 COMMENT '排序权重',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_stall_canteen` (`canteen_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='档口（created_by 列已退役，2026-09-15 阶段4）';

-- -------------------- 菜品 --------------------
CREATE TABLE IF NOT EXISTS `dish`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜品ID',
    `stall_id`       BIGINT       NOT NULL DEFAULT 0 COMMENT '所属档口ID',
    `name`           VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '菜品名称',
    `alias`          VARCHAR(255) NULL     DEFAULT NULL COMMENT '搜索别名（逗号分隔，管理员配置）',
    `price`          INT          NOT NULL DEFAULT 0 COMMENT '价格（单位：分）',
    `original_price` INT          NULL     DEFAULT NULL COMMENT '原价（折扣前，单位：分）；promo_price 非空视为有折扣',
    `promo_price`    INT          NULL     DEFAULT NULL COMMENT '促销价（单位：分，可空）；非空视为有折扣',
    `description`    VARCHAR(512) NULL     DEFAULT NULL COMMENT '菜品描述',
    `images`         VARCHAR(1024) NULL    DEFAULT NULL COMMENT '菜品多图JSON',
    `tags`           VARCHAR(128) NULL     DEFAULT NULL COMMENT '标签，逗号分隔；权威值域：recommended(必吃推荐)/signature(招牌菜)；web 管理端写入以 web/src/api/tags.ts TAG_OPTIONS 为准，仅允许登记值',
    `spice_level`    INT          NOT NULL DEFAULT 0 COMMENT '辣度枚举：0=不辣 1=微辣 2=中辣 3=重辣',
    `status`         VARCHAR(32)  NOT NULL DEFAULT 'on' COMMENT '上架状态：on / off',
    `reject_reason` VARCHAR(255) NULL    DEFAULT NULL COMMENT '【历史留痕列】随菜品审核语义退役（2026-09-15 阶段4 起 audit_status 列亦已下线）：无写入入口、恒为 NULL；「不采纳/退回」语义已迁至反馈处理（user_feedback）',
    `created_by`    BIGINT       NULL    DEFAULT NULL COMMENT '提交人用户ID',
    `view_count`    INT          NOT NULL DEFAULT 0 COMMENT '浏览量',
    `avg_rating`    DECIMAL(3, 2) NULL    DEFAULT NULL COMMENT '平均评分',
    `rating_count`  INT          NOT NULL DEFAULT 0 COMMENT '评价数',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_dish_stall` (`stall_id`),
    -- idx_dish_category（category_id 单列）已随品类整链退役一并删除（2026-09-15）
    -- idx_dish_audit（audit_status 单列）已随 audit_status 列退役一并删除（2026-09-15 阶段4）
    -- 热度/推荐排序（view_count/rating_count/avg_rating 无索引）：组合索引同时覆盖过滤列与排序列，
    -- 支持推荐、榜单、列表 heat 排序走索引扫描（表达式排序本身无法索引，该索引覆盖常用过滤+排序列）
    KEY `idx_dish_heat` (`status`, `view_count`, `rating_count`, `avg_rating`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='菜品（audit_status 列已退役，2026-09-15 阶段4）';

-- -------------------- 评价 --------------------
CREATE TABLE IF NOT EXISTS `review`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评价ID',
    `user_id`    BIGINT       NOT NULL DEFAULT 0 COMMENT '评价者用户ID',
    `dish_id`    BIGINT       NOT NULL DEFAULT 0 COMMENT '被评价菜品ID',
    `rating`     INT          NOT NULL DEFAULT 0 COMMENT '评分（1-5星）',
    `content`    VARCHAR(512) NULL    DEFAULT NULL COMMENT '评价内容',
    `images`     VARCHAR(1024) NULL    DEFAULT NULL COMMENT '评价配图URL列表JSON（COS 绝对地址，≤3 张）',
    -- sec_state 列已随「取消人工复核」全链退役（2026-09-15 用户拍板）；存量库由文件末尾 drop_sec_state_columns 幂等清理
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

-- -------------------- 菜品品类：已整链退役（2026-09-15 用户拍板） --------------------
-- category 表（code/name/sort_order/status）与 dish.category_id 一并删除：
-- 该维度端上零呈现、仅 Web 自用的「不可见第三维度」，维护成本高于收益。
-- 新库：本脚本不再创建该表与列；存量库：由文件末尾 drop_category_chain 幂等段清理（可重跑）。

-- -------------------- 用户反馈 --------------------
CREATE TABLE IF NOT EXISTS `user_feedback`
(
    `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '反馈ID',
    `user_id`      BIGINT   NOT NULL DEFAULT 0 COMMENT '用户ID',
    `type`         VARCHAR(32) NOT NULL DEFAULT 'suggestion' COMMENT '反馈类型：suggestion/error/add/bug/other/report',
    `sub`          VARCHAR(16)  NULL    DEFAULT NULL COMMENT '二级分类（仅 type=suggestion 有效）：idea=想法/problem=问题；其他类型为 NULL',
    `content`      VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '反馈内容',
    `images`       VARCHAR(1024) NULL    DEFAULT NULL COMMENT '反馈配图URL列表JSON（COS 绝对地址，≤3 张）',
    -- sec_state 列已随「取消人工复核」全链退役（2026-09-15 用户拍板）；存量库由文件末尾 drop_sec_state_columns 幂等清理
    `contact`      VARCHAR(128)  NULL    DEFAULT NULL COMMENT '联系方式',
    `status`       VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '处理状态：pending/handled',
    `reply`        VARCHAR(1024) NULL    DEFAULT NULL COMMENT '管理员回复',
    `reject_reason` VARCHAR(200) NULL    DEFAULT NULL COMMENT '不采纳/退回原因（outcome=rejected 时必填）',
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

-- 档口：楼层 / 窗口号（CREATE TABLE 已含；旧库幂等补齐，列定义与 CREATE 保持一致）
-- 注：营业时间 stall.business_hours 已于 2026-09-14 §7.14 D 随列下线，本段不再创建；
--     存量库由文件末尾 drop_stall_business_hours 幂等清理，新库 CREATE TABLE 亦不含该列。
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
END$$
DELIMITER ;
CALL `add_stall_phase1_fields`();
DROP PROCEDURE IF EXISTS `add_stall_phase1_fields`;

-- 菜品：辣度 / 风味菜系（spice_level 等 CREATE 已含；region 仅此处补充；旧库幂等补齐）
-- 注1：供应时段 serve_period 与限量 limited 已于 2026-09-14 整体下线（见文件末尾 drop_dish_unused_fields 迁移）
-- 注2：分量 portion 已于 2026-09-14 §7.14（Q-114）整体下线：CREATE TABLE 已移除该列，
--      此处不再 ADD（新库不创建）；存量库由文件末尾 drop_dish_portion 幂等清理。辣度 spice_level 保留。
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
    KEY `idx_view_target` (`target_type`, `target_id`),
    -- 判重复合索引（2026-09-15）：后端浏览量判重已改用 updated_at（同 userId+targetType+targetId
    -- 按时间判定），该四列组合索引覆盖判重查询的过滤列与时间列
    KEY `idx_view_user_target_time` (`user_id`, `target_type`, `target_id`, `updated_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='浏览足迹（唯一存储，供猜你喜欢个性化读取）';

-- 浏览足迹判重复合索引 idx_view_user_target_time（2026-09-15）：
-- 后端浏览量判重（POST /dishes/{id}/view，spec §7.14 第 1 条）已改用 updated_at 判定，
-- 需 (user_id, target_type, target_id, updated_at) 覆盖判重查询。
-- CREATE TABLE 已含该 KEY；旧库幂等补建（MySQL 8 不支持 CREATE INDEX IF NOT EXISTS，
-- 用存储过程防护，与上方 idx_dish_heat 迁移惯例一致），重复执行安全、不影响既有数据。
DROP PROCEDURE IF EXISTS `add_view_log_dedup_index`;
DELIMITER $$
CREATE PROCEDURE `add_view_log_dedup_index`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'view_log'
          AND INDEX_NAME = 'idx_view_user_target_time'
    ) THEN
        ALTER TABLE `view_log`
            ADD INDEX `idx_view_user_target_time` (`user_id`, `target_type`, `target_id`, `updated_at`);
    END IF;
END$$
DELIMITER ;
CALL `add_view_log_dedup_index`();
DROP PROCEDURE IF EXISTS `add_view_log_dedup_index`;

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
        -- 列定义与 CREATE TABLE 保持一致：不含已退役的 audit_status（2026-09-15 阶段4）；
        -- 存量库该列为索引成员，随列 DROP 自动从索引中摘除，无需重建
        ALTER TABLE `dish`
            ADD INDEX `idx_dish_heat` (`status`, `view_count`, `rating_count`, `avg_rating`);
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

-- UGC 配图列幂等迁移（2026-09-13 产品定稿：评价/反馈支持配图）：
-- review / user_feedback 补齐 images（配图 URL 列表 JSON）；
-- 新库 CREATE TABLE 已含该列；旧库幂等补齐，列定义与 CREATE 保持一致，不破坏既有数据。
-- 注（2026-09-15）：本段原同时补齐的 sec_state 列已随「取消人工复核」全链退役，
-- 不再作为「补齐目标」写入，改由文件末尾 drop_sec_state_columns 幂等段落清理（先建后删会互相打架）。
DROP PROCEDURE IF EXISTS `add_review_ugc_images`;
DELIMITER $$
CREATE PROCEDURE `add_review_ugc_images`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND COLUMN_NAME = 'images'
    ) THEN
        ALTER TABLE `review`
            ADD COLUMN `images` VARCHAR(1024) NULL DEFAULT NULL COMMENT '评价配图URL列表JSON（COS 绝对地址，≤3 张）';
    END IF;
END$$
DELIMITER ;
CALL `add_review_ugc_images`();
DROP PROCEDURE IF EXISTS `add_review_ugc_images`;

DROP PROCEDURE IF EXISTS `add_feedback_ugc_images`;
DELIMITER $$
CREATE PROCEDURE `add_feedback_ugc_images`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_feedback' AND COLUMN_NAME = 'images'
    ) THEN
        ALTER TABLE `user_feedback`
            ADD COLUMN `images` VARCHAR(1024) NULL DEFAULT NULL COMMENT '反馈配图URL列表JSON（COS 绝对地址，≤3 张）';
    END IF;
END$$
DELIMITER ;
CALL `add_feedback_ugc_images`();
DROP PROCEDURE IF EXISTS `add_feedback_ugc_images`;

-- 反馈处理结论列（2026-09-15 蓝图 v1，project_spec.md §7.23 第 5 条）：
-- user_feedback.reject_reason（不采纳/退回原因，1~200 字）：管理端处理结论 outcome=rejected 时必填，
-- 随回执通知（已处理/未采纳）向已认证提交人展示；outcome=handled 时不写、保持 NULL。
-- 新库 CREATE TABLE 已含该列；旧库幂等补齐（MySQL 不支持 ADD COLUMN IF NOT EXISTS，用存储过程防护，
-- 与上方迁移惯例一致），列定义与 CREATE 保持一致、可重跑、不影响既有数据。
DROP PROCEDURE IF EXISTS `add_feedback_reject_reason`;
DELIMITER $$
CREATE PROCEDURE `add_feedback_reject_reason`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_feedback' AND COLUMN_NAME = 'reject_reason'
    ) THEN
        ALTER TABLE `user_feedback`
            ADD COLUMN `reject_reason` VARCHAR(200) NULL DEFAULT NULL COMMENT '不采纳/退回原因（outcome=rejected 时必填）';
    END IF;
END$$
DELIMITER ;
CALL `add_feedback_reject_reason`();
DROP PROCEDURE IF EXISTS `add_feedback_reject_reason`;

-- 反馈二级分类列（DEV-01，2026-09-15）：user_feedback.sub（二级分类，varchar(16) NULL）。
-- 背景：端上「提个想法」页的「想法/问题」二选一此前仅在请求中出现、未落库（假字段），
--       现补全落库，仅 type=suggestion 有效，值域 idea/problem（FeedbackConst.SUB_WRITE_WHITELIST）。
-- 新库 CREATE TABLE 已含该列；旧库幂等补齐（MySQL 不支持 ADD COLUMN IF NOT EXISTS，
-- 用存储过程防护，与上方迁移惯例一致），列定义与 CREATE 保持一致、可重跑、不影响既有数据
-- （存量行该列为 NULL，管理端按「未分类」展示）。
DROP PROCEDURE IF EXISTS `add_feedback_sub`;
DELIMITER $$
CREATE PROCEDURE `add_feedback_sub`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_feedback' AND COLUMN_NAME = 'sub'
    ) THEN
        ALTER TABLE `user_feedback`
            ADD COLUMN `sub` VARCHAR(16) NULL DEFAULT NULL COMMENT '二级分类（仅 type=suggestion 有效）：idea=想法/problem=问题；其他类型为 NULL';
    END IF;
END$$
DELIMITER ;
CALL `add_feedback_sub`();
DROP PROCEDURE IF EXISTS `add_feedback_sub`;

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

-- 字段下线（2026-09-14 §7.14 D 用户拍板）：
--   stall.business_hours（营业时间）——用户明确「不需要营业时间」，端上零消费（无展示/无读取），
--   实体（Stall）/VO（StallDetailVO、StallAdminVO、DishVO）与 Mapper 映射同批移除。
--   CREATE TABLE 已同步移除该列定义；旧库在此幂等 DROP，重复执行安全（先判存在再 DROP），不影响既有数据。
--   注意：同批保留 stall.floor（楼层）与 stall.window_no（窗口号）——端上有消费（档口卡展示位置）。
DROP PROCEDURE IF EXISTS `drop_stall_business_hours`;
DELIMITER $$
CREATE PROCEDURE `drop_stall_business_hours`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stall' AND COLUMN_NAME = 'business_hours'
    ) THEN
        ALTER TABLE `stall` DROP COLUMN `business_hours`;
    END IF;
END$$
DELIMITER ;
CALL `drop_stall_business_hours`();
DROP PROCEDURE IF EXISTS `drop_stall_business_hours`;

-- 字段下线（2026-09-14 §7.14 · Q-114 用户拍板）：
--   dish.portion（分量）——用户拍板「彻底下线」，连后台录入一并移除（PR-07：字段引入与下线成对处置）。
--   实体（Dish）/DTO（DishAdminReq）/VO（DishVO、DishAdminVO、DishDetailVO）/Mapper XML 列映射与查询列
--   已同批移除，后台录入写入与值域校验亦删除；CREATE TABLE 已同步移除该列定义。
--   旧库在此幂等 DROP，重复执行安全（先判存在再 DROP），不影响既有数据。
--   注意：同批保留 dish.spice_level（辣度）——用户拍板要保留的维度，端上有消费。
DROP PROCEDURE IF EXISTS `drop_dish_portion`;
DELIMITER $$
CREATE PROCEDURE `drop_dish_portion`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'portion'
    ) THEN
        ALTER TABLE `dish` DROP COLUMN `portion`;
    END IF;
END$$
DELIMITER ;
CALL `drop_dish_portion`();
DROP PROCEDURE IF EXISTS `drop_dish_portion`;

-- 字段下线（2026-09-14 §7.14 用户拍板 + 2026-09-15 阶段4 收敛）：
--   食堂/档口已去实体化，降级为「菜品筛选属性字典」（生命周期仅新增/改名，无停业、无营业时间、无实体审核），
--   故其 status（停业语义）/ audit_status（实体审核语义）/ reject_reason（退回原因）共 6 列整体下线；
--   阶段4 追加：created_by（归属语义）同批退役，理由为「无归属语义 + 写侧恒系统占位值 + 三端零消费」。
--   合计 8 列：
--     canteen.status / canteen.audit_status / canteen.reject_reason / canteen.created_by
--     stall.status   / stall.audit_status   / stall.reject_reason   / stall.created_by
--   实体（Canteen/Stall）、VO 与 Service 读写已同批移除，Mapper 侧无任何引用；CREATE TABLE 已同步移除列定义。
--   旧库在此幂等 DROP，重复执行安全（先判存在再 DROP），不影响既有数据。
--   注意：保留 canteen.name / stall.name / stall.floor / stall.window_no，以及新增/改名/列表查询能力；
--         菜品 dish 的 dish.status / dish.reject_reason / dish.created_by **保留**（上下架判据与历史留痕），
--         dish.audit_status 由文件末尾 drop_dish_audit_status_column 段单独 DROP；
--         本段只处理 canteen / stall 两表。
DROP PROCEDURE IF EXISTS `drop_canteen_stall_entity_fields`;
DELIMITER $$
CREATE PROCEDURE `drop_canteen_stall_entity_fields`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'canteen' AND COLUMN_NAME = 'status'
    ) THEN
        ALTER TABLE `canteen` DROP COLUMN `status`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'canteen' AND COLUMN_NAME = 'audit_status'
    ) THEN
        ALTER TABLE `canteen` DROP COLUMN `audit_status`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'canteen' AND COLUMN_NAME = 'reject_reason'
    ) THEN
        ALTER TABLE `canteen` DROP COLUMN `reject_reason`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stall' AND COLUMN_NAME = 'status'
    ) THEN
        ALTER TABLE `stall` DROP COLUMN `status`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stall' AND COLUMN_NAME = 'audit_status'
    ) THEN
        ALTER TABLE `stall` DROP COLUMN `audit_status`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stall' AND COLUMN_NAME = 'reject_reason'
    ) THEN
        ALTER TABLE `stall` DROP COLUMN `reject_reason`;
    END IF;

    -- 阶段4（2026-09-15）：created_by 归属列退役（canteen 在前、stall 在后，与上方同表分组顺序一致）
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'canteen' AND COLUMN_NAME = 'created_by'
    ) THEN
        ALTER TABLE `canteen` DROP COLUMN `created_by`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stall' AND COLUMN_NAME = 'created_by'
    ) THEN
        ALTER TABLE `stall` DROP COLUMN `created_by`;
    END IF;
END$$
DELIMITER ;
CALL `drop_canteen_stall_entity_fields`();
DROP PROCEDURE IF EXISTS `drop_canteen_stall_entity_fields`;

-- 字段下线（2026-09-15 阶段4 用户批准「归一后清理」）：
--   dish.audit_status 全量退役——菜品无独立审核：管理员录入 / 编辑即生效，
--   公开可见性唯一判据为上架状态 dish.status='on'（DishMapper.xml 过滤条件已同步移除）。
--   列与相关索引（idx_dish_audit 单列索引、idx_dish_heat 中的该列）同批移除；
--   ATTENTION：DROP COLUMN 会连带删除 idx_dish_audit（其唯一成员列），
--   idx_dish_heat 自动退化为 (status, view_count, rating_count, avg_rating)（与 CREATE TABLE 定义一致），无需重建。
--   执行前置（由运维/用户执行，不由 agent 代跑）：本列退役前须先跑历史一次性归一脚本
--   （normalize_dish_audit_status.sql，2026-09-15 已随本列退役一并删除）以消除存量
--   pending/rejected 行；本段幂等，重复执行安全（先判存在再 DROP），不影响既有数据。
--   dish.reject_reason / dish.created_by 不在本段范围内（保留）。
DROP PROCEDURE IF EXISTS `drop_dish_audit_status_column`;
DELIMITER $$
CREATE PROCEDURE `drop_dish_audit_status_column`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'audit_status'
    ) THEN
        ALTER TABLE `dish` DROP COLUMN `audit_status`;
    END IF;
END$$
DELIMITER ;
CALL `drop_dish_audit_status_column`();
DROP PROCEDURE IF EXISTS `drop_dish_audit_status_column`;

-- 字段/表下线（2026-09-15 用户拍板「品类整链删除」）：
--   dish.category_id 列（含单列索引 idx_dish_category）与整张 category 表同批退役。
--   背景：品类是「端上零呈现、仅 Web 自用的不可见第三维度」，维护成本高于收益；
--   后端同批移除 CategoryAdminController / CategoryService(+Impl) / CategoryMapper / Category
--   与 dish 侧 categoryId 字段（实体/DTO/VO）及 DishMapper.xml 的列映射与查询列。
--   顺序：先 DROP dish.category_id（DROP COLUMN 连带删除 idx_dish_category——其唯一成员列，
--   无需单独 DROP INDEX），再 DROP TABLE category（无外键约束，先列后表保证重复执行安全）。
--   本段幂等（先判存在再操作），重复执行安全、不影响既有数据。
DROP PROCEDURE IF EXISTS `drop_category_chain`;
DELIMITER $$
CREATE PROCEDURE `drop_category_chain`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'category_id'
    ) THEN
        ALTER TABLE `dish` DROP COLUMN `category_id`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'category'
    ) THEN
        DROP TABLE `category`;
    END IF;
END$$
DELIMITER ;
CALL `drop_category_chain`();
DROP PROCEDURE IF EXISTS `drop_category_chain`;

-- 字段下线（2026-09-15 用户拍板「取消人工复核，sec_state 全链退役」）：
--   review.sec_state 与 user_feedback.sec_state 同批退役。
--   背景：机检 pass/review 一律直接放行、仅 risky 拒绝（不落库），不再有「待人工复核」语义，
--   该列恒为单值、属死重；后端同批移除实体字段（Review/Feedback）、VO 字段
--   （ReviewVO/ReviewAdminVO/FeedbackAdminVO）、Mapper 过滤条件与查询列、SecStateConst、
--   管理端复核端点 PUT /admin/reviews/{id}/sec-state 及其 Service 方法、
--   OperationLogConst.ACTION_REVIEW_SEC_STATE，列表查询的 secState 过滤入参一并删除。
--   注意：is_hidden 与事后处置能力（PUT /admin/reviews/{id}/hide、DELETE /admin/reviews/{id}、
--         DELETE /reviews/{id}）**保留**——举报→下架通道不受影响。
--   本段幂等（先判存在再 DROP），可重跑、不影响既有数据（列无索引，DROP COLUMN 无连带对象）。
--   历史一次性数据修正脚本 fix_rating_by_sec_state.sql 已随本列退役一并删除（口径不再引用该列）。
DROP PROCEDURE IF EXISTS `drop_sec_state_columns`;
DELIMITER $$
CREATE PROCEDURE `drop_sec_state_columns`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND COLUMN_NAME = 'sec_state'
    ) THEN
        ALTER TABLE `review` DROP COLUMN `sec_state`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_feedback' AND COLUMN_NAME = 'sec_state'
    ) THEN
        ALTER TABLE `user_feedback` DROP COLUMN `sec_state`;
    END IF;
END$$
DELIMITER ;
CALL `drop_sec_state_columns`();
DROP PROCEDURE IF EXISTS `drop_sec_state_columns`;

SET FOREIGN_KEY_CHECKS = 1;
