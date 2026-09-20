package com.bjtufood;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.config.AdminTokenFilter;
import com.bjtufood.auth.config.JwtAuthFilter;
import com.bjtufood.auth.config.SecurityConfig;
import com.bjtufood.auth.config.TokenBlacklist;
import com.bjtufood.auth.controller.AuthController;
import com.bjtufood.auth.controller.FeedbackController;
import com.bjtufood.auth.dto.LoginResp;
import com.bjtufood.auth.dto.UserInfoVO;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.auth.service.AuthService;
import com.bjtufood.common.aspect.RequireVerifiedAspect;
import com.bjtufood.common.config.IpRateLimiter;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.exception.GlobalExceptionHandler;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JwtUtil;
import com.bjtufood.common.utils.SensitiveFilter;
import com.bjtufood.content.security.ContentSecurityService;
import com.bjtufood.dish.controller.DishController;
import com.bjtufood.dish.dto.DishVO;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.feedback.controller.admin.FeedbackAdminController;
import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.feedback.mapper.FeedbackMapper;
import com.bjtufood.feedback.service.impl.FeedbackServiceImpl;
import com.bjtufood.notify.service.NotificationService;
import com.bjtufood.review.controller.ReviewController;
import com.bjtufood.review.controller.admin.ReviewAdminController;
import com.bjtufood.review.service.ReviewService;
import com.bjtufood.upload.controller.UploadController;
import com.bjtufood.upload.service.UploadService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 六链路接口层冒烟测试（MockMvc 切片 + @MockBean 打桩 Service/Mapper，<b>不连数据库、不连外网</b>）。
 * <p>
 * 目的：为接口层留下最小必要的契约兜底，覆盖六条关键链路的「HTTP 状态码 + 统一响应 code」契约：
 * <ol>
 *   <li>登录：{@code POST /auth/wechat-login}（200/code=200 + token/userInfo，缺 code → 400）；</li>
 *   <li>菜品详情：{@code GET /dishes/{id}}（200 + 关键字段；不存在 → body code=400 口径）；</li>
 *   <li>评价：{@code POST /dishes/{id}/reviews}（匿名 401 / 已登录未认证 4031 / 已认证 200）、
 *       {@code PUT /reviews/{id}}（重新评价，4031 分流）与路径防回归（旧 {@code /reviews} 不再注册）；</li>
 *   <li>反馈：{@code POST /feedback}（sub 严格模式 400、类型白名单 400、suggestion 正常落库 200）；</li>
 *   <li>上传：{@code POST /upload/image}（无/错 X-Admin-Token → 403，正确口令 200）；</li>
 *   <li>管理端：{@code GET /admin/feedbacks}（无口令 403，带口令 200 + 分页契约）；</li>
 *   <li>防回归：{@code GET /admin/categories}（品类整链退役，带正确口令亦无处理器）、
 *       {@code PUT /admin/reviews/{id}/sec-state}（sec_state 全链退役，映射表中不得再注册该端点，
 *       保留的 {@code /admin/reviews/{id}/hide} 仍在册作阳性对照）；</li>
 *   <li>机检口径：反馈机检 risky → 400「内容包含违规信息，请修改后重试」且不落库
 *       （2026-09-15 取消人工复核后，pass/review 一律放行）。</li>
 * </ol>
 * 实现要点：
 * <ul>
 *   <li>主类 {@code BjtuFoodApplication} 上的 {@code @MapperScan} 会在切片内注册 MapperFactoryBean
 *       （需要 SqlSessionFactory → 需连库），故本测试用内嵌空配置 {@link SliceContext} 取代主配置，
 *       并<b>显式 @Import</b> 被测 Bean（控制器 / 统一异常处理 / 真实安全链路），使切片零数据库依赖；
 *       同理不启用组件扫描，避免无关 Bean 拉起数据源相关配置。</li>
 *   <li>鉴权走<b>真实</b> {@link SecurityConfig} + {@link JwtAuthFilter} + {@link AdminTokenFilter}；
 *       token 由真实 {@link JwtUtil} 以测试密钥签发，故 401/403/4031 均为真实分流结果；</li>
 *   <li>{@code 4031}（未完成学号邮箱认证）由真实 {@link RequireVerifiedAspect} 触发，
 *       user.verified 经 {@link UserMapper} 打桩注入，不查库；</li>
 *   <li>反馈入参校验（type 白名单 / sub 严格模式）在 Service 层，故导入真实 {@link FeedbackServiceImpl}，
 *       仅打桩其依赖的 Mapper / 工具类；</li>
 *   <li>所有桩数据在各用例内建立，避免 Mockito 严格模式判定为多余桩。</li>
 * </ul>
 */
@WebMvcTest
@ContextConfiguration(classes = SmokeApiTest.SliceContext.class)
@Import({
        // 被测控制器（显式引入，不依赖组件扫描）
        AuthController.class,
        DishController.class,
        ReviewController.class,
        FeedbackController.class,
        UploadController.class,
        FeedbackAdminController.class,
        // 后台评价管理（用于 sec-state 端点退役的映射表回归断言）
        ReviewAdminController.class,
        // 统一异常处理（HTTP 状态码 + body.code 口径的唯一真源）
        GlobalExceptionHandler.class,
        // 真实安全链路
        SecurityConfig.class,
        JwtAuthFilter.class,
        AdminTokenFilter.class,
        JwtUtil.class,
        TokenBlacklist.class,
        // @RequireVerified → 4031 未认证分流
        RequireVerifiedAspect.class,
        // 反馈入参校验（type 白名单 / sub 严格模式）的真实实现
        FeedbackServiceImpl.class,
        // 切片内显式开启 AOP，保证上述切面在 MockMvc 下生效
        SmokeApiTest.AopTestConfig.class
})
@TestPropertySource(properties = {
        "admin.token=" + SmokeApiTest.TEST_ADMIN_TOKEN,
        // 测试专用强密钥（>=32 字节且非仓库默认值，规避 JwtUtil 启动期 fail-fast）
        "jwt.secret=SmokeApiTestOnlySecretKey_0123456789ABCDEF",
        "jwt.expiration=3600000"
})
class SmokeApiTest {

    /** 管理端口令（仅测试值，经 @TestPropertySource 注入 admin.token） */
    static final String TEST_ADMIN_TOKEN = "smoke-test-admin-token";
    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
    /** AdminTokenFilter 口令无效时的对外文案（断言 403 来源为该过滤器，而非安全链路的权限拒绝） */
    private static final String ADMIN_TOKEN_INVALID_MESSAGE = "管理端口令无效";
    /** 测试登录用户 ID（与签发的 JWT 一致） */
    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    /** 切片内真实注册的处理器映射表（用于「端点是否在册」的防回归断言，不发起请求） */
    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @MockBean
    private AuthService authService;
    @MockBean
    private DishService dishService;
    @MockBean
    private ReviewService reviewService;
    @MockBean
    private UploadService uploadService;
    @MockBean
    private IpRateLimiter ipRateLimiter;
    /** @RequireVerified 切面按 user.verified 实时判定，打桩避免查库 */
    @MockBean
    private UserMapper userMapper;
    @MockBean
    private FeedbackMapper feedbackMapper;
    @MockBean
    private DishMapper dishMapper;
    @MockBean
    private SensitiveFilter sensitiveFilter;
    @MockBean
    private NotificationService notificationService;
    @MockBean
    private ContentSecurityService contentSecurityService;
    @MockBean
    private ImageUrlUtil imageUrlUtil;

    /** 切片上下文：空配置，仅用于取代主类配置（屏蔽 @MapperScan 与全量组件扫描） */
    @Configuration
    static class SliceContext {
    }

    /** 切片内显式开启 AOP：保证 @RequireVerified 切面（4031 分流）在 MockMvc 下真实生效 */
    @TestConfiguration
    @EnableAspectJAutoProxy
    static class AopTestConfig {
    }

    // ==================== 链路 1：登录 ====================

    @Test
    void wechatLogin_success_returnsTokenAndUserInfo() throws Exception {
        UserInfoVO userInfo = new UserInfoVO();
        userInfo.setId(USER_ID);
        userInfo.setUsername("wx_tail16");
        userInfo.setVerified(false);
        when(authService.wechatLogin("wx-login-code")).thenReturn(new LoginResp("minted-jwt", userInfo));

        mockMvc.perform(post("/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"wx-login-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("minted-jwt"))
                .andExpect(jsonPath("$.data.userInfo.id").value(USER_ID))
                .andExpect(jsonPath("$.data.userInfo.verified").value(false));

        verify(authService).wechatLogin("wx-login-code");
    }

    @Test
    void wechatLogin_blankCode_returns400() throws Exception {
        mockMvc.perform(post("/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 链路 2：菜品详情 ====================

    @Test
    void dishDetail_success_returnsKeyFields() throws Exception {
        DishVO vo = new DishVO();
        vo.setId(1L);
        vo.setName("牛肉拉面");
        vo.setPrice(1200);
        vo.setAvgRating(new BigDecimal("4.5"));
        vo.setRatingCount(20);
        when(dishService.getDishDetail(1L)).thenReturn(vo);

        mockMvc.perform(get("/dishes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("牛肉拉面"))
                // 金额一律为「分」（int），元换算只在端上
                .andExpect(jsonPath("$.data.price").value(1200))
                .andExpect(jsonPath("$.data.ratingCount").value(20));
    }

    @Test
    void dishDetail_notFound_returnsCode400() throws Exception {
        // 真实口径：Service 抛 BusinessException（默认 code=400），统一响应由 HTTP 200 承载 body.code
        when(dishService.getDishDetail(999L)).thenThrow(new BusinessException("菜品不存在"));

        mockMvc.perform(get("/dishes/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("菜品不存在"));
    }

    // ==================== 链路 3：评价 ====================

    @Test
    void submitReview_anonymous_returns401() throws Exception {
        mockMvc.perform(post("/dishes/1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void submitReview_unverifiedUser_returns4031() throws Exception {
        // verified=0（游客态）：@RequireVerified 必须给出 4031 细分码而非普通 403
        when(userMapper.selectById(USER_ID)).thenReturn(user(0));

        mockMvc.perform(post("/dishes/1/reviews")
                        .header("Authorization", studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4031))
                .andExpect(jsonPath("$.message").value("请先完成学号邮箱认证"));
    }

    @Test
    void submitReview_verifiedUser_returns200() throws Exception {
        when(userMapper.selectById(USER_ID)).thenReturn(user(1));

        mockMvc.perform(post("/dishes/1/reviews")
                        .header("Authorization", studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 菜品归属由路径锁定：Service 签名 (userId, dishId, req)
        verify(reviewService).submitReview(eq(USER_ID), eq(1L), any());
    }

    @Test
    void updateReview_unverifiedUser_returns4031() throws Exception {
        // 重新评价（PUT /reviews/{id}）同口径要求认证：未认证 → 4031，不进入 Service
        when(userMapper.selectById(USER_ID)).thenReturn(user(0));

        mockMvc.perform(put("/reviews/8")
                        .header("Authorization", studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4031));
    }

    @Test
    void updateReview_verifiedUser_returns200() throws Exception {
        when(userMapper.selectById(USER_ID)).thenReturn(user(1));

        mockMvc.perform(put("/reviews/8")
                        .header("Authorization", studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(reviewService).updateReview(eq(8L), eq(USER_ID), any());
    }

    // ==================== 链路 4：反馈 ====================

    @Test
    void submitFeedback_nonSuggestionWithSub_returns400() throws Exception {
        // sub 严格模式（Service 层真实校验）：非 suggestion 携带 sub 一律 400，不静默忽略
        mockMvc.perform(post("/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"add\",\"sub\":\"idea\",\"content\":\"推荐一道菜\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("suggestion")));
    }

    @Test
    void submitFeedback_invalidType_returns400() throws Exception {
        // bug/other 为历史遗留类型，写入白名单外 → 400
        mockMvc.perform(post("/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"bug\",\"content\":\"历史类型禁新增\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * 机检 risky → 400（2026-09-15 取消人工复核后的统一口径：仅 risky 拒绝，pass/review 一律放行）。
     * <p>
     * 走真实 {@link FeedbackServiceImpl}（机检调用点保留在写入路径内，删列不得顺手摘掉机检），
     * 仅打桩 {@link ContentSecurityService} 让其抛违规异常，断言 400 文案透传且内容不落库。
     */
    @Test
    void submitFeedback_riskyContent_returns400AndNotPersisted() throws Exception {
        when(sensitiveFilter.filter(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(contentSecurityService.checkText(any(), anyString(), eq(2)))
                .thenThrow(new BusinessException(400, "内容包含违规信息，请修改后重试"));

        mockMvc.perform(post("/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"suggestion\",\"sub\":\"idea\",\"content\":\"违规内容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("内容包含违规信息，请修改后重试"));

        // risky 内容不得落库
        verify(feedbackMapper, never()).insert(any());
    }

    @Test
    void submitFeedback_guestSuggestion_persistsSubAndReturns200() throws Exception {
        when(sensitiveFilter.filter(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        // 请求体故意携带已退役的 contact 字段（2026-09-16 产品定型「不收集联系方式」）：
        // FeedbackReq.contact 已删除，Jackson 忽略未知字段，请求应正常落库且不含联系方式语义
        mockMvc.perform(post("/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"suggestion\",\"sub\":\"idea\",\"content\":\"希望增加素食档口\",\"contact\":\"2024001@bjtu.edu.cn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackMapper).insert(captor.capture());
        Feedback saved = captor.getValue();
        Assertions.assertEquals("suggestion", saved.getType());
        Assertions.assertEquals("idea", saved.getSub());
        // 游客反馈：不信任前端 userId，登录态缺失即 null
        Assertions.assertNull(saved.getUserId());
    }

    // ==================== 防回归：评价/浏览端点 RESTful 化（2026-09-20 拍板，旧路径不再提供） ====================

    /**
     * 防回归：评价与浏览端点改为 RESTful 子资源路径后，
     * 新路径（{@code /dishes/{id}/reviews}、{@code /dishes/{id}/views}、{@code /my/reviews}）应在册，
     * 旧路径 {@code /reviews}（查询参数表达归属）不得再注册。
     * <p>
     * 断言手法：直接查切片内 {@link RequestMappingHandlerMapping} 的注册映射（不经请求）。
     */
    @Test
    void reviewEndpoints_restfulPathsRegistered_legacyPathRemoved() {
        Set<String> patterns = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .collect(Collectors.toSet());

        Assertions.assertTrue(patterns.contains("/dishes/{id}/reviews"),
                "评价列表/发表的新路径 /dishes/{id}/reviews 应在册；实际映射：" + patterns);
        Assertions.assertTrue(patterns.contains("/dishes/{id}/views"),
                "浏览量上报的新路径 /dishes/{id}/views 应在册；实际映射：" + patterns);
        Assertions.assertTrue(patterns.contains("/my/reviews"),
                "我的评价 /my/reviews 应在册；实际映射：" + patterns);
        Assertions.assertFalse(patterns.contains("/reviews"),
                "旧路径 /reviews 应随 RESTful 化删除；实际映射：" + patterns);
        Assertions.assertFalse(patterns.contains("/dishes/{id}/view"),
                "旧浏览量路径 /dishes/{id}/view 应删除；实际映射：" + patterns);
    }

    // ==================== 链路 5：上传（管理端口令守卫） ====================

    @Test
    void uploadImage_withoutAdminToken_returns403() throws Exception {
        // 403 由 AdminTokenFilter 直接写出（口令缺失/无效失败的 fail-closed 行为）
        mockMvc.perform(multipart("/upload/image").file(jpegFile()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(ADMIN_TOKEN_INVALID_MESSAGE));
    }

    @Test
    void uploadImage_wrongAdminToken_returns403() throws Exception {
        mockMvc.perform(multipart("/upload/image").file(jpegFile())
                        .header(ADMIN_TOKEN_HEADER, "wrong-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(ADMIN_TOKEN_INVALID_MESSAGE));
    }

    @Test
    void uploadImage_withAdminToken_returnsUrl() throws Exception {
        when(uploadService.uploadImage(any()))
                .thenReturn(Map.of("url", "http://localhost:8080/api/images/2026/05/a.jpg",
                        "relativeUrl", "/images/2026/05/a.jpg"));

        mockMvc.perform(multipart("/upload/image").file(jpegFile())
                        .header(ADMIN_TOKEN_HEADER, TEST_ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.url").value("http://localhost:8080/api/images/2026/05/a.jpg"));
    }

    // ==================== 链路 6：管理端 ====================

    @Test
    void adminFeedbackList_withoutToken_returns403() throws Exception {
        // /admin/** 由 AdminTokenFilter 把关（fail-closed），无口令一律 403
        mockMvc.perform(get("/admin/feedbacks"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(ADMIN_TOKEN_INVALID_MESSAGE));
    }

    @Test
    void adminFeedbackList_withToken_returnsPageContract() throws Exception {
        // 空页桩：管理端列表仅验证分页契约字段，不查库
        IPage<Feedback> emptyPage = new Page<>(1, 10);
        when(feedbackMapper.selectPage(any(), any())).thenReturn(emptyPage);

        mockMvc.perform(get("/admin/feedbacks").header(ADMIN_TOKEN_HEADER, TEST_ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10));
    }

    /**
     * 防回归（2026-09-15 用户拍板「品类整链删除」）：{@code GET /admin/categories} 已无任何处理器。
     * <p>
     * 此前带正确口令返回 200/code=200（CategoryAdminController 在线）；品类全链（Controller /
     * Service / Mapper / Entity + dish.categoryId + category 表）整体退役后，该路径必须失效。
     * <p>
     * <b>状态码口径（实测校准，非 404）</b>：Spring 6.1 起未匹配到任何 {@code @RequestMapping}
     * 的路径（含 /admin/categories）会落到静态资源处理器并抛 {@code NoResourceFoundException}，
     * 由 {@link GlobalExceptionHandler#handleNoResourceFoundException} 统一转为
     * <b>HTTP 400 + body.code=400</b>（message「资源不存在」，见 GlobalExceptionHandler 既有 BE-110 口径，
     * 有意不采用 404）。故本用例断言「400 + code=400 + 资源不存在」，
     * 且非 403「管理端口令无效」——正好证明请求已通过 AdminTokenFilter 口令校验、
     * 失败原因是「路径无处理器」（端点确已删除），回归时会直接失败。
     */
    @Test
    void adminCategories_removed_endpointGone_returns400() throws Exception {
        mockMvc.perform(get("/admin/categories").header(ADMIN_TOKEN_HEADER, TEST_ADMIN_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    /**
     * 防回归（2026-09-15 用户拍板「取消人工复核，sec_state 全链退役」）：
     * {@code PUT /admin/reviews/{id}/sec-state} 必须不再注册（人工复核队列已无存续价值）。
     * <p>
     * 断言手法：直接查切片内 {@link RequestMappingHandlerMapping} 的注册映射（不经请求，
     * 规避「PUT 打到 /** 静态资源处理器」的状态码不确定性）。并以保留的
     * {@code PUT /admin/reviews/{id}/hide}（举报→下架的事后处置通道）作<b>阳性对照</b>：
     * 证明 {@link ReviewAdminController} 确实已注册在本切片，故「无 sec-state 映射」不是空洞断言。
     */
    @Test
    void adminReviewSecState_removedEndpointGone() {
        Set<String> patterns = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .collect(Collectors.toSet());

        Assertions.assertTrue(patterns.contains("/admin/reviews/{id}/hide"),
                "保留端点 /admin/reviews/{id}/hide 应在册；实际映射：" + patterns);
        Assertions.assertFalse(patterns.stream().anyMatch(p -> p.contains("sec-state")),
                "sec-state 端点应已随列退役删除；实际映射：" + patterns);
    }

    // ==================== 辅助方法 ====================

    /** 用真实 JwtUtil 签发学生态 token（JWT 仅含 userId/username，verified 不入 token；role claim 已退役） */
    private String studentToken() {
        return "Bearer " + jwtUtil.createToken(USER_ID, "smoke");
    }

    private User user(int verified) {
        User user = new User();
        user.setId(USER_ID);
        user.setStatus("active");
        user.setVerified(verified);
        return user;
    }

    /** 评价请求体（菜品归属由路径锁定，不含 dishId） */
    private String reviewBody() {
        return "{\"rating\":5,\"content\":\"味道不错，分量也足。\"}";
    }

    private MockMultipartFile jpegFile() {
        return new MockMultipartFile("file", "a.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3});
    }
}
