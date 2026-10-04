package com.bjtufood.auth.config;

import com.bjtufood.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security 安全配置
 * <p>
 * 配置要点：
 * 1. 关闭 CSRF（接口由 JWT 保护，无需 CSRF Token）
 * 2. 无状态会话（不创建 Session，每次请求通过 JWT 验证）
 * 3. 注册 JwtAuthFilter（在 UsernamePasswordAuthenticationFilter 之前执行）
 * 4. 配置公开接口白名单（无需登录即可访问）
 * 5. 启用 @PreAuthorize 注解（方法级别权限控制）
 * <p>
 * 公开接口白名单（无需登录）：
 * - POST /auth/wechat-login（微信静默登录）、POST /auth/email-code（发验证码）、POST /auth/verify-email（邮箱认证）
 * - 管理端 /admin/** 不走白名单：由 AdminTokenFilter 校验请求头 X-Admin-Token（环境变量 ADMIN_TOKEN，方案 C 已作废）
 * - GET /banners（首页顶部轮播图，；/** 无写接口）
 * - GET /dishes、GET /dishes/{id}、GET /dishes/views、GET /dishes/{id}/attributes、GET /dishes/for-you、GET /dishes/{id}/reviews（菜品只读浏览）；POST /dishes/{id}/correction（菜品信息纠错提交，IP 限频兜底在 Controller 层）
 * - 注：GET /canteens 白名单已于删除（食堂字典端点随食堂 / 价格筛选全量下线整体下线）
 * - Swagger UI (SpringDoc) 相关路径
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // 启用 @PreAuthorize 注解
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    private final AdminTokenFilter adminTokenFilter;

    /**
     * Spring MVC 映射内省器（Spring Boot 3 自动配置）。
     * <p>
     * 供 {@link MvcRequestMatcher} 使用——Spring Security 6 起，Spring MVC 应用<b>推荐</b>用
     * {@code MvcRequestMatcher} 而非 {@code AntPathRequestMatcher} 声明白名单：
     * 前者按 Spring MVC 的 {@code HandlerMapping} 语义匹配（区分尾斜杠、method 大小写），
     * 与 Controller 上真实注册的映射口径一致，不会出现「白名单写错却静默不生效」。
     */
    private final HandlerMappingIntrospector introspector;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 任意方法放行的公开接口（鉴权/文档类，无敏感写操作）。
     * <p>
     * <b>路径口径</b>：以下为<b>不含 context-path</b> 的应用内路径。
     * {@link MvcRequestMatcher} 匹配的是 {@code HandlerMapping} 解析出的「应用内路径」，
     * 即已剥离 {@code server.servlet.context-path}——因此
     * {@code server.servlet.context-path} 由 {@code /api} 改为 {@code /api/v1} 时，
     * <b>本表无需任何改动</b>。
     * <p>
     * 历史说明：本表此前对每条路径<b>同时</b>列出了「无前缀」与「{@code /api} 前缀」两份，
     * 理由是担心 matcher 是否含 context-path。实测二者必中其一——无前缀那份才是真正生效的，
     * {@code /api} 那份是永不匹配的死条目。若保留该冗余，context-path 升版后会膨胀为三份。
     * 现已收敛为单一真源。
     */
    private static final String[] PUBLIC_ANY_METHOD = {
            // 认证类公开接口（微信静默登录、学号邮箱认证、验证码）
            "/auth/wechat-login",
            "/auth/email-code",
            "/auth/verify-email",
            // 反馈提交（PUB：产品决策「反馈不登录也能用」）
            "/feedback",
            // 举报原因字典（PUB：举报免认证，端上举报弹层实时拉取）
            // P2 迁址：原 /feedback/report-reasons（字典挂在「反馈提交」写入口下语义错位）
            "/report-reasons",
            // 评价举报提交（PUB：举报免认证，游客可提交；RESTful 子资源）
            "/reviews/*/report",
            // 菜品信息纠错提交（PUB：匿名允许，对齐 feedback 提交口径；IP 限频在 Controller 层）
            "/dishes/*/correction",
            // SpringDoc Swagger UI 文档
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/webjars/**",
            // Actuator 运维端点：仅放行探活与信息，
            // 使云托管/负载均衡的存活探针无需携带任何凭据即可调用。
            // 暴露面由 application-prod.yml 的 management.endpoints.web.exposure 收敛到
            // health/info —— env/beans/heapdump 等高危端点即便被请求也匹配不到本白名单。
            // 注：Spring Security 6 默认会保护 /actuator/**，故必须显式放行。
            "/actuator/health/**",
            "/actuator/info"
    };

    /**
     * 仅 GET 放行的公开浏览接口（覆盖全部 dish / banner 只读路径，
     * 使用 method-scoped 匹配，避免误放行 POST 等写操作）。
     * <p>
     * 说明：学生端菜品写接口已于全部下线，菜品仅由管理员经 /admin/dishes 录入；
     * 本条仅约束 GET 只读浏览（浏览量计数为 GET /dishes/{id} 的响应副作用，无独立上报端点）。
     * /stalls/** 白名单已于CT-05 删除：无公开 StallController 端点（幽灵路由）。
     * /canteens/** 白名单已于删除：食堂字典端点（原 GET /canteens）随食堂 / 价格筛选
     * 全量下线整体删除（K4），公开侧不再有食堂字典接口。
     * 评价只读路径已 RESTful 化为 /dishes/{id}/reviews（由 /dishes/** 覆盖，拍板）；
     * 原 GET /reviews 白名单条目随该路径删除一并移除；GET /my/reviews 需登录，不在白名单内。
     */
    private static final String[] PUBLIC_GET_PREFIXES = {
            "/dishes/**",
            "/banners/**",
            "/images/**",
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. 关闭 CSRF（JWT 无需 CSRF 保护）
                .csrf(csrf -> csrf.disable())

                // 2. 无状态会话
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 3. 请求权限配置
                .authorizeHttpRequests(auth -> auth
                        // 任意方法放行的公开接口（鉴权/文档）
                        .requestMatchers(mvcMatchers(PUBLIC_ANY_METHOD)).permitAll()
                        // 仅 GET 放行的公开浏览接口（游客免登录浏览全部公开内容）
                        .requestMatchers(mvcMatchers(HttpMethod.GET, PUBLIC_GET_PREFIXES)).permitAll()
                        // 管理端接口：由 AdminTokenFilter 用环境变量口令 ADMIN_TOKEN 校验（后台无登录体系），
                        // 此处放行交由过滤器把关（未配置口令时过滤器 fail-closed 拒绝）
                        .requestMatchers(mvcMatchers("/admin/**")).permitAll()
                        // 管理端图片上传已归入 /admin/**（POST /admin/upload），随上行放行并由过滤器口令把关；
                        // 学生端上传 /upload/cloud-image 走 JWT。
                        // 其他接口需要登录
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, Result.unauthorized("请先登录或重新登录")))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN, Result.forbidden("无权限访问该接口")))
                )

                // 4. 注册管理端口令过滤器（先注册即先执行：/admin 走口令，不再走 JWT 角色）
                .addFilterBefore(adminTokenFilter, UsernamePasswordAuthenticationFilter.class)

                // 5. 注册 JWT 过滤器（在 UsernamePasswordAuthenticationFilter 之前）
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 构造 {@link MvcRequestMatcher} 数组（任意 HTTP 方法）。
     * <p>
     * 路径为「应用内路径」（不含 context-path），见 {@link #PUBLIC_ANY_METHOD} 的口径说明。
     *
     * @param patterns 应用内路径模式
     * @return 可直接传给 {@code requestMatchers(...)} 的匹配器数组
     */
    private RequestMatcher[] mvcMatchers(String... patterns) {
        return mvcMatchers(null, patterns);
    }

    /**
     * 构造 {@link MvcRequestMatcher} 数组（限定 HTTP 方法；{@code method} 为 {@code null} 表示不限）。
     *
     * @param method   限定的 HTTP 方法，{@code null} 表示任意方法
     * @param patterns 应用内路径模式
     * @return 匹配器数组
     */
    private RequestMatcher[] mvcMatchers(HttpMethod method, String... patterns) {
        MvcRequestMatcher[] matchers = new MvcRequestMatcher[patterns.length];
        for (int i = 0; i < patterns.length; i++) {
            MvcRequestMatcher matcher = new MvcRequestMatcher(introspector, patterns[i]);
            if (method != null) {
                matcher.setMethod(method);
            }
            matchers[i] = matcher;
        }
        return matchers;
    }

    /**
     * 密码加密器
     * <p>
     * 使用 BCrypt 算法加密密码。
     * BCrypt 每次加密结果不同（内置 salt），安全性高。
     * <p>
     * 保留说明（BE「删除 DataInitializer」联动评估结论）：
     * 管理端已无登录/密码体系（/admin/** 走 {@code AdminTokenFilter} 的 X-Admin-Token 口令），
     * 原 DataInitializer 写入的 admin/admin123 账号已随该类一并删除；
     * 但本 Bean <b>不能摘除</b>——邮箱验证码仍以 BCrypt 存/验哈希：
     * {@code EmailCodeServiceImpl#passwordEncoder.encode(验证码)} 与
     * {@code AuthServiceImpl#passwordEncoder.matches(输入码, code_hash)} 依赖它。
     * 摘除将直接导致启动期依赖注入失败。
     *
     * @return PasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeJson(HttpServletResponse response, int httpStatus, Result<?> result) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(result));
    }
}
