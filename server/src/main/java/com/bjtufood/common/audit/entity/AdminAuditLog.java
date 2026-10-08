package com.bjtufood.common.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端操作审计日志（表 {@code admin_audit_log}）。
 *
 * <p><b>只追加</b>：不提供修改 / 删除入口 —— 管理端是全站唯一具备不可逆破坏力
 * （物理删菜品 + 级联删评价）的入口，「进来后干了什么」必须可追溯、可举证。
 *
 * <p><b>不含请求体</b>：审计只记录「谁、在什么时间、对哪个资源、用什么方法、结果如何」。
 * 请求体可能含口令类字段，一律不入审计表。
 *
 * @see com.bjtufood.common.audit.AdminAuditRecorder 写入点
 */
@Data
@TableName("admin_audit_log")
public class AdminAuditLog {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人（{@code admin_account.id}） */
    private Long adminId;

    /** HTTP 方法（仅写操作落库：POST / PUT / DELETE / PATCH） */
    private String httpMethod;

    /** 应用内路径（已剥离 context-path），如 {@code /admin/dishes/12} */
    private String path;

    /** 目标资源 ID（自路径中提取的数字段；无则 null） */
    private String targetId;

    /** 结果：{@code success} / {@code fail:<HTTP 状态码>} */
    private String result;

    /** 来源 IP */
    private String ip;

    /** User-Agent（按列宽截断存储） */
    private String ua;

    /**
     * 变更前对象快照（JSON）。
     * <p>
     * 来源：Service 在改动前用 {@code AuditSnapshot.before(...)} 登记的对象现状 ——
     * 它回答的是「被删掉的那条记录原来长什么样」，是**误删后重建**的唯一依据。
     * <p>
     * 删除类端点只有本侧（删除后无对象）；未登记快照的端点为 {@code null}。
     */
    private String beforeValue;

    /** 变更后对象快照（JSON）；删除类无此侧，状态跃迁类有 */
    private String afterValue;

    /** 发生时间（**由 DB 时钟写入**：应用层不写、不填充） */
    private LocalDateTime createdAt;
}
