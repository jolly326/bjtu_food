package com.bjtufood.banner.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.banner.entity.Banner;
import com.bjtufood.banner.mapper.BannerMapper;
import com.bjtufood.common.utils.ImageUrlUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code create} 的展示序号回归（A5 管理端）：序号取「现存最大值 + 1」，不取「行数 + 1」。
 * <p>
 * 按行数取号时，删除中间行后行数回缩，新行会撞上现存序号 —— 而 {@code sort()} 的
 * 全量替换语义要求序号互异，撞号会让排序结果不确定。
 */
class BannerServiceImplSortOrderTest {

    /** 纯 Mockito 无 Spring 上下文，MyBatis-Plus 的 lambda 缓存需显式初始化 */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(),
                BannerServiceImplSortOrderTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, Banner.class);
    }

    private final BannerMapper bannerMapper = mock(BannerMapper.class);

    private BannerServiceImpl service() {
        return new BannerServiceImpl(bannerMapper, mock(ImageUrlUtil.class));
    }

    private static Banner banner(Long id, int sortOrder) {
        Banner b = new Banner();
        b.setId(id);
        b.setSortOrder(sortOrder);
        b.setImageUrl("/images/a.jpg");
        return b;
    }

    @Test
    @DisplayName("create：序号 = 现存最大值 + 1（删除中间行后不撞号）")
    void createUsesMaxOrderPlusOne() {
        // 现存 2 行，序号 1 与 5（中间 2/3/4 已被删除）⇒ 按行数取号会得到 3（撞号），按最大值取号得 6
        when(bannerMapper.selectList(any())).thenReturn(List.of(banner(1L, 1), banner(2L, 5)));
        when(bannerMapper.selectById(any())).thenReturn(banner(3L, 6));

        service().create("/images/a.jpg");

        ArgumentCaptor<Banner> saved = ArgumentCaptor.forClass(Banner.class);
        verify(bannerMapper).insert(saved.capture());
        assertThat(saved.getValue().getSortOrder()).isEqualTo(6);
    }

    @Test
    @DisplayName("create：空表 → 首个序号为 1")
    void createOnEmptyTableStartsAtOne() {
        when(bannerMapper.selectList(any())).thenReturn(List.of());
        when(bannerMapper.selectById(any())).thenReturn(banner(1L, 1));

        service().create("/images/a.jpg");

        ArgumentCaptor<Banner> saved = ArgumentCaptor.forClass(Banner.class);
        verify(bannerMapper).insert(saved.capture());
        assertThat(saved.getValue().getSortOrder()).isEqualTo(1);
    }
}
