package com.bjtufood.correction.dto;

import com.bjtufood.moderation.constant.ModerationConst;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 标记已处理菜品问题反馈请求（{@code POST /admin/corrections/{id}/handle}）。
 * <p>
 * <b>只归档处置结论、不改写菜品</b>：管理端阅读反馈后，把处置任务归档为
 * {@code outcome='handled'}（已受理，管理员已手动处理）并投递站内回执；
 * 菜品资料修改 / {@code gone} 可逆下架由管理员在菜品管理（web/dishes）<b>手动</b>执行，不在此端点。
 * <ul>
 *   <li>{@code replyTitle} / {@code replyContent} 均<b>可选</b>（处理附注，随回执下发）；</li>
 *   <li>缺省回执文案由服务端按业务措辞下发（见 {@code CorrectionConst.HANDLE_*}）。</li>
 * </ul>
 */
@Data
@Schema(description = "标记已处理菜品问题反馈请求（仅归档 + 回执，不改写菜品）")
public class DishCorrectionHandleReq {

    @Schema(description = "回执短标题（可选，≤40 字；缺省「已收到你的反馈」）", example = "已收到你的反馈")
    @Size(max = 40, message = "回执标题不能超过 40 字")
    private String replyTitle;

    @Schema(description = "处理附注（可选，≤600 字；缺省「感谢反馈，我们已核实处理」，随回执下发）")
    @Size(max = ModerationConst.REPLY_CONTENT_MAX_LENGTH, message = "处理附注不能超过 600 字")
    private String replyContent;
}
