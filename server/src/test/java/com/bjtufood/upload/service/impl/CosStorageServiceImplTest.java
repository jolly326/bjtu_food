package com.bjtufood.upload.service.impl;

import com.bjtufood.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * COS 对象存储实现的「配置判据 + 入参校验」单测。
 * <p>
 * 本类的四项配置由 {@code @Value} 直接注入字段（生产由环境变量提供），无构造入参，
 * 故沿用仓库既有做法以 {@code ReflectionTestUtils} 注入等价配置
 * （同 {@code EmailCodeServiceImplTest} 对 {@code @Value} 字段的处理）。
 * <p>
 * 前置校验（未配置 / 空内容 / 扩展名）都在 {@code buildClient()} 之前，可在无 COS 凭据、
 * 无出网的前提下完整断言；而真正调用 COS 的转存链路（key 规则 / Content-Type / URL 拼装 /
 * {@code CosClientException} → 400）需要真实 COS 凭据与网络，且 {@code COSClient} 由实现内部
 * 构造、无注入点，故不在本类覆盖范围内 —— 测试不在任何路径上触发出网。
 */
class CosStorageServiceImplTest {

    private static final String MSG_NOT_CONFIGURED = "图片存储未配置";
    private static final String MSG_ILLEGAL_EXT = "仅支持 jpg、jpeg、png、webp 图片";

    private static CosStorageServiceImpl configured() {
        CosStorageServiceImpl cos = new CosStorageServiceImpl();
        ReflectionTestUtils.setField(cos, "bucket", "bjtu-food-1250000000");
        ReflectionTestUtils.setField(cos, "secretId", "secret-id");
        ReflectionTestUtils.setField(cos, "secretKey", "secret-key");
        ReflectionTestUtils.setField(cos, "region", "ap-beijing");
        return cos;
    }

    @Test
    @DisplayName("isConfigured：四项配置齐全 → true")
    void isConfigured_allPresent_true() {
        assertThat(configured().isConfigured()).isTrue();
    }

    @Test
    @DisplayName("isConfigured：任一项缺失 / 空白 → false（不静默降级）")
    void isConfigured_anyMissing_false() {
        for (String field : new String[]{"bucket", "secretId", "secretKey", "region"}) {
            CosStorageServiceImpl cos = configured();
            ReflectionTestUtils.setField(cos, field, "   ");
            assertThat(cos.isConfigured()).as("%s 缺失 ⇒ 未配置", field).isFalse();
        }
        assertThat(new CosStorageServiceImpl().isConfigured()).isFalse();
    }

    @Test
    @DisplayName("upload：未配置 → 400「图片存储未配置」（即便入参本身也不合法，配置判据优先）")
    void upload_notConfigured_rejected400() {
        CosStorageServiceImpl cos = new CosStorageServiceImpl();

        assertThatThrownBy(() -> cos.upload(new byte[]{1}, "gif"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MSG_NOT_CONFIGURED)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> cos.upload(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MSG_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("upload：图片内容为 null / 零字节 → 400「图片内容为空」")
    void upload_emptyContent_rejected400() {
        CosStorageServiceImpl cos = configured();

        assertThatThrownBy(() -> cos.upload(null, "png"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("图片内容为空")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> cos.upload(new byte[0], "png"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("图片内容为空");
    }

    @Test
    @DisplayName("upload：扩展名缺失 / 不在白名单 → 400「仅支持 jpg、jpeg、png、webp 图片」")
    void upload_illegalExtension_rejected400() {
        CosStorageServiceImpl cos = configured();

        for (String ext : new String[]{null, "", "gif", "bmp", "svg"}) {
            assertThatThrownBy(() -> cos.upload(new byte[]{1, 2, 3}, ext))
                    .as("扩展名 %s", ext)
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(MSG_ILLEGAL_EXT)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        }
    }
}
