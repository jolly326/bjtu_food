package com.bjtufood.feedback.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.dto.UserAuthContextVO;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.feedback.dto.FeedbackAdminVO;
import com.bjtufood.feedback.dto.FeedbackReq;
import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.feedback.mapper.FeedbackMapper;
import com.bjtufood.feedback.mapper.ReportMapper;
import com.bjtufood.feedback.service.FeedbackOptionService;
import com.bjtufood.moderation.constant.ModerationConst;
import com.bjtufood.moderation.dto.ModerationHandleReq;
import com.bjtufood.moderation.dto.ModerationTaskVO;
import com.bjtufood.moderation.service.ContentSecurityService;
import com.bjtufood.moderation.service.LocalSensitiveFilter;
import com.bjtufood.moderation.service.ModerationTaskService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FeedbackServiceImpl} 单元测试（**意见反馈**链路；举报链路见
 * {@link ReportServiceImplTest}，字典规则见 {@link ReportOptionServiceImplTest} /
 * {@link FeedbackOptionServiceImplTest}）。
 * <p>
 * 聚焦五类最易被改坏、且退化后即为安全 / 数据问题的逻辑：
 * <ol>
 *   <li><b>提交人恒为登录用户</b>：userId 为空 → 401（不落匿名行）；</li>
 *   <li><b>类型值域由字典下发</b>：{@code optionId} 未命中启用项 → 400 且不落库
 *       （字典是唯一真源，代码里不得再出现类型枚举）；</li>
 *   <li><b>落库两行同事务</b>：反馈行 + 处置任务行（{@code status=pending}）——
 *       少写任务行会让该反馈从管理端「待处置」列表里消失；</li>
 *   <li><b>处置委派</b>：状态机与文案校验归处置任务服务，本域只负责「记录存在 + 归属下发」；</li>
 *   <li><b>归属迁移覆盖两张表</b>：`feedback` 与 `report` 一起改挂（一次迁移一个事务）。</li>
 * </ol>
 * 被测类为纯 POJO：{@code @Transactional} 依赖 Spring 代理，单测中不生效，断言的是方法体内业务逻辑。
 */
class FeedbackServiceImplTest {

    /** 纯 Mockito 无 Spring 上下文，MyBatis-Plus 的 lambda 缓存需显式初始化 */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), FeedbackServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, Feedback.class);
    }

    private final FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);
    private final ReportMapper reportMapper = mock(ReportMapper.class);
    private final UserService userService = mock(UserService.class);
    private final LocalSensitiveFilter localSensitiveFilter = mock(LocalSensitiveFilter.class);
    private final ContentSecurityService contentSecurityService = mock(ContentSecurityService.class);
    private final ImageUrlUtil imageUrlUtil = mock(ImageUrlUtil.class);
    private final FeedbackOptionService feedbackOptionService = mock(FeedbackOptionService.class);
    private final ModerationTaskService moderationTaskService = mock(ModerationTaskService.class);

    /**
     * 构造器参数顺序须与 {@code FeedbackServiceImpl} 的 final 字段声明顺序逐字一致（@RequiredArgsConstructor）。
     * <p>
     * 落库 Bean 用**真实实现**包裹 mock 的 mapper / 任务服务：事务边界收窄（机审移出事务）后，
     * 本类断言仍原样落在 {@code feedbackMapper.insert} 与 {@code moderationTaskService.create} 上
     * —— 这正是「落库两行同一事务」的直接证据。
     */
    private FeedbackServiceImpl service() {
        return new FeedbackServiceImpl(feedbackMapper, new FeedbackPersister(feedbackMapper, moderationTaskService),
                userService, localSensitiveFilter, contentSecurityService, imageUrlUtil,
                feedbackOptionService, moderationTaskService, reportMapper);
    }

    /** 本地词库默认原样返回（不脱敏），便于断言落库值 */
    private void stubFilterPassThrough() {
        when(localSensitiveFilter.filter(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** 反馈类型命中启用项（字典规则由字典服务单测覆盖，本类只关心「未命中即 400」） */
    private void stubOptionSubmittable() {
        when(feedbackOptionService.isSubmittable(anyLong())).thenReturn(true);
    }

    /** 落库后回填主键（模拟 DB 自增），使「预建任务行带上工单 ID」可断言 */
    private void stubInsertAssignsId(Long id) {
        when(feedbackMapper.insert(any())).thenAnswer(inv -> {
            ((Feedback) inv.getArgument(0)).setId(id);
            return 1;
        });
    }

    private static FeedbackReq feedbackReq(Long optionId, String content) {
        FeedbackReq req = new FeedbackReq();
        req.setOptionId(optionId);
        req.setContent(content);
        return req;
    }

    // ==================== submit：归属 / 字典白名单 / 内容 ====================

    @Test
    @DisplayName("submit：合法提交 → 落库归属与类型（optionId）取自请求与登录态，并预建 pending 任务行")
    void submit_writableOption_persistsAndPreCreatesTask() {
        stubFilterPassThrough();
        stubOptionSubmittable();
        stubInsertAssignsId(11L);

        service().submit(7L, feedbackReq(2L, "首页偶发白屏"));

        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackMapper).insert(captor.capture());
        Feedback saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getOptionId()).isEqualTo(2L);
        assertThat(saved.getContent()).isEqualTo("首页偶发白屏");
        assertThat(saved.getImages()).isNull();

        // 同一事务内预建处置任务行：「未处置」是显式行，管理端列表据此走索引
        verify(moderationTaskService).create(ModerationConst.TYPE_FEEDBACK, 11L);
    }

    @Test
    @DisplayName("submit：类型未命中启用字典（不存在 / 已停用）→ 400 且不落库")
    void submit_optionNotSubmittable_400() {
        stubFilterPassThrough();
        // 不加桩 ⇒ isSubmittable 恒 false，等价于「字典项不存在或已停用」

        assertThatThrownBy(() -> service().submit(7L, feedbackReq(999L, "内容")))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(feedbackMapper, never()).insert(any());
        verify(moderationTaskService, never()).create(any(), any());
    }

    @Test
    @DisplayName("submit：内容为空 / 纯空白 → 400 且不落库")
    void submit_blankContent_400() {
        stubFilterPassThrough();
        stubOptionSubmittable();

        assertThatThrownBy(() -> service().submit(7L, feedbackReq(1L, "   ")))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(feedbackMapper, never()).insert(any());
    }

    @Test
    @DisplayName("submit：无登录态（userId=null）→ 401「请先登录」（feedback.user_id 必填真实登录用户），不落库")
    void submit_guest_rejected401() {
        stubFilterPassThrough();

        assertThatThrownBy(() -> service().submit(null, feedbackReq(1L, "匿名反馈")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(401));

        verify(feedbackMapper, never()).insert(any());
    }

    @Test
    @DisplayName("submit：配图非 COS 受信任地址 → 400，且不发起微信机审（不为必然失败的请求白付额度）")
    void submit_illegalImage_400WithoutSecCheck() {
        stubFilterPassThrough();
        stubOptionSubmittable();
        when(imageUrlUtil.isValidCosUgcUrl(any())).thenReturn(false);
        FeedbackReq req = feedbackReq(1L, "内容");
        req.setImages(List.of("http://evil.com/a.jpg"));

        assertThatThrownBy(() -> service().submit(7L, req))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(contentSecurityService, never()).checkText(any(), any(), anyInt());
        verify(feedbackMapper, never()).insert(any());
    }

    @Test
    @DisplayName("submit：机审取 openid 走 msgSecCheck（scene=2）")
    void submit_withOpenid_callsSecCheckWithScene2() {
        stubFilterPassThrough();
        stubOptionSubmittable();
        UserAuthContextVO user = new UserAuthContextVO();
        user.setOpenid("oXxx");
        when(userService.getAuthContext(7L)).thenReturn(user);

        service().submit(7L, feedbackReq(1L, "希望增加素食档口"));

        verify(contentSecurityService).checkText("oXxx", "希望增加素食档口", 2);
    }

    // ==================== handle：委派处置任务服务 ====================

    @Test
    @DisplayName("handle：反馈不存在 → 4001，且不触达处置任务服务")
    void handle_notFound_4001() {
        when(feedbackMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> service().handle(5L, new ModerationHandleReq(), 1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
        verify(moderationTaskService, never()).handle(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handle：委派处置任务服务，收件人取记录归属；结论 / 文案校验不在本域重复实现")
    void handle_delegatesToModerationTaskWithOwner() {
        Feedback feedback = new Feedback();
        feedback.setId(5L);
        feedback.setUserId(9L);
        when(feedbackMapper.selectById(5L)).thenReturn(feedback);
        ModerationHandleReq req = new ModerationHandleReq();
        req.setOutcome(ModerationConst.OUTCOME_REJECTED);
        req.setReplyContent("该问题已在下个版本修复");

        service().handle(5L, req, 3L);

        verify(moderationTaskService).handle(ModerationConst.TYPE_FEEDBACK, 5L,
                ModerationConst.OUTCOME_REJECTED, null, "该问题已在下个版本修复", 3L, 9L);
    }

    // ==================== 管理端列表：批量回填 ====================

    @Test
    @DisplayName("listForAdmin：一次 IN 批量补齐昵称 / 类型中文名 / 处置结果（含处理人）")
    void listForAdmin_fillsNamesAndTaskResult() {
        Feedback f = new Feedback();
        f.setId(1L);
        f.setUserId(7L);
        f.setOptionId(2L);
        f.setContent("希望增加素食档口");
        Page<Feedback> page = new Page<>(1, 10);
        page.setRecords(List.of(f));
        page.setTotal(1);
        when(feedbackMapper.selectPage(any(), any())).thenReturn(page);
        when(userService.mapNameByIds(any())).thenReturn(Map.of(7L, "交大干饭王"));
        when(feedbackOptionService.nameByIdAll()).thenReturn(Map.of(2L, "产品功能建议"));

        ModerationTaskVO task = new ModerationTaskVO();
        task.setStatus(ModerationConst.STATUS_HANDLED);
        task.setOutcome(ModerationConst.OUTCOME_REJECTED);
        task.setReplyTitle("反馈未采纳");
        task.setReplyContent("该问题已在下个版本修复");
        task.setHandledByName("kingdo404");
        when(moderationTaskService.mapByTypeAndIds(eq(ModerationConst.TYPE_FEEDBACK), any()))
                .thenReturn(Map.of(1L, task));

        List<FeedbackAdminVO> records = service().listForAdmin(null, null, null, null, 1, 10).getRecords();

        assertThat(records).hasSize(1);
        FeedbackAdminVO vo = records.get(0);
        assertThat(vo.getUserName()).isEqualTo("交大干饭王");
        assertThat(vo.getOptionName()).isEqualTo("产品功能建议");
        assertThat(vo.getImages()).isEmpty();
        assertThat(vo.getStatus()).isEqualTo(ModerationConst.STATUS_HANDLED);
        assertThat(vo.getOutcome()).isEqualTo(ModerationConst.OUTCOME_REJECTED);
        assertThat(vo.getReplyTitle()).isEqualTo("反馈未采纳");
        assertThat(vo.getReplyContent()).isEqualTo("该问题已在下个版本修复");
        assertThat(vo.getHandledByName()).isEqualTo("kingdo404");
    }

    @Test
    @DisplayName("listForAdmin：处置状态非法 → 400（不静默当「全部」处理）")
    void listForAdmin_illegalStatus_400() {
        assertThatThrownBy(() -> service().listForAdmin("adopted", null, null, null, 1, 10))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(feedbackMapper, never()).selectPage(any(), any());
    }

    // ==================== 账号归属迁移：两张表 ====================

    @Test
    @DisplayName("migrateOwnership：空参 / 同一用户 → 返回 0 且不发起更新")
    void migrateOwnership_noopCases() {
        assertThat(service().migrateOwnership(null, 2L)).isZero();
        assertThat(service().migrateOwnership(1L, null)).isZero();
        assertThat(service().migrateOwnership(1L, 1L)).isZero();
        verify(feedbackMapper, never()).update(any(), any());
        verify(reportMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("migrateOwnership：反馈表与举报表一起改挂，返回合计条数")
    void migrateOwnership_coversBothTables() {
        when(feedbackMapper.update(any(), any())).thenReturn(2);
        when(reportMapper.update(any(), any())).thenReturn(1);

        assertThat(service().migrateOwnership(1L, 2L)).isEqualTo(3);

        verify(feedbackMapper).update(any(), any());
        verify(reportMapper).update(any(), any());
    }
}
