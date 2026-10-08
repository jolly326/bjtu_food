package com.bjtufood.auth.support;

/**
 * RFC 4648 Base32 编解码（TOTP 密钥的文本形态）。
 *
 * <p>认证器 App（Google Authenticator / Microsoft Authenticator 等）的密钥一律是 Base32 文本，
 * 故 TOTP 密钥在库与 otpauth URI 中都以此形态流转，仅在计算 HMAC 时解码为字节。
 *
 * <p>编解码口径：
 * <ul>
 *   <li>编码<b>不带</b> {@code =} 填充（认证器普遍接受无填充形态）；</li>
 *   <li>解码容忍填充符、大小写混写、空白与分组连字符（手输密钥时的常见形态）。</li>
 * </ul>
 */
final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private static final int[] LOOKUP = new int[128];

    static {
        java.util.Arrays.fill(LOOKUP, -1);
        for (int i = 0; i < ALPHABET.length(); i++) {
            char upper = ALPHABET.charAt(i);
            LOOKUP[upper] = i;
            LOOKUP[Character.toLowerCase(upper)] = i;
        }
    }

    private Base32() {
    }

    /** 编码为无填充 Base32 文本 */
    static String encode(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }

    /**
     * 解码 Base32 文本。
     *
     * @param text Base32 文本（容忍填充符 / 大小写 / 空白 / 连字符）
     * @return 解码后的字节
     * @throws IllegalArgumentException 文本含非法字符（密钥形态错误须尽早暴露，不得静默按空密钥计算）
     */
    static byte[] decode(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Base32 文本为空");
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '=' || c == '-' || Character.isWhitespace(c)) {
                continue;
            }
            int value = c < 128 ? LOOKUP[c] : -1;
            if (value < 0) {
                throw new IllegalArgumentException("Base32 文本含非法字符");
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }
}
