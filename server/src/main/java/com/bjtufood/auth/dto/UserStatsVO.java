package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 用户统计视图对象（VO）
 * <p>
 * 个人中心「我的」统计行（StatsRow 三宫格，§七 #3）数据来源。
 * 字段：发布数 / 待审数 / 收藏数 / 评价数。
 * 说明：收藏数因 favorite 模块已整体移除（task-12.12），当前无数据源，暂以 0 占位；
 * 「我的喜欢(❤️)」语义按 spec §5.x 保留但计数存储方案待架构师裁定，待定后回填。
 */
@Data
@AllArgsConstructor
@Schema(description = "用户统计信息")
public class UserStatsVO {

    @Schema(description = "发布数（本人提交的菜品总数，含全部审核态）", example = "5")
    private Long publishedCount;

    @Schema(description = "待审数（本人提交的菜品中 audit_status=pending 的数量）", example = "2")
    private Long pendingCount;

    @Schema(description = "收藏数（favorite 模块已移除，待架构师裁定喜欢计数口径，暂为 0）", example = "0")
    private Long favoriteCount;

    @Schema(description = "评价数（本人已发布且未被隐藏的评价数）", example = "8")
    private Long reviewCount;
}
