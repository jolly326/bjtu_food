package com.bjtufood.canteen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 学生 UGC 提交档口/食堂请求
 * <p>
 * type=stall：提交档口，需关联食堂（canteenId）；
 * type=canteen：提交食堂，canteenId 不适用。
 */
@Data
@Schema(description = "学生提交档口/食堂 UGC")
public class StallUgcSubmitReq {

    /** 提交类型：stall（档口）/ canteen（食堂） */
    @NotBlank(message = "type 不能为空")
    @Schema(description = "提交类型：stall（档口）/ canteen（食堂）", example = "stall")
    private String type;

    /** 名称（档口名 / 食堂名） */
    @NotBlank(message = "名称不能为空")
    @Schema(description = "名称", example = "面食窗口")
    private String name;

    /** 描述（可选） */
    @Schema(description = "描述")
    private String description;

    /** 位置（可选） */
    @Schema(description = "位置")
    private String location;

    /** 关联食堂ID（type=stall 时必填，type=canteen 时无需传） */
    @Schema(description = "关联食堂ID（type=stall 时必填）")
    private Long canteenId;
}
