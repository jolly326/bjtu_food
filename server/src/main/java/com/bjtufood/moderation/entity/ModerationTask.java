package com.bjtufood.moderation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端处置任务实体。
 * <p>
 * 对应数据库表：`moderation_task` —— 三类用户提交（意见反馈 / 评价举报 / 菜品问题反馈）的
 * <b>管理侧处置状态机</b>，按多态键 {@code (type, type_id)} 与工单表 1:1（唯一索引保证）。
 * <ul>
 *   <li><b>提交事务内预建</b>（{@code status=pending}）：「未处置」是显式行 ⇒ 管理端列表可按状态走索引，
 *       三类工单的待处置总数 = 本表单表 {@code COUNT}；</li>
 *   <li><b>单一文案列</b>：处置文案全部走 {@link #replyContent}（配 {@link #replyTitle} 作短标题），
 *       不采纳时把原因写进正文；</li>
 *   <li><b>{@link #outcome} 显式落列</b>：处置文案只有单一列（{@link #replyContent}），
 *       「通过 / 不采纳」必须以本列区分 ⇒ {@code status=handled} 时必填（应用层校验），
 *       {@code pending} 行为 NULL；</li>
 *   <li><b>{@link #handledBy} 记录处置人</b>（引用 `admin.id`，未处置为 NULL）。</li>
 * </ul>
 * 口径真源：docs/schema/moderation_task.md。
 */
@Data
@TableName("moderation_task")
@Schema(description = "管理端处置任务")
public class ModerationTask {

    @TableId(type = IdType.AUTO)
    @Schema(description = "任务ID")
    private Long id;

    /** 工单类型：feedback / report / correction（多态键前半段，无外键） */
    @Schema(description = "工单类型：feedback / report / correction")
    private String type;

    /** 工单 ID（多态键后半段：指向对应工单表的主键，无外键） */
    @Schema(description = "工单 ID（多态键后半段）")
    private Long typeId;

    /** 处置状态：pending / handled */
    @Schema(description = "处置状态：pending / handled")
    private String status;

    /** 处置结论：handled=通过 / rejected=不采纳（pending 为 NULL） */
    @Schema(description = "处置结论：handled=通过 / rejected=不采纳；未处置为 null")
    private String outcome;

    /** 处置回执短标题（与 `notification.reply_title` 同句两份快照） */
    @Schema(description = "处置回执短标题")
    private String replyTitle;

    /** 处置文案（不采纳时即为原因；与 `notification.reply_content` 同句两份快照） */
    @Schema(description = "处置文案（不采纳时即为原因）")
    private String replyContent;

    /** 处置人（引用 `admin.id`；未处置为 NULL） */
    @Schema(description = "处置人账号ID")
    private Long handledBy;

    /** 处置时间（未处置为 NULL） */
    @Schema(description = "处置时间")
    private LocalDateTime handledAt;

    /** 创建时间（DB 时钟：INSERT 由 `DEFAULT CURRENT_TIMESTAMP` 写入，应用层不写、不填充） */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
