package com.bjtufood.canteen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 食堂新增 / 改名请求（{@code POST /admin/canteens} · {@code PUT /admin/canteens/{id}}）。
 *
 * <p><b>可编辑字段只有 {@code name}</b>。以下字段<b>不在本 DTO 内</b>（换 DTO 后 mass-assignment 面
 * 从签名上关闭，{@code @Valid} 不再空转）：
 * <ul>
 *   <li>{@code id} —— 走路径参数；</li>
 *   <li>{@code images} / {@code location} / {@code description} / {@code sortOrder} —— <b>保留列</b>，
 *       管理端无编辑入口（见 docs/schema/canteen.md）；</li>
 *   <li>{@code createdAt} / {@code updatedAt} —— 时间列，由数据库填充；</li>
 *   <li>{@code stallCount} —— 联表派生统计。</li>
 * </ul>
 * 请求体中出现这些字段一律忽略（不落库、不回写）。字段级口径见 docs/api/web/stalls.md。
 */
@Data
@Schema(description = "食堂新增 / 改名请求")
public class CanteenSaveReq {

    @Schema(description = "食堂名称（1~64 字；全站唯一，重名 400）", example = "第一食堂")
    @NotBlank(message = "食堂名称不能为空")
    @Size(max = 64, message = "食堂名称不能超过 64 字")
    private String name;
}
