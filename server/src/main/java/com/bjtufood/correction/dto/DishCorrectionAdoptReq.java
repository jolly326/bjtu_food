package com.bjtufood.correction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 采纳菜品纠错请求（{@code POST /admin/corrections/{id}/adopt}，两段式档口确认）。
 * <p>
 * 两段式语义：
 * <ul>
 *   <li>第一段（不带 stallId / createIfMissing）：提交的档口名与现有档口按归一化精确匹配——
 *       命中直接采纳；未命中则<b>不执行采纳</b>，返回 {@link StallConfirmVO}
 *       （候选档口列表，供管理端选择既有档口或确认新建）。</li>
 *   <li>第二段：带 {@code stallId}（管理端从候选中选定既有档口）或
 *       {@code createIfMissing=true}（确认按提交档口名新建）再次调用，执行采纳。</li>
 * </ul>
 */
@Data
@Schema(description = "采纳菜品纠错请求（两段式档口确认）")
public class DishCorrectionAdoptReq {

    @Schema(description = "档口ID（可选，两段式第二段：管理端选定的既有档口，校验存在后挂靠）", example = "3")
    private Long stallId;

    @Schema(description = "是否按提交档口名新建档口（可选，默认 false；两段式第二段「确认新建」）", example = "false")
    private Boolean createIfMissing;
}
