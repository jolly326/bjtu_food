package com.bjtufood.canteen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 档口新增 / 编辑请求（{@code POST /admin/stalls} · {@code PUT /admin/stalls/{id}}）。
 *
 * <p><b>可编辑字段 = {@code canteenId} / {@code name} / {@code floor} / {@code windowNo}
 * + {@code location} / {@code description} / {@code images} / {@code sortOrder}</b> ——
 * 档口的全部业务列均在写入面内（管理端可达，见 {@code docs/api/web/stalls.md}）。
 *
 * <p>仍然<b>不在本 DTO 内</b>的字段：
 * <ul>
 *   <li>{@code id} —— 走路径参数（不可改）；</li>
 *   <li>{@code updatedAt} —— <b>时间列由 DB 时钟维护</b>
 *      （UPDATE = {@code ON UPDATE CURRENT_TIMESTAMP}），
 *       应用层不写（见 {@code docs/schema/README.md}）；</li>
 *   <li>{@code canteenName} / {@code dishCount} / {@code avgRating} —— 联表 / 派生统计。</li>
 * </ul>
 * 请求体中出现这些字段一律忽略（不落库、不回写）。
 *
 * <p>{@code floor} <b>刻意不在本 DTO 做枚举校验</b>：楼层字典的唯一真源是
 * {@code com.bjtufood.canteen.constant.FloorDict}，在此再列一遍值域会形成<b>第二真源</b>
 * （字典增删时两处必然漂移）。因此「空白串 → 400」「字典外值 → 400」由服务层承担。
 *
 * <p><b>空值语义</b>（{@code PUT}）：{@code canteenId} / {@code name} 必填（整体替换）；
 * {@code floor} / {@code windowNo} / {@code location} / {@code description} / {@code images} /
 * {@code sortOrder} <b>缺省 / {@code null} = 保持原值</b>，给值即覆盖（空串 / 空数组 = 清空，
 * {@code floor} 例外：字典内无「空楼层」，不支持清空）。
 */
@Data
@Schema(description = "档口新增 / 编辑请求")
public class StallSaveReq {

    /** 图片张数上限（管理端可用性约束，与 `dish.images` 同档） */
    private static final int IMAGE_MAX = 5;

    @Schema(description = "所属食堂ID（必填，须为已存在食堂）", example = "1")
    @NotNull(message = "请选择所属食堂")
    @Positive(message = "请选择所属食堂")
    private Long canteenId;

    @Schema(description = "档口名称（1~64 字；同食堂下唯一，重名 400）", example = "面食窗口")
    @NotBlank(message = "档口名称不能为空")
    @Size(max = 64, message = "档口名称不能超过 64 字")
    private String name;

    @Schema(description = "楼层（受控字典、值即汉字：负一层/一层/二层/三层/四层；缺省 = 保持原值，不支持清空）",
            example = "二层")
    private String floor;

    @Schema(description = "窗口号（≤32 字；缺省 = 保持原值，空串 = 清空）", example = "3号窗口")
    @Size(max = 32, message = "窗口号不能超过 32 字")
    private String windowNo;

    @Schema(description = "档口位置（≤128 字；缺省 = 保持原值，空串 = 清空）", example = "二层东侧")
    @Size(max = 128, message = "档口位置不能超过 128 字")
    private String location;

    @Schema(description = "档口描述（≤512 字；缺省 = 保持原值，空串 = 清空）")
    @Size(max = 512, message = "档口描述不能超过 512 字")
    private String description;

    /**
     * 档口图片地址（**有序**，首图为封面）。
     * <p>
     * 缺省 / {@code null} = 保持原值；空数组 = 清空。
     * <p>
     * 双重硬校验：张数 ≤ {@value #IMAGE_MAX}（管理端可用性约束）；序列化后长度
     * ≤ {@code stall.images VARCHAR(1024)}（超长 → 400，禁止静默截断）。
     */
    @Schema(description = "档口图片地址列表（有序，首图作封面，≤5 张；缺省 = 保持原值，[] = 清空）")
    @Size(max = IMAGE_MAX, message = "档口图片最多 5 张")
    private List<String> images;

    @Schema(description = "排序权重（升序，越小越靠前；缺省 = 保持原值）", example = "10")
    private Integer sortOrder;
}
