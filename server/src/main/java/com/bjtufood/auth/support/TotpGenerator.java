package com.bjtufood.auth.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * TOTP（RFC 6238，基于 HOTP / RFC 4226）实现 —— 管理端登录的第二因子。
 *
 * <p><b>为什么是 TOTP</b>：管理端是「单人低频」场景，口令一旦泄露（钓鱼 / 肩窥 / 复用）
 * 攻击者即可拿到不可逆的破坏力。TOTP 的第二因子让「知道口令」不再等于「拿到 token」，
 * 而接入成本极低（认证器 App 免费、无短信通道成本与可达性问题）。
 *
 * <p><b>参数</b>：HMAC-SHA1 · 6 位数字 · 30 秒步长 —— 主流认证器的默认约定，
 * 不做自定义（自定义会让 App 侧无法直接扫码使用）。
 *
 * <p><b>校验窗口</b>：单侧 {@value #DEFAULT_WINDOW} 步（即前后各 30 秒），
 * 用于容忍客户端与服务端的时钟偏差；窗口再放大只会成倍增加可猜空间。
 *
 * <p><b>防重放</b>：认证器在一个时间步内产出的口令是<b>同一个</b> —— 攻击者若截获到
 * 「刚刚用过」的口令，在窗口内重放即可二次登录。故校验结果除「是否匹配」外还必须回传
 * <b>命中的时间步</b>，由调用方记录并在下次校验时拒绝「不晚于已用步」的口令。
 */
public final class TotpGenerator {

    /** HMAC 算法：认证器生态的既有约定（SHA1 在 RFC 6238 中即默认算法） */
    private static final String HMAC_ALGORITHM = "HmacSHA1";

    /** 口令位数 */
    private static final int DIGITS = 6;

    /** 口令模数 */
    private static final int MODULO = 1_000_000;

    /** 时间步长（秒） */
    public static final long STEP_SECONDS = 30L;

    /** 默认容忍的时钟偏差步数（前后各 1 步 = ±30 秒） */
    public static final int DEFAULT_WINDOW = 1;

    /** 密钥长度：160 位（HOTP 建议值，也是认证器的常见值） */
    private static final int SECRET_BYTES = 20;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TotpGenerator() {
    }

    /**
     * 生成新的 TOTP 密钥（Base32 文本，可直接录入或写入 otpauth URI）。
     *
     * @return {@value #SECRET_BYTES} 字节随机密钥的 Base32 文本
     */
    public static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base32.encode(bytes);
    }

    /**
     * 生成认证器扫码用的 otpauth URI。
     *
     * @param issuer  签发方名称（认证器中的分组名）
     * @param account 账号标识（认证器中显示的条目名）
     * @param secret  Base32 密钥
     * @return {@code otpauth://totp/...} URI
     */
    public static String buildOtpAuthUri(String issuer, String account, String secret) {
        String label = urlEncode(issuer + ":" + account);
        return "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /**
     * 校验口令并返回<b>命中的时间步</b>（防重放判据）。
     *
     * <p>逐一比对窗口内的各个时间步，<b>不使用提前返回</b>式短路比较口令字符串 ——
     * 六个数字的枚举空间极小，真正的防护是「窗口小 + 失败计数 + 一次性」，
     * 此处只需保证「匹配到哪一步」这一信息准确。
     *
     * @param secret Base32 密钥
     * @param code   用户输入的 6 位口令
     * @return 命中的时间步；不匹配返回 {@code -1}
     */
    public static long matchedStep(String secret, String code) {
        return matchedStep(secret, code, System.currentTimeMillis() / 1000L);
    }

    /**
     * 按指定时刻校验（供测试注入时钟）。
     *
     * @param secret        Base32 密钥
     * @param code          用户输入的 6 位口令
     * @param epochSeconds  当前时刻（秒）
     * @return 命中的时间步；不匹配返回 {@code -1}
     */
    public static long matchedStep(String secret, String code, long epochSeconds) {
        if (secret == null || secret.isEmpty() || code == null) {
            return -1L;
        }
        String normalized = code.trim();
        if (normalized.length() != DIGITS) {
            return -1L;
        }
        long currentStep = epochSeconds / STEP_SECONDS;
        for (long step = currentStep - DEFAULT_WINDOW; step <= currentStep + DEFAULT_WINDOW; step++) {
            if (step < 0) {
                continue;
            }
            if (normalized.equals(hotp(secret, step))) {
                return step;
            }
        }
        return -1L;
    }

    /** 计算指定时间步的口令 */
    static String hotp(String secret, long step) {
        byte[] key = Base32.decode(secret);
        byte[] counter = ByteBuffer.allocate(8).putLong(step).array();
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(counter);
            // RFC 4226 动态截断：取末字节低 4 位作为偏移，自该偏移取 4 字节并抹去符号位
            int offset = digest[digest.length - 1] & 0x0F;
            int binary = ((digest[offset] & 0x7F) << 24)
                    | ((digest[offset + 1] & 0xFF) << 16)
                    | ((digest[offset + 2] & 0xFF) << 8)
                    | (digest[offset + 3] & 0xFF);
            return String.format("%0" + DIGITS + "d", binary % MODULO);
        } catch (Exception e) {
            // 密钥非法（非 Base32）属于配置/请求数据错误：按「校验不通过」处理，不泄露内部细节
            return "";
        }
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
