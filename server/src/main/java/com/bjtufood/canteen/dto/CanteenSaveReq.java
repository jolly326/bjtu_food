package com.bjtufood.canteen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 食堂新增 / 编辑请求（{@code POST /admin/canteens} · {@code PUT /admin/canteens/{id}}）。
 *
 * <p><b>可编辑字段 = {@code name} + {@code location} / {@code description} / {@code images} /
 * {@code sortOrder}</b> —— 食堂的全部业务列均在写入面内（管理端可达，见
 * {@code docs/api/web/stalls.md}）。
 *
 * <p>仍然<b>不在本 DTO 内</b>的字段：
 * <ul>
 *   <li>{@code id} —— 走路径参数（不可改）；</li>
 *   <li>{@code updatedAt} —— <b>时间列由 DB 时钟维护</b>
 *      （UPDATE = {@code ON UPDATE CURRENT_TIMESTAMP}），
 *       应用层不写（见 {@code docs/schema/README.md}）；</li>
 *   <li>{@code stallCount} —— 联表派生统计。</li>
 * </ul>
 * 请求体中出现这些字段一律忽略（不落库、不回写）。
 *
 * <p><b>空值语义</b>（{@code PUT}）：{@code name} 必填（整体替换）；
 * {@code location} / {@code description} / {@code images} / {@code sortOrder}
 * <b>缺省 / {@code null} = 保持原值</b>，给值即覆盖（空串 / 空数组 = 清空）。
 */
@Data
@Schema(description = "食堂新增 / 编辑请求")
public class CanteenSaveReq {

    /** 图片张数上限（管理端可用性约束，与 `dish.images` 同档） */
    private static final int IMAGE_MAX = 5;

    @Schema(description = "食堂名称（1~64 字；全站唯一，重名 400）", example = "第一食堂")
    @NotBlank(message = "食堂名称不能为空")
    @Size(max = 64, message = "食堂名称不能超过 64 字")
    private String name;

    @Schema(description = "食堂位置（≤128 字；缺省 = 保持原值，空串 = 清空）", example = "学苑路 3 号")
    @Size(max = 128, message = "食堂位置不能超过 128 字")
    private String location;

    @Schema(description = "食堂描述（≤512 字；缺省 = 保持原值，空串 = 清空）")
    @Size(max = 512, message = "食堂描述不能超过 512 字")
    private String description;

    /**
     * 食堂图片地址（**有序**，首图为封面）。
     * <p>
     * 缺省 / {@code null} = 保持原值；空数组 = 清空。
     * <p>
     * 双重硬校验：张数 ≤ {@value #IMAGE_MAX}（管理端可用性约束）；序列化后长度
     * ≤ {@code canteen.images VARCHAR(1024)}（超长 → 400，禁止静默截断）。
     */
    @Schema(description = "食堂图片地址列表（有序，首图作封面，≤5 张；缺省 = 保持原值，[] = 清空）")
    @Size(max = IMAGE_MAX, message = "食堂图片最多 5 张")
    private List<String> images;

    @Schema(description = "排序权重（升序，越小越靠前；缺省 = 保持原值）", example = "10")
    private Integer sortOrder;
}
