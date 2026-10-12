package com.bjtufood.review.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.dto.UserAuthContextVO;
import com.bjtufood.auth.dto.UserBriefVO;
import com.bjtufood.auth.service.AdminAccountService;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.moderation.service.ContentSecurityService;
import com.bjtufood.moderation.service.LocalSensitiveFilter;
import com.bjtufood.notification.service.NotificationService;
import com.bjtufood.review.dto.ReviewAdminVO;
import com.bjtufood.review.entity.Review;
import com.bjtufood.review.event.ReviewSubmittedEvent;
import com.bjtufood.review.mapper.ReviewMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReviewServiceImpl} 单元测试。
 * <p>
 * 聚焦三处最易被改坏、且一旦退化即为安全问题的逻辑：
 * <ol>
 *   <li><b>作者归属校验</b>（{@code updateReview}/{@code deleteReview}）——若退化为「查到就改/删」，
 *       即成为横向越权，可任意篡改他人评价；</li>
 *   <li><b>UGC 准入</b>——已认证(4031) 与 有 openid(403) 是两个不同语义的码，混淆会让端上把
 *       「需先认证」误导向「无权限」；</li>
 *   <li><b>归属迁移的冲突清理顺序</b>（{@code migrateOwnership}）——唯一键
 *       {@code uk_review_user_dish} 决定了必须<b>先删冲突行再改归属</b>，顺序颠倒会触发唯一键冲突。</li>
 * </ol>
 * 被测类为纯 POJO：{@code @Transactional} 依赖 Spring 代理，单测中不生效，断言的是方法体内业务逻辑。
 */
class ReviewServiceImplTest {

    /**
     * 初始化 MyBatis-Plus lambda 缓存（{@code TableInfo}）——纯 Mockito 单测无 Spring 上下文，
     * 该缓存正常由 Mapper 扫描填充，缺它构造 {@code LambdaQueryWrapper} 会抛
     * {@code "MybatisPlus can not find lambda cache for this entity"}。
     * 显式初始化是为让 wrapper 构造被真实执行，而非绕开被测逻辑。
     */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), ReviewServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, Review.class);
    }

    private final ReviewMapper reviewMapper = mock(ReviewMapper.class);
    private final UserService userService = mock(UserService.class);
    private final DishService dishService = mock(DishService.class);
    private final AdminAccountService adminAccountService = mock(AdminAccountService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ImageUrlUtil imageUrlUtil = mock(ImageUrlUtil.class);
    private final LocalSensitiveFilter localSensitiveFilter = mock(LocalSensitiveFilter.class);
    private final ContentSecurityService contentSecurityService = mock(ContentSecurityService.class);
    private final NotificationService notificationService = mock(NotificationService.class);

    /** 构造器参数顺序须与 {@code ReviewServiceImpl} 的 final 字段声明顺序逐字一致（@RequiredArgsConstructor） */
    private ReviewServiceImpl service() {
        // 落库 Bean 用**真实实现**包裹 mock 的 mapper 与事件发布器：事务边界收窄（机审移出事务）后，
        // 本类断言仍原样落在 reviewMapper.insert / update 与 eventPublisher 上 —— 即「可见行为未变」的直接证据。
        return new ReviewServiceImpl(reviewMapper, new ReviewPersister(reviewMapper, eventPublisher),
                userService, dishService, adminAccountService, eventPublisher, imageUrlUtil, localSensitiveFilter,
                contentSecurityService, notificationService);
    }

    private static Review review(Long id, Long userId, Long dishId, int rating) {
        Review r = new Review();
        r.setId(id);
        r.setUserId(userId);
        r.setDishId(dishId);
        r.setRating(rating);
        return r;
    }

    /** 已认证且有 openid 的用户（两项准入均通过） */
    private void stubVerifiedUserWithOpenid(Long userId) {
        when(userService.getAuthContext(userId))
                .thenReturn(new UserAuthContextVO(userId, true, "oX-openid"));
    }

    // ==================== 星级筛选（TD-27）====================

    @Test
    @DisplayName("星级筛选：rating 非空时透传给 Mapper；null = 不过滤")
    void listByDishIdPassesRatingFilterThrough() {
        when(reviewMapper.selectReviewPageByDishId(any(), anyLong(), any())).thenReturn(new Page<>());

        service().listByDishId(7L, 1, 10, 5);
        verify(reviewMapper).selectReviewPageByDishId(any(), anyLong(), eq(5));

        // 不筛选：rating 传 null（端上「全部」不传该参数 ⇒ 服务端按 null 处理）
        service().listByDishId(7L, 1, 10, null);
        verify(reviewMapper).selectReviewPageByDishId(any(), anyLong(), isNull());
    }

    @Test
    @DisplayName("星级筛选：非法值（0 / 6）→ 400，绝不静默降级为不过滤")
    void listByDishIdRejectsIllegalRating() {
        // 静默降级会让端上传错值却被当「全部」返回，属契约违背
        assertThatThrownBy(() -> service().listByDishId(7L, 1, 10, 0))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service().listByDishId(7L, 1, 10, 6))
                .isInstanceOf(BusinessException.class);

        verify(reviewMapper, never()).selectReviewPageByDishId(any(), anyLong(), any());
    }

    // ==================== deleteReview：作者归属 ====================

    @Test
    @DisplayName("删除他人评价 → 403，且绝不删库、绝不发事件")
    void deletingOthersReviewIsForbiddenAndNoSideEffect() {
        when(reviewMapper.selectById(8L)).thenReturn(review(8L, 2L, 1L, 5));

        assertThatThrownBy(() -> service().deleteReview(8L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(403);
                    assertThat(be.getMessage()).isEqualTo("只能删除自己的评价");
                });

        // 安全关键：越权请求不得有任何副作用
        verify(reviewMapper, never()).deleteById(anyLong());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("删除不存在的评价 → 4001「评价不存在」")
    void deletingMissingReviewIsNotFound() {
        when(reviewMapper.selectById(8L)).thenReturn(null);

        // 4001 = 资源不存在（端上据此给恢复路径，不解析 message 文本）
        assertThatThrownBy(() -> service().deleteReview(8L, 1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
        verify(reviewMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除本人评价 → 删库并发布 ReviewSubmittedEvent 以重算评分")
    void deletingOwnReviewRemovesAndPublishesEvent() {
        when(reviewMapper.selectById(8L)).thenReturn(review(8L, 1L, 7L, 4));

        service().deleteReview(8L, 1L);

        verify(reviewMapper).deleteById(8L);
        ArgumentCaptor<org.springframework.context.ApplicationEvent> evt =
                ArgumentCaptor.forClass(org.springframework.context.ApplicationEvent.class);
        verify(eventPublisher).publishEvent(evt.capture());
        assertThat(evt.getValue()).isInstanceOf(ReviewSubmittedEvent.class);
    }

    // ==================== 归属迁移：冲突清理顺序 ====================

    @Test
    @DisplayName("migrateOwnership：同账号或任一为 null → 0，且不触碰数据库")
    void migrateOwnershipShortCircuitsOnNoopInput() {
        ReviewServiceImpl svc = service();

        assertThat(svc.migrateOwnership(null, 2L)).isZero();
        assertThat(svc.migrateOwnership(1L, null)).isZero();
        assertThat(svc.migrateOwnership(1L, 1L)).isZero();

        verify(reviewMapper, never()).delete(any());
        verify(reviewMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("migrateOwnership：先删冲突行再改归属（唯一键 uk_review_user_dish 要求此顺序），且不发事件")
    void migrateOwnershipDeletesConflictsBeforeReassigning() {
        when(reviewMapper.delete(any())).thenReturn(2);
        when(reviewMapper.update(any(), any())).thenReturn(3);

        int affected = service().migrateOwnership(1L, 2L);

        assertThat(affected).isEqualTo(3);
        // 顺序验证：delete 先于 update（InOrder 语义由 verify 先后次序保证）
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(reviewMapper);
        inOrder.verify(reviewMapper).delete(any());
        inOrder.verify(reviewMapper).update(any(), any());
        // 迁移不改评分，故不重算聚合
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ==================== 管理端列表：作者头像联表带出 ====================

    @Test
    @DisplayName("listAllForAdmin：头像经 auth 域契约带出 —— 有头像 → 绝对 URL；无头像 / 用户不在图 → null")
    void listAllForAdminBringsAuthorAvatarThrough() {
        Page<Review> page = new Page<>(1, 10);
        page.setRecords(List.of(
                review(1L, 1L, 10L, 5),
                review(2L, 2L, 10L, 4),
                review(3L, 3L, 10L, 3)));
        doReturn(page).when(reviewMapper).selectPage(any(), any());
        when(userService.mapBriefByIds(any())).thenReturn(Map.of(
                1L, new UserBriefVO(1L, "小明", "http://host/api/v1/images/a.jpg"),
                // 用户 2 存在但无头像（游客 / 未设置 / 已注销）：VO 头像必须为 null，走前端占位
                2L, new UserBriefVO(2L, "小红", null)));
        when(dishService.mapNameByIds(any())).thenReturn(Map.of(10L, "番茄炒蛋"));

        List<ReviewAdminVO> vos = service().listAllForAdmin(1, 10, null, null, null, null).getRecords();

        assertThat(vos).hasSize(3);
        assertThat(vos.get(0).getUserAvatar()).isEqualTo("http://host/api/v1/images/a.jpg");
        assertThat(vos.get(0).getUserName()).isEqualTo("小明");
        assertThat(vos.get(1).getUserAvatar()).isNull();
        // 用户不在契约返回图中（用户已注销被剔除等）：头像同样为 null，不得抛 NPE
        assertThat(vos.get(2).getUserAvatar()).isNull();
        assertThat(vos.get(2).getUserName()).isNull();
    }

    // ==================== 处置留痕：写入（一次 UPDATE 写 4 列 + DB 时钟） ====================

    @Test
    @DisplayName("setHidden：一次 UPDATE 同批写 is_hidden / hidden_note / moderated_by / moderated_at，"
            + "处置时间必须走 DB 时钟 NOW()")
    void setHiddenWritesVisibilityAndTraceInOneUpdate() {
        when(reviewMapper.selectById(8L)).thenReturn(review(8L, 3L, 7L, 4));
        AtomicReference<Wrapper<Review>> captured = new AtomicReference<>();
        when(reviewMapper.update(isNull(), any())).thenAnswer(inv -> {
            captured.set(inv.getArgument(1));
            return 1;
        });

        service().setHidden(8L, true, "  含违规配图  ", 42L);

        assertThat(captured.get())
                .as("必须走列级 UPDATE（update(entity=null, LambdaUpdateWrapper)）："
                        + "整行回写会连带写回内容列，与用户覆盖提交并发时互相覆盖")
                .isInstanceOf(LambdaUpdateWrapper.class);
        AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) captured.get();
        assertThat(wrapper.getSqlSet())
                .as("四列必须同批写入（可见性 + 附注 + 处置人 + 处置时间），否则「最近一次处置」会残缺")
                .contains("is_hidden=")
                .contains("hidden_note=")
                .contains("moderated_by=")
                .contains("moderated_at = NOW()");
        assertThat(wrapper.getParamNameValuePairs().values())
                .as("处置人取当前管理员；附注须 trim 后落库")
                .contains(42L, "含违规配图");
        // 处置时间的唯一来源是 DB 时钟：不能用 LocalDateTime.now()（重评覆盖本行会误刷新它）
        assertThat(wrapper.getParamNameValuePairs().values())
                .as("处置时间不得作为应用层参数落库")
                .noneMatch(v -> v instanceof java.time.LocalDateTime);
        // 可见性翻转后仍须重算评分（隐藏的评价不计入均分）
        verify(eventPublisher).publishEvent(any(ReviewSubmittedEvent.class));
    }

    @Test
    @DisplayName("setHidden：恢复显示同样写处置人 + 处置时间，且附注槽显式写 NULL（不清空会串到下一条回执）")
    void setHiddenOnUnhideClearsNoteAndStillRecordsTrace() {
        when(reviewMapper.selectById(8L)).thenReturn(review(8L, 3L, 7L, 4));
        AtomicReference<Wrapper<Review>> captured = new AtomicReference<>();
        when(reviewMapper.update(isNull(), any())).thenAnswer(inv -> {
            captured.set(inv.getArgument(1));
            return 1;
        });

        service().setHidden(8L, false, "旧附注", 42L);

        AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) captured.get();
        assertThat(wrapper.getSqlSet())
                .as("恢复显示也是「一次可见性处置」：处置人 / 处置时间必须刷新，不能只在隐藏时写")
                .contains("hidden_note=", "moderated_by=", "moderated_at = NOW()");
        assertThat(wrapper.getParamNameValuePairs().values())
                .as("附注槽被显式写 NULL —— updateById 的 NOT_NULL 策略会跳过 null，旧附注将永远清不掉")
                .containsNull();
    }

    @Test
    @DisplayName("hideIfVisible：已隐藏 → 不发起任何写入（幂等，且不覆盖既有处置留痕）")
    void hideIfVisibleIsIdempotentAndKeepsTrace() {
        Review hidden = review(8L, 3L, 7L, 4);
        hidden.setIsHidden(1);
        when(reviewMapper.selectById(8L)).thenReturn(hidden);

        assertThat(service().hideIfVisible(8L, 42L)).isFalse();

        verify(reviewMapper, never()).update(isNull(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ==================== 处置留痕：出参 ====================

    @Test
    @DisplayName("listAllForAdmin：处置人经 auth 域契约批量带出 —— 已处置 → 登录名；未处置 → null")
    void listAllForAdminBringsModeratorNameThrough() {
        Review handled = review(1L, 1L, 10L, 5);
        handled.setModeratedBy(42L);
        handled.setModeratedAt(java.time.LocalDateTime.of(2026, 1, 2, 10, 30));
        Review untouched = review(2L, 2L, 10L, 4);
        Page<Review> page = new Page<>(1, 10);
        page.setRecords(List.of(handled, untouched));
        doReturn(page).when(reviewMapper).selectPage(any(), any());
        when(userService.mapBriefByIds(any())).thenReturn(Map.of());
        when(dishService.mapNameByIds(any())).thenReturn(Map.of(10L, "番茄炒蛋"));
        when(adminAccountService.mapUsernameByIds(any())).thenReturn(Map.of(42L, "superadmin"));

        List<ReviewAdminVO> vos = service().listAllForAdmin(1, 10, null, null, null, null).getRecords();

        assertThat(vos.get(0).getModeratedByName()).isEqualTo("superadmin");
        assertThat(vos.get(0).getModeratedAt()).isEqualTo(java.time.LocalDateTime.of(2026, 1, 2, 10, 30));
        // 未处置：两列均 null（端上显示 —），不得伪造成「已处置」
        assertThat(vos.get(1).getModeratedByName()).isNull();
        assertThat(vos.get(1).getModeratedAt()).isNull();
    }

    // ==================== deleteByDishId：级联清理 ====================

    @Test
    @DisplayName("deleteByDishId：dishId 为 null → 0 且不查库；否则按 dishId 批量删并返回行数")
    void deleteByDishIdHandlesNullAndReturnsAffected() {
        ReviewServiceImpl svc = service();
        assertThat(svc.deleteByDishId(null)).isZero();
        verify(reviewMapper, never()).delete(any());

        when(reviewMapper.delete(any())).thenReturn(4);
        assertThat(svc.deleteByDishId(7L)).isEqualTo(4);
    }
}

