package com.bjtufood.common.utils;

import com.bjtufood.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * UGC 配图校验与序列化的公共工具（评价 / 反馈同一口径）。
 * <p>
 * 背景（PR-05 冗余清理）：{@code ReviewServiceImpl#encodeImages} 与
 * {@code FeedbackServiceImpl#encodeImages} 原为逐行近乎重复的实现（≤3 张 + COS 域名白名单 + JSON 序列化），
 * 抽为本工具类消除双份维护。
 * <p>
 * 规则：
 * <ol>
 *   <li>配图上限 {@value #MAX_IMAGES} 张（超限 400）；</li>
 *   <li>每项 trim 后去空白，空白项不计入；trim 后为空返回 {@code null}（不落库空数组）；</li>
 *   <li>每项必须为 {@link ImageUrlUtil#isValidCosUgcUrl} 认可的受信任 COS 绝对地址，
 *       防止 UGC 配图沦为任意 URL 载体；</li>
 *   <li>校验通过后序列化为 JSON 字符串落库（空入参返回 {@code null}）。</li>
 * </ol>
 * <p>
 * 错误文案由调用方以 {@code subject}（如「评价」「反馈」）参数化，保证各业务对外提示与既有行为完全一致。
 */
public final class UgcImageValidator {

    /** UGC 配图上限（张） */
    public static final int MAX_IMAGES = 3;

    private UgcImageValidator() {
    }

    /**
     * 校验并序列化 UGC 配图。
     *
     * @param images  待校验的配图 URL 列表（可空）
     * @param subject 业务主体名（如「评价」「反馈」），用于拼装对外错误文案
     * @param imageUrlUtil 图片 URL 工具（校验 COS 绝对地址）
     * @return 序列化后的 JSON 字符串；无有效配图时返回 {@code null}
     * @throws BusinessException 超过上限或含非法地址（400）
     */
    public static String encode(List<String> images, String subject, ImageUrlUtil imageUrlUtil) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        if (images.size() > MAX_IMAGES) {
            throw new BusinessException(subject + "配图最多 " + MAX_IMAGES + " 张");
        }
        List<String> normalized = images.stream().map(String::trim).filter(StringUtils::hasText).toList();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.size() > MAX_IMAGES) {
            throw new BusinessException(subject + "配图最多 " + MAX_IMAGES + " 张");
        }
        for (String url : normalized) {
            if (!imageUrlUtil.isValidCosUgcUrl(url)) {
                throw new BusinessException("图片地址不合法，请重新上传");
            }
        }
        return JsonListUtil.toJson(normalized);
    }
}
