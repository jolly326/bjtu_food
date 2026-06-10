package com.bjtufood.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j API documentation configuration.
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录后填写 JWT Token。Knife4j 中通常只需要粘贴 token 本身，不需要手动加 Bearer 前缀。")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .info(new Info()
                        .title("校园食堂信息系统 API 文档")
                        .version("1.0.0")
                        .description("""
                                校园食堂菜品展示与互动后端接口。

                                ## Knife4j 测试步骤
                                1. 调用 POST /auth/email-code 获取邮箱验证码，验证码发送至校园邮箱。
                                2. 调用 POST /auth/register 注册新用户，或使用预置账号 20240001 / 123456 登录。
                                3. 密码登录：POST /auth/login 传 `{ "account": "20240001", "password": "123456" }`。
                                4. 验证码登录：先获取邮箱验证码，再传 `{ "email": "...@bjtu.edu.cn", "code": "123456" }`。
                                5. 复制登录响应 data.token。
                                6. 点击 Knife4j 页面右上角 Authorize，填入 token。
                                7. 再测试收藏、评价、清单、个人资料等需要登录的接口。

                                ## 统一响应格式
                                所有接口返回 `{ "code": 200, "message": "操作成功", "data": ... }`。

                                ## 图片规则
                                数据库存储 `/images/...` 相对路径，后端根据 `app.public-base-url` 拼接为完整 URL。
                                """)
                        .contact(new Contact()
                                .name("BJTU Food Team")
                                .email("bjtu-food@example.com")));
    }
}
