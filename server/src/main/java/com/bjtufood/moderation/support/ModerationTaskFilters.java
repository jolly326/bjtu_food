package com.bjtufood.moderation.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 处置任务筛选条件构造器（**跨域只读查询契约**）。
 *
 * <p><b>为什么由本域提供</b>：工单表（feedback / report / correction）**只存提交事实**，
 * 「是否已处置」「处置文案是什么」全在 {@code moderation_task}。管理端列表既要按处置状态筛选，
 * 又要按处置文案做关键词匹配，而这两列的 SQL 知识属本域 —— 故由本类提供条件片段，
 * 工单属主域只说「按处置状态筛我的行」，无需（也不许）自己拼本域表名与列名。
 *
 * <p><b>为何用 EXISTS 而不是 JOIN</b>：多态键 {@code (type, type_id)} 无外键、无法建 ORM 关联，
 * 且列表分页必须落在工单表上（保留分页壳与工单表排序）。{@code EXISTS} 相关子查询在
 * {@code (type, type_id)} 唯一索引上可走索引，语义与 JOIN 等价，同时不改变工单查询的行数与投影。
 *
 * <p><b>参数占位符</b>：片段里的 {@code {0}} / {@code {1}} 由 MyBatis-Plus 在
 * {@code apply(sql, values...)} 时替换为预编译占位符（下标即 {@code values} 的序号）。
 * 同一片段里复用同一下标是安全的（替换为同一个占位符与同一份参数值）。
 *
 * <p><b>入参安全性</b>：工单类型、状态与关键词一律走占位符；{@code workOrderIdColumn}
 * 由调用方以**本域常量**传入（如 {@code "feedback.id"}），**从不来自请求**，故不存在注入面。
 */
public final class ModerationTaskFilters {

    /** 处置任务表名（本域知识，不外泄给调用方） */
    private static final String TASK_TABLE = "moderation_task";

    private ModerationTaskFilters() {
    }

    /**
     * 按处置状态筛选工单（{@code pending} / {@code handled}）。
     *
     * @param wrapper           工单表的查询构造器
     * @param type              工单类型（{@code ModerationConst.TYPE_*}，占位符 {@code {0}}）
     * @param workOrderIdColumn 工单表主键列（带表名限定，如 {@code "feedback.id"}；由调用方常量传入）
     * @param status            处置状态（占位符 {@code {1}}；已由调用方做白名单校验）
     */
    public static <T> void byStatus(LambdaQueryWrapper<T> wrapper, String type,
                                    String workOrderIdColumn, String status) {
        wrapper.apply(statusCondition(workOrderIdColumn), type, status);
    }

    /**
     * 处置状态条件片段（供调用方把它 AND 进自己的条件组）。
     * <p>占位符取值顺序：{@code 0}=工单类型，{@code 1}=处置状态。
     */
    public static String statusCondition(String workOrderIdColumn) {
        return "EXISTS (SELECT 1 FROM " + TASK_TABLE + " t WHERE t.type = {0}"
                + " AND t.type_id = " + workOrderIdColumn + " AND t.status = {1})";
    }

    /**
     * 处置文案条件片段（模糊匹配 {@code reply_content}）。
     * <p>占位符取值顺序：{@code 0}=工单类型，{@code 1}=关键词 LIKE 模式。
     * <p>用途：管理端列表的关键词口径是「提交正文**或**处置文案」——
     * 调用方把本片段与自身的正文条件用 {@code OR} 组合在同一组括号内。
     */
    public static String replyKeywordCondition(String workOrderIdColumn) {
        return "EXISTS (SELECT 1 FROM " + TASK_TABLE + " t WHERE t.type = {0}"
                + " AND t.type_id = " + workOrderIdColumn + " AND t.reply_content LIKE {1})";
    }

    /** 关键词 LIKE 模式（两侧通配） */
    public static String likePattern(String keyword) {
        return "%" + keyword + "%";
    }
}
