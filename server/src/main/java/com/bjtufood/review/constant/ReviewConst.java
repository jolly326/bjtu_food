package com.bjtufood.review.constant;

import java.util.Set;

/**
 * 评价模块常量（排序等查询入参值域单一真源）。
 */
public interface ReviewConst {

    /** 排序：按「有用数」置顶（缺省口径，与 ReviewMapper.xml otherwise 分支一致） */
    String SORT_USEFUL = "useful";
    /** 排序：按发表时间倒序 */
    String SORT_LATEST = "latest";

    /** 公开评价列表排序白名单（P2-01 / PR-06：非法值 400，不再静默落 useful 分支） */
    Set<String> SORT_WHITELIST = Set.of(SORT_USEFUL, SORT_LATEST);
}
