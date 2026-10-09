package com.bjtufood.auth.support;

import com.bjtufood.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 管理端口令强度策略（≥12 位且含大写 / 小写 / 数字 / 符号中至少三类）。
 *
 * <p>校验入口唯一 = {@code POST /admin/auth/password}（改密端点）；账号由直连库登记，
 * 不存在「初始化设弱口令」的旁路，故此处即全部落地面。
 */
class PasswordPolicyTest {

    @Test
    @DisplayName("强度策略：≥12 位且至少三类字符")
    void acceptsStrongPassword() {
        assertThatCode(() -> PasswordPolicy.assertStrong("Bjtu-Food-2026")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("强度策略：长度不足 / 类别不足一律拒绝，文案可直接展示")
    void rejectsWeakPassword() {
        // 长度不足 12 位
        assertThatThrownBy(() -> PasswordPolicy.assertStrong("Bjtu-Fod-26"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("12");
        // 长度足够但只有小写 + 符号两类
        assertThatThrownBy(() -> PasswordPolicy.assertStrong("bjtu-food-only"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少 3 类");
    }

    @Test
    @DisplayName("类别计数：大写 / 小写 / 数字 / 符号各计一类")
    void countsCharacterClasses() {
        assertThatCode(() -> PasswordPolicy.assertStrong("Bjtu-Food-2026")).doesNotThrowAnyException();
    }
}
