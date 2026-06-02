-- BJTU Food seed data, researched conservative edition
-- Target database: bjtu_food
-- Character set: utf8mb4
--
-- Modeling rule:
-- 1. canteen = physical dining venue/building, such as 明湖餐厅 or 学生活动中心餐厅.
-- 2. stall = floor/zone/window inside a canteen, such as 学生二餐厅、学生三餐厅、清真餐厅.
-- 3. Do not split different floors of the same venue into different canteen rows.
--
-- Confidence rule:
-- 1. Official/publicly indexed BJTU sources are used for venue names and some layout notes.
-- 2. Map/review/social-media keywords are only used as supporting clues.
-- 3. Dish rows marked as "演示菜品" are realistic development seed data, not official fixed menus.
--
-- Public references checked while preparing this file:
-- - 北京交通大学学生活动服务中心资料：一层学生二餐厅、二层学生三餐厅、三层清真餐厅。
-- - 北京交通大学新闻网/信息中心公开内容：可见明湖、学苑、留园、学四、学生食堂等名称。
-- - 北京交通大学新闻网学苑餐厅厨师技能比赛：出现酸辣土豆丝、农家小炒肉、干煸豆角等菜品。
-- - 第三方食宿/地图/点评聚合内容：可见学一/学二/学三/学四/清真/东区/学苑/明湖/
--   益民/西式快餐等名称，以及部分推荐菜关键词。
--
-- Image URL rule:
-- - Seed image files are stored under backend/uploads/images/seed.
-- - Spring Boot maps upload.path=./uploads/images to /api/images/**.
-- - Relative URL example: /images/seed/dishes/tomato-egg.jpg
-- - The backend converts relative image paths to full URLs using app.public-base-url.
-- - If you require HTTPS, add TLS through Nginx/Caddy or Spring Boot SSL first.

SET NAMES utf8mb4;

INSERT IGNORE INTO canteen
    (id, name, images, location, description, sort_order, status)
VALUES
    (1, '明湖餐厅', '["/images/seed/canteens/canteen-dining-hall.jpg"]', '主校区明湖附近', '主校区学生餐饮点。公开资料中可见明湖餐厅相关报道；按物理餐厅建模，楼层与窗口放入 stall。', 1, 'open'),
    (2, '学生活动中心餐厅', '["/images/seed/canteens/canteen-food-counter.jpg"]', '学生活动服务中心', '按同一物理建筑建模。公开资料显示该中心一层为学生二餐厅、二层为学生三餐厅、三层为清真餐厅。', 2, 'open'),
    (3, '学生四餐厅', '["/images/seed/canteens/canteen-dining-hall.jpg"]', '主校区学生生活区', '公开食宿资料与校园报道中可见学生四餐厅、学四二层风味餐厅等名称。', 3, 'open'),
    (4, '东校区学生餐厅', '["/images/seed/canteens/canteen-food-counter.jpg"]', '东校区', '服务东校区日常就餐。公开食宿资料中可见东区餐厅、东区清真餐厅等相关名称。', 4, 'open'),
    (5, '学苑公寓学生餐厅', '["/images/seed/canteens/canteen-dining-hall.jpg"]', '学苑公寓/交大东路附近', '北京交通大学新闻网曾提及学苑餐厅及厨师技能比赛；适合承载学苑公寓周边就餐数据。', 5, 'open'),
    (6, '留园餐厅', '["/images/seed/canteens/canteen-food-counter.jpg"]', '主校区留园附近', '北京交通大学新闻网报道中提及留园餐厅；地图/点评平台也可见相关条目。', 6, 'open'),
    (7, '益民餐厅', '["/images/seed/canteens/canteen-dining-hall.jpg"]', '主校区', '地图/点评平台可见“益民餐厅（交通大学店）”相关条目；作为开发测试数据保留，后续建议实地核验。', 7, 'open'),
    (8, '西式快餐厅', '["/images/seed/dishes/hamburger.jpg"]', '主校区', '第三方食宿信息与地图/点评平台可见西式快餐/西餐厅相关信息；作为开发测试数据保留，后续建议实地核验。', 8, 'open');

INSERT IGNORE INTO stall
    (id, canteen_id, name, images, location, description, avg_rating, sort_order, status)
VALUES
    (1, 1, '明湖一层基本伙食窗口', '["/images/seed/canteens/canteen-food-counter.jpg"]', '明湖餐厅一层', '基础大伙、米饭套餐、热菜窗口。用于展示日常高频饭菜。', 4.2, 1, 'open'),
    (2, 1, '明湖风味面食窗口', '["/images/seed/dishes/malatang-counter.jpg"]', '明湖餐厅', '承载风味面食、小吃、砂锅等数据。公开资料曾提到风味面食小吃。', 4.3, 2, 'open'),
    (3, 1, '明湖小炒套餐窗口', '["/images/seed/dishes/kung-pao-chicken.jpg"]', '明湖餐厅', '承载小炒、套餐类数据。公开资料曾提到小炒、套餐等供餐形态。', 4.3, 3, 'open'),
    (4, 2, '学生二餐厅', '["/images/seed/canteens/canteen-food-counter.jpg"]', '学生活动服务中心一层', '官方学生活动服务中心资料确认的一层餐厅；这里作为学活餐厅内部楼层/区域建模。', 4.3, 1, 'open'),
    (5, 2, '学生三餐厅', '["/images/seed/canteens/canteen-dining-hall.jpg"]', '学生活动服务中心二层', '官方学生活动服务中心资料确认的二层餐厅；这里作为学活餐厅内部楼层/区域建模。', 4.3, 2, 'open'),
    (6, 2, '清真餐厅', '["/images/seed/dishes/beef-noodle.jpg"]', '学生活动服务中心三层', '官方学生活动服务中心资料确认的三层清真餐厅。', 4.5, 3, 'open'),
    (7, 2, '学活风味快餐窗口', '["/images/seed/dishes/pasta.jpg"]', '学生活动服务中心', '用于承载学活区域常见快餐、焗饭、面食等演示数据。', 4.2, 4, 'open'),
    (8, 3, '学四基本伙食窗口', '["/images/seed/canteens/canteen-food-counter.jpg"]', '学生四餐厅', '学生四餐厅日常餐食窗口。', 4.2, 1, 'open'),
    (9, 3, '学四二层风味餐厅', '["/images/seed/dishes/malatang-counter.jpg"]', '学生四餐厅二层', '公开食宿资料中可见学四二层风味餐厅说法；作为学生四餐厅内部楼层建模。', 4.1, 2, 'open'),
    (10, 3, '学四保障套餐窗口', '["/images/seed/dishes/fried-rice.jpg"]', '学生四餐厅', '用于承载套餐、盒饭、活动供餐等演示数据。', 4.2, 3, 'open'),
    (11, 4, '东区基本伙食窗口', '["/images/seed/canteens/canteen-food-counter.jpg"]', '东校区学生餐厅', '服务东校区学生日常就餐。', 4.2, 1, 'open'),
    (12, 4, '东区清真窗口', '["/images/seed/dishes/beef-noodle.jpg"]', '东校区学生餐厅', '第三方食宿资料中出现东区清真餐厅名称；这里作为东校区学生餐厅内部窗口建模。', 4.4, 2, 'open'),
    (13, 5, '学苑基本伙食窗口', '["/images/seed/dishes/tomato-egg.jpg"]', '学苑公寓学生餐厅', '靠近学苑公寓，承载日常套餐与家常菜数据。', 4.2, 1, 'open'),
    (14, 5, '学苑风味家常菜窗口', '["/images/seed/dishes/kung-pao-chicken.jpg"]', '学苑公寓学生餐厅', '学苑餐厅厨师技能比赛报道中出现多道家常菜，可作为较可信的菜品来源。', 4.3, 2, 'open'),
    (15, 5, '学苑清真/面食窗口', '["/images/seed/dishes/beef-noodle.jpg"]', '学苑公寓学生餐厅', '地图/点评内容中有学苑餐厅与清真、面食相关评价；用于联调。', 4.3, 3, 'open'),
    (16, 6, '留园打餐窗口', '["/images/seed/canteens/canteen-food-counter.jpg"]', '留园餐厅', '地图/点评内容称留园为排队打餐式食堂，菜品较丰富。', 4.4, 1, 'open'),
    (17, 6, '留园家常套餐窗口', '["/images/seed/dishes/fried-rice.jpg"]', '留园餐厅', '用于承载留园区域家常套餐与双拼饭演示数据。', 4.3, 2, 'open'),
    (18, 7, '益民家常菜窗口', '["/images/seed/dishes/dumpling.jpg"]', '益民餐厅', '益民餐厅开发演示窗口，等待后续用实地采集数据替换。', 4.2, 1, 'open'),
    (19, 8, '西式快餐窗口', '["/images/seed/dishes/hamburger.jpg"]', '西式快餐厅', '地图/点评中可见汉堡、披萨、意面等西式快餐关键词。', 4.1, 1, 'open');

INSERT IGNORE INTO user
    (id, username, password, nickname, avatar, role, stall_id, status)
VALUES
    (1, '20240001', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhiI1S.kp6VqL0mLRzv4Y5h9SR7hH4wS', '交大学子', '/images/seed/dishes/tomato-egg.jpg', 'student', NULL, 'active'),
    (2, 'admin001', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhiI1S.kp6VqL0mLRzv4Y5h9SR7hH4wS', '学活管理员', '/images/seed/canteens/canteen-food-counter.jpg', 'canteen_admin', 4, 'active'),
    (3, 'sysadmin', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhiI1S.kp6VqL0mLRzv4Y5h9SR7hH4wS', '系统管理员', '/images/seed/canteens/canteen-dining-hall.jpg', 'sys_admin', NULL, 'active');

INSERT IGNORE INTO dish
    (id, stall_id, name, images, price, tags, description, avg_rating, rating_count, favorite_count, view_count, status)
VALUES
    (1, 1, '番茄炒蛋盖饭', '["/images/seed/dishes/tomato-egg.jpg"]', 1200, 'daily,recommended', '演示菜品：学生餐厅常见基础套餐，用于首页与列表联调。', 4.3, 16, 28, 260, 'on'),
    (2, 1, '宫保鸡丁套餐', '["/images/seed/dishes/kung-pao-chicken.jpg"]', 1500, 'daily,signature', '演示菜品：学生餐厅常见基础套餐，用于首页与列表联调。', 4.2, 14, 24, 220, 'on'),
    (3, 2, '砂锅豆腐', '["/images/seed/dishes/tomato-egg.jpg"]', 600, 'recommended,low-price', '公开点评/地图推荐关键词中出现砂锅豆腐；非官方固定菜单。', 4.3, 8, 15, 150, 'on'),
    (4, 2, '牛肉汤', '["/images/seed/dishes/beef-noodle.jpg"]', 1400, 'recommended', '公开点评/地图推荐关键词中出现牛肉汤；非官方固定菜单。', 4.2, 6, 12, 130, 'on'),
    (5, 2, '烤冷面', '["/images/seed/dishes/malatang-counter.jpg"]', 900, 'snack,recommended', '公开点评/地图推荐关键词中出现烤冷面；非官方固定菜单。', 4.0, 5, 8, 100, 'on'),
    (6, 3, '烤鸭单人餐', '["/images/seed/dishes/kung-pao-chicken.jpg"]', 2400, 'recommended,signature', '公开点评/地图推荐关键词中出现烤鸭；非官方固定菜单。', 4.4, 9, 18, 180, 'on'),
    (7, 3, '麻辣鸡扒饭', '["/images/seed/dishes/kung-pao-chicken.jpg"]', 1500, 'recommended,spicy', '公开点评/地图推荐关键词中出现麻辣鸡扒饭；非官方固定菜单。', 4.0, 5, 9, 110, 'on'),
    (8, 4, '土豆牛肉焗饭', '["/images/seed/dishes/fried-rice.jpg"]', 1800, 'signature', '公开点评/地图推荐关键词中出现土豆牛肉焗饭；非官方固定菜单。', 4.2, 7, 16, 160, 'on'),
    (9, 4, '咖喱鸡肉饭', '["/images/seed/dishes/fried-rice.jpg"]', 1200, 'recommended', '公开点评/地图推荐关键词中出现咖喱鸡肉饭；非官方固定菜单。', 4.3, 9, 19, 190, 'on'),
    (10, 5, '日式牛肉面', '["/images/seed/dishes/beef-noodle.jpg"]', 1600, 'recommended,noodle', '公开点评/地图推荐关键词中出现日式牛肉面；非官方固定菜单。', 4.1, 6, 14, 140, 'on'),
    (11, 5, '旋转小火锅', '["/images/seed/stalls/hotpot-restaurant.jpg"]', 2000, 'recommended,hotpot', '公开点评/地图推荐关键词中出现旋转小火锅；非官方固定菜单。', 4.2, 7, 14, 140, 'on'),
    (12, 6, '牛肉拉面', '["/images/seed/dishes/beef-noodle.jpg"]', 1600, 'halal,recommended,signature', '演示菜品：清真餐厅常见面食，用于联调。', 4.5, 12, 24, 240, 'on'),
    (13, 6, '孜然羊肉盖饭', '["/images/seed/dishes/fried-rice.jpg"]', 1800, 'halal,signature', '演示菜品：清真风味菜品，用于联调。', 4.5, 10, 21, 210, 'on'),
    (14, 7, '酥肉罐罐土豆粉', '["/images/seed/dishes/beef-noodle.jpg"]', 1600, 'signature,noodle', '公开点评/地图推荐关键词中出现酥肉罐罐土豆粉；非官方固定菜单。', 4.1, 6, 11, 120, 'on'),
    (15, 7, '鸡排意面套餐', '["/images/seed/dishes/pasta.jpg"]', 1800, 'western,recommended', '演示菜品：学活风味快餐数据，用于联调。', 4.1, 6, 10, 115, 'on'),
    (16, 8, '鱼香肉丝套餐', '["/images/seed/dishes/kung-pao-chicken.jpg"]', 1500, 'daily,recommended', '演示菜品：学生餐厅常见套餐，用于联调。', 4.2, 7, 13, 130, 'on'),
    (17, 8, '红烧鸡块饭', '["/images/seed/dishes/kung-pao-chicken.jpg"]', 1600, 'daily', '演示菜品：学生餐厅常见套餐，用于联调。', 4.2, 7, 13, 125, 'on'),
    (18, 9, '麻辣烫自选', '["/images/seed/dishes/malatang-counter.jpg"]', 2200, 'spicy,recommended', '演示菜品：风味窗口常见菜品，用于联调。', 4.1, 9, 17, 170, 'on'),
    (19, 9, '酸辣粉', '["/images/seed/dishes/beef-noodle.jpg"]', 1200, 'spicy,noodle', '演示菜品：风味窗口常见菜品，用于联调。', 4.0, 6, 9, 105, 'on'),
    (20, 10, '奥尔良鸡腿盒饭', '["/images/seed/dishes/kung-pao-chicken.jpg"]', 1800, 'combo,recommended', '演示菜品：套餐、盒饭、活动供餐数据，用于联调。', 4.2, 6, 12, 130, 'on'),
    (21, 11, '东区鸡肉盖饭', '["/images/seed/dishes/fried-rice.jpg"]', 1400, 'daily,recommended', '演示菜品：东校区学生餐厅数据占位，用于联调。', 4.1, 6, 12, 120, 'on'),
    (22, 11, '东区素菜双拼饭', '["/images/seed/dishes/tomato-egg.jpg"]', 1100, 'daily,vegetarian', '演示菜品：东校区学生餐厅数据占位，用于联调。', 4.1, 5, 10, 95, 'on'),
    (23, 12, '清真牛肉炒饭', '["/images/seed/dishes/fried-rice.jpg"]', 1600, 'halal,signature', '演示菜品：东区清真窗口数据占位，用于联调。', 4.3, 7, 14, 140, 'on'),
    (24, 12, '清真鸡肉拌面', '["/images/seed/dishes/beef-noodle.jpg"]', 1500, 'halal,noodle', '演示菜品：东区清真窗口数据占位，用于联调。', 4.3, 6, 12, 128, 'on'),
    (25, 13, '学苑家常套餐', '["/images/seed/dishes/tomato-egg.jpg"]', 1300, 'daily,recommended', '演示菜品：学苑餐厅日常套餐数据，用于联调。', 4.2, 8, 16, 160, 'on'),
    (26, 13, '低油低盐套餐', '["/images/seed/dishes/tomato-egg.jpg"]', 1400, 'healthy,daily', '演示菜品：结合公开活动中“低油低盐”诉求设置的健康餐数据。', 4.2, 6, 11, 120, 'on'),
    (27, 14, '酸辣土豆丝', '["/images/seed/dishes/tomato-egg.jpg"]', 800, 'xueyuan,competition,vegetarian', '较可信菜品：学苑餐厅厨师技能比赛报道中出现。', 4.3, 8, 15, 150, 'on'),
    (28, 14, '农家小炒肉', '["/images/seed/dishes/kung-pao-chicken.jpg"]', 1600, 'xueyuan,competition,signature', '较可信菜品：学苑餐厅厨师技能比赛报道中出现。', 4.4, 10, 18, 180, 'on'),
    (29, 14, '干煸豆角', '["/images/seed/dishes/tomato-egg.jpg"]', 1000, 'xueyuan,competition,vegetarian', '较可信菜品：学苑餐厅厨师技能比赛报道中出现。', 4.3, 7, 13, 140, 'on'),
    (30, 14, '芫爆肉丝', '["/images/seed/dishes/kung-pao-chicken.jpg"]', 1600, 'xueyuan,competition', '较可信菜品：学苑餐厅厨师技能比赛报道中出现。', 4.2, 6, 11, 130, 'on'),
    (31, 14, '铁钵娃娃菜', '["/images/seed/dishes/tomato-egg.jpg"]', 1200, 'xueyuan,competition,vegetarian', '较可信菜品：学苑餐厅厨师技能比赛报道中出现。', 4.2, 6, 10, 120, 'on'),
    (32, 14, '排骨焖蛋', '["/images/seed/dishes/kung-pao-chicken.jpg"]', 1800, 'xueyuan,competition,signature', '较可信菜品：学苑餐厅厨师技能比赛报道中出现。', 4.4, 9, 16, 170, 'on'),
    (33, 15, '学苑清真鸡肉饭', '["/images/seed/dishes/fried-rice.jpg"]', 1500, 'halal,recommended', '演示菜品：学苑清真/面食窗口数据占位，用于联调。', 4.2, 7, 13, 130, 'on'),
    (34, 15, '牛肉刀削面', '["/images/seed/dishes/beef-noodle.jpg"]', 1600, 'halal,noodle', '演示菜品：学苑清真/面食窗口数据占位，用于联调。', 4.3, 7, 14, 145, 'on'),
    (35, 16, '留园双拼饭', '["/images/seed/dishes/fried-rice.jpg"]', 1800, 'recommended,combo', '演示菜品：留园餐厅数据占位，用于联调。', 4.4, 9, 18, 180, 'on'),
    (36, 16, '留园家常小炒', '["/images/seed/dishes/kung-pao-chicken.jpg"]', 1700, 'daily', '演示菜品：留园餐厅数据占位，用于联调。', 4.3, 7, 14, 150, 'on'),
    (37, 17, '留园素菜套餐', '["/images/seed/dishes/tomato-egg.jpg"]', 1200, 'vegetarian,daily', '演示菜品：留园家常套餐窗口数据，用于联调。', 4.2, 6, 12, 120, 'on'),
    (38, 18, '益民家常盖饭', '["/images/seed/dishes/fried-rice.jpg"]', 1500, 'daily,recommended', '演示菜品：益民餐厅数据占位，用于联调，后续建议实地核验。', 4.1, 5, 10, 100, 'on'),
    (39, 18, '益民水饺', '["/images/seed/dishes/dumpling.jpg"]', 1600, 'daily', '演示菜品：益民餐厅数据占位，用于联调，后续建议实地核验。', 4.1, 5, 9, 98, 'on'),
    (40, 19, '汉堡套餐', '["/images/seed/dishes/hamburger.jpg"]', 1400, 'western,recommended', '公开点评/地图相关条目中出现汉堡；非官方固定菜单。', 4.1, 6, 12, 120, 'on'),
    (41, 19, '披萨套餐', '["/images/seed/dishes/pizza.jpg"]', 1800, 'western,signature', '公开点评/地图相关条目中出现披萨；非官方固定菜单。', 4.0, 5, 9, 100, 'on'),
    (42, 19, '意面套餐', '["/images/seed/dishes/pasta.jpg"]', 1600, 'western', '演示菜品：西式快餐窗口数据，用于联调。', 4.0, 5, 9, 95, 'on');

INSERT IGNORE INTO favorite
    (id, user_id, dish_id)
VALUES
    (1, 1, 6),
    (2, 1, 11),
    (3, 1, 12),
    (4, 1, 28),
    (5, 1, 35),
    (6, 1, 40);

INSERT IGNORE INTO review
    (id, user_id, dish_id, rating, content, images, is_hidden)
VALUES
    (1, 1, 6, 4, '演示评价：明湖餐厅推荐菜数据，适合作为首页展示。', '[]', 0),
    (2, 1, 11, 4, '演示评价：小火锅适合朋友一起吃。', '["/images/seed/stalls/hotpot-restaurant.jpg"]', 0),
    (3, 1, 12, 5, '演示评价：清真面食窗口数据。', '[]', 0),
    (4, 1, 28, 5, '演示评价：学苑家常菜数据，来源边界已在菜品描述中标注。', '[]', 0),
    (5, 1, 35, 4, '演示评价：留园餐厅菜品测试。', '["/images/seed/dishes/fried-rice.jpg"]', 0),
    (6, 1, 40, 4, '演示评价：西式快餐窗口联调数据。', '[]', 0);

INSERT IGNORE INTO banner
    (id, title, images, type, target_id, target_url, canteen_id, sort_order, status, subtitle)
VALUES
    (1, '明湖餐厅推荐', '["/images/seed/dishes/kung-pao-chicken.jpg"]', 'dish', 6, NULL, 1, 1, 'enabled', '明湖附近的学生餐饮、风味面食与小炒套餐'),
    (2, '学活食堂风味', '["/images/seed/canteens/canteen-food-counter.jpg"]', 'dish', 8, NULL, 2, 2, 'enabled', '学生二餐厅、学生三餐厅与清真餐厅同属学活餐厅'),
    (3, '学苑家常菜', '["/images/seed/dishes/kung-pao-chicken.jpg"]', 'dish', 28, NULL, 5, 3, 'enabled', '学苑公寓附近的日常就餐与家常菜'),
    (4, '留园餐厅', '["/images/seed/canteens/canteen-dining-hall.jpg"]', 'canteen', NULL, NULL, 6, 4, 'enabled', '留园附近的打餐窗口与套餐选择');

