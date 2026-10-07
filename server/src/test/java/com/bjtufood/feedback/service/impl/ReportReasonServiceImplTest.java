package com.bjtufood.feedback.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.feedback.entity.ReportReason;
import com.bjtufood.feedback.mapper.FeedbackMapper;
import com.bjtufood.feedback.mapper.ReportReasonMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 举报原因字典（A7）三条不变量的单测：
 * <ol>
 *   <li><b>至少 1 条启用</b>：停用最后一条启用 → {@code 400}（举报入口不能被配空）；</li>
 *   <li><b>启用 ≤8 条</b>：新增 / 启用时超上限 → {@code 400}；</li>
 *   <li><b>删除受引用约束</b>：被举报记录引用 → {@code 400}（下线一律用停用）。</li>
 * </ol>
 */
class ReportReasonServiceImplTest {

    /** MyBatis-Plus 的 lambda 缓存需显式初始化，否则构造 LambdaQueryWrapper 会抛异常 */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(),
                ReportReasonServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, ReportReason.class);
        TableInfoHelper.initTableInfo(assistant, Feedback.class);
    }

    private final ReportReasonMapper reportReasonMapper = mock(ReportReasonMapper.class);
    private final FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);

    private ReportReasonServiceImpl service() {
        return new ReportReasonServiceImpl(reportReasonMapper, feedbackMapper);
    }

    private static ReportReason reason(Long id, String status, int sortOrder) {
        ReportReason r = new ReportReason();
        r.setId(id);
        r.setLabel("标签");
        r.setStatus(status);
        r.setSortOrder(sortOrder);
        return r;
    }

    @Test
    @DisplayName("updateStatus：停用最后一条启用 → 400（举报入口不能被配空）")
    void updateStatus_disablingLastEnabled_rejected400() {
        when(reportReasonMapper.selectById(1L)).thenReturn(reason(1L, "on", 1));
        // countEnabled 只数到 1 条启用
        when(reportReasonMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service().updateStatus(1L, "off"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(reportReasonMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("updateStatus：启用数已达上限 8 → 400")
    void updateStatus_beyondEnabledCap_rejected400() {
        when(reportReasonMapper.selectById(2L)).thenReturn(reason(2L, "off", 2));
        when(reportReasonMapper.selectCount(any())).thenReturn(8L);

        assertThatThrownBy(() -> service().updateStatus(2L, "on"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(reportReasonMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("create：启用数已达上限 8 → 400（新增默认启用）")
    void create_beyondEnabledCap_rejected400() {
        when(reportReasonMapper.selectCount(any())).thenReturn(8L);

        assertThatThrownBy(() -> service().create("新原因"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(reportReasonMapper, never()).insert(any());
    }

    @Test
    @DisplayName("delete：被举报记录引用 → 400（删掉会让历史举报翻不出中文）")
    void delete_referencedByReport_rejected400() {
        when(reportReasonMapper.selectById(1L)).thenReturn(reason(1L, "on", 1));
        when(feedbackMapper.selectCount(any())).thenReturn(3L);

        assertThatThrownBy(() -> service().delete(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(reportReasonMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("isSubmittable：不存在 / 已停用 → false（停用的原因不能再被提交）")
    void isSubmittable_disabledOrMissing_false() {
        when(reportReasonMapper.selectCount(any())).thenReturn(0L);

        assertThat(service().isSubmittable(1L)).isFalse();
        assertThat(service().isSubmittable(null)).isFalse();
    }

    @Test
    @DisplayName("listEnabled：只下发启用项（查询条件含 status='on'）")
    void listEnabled_returnsEnabledOnly() {
        when(reportReasonMapper.selectList(any())).thenReturn(List.of(reason(1L, "on", 1)));

        var vos = service().listEnabled();

        assertThat(vos).hasSize(1);
        assertThat(vos.get(0).getId()).isEqualTo(1L);
        assertThat(vos.get(0).getLabel()).isEqualTo("标签");
    }
}
