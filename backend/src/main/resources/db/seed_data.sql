-- =============================================================
-- 食在交大 演示数据种子（一次性执行，非自动加载）
-- 说明：项目仅提供 schema.sql，运行期数据需手动写入。
-- 执行：mysql -u <user> -p <pwd> -h localhost bjtu_food < seed_data.sql
-- 注意：重复执行会重复插入，请勿多次运行。
-- 金额字段单位：分（如 1600 = 16.00 元）
-- images 置 NULL，由前端占位图（emoji）优雅降级，避免小程序外链域名限制。
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------- 食堂（在原有「学一食堂」id=1 基础上补充） --------------------
INSERT INTO canteen (name, location, description, status, sort_order, audit_status) VALUES
('学二食堂', '学苑区一栋', '明亮整洁，家常味道', 'open', 2, 'approved'),
('学三食堂', '学苑区二栋', '品类丰富，平价美味', 'open', 3, 'approved'),
('明湖餐厅', '明湖旁',     '湖景餐厅，聚餐首选', 'open', 4, 'approved'),
('嘉园餐厅', '嘉园公寓',   '夜宵与小吃天堂',     'open', 5, 'approved'),
('清真食堂', '学苑区',     '清真风味，干净卫生', 'open', 6, 'approved'),
('留园餐厅', '留园区',     '精致小炒与面点',     'open', 7, 'approved');

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

-- -------------------- 菜品（stall_id 对应上面档口；价格单位：分） --------------------
INSERT INTO dish (stall_id, name, price, description, images, tags, status, audit_status, view_count, favorite_count, avg_rating, rating_count) VALUES
(1,  '宫保鸡丁',   1600, '酸甜微辣，下饭神器',           NULL, 'recommended,signature', 'on', 'approved', 560, 120, 4.7, 120),
(1,  '水煮牛肉',   2800, '麻辣鲜香，分量十足',           NULL, 'signature',            'on', 'approved', 720,  98, 4.8,  98),
(1,  '回锅肉',     1800, '肥而不腻，川味经典',           NULL, 'recommended',          'on', 'approved', 430,  76, 4.6,  76),
(2,  '番茄炒蛋',    900, '家常味道，酸甜可口',           NULL, 'recommended',          'on', 'approved', 610, 150, 4.5, 150),
(2,  '土豆烧牛肉', 2200, '软烂入味，暖心暖胃',           NULL, '',                    'on', 'approved', 380,  64, 4.4,  64),
(3,  '牛肉拉面',   1500, '筋道爽滑，汤头浓郁',           NULL, 'signature',            'on', 'approved', 880, 200, 4.7, 200),
(3,  '鲜肉小笼',   1200, '皮薄汁多，一口爆汁',           NULL, 'recommended',          'on', 'approved', 760, 180, 4.8, 180),
(4,  '黄焖鸡米饭', 1800, '酱香浓郁，鸡肉嫩滑',           NULL, 'recommended',          'on', 'approved', 690, 140, 4.6, 140),
(4,  '香辣虾',     3200, '鲜香麻辣，弹牙爽口',           NULL, 'signature',            'on', 'approved', 320,  55, 4.5,  55),
(5,  '招牌烤肉饭', 2000, '肉香四溢，粒粒分明',           NULL, 'recommended',          'on', 'approved', 700, 130, 4.7, 130),
(5,  '咖喱鸡排饭', 1900, '咖喱醇厚，外酥里嫩',           NULL, '',                    'on', 'approved', 410,  88, 4.4,  88),
(6,  '骨汤麻辣烫', 1700, '自选食材，麻辣鲜香',           NULL, 'recommended',          'on', 'approved', 820, 160, 4.6, 160),
(6,  '冒脑花',     1500, '嫩滑入味，辣得过瘾',           NULL, 'signature',            'on', 'approved', 260,  42, 4.3,  42),
(7,  '皮蛋瘦肉粥',  800, '绵密温润，暖胃首选',           NULL, 'recommended',          'on', 'approved', 520, 110, 4.5, 110),
(7,  '广式肠粉',   1000, '晶莹剔透，酱香清爽',           NULL, '',                    'on', 'approved', 470,  95, 4.6,  95),
(8,  '干锅花菜',   1600, '爽脆下饭，锅气十足',           NULL, 'recommended',          'on', 'approved', 390,  70, 4.5,  70),
(8,  '糖醋里脊',   2100, '外酥里嫩，酸甜开胃',           NULL, 'signature',            'on', 'approved', 640, 120, 4.7, 120),
(9,  '烤五花肉',   2500, '滋滋冒油，焦香四溢',           NULL, 'recommended',          'on', 'approved', 780, 140, 4.8, 140),
(9,  '烤茄子',     1200, '蒜香浓郁，软糯鲜甜',           NULL, '',                    'on', 'approved', 300,  60, 4.4,  60),
(10, '炒粉',       1300, '镬气十足，宵夜之王',           NULL, 'recommended',          'on', 'approved', 700, 150, 4.6, 150),
(10, '烤冷面',     1100, '酸甜筋道，东北风味',           NULL, 'signature',            'on', 'approved', 560, 130, 4.5, 130),
(11, '珍珠奶茶',   1000, 'Q弹珍珠，奶香醇厚',            NULL, 'recommended',          'on', 'approved', 980, 220, 4.7, 220),
(11, '杨枝甘露',   1400, '芒果西米，清甜解腻',           NULL, 'signature',            'on', 'approved', 840, 190, 4.8, 190),
(12, '兰州牛肉面', 1500, '一清二白，汤鲜面劲',           NULL, 'signature',            'on', 'approved', 900, 210, 4.8, 210),
(12, '羊肉泡馍',   2000, '馍香肉烂，汤浓味厚',           NULL, 'recommended',          'on', 'approved', 460,  80, 4.6,  80),
(13, '羊肉串',     2000, '孜然飘香，外焦里嫩',           NULL, 'recommended',          'on', 'approved', 720, 160, 4.7, 160),
(13, '烤馕',        900, '金黄酥脆，麦香十足',           NULL, '',                    'on', 'approved', 320,  70, 4.5,  70),
(14, '鱼香茄子',   1400, '咸鲜微甜，超级下饭',           NULL, 'recommended',          'on', 'approved', 500,  90, 4.5,  90),
(14, '宫保虾球',   3000, '荔枝口型，弹嫩鲜香',           NULL, 'signature',            'on', 'approved', 360,  60, 4.6,  60),
(15, '鲜虾烧卖',   1300, '皮薄馅大，鲜香多汁',           NULL, 'recommended',          'on', 'approved', 580, 110, 4.7, 110),
(15, '叉烧包',     1000, '松软甜香，广式经典',           NULL, '',                    'on', 'approved', 520, 100, 4.6, 100);

-- -------------------- 轮播图（status=enabled 才会被首页展示） --------------------
INSERT INTO banner (title, subtitle, images, target_type, target_id, status, sort_order) VALUES
('食在交大',   '校园美食一站式发现', NULL, 'NONE',     NULL, 'enabled', 1),
('今日上新',   '新鲜菜品抢先看',     NULL, 'DISH',     4,    'enabled', 2),
('限时特惠',   '活动菜品低至8折',    NULL, 'ACTIVITY', 1,    'enabled', 3),
('食堂探店',   '发现身边的好味道',   NULL, 'NONE',     NULL, 'enabled', 4);

-- -------------------- 活动（status=enabled 才会被「限时活动」展示） --------------------
INSERT INTO activity (title, description, cover_image, dish_id, activity_price, origin_price, official_account_url, start_time, end_time, status, sort_order) VALUES
('烤肉狂欢节',   '明湖烧烤 烤五花肉 限时8折',     NULL, 18, 2000, 2500, NULL, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 'enabled', 1),
('奶茶买一送一', '嘉园奶茶 珍珠奶茶 第二杯0元',   NULL, 22, 1000, 2000, NULL, NOW(), DATE_ADD(NOW(), INTERVAL 15 DAY), 'enabled', 2),
('清真拉面特惠', '兰州牛肉面 立减3元',           NULL, 24, 1200, 1500, NULL, NOW(), DATE_ADD(NOW(), INTERVAL 20 DAY), 'enabled', 3);

-- -------------------- 评价（为部分菜品填充评价，丰富详情页） --------------------
INSERT INTO review (user_id, dish_id, rating, content, images, is_hidden) VALUES
(1, 1,  5, '宫保鸡丁真的绝，下饭神器！',               NULL, 0),
(1, 2,  5, '水煮牛肉麻辣鲜香，分量很足',               NULL, 0),
(1, 6,  4, '牛肉拉面汤头浓郁，就是稍微有点咸',         NULL, 0),
(1, 18, 5, '烤五花肉滋滋冒油，太香了',                 NULL, 0),
(1, 22, 5, '珍珠奶茶奶香十足，珍珠很Q',                NULL, 0),
(1, 24, 5, '兰州牛肉面一清二白，地道！',               NULL, 0);

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

SET FOREIGN_KEY_CHECKS = 1;
