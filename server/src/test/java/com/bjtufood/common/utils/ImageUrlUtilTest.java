package com.bjtufood.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ImageUrlUtil#isValidAvatar(String)} 头像地址白名单口径。
 * <p>
 * 口径：只接受站内相对路径，或走 {@code POST /upload/cloud-image}（已过 {@code imgSecCheck} + COS 转存）
 * 产出的 COS 绝对地址；微信云存储 {@code cloud://} fileID 因绕过内容安检而拒绝。
 */
@DisplayName("ImageUrlUtil · 头像地址校验")
class ImageUrlUtilTest {

    private final ImageUrlUtil util = new ImageUrlUtil("http://localhost:8080/api/v1");

    @Test
    @DisplayName("接受站内相对路径与已过检的 COS 绝对地址")
    void acceptsTrustedSources() {
        assertThat(util.isValidAvatar("/images/seed/avatar.png")).isTrue();
        assertThat(util.isValidAvatar("/uploads/2026/avatar.jpg")).isTrue();
        assertThat(util.isValidAvatar(
                "https://bjtu-food-1250000000.cos.ap-beijing.myqcloud.com/ugc/20261005/a.jpg")).isTrue();
    }

    @Test
    @DisplayName("拒绝 cloud:// fileID、外部 http(s) 与空值")
    void rejectsUntrustedSources() {
        assertThat(util.isValidAvatar("cloud://bjtu-dev.6a6x/ugc/tmp.jpg")).isFalse();
        assertThat(util.isValidAvatar("https://evil.example.com/x.png")).isFalse();
        assertThat(util.isValidAvatar("http://localhost:8080/api/v1/images/a.png")).isFalse();
        assertThat(util.isValidAvatar(null)).isFalse();
        assertThat(util.isValidAvatar("   ")).isFalse();
    }
}
