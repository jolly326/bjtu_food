package com.bjtufood.feedback.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.feedback.constant.OptionConst;
import com.bjtufood.feedback.entity.Report;
import com.bjtufood.feedback.entity.ReportOption;
import com.bjtufood.feedback.mapper.ReportMapper;
import com.bjtufood.feedback.mapper.ReportOptionMapper;
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
 * 举报原因字典（`report_option`）三条不变量的单测：
 * <ol>
 *   <li><b>至少 1 条启用</b>：停用最后一条启用 → {@code 400}（举报入口不能被配空）；</li>
 *   <li><b>启用 ≤8 条</b>：新增 / 启用时超上限 → {@code 400}；</li>
 *   <li><b>删除受引用约束</b>：被举报记录引用 → {@code 400}（下线一律用停用）。</li>
 * </ol>
 * 规则实现由 {@link AbstractOptionDictionaryService} 与反馈类型字典共用，
 * 本类同时是两字典口径一致性的证据（{@link FeedbackOptionServiceImplTest} 为对照）。
 */
class ReportOptionServiceImplTest {

    /** MyBatis-Plus 的 lambda 缓存需显式初始化（引用计数经 Report 实体的 lambda 条件） */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(),
                ReportOptionServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, ReportOption.class);
        TableInfoHelper.initTableInfo(assistant, Report.class);
    }

    private final ReportOptionMapper reportOptionMapper = mock(ReportOptionMapper.class);
    private final ReportMapper reportMapper = mock(ReportMapper.class);

    private ReportOptionServiceImpl service() {
        return new ReportOptionServiceImpl(reportOptionMapper, reportMapper);
    }

    private static ReportOption option(Long id, String status) {
        ReportOption option = new ReportOption();
        option.setId(id);
        option.setName("标签");
        option.setStatus(status);
        return option;
    }

    @Test
    @DisplayName("updateStatus：停用最后一条启用 → 400（举报入口不能被配空）")
    void updateStatus_disablingLastEnabled_rejected400() {
        when(reportOptionMapper.selectById(1L)).thenReturn(option(1L, OptionConst.STATUS_ON));
        // countEnabled 只数到 1 条启用
        when(reportOptionMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service().updateStatus(1L, OptionConst.STATUS_OFF))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(reportOptionMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("updateStatus：启用数已达上限 8 → 400")
    void updateStatus_beyondEnabledCap_rejected400() {
        when(reportOptionMapper.selectById(2L)).thenReturn(option(2L, OptionConst.STATUS_OFF));
        when(reportOptionMapper.selectCount(any())).thenReturn(8L);

        assertThatThrownBy(() -> service().updateStatus(2L, OptionConst.STATUS_ON))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(reportOptionMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("updateStatus：status 非法 → 400；不存在 → 4001")
    void updateStatus_illegalStatusOrMissing_rejected() {
        assertThatThrownBy(() -> service().updateStatus(1L, "paused"))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        when(reportOptionMapper.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> service().updateStatus(9L, OptionConst.STATUS_OFF))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));
    }

    @Test
    @DisplayName("create：启用数已达上限 8 / 重名 → 400（新增默认启用）")
    void create_beyondEnabledCapOrDuplicate_rejected400() {
        when(reportOptionMapper.selectCount(any())).thenReturn(8L);
        assertThatThrownBy(() -> service().create("新原因"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        when(reportOptionMapper.selectCount(any())).thenReturn(1L, 1L);
        assertThatThrownBy(() -> service().create("已存在的名"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(reportOptionMapper, never()).insert(any());
    }

    @Test
    @DisplayName("delete：被举报记录引用 → 400（删掉会让历史举报翻不出中文）")
    void delete_referencedByReport_rejected400() {
        when(reportOptionMapper.selectById(1L)).thenReturn(option(1L, OptionConst.STATUS_ON));
        when(reportMapper.selectCount(any())).thenReturn(3L);

        assertThatThrownBy(() -> service().delete(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(reportOptionMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("isSubmittable：不存在 / 已停用 → false（停用的原因不能再被提交）")
    void isSubmittable_disabledOrMissing_false() {
        when(reportOptionMapper.selectCount(any())).thenReturn(0L);

        assertThat(service().isSubmittable(1L)).isFalse();
        assertThat(service().isSubmittable(null)).isFalse();
    }

    @Test
    @DisplayName("listEnabled：只下发启用项（查询条件含 status='on'），出参 id + name")
    void listEnabled_returnsEnabledOnly() {
        when(reportOptionMapper.selectList(any())).thenReturn(List.of(option(1L, OptionConst.STATUS_ON)));

        var vos = service().listEnabled();

        assertThat(vos).hasSize(1);
        assertThat(vos.get(0).getId()).isEqualTo(1L);
        assertThat(vos.get(0).getName()).isEqualTo("标签");
    }

    @Test
    @DisplayName("nameByIdAll：含停用项（历史举报的原因仍要能翻译出中文）")
    void nameByIdAll_includesDisabled() {
        when(reportOptionMapper.selectList(any()))
                .thenReturn(List.of(option(1L, OptionConst.STATUS_ON), option(2L, OptionConst.STATUS_OFF)));

        assertThat(service().nameByIdAll()).containsOnlyKeys(1L, 2L);
    }
}
