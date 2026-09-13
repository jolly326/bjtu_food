-- =============================================================
-- 食在交大 种子数据脚本（重置服务器数据库用，一次性执行，非自动加载）
-- =============================================================
-- 用途：重置服务器数据库时灌入演示/基础数据（用户、分类、食堂、档口、
--       菜品、评价、反馈、通知等），使三端有完整联调数据。
-- 本脚本自包含：自动建库并切换 USE bjtu_food（与 schema.sql 一致，库不存在时先建库）。
-- 执行前提：已按 schema.sql 建好全部表与最终字段；本脚本不建表。
-- 执行：mysql -u <user> -p -h localhost < seed_data.sql
-- 注意：本脚本部分段落（user_feedback / notification）采用先清后插，可重复执行；
--       其余段落（user / dish 等）重复执行会重复插入，重置时请先清库再运行。
-- 金额字段单位：分（如 1600 = 16.00 元）
-- images 置 NULL，由前端占位图（emoji）优雅降级，避免小程序外链域名限制。
-- =============================================================

-- 自包含建库选库：避免在未选中库时 INSERT 落入默认库触发 1044 权限错误
CREATE DATABASE IF NOT EXISTS `bjtu_food` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `bjtu_food`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------- 用户（评价/通知/反馈等均依赖；密码统一为 123456，BCrypt 哈希） --------------------
-- BCrypt('123456') = $2a$10$cpM/NAuF4sjRoKILNJ.G7uDAoLHwF0G6eOpY2P/uTKy8WMyNjQuHa
INSERT INTO `user` (username, email, password, nickname, avatar, role, status, last_login_at) VALUES
('2024001',  '2024001@bjtu.edu.cn', '$2a$10$cpM/NAuF4sjRoKILNJ.G7uDAoLHwF0G6eOpY2P/uTKy8WMyNjQuHa', '交大干饭王',  NULL, 'student', 'active', NOW()),
('2024002',  '2024002@bjtu.edu.cn', '$2a$10$cpM/NAuF4sjRoKILNJ.G7uDAoLHwF0G6eOpY2P/uTKy8WMyNjQuHa', '食堂常客',    NULL, 'student', 'active', NOW()),
('2024003',  '2024003@bjtu.edu.cn', '$2a$10$cpM/NAuF4sjRoKILNJ.G7uDAoLHwF0G6eOpY2P/uTKy8WMyNjQuHa', '深夜放毒',    NULL, 'student', 'active', NOW()),
('2024004',  '2024004@bjtu.edu.cn', '$2a$10$cpM/NAuF4sjRoKILNJ.G7uDAoLHwF0G6eOpY2P/uTKy8WMyNjQuHa', '奶茶三分甜',  NULL, 'student', 'active', NOW()),
('admin',    'admin@bjtu.edu.cn',   '$2a$10$cpM/NAuF4sjRoKILNJ.G7uDAoLHwF0G6eOpY2P/uTKy8WMyNjQuHa', '管理员',      NULL, 'super_admin', 'active', NOW());

-- -------------------- 菜品品类（首页品类滚轮：贴合大学食堂档口业态的真实品类） --------------------
INSERT INTO category (code, name, sort_order, status) VALUES
('malatang', '麻辣烫',   1, 'enabled'),
('noodle',   '面食',     2, 'enabled'),
('rice',     '盖饭套餐', 3, 'enabled'),
('home',     '家常小炒', 4, 'enabled'),
('bbq',      '烧烤炸物', 5, 'enabled'),
('porridge', '汤粥',     6, 'enabled'),
('drink',    '饮品甜点', 7, 'enabled'),
('halal',    '清真',     8, 'enabled');

-- -------------------- 食堂（共 7 个；id=1 为学一食堂，档口/菜品 canteen_id 引用以此对齐） --------------------
INSERT INTO canteen (name, location, description, status, sort_order, audit_status, latitude, longitude) VALUES
('学一食堂', '学苑区',     '综合食堂，家常风味', 'open', 1, 'approved', 39.953800, 116.335400),
('学二食堂', '学苑区一栋', '明亮整洁，家常味道', 'open', 2, 'approved', 39.954200, 116.335800),
('学三食堂', '学苑区二栋', '品类丰富，平价美味', 'open', 3, 'approved', 39.954600, 116.336200),
('明湖餐厅', '明湖旁',     '湖景餐厅，聚餐首选', 'open', 4, 'approved', 39.955800, 116.331500),
('嘉园餐厅', '嘉园公寓',   '夜宵与小吃天堂',     'open', 5, 'approved', 39.953000, 116.339000),
('清真食堂', '学苑区',     '清真风味，干净卫生', 'open', 6, 'approved', 39.954800, 116.335000),
('留园餐厅', '留园区',     '精致小炒与面点',     'open', 7, 'approved', 39.957000, 116.338000);

-- -------------------- 档口（canteen_id 对应上面的食堂） --------------------
INSERT INTO stall (canteen_id, name, location, description, status, sort_order, audit_status) VALUES
(1, '学一基本伙食', '学一食堂一层', '平价家常菜',       'open', 2, 'approved'),
(1, '学一面点坊',   '学一食堂一层', '现做面点与汤包',   'open', 3, 'approved'),
(2, '学二快餐档',   '学二食堂',     '快捷套餐',         'open', 1, 'approved'),
(2, '学二盖饭档',   '学二食堂',     '各式盖饭',         'open', 2, 'approved'),
(3, '学三麻辣烫',   '学三食堂',     '自选麻辣烫',       'open', 1, 'approved'),
(3, '学三粥铺',     '学三食堂',     '养生粥品',         'open', 2, 'approved'),
(4, '明湖小炒',     '明湖餐厅',     '现炒小菜',         'open', 1, 'approved'),
(4, '明湖烧烤',     '明湖餐厅',     '炭火烧烤',         'open', 2, 'approved'),
(5, '嘉园夜宵',     '嘉园餐厅',     '深夜食堂',         'open', 1, 'approved'),
(5, '嘉园奶茶',     '嘉园餐厅',     '鲜制饮品',         'open', 2, 'approved'),
(6, '清真拉面',     '清真食堂',     '手工拉面',         'open', 1, 'approved'),
(6, '清真烤串',     '清真食堂',     '清真烤串',         'open', 2, 'approved'),
(7, '留园小炒',     '留园餐厅',     '精致小炒',         'open', 1, 'approved'),
(7, '留园包点',     '留园餐厅',     '广式包点',         'open', 2, 'approved');

-- -------------------- 菜品（stall_id 对应上面档口；category_id 对应上面品类 1=麻辣烫 2=面食 3=盖饭套餐 4=家常小炒 5=烧烤炸物 6=汤粥 7=饮品甜点 8=清真；价格单位：分） --------------------
INSERT INTO dish (stall_id, category_id, name, price, description, images, tags, status, audit_status, view_count, avg_rating, rating_count) VALUES
(1,  4, '宫保鸡丁',   1600, '酸甜微辣，下饭神器',           NULL, 'recommended,signature', 'on', 'approved', 560, 4.7, 120),
(1,  4, '水煮牛肉',   2800, '麻辣鲜香，分量十足',           NULL, 'signature',            'on', 'approved', 720, 4.8,  98),
(1,  4, '回锅肉',     1800, '肥而不腻，川味经典',           NULL, 'recommended',          'on', 'approved', 430, 4.6,  76),
(1,  4, '番茄炒蛋',    900, '家常味道，酸甜可口',           NULL, 'recommended',          'on', 'approved', 610, 4.5, 150),
(1,  4, '土豆烧牛肉', 2200, '软烂入味，暖心暖胃',           NULL, '',                    'on', 'approved', 380, 4.4,  64),
(11, 2, '牛肉拉面',   1500, '筋道爽滑，汤头浓郁',           NULL, 'signature',            'on', 'approved', 880, 4.7, 200),
(2,  2, '鲜肉小笼',   1200, '皮薄汁多，一口爆汁',           NULL, 'recommended',          'on', 'approved', 760, 4.8, 180),
(4,  3, '黄焖鸡米饭', 1800, '酱香浓郁，鸡肉嫩滑',           NULL, 'recommended',          'on', 'approved', 690, 4.6, 140),
(4,  4, '香辣虾',     3200, '鲜香麻辣，弹牙爽口',           NULL, 'signature',            'on', 'approved', 320, 4.5,  55),
(4,  3, '招牌烤肉饭', 2000, '肉香四溢，粒粒分明',           NULL, 'recommended',          'on', 'approved', 700, 4.7, 130),
(4,  3, '咖喱鸡排饭', 1900, '咖喱醇厚，外酥里嫩',           NULL, '',                    'on', 'approved', 410, 4.4,  88),
(5,  1, '骨汤麻辣烫', 1700, '自选食材，麻辣鲜香',           NULL, 'recommended',          'on', 'approved', 820, 4.6, 160),
(5,  1, '冒脑花',     1500, '嫩滑入味，辣得过瘾',           NULL, 'signature',            'on', 'approved', 260, 4.3,  42),
(6,  6, '皮蛋瘦肉粥',  800, '绵密温润，暖胃首选',           NULL, 'recommended',          'on', 'approved', 520, 4.5, 110),
(6,  2, '广式肠粉',   1000, '晶莹剔透，酱香清爽',           NULL, '',                    'on', 'approved', 470, 4.6,  95),
(7,  4, '干锅花菜',   1600, '爽脆下饭，锅气十足',           NULL, 'recommended',          'on', 'approved', 390, 4.5,  70),
(7,  4, '糖醋里脊',   2100, '外酥里嫩，酸甜开胃',           NULL, 'signature',            'on', 'approved', 640, 4.7, 120),
(8,  5, '烤五花肉',   2500, '滋滋冒油，焦香四溢',           NULL, 'recommended',          'on', 'approved', 780, 4.8, 140),
(8,  5, '烤茄子',     1200, '蒜香浓郁，软糯鲜甜',           NULL, '',                    'on', 'approved', 300, 4.4,  60),
(9,  2, '炒粉',       1300, '镬气十足，宵夜之王',           NULL, 'recommended',          'on', 'approved', 700, 4.6, 150),
(9,  5, '烤冷面',     1100, '酸甜筋道，东北风味',           NULL, 'signature',            'on', 'approved', 560, 4.5, 130),
(10, 7, '珍珠奶茶',   1000, 'Q弹珍珠，奶香醇厚',            NULL, 'recommended',          'on', 'approved', 980, 4.7, 220),
(10, 7, '杨枝甘露',   1400, '芒果西米，清甜解腻',           NULL, 'signature',            'on', 'approved', 840, 4.8, 190),
(11, 8, '兰州牛肉面', 1500, '一清二白，汤鲜面劲',           NULL, 'signature',            'on', 'approved', 900, 4.8, 210),
(11, 8, '羊肉泡馍',   2000, '馍香肉烂，汤浓味厚',           NULL, 'recommended',          'on', 'approved', 460, 4.6,  80),
(12, 8, '羊肉串',     2000, '孜然飘香，外焦里嫩',           NULL, 'recommended',          'on', 'approved', 720, 4.7, 160),
(12, 8, '烤馕',        900, '金黄酥脆，麦香十足',           NULL, '',                    'on', 'approved', 320, 4.5,  70),
(13, 4, '鱼香茄子',   1400, '咸鲜微甜，超级下饭',           NULL, 'recommended',          'on', 'approved', 500, 4.5,  90),
(13, 4, '宫保虾球',   3000, '荔枝口型，弹嫩鲜香',           NULL, 'signature',            'on', 'approved', 360, 4.6,  60),
(14, 2, '鲜虾烧卖',   1300, '皮薄馅大，鲜香多汁',           NULL, 'recommended',          'on', 'approved', 580, 4.7, 110),
(14, 2, '叉烧包',     1000, '松软甜香，广式经典',           NULL, '',                    'on', 'approved', 520, 4.6, 100);

-- -------------------- 评价（为部分菜品填充评价，丰富详情页；与 dish.avg_rating/rating_count 大致对应） --------------------
INSERT INTO review (user_id, dish_id, rating, content, is_hidden) VALUES
(1, 1,  5, '宫保鸡丁真的绝，下饭神器！',               0),
(1, 2,  5, '水煮牛肉麻辣鲜香，分量很足',               0),
(1, 6,  4, '牛肉拉面汤头浓郁，就是稍微有点咸',         0),
(1, 18, 5, '烤五花肉滋滋冒油，太香了',                 0),
(1, 22, 5, '珍珠奶茶奶香十足，珍珠很Q',                0),
(1, 24, 5, '兰州牛肉面一清二白，地道！',               0),
(2, 1,  4, '分量足，性价比高',                         0),
(2, 3,  5, '回锅肉肥而不腻，川味正',                   0),
(2, 12, 5, '骨汤麻辣烫自选很爽，汤底好喝',             0),
(2, 16, 5, '干锅花菜锅气十足，下饭',                   0),
(2, 26, 5, '羊肉串外焦里嫩，孜然味足',                 0),
(3, 4,  5, '番茄炒蛋家常味，酸甜可口',                 0),
(3, 7,  5, '皮蛋瘦肉粥绵密温润，暖胃',                 0),
(3, 8,  5, '干锅花菜朋友都夸',                         0),
(3, 14, 5, '皮蛋瘦肉粥配油条绝配',                     0),
(3, 29, 5, '鲜虾烧卖皮薄馅大，好吃',                   0),
(4, 9,  5, '香辣虾弹牙爽口，够味',                     0),
(4, 19, 4, '炒粉镬气足，宵夜首选',                     0),
(4, 23, 5, '杨枝甘露清甜解腻',                         0),
(4, 30, 5, '叉烧包松软甜香，广式经典',                 0);

-- -------------------- 评价「有用」标记（review_useful，一人一票） --------------------
INSERT INTO review_useful (user_id, review_id, created_at) VALUES
(2, 1, NOW()), (3, 1, NOW()), (4, 1, NOW()),
(1, 2, NOW()), (3, 2, NOW()),
(2, 3, NOW()), (4, 3, NOW()),
(1, 4, NOW()), (3, 4, NOW()),
(2, 5, NOW()), (4, 5, NOW()),
(1, 6, NOW()), (3, 6, NOW());

-- 回填 review.useful_count 冗余列，与上方 review_useful 标记保持一致（评价卡展示「有用」数）
UPDATE review SET useful_count = 3 WHERE id = 1;
UPDATE review SET useful_count = 2 WHERE id IN (2, 3, 4, 5, 6);

-- -------------------- 用户反馈（含建议/纠错/举报，测试反馈处理流；无唯一键，先清后插保证可重复执行） --------------------
DELETE FROM user_feedback;
INSERT INTO user_feedback (user_id, type, content, contact, status, related_type, related_id, created_at) VALUES
(1, 'suggestion', '希望菜品详情页能标注过敏原信息，方便有忌口的同学选择', '2024001@bjtu.edu.cn', 'pending', 'none', NULL, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(2, 'error',     '明湖烧烤的营业时间写的是 10:00-22:00，实际下午才开门，麻烦修正一下', '2024002@bjtu.edu.cn', 'pending', 'none', NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(3, 'report',    '有评价内容疑似广告引流，建议管理员审核处理', NULL, 'pending', 'review', 1, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(4, 'other',     '账号无法收到登录验证码，邮箱没有新邮件，求帮助', '2024004@bjtu.edu.cn', 'pending', 'none', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(2, 'bug',       '首页瀑布流下拉刷新偶发卡死，需要杀掉小程序重进才恢复', '2024002@bjtu.edu.cn', 'pending', 'none', NULL, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(1, 'add',       '【新增菜品】香煎鸡排饭\n位置：二食堂二楼 3 号窗口\n特色：外酥里嫩，配时蔬', NULL, 'pending', 'dish', NULL, DATE_SUB(NOW(), INTERVAL 1 HOUR));

-- =============================================================
-- 一期扩展字段补充（新增列后回填；基于默认值的幂等 UPDATE，可重复执行）
-- 来源：tasks/ARCH_DECISIONS_PHASE1.md §1.2
-- 仅用于演示/联调，给档口填位置链路、给菜品填属性标签。
-- =============================================================

-- 档口：楼层 / 窗口号 / 营业时间
UPDATE stall SET floor='1F',  window_no='1号窗口',  business_hours='10:00-20:00' WHERE id=1;
UPDATE stall SET floor='1F',  window_no='2号窗口',  business_hours='07:00-14:00' WHERE id=2;
UPDATE stall SET floor='1F',  window_no='3号窗口',  business_hours='10:00-20:00' WHERE id=3;
UPDATE stall SET floor='1F',  window_no='4号窗口',  business_hours='10:00-20:00' WHERE id=4;
UPDATE stall SET floor='2F',  window_no='5号窗口',  business_hours='10:00-21:00' WHERE id=5;
UPDATE stall SET floor='2F',  window_no='6号窗口',  business_hours='06:30-14:00' WHERE id=6;
UPDATE stall SET floor='1F',  window_no='7号窗口',  business_hours='10:00-22:00' WHERE id=7;
UPDATE stall SET floor='1F',  window_no='8号窗口',  business_hours='16:00-23:00' WHERE id=8;
UPDATE stall SET floor='B1',  window_no='9号窗口',  business_hours='17:00-02:00' WHERE id=9;
UPDATE stall SET floor='B1',  window_no='10号窗口', business_hours='10:00-23:00' WHERE id=10;
UPDATE stall SET floor='1F',  window_no='11号窗口', business_hours='10:00-21:00' WHERE id=11;
UPDATE stall SET floor='1F',  window_no='12号窗口', business_hours='17:00-23:00' WHERE id=12;
UPDATE stall SET floor='2F',  window_no='13号窗口', business_hours='10:00-21:00' WHERE id=13;
UPDATE stall SET floor='2F',  window_no='14号窗口', business_hours='07:00-14:00' WHERE id=14;

-- 菜品：先给全部菜品一个基础属性，再对部分招牌/特征菜做差异化
UPDATE dish SET spice_level=1, portion=1, serve_period='lunch,dinner', limited=0
    WHERE spice_level=0 AND serve_period='';

UPDATE dish SET spice_level=2, portion=1, serve_period='lunch,dinner' WHERE id=1;   -- 宫保鸡丁
UPDATE dish SET spice_level=3, portion=2, serve_period='lunch,dinner' WHERE id=2;   -- 水煮牛肉
UPDATE dish SET spice_level=2, portion=1, serve_period='lunch,dinner' WHERE id=3;   -- 回锅肉
UPDATE dish SET spice_level=0, portion=1, serve_period='lunch,dinner' WHERE id=4;   -- 番茄炒蛋
UPDATE dish SET spice_level=1, portion=1, serve_period='lunch,dinner' WHERE id=6;   -- 牛肉拉面
UPDATE dish SET spice_level=3, portion=2, serve_period='dinner'       WHERE id=9;   -- 香辣虾
UPDATE dish SET spice_level=2, portion=2, serve_period='lunch,dinner' WHERE id=12;  -- 骨汤麻辣烫
UPDATE dish SET spice_level=3, portion=1, serve_period='dinner'       WHERE id=13;  -- 冒脑花
UPDATE dish SET spice_level=0, portion=1, serve_period='breakfast,lunch' WHERE id=14; -- 皮蛋瘦肉粥
UPDATE dish SET spice_level=0, portion=1, serve_period='breakfast,lunch' WHERE id=15; -- 广式肠粉
UPDATE dish SET spice_level=1, portion=1, serve_period='midnight'     WHERE id=19;  -- 炒粉
UPDATE dish SET spice_level=1, portion=1, serve_period='midnight'     WHERE id=20;  -- 烤冷面
UPDATE dish SET spice_level=0, portion=1, serve_period='lunch,dinner' WHERE id=22;  -- 珍珠奶茶
UPDATE dish SET spice_level=0, portion=1, serve_period='lunch,dinner' WHERE id=23;  -- 杨枝甘露
UPDATE dish SET spice_level=1, portion=1, serve_period='lunch,dinner' WHERE id=24;  -- 兰州牛肉面
UPDATE dish SET spice_level=2, portion=1, serve_period='dinner,midnight' WHERE id=26; -- 羊肉串
UPDATE dish SET spice_level=0, portion=1, serve_period='breakfast,lunch' WHERE id=29; -- 鲜虾烧卖
UPDATE dish SET spice_level=0, portion=1, serve_period='breakfast,lunch' WHERE id=30; -- 叉烧包

-- 地域（美食来源地，与食堂位置无关）：按菜品特征推断
UPDATE dish SET region='川湘'   WHERE id IN (1,2,3);      -- 宫保鸡丁/水煮牛肉/回锅肉
UPDATE dish SET region='清真'   WHERE id IN (6,24,26);    -- 牛肉拉面/兰州牛肉面/羊肉串
UPDATE dish SET region='粤式'   WHERE id IN (15,22,29,30);-- 广式肠粉/珍珠奶茶/鲜虾烧卖/叉烧包
UPDATE dish SET region='东北'   WHERE id IN (19,20);      -- 炒粉/烤冷面
UPDATE dish SET region='西北'   WHERE id=12;              -- 骨汤麻辣烫
UPDATE dish SET region='川湘'   WHERE id IN (9,13);       -- 香辣虾/冒脑花

-- -------------------- 菜品折扣（促销角标/划线价演示；promo_price 非空视为有折扣；幂等 UPDATE 可重复执行） --------------------
UPDATE dish SET original_price=2000, promo_price=1600 WHERE id=1;   -- 宫保鸡丁 20.00 → 16.00
UPDATE dish SET original_price=3200, promo_price=2800 WHERE id=2;   -- 水煮牛肉 32.00 → 28.00
UPDATE dish SET original_price=2500, promo_price=2000 WHERE id=18;  -- 烤五花肉 25.00 → 20.00
UPDATE dish SET original_price=2400, promo_price=2000 WHERE id=10;  -- 招牌烤肉饭 24.00 → 20.00
UPDATE dish SET original_price=1300, promo_price=1100 WHERE id=21;  -- 烤冷面 13.00 → 11.00
UPDATE dish SET original_price=1200, promo_price=1000 WHERE id=22;  -- 珍珠奶茶 12.00 → 10.00
UPDATE dish SET original_price=2400, promo_price=2000 WHERE id=26;  -- 羊肉串 24.00 → 20.00

-- -------------------- 消息通知（演示个人中心红点与通知列表；无唯一键，先清后插保证可重复执行） --------------------
-- 类型 dish_audit / feedback_handle 与后端 NotificationConst 一致；related_id 指向真实菜品 / 反馈 ID。
DELETE FROM notification;
INSERT INTO notification (user_id, type, title, content, related_id, is_read, created_at) VALUES
(2, 'dish_audit', '菜品审核通过', '您提交的菜品「牛肉拉面」已通过审核，可以在对应档口查看。', 6, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(4, 'dish_audit', '菜品审核通过', '您提交的菜品「珍珠奶茶」已通过审核，可以在对应档口查看。', 22, 1, DATE_SUB(NOW(), INTERVAL 2 DAY));

SET FOREIGN_KEY_CHECKS = 1;
