package com.bjtufood.auth.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TOTP 实现回归：以 RFC 6238 附录 B 的标准向量锁定算法口径。
 *
 * <p>之所以用标准向量而不是「自己算一遍再断言」：TOTP 的正确性只由「认证器 App 与我们算出同一个数」
 * 决定，标准向量是这一点的唯一外部证据 —— 自证式断言会把实现错误一起固化下来。
 */
class TotpGeneratorTest {

    /** RFC 6238 附录取用的种子（ASCII "12345678901234567890" 的 Base32） */
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    @DisplayName("RFC 6238 标准向量：59 秒 ⇒ 口令 287082（SHA1 / 6 位）")
    void matchesRfc6238Vector() {
        // RFC 用 8 位输出 94287082；本实现固定 6 位 ⇒ 取末 6 位 287082
        assertThat(TotpGenerator.hotp(RFC_SECRET, 59L / 30L)).isEqualTo("287082");
    }

    @Test
    @DisplayName("窗口内相邻时间步可校验通过；窗口外不通过")
    void verifiesWithinWindowOnly() {
        long now = 1_700_000_000L;
        String current = TotpGenerator.hotp(RFC_SECRET, now / TotpGenerator.STEP_SECONDS);
        String next = TotpGenerator.hotp(RFC_SECRET, now / TotpGenerator.STEP_SECONDS + 1);
        String farAway = TotpGenerator.hotp(RFC_SECRET, now / TotpGenerator.STEP_SECONDS + 5);

        assertThat(TotpGenerator.matchedStep(RFC_SECRET, current, now)).isEqualTo(now / 30);
        // 服务端慢一步（客户端已跳到下一步）不应误拒
        assertThat(TotpGenerator.matchedStep(RFC_SECRET, next, now)).isEqualTo(now / 30 + 1);
        assertThat(TotpGenerator.matchedStep(RFC_SECRET, farAway, now)).isEqualTo(-1L);
    }

    @Test
    @DisplayName("格式不符（位数不对 / 空 / 非数字）一律不通过，不抛异常")
    void rejectsMalformedInput() {
        long now = 1_700_000_000L;
        assertThat(TotpGenerator.matchedStep(RFC_SECRET, "12345", now)).isEqualTo(-1L);
        assertThat(TotpGenerator.matchedStep(RFC_SECRET, "1234567", now)).isEqualTo(-1L);
        assertThat(TotpGenerator.matchedStep(RFC_SECRET, "abcdef", now)).isEqualTo(-1L);
        assertThat(TotpGenerator.matchedStep(RFC_SECRET, null, now)).isEqualTo(-1L);
        assertThat(TotpGenerator.matchedStep("", "123456", now)).isEqualTo(-1L);
    }

    @Test
    @DisplayName("生成的密钥可被自身校验：Base32 往返 + otpauth URI 形态正确")
    void generatedSecretIsUsableAndUriIsWellFormed() {
        String secret = TotpGenerator.generateSecret();
        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");

        String code = TotpGenerator.hotp(secret, 100L);
        assertThat(code).hasSize(6).matches("\\d{6}");
        assertThat(TotpGenerator.matchedStep(secret, code, 100L * TotpGenerator.STEP_SECONDS)).isEqualTo(100L);

        String uri = TotpGenerator.buildOtpAuthUri("知行食记管理后台", "boss", secret);
        assertThat(uri).startsWith("otpauth://totp/")
                .contains("secret=" + secret)
                .contains("digits=6")
                .contains("period=30")
                .contains("algorithm=SHA1");
    }

    @Test
    @DisplayName("每次生成的密钥不同（不得复用固定密钥）")
    void secretsDifferPerCall() {
        assertThat(TotpGenerator.generateSecret()).isNotEqualTo(TotpGenerator.generateSecret());
    }
}
