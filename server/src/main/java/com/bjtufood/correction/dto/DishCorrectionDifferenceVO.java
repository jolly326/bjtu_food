package com.bjtufood.correction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 纠错**差异对照**项（{@code GET /admin/corrections/{id}} 的 `differences[]`）。
 *
 * <p>契约真源：docs/api/web/corrections.md 的「响应 · `differences[]`」。
 * <ul>
 *   <li><b>只列「仍有差异」的项</b>：快照是**提交当时**的差异；若管理员已通过 A3 手动改成了同样的值，
 *       该项**已无差异** —— 再列进采纳清单等于「采纳一个已经相同的值」，徒增噪音与误操作；</li>
 *   <li>{@code oldValue} 取**当前菜品/档口的实时值**（快照只存"提交的新值"，原值必须回查）；</li>
 *   <li>{@link #field} 可直接作为 `acceptedFields` 的取值（端上即用它与采纳提交的键对齐）。</li>
 * </ul>
 */
@Data
@Schema(description = "纠错差异对照项")
public class DishCorrectionDifferenceVO {

    @Schema(description = "差异项键（可直接作为 acceptedFields 的取值）：name/price/canteenName/stallName/floor/images/attributes.<维度ID>",
            example = "name")
    private String field;

    @Schema(description = "字段中文名（服务端下发，端上零硬编码）", example = "菜品名称")
    private String label;

    @Schema(description = "当前实时值（回查 dish / stall）")
    private String oldValue;

    @Schema(description = "用户提交的值")
    private String newValue;

    /**
     * 连带影响提示：仅 {@code floor} 为 {@code true}。
     * <p>
     * 楼层归属**档口**（{@code stall.floor}）而非菜品 ⇒ 采纳会**连带改同档口所有菜品**的详情楼层，
     * UI 必须高亮提示供二次确认。
     */
    @Schema(description = "是否连带影响同档口其它菜品（仅 floor 为 true）")
    private boolean affectsOthers;
}
