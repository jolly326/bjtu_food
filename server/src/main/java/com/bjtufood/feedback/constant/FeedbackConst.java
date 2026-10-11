package com.bjtufood.feedback.constant;

/**
 * 意见反馈提交约束单一真源（值域由字典表下发，本类只留列宽 / 张数等结构约束）。
 * <p>
 * 「反馈类型」的值域由字典表下发（`feedback_option`，管理端可维护、免发版），
 * 提交时经 {@code FeedbackOptionService#isSubmittable} 校验命中启用项。
 */
public interface FeedbackConst {

    /** 反馈正文最大长度（字，与 `feedback.content` 列宽一致） */
    int CONTENT_MAX_LENGTH = 1000;

    /** 反馈配图上限（张）：全站 UGC 配图统一上限，见 {@code UgcImageValidator#MAX_IMAGES} */
    int IMAGE_MAX = 3;
}
