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
 * SpringDoc OpenAPI (Swagger UI) documentation configuration.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录后填写 JWT Token。Swagger UI 中通常只需要粘贴 token 本身，不需要手动加 Bearer 前缀。")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .info(new Info()
                        .title("校园食堂信息系统 API 文档")
                        .version("1.0.0")
                        .description("""
                                校园食堂菜品展示与互动后端接口。

                                ## Swagger UI 测试步骤（微信登录体系，spec §5.y）
                                1. 小程序端微信静默登录：POST /auth/wechat-login 传 `{ "code": "wx.login 临时凭证" }`，自动建号返回游客态。
                                2. 学号邮箱认证：先 POST /auth/email-code 传 `{ "username": "20240001", "purpose": "verify" }` 获取验证码（发至校园邮箱），再 POST /auth/verify-email 传 `{ "code": "123456" }` 完成认证（verified=true，解锁 UGC 写操作）。
                                3. 管理端接口（/admin/**）无登录体系：请求头 `X-Admin-Token` 携带环境变量 ADMIN_TOKEN（未配置时 fail-closed 403）。
                                4. 小程序用户态接口：点击右上角 Authorize，填入 wechat-login 返回的 data.token。
                                5. 再测试评价、点赞等需要登录的接口。未认证用户访问写接口返回 code=4031「请先完成学号邮箱认证」。
                                （学生端菜品写接口已于 2026-09-13 全部下线，菜品由管理员经 /admin/dishes 录入。）

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
