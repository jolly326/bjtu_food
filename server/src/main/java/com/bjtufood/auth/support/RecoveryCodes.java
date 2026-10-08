package com.bjtufood.auth.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MFA 恢复码的生成与校验（设备丢失时的最后一条登录路径）。
 *
 * <p><b>为什么必须有</b>：TOTP 绑定在单一设备上，设备丢失 / 卸载认证器即永久无法登录 ——
 * 而管理端只可能通过「直连数据库」恢复，成本与风险都高。恢复码是这条兜底路径。
 *
 * <p><b>存储口径</b>：与口令同款 —— <b>只存哈希</b>（SHA-256 十六进制），明文仅在绑定成功时
 * 一次性下发；库被读走后拿到的是不可逆摘要，无法反推可用码。
 *
 * <p><b>比对口径</b>：一律 {@link MessageDigest#isEqual}（等时比较），
 * 不用 {@code String.equals}（首个字节不同即提前返回，可据响应时间差逐步试出码）。
 *
 * <p><b>字符集</b>：剔除易混淆字符（{@code 0/O}、{@code 1/I/L}）——
 * 恢复码需要用户从屏幕抄到纸上，混淆字符会直接导致「抄对了却登录不上」。
 */
public final class RecoveryCodes {

    /** 字符集：数字与大写字母，剔除 0 / O / 1 / I / L */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();

    /** 单个恢复码的有效字符数（10 位 ≈ 49 bit 熵，足够抵抗在线爆破；配合失败计数更宽裕） */
    private static final int CODE_LENGTH = 10;

    /** 分组长度（每 5 位一组，便于抄写） */
    private static final int GROUP_LENGTH = 5;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private RecoveryCodes() {
    }

    /**
     * 生成一批恢复码（明文形态，形如 {@code ABCDE-FGHJK}）。
     *
     * @param count 生成数量
     * @return 明文恢复码列表（**只应下发一次**，服务端仅保留哈希）
     */
    public static List<String> generate(int count) {
        List<String> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH + 1);
            for (int j = 0; j < CODE_LENGTH; j++) {
                if (j == GROUP_LENGTH) {
                    sb.append('-');
                }
                sb.append(ALPHABET[SECURE_RANDOM.nextInt(ALPHABET.length)]);
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    /**
     * 计算恢复码的存储摘要（SHA-256 十六进制）。
     *
     * @param code 明文恢复码（容忍大小写与分组连字符 / 空格）
     * @return 小写十六进制摘要；{@code code} 为空时返回 {@code null}
     */
    public static String hash(String code) {
        String normalized = normalize(code);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(HEX[(b >> 4) & 0x0F]).append(HEX[b & 0x0F]);
            }
            return sb.toString();
        } catch (Exception e) {
            // SHA-256 是 JDK 必备算法，走到这里说明运行环境异常 —— 抛出让上层按系统故障处理
            throw new IllegalStateException("恢复码摘要计算失败", e);
        }
    }

    /**
     * 等时比较：明文恢复码是否匹配存储摘要。
     *
     * @param code       用户输入的明文恢复码
     * @param storedHash 库中存储的摘要
     * @return 是否匹配
     */
    public static boolean matches(String code, String storedHash) {
        String candidate = hash(code);
        if (candidate == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    /** 归一化：去连字符 / 空格、转大写（用户抄写形态与存储形态解耦） */
    private static String normalize(String code) {
        if (code == null) {
            return "";
        }
        return code.replace("-", "")
                .replace(" ", "")
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}
