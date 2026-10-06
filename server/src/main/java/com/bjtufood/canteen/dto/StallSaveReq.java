package com.bjtufood.canteen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 档口新增 / 修改请求（{@code POST /admin/stalls} · {@code PUT /admin/stalls/{id}}）。
 *
 * <p><b>可编辑字段 = {@code canteenId} / {@code name} / {@code floor} / {@code windowNo}</b>。
 * 以下字段<b>不在本 DTO 内</b>（换 DTO 后 mass-assignment 面从签名上关闭，{@code @Valid} 不再空转）：
 * <ul>
 *   <li>{@code id} —— 走路径参数；</li>
 *   <li>{@code images} / {@code location} / {@code description} —— <b>保留列</b>，管理端无编辑入口；
 *       {@code sortOrder} —— 排序位（A2 无排序入口），恒由维护方直改列（见 docs/schema/stall.md）；</li>
 *   <li>{@code createdAt} / {@code updatedAt} —— 时间列，由数据库填充；</li>
 *   <li>{@code canteenName} / {@code dishCount} / {@code avgRating} —— 联表 / 派生统计。</li>
 * </ul>
 * 请求体中出现这些字段一律忽略（不落库、不回写）。字段级口径见 docs/api/web/stalls.md。
 *
 * <p>{@code floor} <b>刻意不在本 DTO 做枚举校验</b>：楼层字典的唯一真源是
 * {@code com.bjtufood.canteen.constant.FloorDict}，在此再列一遍值域会形成<b>第二真源</b>
 * （字典增删时两处必然漂移）。因此「空白串 → 400」「字典外值 → 400」由服务层承担。
 */
@Data
@Schema(description = "档口新增 / 修改请求")
public class StallSaveReq {

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
}
