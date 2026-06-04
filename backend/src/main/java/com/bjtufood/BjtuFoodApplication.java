package com.bjtufood;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 校园食堂信息系统 - 后端启动类
 * <p>
 * 技术栈：Spring Boot 3.2 + Java 21 + MyBatis-Plus + MySQL + JWT + Knife4j
 * 模块说明：
 * - common：公共模块（配置、异常、工具类）
 * - auth：认证模块（登录注册、JWT校验、用户管理）
 * - canteen：食堂档口模块（食堂/档口 CRUD）
 * - dish：菜品模块（菜品展示、搜索、管理、统计）
 * - review：评价模块（评价提交、审核）
 * - favorite：收藏模块（收藏/取消收藏）
 * - list：清单模块（创建清单、分享）
 * - upload：文件上传模块（图片上传）
 */
@MapperScan("com.bjtufood.**.mapper")
@SpringBootApplication
@EnableScheduling
public class BjtuFoodApplication {

    public static void main(String[] args) {
        SpringApplication.run(BjtuFoodApplication.class, args);
    }
}
