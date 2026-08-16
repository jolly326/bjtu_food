package com.bjtufood.moment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 社区动态视图对象（公开列表/详情）
 */
@Data
@Schema(description = "动态展示信息")
public class MomentVO {

    @Schema(description = "动态ID")
    private Long id;

    @Schema(description = "发布者用户ID")
    private Long userId;

    @Schema(description = "发布者昵称")
    private String userNickname;

    @Schema(description = "发布者头像URL")
    private String userAvatar;

    @Schema(description = "正文内容")
    private String content;

    /** 图片URL列表（由逗号串解析，DB 列内部使用 imagesJson） */
    @Schema(description = "图片URL列表")
    private List<String> images;

    @JsonIgnore
    @Schema(hidden = true)
    private String imagesJson;

    @Schema(description = "关联对象类型：dish/stall/none")
    private String relatedType;

    @Schema(description = "关联对象ID")
    private Long relatedId;

    @Schema(description = "关联对象名称（菜品名/档口名，可选）")
    private String relatedName;

    @Schema(description = "关联档口所属食堂名（仅 relatedType=stall 返回，供前端跳档口详情携带 navParams.canteen）")
    private String relatedCanteen;

    @Schema(description = "关联对象缩略图（仅 relatedType=dish 返回，关联菜品主图，供前端卡片展示）")
    private String relatedImage;

    @Schema(description = "当前登录用户是否已点「有用」（未登录为 null/false）")
    private Boolean useful;

    @Schema(description = "审核状态：pending/approved/rejected")
    private String auditStatus;

    /** 仅作者本人/Admin 可见 */
    @Schema(description = "退回原因（仅作者/管理员可见）")
    private String rejectReason;

    @Schema(description = "有用数")
    private Integer usefulCount;

    @Schema(description = "评论数")
    private Integer commentCount;

    @Schema(description = "下架状态：0=正常 1=下架")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
