package com.bjtufood.upload.service.impl;

import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.upload.service.CosStorageService;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.region.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 腾讯云 COS 对象存储服务实现。
 * <p>
 * COSClient 线程安全，实例级单例复用（连接池内置）；未配置四项环境变量时直接抛
 * 400「图片存储未配置」，绝不静默写本地（UGC 配图只有 COS 一种正式存储）。
 */
@Service
public class CosStorageServiceImpl implements CosStorageService {

    private static final Logger log = LoggerFactory.getLogger(CosStorageServiceImpl.class);

    /** UGC 配图 key 前缀 */
    private static final String KEY_PREFIX = "ugc";

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    @Value("${cos.bucket:}")
    private String bucket;

    @Value("${cos.secret-id:}")
    private String secretId;

    @Value("${cos.secret-key:}")
    private String secretKey;

    @Value("${cos.region:}")
    private String region;

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(bucket) && StringUtils.hasText(secretId)
                && StringUtils.hasText(secretKey) && StringUtils.hasText(region);
    }

    @Override
    public String upload(byte[] data, String ext) {
        if (!isConfigured()) {
            throw new BusinessException(400, "图片存储未配置");
        }
        if (data == null || data.length == 0) {
            throw new BusinessException(400, "图片内容为空");
        }
        String normalizedExt = ext == null ? null : ext.toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
        if (normalizedExt == null || !ALLOWED_EXTENSIONS.contains(normalizedExt)) {
            throw new BusinessException(400, "仅支持 jpg、jpeg、png、webp 图片");
        }

        String key = "%s/%s/%s.%s".formatted(
                KEY_PREFIX,
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                UUID.randomUUID(),
                normalizedExt);

        COSClient client = buildClient();
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(data.length);
            metadata.setContentType(contentTypeOf(normalizedExt));
            client.putObject(bucket, key, new java.io.ByteArrayInputStream(data), metadata);
        } catch (CosClientException e) {
            // CosServiceException（服务端错误）为其子类，一并在此兜底转业务 400
            log.error("COS 上传失败 key={} bucket={}", key, bucket, e);
            throw new BusinessException(400, "图片上传失败，请稍后重试");
        } finally {
            client.shutdown();
        }

        // URL 规则：https://{bucket}.cos.{region}.myqcloud.com/{key}
        String url = "https://%s.cos.%s.myqcloud.com/%s".formatted(bucket, region, key);
        log.info("COS 上传成功 key={} size={}", key, data.length);
        return url;
    }

    /**
     * 构建 COSClient。当前实现按次构建并 shutdown（UGC 配图低频写，成本可接受）；
     * 若未来图量上升，可改为实例级缓存复用（COSClient 线程安全）。
     */
    private COSClient buildClient() {
        COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig config = new ClientConfig(new Region(region));
        config.setHttpProtocol(HttpProtocol.https);
        return new COSClient(credentials, config);
    }

    /** 扩展名 → Content-Type（COS 侧按类型分发/预览） */
    private String contentTypeOf(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }
}
