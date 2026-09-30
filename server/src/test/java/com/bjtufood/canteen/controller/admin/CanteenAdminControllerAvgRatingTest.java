package com.bjtufood.canteen.controller.admin;

import com.bjtufood.canteen.dto.StallAdminVO;
import com.bjtufood.canteen.service.CanteenService;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.canteen.service.impl.StallServiceImpl;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.review.dto.StallAvgRatingVO;
import com.bjtufood.review.service.ReviewQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 包级环偿还的回归护栏：<b>档口均分由 controller 层编排，而非 canteen 域自行拉取</b>。
 * <p>
 * 背景：{@code StallServiceImpl} 曾注入 {@code ReviewQueryService} 以填充
 * 后台列表的档口平均分，构成
 * <pre>
 *   canteen -> review -> dish -> canteen
 * </pre>
 * 真实包级循环。均分是 review 按 dish 聚合的<b>派生展示值</b>，不属于 canteen（菜品属性字典）
 * 的自有知识，由属性字典反向依赖评价域方向本就颠倒。现上移至
 * {@link CanteenAdminController#fillAvgRatings} 编排，controller 位于依赖图顶端，不产生新包级边。
 * <p>
 * 配套的架构护栏见 {@code ArchTests#canteen_mustNotDependOnReview}；
 * 本类锁定其<b>行为面</b>——出参口径（2 位小数、无评价按 0.00）、批量查询（不 N+1）、
 * 以及 canteen 侧确实不再触发 review 查询。
 */
class CanteenAdminControllerAvgRatingTest {

    private static StallAdminVO vo(Long id) {
        StallAdminVO v = new StallAdminVO();
        v.setId(id);
        v.setName("档口" + id);
        // 复刻 StallServiceImpl.listAllForAdmin 的兜底：未回填前即为 0.00（非 null）
        v.setAvgRating(new BigDecimal("0.00"));
        return v;
    }

    private static StallAvgRatingVO rating(Long stallId, String avg) {
        StallAvgRatingVO v = new StallAvgRatingVO();
        v.setStallId(stallId);
        v.setAvgRating(new BigDecimal(avg));
        return v;
    }

    @Test
    @DisplayName("均分按 stallId 精确回填到对应档口，不串号")
    void fillsRatingByStallId() {
        StallService stallService = mock(StallService.class);
        ReviewQueryService reviewQuery = mock(ReviewQueryService.class);
        // 必须是可变 List：controller 原地回填后，断言要看的是同一个对象实例的字段
        List<StallAdminVO> stalls = new ArrayList<>(List.of(vo(1L), vo(2L), vo(3L)));
        when(stallService.listAllForAdmin()).thenReturn(stalls);
        when(reviewQuery.findAvgRatingByStallIds(any()))
                .thenReturn(List.of(rating(1L, "4.5"), rating(3L, "3.2")));

        new CanteenAdminController(mock(CanteenService.class), stallService, reviewQuery).listStalls();

        assertThat(stalls.get(0).getAvgRating()).isEqualByComparingTo("4.50");
        assertThat(stalls.get(1).getAvgRating()).isEqualByComparingTo("0.00");  // 无评价
        assertThat(stalls.get(2).getAvgRating()).isEqualByComparingTo("3.20");
    }

    @Test
    @DisplayName("无评价档口保持 0.00 兜底（不被置为 null，也不被其他档口的均分污染）")
    void keepsZeroForStallsWithoutReviews() {
        StallService stallService = mock(StallService.class);
        ReviewQueryService reviewQuery = mock(ReviewQueryService.class);
        List<StallAdminVO> stalls = new ArrayList<>(List.of(vo(1L), vo(2L)));
        stalls.get(0).setAvgRating(new BigDecimal("0.00"));
        stalls.get(1).setAvgRating(new BigDecimal("0.00"));
        when(stallService.listAllForAdmin()).thenReturn(stalls);
        when(reviewQuery.findAvgRatingByStallIds(any())).thenReturn(List.of(rating(1L, "4.567")));

        new CanteenAdminController(mock(CanteenService.class), stallService, reviewQuery).listStalls();

        assertThat(stalls.get(0).getAvgRating()).isEqualByComparingTo("4.57");   // HALF_UP 到 2 位
        assertThat(stalls.get(1).getAvgRating()).isEqualByComparingTo("0.00");   // 无评价 → 保留兜底
    }

    @Test
    @DisplayName("空档口列表 → 不查 review（省掉无意义的 IN ()）")
    void skipsReviewQueryForEmptyStallList() {
        StallService stallService = mock(StallService.class);
        ReviewQueryService reviewQuery = mock(ReviewQueryService.class);
        when(stallService.listAllForAdmin()).thenReturn(List.of());

        new CanteenAdminController(mock(CanteenService.class), stallService, reviewQuery).listStalls();

        verify(reviewQuery, never()).findAvgRatingByStallIds(any());
    }

    @Test
    @DisplayName("关键回归：StallServiceImpl 不再持有 review 依赖，canteen 侧不触发任何 review 查询")
    void stallServiceNoLongerDependsOnReview() {
        StallServiceImpl impl = new StallServiceImpl(
                mock(com.bjtufood.canteen.mapper.StallMapper.class),
                mock(com.bjtufood.canteen.mapper.CanteenMapper.class),
                mock(ImageUrlUtil.class));

        // 构造器只剩 3 个参数（无 ReviewQueryService）——若有人重新注入 review，本行编译即失败，
        // ArchTests#canteenBusinessLayers_mustNotDependOnReview 也会在 mvn test 阶段拦下。
        assertThat(impl).isNotNull();
    }
}
