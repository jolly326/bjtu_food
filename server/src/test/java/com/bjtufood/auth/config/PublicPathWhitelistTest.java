package com.bjtufood.auth.config;

import com.bjtufood.auth.support.JwtUtil;
import com.bjtufood.common.config.CorsProperties;
import com.bjtufood.common.exception.GlobalExceptionHandler;
import com.bjtufood.dish.controller.DishController;
import com.bjtufood.dish.service.DishService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公开浏览路径白名单的「context-path 相关性」实证测试。
 * <p>
 * <b>存在理由</b>：{@code SecurityConfig} 用 {@link org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher}
 * 声明白名单（见 SecurityConfig 第 55-78 行注释）。该 matcher 匹配的是「应用内路径」还是
 * 「完整 requestURI」，直接决定白名单是否生效——而 {@code SmokeApiTest}
 * （{@code @WebMvcTest} + MockMvc）的 {@code contextPath} 默认为<b>空</b>，
 * 在这种约定下两种口径<b>恰好重合</b>，因此<b>测不出</b>线上带
 * {@code server.servlet.context-path=/api/v1} 时的真实行为。
 * <p>
 * 这与 {@link AdminTokenFilterTest} 记载的盲区同源（该测试注释明确指出
 * 「MockMvc 默认 contextPath 为空，无论前缀怎么变都命中」）。
 * 生产事故表现：小程序 {@code GET /api/v1/dishes} 返回
 * 401「请先登录或重新登录」——即请求落到了 {@code anyRequest().authenticated()}，
 * 说明 {@code /dishes/**} 这条 permitAll 白名单<b>未命中</b>。
 * <p>
 * 本测试通过显式设置 {@code contextPath="/api/v1"} 复现线上形态，
 * 把「白名单必须对 context-path 无关」这一前提锁死为可执行断言。
 */
@WebMvcTest
@ContextConfiguration(classes = PublicPathWhitelistTest.SliceContext.class)
@Import({
        DishController.class,
        GlobalExceptionHandler.class,
        JwtProperties.class,
        AdminProperties.class,
        CorsProperties.class,
        SecurityConfig.class,
        JwtAuthFilter.class,
        AdminTokenFilter.class,
        JwtUtil.class,
        TokenBlacklist.class
})
@TestPropertySource(properties = {
        "admin.token=" + PublicPathWhitelistTest.TEST_ADMIN_TOKEN,
        "jwt.secret=PublicPathWhitelistOnlySecretKey_0123456789ABCDEF",
        "jwt.expiration=3600000"
})
class PublicPathWhitelistTest {

    /** 管理端口令（仅测试值，经 @TestPropertySource 注入 admin.token） */
    static final String TEST_ADMIN_TOKEN = "whitelist-test-admin-token";

    /** 线上真实的 context-path（与 application.yml 的 server.servlet.context-path 一致） */
    private static final String CONTEXT_PATH = "/api/v1";

    /** 切片上下文：空配置，仅用于取代主类配置（屏蔽 @MapperScan 与全量组件扫描，零数据库依赖） */
    @Configuration
    static class SliceContext {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DishService dishService;

    /** DishController 构造参数 1：IP 限频（打桩，避免切片依赖真实限频器） */
    @MockBean
    private com.bjtufood.common.ratelimit.IpRateLimiter ipRateLimiter;

    /**
     * 核心用例：游客（无 Authorization 头）访问 {@code GET /dishes} 必须放行。
     * <p>
     * 断言「不是 401」而非「是 200」——目的是<b>精确区分</b>两类失败：
     * <ul>
     *   <li>401 ⇒ 白名单未命中，请求被 {@code authenticationEntryPoint} 拒绝（本次生产事故）</li>
     *   <li>非 401（200/400/500 等）⇒ 白名单已命中，请求进入了 Controller/业务层</li>
     * </ul>
     * 这样即便业务层因 Mock 未打桩而抛错，用例仍能准确反映「安全层是否放行」，
     * 不会因业务细节变化而误报。
     */
    @Test
    @DisplayName("游客 GET /dishes 在 context-path=/api/v1 下必须命中白名单（非 401）")
    void guestGetDishesIsPublicUnderRealContextPath() throws Exception {
        int status = mockMvc.perform(get(CONTEXT_PATH + "/dishes")
                        .contextPath(CONTEXT_PATH)
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(status)
                .as("游客浏览菜品是产品底线（docs/client/README.md「游客可浏览一切」），"
                        + "GET %s/dishes 不得返回 401。返回 401 说明 SecurityConfig 中 "
                        + "/dishes/** 白名单在带 context-path 的线上环境下未命中。", CONTEXT_PATH)
                .isNotEqualTo(401);
    }

    /**
     * 对照用例：无 context-path 时同样必须放行。
     * <p>
     * 与上一用例配对，用于<b>定位失效形态</b>：
     * <ul>
     *   <li>本用例过 + 上一用例挂 ⇒ matcher 只认「无前缀路径」，
     *       线上带前缀即失效（需改为同时声明带前缀的条目，或改用 AntPathRequestMatcher）</li>
     *   <li>本用例挂 ⇒ 问题不在 context-path，而在白名单条目本身写错</li>
     * </ul>
     */
    @Test
    @DisplayName("对照：无 context-path 时 GET /dishes 亦必须放行（用于区分失效形态）")
    void guestGetDishesIsPublicWithoutContextPath() throws Exception {
        int status = mockMvc.perform(get("/dishes")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(status)
                .as("裸路径 /dishes 是 matcher 的基准口径，必须命中白名单")
                .isNotEqualTo(401);
    }

    /**
     * 自校验用例：证明 {@code contextPath(...)} 确实生效，上面的用例不是「假绿」。
     * <p>
     * <b>为什么必须要有这条</b>：MockMvc 的 {@code contextPath} 若被忽略，
     * 带前缀的请求会被当作裸路径处理，于是「白名单命中」这一结论<b>无从证伪</b>。
     * 本用例断言「请求 URI 的前缀被 Spring 正确登记为 contextPath」——
     * 若该断言失败，说明本测试类无法复现线上形态，前述结论一并作废。
     */
    @Test
    @DisplayName("自校验：contextPath(/api/v1) 必须真实生效，否则上述结论不成立")
    void contextPathIsActuallyApplied() throws Exception {
        org.springframework.mock.web.MockHttpServletRequest req =
                mockMvc.perform(get(CONTEXT_PATH + "/dishes")
                                .contextPath(CONTEXT_PATH)
                                .param("page", "1")
                                .param("pageSize", "10"))
                        .andReturn().getRequest();

        org.assertj.core.api.Assertions.assertThat(req.getContextPath())
                .as("contextPath 必须被登记为 %s；若为空则本测试类退化为裸路径，无法复现线上形态",
                        CONTEXT_PATH)
                .isEqualTo(CONTEXT_PATH);

        org.assertj.core.api.Assertions.assertThat(req.getRequestURI())
                .as("原始 requestURI 必须仍带前缀（模拟网关转发来的真实形态）")
                .isEqualTo(CONTEXT_PATH + "/dishes");
    }

    /**
     * <b>事故复现用例</b>：当 {@code context-path} 未生效（应用跑在根上下文 {@code /}）时，
     * 小程序实际发出的 {@code GET /api/v1/dishes} 会退化为 401 —— 这正是 2026-10-02
     * 线上事故的<b>确切机制</b>。
     * <p>
     * 根因链：{@code application.yml} 被移出 git 跟踪 ⇒ 云构建（从 GitHub 克隆）拿不到该文件
     * ⇒ jar 不含任何配置 ⇒ {@code server.servlet.context-path} 未设置
     * ⇒ Tomcat 跑在 {@code /}，启动日志中表现为 {@code o.a.c.c.C.[Tomcat].[localhost].[/]}
     * ⇒ 请求的 {@code /api/v1} 前缀<b>不被剥离</b>，应用内路径成了 {@code /api/v1/dishes}
     * ⇒ 命中不到 {@code /dishes/**} 白名单 ⇒ 落入 {@code anyRequest().authenticated()}。
     * <p>
     * 本用例的作用是<b>把事故形态钉成可执行断言</b>，使「context-path 丢失」不再是
     * 只在部署后才暴露的幽灵故障：它与
     * {@link #guestGetDishesIsPublicUnderRealContextPath()} 构成一对互斥断言——
     * 前者证明「带前缀 + context-path 生效 ⇒ 放行」，本例证明「context-path 丢失 ⇒ 必现该 401」，
     * 两者共同锁死「context-path 必须生效」这一隐含前提。
     */
    @Test
    @DisplayName("事故复现：context-path 丢失（根上下文）时 GET /api/v1/dishes 必现 401")
    void rootContextWithApiPrefixReproducesProduction401() throws Exception {
        mockMvc.perform(get(CONTEXT_PATH + "/dishes")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("请先登录或重新登录"));
    }

    /**
     * <b>事故复现用例</b>：同理，静默重登端点在根上下文下也会 401
     * ⇒ {@code http.ts} 的 {@code trySilentRelogin()} 永远拿不到 token
     * ⇒ 端上陷入「401 → 重登 → 再 401」死循环，用户表现为「一直提示请先登录」。
     * <p>
     * 这解释了为何线上症状是「刷新多少次都登不进去」而非偶发：只要 context-path 丢失，
     * 整条登录链路与全部公开浏览接口会<b>同时</b>失效。
     */
    @Test
    @DisplayName("事故复现：根上下文下 POST /auth/wechat-login 也 401（静默重登死锁）")
    void rootContextBreaksSilentRelogin() throws Exception {
        mockMvc.perform(post(CONTEXT_PATH + "/auth/wechat-login")
                        .contentType("application/json")
                        .content("{\"code\":\"dummy\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    /**
     * 受保护路径必须仍被拦截（阳性对照，防「测试因全体放行而假绿」）。
     * <p>
     * {@code /my/reviews} 需登录且不在任何白名单内。若本用例也返回非 401，
     * 说明 SecurityConfig 未生效，则前两个用例的「非 401」结论不可信。
     */
    @Test
    @DisplayName("阳性对照：受保护的 GET /my/reviews 必须返回 401（证明 SecurityConfig 生效）")
    void protectedPathStillRequiresAuth() throws Exception {
        mockMvc.perform(get(CONTEXT_PATH + "/my/reviews")
                        .contextPath(CONTEXT_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("请先登录或重新登录"));
    }

    /**
     * 登录接口本身必须可匿名访问——否则 401 后的静默重登无从谈起（死锁）。
     * <p>
     * 对应客户端 {@code http.ts} 的 {@code trySilentRelogin()}：拿到 401 后
     * 调 {@code POST /auth/wechat-login}。若该端点被 Security 拦截，重登必定失败，
     * 端上最终只会抛出「请先登录或重新登录」这一表象。
     */
    @Test
    @DisplayName("静默重登端点 POST /auth/wechat-login 必须匿名可达（不得 401）")
    void wechatLoginIsPublicUnderRealContextPath() throws Exception {
        int status = mockMvc.perform(post(CONTEXT_PATH + "/auth/wechat-login")
                        .contextPath(CONTEXT_PATH)
                        .contentType("application/json")
                        .content("{\"code\":\"dummy\"}"))
                .andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(status)
                .as("POST %s/auth/wechat-login 必须在 PUBLIC_ANY_METHOD 白名单内且匿名可达；"
                        + "返回 401 会使端上静默重登死锁，用户无论如何刷新都无法登录。", CONTEXT_PATH)
                .isNotEqualTo(401);
    }
}
