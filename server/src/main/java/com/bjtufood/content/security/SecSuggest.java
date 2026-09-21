package com.bjtufood.content.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 微信内容安全检测结果（msgSecCheck v2 的 result.suggest，产品定稿 2026-09-13 / 2026-09-15 归一）。
 * <p>
 * 判定必须以 {@code result.suggest} 为准（pass/review/risky），不得只看 errcode——
 * errcode=0 仅代表接口调用成功，不代表内容合规。
 * <p>
 * 生效语义（2026-09-15 用户拍板「取消人工复核」）：
 * <ul>
 *   <li>{@link #PASS} = <b>放行</b>（含内容安全检测 review 疑似态归一）；</li>
 *   <li>{@link #RISKY} = <b>拒绝</b>（违规）。</li>
 * </ul>
 * 内容安全检测 review（疑似）不再产生「待复核」语义：由 {@link #fromValue} 在判定入口归一为 PASS，
 * 故本枚举对外实际只有「放行 / 拒绝」二态。{@link #REVIEW} 保留仅用于对应微信原始三态值域。
 */
public enum SecSuggest {

    /** 放行（含内容安全检测 review 态归一；无人工复核、无落库安全态） */
    PASS("pass"),

    /**
     * 微信原始「疑似」态。2026-09-15 起取消人工复核，本态一律归一为 {@link #PASS}（放行），
     * 不再由本枚举对外产生（保留以对应微信 suggest 值域，避免上游语义映射丢失）。
     */
    REVIEW("review"),

    /** 拒绝（违规；统一由 ContentSecurityService 拦截为 400，不落库） */
    RISKY("risky");

    private final String value;

    SecSuggest(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 微信 suggest 字符串 → 生效语义（2026-09-15 归一：{@code review} 视为放行）。
     * <p>
     * <ul>
     *   <li>{@code pass} / {@code review} → {@link #PASS}（放行；review 为内容安全检测疑似，取消人工复核后直接放行）；</li>
     *   <li>{@code risky} 及微信未来新增的未知/缺失态 → {@link #RISKY}（拒绝，fail-closed，宁可误拦不放行）。</li>
     * </ul>
     */
    @JsonCreator
    public static SecSuggest fromValue(String value) {
        if (value == null || value.isBlank()) {
            return RISKY;
        }
        return switch (value.trim().toLowerCase()) {
            case "pass" -> PASS;
            // 2026-09-15 用户拍板「取消人工复核」：review（疑似）不再进人工队列，直接视为放行
            case "review" -> PASS;
            default -> RISKY;
        };
    }
}
