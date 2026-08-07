package com.bjtufood.canteen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生"我的发布"档口/食堂列表项
 * <p>
 * 合并 stall 表与 canteen 表中由当前学生提交（created_by=当前用户）的记录。
 * 前端依据 canteenId 是否为 null 推断 type=canteen/stall（canteen 记录无 canteenId 字段，恒为 null）。
 * 字段名遵循 project_spec §3.x.6.1 camelCase 对外约定。
 */
@Data
@Schema(description = "学生我的发布-档口/食堂列表项")
public class MyPublishStallVO {

    @Schema(description = "记录ID", example = "1")
    private Long id;

    /**
     * 关联食堂ID。
     * - stall 记录：所属食堂ID（非 null）→ 前端推断 type=stall
     * - canteen 记录：恒为 null → 前端推断 type=canteen
     */
    @Schema(description = "关联食堂ID（canteen 记录为 null，前端据此推断 type）")
    private Long canteenId;

    @Schema(description = "名称（档口名/食堂名）", example = "面食窗口")
    private String name;

    @Schema(description = "位置")
    private String location;

    @Schema(description = "描述")
    private String description;

    /** 多图，已解析为字符串列表（原始 images 为 JSON 字符串） */
    @Schema(description = "多图URL列表")
    private List<String> images;

    @Schema(description = "审核状态：pending/approved/rejected", example = "pending")
    private String auditStatus;

    @Schema(description = "退回原因（audit_status=rejected 时由后台填写）")
    private String rejectReason;

    @Schema(description = "创建时间", example = "2024-01-01T12:00:00")
    private LocalDateTime createdAt;
}
