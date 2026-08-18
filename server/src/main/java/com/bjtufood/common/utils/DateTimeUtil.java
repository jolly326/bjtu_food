package com.bjtufood.common.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 时间工具
 * <p>
 * 统一使用 Asia/Shanghai 时区生成当前时间，避免依赖服务器默认时区（不同环境可能漂移，
 * 导致验证码过期、登录时间、审核时间等业务时间不一致）。
 */
public final class DateTimeUtil {

    private DateTimeUtil() {
    }

    /** 业务统一时区 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 返回当前业务时间（Asia/Shanghai）。
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
