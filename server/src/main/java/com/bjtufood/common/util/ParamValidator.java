package com.bjtufood.common.util;

import com.bjtufood.common.exception.BusinessException;

import java.util.Collection;

/**
 * 入参值域/白名单校验工具（PR-06：非法入参必须报错，不得静默降级）。
 * <p>
 * 统一产出 400 业务码（{@link BusinessException}，走既有 {@code GlobalExceptionHandler} 口径），
 * 避免各 Service 自行散写值域判断导致口径分叉。
 */
public final class ParamValidator {

    private ParamValidator() {
    }

    /**
     * 白名单校验（可空入参）：null / 空白视为「未传」直接放行（不修改/不过滤语义）；
     * 非空时去除首尾空白并小写归一化后必须命中白名单，否则抛 400。
     *
     * @param value     待校验值（可为 null）
     * @param allowed   白名单（单一真源，通常来自常量类）
     * @param fieldName 字段名（用于错误提示）
     * @return 归一化后的值（去空白并小写；未传/空白时为 null）
     */
    public static String optionalInWhitelist(String value, Collection<String> allowed, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        if (!allowed.contains(normalized)) {
            throw new BusinessException(400,
                    fieldName + "取值非法：" + value + "（允许：" + String.join("/", allowed) + "）");
        }
        return normalized;
    }

    /**
     * 白名单校验（必填入参）：null / 空白 → 400（缺失必填值）；非空时归一化后必须命中白名单。
     *
     * @return 归一化后的值（去空白并小写，非 null）
     */
    public static String requiredInWhitelist(String value, Collection<String> allowed, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(400, fieldName + "不能为空");
        }
        return optionalInWhitelist(value, allowed, fieldName);
    }
}
