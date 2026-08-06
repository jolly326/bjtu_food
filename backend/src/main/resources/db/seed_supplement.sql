-- =============================================================
-- 食在交大 补充演示数据（一次性执行，非自动加载）
-- 用途：补齐联调库中缺失的 分类 / 广播 / 反馈 / 申请 数据，
--       使三端管理链路（反馈处理、UGC 申请审核、分类管理、广播管理）有数据可审。
-- 前置：先执行 schema.sql + seed_data.sql（用户已由 seed_data 建立，勿在此重建）。
--       旧库（用户为测试数据、admin 非 123456）修复请另执行 seed_users.sql。
-- 执行：mysql -u <user> -p <pwd> bjtu_food < seed_supplement.sql
-- 幂等：无唯一键的表先清后插；可重复执行。
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------- 菜品分类表（find 宫格，A.17；早期运行库缺失，幂等创建） --------------------
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

-- -------------------- 菜品分类数据（无唯一键，先清后插保证可重复执行） --------------------
DELETE FROM category;
INSERT INTO category (name, sort_order, status) VALUES
('早餐',    1, 'enabled'),
('午餐',    2, 'enabled'),
('晚餐',    3, 'enabled'),
('夜宵',    4, 'enabled'),
('面食',    5, 'enabled'),
('米饭',    6, 'enabled'),
('麻辣',    7, 'enabled'),
('清淡',    8, 'enabled');

-- -------------------- 首页广播条（A.14；无唯一键，先清后插保证可重复执行） --------------------
DELETE FROM broadcast;
INSERT INTO broadcast (title, content, broadcast_type, target_id, target_url, sort_order, status) VALUES
('开餐提醒',   '学苑区各食堂午间 11:00-13:30 供餐，错峰就餐更舒适', 'NOTICE', NULL, NULL, 1, 'enabled'),
('今日特惠',   '明湖烧烤 烤五花肉 限时 8 折',                     'DISH',   18,  NULL, 2, 'enabled'),
('新生指引',   '扫码进入小程序，探索交大各食堂招牌菜',           'NONE',   NULL, NULL, 3, 'enabled');

-- -------------------- 用户反馈（含建议/纠错/举报，测试反馈处理流；无唯一键，先清后插保证可重复执行） --------------------
DELETE FROM user_feedback;
INSERT INTO user_feedback (user_id, type, content, contact, status, related_type, related_id, created_at) VALUES
(1, 'suggestion', '希望菜品详情页能标注过敏原信息，方便有忌口的同学选择', '2024001@bjtu.edu.cn', 'pending', 'none', NULL, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(2, 'error',     '明湖烧烤的营业时间写的是 10:00-22:00，实际下午才开门，麻烦修正一下', '2024002@bjtu.edu.cn', 'pending', 'none', NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(3, 'report',    '有动态内容疑似广告引流，建议管理员审核处理', NULL, 'pending', 'moment', 1, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(4, 'other',     '账号无法收到登录验证码，邮箱没有新邮件，求帮助', '2024004@bjtu.edu.cn', 'pending', 'none', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY));

-- -------------------- 实体贡献申请（UGC，测试申请审核流；无唯一键，先清后插保证可重复执行） --------------------
DELETE FROM apply_action;
INSERT INTO apply_action (applicant_id, entity_type, entity_id, apply_type, status, payload, created_at) VALUES
(1, 'DISH',   NULL, 'NEW',    'pending', '{"name":"香煎鸡排饭","price":1800,"description":"外酥里嫩，配时蔬","stall_id":1,"tags":"recommended"}',     DATE_SUB(NOW(), INTERVAL 40 MINUTE)),
(2, 'STALL',  NULL, 'NEW',    'pending', '{"name":"学二奶茶铺","location":"学二食堂一层","description":"鲜制果茶与奶茶","canteen_id":2}',            DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(3, 'CANTEEN', 2,  'CHANGE', 'pending', '{"name":"学二食堂","location":"学苑区一栋东侧","description":"新增夜宵窗口"}',                          DATE_SUB(NOW(), INTERVAL 6 HOUR)),
(4, 'STALL',  14, 'CLOSE',   'pending', '{"name":"留园包点","reason":"档口歇业，申请关闭"}',                                                   DATE_SUB(NOW(), INTERVAL 1 DAY));

SET FOREIGN_KEY_CHECKS = 1;
