package com.bjtufood;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.config.AdminProperties;
import com.bjtufood.auth.support.AdminJwtUtil;
import com.bjtufood.auth.config.AdminAuthFilter;
import com.bjtufood.auth.config.JwtAuthFilter;
import com.bjtufood.auth.config.JwtProperties;
import com.bjtufood.auth.config.SecurityConfig;
import com.bjtufood.auth.config.TokenBlacklist;
import com.bjtufood.auth.controller.AdminAuthController;
import com.bjtufood.auth.controller.AuthController;
import com.bjtufood.auth.dto.LoginVO;
import com.bjtufood.auth.dto.UserInfoVO;
import com.bjtufood.upload.controller.AdminUploadController;
import com.bjtufood.upload.dto.UploadResultVO;
import com.bjtufood.auth.entity.AdminAccount;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.EmailVerificationCodeMapper;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.auth.service.AdminAccountService;
import com.bjtufood.auth.service.AuthService;
import com.bjtufood.auth.aspect.RequireVerifiedAspect;
import com.bjtufood.auth.service.impl.AccountCloser;
import com.bjtufood.auth.service.impl.UserServiceImpl;
import com.bjtufood.auth.service.impl.UserStateWriteLock;
import com.bjtufood.common.config.CorsProperties;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.exception.GlobalExceptionHandler;
import com.bjtufood.common.ratelimit.IpRateLimiter;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.auth.support.JwtUtil;
import com.bjtufood.moderation.service.LocalSensitiveFilter;
import com.bjtufood.moderation.service.ContentSecurityService;
import com.bjtufood.dish.controller.DishController;
import com.bjtufood.dish.dto.DishAttributeItem;
import com.bjtufood.dish.dto.DishDetailVO;
import com.bjtufood.dish.dto.GuessLikeVO;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.feedback.controller.FeedbackController;
import com.bjtufood.feedback.controller.OptionDictionaryController;
import com.bjtufood.feedback.controller.ReportController;
import com.bjtufood.feedback.controller.admin.FeedbackAdminController;
import com.bjtufood.feedback.controller.admin.ReportAdminController;
import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.feedback.entity.FeedbackOption;
import com.bjtufood.feedback.entity.Report;
import com.bjtufood.feedback.mapper.FeedbackMapper;
import com.bjtufood.feedback.mapper.FeedbackOptionMapper;
import com.bjtufood.feedback.mapper.ReportMapper;
import com.bjtufood.feedback.mapper.ReportOptionMapper;
import com.bjtufood.feedback.service.impl.FeedbackOptionServiceImpl;
import com.bjtufood.feedback.service.impl.FeedbackPersister;
import com.bjtufood.feedback.service.impl.FeedbackServiceImpl;
import com.bjtufood.feedback.service.impl.ReportOptionServiceImpl;
import com.bjtufood.feedback.service.impl.ReportServiceImpl;
import com.bjtufood.moderation.constant.ModerationConst;
import com.bjtufood.moderation.entity.ModerationTask;
import com.bjtufood.moderation.mapper.ModerationTaskMapper;
import com.bjtufood.moderation.service.impl.ModerationTaskServiceImpl;
import com.bjtufood.notification.service.NotificationService;
import com.bjtufood.review.controller.ReviewController;
import com.bjtufood.review.controller.admin.ReviewAdminController;
import com.bjtufood.review.service.ReviewService;
import com.bjtufood.upload.controller.UploadController;
import com.bjtufood.upload.service.UploadService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
 * 接口层冒烟测试（MockMvc 切片 + {@code @MockBean} 打桩 Mapper，<b>不连数据库、不连外网</b>）。
 * <p>
 * 目的：为接口层留下最小必要的契约兜底，覆盖关键链路的「HTTP 状态码 + 统一响应 code」契约：
 * <ol>
 *   <li>登录：{@code POST /auth/wechat-login}（200/code=200 + token/userInfo，缺 code → 400）；</li>
 *   <li>菜品详情：{@code GET /dishes/{id}}（200 + 关键字段；不存在 → body code=4001）；</li>
 *   <li>评价：{@code POST /dishes/{id}/reviews}（匿名 401 / 已登录未认证 4031 / 已认证 200）
 *       与路径防回归（旧 {@code /reviews} 不再注册）；</li>
 *   <li>三类用户提交：意见反馈 {@code POST /feedback}、举报 {@code POST /reviews/{id}/report}
 *       （原因缺失 400 / 原因非法 400 / 评价不存在 4001）；</li>
 *   <li>分类字典：{@code GET /feedback-options} 与 {@code GET /report-options}（PUB，免登录 200）；</li>
 *   <li>管理端：{@code GET /admin/feedbacks}、{@code GET /admin/reports}（无凭证 401，带 token 200 + 分页契约）、
 *       {@code PUT /admin/feedbacks/{id}}（处置：不采纳未填文案 → 400；正常处置 → 200）；</li>
 *   <li>上传：{@code POST /admin/upload}（无/错 Bearer token → 401，正确 token 200）；</li>
 *   <li>防回归：{@code GET /admin/categories}（品类整链退役，带正确口令亦无处理器）、
 *       {@code PUT /admin/reviews/{id}/sec-state}（sec_state 全链退役，映射表中不得再注册）。</li>
 * </ol>
 * 实现要点：
 * <ul>
 *   <li>主类 {@code BjtuFoodApplication} 上的 {@code @MapperScan} 会在切片内注册 MapperFactoryBean
 *       （需要 SqlSessionFactory → 需连库），故本测试用内嵌空配置 {@link SliceContext} 取代主配置，
 *       并<b>显式 @Import</b> 被测 Bean（控制器 / 统一异常处理 / 真实安全链路），使切片零数据库依赖；</li>
 *   <li>鉴权走<b>真实</b> {@link SecurityConfig} + {@link JwtAuthFilter} + {@link AdminAuthFilter}；
 *       token 由真实 {@link JwtUtil} 以测试密钥签发，故 401/403/4031 均为真实分流结果；</li>
 *   <li><b>提交 / 处置链路的校验口径在 Service 层</b>（字典白名单、内容安检、处置结论与文案校验），
 *       故导入真实 {@code FeedbackServiceImpl} / {@code ReportServiceImpl} / {@code ModerationTaskServiceImpl}
 *       与两个字典实现，仅打桩其依赖的 Mapper；</li>
 *   <li>所有桩数据在各用例内建立，避免 Mockito 严格模式判定为多余桩。</li>
 * </ul>
 */
@WebMvcTest
@ContextConfiguration(classes = SmokeApiTest.SliceContext.class)
@Import({
        // 被测控制器（显式引入，不依赖组件扫描）
        AuthController.class,
        // 管理端账密登录 / 登录态（TD-19 · TD-23）：断言「登录端点在鉴权白名单内」这一回归
        AdminAuthController.class,
        DishController.class,
        ReviewController.class,
        FeedbackController.class,
        ReportController.class,
        OptionDictionaryController.class,
        UploadController.class,
        AdminUploadController.class,
        FeedbackAdminController.class,
        ReportAdminController.class,
        // 分类字典管理端（反馈类型 / 举报原因）：端点是否在册的防回归断言要扫到它们
        com.bjtufood.feedback.controller.admin.FeedbackOptionAdminController.class,
        com.bjtufood.feedback.controller.admin.ReportOptionAdminController.class,
        // 后台评价管理（用于 sec-state 端点退役的映射表回归断言）
        ReviewAdminController.class,
        // 统一异常处理（HTTP 状态码 + body.code 口径的唯一真源）
        GlobalExceptionHandler.class,
        // 类型化配置：本切片以 @ContextConfiguration 取代主配置，
        // 故 JwtUtil / AdminAuthFilter 所依赖的配置 Bean 需在此显式登记
        // （主类侧由 @EnableConfigurationProperties 统一登记，两者需同步）。
        JwtProperties.class,
        AdminProperties.class,
        // CORS 受信任源：JwtAuthFilter 的 Origin 二次校验依赖它（新增配置类必须在此与主类同步登记）
        CorsProperties.class,
        // 真实安全链路
        SecurityConfig.class,
        JwtAuthFilter.class,
        AdminAuthFilter.class,
        AdminJwtUtil.class,
        JwtUtil.class,
        TokenBlacklist.class,
        // @RequireVerified → 4031 未认证分流
        RequireVerifiedAspect.class,
        // auth 域真实实现：切面经 UserService.requireUgcAuthorized 判定准入（判据与错误码收敛在 auth），
        // 同时供 FeedbackServiceImpl（昵称投影）消费；其 UserMapper 仍打桩，故不查库
        UserServiceImpl.class,
        // UserServiceImpl 的「DB status 写 + 黑名单写」临界区组件（无外部依赖，切片内用真实实例即可）
        UserStateWriteLock.class,
        // UserServiceImpl 的解绑邮箱 / 删除账号路径依赖：验证码清理 Mapper 打桩（见下方 @MockBean）、
        // 注销执行器用真实实例（事件发布器 / TokenBlacklist 由切片提供）
        AccountCloser.class,
        // 提交链路真实实现：入参白名单（反馈类型字典 / 举报原因字典）与内容安检在 Service 层
        FeedbackServiceImpl.class,
        ReportServiceImpl.class,
        FeedbackOptionServiceImpl.class,
        ReportOptionServiceImpl.class,
        // 处置状态机真实实现：处置结论 / 文案校验（rejected 必填）在此强制
        ModerationTaskServiceImpl.class,
        // 反馈落库事务 Bean（反馈行 + 处置任务行同一事务）
        FeedbackPersister.class,
        // 切片内显式开启 AOP，保证上述切面在 MockMvc 下生效
        SmokeApiTest.AopTestConfig.class
})
@TestPropertySource(properties = {
        // 管理端 JWT 密钥（>=32 字节；TD-20 AdminJwtUtil 启动期 fail-fast 需要）
        "admin.jwt.secret=SmokeApiTestAdminJwtSecret_0123456789ABC",
        "admin.jwt.expiration-seconds=3600",
        // 测试专用强密钥（>=32 字节且非仓库默认值，规避 JwtUtil 启动期 fail-fast）
        "jwt.secret=SmokeApiTestOnlySecretKey_0123456789ABCDEF",
        "jwt.expiration=3600000"
})
class SmokeApiTest {

    /** 管理端鉴权头：{@code Authorization: Bearer <管理端 JWT>} */
    private static final String ADMIN_AUTH_HEADER = "Authorization";
    /** 签发一枚<b>真实</b>管理端 JWT（用 @TestPropertySource 注入的 admin.jwt.secret，与 AdminAuthFilter 同一套校验） */
    private static String adminBearer() {
        AdminProperties props = new AdminProperties();
        AdminProperties.Jwt jwt = new AdminProperties.Jwt();
        jwt.setSecret("SmokeApiTestAdminJwtSecret_0123456789ABC");
        jwt.setExpirationSeconds(3600L);
        props.setJwt(jwt);
        return "Bearer " + new AdminJwtUtil(props).createToken(1L, "smoke-admin", 1);
    }

    /**
     * 打桩：管理端 JWT 内账号 ID=1 的账号**存在且启用**。
     * <p>
     * {@code AdminAuthFilter} 验签后按 token 内 ID 回查 {@code admin.status}
     * （凭证吊销，TD-25）；切片不连库，未打桩则回查为 null ⇒ 401。
     * 用 {@code lenient()} 避免「未触发该回查的用例」被判无用桩。
     */
    @BeforeEach
    void stubActiveAdminAccount() {
        AdminAccount account = new AdminAccount();
        account.setId(1L);
        account.setStatus("on");
        // 凭证版本与 adminBearer() 内嵌值一致（改密后版本自增 ⇒ 旧 token 失效）
        account.setCredentialVersion(1);
        // 全权角色：默认桩按 super（写 / 删除均放行），RBAC 用例在自身内部覆盖为受限角色
        account.setRole("super");
        org.mockito.Mockito.lenient().when(adminAccountService.findById(1L)).thenReturn(account);
    }

    /** AdminAuthFilter 鉴权失败时的对外文案（断言 401 来自该过滤器） */
    private static final String ADMIN_TOKEN_INVALID_MESSAGE = "管理端登录已失效，请重新登录";
    /** 测试登录用户 ID（与签发的 JWT 一致） */
    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    /** 管理端 JWT 签发器（/admin/auth/me 用例需以同一套密钥签发凭证） */
    @Autowired
    private AdminJwtUtil adminJwtUtil;

    /** 登录口令比对（SecurityConfig 提供的 BCrypt 实现） */
    @Autowired
    private PasswordEncoder passwordEncoder;

    /** 切片内真实注册的处理器映射表（用于「端点是否在册」的防回归断言，不发起请求） */
    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @MockBean
    private AuthService authService;
    /** 管理员账号（TD-16）：切片不连库，登录 / 登录态用例按需打桩 */
    @MockBean
    private AdminAccountService adminAccountService;
    /** ID 枚举检测（DishController / ReviewController 构造依赖）：打桩避免测试间按 IP 互相污染计数 */
    @MockBean
    private com.bjtufood.common.ratelimit.IdEnumerationGuard idEnumerationGuard;
    @MockBean
    private DishService dishService;
    @MockBean
    private ReviewService reviewService;
    @MockBean
    private UploadService uploadService;
    @MockBean
    private IpRateLimiter ipRateLimiter;
    /** 管理端账号维度登录防护（AdminAuthController 构造依赖）：打桩使锁定 / 退避判定恒为「无」 */
    @MockBean
    private com.bjtufood.auth.support.LoginAttemptGuard loginAttemptGuard;
    /** 安全告警通道：打桩，避免测试真的发起 HTTP 推送 */
    @MockBean
    private com.bjtufood.common.alert.SecurityAlertNotifier securityAlertNotifier;
    /** @RequireVerified 切面按 user.email（认证态唯一判据）实时判定，打桩避免查库 */
    @MockBean
    private UserMapper userMapper;
    /** UserServiceImpl 解绑邮箱时的验证码清理依赖（表无 user_id 列，按 email 匹配），打桩避免查库 */
    @MockBean
    private EmailVerificationCodeMapper emailVerificationCodeMapper;
    @MockBean
    private FeedbackMapper feedbackMapper;
    @MockBean
    private ReportMapper reportMapper;
    /** 反馈类型字典表：打桩避免查库（提交白名单 / 公开下发 / 管理端翻译都经它） */
    @MockBean
    private FeedbackOptionMapper feedbackOptionMapper;
    /** 举报原因字典表：打桩避免查库 */
    @MockBean
    private ReportOptionMapper reportOptionMapper;
    /** 处置任务表：打桩避免查库（提交预建 / 处置归档都在此） */
    @MockBean
    private ModerationTaskMapper moderationTaskMapper;
    @MockBean
    private LocalSensitiveFilter localSensitiveFilter;
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
        when(authService.wechatLogin("wx-login-code")).thenReturn(new LoginVO("minted-jwt", userInfo));

        mockMvc.perform(post("/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"wx-login-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("minted-jwt"))
                .andExpect(jsonPath("$.data.userInfo.id").value(USER_ID))
                // 字段集契约回归：verified 已删除（认证态由 email 非空派生），不得回流
                .andExpect(jsonPath("$.data.userInfo.verified").doesNotExist())
                // wxId 端上零消费，不得回流（账号标识保留在 user 表与 JWT 载荷）
                .andExpect(jsonPath("$.data.userInfo.wxId").doesNotExist());

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
        DishDetailVO vo = new DishDetailVO();
        vo.setId(1L);
        vo.setName("牛肉拉面");
        vo.setPrice(1200);
        vo.setAvgRating(new BigDecimal("4.5"));
        // 描述属性（动态属性模型）：值即中文，端上零翻译；dimensionId = 维度 ID
        vo.setAttributes(List.of(new DishAttributeItem(3L, "口味",
                List.of("辣", "酸"))));
        when(dishService.getDishDetail(eq(1L))).thenReturn(vo);

        mockMvc.perform(get("/dishes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("牛肉拉面"))
                // 金额一律为「分」（int），元换算只在端上
                .andExpect(jsonPath("$.data.price").value(1200))
                // 契约回归：ratingCount 已从详情出参删除（零消费），不得回流
                .andExpect(jsonPath("$.data.ratingCount").doesNotExist())
                .andExpect(jsonPath("$.data.attributes[0].dimensionId").value(3))
                .andExpect(jsonPath("$.data.attributes[0].value[0]").value("辣"));
    }

    @Test
    void dishDetail_notFoundAndOffShelf_returnsCode4001() throws Exception {
        // 契约：资源不存在用**专属业务码 4001**。统一响应由 HTTP 200 承载 body.code。
        when(dishService.getDishDetail(eq(999L))).thenThrow(new BusinessException(4001, "菜品不存在"));

        mockMvc.perform(get("/dishes/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4001))
                .andExpect(jsonPath("$.message").value("菜品不存在"));
    }

    // ==================== 链路 2b：猜你喜欢（会话级 seed） ====================

    /**
     * {@code seed} 是**可选**参数：控制器须原样透传给 Service（参数名写错即桩不匹配 ⇒ data 为 null）。
     */
    @Test
    void guessLike_seedPassedThroughToService() throws Exception {
        when(dishService.guessLike("session-abc")).thenReturn(List.of(new GuessLikeVO("牛肉拉面")));

        mockMvc.perform(get("/dishes/for-you").param("seed", "session-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("牛肉拉面"));
    }

    @Test
    void guessLike_seedOmitted_isNotRequired() throws Exception {
        when(dishService.guessLike(isNull())).thenReturn(List.of(new GuessLikeVO("蛋炒饭")));

        mockMvc.perform(get("/dishes/for-you"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("蛋炒饭"));
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
        // bind_email=NULL（游客态）：@RequireVerified 必须给出 4031 细分码而非普通 403
        when(userMapper.selectById(USER_ID)).thenReturn(user(false));

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
        when(userMapper.selectById(USER_ID)).thenReturn(user(true));
        when(reviewService.submitReview(eq(USER_ID), eq(1L), any())).thenReturn(9L);

        mockMvc.perform(post("/dishes/1/reviews")
                        .header("Authorization", studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(9));

        // 菜品归属由路径锁定：Service 签名 (userId, dishId, req)
        verify(reviewService).submitReview(eq(USER_ID), eq(1L), any());
    }

    // ==================== 链路 4a：意见反馈 ====================

    @Test
    void submitFeedback_legalOption_returns200AndPersists() throws Exception {
        when(localSensitiveFilter.filter(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        // 类型字典命中启用项（字典规则由 FeedbackOptionServiceImplTest 覆盖）
        when(feedbackOptionMapper.selectCount(any())).thenReturn(1L);
        when(feedbackMapper.insert(any())).thenAnswer(invocation -> {
            ((Feedback) invocation.getArgument(0)).setId(11L);
            return 1;
        });

        mockMvc.perform(post("/feedback")
                        .header("Authorization", studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":2,\"content\":\"希望增加素食档口\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackMapper).insert(captor.capture());
        Feedback saved = captor.getValue();
        Assertions.assertEquals(2L, saved.getOptionId());
        // 归属只认 JWT：写请求一律绑定当前登录用户，不信任请求体
        Assertions.assertEquals(USER_ID, saved.getUserId());

        // 「未处置」是显式行：反馈行与处置任务行同一落库事务
        ArgumentCaptor<ModerationTask> task = ArgumentCaptor.forClass(ModerationTask.class);
        verify(moderationTaskMapper).insert(task.capture());
        Assertions.assertEquals(ModerationConst.TYPE_FEEDBACK, task.getValue().getType());
        Assertions.assertEquals(11L, task.getValue().getTypeId());
        Assertions.assertEquals(ModerationConst.STATUS_PENDING, task.getValue().getStatus());
    }

    /**
     * 类型未命中启用字典（不存在 / 已停用）→ 400：字典是类型值域的唯一真源，
     * 收紧写入口后不得再出现「任意字符串原样落库」。
     */
    @Test
    void submitFeedback_illegalOption_returns400() throws Exception {
        when(localSensitiveFilter.filter(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        // 不加字典桩 ⇒ selectCount 返回 0 ⇒ 未命中启用项

        mockMvc.perform(post("/feedback")
                        .header("Authorization", studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":999,\"content\":\"类型不存在\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("反馈类型非法"));

        verify(feedbackMapper, never()).insert(any());
    }

    @Test
    void submitFeedback_missingOption_returns400() throws Exception {
        // optionId **必选**：DTO @NotNull 在进入 Controller 方法体前拦截 → HTTP 400 + body code=400
        mockMvc.perform(post("/feedback")
                        .header("Authorization", studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"未选类型\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * 内容安全检测 risky → 400。
     * <p>
     * 走真实 {@link FeedbackServiceImpl}（内容安全检测调用点保留在写入路径内，删列不得顺手摘掉内容安全检测），
     * 仅打桩 {@link ContentSecurityService} 让其抛违规异常，断言 400 文案透传且内容不落库。
     */
    @Test
    void submitFeedback_riskyContent_returns400AndNotPersisted() throws Exception {
        when(localSensitiveFilter.filter(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(feedbackOptionMapper.selectCount(any())).thenReturn(1L);
        when(contentSecurityService.checkText(any(), anyString(), eq(2)))
                .thenThrow(new BusinessException(400, "内容包含违规信息，请修改后重试"));

        mockMvc.perform(post("/feedback")
                        .header("Authorization", studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":1,\"content\":\"违规内容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("内容包含违规信息，请修改后重试"));

        // risky 内容不得落库
        verify(feedbackMapper, never()).insert(any());
    }

    @Test
    void submitFeedback_blankContent_returns400() throws Exception {
        when(localSensitiveFilter.filter(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/feedback")
                        .header("Authorization", studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":1,\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * 未携带凭证 ⇒ 401：三类用户提交一律需登录，
     * 匿名请求不再可写（否则脚本可无 token 灌库且无法归因、无法按账号限频）。
     */
    @Test
    void submitFeedback_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":1,\"content\":\"匿名灌库尝试\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    /** 菜品问题反馈同口径：无登录态 ⇒ 401（记录恒绑定真实用户，`correction.user_id` 必填 + 外键）。 */
    @Test
    void submitCorrection_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/dishes/1/correction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"gone\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    /** 请求体携带**未声明字段** ⇒ 400（全局开启拒绝未知字段，封堵字段注入试探） */
    @Test
    void submitFeedback_unknownField_returns400() throws Exception {
        mockMvc.perform(post("/feedback")
                        .header("Authorization", studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":1,\"content\":\"字段注入试探\",\"userId\":999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 链路 4b：分类字典（PUB） ====================

    @Test
    void feedbackOptions_publicEndpoint_returnsEnabledItems() throws Exception {
        FeedbackOption option = new FeedbackOption();
        option.setId(1L);
        option.setName("程序功能Bug");
        option.setStatus("on");
        when(feedbackOptionMapper.selectList(any())).thenReturn(List.of(option));

        mockMvc.perform(get("/feedback-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("程序功能Bug"));
    }

    @Test
    void reportOptions_publicEndpoint_returnsEnabledItems() throws Exception {
        var option = new com.bjtufood.feedback.entity.ReportOption();
        option.setId(3L);
        option.setName("垃圾广告 / 营销刷屏");
        option.setStatus("on");
        when(reportOptionMapper.selectList(any())).thenReturn(List.of(option));

        mockMvc.perform(get("/report-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(3));
    }

    // ==================== 链路 4c：评价举报 ====================

    @Test
    void reportReview_withReason_persistsReportAndTask() throws Exception {
        when(reviewService.existsVisibleById(3L)).thenReturn(true);
        // 举报原因白名单查表：原因 ID 1 存在且启用 ⇒ selectCount > 0
        when(reportOptionMapper.selectCount(any())).thenReturn(1L);
        // 去重：同一用户对该评价未举报过
        when(reportMapper.selectCount(any())).thenReturn(0L);
        when(reportMapper.insert(any())).thenAnswer(invocation -> {
            ((Report) invocation.getArgument(0)).setId(4L);
            return 1;
        });
        mockMvc.perform(post("/reviews/3/report")
                        .header("Authorization", studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportMapper).insert(captor.capture());
        Report saved = captor.getValue();
        Assertions.assertEquals(USER_ID, saved.getUserId());
        Assertions.assertEquals(1L, saved.getOptionId());
        // 被举报对象由路径表达（不在请求体里）
        Assertions.assertEquals(3L, saved.getReviewId());

        ArgumentCaptor<ModerationTask> task = ArgumentCaptor.forClass(ModerationTask.class);
        verify(moderationTaskMapper).insert(task.capture());
        Assertions.assertEquals(ModerationConst.TYPE_REPORT, task.getValue().getType());
        Assertions.assertEquals(4L, task.getValue().getTypeId());
    }

    @Test
    void reportReview_missingReason_returns400() throws Exception {
        // 举报原因**必选**：DTO @NotNull 在进入 Controller 方法体前拦截 → HTTP 400 + body code=400
        mockMvc.perform(post("/reviews/3/report")
                        .header("Authorization", studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void reportReview_illegalReason_returns400() throws Exception {
        // 原因 ID 不在字典白名单 → 400（非法入参必须报错，杜绝脏值入库）
        when(reviewService.existsVisibleById(3L)).thenReturn(true);
        mockMvc.perform(post("/reviews/3/report")
                        .header("Authorization", studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("举报原因")));
    }

    @Test
    void reportReview_reviewNotFound_returns4001() throws Exception {
        // 被举报评价不存在 / 不可见 → 4001（RESTful 子资源：父资源缺失即 4001）
        when(reviewService.existsVisibleById(3L)).thenReturn(false);
        mockMvc.perform(post("/reviews/3/report")
                        .header("Authorization", studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4001));
    }

    // ==================== 防回归：评价端点 RESTful 化 + 浏览计数内聚详情 ====================

    @Test
    void reviewEndpoints_restfulPathsRegistered_legacyPathRemoved() {
        Set<String> patterns = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .collect(Collectors.toSet());

        Assertions.assertTrue(patterns.contains("/dishes/{id}/reviews"),
                "评价列表/发表的新路径 /dishes/{id}/reviews 应在册；实际映射：" + patterns);
        Assertions.assertTrue(patterns.contains("/my/reviews"),
                "我的评价 /my/reviews 应在册；实际映射：" + patterns);
        Assertions.assertFalse(patterns.contains("/reviews"),
                "旧路径 /reviews 应随 RESTful 化删除；实际映射：" + patterns);
        Assertions.assertFalse(patterns.contains("/dishes/{id}/views"),
                "独立浏览上报端点应随计数内聚于 GET /dishes/{id} 删除；实际映射：" + patterns);
        Assertions.assertFalse(patterns.contains("/dishes/{id}/view"),
                "旧浏览量路径 /dishes/{id}/view 应删除；实际映射：" + patterns);
    }

    // ==================== 链路 5：上传（管理端口令守卫） ====================

    @Test
    void uploadImage_withoutAdminToken_returns401() throws Exception {
        // 401 由 AdminAuthFilter 直接写出（未携带凭证的 fail-closed 行为）
        mockMvc.perform(multipart("/admin/upload").file(jpegFile()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value(ADMIN_TOKEN_INVALID_MESSAGE));
    }

    /** RBAC：只读角色执行写操作 → 403（门控在服务端强制，与前端隐显入口无关） */
    @Test
    void adminWriteWithViewerRole_returns403() throws Exception {
        AdminAccount viewer = new AdminAccount();
        viewer.setId(1L);
        viewer.setStatus("on");
        viewer.setCredentialVersion(1);
        viewer.setRole("viewer");
        org.mockito.Mockito.lenient().when(adminAccountService.findById(1L)).thenReturn(viewer);

        mockMvc.perform(multipart("/admin/upload").file(jpegFile())
                        .header(ADMIN_AUTH_HEADER, adminBearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void uploadImage_invalidAdminToken_returns401() throws Exception {
        mockMvc.perform(multipart("/admin/upload").file(jpegFile())
                        .header(ADMIN_AUTH_HEADER, "Bearer not-a-valid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value(ADMIN_TOKEN_INVALID_MESSAGE));
    }

    @Test
    void uploadImage_withAdminToken_returnsUrl() throws Exception {
        when(uploadService.uploadImage(any()))
                .thenReturn(new UploadResultVO("http://localhost:8080/api/images/2026/05/a.jpg",
                        "/images/2026/05/a.jpg"));

        mockMvc.perform(multipart("/admin/upload").file(jpegFile())
                        .header(ADMIN_AUTH_HEADER, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.url").value("http://localhost:8080/api/images/2026/05/a.jpg"));
    }

    /**
     * 防回归：上传端点命名去混淆后，学生端 {@code /upload/cloud-image} 与管理端
     * {@code /admin/upload} 应在册（口径见 docs/api/web/upload.md「管理端素材上传」），
     * 旧路径 {@code /upload/images}、{@code /upload/image}、{@code /admin/upload/image} 不得残留。
     */
    @Test
    void uploadEndpoints_renamedPathsRegistered_legacyPathsRemoved() {
        Set<String> patterns = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .collect(Collectors.toSet());

        Assertions.assertTrue(patterns.contains("/upload/cloud-image"),
                "学生端上传路径 /upload/cloud-image 应在册；实际映射：" + patterns);
        Assertions.assertTrue(patterns.contains("/admin/upload"),
                "管理端上传路径 /admin/upload 应在册；实际映射：" + patterns);
        Assertions.assertFalse(patterns.contains("/admin/upload/image"),
                "旧管理端上传路径 /admin/upload/image 应删除；实际映射：" + patterns);
        Assertions.assertFalse(patterns.contains("/upload/images"),
                "旧学生端路径 /upload/images 应删除；实际映射：" + patterns);
        Assertions.assertFalse(patterns.contains("/upload/image"),
                "旧管理端路径 /upload/image 应删除；实际映射：" + patterns);
    }

    // ==================== 链路 6：管理端（反馈 / 举报 / 处置） ====================

    @Test
    void adminFeedbackList_withoutToken_returns401() throws Exception {
        // /admin/** 由 AdminAuthFilter 把关（fail-closed），未带凭证一律 401
        mockMvc.perform(get("/admin/feedbacks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value(ADMIN_TOKEN_INVALID_MESSAGE));
    }

    @Test
    void adminFeedbackList_withToken_returnsPageContract() throws Exception {
        // 空页桩：管理端列表仅验证分页契约字段，不查库
        IPage<Feedback> emptyPage = new Page<>(1, 10);
        when(feedbackMapper.selectPage(any(), any())).thenReturn(emptyPage);

        mockMvc.perform(get("/admin/feedbacks").header(ADMIN_AUTH_HEADER, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                // 管理端分页契约（AdminPageResult，见 docs/api/README.md）：
                // records + total（「共 N 条」与总页数 = ceil(total / pageSize)）；
                // page / pageSize 仍由请求侧掌握、不回传。
                // ⚠️ 学生端 PageResult 仍只有 records —— 两者不可混用。
                .andExpect(jsonPath("$.data.total").exists())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.page").doesNotExist())
                .andExpect(jsonPath("$.data.pageSize").doesNotExist());
    }

    @Test
    void adminReportList_withToken_returnsPageContract() throws Exception {
        IPage<Report> emptyPage = new Page<>(1, 10);
        when(reportMapper.selectPage(any(), any())).thenReturn(emptyPage);

        mockMvc.perform(get("/admin/reports").header(ADMIN_AUTH_HEADER, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    /** 处置：结论 rejected 但未填处置文案 → 400（不采纳必须写清原因，校验在处置任务服务内） */
    @Test
    void adminFeedbackHandle_rejectedWithoutContent_returns400() throws Exception {
        Feedback feedback = new Feedback();
        feedback.setId(5L);
        feedback.setUserId(9L);
        when(feedbackMapper.selectById(5L)).thenReturn(feedback);

        mockMvc.perform(put("/admin/feedbacks/5")
                        .header(ADMIN_AUTH_HEADER, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"rejected\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请填写不采纳原因"));

        verify(moderationTaskMapper, never()).updateById(any());
    }

    /** 处置：正常受理 → 任务行归档 handled + 站内回执（同一事务的快照两列同名） */
    @Test
    void adminFeedbackHandle_handled_archivesAndNotifies() throws Exception {
        Feedback feedback = new Feedback();
        feedback.setId(5L);
        feedback.setUserId(9L);
        when(feedbackMapper.selectById(5L)).thenReturn(feedback);
        ModerationTask task = new ModerationTask();
        task.setId(7L);
        task.setType(ModerationConst.TYPE_FEEDBACK);
        task.setTypeId(5L);
        task.setStatus(ModerationConst.STATUS_PENDING);
        when(moderationTaskMapper.selectOne(any())).thenReturn(task);

        mockMvc.perform(put("/admin/feedbacks/5")
                        .header(ADMIN_AUTH_HEADER, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"replyContent\":\"已收到，下个版本优化\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<ModerationTask> saved = ArgumentCaptor.forClass(ModerationTask.class);
        verify(moderationTaskMapper).updateById(saved.capture());
        Assertions.assertEquals(ModerationConst.STATUS_HANDLED, saved.getValue().getStatus());
        Assertions.assertEquals("反馈已处理", saved.getValue().getReplyTitle());
        Assertions.assertEquals("已收到，下个版本优化", saved.getValue().getReplyContent());
        // 处置人 = 管理端 JWT 内账号 ID（不信任请求体）
        Assertions.assertEquals(1L, saved.getValue().getHandledBy());

        // 站内回执：与任务行同一份文案快照
        ArgumentCaptor<com.bjtufood.notification.dto.NotificationCmd> cmd =
                ArgumentCaptor.forClass(com.bjtufood.notification.dto.NotificationCmd.class);
        verify(notificationService).notifyNow(cmd.capture());
        Assertions.assertEquals(9L, cmd.getValue().getUserId());
        Assertions.assertEquals("反馈已处理", cmd.getValue().getReplyTitle());
    }

    /** 处置：记录不存在 → 4001 */
    @Test
    void adminFeedbackHandle_missing_returns4001() throws Exception {
        when(feedbackMapper.selectById(anyLong())).thenReturn(null);

        mockMvc.perform(put("/admin/feedbacks/404")
                        .header(ADMIN_AUTH_HEADER, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"replyContent\":\"已处理\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4001));
    }

    /** 处置：举报受理 + 联动隐藏 → 回执写明「已隐藏该评价」 */
    @Test
    void adminReportHandle_handledWithHide_notifiesWithExplanation() throws Exception {
        Report report = new Report();
        report.setId(4L);
        report.setUserId(9L);
        report.setReviewId(30L);
        when(reportMapper.selectById(4L)).thenReturn(report);
        ModerationTask task = new ModerationTask();
        task.setId(7L);
        task.setType(ModerationConst.TYPE_REPORT);
        task.setTypeId(4L);
        task.setStatus(ModerationConst.STATUS_PENDING);
        when(moderationTaskMapper.selectOne(any())).thenReturn(task);
        when(reviewService.hideIfVisible(30L)).thenReturn(true);

        mockMvc.perform(put("/admin/reports/4")
                        .header(ADMIN_AUTH_HEADER, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"handled\",\"hideReview\":true,\"replyContent\":\"举报属实\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(reviewService).hideIfVisible(30L);
        ArgumentCaptor<ModerationTask> saved = ArgumentCaptor.forClass(ModerationTask.class);
        verify(moderationTaskMapper).updateById(saved.capture());
        Assertions.assertEquals("举报已受理", saved.getValue().getReplyTitle());
        Assertions.assertEquals("举报属实（已隐藏该评价）", saved.getValue().getReplyContent());
    }

    /**
     * 防回归：{@code GET /admin/categories} 无任何处理器 —— 品类全链
     * （Controller / Service / Mapper / Entity + dish.categoryId + category 表）整体退役后，该路径必须失效。
     * <p>
     * <b>状态码口径（实测校准，非 404）</b>：Spring 6.1 起未匹配到任何 {@code @RequestMapping}
     * 的路径（含 /admin/categories）会落到静态资源处理器并抛 {@code NoResourceFoundException}，
     * 由 {@link GlobalExceptionHandler#handleNoResourceFoundException} 统一转为
     * <b>HTTP 400 + body.code=400</b>（message「资源不存在」）。故本用例断言「400 + code=400 + 资源不存在」，
     * 且非 401「管理端登录已失效」—— 正好证明请求已通过 AdminAuthFilter token 校验、
     * 失败原因是「路径无处理器」（端点确已删除），回归时会直接失败。
     */
    @Test
    void adminCategories_removed_endpointGone_returns400() throws Exception {
        mockMvc.perform(get("/admin/categories").header(ADMIN_AUTH_HEADER, adminBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    /**
     * 防回归：
     * {@code PUT /admin/reviews/{id}/sec-state} 必须不再注册（人工复核队列已无存续价值）。
     * <p>
     * 断言手法：直接查切片内 {@link RequestMappingHandlerMapping} 的注册映射（不经请求，
     * 规避「PUT 打到 /** 静态资源处理器」的状态码不确定性）。并以保留的
     * {@code PUT /admin/reviews/{id}/hidden}（举报→下架的事后处置通道）作<b>阳性对照</b>。
     */
    @Test
    void adminReviewSecState_removedEndpointGone() {
        Set<String> patterns = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .collect(Collectors.toSet());

        Assertions.assertTrue(patterns.contains("/admin/reviews/{id}/hidden"),
                "保留端点 /admin/reviews/{id}/hidden 应在册；实际映射：" + patterns);
        Assertions.assertFalse(patterns.stream().anyMatch(p -> p.contains("sec-state")),
                "sec-state 端点应已随列退役删除；实际映射：" + patterns);
    }

    /**
     * 防回归：举报管理端端点与字典端点必须真实在册（web 侧照契约调用，路径改动即 404）。
     * <p>确定性断言在映射表上做：处置端点方法必须为 PUT（只比路径会让 405 漏网）。
     */
    @Test
    void adminReportAndDictionaryEndpoints_registered() {
        Set<String> patterns = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .collect(Collectors.toSet());

        Assertions.assertTrue(patterns.contains("/admin/reports"),
                "举报列表 /admin/reports 应在册；实际映射：" + patterns);
        Assertions.assertTrue(patterns.contains("/admin/reports/{id}"),
                "举报处置 /admin/reports/{id} 应在册；实际映射：" + patterns);
        Assertions.assertTrue(patterns.contains("/feedback-options"),
                "反馈类型字典 /feedback-options 应在册；实际映射：" + patterns);
        Assertions.assertTrue(patterns.contains("/report-options"),
                "举报原因字典 /report-options 应在册；实际映射：" + patterns);
        Assertions.assertTrue(patterns.contains("/admin/feedback-options"),
                "反馈类型管理 /admin/feedback-options 应在册；实际映射：" + patterns);
        Assertions.assertTrue(patterns.contains("/admin/report-options"),
                "举报原因管理 /admin/report-options 应在册；实际映射：" + patterns);
    }

    // ==================== 管理端鉴权（TD-19 / TD-23 · P0-4）====================

    /**
     * 🔴 <b>登录端点白名单回归</b>：`POST /admin/auth/login` 必须能穿过 `AdminAuthFilter`。
     *
     * <p>断言手法：比对响应 `message`。若被过滤器拦下，文案是「管理端登录已失效，请重新登录」；
     * 只有真正走到 Controller，才会是登录失败的统一文案「账号或密码错误」。
     */
    @Test
    void adminLogin_isWhitelistedFromAdminAuthFilter() throws Exception {
        when(adminAccountService.findByUsername("ghost")).thenReturn(null);

        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ghost\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
    }

    /** 登录成功：200 + `token`/`username`/`expiresIn`（契约见 docs/api/web/auth.md） */
    @Test
    void adminLogin_successIssuesAdminJwt() throws Exception {
        AdminAccount account = new AdminAccount();
        account.setId(7L);
        account.setUsername("kingdo404");
        account.setPasswordHash(passwordEncoder.encode("S3cret-passw0rd"));
        account.setStatus("on");
        account.setCredentialVersion(1);
        when(adminAccountService.findByUsername("kingdo404")).thenReturn(account);

        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"kingdo404\",\"password\":\"S3cret-passw0rd\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value("kingdo404"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    /** 改密后旧 token 立即失效（凭证版本比对），既有 401 处置不变 */
    @Test
    void adminTokenWithStaleCredentialVersion_isUnauthorized() throws Exception {
        AdminAccount account = new AdminAccount();
        account.setId(7L);
        account.setUsername("kingdo404");
        account.setStatus("on");
        // 库中版本已自增（如改密后），token 内仍是旧版本
        account.setCredentialVersion(2);
        when(adminAccountService.findById(7L)).thenReturn(account);

        mockMvc.perform(get("/admin/auth/me")
                        .header(ADMIN_AUTH_HEADER, "Bearer " + adminJwtUtil.createToken(7L, "kingdo404", 1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    /**
     * `GET /admin/auth/me` 契约：只回 `username` + `lastLoginAt`，**不回 token / expiresIn**。
     */
    @Test
    void adminMe_returnsUsernameAndLastLoginAtPerContract() throws Exception {
        AdminAccount account = new AdminAccount();
        account.setId(7L);
        account.setUsername("kingdo404");
        account.setStatus("on");
        account.setLastLoginAt(LocalDateTime.of(2026, 10, 5, 9, 30, 15));
        account.setCredentialVersion(1);
        when(adminAccountService.findById(7L)).thenReturn(account);

        mockMvc.perform(get("/admin/auth/me")
                        .header("Authorization", "Bearer " + adminJwtUtil.createToken(7L, "kingdo404", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("kingdo404"))
                .andExpect(jsonPath("$.data.lastLoginAt").value("2026-10-05 09:30:15"))
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.expiresIn").doesNotExist());
    }

    // ==================== 辅助方法 ====================

    /** 用真实 JwtUtil 签发学生态 token（JWT 仅含 userId/wxId，认证态不入 token） */
    private String studentToken() {
        return "Bearer " + jwtUtil.createToken(USER_ID, "smoke");
    }

    /**
     * 构造按认证态区分的用户桩：认证态唯一判据 = email 非空（见 AuthStateUtil），
     * 已认证注入校园邮箱、游客态留 NULL。
     */
    private User user(boolean verified) {
        User user = new User();
        user.setId(USER_ID);
        user.setStatus("active");
        user.setEmail(verified ? "20240001@bjtu.edu.cn" : null);
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
