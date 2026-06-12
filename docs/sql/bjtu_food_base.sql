-- MySQL dump 10.13  Distrib 8.0.19, for Win64 (x86_64)
--
-- Host: localhost    Database: bjtu_food
-- ------------------------------------------------------
-- Server version	8.0.45

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `banner`
--

DROP TABLE IF EXISTS `banner`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `banner` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(100) NOT NULL,
  `subtitle` varchar(255) DEFAULT NULL,
  `type` varchar(30) NOT NULL DEFAULT 'activity',
  `target_id` bigint DEFAULT NULL,
  `target_url` varchar(255) DEFAULT NULL,
  `canteen_id` bigint DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL DEFAULT 'enabled',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `images` text,
  PRIMARY KEY (`id`),
  KEY `idx_banner_status` (`status`),
  KEY `idx_banner_canteen_id` (`canteen_id`),
  KEY `idx_banner_sort_order` (`sort_order`),
  CONSTRAINT `fk_banner_canteen` FOREIGN KEY (`canteen_id`) REFERENCES `canteen` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `banner`
--

LOCK TABLES `banner` WRITE;
/*!40000 ALTER TABLE `banner` DISABLE KEYS */;
INSERT INTO `banner` VALUES (1,'明湖餐厅推荐','明湖附近的学生餐饮、风味面食与小炒套餐','dish',6,NULL,1,1,'enabled','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/kung-pao-chicken.jpg\"]'),(2,'学活食堂风味','学生二餐厅、学生三餐厅与清真餐厅同属学活餐厅','dish',8,NULL,2,2,'enabled','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/canteens/canteen-food-counter.jpg\"]'),(3,'学苑家常菜','学苑公寓附近的日常就餐与家常菜','dish',28,NULL,5,3,'enabled','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/kung-pao-chicken.jpg\"]'),(4,'留园餐厅','留园附近的打餐窗口与套餐选择','canteen',NULL,NULL,6,4,'enabled','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/canteens/canteen-dining-hall.jpg\"]');
/*!40000 ALTER TABLE `banner` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `canteen`
--

DROP TABLE IF EXISTS `canteen`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `canteen` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `location` varchar(255) DEFAULT NULL,
  `description` text,
  `sort_order` int NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL DEFAULT 'open',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `images` text,
  PRIMARY KEY (`id`),
  KEY `idx_canteen_status` (`status`),
  KEY `idx_canteen_sort_order` (`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `canteen`
--

LOCK TABLES `canteen` WRITE;
/*!40000 ALTER TABLE `canteen` DISABLE KEYS */;
INSERT INTO `canteen` VALUES (1,'明湖餐厅','主校区明湖附近','主校区学生餐饮点。公开资料中可见明湖餐厅相关报道；按物理餐厅建模，楼层与窗口放入 stall。',1,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/canteens/canteen-dining-hall.jpg\"]'),(2,'学生活动中心餐厅','学生活动服务中心','按同一物理建筑建模。公开资料显示该中心一层为学生二餐厅、二层为学生三餐厅、三层为清真餐厅。',2,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/canteens/canteen-food-counter.jpg\"]'),(3,'学生四餐厅','主校区学生生活区','公开食宿资料与校园报道中可见学生四餐厅、学四二层风味餐厅等名称。',3,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/canteens/canteen-dining-hall.jpg\"]'),(4,'东校区学生餐厅','东校区','服务东校区日常就餐。公开食宿资料中可见东区餐厅、东区清真餐厅等相关名称。',4,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/canteens/canteen-food-counter.jpg\"]'),(5,'学苑公寓学生餐厅','学苑公寓/交大东路附近','北京交通大学新闻网曾提及学苑餐厅及厨师技能比赛；适合承载学苑公寓周边就餐数据。',5,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/canteens/canteen-dining-hall.jpg\"]'),(6,'留园餐厅','主校区留园附近','北京交通大学新闻网报道中提及留园餐厅；地图/点评平台也可见相关条目。',6,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/canteens/canteen-food-counter.jpg\"]'),(7,'益民餐厅','主校区','地图/点评平台可见“益民餐厅（交通大学店）”相关条目；作为开发测试数据保留，后续建议实地核验。',7,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/canteens/canteen-dining-hall.jpg\"]'),(8,'西式快餐厅','主校区','第三方食宿信息与地图/点评平台可见西式快餐/西餐厅相关信息；作为开发测试数据保留，后续建议实地核验。',8,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/hamburger.jpg\"]');
/*!40000 ALTER TABLE `canteen` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `dish`
--

DROP TABLE IF EXISTS `dish`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dish` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stall_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `price` int NOT NULL DEFAULT '0',
  `tags` varchar(255) NOT NULL DEFAULT '',
  `description` text,
  `avg_rating` decimal(2,1) NOT NULL DEFAULT '0.0',
  `rating_count` int NOT NULL DEFAULT '0',
  `favorite_count` int NOT NULL DEFAULT '0',
  `view_count` int NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL DEFAULT 'on',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `images` text,
  PRIMARY KEY (`id`),
  KEY `idx_dish_stall_id` (`stall_id`),
  KEY `idx_dish_status` (`status`),
  KEY `idx_dish_name` (`name`),
  KEY `idx_dish_rating` (`avg_rating`),
  KEY `idx_dish_favorite_count` (`favorite_count`),
  KEY `idx_dish_price` (`price`),
  CONSTRAINT `fk_dish_stall` FOREIGN KEY (`stall_id`) REFERENCES `stall` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `dish`
--

LOCK TABLES `dish` WRITE;
/*!40000 ALTER TABLE `dish` DISABLE KEYS */;
INSERT INTO `dish` VALUES (1,1,'番茄炒蛋盖饭',1200,'daily,recommended','演示菜品：学生餐厅常见基础套餐，用于首页与列表联调。',4.3,16,28,260,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/tomato-egg.jpg\"]'),(2,1,'宫保鸡丁套餐',1500,'daily,signature','演示菜品：学生餐厅常见基础套餐，用于首页与列表联调。',4.2,14,24,220,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/kung-pao-chicken.jpg\"]'),(3,2,'砂锅豆腐',600,'recommended,low-price','公开点评/地图推荐关键词中出现砂锅豆腐；非官方固定菜单。',4.3,8,15,150,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/tomato-egg.jpg\"]'),(4,2,'牛肉汤',1400,'recommended','公开点评/地图推荐关键词中出现牛肉汤；非官方固定菜单。',4.2,6,12,130,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/beef-noodle.jpg\"]'),(5,2,'烤冷面',900,'snack,recommended','公开点评/地图推荐关键词中出现烤冷面；非官方固定菜单。',4.0,5,8,100,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/malatang-counter.jpg\"]'),(6,3,'烤鸭单人餐',2400,'recommended,signature','公开点评/地图推荐关键词中出现烤鸭；非官方固定菜单。',4.4,9,18,180,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/kung-pao-chicken.jpg\"]'),(7,3,'麻辣鸡扒饭',1500,'recommended,spicy','公开点评/地图推荐关键词中出现麻辣鸡扒饭；非官方固定菜单。',4.0,5,9,110,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/kung-pao-chicken.jpg\"]'),(8,4,'土豆牛肉焗饭',1800,'signature','公开点评/地图推荐关键词中出现土豆牛肉焗饭；非官方固定菜单。',4.2,7,16,160,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/fried-rice.jpg\"]'),(9,4,'咖喱鸡肉饭',1200,'recommended','公开点评/地图推荐关键词中出现咖喱鸡肉饭；非官方固定菜单。',4.3,9,19,190,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/fried-rice.jpg\"]'),(10,5,'日式牛肉面',1600,'recommended,noodle','公开点评/地图推荐关键词中出现日式牛肉面；非官方固定菜单。',4.1,6,14,140,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/beef-noodle.jpg\"]'),(11,5,'旋转小火锅',2000,'recommended,hotpot','公开点评/地图推荐关键词中出现旋转小火锅；非官方固定菜单。',4.2,7,14,140,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/stalls/hotpot-restaurant.jpg\"]'),(12,6,'牛肉拉面',1600,'halal,recommended,signature','演示菜品：清真餐厅常见面食，用于联调。',4.5,12,24,240,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/beef-noodle.jpg\"]'),(13,6,'孜然羊肉盖饭',1800,'halal,signature','演示菜品：清真风味菜品，用于联调。',4.5,10,21,210,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/fried-rice.jpg\"]'),(14,7,'酥肉罐罐土豆粉',1600,'signature,noodle','公开点评/地图推荐关键词中出现酥肉罐罐土豆粉；非官方固定菜单。',4.1,6,11,120,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/beef-noodle.jpg\"]'),(15,7,'鸡排意面套餐',1800,'western,recommended','演示菜品：学活风味快餐数据，用于联调。',4.1,6,10,115,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/pasta.jpg\"]'),(16,8,'鱼香肉丝套餐',1500,'daily,recommended','演示菜品：学生餐厅常见套餐，用于联调。',4.2,7,13,130,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/kung-pao-chicken.jpg\"]'),(17,8,'红烧鸡块饭',1600,'daily','演示菜品：学生餐厅常见套餐，用于联调。',4.2,7,13,125,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/kung-pao-chicken.jpg\"]'),(18,9,'麻辣烫自选',2200,'spicy,recommended','演示菜品：风味窗口常见菜品，用于联调。',4.1,9,17,170,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/malatang-counter.jpg\"]'),(19,9,'酸辣粉',1200,'spicy,noodle','演示菜品：风味窗口常见菜品，用于联调。',4.0,6,9,105,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/beef-noodle.jpg\"]'),(20,10,'奥尔良鸡腿盒饭',1800,'combo,recommended','演示菜品：套餐、盒饭、活动供餐数据，用于联调。',4.2,6,12,130,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/kung-pao-chicken.jpg\"]'),(21,11,'东区鸡肉盖饭',1400,'daily,recommended','演示菜品：东校区学生餐厅数据占位，用于联调。',4.1,6,12,120,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/fried-rice.jpg\"]'),(22,11,'东区素菜双拼饭',1100,'daily,vegetarian','演示菜品：东校区学生餐厅数据占位，用于联调。',4.1,5,10,95,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/tomato-egg.jpg\"]'),(23,12,'清真牛肉炒饭',1600,'halal,signature','演示菜品：东区清真窗口数据占位，用于联调。',4.3,7,14,140,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/fried-rice.jpg\"]'),(24,12,'清真鸡肉拌面',1500,'halal,noodle','演示菜品：东区清真窗口数据占位，用于联调。',4.3,6,12,128,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/beef-noodle.jpg\"]'),(25,13,'学苑家常套餐',1300,'daily,recommended','演示菜品：学苑餐厅日常套餐数据，用于联调。',4.2,8,16,160,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/tomato-egg.jpg\"]'),(26,13,'低油低盐套餐',1400,'healthy,daily','演示菜品：结合公开活动中“低油低盐”诉求设置的健康餐数据。',4.2,6,11,120,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/tomato-egg.jpg\"]'),(27,14,'酸辣土豆丝',800,'xueyuan,competition,vegetarian','较可信菜品：学苑餐厅厨师技能比赛报道中出现。',4.3,8,15,150,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/tomato-egg.jpg\"]'),(28,14,'农家小炒肉',1600,'xueyuan,competition,signature','较可信菜品：学苑餐厅厨师技能比赛报道中出现。',4.4,10,18,180,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/kung-pao-chicken.jpg\"]'),(29,14,'干煸豆角',1000,'xueyuan,competition,vegetarian','较可信菜品：学苑餐厅厨师技能比赛报道中出现。',4.3,7,13,140,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/tomato-egg.jpg\"]'),(30,14,'芫爆肉丝',1600,'xueyuan,competition','较可信菜品：学苑餐厅厨师技能比赛报道中出现。',4.2,6,11,130,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/kung-pao-chicken.jpg\"]'),(31,14,'铁钵娃娃菜',1200,'xueyuan,competition,vegetarian','较可信菜品：学苑餐厅厨师技能比赛报道中出现。',4.2,6,10,120,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/tomato-egg.jpg\"]'),(32,14,'排骨焖蛋',1800,'xueyuan,competition,signature','较可信菜品：学苑餐厅厨师技能比赛报道中出现。',4.4,9,16,170,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/kung-pao-chicken.jpg\"]'),(33,15,'学苑清真鸡肉饭',1500,'halal,recommended','演示菜品：学苑清真/面食窗口数据占位，用于联调。',4.2,7,13,130,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/fried-rice.jpg\"]'),(34,15,'牛肉刀削面',1600,'halal,noodle','演示菜品：学苑清真/面食窗口数据占位，用于联调。',4.3,7,14,145,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/beef-noodle.jpg\"]'),(35,16,'留园双拼饭',1800,'recommended,combo','演示菜品：留园餐厅数据占位，用于联调。',4.4,9,18,180,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/fried-rice.jpg\"]'),(36,16,'留园家常小炒',1700,'daily','演示菜品：留园餐厅数据占位，用于联调。',4.3,7,14,150,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/kung-pao-chicken.jpg\"]'),(37,17,'留园素菜套餐',1200,'vegetarian,daily','演示菜品：留园家常套餐窗口数据，用于联调。',4.2,6,12,120,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/tomato-egg.jpg\"]'),(38,18,'益民家常盖饭',1500,'daily,recommended','演示菜品：益民餐厅数据占位，用于联调，后续建议实地核验。',4.1,5,10,100,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/fried-rice.jpg\"]'),(39,18,'益民水饺',1600,'daily','演示菜品：益民餐厅数据占位，用于联调，后续建议实地核验。',4.1,5,9,98,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/dumpling.jpg\"]'),(40,19,'汉堡套餐',1400,'western,recommended','公开点评/地图相关条目中出现汉堡；非官方固定菜单。',4.1,6,12,120,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/hamburger.jpg\"]'),(41,19,'披萨套餐',1800,'western,signature','公开点评/地图相关条目中出现披萨；非官方固定菜单。',4.0,5,9,100,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/pizza.jpg\"]'),(42,19,'意面套餐',1600,'western','演示菜品：西式快餐窗口数据，用于联调。',4.0,5,9,95,'on','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/pasta.jpg\"]');
/*!40000 ALTER TABLE `dish` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `favorite`
--

DROP TABLE IF EXISTS `favorite`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `dish_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_user_dish` (`user_id`,`dish_id`),
  KEY `idx_favorite_user_id` (`user_id`),
  KEY `idx_favorite_dish_id` (`dish_id`),
  CONSTRAINT `fk_favorite_dish` FOREIGN KEY (`dish_id`) REFERENCES `dish` (`id`),
  CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `favorite`
--

LOCK TABLES `favorite` WRITE;
/*!40000 ALTER TABLE `favorite` DISABLE KEYS */;
INSERT INTO `favorite` VALUES (1,1,6,'2026-06-01 20:06:03'),(2,1,11,'2026-06-01 20:06:03'),(3,1,12,'2026-06-01 20:06:03'),(4,1,28,'2026-06-01 20:06:03'),(5,1,35,'2026-06-01 20:06:03'),(6,1,40,'2026-06-01 20:06:03');
/*!40000 ALTER TABLE `favorite` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `review`
--

DROP TABLE IF EXISTS `review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `dish_id` bigint NOT NULL,
  `rating` tinyint NOT NULL,
  `content` text,
  `images` text,
  `is_hidden` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_user_dish` (`user_id`,`dish_id`),
  KEY `idx_review_user_id` (`user_id`),
  KEY `idx_review_dish_id` (`dish_id`),
  KEY `idx_review_dish_visible` (`dish_id`,`is_hidden`),
  KEY `idx_review_created_at` (`created_at`),
  CONSTRAINT `fk_review_dish` FOREIGN KEY (`dish_id`) REFERENCES `dish` (`id`),
  CONSTRAINT `fk_review_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `review`
--

LOCK TABLES `review` WRITE;
/*!40000 ALTER TABLE `review` DISABLE KEYS */;
INSERT INTO `review` VALUES (1,1,6,4,'演示评价：明湖餐厅推荐菜数据，适合作为首页展示。','[]',0,'2026-06-01 20:06:03','2026-06-01 20:06:03'),(2,1,11,4,'演示评价：小火锅适合朋友一起吃。','[\"/images/seed/stalls/hotpot-restaurant.jpg\"]',0,'2026-06-01 20:06:03','2026-06-01 20:06:03'),(3,1,12,5,'演示评价：清真面食窗口数据。','[]',0,'2026-06-01 20:06:03','2026-06-01 20:06:03'),(4,1,28,5,'演示评价：学苑家常菜数据，来源边界已在菜品描述中标注。','[]',0,'2026-06-01 20:06:03','2026-06-01 20:06:03'),(5,1,35,4,'演示评价：留园餐厅菜品测试。','[\"/images/seed/dishes/fried-rice.jpg\"]',0,'2026-06-01 20:06:03','2026-06-01 20:06:03'),(6,1,40,4,'演示评价：西式快餐窗口联调数据。','[]',0,'2026-06-01 20:06:03','2026-06-01 20:06:03');
/*!40000 ALTER TABLE `review` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `stall`
--

DROP TABLE IF EXISTS `stall`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stall` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `canteen_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `location` varchar(255) DEFAULT NULL,
  `description` text,
  `avg_rating` decimal(2,1) NOT NULL DEFAULT '0.0',
  `sort_order` int NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL DEFAULT 'open',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `images` text,
  PRIMARY KEY (`id`),
  KEY `idx_stall_canteen_id` (`canteen_id`),
  KEY `idx_stall_status` (`status`),
  KEY `idx_stall_rating` (`avg_rating`),
  CONSTRAINT `fk_stall_canteen` FOREIGN KEY (`canteen_id`) REFERENCES `canteen` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `stall`
--

LOCK TABLES `stall` WRITE;
/*!40000 ALTER TABLE `stall` DISABLE KEYS */;
INSERT INTO `stall` VALUES (1,1,'明湖一层基本伙食窗口','明湖餐厅一层','基础大伙、米饭套餐、热菜窗口。用于展示日常高频饭菜。',4.2,1,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/canteens/canteen-food-counter.jpg\"]'),(2,1,'明湖风味面食窗口','明湖餐厅','承载风味面食、小吃、砂锅等数据。公开资料曾提到风味面食小吃。',4.3,2,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/malatang-counter.jpg\"]'),(3,1,'明湖小炒套餐窗口','明湖餐厅','承载小炒、套餐类数据。公开资料曾提到小炒、套餐等供餐形态。',4.3,3,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/kung-pao-chicken.jpg\"]'),(4,2,'学生二餐厅','学生活动服务中心一层','官方学生活动服务中心资料确认的一层餐厅；这里作为学活餐厅内部楼层/区域建模。',4.3,1,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/canteens/canteen-food-counter.jpg\"]'),(5,2,'学生三餐厅','学生活动服务中心二层','官方学生活动服务中心资料确认的二层餐厅；这里作为学活餐厅内部楼层/区域建模。',4.3,2,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/canteens/canteen-dining-hall.jpg\"]'),(6,2,'清真餐厅','学生活动服务中心三层','官方学生活动服务中心资料确认的三层清真餐厅。',4.5,3,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/beef-noodle.jpg\"]'),(7,2,'学活风味快餐窗口','学生活动服务中心','用于承载学活区域常见快餐、焗饭、面食等演示数据。',4.2,4,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/pasta.jpg\"]'),(8,3,'学四基本伙食窗口','学生四餐厅','学生四餐厅日常餐食窗口。',4.2,1,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/canteens/canteen-food-counter.jpg\"]'),(9,3,'学四二层风味餐厅','学生四餐厅二层','公开食宿资料中可见学四二层风味餐厅说法；作为学生四餐厅内部楼层建模。',4.1,2,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/malatang-counter.jpg\"]'),(10,3,'学四保障套餐窗口','学生四餐厅','用于承载套餐、盒饭、活动供餐等演示数据。',4.2,3,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/fried-rice.jpg\"]'),(11,4,'东区基本伙食窗口','东校区学生餐厅','服务东校区学生日常就餐。',4.2,1,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/canteens/canteen-food-counter.jpg\"]'),(12,4,'东区清真窗口','东校区学生餐厅','第三方食宿资料中出现东区清真餐厅名称；这里作为东校区学生餐厅内部窗口建模。',4.4,2,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/beef-noodle.jpg\"]'),(13,5,'学苑基本伙食窗口','学苑公寓学生餐厅','靠近学苑公寓，承载日常套餐与家常菜数据。',4.2,1,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/tomato-egg.jpg\"]'),(14,5,'学苑风味家常菜窗口','学苑公寓学生餐厅','学苑餐厅厨师技能比赛报道中出现多道家常菜，可作为较可信的菜品来源。',4.3,2,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/kung-pao-chicken.jpg\"]'),(15,5,'学苑清真/面食窗口','学苑公寓学生餐厅','地图/点评内容中有学苑餐厅与清真、面食相关评价；用于联调。',4.3,3,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/beef-noodle.jpg\"]'),(16,6,'留园打餐窗口','留园餐厅','地图/点评内容称留园为排队打餐式食堂，菜品较丰富。',4.4,1,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/canteens/canteen-food-counter.jpg\"]'),(17,6,'留园家常套餐窗口','留园餐厅','用于承载留园区域家常套餐与双拼饭演示数据。',4.3,2,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/fried-rice.jpg\"]'),(18,7,'益民家常菜窗口','益民餐厅','益民餐厅开发演示窗口，等待后续用实地采集数据替换。',4.2,1,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/dumpling.jpg\"]'),(19,8,'西式快餐窗口','西式快餐厅','地图/点评中可见汉堡、披萨、意面等西式快餐关键词。',4.1,1,'open','2026-06-01 20:06:03','2026-06-01 20:06:03','[\"/images/seed/dishes/hamburger.jpg\"]');
/*!40000 ALTER TABLE `stall` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `email` varchar(100) NOT NULL,
  `password` varchar(255) NOT NULL,
  `nickname` varchar(50) NOT NULL DEFAULT '',
  `avatar` varchar(255) DEFAULT NULL,
  `role` varchar(20) NOT NULL DEFAULT 'user',
  `status` varchar(20) NOT NULL DEFAULT 'active',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `last_login_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  UNIQUE KEY `uk_user_email` (`email`),
  KEY `idx_user_role` (`role`),
  KEY `idx_user_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` (`id`,`username`,`email`,`password`,`nickname`,`avatar`,`role`,`status`,`created_at`,`updated_at`,`last_login_at`) VALUES
(1,'20240001','20240001@bjtu.edu.cn','$2a$10$PKDZfcJPBEtOqeSSsnEgruL9EEGiBZUzfhhv.8T82Y8iRw5Om03Ue','交大学子','/images/seed/dishes/tomato-egg.jpg','user','active','2026-06-01 20:06:03','2026-06-01 20:06:03',NULL),
(2,'admin001','admin001@bjtu.edu.cn','$2a$10$PKDZfcJPBEtOqeSSsnEgruL9EEGiBZUzfhhv.8T82Y8iRw5Om03Ue','管理员','/images/seed/canteens/canteen-food-counter.jpg','admin','active','2026-06-01 20:06:03','2026-06-01 20:06:03',NULL);
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `email_verification_code`
--

DROP TABLE IF EXISTS `email_verification_code`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_verification_code` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(100) NOT NULL,
  `code_hash` varchar(255) NOT NULL,
  `purpose` varchar(30) NOT NULL DEFAULT 'login',
  `expires_at` datetime NOT NULL,
  `used_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_email_code_email` (`email`),
  KEY `idx_email_code_expires_at` (`expires_at`),
  KEY `idx_email_code_purpose` (`purpose`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `item_list`
--

DROP TABLE IF EXISTS `item_list`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `item_list` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `description` text DEFAULT NULL,
  `share_token` varchar(64) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_list_share_token` (`share_token`),
  KEY `idx_item_list_user_id` (`user_id`),
  CONSTRAINT `fk_item_list_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `list_item`
--

DROP TABLE IF EXISTS `list_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `list_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `list_id` bigint NOT NULL,
  `dish_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_list_item_list_dish` (`list_id`,`dish_id`),
  KEY `idx_list_item_list_id` (`list_id`),
  KEY `idx_list_item_dish_id` (`dish_id`),
  CONSTRAINT `fk_list_item_list` FOREIGN KEY (`list_id`) REFERENCES `item_list` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_list_item_dish` FOREIGN KEY (`dish_id`) REFERENCES `dish` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'bjtu_food'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-04  0:29:52
