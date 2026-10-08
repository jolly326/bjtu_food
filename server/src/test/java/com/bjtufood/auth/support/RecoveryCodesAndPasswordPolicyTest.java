package com.bjtufood.auth.support;

import com.bjtufood.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 恢复码与口令强度策略的回归测试。
 */
class RecoveryCodesAndPasswordPolicyTest {

    // ==================== 恢复码 ====================

    @Test
    @DisplayName("恢复码只存哈希：摘要固定 64 位十六进制，且能按原文等时比对通过")
    void hashIsStableAndVerifiable() {
        List<String> codes = RecoveryCodes.generate(8);
        assertThat(codes).hasSize(8).doesNotHaveDuplicates();
        for (String code : codes) {
            assertThat(code).matches("[2-9A-HJ-NP-Z]{5}-[2-9A-HJ-NP-Z]{5}");
            String hash = RecoveryCodes.hash(code);
            assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
            assertThat(RecoveryCodes.matches(code, hash)).isTrue();
        }
    }

    @Test
    @DisplayName("比对容忍大小写与连字符（用户抄写形态不同不应登录失败），错误码不通过")
    void matchesToleratesHandWritingVariants() {
        List<String> codes = RecoveryCodes.generate(1);
        String code = codes.get(0);
        String hash = RecoveryCodes.hash(code);

        assertThat(RecoveryCodes.matches(code.toLowerCase(), hash)).isTrue();
        assertThat(RecoveryCodes.matches(code.replace("-", " "), hash)).isTrue();
        assertThat(RecoveryCodes.matches("AAAAA-AAAAA", hash)).isFalse();
        assertThat(RecoveryCodes.matches(null, hash)).isFalse();
    }

    @Test
    @DisplayName("剔除易混淆字符：生成结果不含 0 / O / 1 / I / L")
    void alphabetExcludesConfusableCharacters() {
        for (String code : RecoveryCodes.generate(50)) {
            assertThat(code.replace("-", "")).doesNotContain("0", "O", "1", "I", "L");
        }
    }

    // ==================== 口令强度 ====================

    @Test
    @DisplayName("强度策略：≥12 位且至少三类字符")
    void acceptsStrongPassword() {
        assertThatCode(() -> PasswordPolicy.assertStrong("Bjtu-food-2026")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.assertStrong("abcdEFGH1234")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("强度策略：长度不足 / 类别不足一律拒绝，文案可直接展示")
    void rejectsWeakPassword() {
        assertThatThrownBy(() -> PasswordPolicy.assertStrong("Bjtu-2026"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少 12 位");
        // 小写 + 符号两类（长度足够仍应拒绝：类别不足）
        assertThatThrownBy(() -> PasswordPolicy.assertStrong("bjtu-food-only"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少 3 类");
        assertThatThrownBy(() -> PasswordPolicy.assertStrong("123456789012"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少 3 类");
        assertThatThrownBy(() -> PasswordPolicy.assertStrong(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("类别计数：大写 / 小写 / 数字 / 符号各计一类")
    void countsCharacterClasses() {
        assertThat(PasswordPolicy.characterClasses("aA1!")).isEqualTo(4);
        assertThat(PasswordPolicy.characterClasses("aA1")).isEqualTo(3);
        assertThat(PasswordPolicy.characterClasses("aaaa")).isEqualTo(1);
        assertThat(PasswordPolicy.characterClasses("")).isZero();
    }
}
