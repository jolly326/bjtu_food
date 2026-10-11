package com.bjtufood.feedback.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.feedback.constant.OptionConst;
import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.feedback.entity.FeedbackOption;
import com.bjtufood.feedback.mapper.FeedbackMapper;
import com.bjtufood.feedback.mapper.FeedbackOptionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 反馈类型字典（`feedback_option`）单测。
 * <p>
 * 与举报原因字典同族同口径（规则实现在 {@link AbstractOptionDictionaryService} 内只有一份），
 * 本类锁定两点：① 规则在反馈类型字典上同样生效；② <b>引用计数统计的是反馈表</b>
 * （引用计数走错表 → 删除拦截形同虚设，会把历史反馈的分类翻不出中文）。
 */
class FeedbackOptionServiceImplTest {

    /** MyBatis-Plus 的 lambda 缓存需显式初始化（引用计数经 Feedback 实体的 lambda 条件） */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(),
                FeedbackOptionServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, FeedbackOption.class);
        TableInfoHelper.initTableInfo(assistant, Feedback.class);
    }

    private final FeedbackOptionMapper feedbackOptionMapper = mock(FeedbackOptionMapper.class);
    private final FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);

    private FeedbackOptionServiceImpl service() {
        return new FeedbackOptionServiceImpl(feedbackOptionMapper, feedbackMapper);
    }

    private static FeedbackOption option(Long id, String status) {
        FeedbackOption option = new FeedbackOption();
        option.setId(id);
        option.setName("程序功能Bug");
        option.setStatus(status);
        return option;
    }

    @Test
    @DisplayName("listEnabled：只下发启用项（端上反馈类型下拉的唯一来源）")
    void listEnabled_returnsEnabledOnly() {
        when(feedbackOptionMapper.selectList(any())).thenReturn(List.of(option(1L, OptionConst.STATUS_ON)));

        var vos = service().listEnabled();

        assertThat(vos).hasSize(1);
        assertThat(vos.get(0).getName()).isEqualTo("程序功能Bug");
    }

    @Test
    @DisplayName("删除：被反馈引用 → 400，且引用计数查的是**反馈表**（查错表则拦截失效）")
    void delete_referencedByFeedback_rejected400() {
        when(feedbackOptionMapper.selectById(1L)).thenReturn(option(1L, OptionConst.STATUS_ON));
        when(feedbackMapper.selectCount(any())).thenReturn(5L);

        assertThatThrownBy(() -> service().delete(1L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(feedbackMapper).selectCount(any());
        verify(feedbackOptionMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("停用：最后一条启用 → 400（反馈入口不能被配空）")
    void updateStatus_disablingLastEnabled_rejected400() {
        when(feedbackOptionMapper.selectById(1L)).thenReturn(option(1L, OptionConst.STATUS_ON));
        when(feedbackOptionMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service().updateStatus(1L, OptionConst.STATUS_OFF))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(feedbackOptionMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("isSubmittable：命中启用项 → true；未命中 → false（提交白名单）")
    void isSubmittable_hitsEnabledOption() {
        when(feedbackOptionMapper.selectCount(any())).thenReturn(1L);
        assertThat(service().isSubmittable(1L)).isTrue();

        when(feedbackOptionMapper.selectCount(any())).thenReturn(0L);
        assertThat(service().isSubmittable(2L)).isFalse();
    }

    @Test
    @DisplayName("nameByIdAll：含停用项（历史反馈的类型仍要能翻译出中文）")
    void nameByIdAll_includesDisabled() {
        when(feedbackOptionMapper.selectList(any()))
                .thenReturn(List.of(option(1L, OptionConst.STATUS_ON), option(2L, OptionConst.STATUS_OFF)));

        Map<Long, String> names = service().nameByIdAll();

        assertThat(names).containsOnlyKeys(1L, 2L);
    }
}
