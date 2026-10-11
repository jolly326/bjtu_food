package com.bjtufood.feedback.entity;

import java.time.LocalDateTime;

/**
 * 分类字典项（`feedback_option` / `report_option`）的实体契约。
 * <p>
 * 两张字典表结构同族，维护口径完全一致，故把「字典项的形状」抽成接口：
 * 字典服务实现只写一份规则（启停上下限、引用计数、改名免费），两张字典自动同口径。
 * <p>
 * 本接口只暴露字典项自身的列（`id` / `name` / `status` / `updated_at`），
 * 不含任何业务字段 —— 消费表（feedback / report）的差异由各字典服务的引用计数钩子表达。
 */
public interface OptionDictionaryItem {

    /** 选项 ID（历史工单的数据锚点） */
    Long getId();

    void setId(Long id);

    /** 中文名（可改，改名免费） */
    String getName();

    void setName(String name);

    /** 状态：on=启用 / off=停用 */
    String getStatus();

    void setStatus(String status);

    /** 更新时间（DB 时钟维护） */
    LocalDateTime getUpdatedAt();
}
