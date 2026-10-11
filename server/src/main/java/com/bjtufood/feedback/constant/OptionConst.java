package com.bjtufood.feedback.constant;

import java.util.Set;

/**
 * 分类字典（`feedback_option` / `report_option`）约束单一真源。
 * <p>
 * 两张字典表结构同族（`id` / `name` / `status` / `updated_at`）、维护口径完全一致，
 * 故共用同一组不变量：字典 ID 是历史数据的**数据锚点**（改 `name` 免费、没有「改 ID」的端点），
 * 删除受引用约束，入口不得被配空。
 */
public interface OptionConst {

    /** 状态：启用 */
    String STATUS_ON = "on";
    /** 状态：停用 */
    String STATUS_OFF = "off";

    /** 状态白名单（启停入参校验用） */
    Set<String> STATUSES = Set.of(STATUS_ON, STATUS_OFF);

    /**
     * 启用条数上限（单选弹层可用性约束，非技术约束）。
     * <p>
     * 同一个分类字典里可并排展示的选项数量——超出后弹层需要滚动，选中成本上升。
     */
    int MAX_ENABLED = 8;

    /**
     * 启用条数下限：**至少保留 1 条启用**。
     * <p>
     * 提交入口（意见反馈 / 举报）依赖字典下发选项，把字典配空等于让用户无法提交。
     */
    int MIN_ENABLED = 1;

    /** 中文名最大长度（字） */
    int NAME_MAX_LENGTH = 32;
}
