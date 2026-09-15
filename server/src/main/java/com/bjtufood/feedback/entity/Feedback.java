package com.bjtufood.feedback.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户反馈实体类（升级后的 user_feedback 映射）
 * <p>
 * 对应数据库表：user_feedback
 */
@Data
@TableName("user_feedback")
@Schema(description = "用户反馈")
public class Feedback {

    @TableId(type = IdType.AUTO)
    @Schema(description = "反馈ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    /**
     * 反馈类型：写入口径仅 suggestion / add / error / report（FeedbackConst.WRITABLE_TYPES）；
     * 历史存量数据可含 bug / other（已下线，读取与后台筛选保持兼容）。
     */
    @Schema(description = "反馈类型：suggestion/add/error/report（历史可含 bug/other）")
    private String type;

    /**
     * 二级分类（DEV-01 补全落库，映射列 {@code user_feedback.sub}）：
     * 仅 {@code type=suggestion} 有效，值域 idea/problem（FeedbackConst.SUB_WRITE_WHITELIST）；
     * 其他 type 或不填时为 NULL。
     */
    @Schema(description = "二级分类（仅 suggestion 有效）：idea/problem，其余为 null")
    private String sub;

    @Schema(description = "反馈内容")
    private String content;

    /** 反馈配图 URL 列表 JSON（COS 绝对地址，≤3 张；落库为 JSON 字符串，见 JsonListUtil） */
    @Schema(description = "反馈配图URL列表JSON（COS 绝对地址，≤3 张）")
    private String images;

    /*
     * 内容安全状态 sec_state 已随「取消人工复核」（2026-09-15 用户拍板）全链退役：
     * 机检 pass/review 一律放行、risky 直接拒绝（不落库），反馈侧亦无安全态可存。
     */

    @Schema(description = "联系方式")
    private String contact;

    /** 关联类型：report 举报为 review（被举报评价）；error 信息纠错为 dish；其他反馈为 null */
    @Schema(description = "关联类型：举报为 review；信息纠错为 dish；其他为 null")
    private String relatedType;

    /** 关联对象ID（举报场景：被举报评价ID；信息纠错：菜品ID）；其他反馈为 null */
    @Schema(description = "关联对象ID（举报：评价ID；信息纠错：菜品ID）；其他为 null")
    private Long relatedId;

    /** 处理状态：pending / handled */
    @Schema(description = "处理状态：pending/handled")
    private String status;

    /** 管理员回复/处理说明 */
    @Schema(description = "管理员回复")
    private String reply;

    /**
     * 不采纳原因（§7.23 第 5 条）：处理结论为「不采纳/退回」时必填（1~200 字），
     * 随回执通知一并向已认证提交人展示；结论为通过/已处理时保持 NULL。
     * 对应列 user_feedback.reject_reason（由技术负责人在 schema.sql 幂等补列）。
     */
    @Schema(description = "不采纳原因（处理结论为不采纳/退回时必填，1~200 字）")
    private String rejectReason;

    /** 处理时间 */
    @Schema(description = "处理时间")
    private LocalDateTime handledAt;

    /** 处理人管理员ID */
    @Schema(description = "处理人管理员ID")
    private Long handlerId;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
