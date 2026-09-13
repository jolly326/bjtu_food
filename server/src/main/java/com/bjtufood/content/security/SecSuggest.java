package com.bjtufood.content.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 微信内容安全检测结果（msgSecCheck v2 的 result.suggest 三态，产品定稿 2026-09-13）。
 * <p>
 * 判定必须以 {@code result.suggest} 为准（pass/review/risky），不得只看 errcode——
 * errcode=0 仅代表接口调用成功，不代表内容合规。
 */
public enum SecSuggest {

    /** 通过（对外可见） */
    PASS("pass"),

    /** 待人工复核（机检存疑；对他端不可见，作者本人可见并提示「审核中」） */
    REVIEW("review"),

    /** 违规（统一由 ContentSecurityService 拦截，不落库对外） */
    RISKY("risky");

    private final String value;

    SecSuggest(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /** 微信 suggest 字符串 → 枚举；未识别值按最严格态 RISKY 处理（fail-closed） */
    @JsonCreator
    public static SecSuggest fromValue(String value) {
        if (value == null || value.isBlank()) {
            return RISKY;
        }
        return switch (value.trim().toLowerCase()) {
            case "pass" -> PASS;
            case "review" -> REVIEW;
            // risky 及微信未来新增的未知态一律按违规处理，宁可误拦不放行
            default -> RISKY;
        };
    }
}
