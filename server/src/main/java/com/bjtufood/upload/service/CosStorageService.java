package com.bjtufood.upload.service;

/**
 * 腾讯云 COS 对象存储服务（UGC 配图最终存储，产品定稿 2026-09-13）。
 * <p>
 * URL 规则：{@code https://{bucket}.cos.{region}.myqcloud.com/{key}}
 * key 规则：{@code ugc/{yyyyMMdd}/{uuid}.{ext}}
 * <p>
 * COS 配置（COS_BUCKET / COS_SECRET_ID / COS_SECRET_KEY / COS_REGION）缺省任一为空时
 * {@link #isConfigured()} 返回 false，调用方返回明确业务错误「图片存储未配置」。
 */
public interface CosStorageService {

    /**
     * COS 是否已配置（四项环境变量全部非空）。
     */
    boolean isConfigured();

    /**
     * 上传图片二进制到 COS。
     *
     * @param data 图片二进制
     * @param ext  扩展名（不含点，如 jpg/png/webp；内部归一化小写）
     * @return COS 绝对 URL
     * @throws com.bjtufood.common.exception.BusinessException 未配置=400「图片存储未配置」；上传失败=400
     */
    String upload(byte[] data, String ext);
}
