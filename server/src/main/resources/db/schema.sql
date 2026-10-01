-- =============================================================
-- 食在交大 建立数据库（建表）脚本（MySQL 8）
-- =============================================================
-- 用途：从零创建数据库与全部表结构、最终字段（含描述属性 dish.attributes JSON 与属性字典两表等）。
-- 本脚本自包含：自动建库并切换 USE bjtu_food，不依赖工具/命令行预先选中库。
-- 重置服务器：先 DROP DATABASE bjtu_food 再执行本文件即可还原表结构（或直接执行本文件覆盖）。
-- 配合 seed_data.sql 使用：本文件只建表不插数据。
--
-- 说明：
--   1. 角色体系已整体退役（2026-09-15 用户拍板「user.role 收敛为恒 student，删列」）——
--      管理端无登录与角色体系，/admin/** 统一由环境变量口令（AdminTokenFilter，X-Admin-Token == ADMIN_TOKEN）把关；
--      小程序端全量用户即学生，JWT 不再携带 role claim，JwtAuthFilter 固定授学生态 authorities。
--      user.role 列已从 CREATE TABLE 移除（原默认值 'student'），存量库由文件末尾
--      drop_user_redundant_columns 幂等段清理（同段一并退役 user.last_login_at 只写不读列）。
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
--        · dish.reject_reason / dish.created_by 已于 2026-09-16 用户拍板「零消费即删除」退役
--          （由文件末尾 drop_zero_consumer_columns 幂等段清理）；review / user_feedback 的审核类列见第 5 条。
--   5. UGC 内容安全（2026-09-13 产品定稿 + 2026-09-15 用户拍板「取消人工复核」）：
--      review / user_feedback 支持配图（images JSON，配图经 COS 转存后以 COS 绝对 URL 存库）；
--      内容安全内容安全检测结果（sec_state：pass/review/rejected）**已全链退役**——内容安全检测 pass/review 一律放行、
--      risky 直接拒绝（不入库），无人工复核队列，故 CREATE TABLE 不再创建该列，
--      存量库由文件末尾 drop_sec_state_columns 幂等段清理（可重跑）。
--   6. 菜品品类整链退役（2026-09-15 用户拍板）：category 表与 dish.category_id 列（含单列索引
--      idx_dish_category）不再创建（端上零呈现、仅 Web 自用的不可见第三维度）；
--      存量库由文件末尾 drop_category_chain 幂等段清理（可重跑）。
--   7. 操作日志整链退役（2026-09-15 用户拍板「管理端不需要操作日志，相关链路全部删除」）：
--      operation_log 表不再创建（CREATE TABLE 段已删除），存量库由文件末尾
--      drop_operation_log_table 幂等段清理（可重跑）；后端同批删除 AuditLog 注解 /
--      AuditLogAspect 切面 / OperationLogConst / OperationLogAdminController / OperationLogVO /
--      OperationLog 实体 / OperationLogMapper / OperationLogService(+Impl) 与 4 处调用点。
--      **数据表基线 11 → 10**（品类表下线后基线 11，本次再收敛为 10）。
--   8. 菜品详情模块整改（2026-09-20 用户拍板，dish-detail-remediation）：
--      · 价格口径——promo_price（促销价）列整链下线，price 为唯一价格数据源（现价，已含折扣），
--        original_price 为可空原价（判据 original_price > price）；存量迁移见文件末尾
--        migrate_dish_promo_to_price（先 UPDATE 再 DROP，幂等）。
--      · 标签下线——dish.tags 列整链删除（含标签筛选与展示）。
--      · 描述属性动态化——ADD dish.attributes JSON 并回填旧描述列，DROP diet_type/ingredients/flavor_tags/serve_temp
--        （属性模型改为两表字典驱动：dish_attribute_dimension + dish_attribute_value）。
--      · 坐标下线——canteen.latitude/longitude 两列幂等 DROP；原 add_canteen_location 迁移存储过程删除。
--      · 「有用」全链下线——review.useful_count 列与 review_useful 表幂等 DROP，**数据表基线 10 → 9**。
--      上述清理统一由文件末尾「菜品详情模块整改」幂等段完成（禁止直连 ALTER，可重跑）。
-- =============================================================

-- 自包含建库选库：避免在未选中库时建表语句落入默认库（如 mysql 系统库）触发 1044 权限错误
CREATE DATABASE IF NOT EXISTS `bjtu_food` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `bjtu_food`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------- 用户 --------------------
-- 认证模型（2026-08 微信登录体系，spec §5.y）：
--   · 微信自动静默登录为游客态（bind_email 为 NULL），openid 为登录取号依据（唯一）。
--   · @bjtu.edu.cn 邮箱验证码认证（purpose=verify）→ 写 bind_email（**认证态唯一真源**：非空即已认证），
--     解锁 UGC 写操作。
--   · user.verified / user.verified_at 已于 2026-09-22 用户拍板退役（与 bind_email 同源冗余：三列表达同一事实，
--     历史写入路径「认证 / 释放绑定替换 / 注销」恒成对写，无独立语义）：CREATE TABLE 不再创建，
--     存量库由下方 drop_verified_columns 幂等段清理（先建后删无意义，故 add_user_wechat_auth 段亦不补齐）。
--   · username 语义：游客建号 'wx_'+openid 尾 16 位；旧邮箱注册用户保留学号。
--   · email 列保留作为历史迁移凭证。
--   · user.password 已于 2026-09-16 用户拍板「零消费即删除」退役：三端零读（唯一写点=注销置 NULL），
--     管理端为环境变量口令 ADMIN_TOKEN、学生端为微信静默登录 + 邮箱验证码，均无密码体系；
--     BCrypt 仅用于邮箱验证码哈希（email_verification_code.code_hash），不用于任何登录口令校验。
--   · user.unionid 同批退役：只写不读（同主体多应用预留撤销，用户拍板「零消费即删除」）。
--     两列存量库由文件末尾 drop_zero_consumer_columns 幂等段清理（可重跑）。
CREATE TABLE IF NOT EXISTS `user`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '学号/工号（游客建号为 wx_+openid 尾 16 位，唯一）',
    `email`        VARCHAR(128) NULL    DEFAULT NULL COMMENT '校园邮箱（历史迁移凭证；微信游客为 NULL，多游客 NULL 不冲突唯一索引 uk_user_email）',
    `nickname`     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '昵称',
    `avatar`       VARCHAR(512) NULL     DEFAULT NULL COMMENT '头像URL',
    -- user.role / user.last_login_at 已于 2026-09-15 用户拍板退役（role 恒 student、last_login_at 只写不读零消费）；
    -- user.password / user.unionid 已于 2026-09-16 用户拍板退役（零读 / 只写不读）；
    -- CREATE TABLE 均不再创建，存量库由文件末尾幂等 DROP 段清理。
    `status`       VARCHAR(32)  NOT NULL DEFAULT 'active' COMMENT '状态：active / disabled / deleted',
    `openid`       VARCHAR(64)  NULL     DEFAULT NULL COMMENT '微信 openid（静默登录取号依据，唯一；仅微信游客/已认证账号有值，历史学号账号为 NULL）',
    `bind_email`   VARCHAR(128) NULL     DEFAULT NULL COMMENT '已认证绑定邮箱（仅存认证关系，可空；非空即已认证 = 认证状态唯一真源）',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
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
    -- canteen.latitude / canteen.longitude 已于 2026-09-20 拍板全链下线（位置表达收敛为 食堂 · 楼层 · 档口名，
    -- 端上不申请定位权限、不算距离）；CREATE TABLE 不再创建，存量库由文件末尾 drop_canteen_coordinates 幂等段清理。
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
    -- dish.alias（搜索别名）已于 2026-09-22 用户拍板「菜品无需昵称」退役：
    -- CREATE TABLE 不再创建；存量库由文件末尾 drop_dish_alias_column 幂等段清理（可重跑）。
    -- 同批：Dish / DishAdminReq / DishAdminVO 去字段、DishMapper.xml 去别名匹配路与列映射（change search-page-refresh）。
    `price`          INT          NOT NULL DEFAULT 0 COMMENT '现价（单位：分，已含折扣；唯一价格数据源）',
    `original_price` INT          NULL     DEFAULT NULL COMMENT '原价（单位：分，可空）；original_price > price 视为有折扣',
    `description`    VARCHAR(512) NULL     DEFAULT NULL COMMENT '菜品描述',
    `images`         VARCHAR(1024) NULL    DEFAULT NULL COMMENT '菜品多图JSON',
    -- 描述属性（动态属性模型，方案 A：值即中文）：JSON 对象，键 = 维度 field_key（camelCase：dietType/ingredients/flavorTags/serveTemp），
    -- 值 = **中文文本**（single 维度为字符串 / multi 维度为字符串数组）；仅含该菜实际拥有的维度。
    -- 维度真源 = dish_attribute_dimension（表驱动，新增维度免 ALTER、免发版）；
    -- **无取值字典表**——取值就是中文本身，编辑候选由「全库已用值」去重得出（旧 dish_attribute_value 已退役）。
    -- 存量四维列（diet_type/ingredients/flavor_tags/serve_temp）由文件末尾 migrate_dish_attributes_json 幂等段回填后 DROP。
    `attributes`     JSON         NULL     DEFAULT NULL COMMENT '描述属性（JSON：键=维度 field_key，值=中文文本/数组）',
    `status`         VARCHAR(32)  NOT NULL DEFAULT 'on' COMMENT '上架状态：on / off',
    -- dish.reject_reason（恒 NULL，审核语义退役）与 dish.created_by（只写不读留痕）
    -- 已于 2026-09-16 用户拍板「零消费即删除」退役：CREATE TABLE 不再创建，
    -- 存量库由文件末尾 drop_zero_consumer_columns 幂等段清理。
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

-- -------------------- 菜品描述属性（动态属性模型，单表维度，值即中文） --------------------
-- 维度由表驱动（免发版增维度）；维度键 `field_key` 恒等于 `dish.attributes` JSON 的键（camelCase）。
-- **无取值字典表**：取值就是中文文本本身；编辑候选由「全库已用值」去重得出，加值零登记。
-- 旧「取值字典」dish_attribute_value（机器值 → 中文）由文件末尾 `migrate_attribute_values_to_text`
-- 幂等段迁移 dish.attributes / dish_correction.attributes 后 DROP（方案 A：值即中文）。
CREATE TABLE IF NOT EXISTS `dish_attribute_dimension`
(
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '维度ID',
    `field_key`  VARCHAR(32) NOT NULL COMMENT '维度键（= 菜品 attributes 的键，camelCase）',
    `name`       VARCHAR(32) NOT NULL DEFAULT '' COMMENT '维度中文名（饮食属性/食材/口味/冷热）',
    `value_type` VARCHAR(16) NOT NULL DEFAULT 'single' COMMENT '取值类型：single=单值 / multi=多值',
    `order`      INT         NOT NULL DEFAULT 0 COMMENT '维度展示顺序（升序）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dimension_field_key` (`field_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='菜品描述属性维度';

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
    -- review.updated_at 已于 2026-09-23 用户拍板退役（§7.40 R6 / change dish-detail-contract-hardening）：
    -- 重评时与 created_at **同批刷新** → 两者恒等，该列对评价无独立语义，且端上与管理端双双零消费
    -- （web/src/views 对 updated_at 零命中）；Review 实体字段同批移除。
    -- 存量库由文件末尾 drop_review_updated_at 幂等段清理。
    -- ⚠️ dish.updated_at **保留**（DishMapper 排序 + DishFormDialog 的 Q-112「他人已修改」轻提示依赖）。
    PRIMARY KEY (`id`),
    -- 2026-09-29 性能修正：列表查询为「dish_id/user_id + is_hidden 过滤 + created_at 倒序」，
    -- 复合索引把过滤列与排序列一次覆盖，消除 filesort；两者前缀已取代原单列 idx_review_dish / idx_review_user。
    KEY `idx_review_dish_visible` (`dish_id`, `is_hidden`, `created_at`),
    KEY `idx_review_user_visible` (`user_id`, `is_hidden`, `created_at`),
    UNIQUE KEY `uk_review_user_dish` (`user_id`, `dish_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='评价';

-- -------------------- 评价「有用」：已整链退役（2026-09-20 拍板） --------------------
-- review_useful 表与 review.useful_count 列、投票端点、评价响应「有用」字段全部删除。
-- 新库：本脚本不再创建该表与该列；存量库：由文件末尾 drop_review_useful_chain 幂等段清理（表基线 10 → 9）。

-- -------------------- 收藏（本期整体移除，见 task-12.12） --------------------
-- /favorites 端点与 favorite 表本期彻底删除。产品定稿：不做「收藏/喜欢」功能，
-- 评价互动中的「有用」亦已于 2026-09-20 整链下线，故不另建任何评价点赞/计数存储。

-- -------------------- 消息通知（账号注销级联清理依赖，A.15） --------------------
CREATE TABLE IF NOT EXISTS `notification`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    `user_id`    BIGINT       NOT NULL DEFAULT 0 COMMENT '接收用户ID',
    `type`       VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '通知类型：feedback_handle=反馈处理回执 / correction_handle=菜品信息纠错回执',
    `title`      VARCHAR(128) NOT NULL DEFAULT '' COMMENT '通知标题',
    `content`    VARCHAR(512) NULL     DEFAULT NULL COMMENT '通知正文',
    `related_id` BIGINT       NULL     DEFAULT NULL COMMENT '关联对象ID（菜品/反馈ID，按 type 解释）',
    `is_read`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读：0=未读 1=已读',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 2026-09-29 性能修正：列表按 (user_id, created_at 倒序)、未读数按 (user_id, is_read) 过滤；
    -- 复合索引前缀已取代原单列 idx_notification_user。
    KEY `idx_notification_user_created` (`user_id`, `created_at`),
    KEY `idx_notification_user_read` (`user_id`, `is_read`)
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
    -- user_feedback.contact 已于 2026-09-16 用户拍板退役（产品定型「不收集联系方式」）：
    -- FeedbackReq.contact / 实体字段 / 落库逻辑同批删除；
    -- user_feedback.handler_id 同批退役（§7.10 操作人身份降级为单口令后一直未写、读侧恒 NULL）。
    -- 两列存量库由文件末尾 drop_zero_consumer_columns 幂等段清理。
    `status`       VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '处理状态：pending/handled',
    `reply`        VARCHAR(1024) NULL    DEFAULT NULL COMMENT '管理员回复',
    `reject_reason` VARCHAR(200) NULL    DEFAULT NULL COMMENT '不采纳/退回原因（outcome=rejected 时必填）',
    `related_type` VARCHAR(32)   NULL    DEFAULT NULL COMMENT '关联类型：举报为 review；信息纠错为 dish；其他为 null',
    `related_id`   BIGINT        NULL    DEFAULT NULL COMMENT '关联对象ID：举报为评价ID；信息纠错为菜品ID；其他为 null',
    `handled_at`   DATETIME      NULL    DEFAULT NULL COMMENT '处理时间',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_feedback_user` (`user_id`),
    -- 2026-09-29 性能修正：管理端按 status 筛选 + created_at 倒序列表
    KEY `idx_feedback_status_created` (`status`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='用户反馈';

-- -------------------- 菜品信息纠错 --------------------
-- 独立资源（区别于 user_feedback 意见反馈）：用户在菜品详情页提交的**改动项快照**（局部提交 patch），
-- 未改动的列留 NULL（采纳时不覆盖 dish 既有值），管理端采纳后写回 dish（两段式档口确认），拒绝则留存不采纳原因。
CREATE TABLE IF NOT EXISTS `dish_correction`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '纠错ID',
    `dish_id`       BIGINT       NOT NULL COMMENT '目标菜品ID（逻辑关联 dish，不设外键，与项目现状一致）',
    `user_id`       BIGINT       NULL DEFAULT NULL COMMENT '提交人用户ID（匿名提交为 NULL）',
    `name`          VARCHAR(64)  NULL DEFAULT NULL COMMENT '提交的菜品名称（未改动为 NULL）',
    `price`         INT          NULL DEFAULT NULL COMMENT '提交的现价（单位：分；未改动为 NULL）',
    `canteen_name`  VARCHAR(64)  NULL DEFAULT NULL COMMENT '提交的食堂名称（自由文本，无字典端点；未改动为 NULL）',
    `stall_name`    VARCHAR(64)  NULL DEFAULT NULL COMMENT '提交的档口名称（自由文本，采纳时两段式确认归档；未改动为 NULL）',
    `floor`         VARCHAR(16)  NULL DEFAULT NULL COMMENT '提交的楼层（自由文本；未改动为 NULL；采纳时写回所属档口 stall.floor）',
    `attributes`    JSON         NULL DEFAULT NULL COMMENT '提交的描述属性（JSON：键=维度 field_key，值=中文文本/数组；仅含改动维度）',
    `images`        JSON         NULL DEFAULT NULL COMMENT '提交的菜品图片URL列表（JSON 数组，COS 绝对地址，≤3 张）',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT '处理状态：pending/adopted/rejected',
    `reply`         VARCHAR(512) NULL DEFAULT NULL COMMENT '处理回复（采纳时固定「已采纳，菜品信息已更新」）',
    `reject_reason` VARCHAR(200) NULL DEFAULT NULL COMMENT '不采纳原因（status=rejected 时必填，1~200 字）',
    `handled_at`    DATETIME     NULL DEFAULT NULL COMMENT '处理时间',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_correction_dish` (`dish_id`),
    -- 2026-09-29 性能修正：管理端按 status 筛选 + created_at 倒序列表；前缀已取代原单列 idx_correction_status
    KEY `idx_correction_status_created` (`status`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='菜品信息纠错';

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

-- 菜品：原价（task-12.9；CREATE TABLE 已含，列定义以 CREATE 为准：original_price 允许 NULL；旧库幂等补齐）
-- 注1：供应时段 serve_period 与限量 limited 已于 2026-09-14 整体下线（见文件末尾 drop_dish_unused_fields 迁移）。
-- 注2：分量 portion 已于 2026-09-14 §7.14（Q-114）整体下线，由文件末尾 drop_dish_portion 幂等清理。
-- 注3（2026-09-20 拍板）：promo_price（促销价）整链下线——price 为唯一价格数据源（现价，已含折扣），
--      original_price 为可空原价；新库 CREATE TABLE 不再创建 promo_price，旧库由文件末尾
--      migrate_dish_promo_to_price 幂等段先迁移数据再 DROP。故本段不再 ADD promo_price。
-- 注4（2026-09-20 拍板）：spice_level（辣度）与 region（风味/菜系）已被四维替换——
--      新库 CREATE TABLE 不再创建，旧库由文件末尾 drop_dish_description_dimensions 幂等段清理；故本段不再 ADD。
DROP PROCEDURE IF EXISTS `add_dish_promo_fields`;
DELIMITER $$
CREATE PROCEDURE `add_dish_promo_fields`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'original_price'
    ) THEN
        ALTER TABLE `dish` ADD COLUMN `original_price` INT NULL DEFAULT NULL COMMENT '原价（单位：分，可空）；original_price > price 视为有折扣';
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

-- 访问日志 view_log：**整链退役**（只写不读的行为数据 → 按数据最小化原则停止采集）——
-- 本文件不再创建该表；实体 / Mapper / 写入点（详情计数日志、注销清理、归属迁移、菜品级联）同批移除；
-- 浏览量由 dish.view_count 独立承载（GET /dishes/{id} 成功路径原子自增）。
-- 存量库由文件末尾 drop_view_log_table 幂等段清理。

-- 首页顶部轮播图 banner（2026-09-22 新增，change「首页 Banner 接口化」）：
--   · status 取 on / off（与 dish.status 同风格；**不复活**已随下线删除的 enabled/disabled 枚举），
--     服务端按 status='on' 过滤，该列不出参；
--   · sort_order 为展示顺序（升序），服务端排序用、不出参；
--   · image_url 与菜品图片同口径（库内可存相对路径，出参经 ImageUrlUtil 转绝对 URL），素材统一 16:10；
--   · 本期仅只读（GET /banners），无管理端写入口，素材由 seed_data.sql 维护。
-- CREATE TABLE IF NOT EXISTS 本身幂等，存量库重复执行安全（无需存储过程防护）。
CREATE TABLE IF NOT EXISTS `banner`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Banner ID',
    `image_url`  VARCHAR(500) NOT NULL DEFAULT '' COMMENT '轮播图URL（16:10 素材；可存相对路径，出参转绝对URL）',
    `sort_order` INT          NOT NULL DEFAULT 0 COMMENT '展示顺序（升序，数字越小越靠前）',
    `status`     VARCHAR(10)  NOT NULL DEFAULT 'on' COMMENT '状态：on=启用 / off=停用（服务端过滤用，不出参）',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 公开查询唯一条件 + 排序：WHERE status='on' ORDER BY sort_order ASC
    KEY `idx_banner_status_sort` (`status`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='首页顶部轮播图（公开只读，本期无管理端入口）';

-- 操作日志 operation_log：**整链退役**（2026-09-15 用户拍板「管理端不需要操作日志」）——
-- 原 CREATE TABLE 段（含 idx_op_admin_time / idx_op_target 两索引）已删除，本文件不再创建该表；
-- 存量库由文件末尾 drop_operation_log_table 幂等段清理（详见文件头说明第 7 条）。

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
-- 旧库（尚无 openid/bind_email）补齐列与唯一索引，不破坏既有数据。
-- 注：unionid 不再补齐——该列已于 2026-09-16 零消费退役，由末尾 drop_zero_consumer_columns 段 DROP；
--     verified / verified_at 已于 2026-09-22 退役，本段不再补齐（补齐后即被 drop_verified_columns 删掉）。
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
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'bind_email'
    ) THEN
        ALTER TABLE `user`
            ADD COLUMN `bind_email` VARCHAR(128) NULL DEFAULT NULL COMMENT '已认证绑定邮箱（仅存认证关系，可空）';
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

-- 菜品搜索别名（2026-09-13 引入 → 2026-09-22 退役）：
-- 原 add_dish_alias 迁移段已整段删除——菜品无搜索别名（用户拍板「菜品无需昵称」），
-- CREATE TABLE 不再创建该列，存量库由文件末尾 drop_dish_alias_column 幂等段清理
-- （先建后删无意义，与 add_canteen_location 整段删除的既有惯例一致）。

-- 字段下线（2026-09-14 §7.9 用户拍板）：
--   serve_period（餐段）与 limited（限量）在端上/后台/代码中均为零消费，整体下线。
-- 幂等：先做存在性判断再 DROP，重复执行安全；两列无任何代码/数据引用。
-- 注（2026-09-20 拍板）：原本段的 region 列注释放宽逻辑已删除——region 已随四维替换整链下线，
--   由文件末尾 drop_dish_description_dimensions 幂等段 DROP。
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
END$$
DELIMITER ;
CALL `drop_dish_unused_fields`();
DROP PROCEDURE IF EXISTS `drop_dish_unused_fields`;

-- 字段下线（2026-09-14 §7.10 用户拍板）：
--   review.tags（评价标签）随「美团式写评」确认不做而下线：
--   写入侧无任何入口（ReviewReq / 小程序端均无该字段），读取侧实体/VO 零引用（已复核），
--   属纯零消费列。CREATE TABLE 已同步移除该列定义；旧库在此幂等 DROP，保证重复执行安全、不影响既有数据。
--   同批 §7.10 曾决定保留的 retired 列 user_feedback.handler_id（管理端操作人身份降级：
--     单口令即单人，handle 不再写入），已于 2026-09-16 用户拍板「零消费即删除」随
--     drop_zero_consumer_columns 幂等段退役删除；
--     另一列原为操作日志 admin_id，已随整张操作日志表退役（2026-09-15，见文件末尾
--     drop_operation_log_table 幂等段），无需再处理。
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
--   实体（Stall）/VO（StallDetailVO、StallAdminVO、公开菜品 VO）与 Mapper 映射同批移除。
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
--   实体（Dish）/DTO（DishAdminReq）/VO（公开菜品 VO、DishAdminVO、DishDetailVO）/Mapper XML 列映射与查询列
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
--         菜品 dish 的 dish.status **保留**（上下架判据）；
--         dish.audit_status 由文件末尾 drop_dish_audit_status_column 段单独 DROP；
--         dish.reject_reason / dish.created_by 已由 2026-09-16 drop_zero_consumer_columns 段退役；
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
--   dish.reject_reason / dish.created_by 不在本段范围内（已由 2026-09-16 drop_zero_consumer_columns 段退役）。
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
--   背景：内容安全检测 pass/review 一律直接放行、仅 risky 拒绝（不落库），不再有「待人工复核」语义，
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

-- 表下线（2026-09-15 用户拍板「管理端不需要操作日志，相关链路全部删除」）：
--   操作日志表整体退役——CREATE TABLE 段已从本文件删除，后端同批删除 AuditLog 注解 /
--   AuditLogAspect 切面 / OperationLogConst / OperationLogAdminController / OperationLogVO /
--   OperationLog 实体 / OperationLogMapper / OperationLogService(+Impl)，并清理 4 处调用点
--   （AuthController / DishAdminController / FeedbackAdminController / ReviewAdminController）。
--   **数据表基线 11 → 10**（品类表下线后基线 11，本次再收敛为 10）；seed_data.sql 自始未灌入
--   该表种子数据，无需清理。
--   顺序：该表无外键约束、无其他表以逻辑外键引用，直接 DROP TABLE 即可；
--   本段幂等（先查 INFORMATION_SCHEMA.TABLES 判存在再 DROP，与上方 drop_category_chain 惯例一致），
--   重复执行安全、不影响既有数据。
DROP PROCEDURE IF EXISTS `drop_operation_log_table`;
DELIMITER $$
CREATE PROCEDURE `drop_operation_log_table`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log'
    ) THEN
        DROP TABLE `operation_log`;
    END IF;
END$$
DELIMITER ;
CALL `drop_operation_log_table`();
DROP PROCEDURE IF EXISTS `drop_operation_log_table`;

-- 字段下线（2026-09-15 用户拍板）：user 两列退役——
--   · user.role：收敛为恒 student（全量用户即学生，管理端走口令体系），列与角色语义一并退役；
--   · user.last_login_at：只写不读、零消费。
-- CREATE TABLE 已同步移除两列定义；实体/Service 写入点同批移除；存量库在此幂等 DROP
-- （先判 INFORMATION_SCHEMA.COLUMNS 存在再 DROP COLUMN，可重跑；两列均无索引成员，无连带对象）。
DROP PROCEDURE IF EXISTS `drop_user_redundant_columns`;
DELIMITER $$
CREATE PROCEDURE `drop_user_redundant_columns`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'role'
    ) THEN
        ALTER TABLE `user` DROP COLUMN `role`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'last_login_at'
    ) THEN
        ALTER TABLE `user` DROP COLUMN `last_login_at`;
    END IF;
END$$
DELIMITER ;
CALL `drop_user_redundant_columns`();
DROP PROCEDURE IF EXISTS `drop_user_redundant_columns`;

-- 字段下线（2026-09-22 用户拍板 A 方案）：user 两列退役——
--   · user.verified    —— 与 bind_email 同源冗余：同一事实的布尔镜像，历史写入路径
--                         「邮箱认证 / 释放绑定替换 / 注销」三处恒成对写，无任何独立语义；
--   · user.verified_at —— 只写不读（三端零读取，仅认证事务内写入一次）。
-- 判据统一为「bind_email 非空」（服务端 AuthStateUtil 唯一真源；端上 bindEmail != null）。
-- CREATE TABLE 已同步移除两列定义，实体 / UserInfoVO / UserVO / Service 写入点同批移除；
-- 存量库在此幂等 DROP（先判 INFORMATION_SCHEMA.COLUMNS 存在再 DROP COLUMN，可重跑；
-- 两列均无索引成员，无连带对象），不影响既有数据。
DROP PROCEDURE IF EXISTS `drop_verified_columns`;
DELIMITER $$
CREATE PROCEDURE `drop_verified_columns`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'verified'
    ) THEN
        ALTER TABLE `user` DROP COLUMN `verified`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'verified_at'
    ) THEN
        ALTER TABLE `user` DROP COLUMN `verified_at`;
    END IF;
END$$
DELIMITER ;
CALL `drop_verified_columns`();
DROP PROCEDURE IF EXISTS `drop_verified_columns`;

-- 字段下线（2026-09-16 用户拍板「数据库重设计：零消费列全部删除，满足 BCNF」）：
--   共 6 列，逐列三端 grep 复核零消费后退役：
--   · user.password          —— 零读（唯一写点=注销置 NULL，AuthService 已同步删除该置空逻辑）；
--   · user.unionid           —— 只写不读（同主体多应用预留撤销，微信登录仅消费 openid）；
--   · dish.reject_reason     —— 恒 NULL（审核语义退役后无写入入口）；
--   · dish.created_by        —— 只写不读（upsert 留痕撤销；migrateOwnership 的归属迁移同批删除）；
--   · user_feedback.handler_id —— 退役不写（§7.10 操作人身份降级后读侧恒 NULL）；
--   · user_feedback.contact  —— 产品定型「不收集联系方式」（FeedbackReq.contact / 实体字段 /
--                               落库逻辑 / FeedbackAdminVO 展示同批删除，web 展示列由前端任务同步删）。
-- CREATE TABLE 段已同步移除上述列定义；存量库在此幂等 DROP（先判 INFORMATION_SCHEMA.COLUMNS
-- 存在再 DROP COLUMN，可重跑；六列均无索引成员，无连带对象），不影响既有数据。
DROP PROCEDURE IF EXISTS `drop_zero_consumer_columns`;
DELIMITER $$
CREATE PROCEDURE `drop_zero_consumer_columns`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'password'
    ) THEN
        ALTER TABLE `user` DROP COLUMN `password`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'unionid'
    ) THEN
        ALTER TABLE `user` DROP COLUMN `unionid`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'reject_reason'
    ) THEN
        ALTER TABLE `dish` DROP COLUMN `reject_reason`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'created_by'
    ) THEN
        ALTER TABLE `dish` DROP COLUMN `created_by`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_feedback' AND COLUMN_NAME = 'handler_id'
    ) THEN
        ALTER TABLE `user_feedback` DROP COLUMN `handler_id`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_feedback' AND COLUMN_NAME = 'contact'
    ) THEN
        ALTER TABLE `user_feedback` DROP COLUMN `contact`;
    END IF;
END$$
DELIMITER ;
CALL `drop_zero_consumer_columns`();
DROP PROCEDURE IF EXISTS `drop_zero_consumer_columns`;

-- =============================================================
-- 菜品详情模块整改（2026-09-20 用户拍板，dish-detail-remediation）
-- 库结构变更（列级 + 一张表删除），全部幂等、可重复执行；**禁止直连 ALTER**，统一走本段。
-- 涉及：promo_price 迁移/DROP、dish.tags DROP、描述属性 ADD dish.attributes + 旧描述列回填后 DROP、
--      spice_level/region DROP、canteen 坐标 DROP、
--      review.useful_count DROP + review_useful 表 DROP（表基线 10 → 9）。
-- =============================================================

-- 4.1 价格唯一数据源：先迁移再删除 promo_price（§7.26 / D2）
--     迁移语义：现价以 promo_price 为准（原展示源），故 UPDATE dish SET price = promo_price；
--     随后幂等 DROP 该列。顺序不可颠倒（先 UPDATE 后 DROP），重复执行安全
--     （第二次执行时列已不存在，UPDATE/DROP 均跳过）。
DROP PROCEDURE IF EXISTS `migrate_dish_promo_to_price`;
DELIMITER $$
CREATE PROCEDURE `migrate_dish_promo_to_price`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'promo_price'
    ) THEN
        -- 仅对设有促销价的行迁移；未设行保持原 price
        UPDATE `dish` SET `price` = `promo_price` WHERE `promo_price` IS NOT NULL;
        ALTER TABLE `dish` DROP COLUMN `promo_price`;
    END IF;
END$$
DELIMITER ;
CALL `migrate_dish_promo_to_price`();
DROP PROCEDURE IF EXISTS `migrate_dish_promo_to_price`;

-- 4.2 标签下线：幂等 DROP dish.tags（§7.29；标签筛选与展示整链删除）
DROP PROCEDURE IF EXISTS `drop_dish_tags_column`;
DELIMITER $$
CREATE PROCEDURE `drop_dish_tags_column`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'tags'
    ) THEN
        ALTER TABLE `dish` DROP COLUMN `tags`;
    END IF;
END$$
DELIMITER ;
CALL `drop_dish_tags_column`();
DROP PROCEDURE IF EXISTS `drop_dish_tags_column`;

-- 4.3 描述属性迁移（动态属性模型：**先加列、再回填、最后删旧列**）
--     ① ADD dish.attributes JSON（新库 CREATE 已含，此处对旧库幂等补齐）；
--     ② 由存量四维列 diet_type / ingredients / flavor_tags / serve_temp 回填 attributes（仅写入该菜实际拥有的维度，
--        空维度不占位；四维全空的行落 NULL）；
--     ③ 回填完成后再 DROP 四维旧列 —— 先落新结构再删旧数据，避免任何中间态丢数据；重复执行安全。
--     另：spice_level / region 为更早的旧列（辣度语义并入口味、菜系放弃），一并幂等 DROP。
DROP PROCEDURE IF EXISTS `migrate_dish_attributes_json`;
DELIMITER $$
CREATE PROCEDURE `migrate_dish_attributes_json`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'attributes'
    ) THEN
        ALTER TABLE `dish`
            ADD COLUMN `attributes` JSON NULL DEFAULT NULL
            COMMENT '描述属性（JSON：键=维度 field_key，值=机器值/数组）';
    END IF;

    -- 回填（仅当旧四维列仍存在时执行；已迁移库跳过）
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'diet_type'
    ) THEN
        UPDATE `dish`
        SET `attributes` = CONCAT('{', CONCAT_WS(',',
            CASE WHEN `diet_type` IS NOT NULL AND `diet_type` <> ''
                 THEN CONCAT('"dietType":"', `diet_type`, '"') END,
            CASE WHEN `ingredients` IS NOT NULL AND `ingredients` <> '' AND `ingredients` <> '[]' AND `ingredients` <> 'null'
                 THEN CONCAT('"ingredients":', `ingredients`) END,
            CASE WHEN `flavor_tags` IS NOT NULL AND `flavor_tags` <> '' AND `flavor_tags` <> '[]' AND `flavor_tags` <> 'null'
                 THEN CONCAT('"flavorTags":', `flavor_tags`) END,
            CASE WHEN `serve_temp` IS NOT NULL AND `serve_temp` <> ''
                 THEN CONCAT('"serveTemp":"', `serve_temp`, '"') END
            ), '}');
        UPDATE `dish` SET `attributes` = NULL WHERE `attributes` = '{}';
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'diet_type'
    ) THEN
        ALTER TABLE `dish` DROP COLUMN `diet_type`;
    END IF;
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'ingredients'
    ) THEN
        ALTER TABLE `dish` DROP COLUMN `ingredients`;
    END IF;
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'flavor_tags'
    ) THEN
        ALTER TABLE `dish` DROP COLUMN `flavor_tags`;
    END IF;
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'serve_temp'
    ) THEN
        ALTER TABLE `dish` DROP COLUMN `serve_temp`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'spice_level'
    ) THEN
        ALTER TABLE `dish` DROP COLUMN `spice_level`;
    END IF;
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'region'
    ) THEN
        ALTER TABLE `dish` DROP COLUMN `region`;
    END IF;
END$$
DELIMITER ;
CALL `migrate_dish_attributes_json`();
DROP PROCEDURE IF EXISTS `migrate_dish_attributes_json`;

-- 4.3.1 属性字典遗留收敛（方案 A 起：取值字典退役，仅保留维度表）
--   遗留 dish_attribute_def（维度定义）→ 并入 dish_attribute_dimension 后 DROP；
--   遗留 dish_attribute_value（旧形态：含 dish_id 列、无数据）→ DROP；
--   遗留 dish_attribute_option（机器值→中文映射）→ 临时并入 dish_attribute_value，供 4.3.3 迁移使用后 DROP。
DROP PROCEDURE IF EXISTS `migrate_dish_attribute_dictionary`;
DELIMITER $$
CREATE PROCEDURE `migrate_dish_attribute_dictionary`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_attribute_def'
    ) THEN
        INSERT INTO `dish_attribute_dimension` (`id`, `field_key`, `name`, `value_type`, `order`)
        SELECT `id`, `field_key`, `name`, `value_type`, `order` FROM `dish_attribute_def`
        ON DUPLICATE KEY UPDATE `field_key` = VALUES(`field_key`), `name` = VALUES(`name`),
                                `value_type` = VALUES(`value_type`), `order` = VALUES(`order`);
        DROP TABLE `dish_attribute_def`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_attribute_value' AND COLUMN_NAME = 'dish_id'
    ) THEN
        DROP TABLE `dish_attribute_value`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_attribute_option'
    ) THEN
        CREATE TABLE IF NOT EXISTS `dish_attribute_value`
        (
            `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '取值ID',
            `dimension_id` BIGINT      NOT NULL DEFAULT 0 COMMENT '所属维度ID',
            `value_key`    VARCHAR(32) NOT NULL DEFAULT '' COMMENT '机器值（如 spicy）',
            `label`        VARCHAR(32) NOT NULL DEFAULT '' COMMENT '中文标签（如 辣）',
            `order`        INT         NOT NULL DEFAULT 0 COMMENT '组内展示顺序（升序）',
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_value_dimension_key` (`dimension_id`, `value_key`)
        ) ENGINE = InnoDB
          DEFAULT CHARSET = utf8mb4
          COLLATE = utf8mb4_general_ci COMMENT ='菜品描述属性取值（方案 A 迁移用临时表，随后 DROP）';
        INSERT INTO `dish_attribute_value` (`dimension_id`, `value_key`, `label`, `order`)
        SELECT `def_id`, `value_key`, `label`, `order` FROM `dish_attribute_option`
        ON DUPLICATE KEY UPDATE `label` = VALUES(`label`), `order` = VALUES(`order`);
        DROP TABLE `dish_attribute_option`;
    END IF;
END$$
DELIMITER ;
CALL `migrate_dish_attribute_dictionary`();
DROP PROCEDURE IF EXISTS `migrate_dish_attribute_dictionary`;

-- 4.3.2 纠错表：属性快照改 attributes JSON；局部提交（patch）下四项快照列改为可空
DROP PROCEDURE IF EXISTS `migrate_dish_correction_attributes`;
DELIMITER $$
CREATE PROCEDURE `migrate_dish_correction_attributes`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_correction' AND COLUMN_NAME = 'attributes'
    ) THEN
        ALTER TABLE `dish_correction`
            ADD COLUMN `attributes` JSON NULL DEFAULT NULL
            COMMENT '提交的描述属性（JSON：键=维度 field_key，值=机器值/数组；仅含改动维度）';
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_correction' AND COLUMN_NAME = 'name'
          AND IS_NULLABLE = 'NO'
    ) THEN
        ALTER TABLE `dish_correction`
            MODIFY COLUMN `name` VARCHAR(64) NULL DEFAULT NULL COMMENT '提交的菜品名称（未改动为 NULL）',
            MODIFY COLUMN `price` INT NULL DEFAULT NULL COMMENT '提交的现价（单位：分；未改动为 NULL）',
            MODIFY COLUMN `canteen_name` VARCHAR(64) NULL DEFAULT NULL COMMENT '提交的食堂名称（自由文本，无字典端点；未改动为 NULL）',
            MODIFY COLUMN `stall_name` VARCHAR(64) NULL DEFAULT NULL COMMENT '提交的档口名称（自由文本，采纳时两段式确认归档；未改动为 NULL）';
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_correction' AND COLUMN_NAME = 'flavor_tags'
    ) THEN
        ALTER TABLE `dish_correction` DROP COLUMN `flavor_tags`;
    END IF;
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_correction' AND COLUMN_NAME = 'ingredients'
    ) THEN
        ALTER TABLE `dish_correction` DROP COLUMN `ingredients`;
    END IF;
END$$
DELIMITER ;
CALL `migrate_dish_correction_attributes`();
DROP PROCEDURE IF EXISTS `migrate_dish_correction_attributes`;

-- 4.3.2b dish_correction.floor（楼层纠错，2026-09-30 新增）
--   楼层归属档口（stall.floor）；纠错快照存提交的楼层，采纳时写回目标档口。
--   幂等：列已存在时跳过，重复执行安全。
DROP PROCEDURE IF EXISTS `migrate_dish_correction_floor`;
DELIMITER $$
CREATE PROCEDURE `migrate_dish_correction_floor`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_correction' AND COLUMN_NAME = 'floor'
    ) THEN
        ALTER TABLE `dish_correction`
            ADD COLUMN `floor` VARCHAR(16) NULL DEFAULT NULL
            COMMENT '提交的楼层（自由文本；未改动为 NULL；采纳时写回所属档口 stall.floor）';
    END IF;
END$$
DELIMITER ;
CALL `migrate_dish_correction_floor`();
DROP PROCEDURE IF EXISTS `migrate_dish_correction_floor`;

-- 4.3.3 属性取值「机器值 → 中文」迁移（方案 A：值即中文，去取值字典）
--   存量 dish.attributes / dish_correction.attributes 存的是机器值；用 dish_attribute_value 的
--   (维度, 机器值)→中文 映射就地替换为中文，随后 DROP 取值表（dish_attribute_value）。
--   维度感知（同机器值在不同维度中文不同：veg 在「饮食属性」=素、在「食材」=青菜）。
--   幂等：取值表不存在（新库 / 已迁移）时整段跳过。
DROP PROCEDURE IF EXISTS `migrate_attribute_values_to_text`;
DELIMITER $$
CREATE PROCEDURE `migrate_attribute_values_to_text`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_attribute_value'
    ) THEN
        -- 单值维度（JSON_SET 标量）：dietType(1) / serveTemp(4)
        UPDATE `dish` d JOIN `dish_attribute_value` v
            ON v.`dimension_id` = 1 AND v.`value_key` = JSON_UNQUOTE(JSON_EXTRACT(d.`attributes`, '$."dietType"'))
        SET d.`attributes` = JSON_SET(d.`attributes`, '$."dietType"', v.`label`)
        WHERE d.`attributes` IS NOT NULL;
        UPDATE `dish` d JOIN `dish_attribute_value` v
            ON v.`dimension_id` = 4 AND v.`value_key` = JSON_UNQUOTE(JSON_EXTRACT(d.`attributes`, '$."serveTemp"'))
        SET d.`attributes` = JSON_SET(d.`attributes`, '$."serveTemp"', v.`label`)
        WHERE d.`attributes` IS NOT NULL;
        UPDATE `dish_correction` d JOIN `dish_attribute_value` v
            ON v.`dimension_id` = 1 AND v.`value_key` = JSON_UNQUOTE(JSON_EXTRACT(d.`attributes`, '$."dietType"'))
        SET d.`attributes` = JSON_SET(d.`attributes`, '$."dietType"', v.`label`)
        WHERE d.`attributes` IS NOT NULL;
        UPDATE `dish_correction` d JOIN `dish_attribute_value` v
            ON v.`dimension_id` = 4 AND v.`value_key` = JSON_UNQUOTE(JSON_EXTRACT(d.`attributes`, '$."serveTemp"'))
        SET d.`attributes` = JSON_SET(d.`attributes`, '$."serveTemp"', v.`label`)
        WHERE d.`attributes` IS NOT NULL;

        -- 多值维度（重建数组，未命中字典的保留原值）：ingredients(2) / flavorTags(3)
        UPDATE `dish` d JOIN (
            SELECT d2.`id` AS did,
                   CONCAT('[', GROUP_CONCAT(CONCAT('"', COALESCE(v.`label`, jt.k), '"') ORDER BY jt.ord SEPARATOR ','), ']') AS newarr
            FROM `dish` d2,
                 JSON_TABLE(d2.`attributes`, '$."ingredients"[*]' COLUMNS (ord FOR ORDINALITY, k VARCHAR(64) PATH '$')) jt
            LEFT JOIN `dish_attribute_value` v ON v.`dimension_id` = 2 AND v.`value_key` = jt.k
            WHERE d2.`attributes` IS NOT NULL
              AND JSON_TYPE(JSON_EXTRACT(d2.`attributes`, '$."ingredients"')) = 'ARRAY'
            GROUP BY d2.`id`
        ) t ON t.did = d.`id`
        SET d.`attributes` = JSON_SET(d.`attributes`, '$."ingredients"', CAST(t.newarr AS JSON));
        UPDATE `dish` d JOIN (
            SELECT d2.`id` AS did,
                   CONCAT('[', GROUP_CONCAT(CONCAT('"', COALESCE(v.`label`, jt.k), '"') ORDER BY jt.ord SEPARATOR ','), ']') AS newarr
            FROM `dish` d2,
                 JSON_TABLE(d2.`attributes`, '$."flavorTags"[*]' COLUMNS (ord FOR ORDINALITY, k VARCHAR(64) PATH '$')) jt
            LEFT JOIN `dish_attribute_value` v ON v.`dimension_id` = 3 AND v.`value_key` = jt.k
            WHERE d2.`attributes` IS NOT NULL
              AND JSON_TYPE(JSON_EXTRACT(d2.`attributes`, '$."flavorTags"')) = 'ARRAY'
            GROUP BY d2.`id`
        ) t ON t.did = d.`id`
        SET d.`attributes` = JSON_SET(d.`attributes`, '$."flavorTags"', CAST(t.newarr AS JSON));
        UPDATE `dish_correction` d JOIN (
            SELECT d2.`id` AS did,
                   CONCAT('[', GROUP_CONCAT(CONCAT('"', COALESCE(v.`label`, jt.k), '"') ORDER BY jt.ord SEPARATOR ','), ']') AS newarr
            FROM `dish_correction` d2,
                 JSON_TABLE(d2.`attributes`, '$."ingredients"[*]' COLUMNS (ord FOR ORDINALITY, k VARCHAR(64) PATH '$')) jt
            LEFT JOIN `dish_attribute_value` v ON v.`dimension_id` = 2 AND v.`value_key` = jt.k
            WHERE d2.`attributes` IS NOT NULL
              AND JSON_TYPE(JSON_EXTRACT(d2.`attributes`, '$."ingredients"')) = 'ARRAY'
            GROUP BY d2.`id`
        ) t ON t.did = d.`id`
        SET d.`attributes` = JSON_SET(d.`attributes`, '$."ingredients"', CAST(t.newarr AS JSON));
        UPDATE `dish_correction` d JOIN (
            SELECT d2.`id` AS did,
                   CONCAT('[', GROUP_CONCAT(CONCAT('"', COALESCE(v.`label`, jt.k), '"') ORDER BY jt.ord SEPARATOR ','), ']') AS newarr
            FROM `dish_correction` d2,
                 JSON_TABLE(d2.`attributes`, '$."flavorTags"[*]' COLUMNS (ord FOR ORDINALITY, k VARCHAR(64) PATH '$')) jt
            LEFT JOIN `dish_attribute_value` v ON v.`dimension_id` = 3 AND v.`value_key` = jt.k
            WHERE d2.`attributes` IS NOT NULL
              AND JSON_TYPE(JSON_EXTRACT(d2.`attributes`, '$."flavorTags"')) = 'ARRAY'
            GROUP BY d2.`id`
        ) t ON t.did = d.`id`
        SET d.`attributes` = JSON_SET(d.`attributes`, '$."flavorTags"', CAST(t.newarr AS JSON));

        DROP TABLE IF EXISTS `dish_attribute_value`;
    END IF;
END$$
DELIMITER ;
CALL `migrate_attribute_values_to_text`();
DROP PROCEDURE IF EXISTS `migrate_attribute_values_to_text`;

-- 4.4 坐标下线：幂等 DROP canteen.latitude / canteen.longitude（D8；位置表达收敛为 食堂 · 楼层 · 档口名）
--     原 add_canteen_location 迁移存储过程（含逐食堂坐标回填）已从本文件删除，建列与回填逻辑一并退役。
DROP PROCEDURE IF EXISTS `drop_canteen_coordinates`;
DELIMITER $$
CREATE PROCEDURE `drop_canteen_coordinates`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'canteen' AND COLUMN_NAME = 'latitude'
    ) THEN
        ALTER TABLE `canteen` DROP COLUMN `latitude`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'canteen' AND COLUMN_NAME = 'longitude'
    ) THEN
        ALTER TABLE `canteen` DROP COLUMN `longitude`;
    END IF;
END$$
DELIMITER ;
CALL `drop_canteen_coordinates`();
DROP PROCEDURE IF EXISTS `drop_canteen_coordinates`;

-- 4.5 「有用」全链下线（§7.30 / D-2.7）：幂等 DROP review.useful_count 列与整张 review_useful 表
--     **数据表基线 10 → 9**（该表为本次唯一表级变更）。热度公式与评分聚合均不含有用数，无下游依赖。
--     CREATE TABLE 段已移除；本段清理存量库。顺序：先 DROP 列（无索引成员，无连带对象）再 DROP 表。
DROP PROCEDURE IF EXISTS `drop_review_useful_chain`;
DELIMITER $$
CREATE PROCEDURE `drop_review_useful_chain`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND COLUMN_NAME = 'useful_count'
    ) THEN
        ALTER TABLE `review` DROP COLUMN `useful_count`;
    END IF;

    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review_useful'
    ) THEN
        DROP TABLE `review_useful`;
    END IF;
END$$
DELIMITER ;
CALL `drop_review_useful_chain`();
DROP PROCEDURE IF EXISTS `drop_review_useful_chain`;

-- 4.6 菜品大类：幂等 ADD dish.meal_type（2026-09-21 §7.34 / change home-ui-refresh，H5-1）
--     单值枚举列（VARCHAR，可空），值域由后端 DishViewConst 派生（唯一真源，不建字典表/外键——§7.22 第 1 条继续有效）。
--     语义：每个菜品恰属一个大类（单值互斥）；大类不进公开菜品出参（DishListItemVO / DishDetailVO），仅供筛选（GET /dishes?view=，
--     白名单校验非法值 400）与视图字典下发（GET /dishes/views，空类自动隐藏）。
--     写法兼容 MySQL 5.7（information_schema 判列 + PREPARE 动态 ALTER；目标库 TDSQL-C 为 5.7 兼容版）。
DROP PROCEDURE IF EXISTS `add_dish_meal_type`;
DELIMITER $$
CREATE PROCEDURE `add_dish_meal_type`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'meal_type'
    ) THEN
        -- 注意：MySQL 5.7 默认 sql_mode 下 `||` 是逻辑 OR 而非字符串拼接（8.0 才支持），
        -- 必须用 CONCAT()（目标库 TDSQL-C 为 5.7 兼容版）
        SET @ddl = CONCAT('ALTER TABLE `dish` ADD COLUMN `meal_type` VARCHAR(20) NULL ',
                  'COMMENT ''菜品大类（单值枚举，键域由 DishViewConst 派生；可空；筛选与字典下发用，不进公开出参）'' ',
                  'AFTER `serve_temp`');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;
CALL `add_dish_meal_type`();
DROP PROCEDURE IF EXISTS `add_dish_meal_type`;

-- 4.7 菜品搜索别名下线：幂等 DROP dish.alias（2026-09-22 用户拍板「菜品无需昵称」，change search-page-refresh）
--     背景：别名层只增后台录入负担，端上从未消费；关键词匹配收敛为菜名 / 档口名 / 食堂名三处。
--     同批：Dish 实体 / DishAdminReq / DishAdminVO 去字段；DishMapper.xml 去别名匹配路与列映射；
--           后台表单去「搜索别名」输入与 ≤255 校验；seed_data.sql 经核对本就不含该列。
--     CREATE TABLE 已不再创建该列（原 add_dish_alias 段已整段删除）；本段仅清理存量库。
--     幂等：先判 INFORMATION_SCHEMA.COLUMNS 存在再 DROP，可重跑、不影响既有数据
--     （该列无索引成员，DROP COLUMN 无连带对象）。
DROP PROCEDURE IF EXISTS `drop_dish_alias_column`;
DELIMITER $$
CREATE PROCEDURE `drop_dish_alias_column`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'alias'
    ) THEN
        ALTER TABLE `dish` DROP COLUMN `alias`;
    END IF;
END$$
DELIMITER ;
CALL `drop_dish_alias_column`();
DROP PROCEDURE IF EXISTS `drop_dish_alias_column`;

-- =============================================================
-- 菜品详情契约加固（2026-09-23 用户拍板，change dish-detail-contract-hardening）
-- 本段为**破坏批**库结构变更：review.updated_at 下线（R6）。
-- 全部幂等、可重复执行；**禁止直连 ALTER**，库结构变更统一走本段。
-- =============================================================

-- 5.1 review.updated_at 下线（§7.40 R6）：幂等 DROP
--     退役依据：重评时与 created_at **同批刷新** → 两者恒等，该列对评价无独立语义；
--     消费方核实（2026-09-23）：端上与 web/src/views **双双零命中**（仅 adapter/types 有映射声明）。
--     ⚠️ 落地前建议人工核对一次「不存在 updated_at <> created_at 的行」（历史值即将丢失、不可逆）：
--        SELECT COUNT(*) FROM review WHERE updated_at <> created_at;
--        —— 预期为 0；若不为 0，请先确认这些差值无业务含义再执行本段。
--     ⚠️ 只删**评价侧**：dish.updated_at 有真实消费（DishMapper 列表排序 + DishFormDialog 的
--        Q-112「他人已修改」轻提示基线），**不得**据此误删。
DROP PROCEDURE IF EXISTS `drop_review_updated_at`;
DELIMITER $$
CREATE PROCEDURE `drop_review_updated_at`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND COLUMN_NAME = 'updated_at'
    ) THEN
        ALTER TABLE `review` DROP COLUMN `updated_at`;
    END IF;
END$$
DELIMITER ;
CALL `drop_review_updated_at`();
DROP PROCEDURE IF EXISTS `drop_review_updated_at`;

-- =============================================================
-- 表退役：view_log（只写不读的行为数据 → 数据最小化，停止采集）
--   ① 本文件不再创建该表，实体 / Mapper / 全部写入点同批移除；
--   ② 浏览量由 dish.view_count 独立承载（GET /dishes/{id} 成功路径原子自增）；
--   ③ 存量库在此幂等 DROP（先判 TABLE 存在再删，可重跑）。
-- =============================================================
DROP PROCEDURE IF EXISTS `drop_view_log_table`;
DELIMITER $$
CREATE PROCEDURE `drop_view_log_table`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'view_log'
    ) THEN
        DROP TABLE `view_log`;
    END IF;
END$$
DELIMITER ;
CALL `drop_view_log_table`();
DROP PROCEDURE IF EXISTS `drop_view_log_table`;

-- =============================================================
-- 索引补齐（2026-09-29 性能修正）：消除列表 / 未读数 / 管理端筛选的 filesort
--   · review(dish_id, is_hidden, created_at)      ← 公开评价列表
--   · review(user_id, is_hidden, created_at)      ← 「我的评价」列表
--   · notification(user_id, created_at)           ← 通知列表（时间倒序）
--   · notification(user_id, is_read)              ← 未读数统计
--   · user_feedback(status, created_at)           ← 管理端反馈筛选
--   · dish_correction(status, created_at)         ← 管理端纠错筛选
-- 新库由上述 CREATE TABLE 直接建成；存量库由本段补建。
-- 幂等：先查 INFORMATION_SCHEMA.STATISTICS 再 ADD INDEX，重复执行安全（可重跑）。
-- 注意：本段**不**DROP 既有单列索引（idx_review_dish / idx_review_user / idx_notification_user /
--   idx_correction_status），避免对存量库做非必要破坏性变更；新库的 CREATE TABLE 已按
--   「复合索引取代同前缀单列索引」定义，无冗余。
-- =============================================================
DROP PROCEDURE IF EXISTS `ensure_perf_indexes`;
DELIMITER $$
CREATE PROCEDURE `ensure_perf_indexes`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review'
                     AND INDEX_NAME = 'idx_review_dish_visible') THEN
        ALTER TABLE `review` ADD INDEX `idx_review_dish_visible` (`dish_id`, `is_hidden`, `created_at`);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review'
                     AND INDEX_NAME = 'idx_review_user_visible') THEN
        ALTER TABLE `review` ADD INDEX `idx_review_user_visible` (`user_id`, `is_hidden`, `created_at`);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification'
                     AND INDEX_NAME = 'idx_notification_user_created') THEN
        ALTER TABLE `notification` ADD INDEX `idx_notification_user_created` (`user_id`, `created_at`);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification'
                     AND INDEX_NAME = 'idx_notification_user_read') THEN
        ALTER TABLE `notification` ADD INDEX `idx_notification_user_read` (`user_id`, `is_read`);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_feedback'
                     AND INDEX_NAME = 'idx_feedback_status_created') THEN
        ALTER TABLE `user_feedback` ADD INDEX `idx_feedback_status_created` (`status`, `created_at`);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_correction'
                     AND INDEX_NAME = 'idx_correction_status_created') THEN
        ALTER TABLE `dish_correction` ADD INDEX `idx_correction_status_created` (`status`, `created_at`);
    END IF;
END$$
DELIMITER ;
CALL `ensure_perf_indexes`();
DROP PROCEDURE IF EXISTS `ensure_perf_indexes`;

-- =============================================================
-- 孤儿数据体检（D5：外键约束前必须先看清存量是否干净）
-- =============================================================
-- 背景：全库此前**零外键**，一致性全靠应用层事件维护
--   （dish 删除 → ReviewDishCascadeListener 删评价；user 注销 → NotificationAccountCleanListener 删消息；
--     账号归属迁移 → 各域监听器改挂）。风险：这些监听器一旦被误加 @TransactionalEventListener
--     或误改注解，对应行就成孤儿，而读侧 JOIN 仍能查出（user 行存在）、只是永远关联不到菜品——
--     **数据静默腐烂，不报错**。有外键时这类改动会立刻失败。
--
-- 下方两条外键只覆盖「应用层已有可靠级联」的关系（review.dish_id / notification.user_id）；
-- 其余关系**刻意不加**（理由见下方逐条注释），不是遗漏。
--
-- ⚠️ 执行顺序：**先跑体检、确认 0 行孤儿，再执行 ensure_integrity_foreign_keys**。
--   存量库若已有孤儿，ADD FOREIGN KEY 会直接失败（这正是期望行为：宁可迁移失败，
--   也不要静默删数据）。届时按体检结果人工确认处理策略。
-- =============================================================
-- 体检 1：review 指向已不存在的 dish（菜品被物理删除但评价未级联清理）
SELECT COUNT(*) AS orphan_review_dish
FROM review r
         LEFT JOIN dish d ON r.dish_id = d.id
WHERE d.id IS NULL;

-- 体检 2：notification 指向已不存在的 user
-- 注：user 行只做匿名化（status='deleted'）从不物理删除，故此孤儿只可能来自「历史上误删过 user 行」
SELECT COUNT(*) AS orphan_notification_user
FROM notification n
         LEFT JOIN `user` u ON n.user_id = u.id
WHERE u.id IS NULL;

-- =============================================================
-- 外键约束补齐（D5）
-- =============================================================
-- 逐条说明为何「只加这两条」——零外键是既有事实，但并非所有关系都适合加：
--
-- ✅ review.dish_id → dish.id（ON DELETE CASCADE）
--    应用层已由 ReviewDishCascadeListener 同步删除评价，CASCADE 是**第二道防线**而非新行为：
--    正常路径下 review 行已先被删掉，CASCADE 命中 0 行；只有监听器失效时才由数据库兜底。
--    两级方向一致（都删），故加约束不改变任何既有语义。
--
-- ✅ notification.user_id → user.id（ON DELETE CASCADE）
--    同理：NotificationAccountCleanListener 已在注销时硬删消息，CASCADE 兜底。
--    且 user 行实际从不物理删除（注销=匿名化），此约束主要防「误删 user 行」这类未来改动。
--
-- ❌ dish_correction.dish_id → dish.id —— **刻意不加**
--    纠错记录在菜品删除后**有意保留**（它是「用户反馈过什么」的历史痕迹，非菜品附属数据）。
--    加 CASCADE 会连带删掉纠错记录（改变行为），加 RESTRICT 会让菜品根本删不掉（更是破坏功能）。
--    schema 中该列注释「逻辑关联 dish，不设外键，与项目现状一致」即此意，保留原状。
--
-- ❌ review.user_id / user_feedback.user_id / dish_correction.user_id → user.id —— **刻意不加**
--    user 行永不物理删除（注销为匿名化），加约束无实际收益；而 dish_correction.user_id 可为 NULL
--    （匿名提交），加约束需额外处理 NULL 语义，收益与复杂度不成正比。
--
-- ❌ dish.stall_id → stall.id —— **刻意不加**
--    stall/canteen 已去实体化，是「按名 upsert 的属性字典」，存在独立的同名合并/清理语义，
--    约束化可能干扰 upsert 流程，暂不纳入。
--
-- 幂等：先查 INFORMATION_SCHEMA.TABLE_CONSTRAINTS 再 ADD CONSTRAINT，重复执行安全。
-- =============================================================
DROP PROCEDURE IF EXISTS `ensure_integrity_foreign_keys`;
DELIMITER $$
CREATE PROCEDURE `ensure_integrity_foreign_keys`()
BEGIN
    -- 前置断言：存量库若有孤儿，下面两条 ADD 会失败并中止迁移（期望行为，勿用 DELETE 绕过）
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review'
                     AND CONSTRAINT_NAME = 'fk_review_dish') THEN
        ALTER TABLE `review`
            ADD CONSTRAINT `fk_review_dish` FOREIGN KEY (`dish_id`) REFERENCES `dish` (`id`) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification'
                     AND CONSTRAINT_NAME = 'fk_notification_user') THEN
        ALTER TABLE `notification`
            ADD CONSTRAINT `fk_notification_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE;
    END IF;
END$$
DELIMITER ;
CALL `ensure_integrity_foreign_keys`();
DROP PROCEDURE IF EXISTS `ensure_integrity_foreign_keys`;

SET FOREIGN_KEY_CHECKS = 1;
