package com.bjtufood.auth.support;

import com.bjtufood.common.exception.BusinessException;

/**
 * 管理员口令强度策略（真源 {@code docs/secur/web/防爆破与限流.md} §3）。
 *
 * <p><b>口径</b>：长度 ≥{@value #MIN_LENGTH} 位，且「大写 / 小写 / 数字 / 符号」四类中
 * <b>至少 {@value #REQUIRED_CHARACTER_CLASSES} 类</b>。
 *
 * <p><b>为什么不更强</b>：口令强度的最终防线是 MFA（第二因子）而非复杂度 ——
 * 强制特殊字符组合只会把人推向「写在便签上」，收益为负。此处的下限只用来挡
 * 「短口令 + 字典词」这一档离线可破的形态。
 *
 * <p><b>校验位置</b>：服务端。前端提示只是体验，绕过前端直接调接口同样被本策略拦下。
 */
public final class PasswordPolicy {

    /** 口令最小长度 */
    public static final int MIN_LENGTH = 12;

    /** 口令最大长度（与 DTO 的 {@code @Size} 一致，避免 BCrypt 单次计算成本过高） */
    public static final int MAX_LENGTH = 128;

    /** 须满足的字符类别数 */
    public static final int REQUIRED_CHARACTER_CLASSES = 3;

    private PasswordPolicy() {
    }

    /**
     * 校验口令是否满足强度要求，不满足即抛出业务异常（文案为可直接展示给用户的最终形态）。
     *
     * @param password 明文口令
     * @throws BusinessException 不满足强度要求
     */
    public static void assertStrong(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new BusinessException("新密码至少 " + MIN_LENGTH + " 位");
        }
        if (password.length() > MAX_LENGTH) {
            throw new BusinessException("新密码长度超限");
        }
        int classes = characterClasses(password);
        if (classes < REQUIRED_CHARACTER_CLASSES) {
            throw new BusinessException("新密码需包含大写字母、小写字母、数字、符号中的至少 "
                    + REQUIRED_CHARACTER_CLASSES + " 类");
        }
    }

    /**
     * 统计口令命中的字符类别数（大写 / 小写 / 数字 / 符号）。
     *
     * @param password 明文口令
     * @return 命中的类别数（0~4）
     */
    public static int characterClasses(String password) {
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean symbol = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isUpperCase(c)) {
                upper = true;
            } else if (Character.isLowerCase(c)) {
                lower = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            } else {
                symbol = true;
            }
        }
        return (upper ? 1 : 0) + (lower ? 1 : 0) + (digit ? 1 : 0) + (symbol ? 1 : 0);
    }
}
