package com.bjtufood.correction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 采纳菜品问题反馈请求（{@code POST /admin/corrections/{id}/adopt}）。
 * <p>
 * <b>逐项采纳</b>：管理员先调 {@code GET /admin/corrections/{id}} 拿到 `differences[]`，
 * 勾选后把**选中项的 `field`** 放进 {@link #acceptedFields}。取值必须是该清单里的键
 * （`name` / `price` / `canteenName` / `stallName` / `floor` / `images` / `attributes.<fieldKey>`）。
 * <ul>
 *   <li><b>必填且非空</b>：空数组 → `400`（「什么都不采纳」不是采纳，是**拒绝**，有独立动作与必填原因）；</li>
 *   <li><b>只写回选中项</b>：管理员的判断粒度是「逐项取舍」，而非「全采纳 / 全拒绝」；</li>
 *   <li>选中项若**已无差异**（管理员已手工改成同值）→ `400`，避免「采纳一个已经相同的值」。</li>
 * </ul>
 * <p>
 * {@code type=gone}（已经下架）型**忽略本请求体**，采纳动作 = 置 {@code dish.status=off}（可逆）。
 *
 * <p>两段式档口确认（**仅在采纳了档口 / 食堂项时才需要**）：
 * <ul>
 *   <li>第一段（不带 stallId / createIfMissing）：提交的档口名与现有档口按归一化精确匹配——
 *       命中直接采纳；未命中则<b>不执行采纳</b>，返回 {@link StallConfirmVO}
 *       （候选档口列表，供管理端选择既有档口或确认新建）。</li>
 *   <li>第二段：带 {@code stallId}（管理端从候选中选定既有档口）或
 *       {@code createIfMissing=true}（确认按提交档口名新建）再次调用，执行采纳。</li>
 * </ul>
 */
@Data
@Schema(description = "采纳菜品问题反馈请求（逐项 + 两段式档口确认）")
public class DishCorrectionAdoptReq {

    @Schema(description = "采纳哪些差异项（取值 = GET /admin/corrections/{id} 的 differences[].field）；**必填且非空**",
            example = "[\"name\",\"price\"]")
    private List<String> acceptedFields;

    @Schema(description = "档口ID（可选，两段式第二段：管理端选定的既有档口，校验存在后挂靠）", example = "3")
    private Long stallId;

    @Schema(description = "是否按提交档口名新建档口（可选，默认 false；两段式第二段「确认新建」）", example = "false")
    private Boolean createIfMissing;

    @Schema(description = "采纳附注（可选，≤600 字；缺省用固定文案「已采纳，菜品信息已更新」随回执下发）")
    @Size(max = 600, message = "采纳附注不能超过 600 字")
    private String reply;
}
