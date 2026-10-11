package com.bjtufood.feedback.controller;

import com.bjtufood.common.result.Result;
import com.bjtufood.feedback.dto.OptionVO;
import com.bjtufood.feedback.service.FeedbackOptionService;
import com.bjtufood.feedback.service.ReportOptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类字典公开只读端点（学生端表单 / 弹层实时拉取）。
 * <p>
 * 两张字典同族（`feedback_option` / `report_option`），公开形态与出参完全一致（恰 {@code id} + {@code name}、
 * 仅启用项、按 `id` 升序），故合并在一个控制器内下发 —— 合并的依据是<b>出参同形</b>，
 * 而不是「都属于反馈」：字典是<b>独立的只读枚举</b>，不是提交端点的子资源，故不复用
 * {@code /feedback} / {@code /reviews} 的路径前缀。
 * <p>
 * 两个路径均在 {@code SecurityConfig} 的公开白名单内（非敏感枚举，两端共用，免登录可读）；
 * 管理端维护入口在 {@code /admin/feedback-options} 与 {@code /admin/report-options}（管理端 JWT 保护）。
 */
@Tag(name = "分类字典", description = "反馈类型 / 举报原因字典（公开只读，端上零硬编码）")
@RestController
@RequiredArgsConstructor
public class OptionDictionaryController {

    private final FeedbackOptionService feedbackOptionService;
    private final ReportOptionService reportOptionService;

    /**
     * 反馈类型字典（PUB）——{@code GET /feedback-options}。
     * 值域与文案唯一真源 = `feedback_option` 表，本端点只下发**启用项**。
     */
    @Operation(summary = "反馈类型字典",
            description = "PUB。意见反馈页的「反馈类型」单选项（id 选项 ID + name 中文名），仅含启用项；"
                    + "服务端按 id 升序下发，端上按数组顺序渲染；提交反馈时选中的 id 作为 optionId 上送。"
                    + "端上零硬编码。测试示例：/feedback-options")
    @GetMapping("/feedback-options")
    public Result<List<OptionVO>> feedbackOptions() {
        return Result.success(feedbackOptionService.listEnabled());
    }

    /**
     * 举报原因字典（PUB）——{@code GET /report-options}。
     * 值域与文案唯一真源 = `report_option` 表，本端点只下发**启用项**。
     */
    @Operation(summary = "举报原因字典",
            description = "PUB。举报时的原因单选项（id 原因 ID + name 中文名），仅含启用项；"
                    + "服务端按 id 升序下发，端上按数组顺序渲染；提交举报时选中的 id 作为 optionId 上送。"
                    + "端上零硬编码。测试示例：/report-options")
    @GetMapping("/report-options")
    public Result<List<OptionVO>> reportOptions() {
        return Result.success(reportOptionService.listEnabled());
    }
}
