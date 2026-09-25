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
 * <p>
 * 两套鉴权方案（与实现严格一致）：
 * <ul>
 *   <li>{@code bearerAuth}：学生端 JWT（{@code Authorization: Bearer <token>}）——默认 scheme；</li>
 *   <li>{@code adminToken}：管理端口令（请求头 {@code X-Admin-Token}）——仅 {@code /admin/**} 使用。</li>
 * </ul>
 */
@Configuration
public class SwaggerConfig {

    /** 学生端 JWT 方案名 */
    public static final String BEARER_SCHEME = "bearerAuth";

    /** 管理端口令方案名 */
    public static final String ADMIN_TOKEN_SCHEME = "adminToken";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("学生端 JWT。Authorize 中粘贴 wechat-login 返回的 data.token（无需手写 Bearer 前缀）。"))
                        .addSecuritySchemes(ADMIN_TOKEN_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Admin-Token")
                                .description("管理端口令。值 = 服务端环境变量 ADMIN_TOKEN（未配置时管理端接口 fail-closed 403）。")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .info(new Info()
                        .title("校园食堂信息系统 API 文档")
                        .version("1.0.0")
                        .description("""
                                校园食堂菜品展示与互动后端接口。

                                ## Swagger UI 测试步骤（微信登录体系，spec §5.y）
                                1. 小程序端微信静默登录：POST /auth/wechat-login 传 `{ "code": "wx.login 临时凭证" }`，自动建号返回游客态。
                                2. 学号邮箱认证：先 POST /auth/email-code 传 `{ "username": "20240001" }` 获取验证码（发至校园邮箱，
                                   邮箱由学号推导），再 POST /auth/verify-email 传 `{ "code": "123456" }` 完成认证
                                   （写入 bind_email，解锁 UGC 写操作）。
                                3. 管理端接口（/admin/**）：无登录体系，Authorize 选 `adminToken` 填入环境变量 ADMIN_TOKEN（未配置时 fail-closed 403）。
                                4. 小程序用户态接口：Authorize 选 `bearerAuth`，填入 wechat-login 返回的 data.token。
                                5. 未完成学号邮箱认证访问写接口返回 code=4031「请先完成学号邮箱认证」。
                                （学生端菜品写接口已下线，菜品由管理员经 /admin/dishes 录入。）

                                ## 统一响应格式
                                所有接口返回 `{ "code": 200, "message": "操作成功", "data": ... }`。
                                **业务码为唯一判据**（端上只读 body.code）：除 403 类授权错误与参数校验 / 过滤器层外，
                                业务码统一挂在 HTTP 200 响应上；完整码表见 docs/project_spec.md。

                                ## 图片规则
                                上传：小程序 UGC 配图走 POST /upload/cloud-image（云存储 fileID → 安检 → COS）；
                                管理端菜品图走 POST /admin/upload/image（multipart，X-Admin-Token）。
                                数据库可存 `/images/...` 相对路径，后端按 `app.public-base-url` 拼接为完整 URL。
                                """)
                        .contact(new Contact()
                                .name("BJTU Food Team")
                                .email("bjtu-food@example.com")));
    }
}
