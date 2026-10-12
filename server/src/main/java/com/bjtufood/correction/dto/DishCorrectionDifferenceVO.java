package com.bjtufood.correction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 菜品问题反馈**提交项对照**项（{@code GET /admin/corrections/{id}} 的 `differences[]`）。
 *
 * <p>契约真源：docs/api/web/corrections.md 的「响应 · `differences[]`」。
 * <ul>
 *   <li><b>只列「用户提交过的项」</b>：{@code field} 型只提交改动项，逐项呈现「当前值 / 提交值」，
 *       供管理员阅读判断；<b>不再有「采纳清单 / 仍有差异」语义</b>；</li>
 *   <li>菜品已物理删除时 {@code oldValue} 为 {@code null}。</li>
 * </ul>
 */
@Data
@Schema(description = "菜品问题反馈提交项对照项")
public class DishCorrectionDifferenceVO {

    @Schema(description = "对照项键：name/price/canteenName/stallName/floor/images/attributes.<维度ID>",
            example = "name")
    private String field;

    @Schema(description = "字段中文名（服务端下发，端上零硬编码）", example = "菜品名称")
    private String label;

    @Schema(description = "当前实时值（回查 dish / stall；菜品已物理删除为 null）")
    private String oldValue;

    @Schema(description = "用户提交的值")
    private String newValue;

    @Schema(description = "提交值是否与当前值一致（true = 已一致，端上据此标注「已一致 / 有差异」）")
    private boolean same;
}
