package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 用户统计视图对象（VO）
 * <p>
 * 个人中心页展示的用户统计数据
 */
@Data
@AllArgsConstructor
@Schema(description = "用户统计信息")
public class UserStatsVO {

    @Schema(description = "收藏数", example = "12")
    private Long favoriteCount;

    @Schema(description = "评价数", example = "8")
    private Long reviewCount;
}
