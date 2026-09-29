package com.bjtufood;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.banner.dto.BannerVO;
import com.bjtufood.banner.entity.Banner;
import com.bjtufood.banner.mapper.BannerMapper;
import com.bjtufood.banner.service.impl.BannerServiceImpl;
import com.bjtufood.canteen.dto.CanteenAdminVO;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.service.impl.CanteenServiceImpl;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.review.dto.StallAvgRatingVO;
import com.bjtufood.review.mapper.ReviewMapper;
import com.bjtufood.review.service.impl.ReviewQueryServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 三个<b>薄适配层</b> Service 的单元测试（合并一处，因三者同质且断言点相近）：
 * <ul>
 *   <li>{@link ReviewQueryServiceImpl} —— 跨域只读契约（canteen 消费档口平均评分）。
 *       刻意零业务依赖是<b>防依赖环</b>的设计决策（见其类注释），须由测试守住不被误注入业务依赖；</li>
 *   <li>{@link BannerServiceImpl} —— 公开只读轮播；<b>出参收敛</b>是契约
 *       （{@code status}/{@code sort_order} 仅用于过滤与排序，<b>不得出参</b>）；</li>
 *   <li>{@link CanteenServiceImpl} —— 后台列表与编辑；{@code update} 的存在性校验决定
 *       「编辑不存在的食堂」是报错还是<b>静默成功</b>。</li>
 * </ul>
 * 三者构造 {@code LambdaQueryWrapper}，故需在 {@code @BeforeAll} 初始化 MyBatis lambda 缓存
 * （{@code TableInfo}）——该缓存正常由 Spring 启动时的 Mapper 扫描填充，
 * 纯 Mockito 单测下缺失会抛 {@code "MybatisPlus can not find lambda cache for this entity"}。
 */
class ThinServicesTest {

    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), ThinServicesTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, Banner.class);
        TableInfoHelper.initTableInfo(assistant, Canteen.class);
    }

    // ==================== ReviewQueryServiceImpl ====================

    @Test
    @DisplayName("findAvgRatingByStallIds：null/空集合 → 空 List 且不查库（省掉无意义的 IN ()）")
    void findAvgRatingShortCircuitsOnEmptyInput() {
        ReviewMapper reviewMapper = mock(ReviewMapper.class);
        ReviewQueryServiceImpl svc = new ReviewQueryServiceImpl(reviewMapper);

        assertThat(svc.findAvgRatingByStallIds((Collection<Long>) null)).isEmpty();
        assertThat(svc.findAvgRatingByStallIds(List.of())).isEmpty();

        verify(reviewMapper, never()).selectAvgRatingByStallIds(any());
    }

    @Test
    @DisplayName("findAvgRatingByStallIds：委托 Mapper 并原样返回（不二次加工，避免口径漂移）")
    void findAvgRatingDelegatesToMapper() {
        ReviewMapper reviewMapper = mock(ReviewMapper.class);
        StallAvgRatingVO vo = new StallAvgRatingVO();
        vo.setStallId(3L);
        vo.setAvgRating(new java.math.BigDecimal("4.5"));
        when(reviewMapper.selectAvgRatingByStallIds(any())).thenReturn(List.of(vo));

        assertThat(new ReviewQueryServiceImpl(reviewMapper).findAvgRatingByStallIds(List.of(3L)))
                .containsExactly(vo);
    }

    // ==================== BannerServiceImpl ====================

    @Test
    @DisplayName("listBanners：imageUrl 转绝对地址；出参仅 id + imageUrl（status/sortOrder 收敛不出参）")
    void listBannersConvertsUrlAndConvergesFields() {
        BannerMapper bannerMapper = mock(BannerMapper.class);
        ImageUrlUtil imageUrlUtil = mock(ImageUrlUtil.class);
        Banner b = new Banner();
        b.setId(1L);
        b.setImageUrl("/images/b1.png");
        b.setStatus("on");
        b.setSortOrder(3);
        when(bannerMapper.selectList(any())).thenReturn(List.of(b));
        when(imageUrlUtil.toAbsoluteUrl("/images/b1.png")).thenReturn("http://host/api/v1/images/b1.png");

        List<BannerVO> vos = new BannerServiceImpl(bannerMapper, imageUrlUtil).listBanners();

        assertThat(vos).hasSize(1);
        assertThat(vos.get(0).getId()).isEqualTo(1L);
        assertThat(vos.get(0).getImageUrl()).isEqualTo("http://host/api/v1/images/b1.png");
        // 出参收敛为公开契约：仅 2 字段，不得泄漏 status / sortOrder
        assertThat(BannerVO.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .containsExactlyInAnyOrder("id", "imageUrl");
    }

    // ==================== CanteenServiceImpl ====================

    @Test
    @DisplayName("canteen.update：id 为 null → 抛业务异常，且不触碰数据库（防无主更新）")
    void updateWithoutIdIsRejected() {
        CanteenMapper canteenMapper = mock(CanteenMapper.class);
        Canteen c = new Canteen();
        c.setName("第一食堂");

        assertThatThrownBy(() -> new CanteenServiceImpl(canteenMapper, mock(ImageUrlUtil.class)).update(c))
                .isInstanceOf(BusinessException.class);
        verify(canteenMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("canteen.update：影响行数为 0（实体不存在）→ 抛异常，不得静默成功")
    void updateMissingEntityIsRejected() {
        CanteenMapper canteenMapper = mock(CanteenMapper.class);
        when(canteenMapper.updateById(any())).thenReturn(0);
        Canteen c = new Canteen();
        c.setId(9L);

        assertThatThrownBy(() -> new CanteenServiceImpl(canteenMapper, mock(ImageUrlUtil.class)).update(c))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("canteen.update：影响行数 > 0 → 放行")
    void updateExistingEntitySucceeds() {
        CanteenMapper canteenMapper = mock(CanteenMapper.class);
        when(canteenMapper.updateById(any())).thenReturn(1);
        Canteen c = new Canteen();
        c.setId(1L);

        assertThatCode(() -> new CanteenServiceImpl(canteenMapper, mock(ImageUrlUtil.class)).update(c))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("listAllForAdmin：images 经 parseAndToAbsoluteUrls 转绝对地址数组")
    void listAllForAdminConvertsImages() {
        CanteenMapper canteenMapper = mock(CanteenMapper.class);
        ImageUrlUtil imageUrlUtil = mock(ImageUrlUtil.class);
        Canteen c = new Canteen();
        c.setId(1L);
        c.setName("第一食堂");
        when(canteenMapper.selectList(any())).thenReturn(List.of(c));
        when(imageUrlUtil.parseAndToAbsoluteUrls(any())).thenReturn(List.of("http://host/api/v1/i/1.jpg"));

        List<CanteenAdminVO> vos = new CanteenServiceImpl(canteenMapper, imageUrlUtil).listAllForAdmin();

        assertThat(vos).hasSize(1);
        assertThat(vos.get(0).getName()).isEqualTo("第一食堂");
        assertThat(vos.get(0).getImages()).containsExactly("http://host/api/v1/i/1.jpg");
    }
}
