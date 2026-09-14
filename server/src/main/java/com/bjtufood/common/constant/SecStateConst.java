package com.bjtufood.common.constant;

/**
 * UGC 内容安全状态落库值域 —— 单一真源（RB15 收敛）。
 * <p>
 * 值域：{@code pass}（对外可见）/ {@code review}（机检待人工复核，仅作者本人可见）/
 * {@code rejected}（人工复核不通过，对外不可见）。
 * <p>
 * 与微信 msgSecCheck v2 输入枚举 {@code content.security.SecSuggest}（pass/review/risky）的区别：
 * SecSuggest 是「机检入参三态」，risky 会在 ContentSecurityService 内被拦截（不会落库）；
 * 本常量描述的是「落库/复核用」的 sec_state 值域，故人工复核不通过记为 rejected 而非 risky。
 * <p>
 * {@code ReviewServiceImpl} / {@code FeedbackServiceImpl} 原先各自定义一份同名常量，现统一引用本接口。
 */
public interface SecStateConst {

    /** 内容安全状态：通过（对外可见） */
    String PASS = "pass";

    /** 内容安全状态：待人工复核（对他端不可见，作者本人可见并提示「审核中」） */
    String REVIEW = "review";

    /** 内容安全状态：人工复核不通过（对外不可见） */
    String REJECTED = "rejected";
}
