package com.bjtufood.correction.constant;

import java.util.Set;

/**
 * 菜品信息纠错相关常量（状态值域/字段约束单一真源）。
 */
public interface CorrectionConst {

    /** 处理状态：待处理 */
    String STATUS_PENDING = "pending";
    /** 处理状态：已采纳（七字段已写回 dish） */
    String STATUS_ADOPTED = "adopted";
    /** 处理状态：已拒绝 */
    String STATUS_REJECTED = "rejected";

    /** 处理状态查询白名单（后台筛选入参校验用；不传 = 全部） */
    Set<String> QUERY_STATUSES = Set.of(STATUS_PENDING, STATUS_ADOPTED, STATUS_REJECTED);

    /** 处理结论（PUT /admin/corrections/{id} 即拒绝端点，固定 rejected，形态对齐 feedback handle） */
    String OUTCOME_REJECTED = "rejected";

    /** 提交的菜品名称最大长度（字，与 dish.name VARCHAR(64) 一致） */
    int NAME_MAX_LENGTH = 64;

    /** 提交的食堂名称最大长度（字，与 canteen.name VARCHAR(64) 一致） */
    int CANTEEN_NAME_MAX_LENGTH = 64;

    /** 提交的档口名称最大长度（字，与 stall.name VARCHAR(64) 一致） */
    int STALL_NAME_MAX_LENGTH = 64;

    /** 纠错配图上限（张，快照为全量图片集） */
    int IMAGE_MAX = 9;

    /** 不采纳原因最大长度（schema dish_correction.reject_reason VARCHAR(200)，与 feedback 口径一致） */
    int REJECT_REASON_MAX_LENGTH = 200;

    /** 采纳固定回复文案（落库 dish_correction.reply） */
    String ADOPT_REPLY = "已采纳，菜品信息已更新";
}
