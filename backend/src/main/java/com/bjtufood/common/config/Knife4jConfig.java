package com.bjtufood.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j API 文档配置
 * <p>
 * 启动后访问：http://localhost:8080/api/doc.html
 * 提供所有接口的在线文档与调试功能
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("校园食堂信息系统 API 文档")
                        .version("1.0.0")
                        .description("""
                                校园食堂菜品展示与互动小程序后端接口。
                                
                                ## 模块说明
                                - auth：登录注册、用户管理
                                - canteen：食堂/档口查询与管理
                                - dish：菜品展示、搜索、详情
                                - review：评价提交、审核
                                - favorite：收藏/取消收藏
                                - list：美食清单创建与分享
                                - upload：图片上传
                                
                                ## 认证方式
                                需登录接口在 Header 中携带：Authorization: Bearer <token>
                                """)
                        .contact(new Contact()
                                .name("BJTU Food Team")
                                .email("bjtu-food@example.com")));
    }
}
