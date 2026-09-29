package com.bjtufood.correction.service.impl;

import com.bjtufood.auth.dto.UserAuthContextVO;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JsonMapUtil;
import com.bjtufood.correction.dto.DishCorrectionReq;
import com.bjtufood.correction.entity.DishCorrection;
import com.bjtufood.correction.mapper.DishCorrectionMapper;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.moderation.service.ContentSecurityService;
import com.bjtufood.moderation.service.LocalSensitiveFilter;
import com.bjtufood.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CorrectionServiceImpl#submit} 的<b>微信内容安全检测</b>单测（2026-09-29 补齐机审时新增）。
 * <p>
 * <b>为何这条链路原先零测试</b>：纠错是唯一<b>不经微信机审</b>的 UGC 入口（仅靠本地静态词库），
 * 且其内容会被管理员采纳后<b>写回 dish 进入公开展示</b>——风险等级不低，却无任何测试覆盖
 * 「文本是否送检、送检内容是否完整、risky 是否拦截、纯结构化改动是否跳过」。
 * <p>
 * 本测试锁定四条契约：
 * <ol>
 *   <li><b>合并送检且只调一次</b>——msgSecCheck 按调用计费，逐字段送检会成倍放大额度；</li>
 *   <li><b>送检内容覆盖全部自由文本字段</b>（name / canteenName / stallName / attributes）
 *       ——只送 name 会让「把违规词写进食堂名」绕过；</li>
 *   <li><b>risky 必须拦截且不落库</b>；</li>
 *   <li><b>纯 price / images 改动不产生多余微信调用</b>（省额度）。</li>
 * </ol>
 */
class CorrectionModerationTest {

    private DishCorrectionMapper correctionMapper;
    private DishService dishService;
    private StallService stallService;
    private UserService userService;
    private LocalSensitiveFilter localSensitiveFilter;
    private ContentSecurityService contentSecurityService;
    private NotificationService notificationService;
    private ImageUrlUtil imageUrlUtil;
    private CorrectionServiceImpl svc;

    @BeforeEach
    void setUp() {
        correctionMapper = mock(DishCorrectionMapper.class);
        dishService = mock(DishService.class);
        stallService = mock(StallService.class);
        userService = mock(UserService.class);
        localSensitiveFilter = mock(LocalSensitiveFilter.class);
        contentSecurityService = mock(ContentSecurityService.class);
        notificationService = mock(NotificationService.class);
        imageUrlUtil = mock(ImageUrlUtil.class);

        svc = new CorrectionServiceImpl(correctionMapper, new CorrectionPersister(correctionMapper), dishService,
                stallService, userService, localSensitiveFilter, contentSecurityService, notificationService, imageUrlUtil);

        when(dishService.existsOnSale(1L)).thenReturn(true);
        when(localSensitiveFilter.containsSensitive(anyString())).thenReturn(false);
        UserAuthContextVO user = new UserAuthContextVO();
        user.setUserId(7L);
        user.setOpenid("oX-openid");
        when(userService.getAuthContext(7L)).thenReturn(user);
    }

    private DishCorrectionReq req(String name) {
        DishCorrectionReq r = new DishCorrectionReq();
        r.setName(name);
        return r;
    }
    @Test
    @DisplayName("提交含文本的纠错：文本送微信机审，scene=2，且恰好调用一次（合并送检，不按字段数放大）")
    void submitSendsTextToModerationExactlyOnce() {
        svc.submit(7L, 1L, req("宫保鸡丁"));

        // times(1) 锁定「合并为单次调用」：msgSecCheck 按调用计费，
        // 若后人改成逐字段送检，times(1) 立即失败（这正是本用例要防的回归）。
        verify(contentSecurityService, times(1)).checkText(eq("oX-openid"), anyString(), eq(2));
        verify(correctionMapper).insert(any(DishCorrection.class));
    }

    @Test
    @DisplayName("送检文本必须覆盖 name + canteenName + stallName + attributes（换行合并）")
    void moderationTextCoversAllFreeTextFields() {
        DishCorrectionReq r = req("宫保鸡丁");
        r.setCanteenName("学一食堂");
        r.setStallName("基本伙食");
        r.setAttributes(Map.of("dietType", "veg", "note", "微辣"));

        svc.submit(7L, 1L, r);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(contentSecurityService).checkText(eq("oX-openid"), captor.capture(), eq(2));

        assertThat(captor.getValue())
                .as("四个自由文本字段必须全部进入送检内容——漏检任一字段即可被绕过")
                .contains("宫保鸡丁")
                .contains("学一食堂")
                .contains("基本伙食")
                .contains("微辣");
    }

    @Test
    @DisplayName("微信判定 risky：抛 400 且不落库")
    void riskyContentIsRejectedAndNotPersisted() {
        when(contentSecurityService.checkText(any(), anyString(), anyInt()))
                .thenThrow(new BusinessException(400, "内容包含违规信息，请修改后重试"));

        assertThatThrownBy(() -> svc.submit(7L, 1L, req("违规内容")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(correctionMapper, never()).insert(any(DishCorrection.class));
    }

    @Test
    @DisplayName("纯 price 改动（无文本）：跳过微信调用，不浪费额度，且正常落库")
    void priceOnlyChangeSkipsModerationCall() {
        DishCorrectionReq r = new DishCorrectionReq();
        r.setPrice(1600);

        assertThatCode(() -> svc.submit(7L, 1L, r)).doesNotThrowAnyException();

        verify(contentSecurityService, never()).checkText(any(), anyString(), anyInt());
        verify(correctionMapper).insert(any(DishCorrection.class));
    }

    @Test
    @DisplayName("无 openid（登录态缺失）：仍调用 checkText，由其内部按既有口径跳过机审（不 NPE）")
    void missingOpenidDoesNotBreakSubmit() {
        when(userService.getAuthContext(7L)).thenReturn(null);

        assertThatCode(() -> svc.submit(7L, 1L, req("宫保鸡丁"))).doesNotThrowAnyException();

        verify(contentSecurityService).checkText(eq(null), anyString(), eq(2));
        verify(correctionMapper).insert(any(DishCorrection.class));
    }
    @Test
    @DisplayName("userId 为 null（公开入口未登录）：不查用户上下文，直接以 null openid 送检")
    void nullUserIdSkipsUserLookup() {
        svc.submit(null, 1L, req("宫保鸡丁"));

        verify(userService, never()).getAuthContext(any());
        verify(contentSecurityService).checkText(eq(null), anyString(), eq(2));
        verify(correctionMapper).insert(any(DishCorrection.class));
    }

    @Test
    @DisplayName("本地词库拦截的 name：不触发微信调用（前置校验已短路，省一次额度）")
    void locallyBlockedNameShortCircuitsBeforeWechat() {
        when(localSensitiveFilter.containsSensitive("违规名")).thenReturn(true);

        assertThatThrownBy(() -> svc.submit(7L, 1L, req("违规名")))
                .isInstanceOf(BusinessException.class);

        verify(contentSecurityService, never()).checkText(any(), anyString(), anyInt());
        verify(correctionMapper, never()).insert(any(DishCorrection.class));
    }

    @Test
    @DisplayName("空请求体（无任何改动）：400，不送检不落库")
    void emptyPatchIsRejected() {
        assertThatThrownBy(() -> svc.submit(7L, 1L, new DishCorrectionReq()))
                .isInstanceOf(BusinessException.class);

        verify(contentSecurityService, never()).checkText(any(), anyString(), anyInt());
        verify(correctionMapper, never()).insert(any(DishCorrection.class));
    }

    @Test
    @DisplayName("仅 images 改动：跳过送检，且图片地址需为合法 COS 地址")
    void imagesOnlyChangeSkipsModeration() {
        when(imageUrlUtil.isValidCosUgcUrl(anyString())).thenReturn(true);
        DishCorrectionReq r = new DishCorrectionReq();
        r.setImages(List.of("https://x-1250000000.cos.ap-beijing.myqcloud.com/ugc/a.jpg"));

        assertThatCode(() -> svc.submit(7L, 1L, r)).doesNotThrowAnyException();

        verify(contentSecurityService, never()).checkText(any(), anyString(), anyInt());
        verify(correctionMapper).insert(any(DishCorrection.class));
    }

    @Test
    @DisplayName("attributes 送检用 JSON 还原（覆盖自由文本维度值，且与落库序列化同源）")
    void attributesAreJsonEncodedIntoModerationText() {
        DishCorrectionReq r = new DishCorrectionReq();
        r.setAttributes(Map.of("flavorTags", List.of("spicy", "sour")));

        svc.submit(7L, 1L, r);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(contentSecurityService).checkText(eq("oX-openid"), captor.capture(), eq(2));
        assertThat(captor.getValue()).contains("spicy").contains("sour");
        // 与落库序列化共用同一工具，避免「送检文本」与「落库文本」形态分叉
        assertThat(captor.getValue()).isEqualTo(JsonMapUtil.toJson(r.getAttributes()));
    }
}