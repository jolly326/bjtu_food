package com.bjtufood.upload.service.impl;

import com.bjtufood.common.config.UploadProperties;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.moderation.service.ContentSecurityService;
import com.bjtufood.wechat.config.WechatProperties;
import com.bjtufood.wechat.constant.WechatApiConst;
import com.bjtufood.wechat.service.WechatAccessTokenProvider;
import com.bjtufood.upload.dto.UploadResultVO;
import com.bjtufood.upload.service.CosStorageService;
import com.bjtufood.upload.service.UploadService;
import com.bjtufood.upload.support.ImageDimensionChecker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class UploadServiceImpl implements UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadServiceImpl.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    /** COS 临时下载链接有效期（秒）= 2h，与微信 batchdownloadfile 文档一致 */
    private static final int COS_DOWNLOAD_URL_MAX_AGE_SECONDS = 7200;

    /** 单图大小上限 5MB（与 spring.servlet.multipart.max-file-size 一致，服务层再兜底一次） */
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    /** 云存储配图下载超时（毫秒）：拉临时链接后从微信云存储 CDN 下载，较内容安全检测接口放宽 */
    private static final int CLOUD_DOWNLOAD_TIMEOUT_MS = 10_000;

    /** 云存储批量下载端点（平台常量归位 wechat 域，避免与 {@code WechatApiConst} 重复定义） */
    private static final String BATCH_DOWNLOAD_URL = WechatApiConst.BATCH_DOWNLOAD_URL;

    /** 微信响应固定以 text/plain 返回，统一先取 String 再手工反序列化（不依赖 Content-Type） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ImageUrlUtil imageUrlUtil;
    private final ContentSecurityService contentSecurityService;
    private final CosStorageService cosStorageService;

    /**
     * 微信 access_token 凭据提供方。
     * <p>
     * 平台凭据由 {@code wechat} 域独立提供，本类<b>只依赖接口</b> ——
     * 「上传域」不依赖「内容安全服务」拿凭据（依赖方向正确）。
     */
    private final WechatAccessTokenProvider tokenProvider;

    /** 云存储图片下载专用 RestTemplate（实例化一次复用；仅 batchdownloadfile / 临时链接下载两个出网点） */
    private final RestTemplate cloudRestTemplate;

    /**
     * 本地存储配置（根目录 + URL 前缀），与 {@code WebMvcConfig} 的静态资源映射**共用同一份**——
     * 两处各自绑定同一对键会在改一处忘另一处时「存得进去、访问不到」。
     */
    private final UploadProperties uploadProperties;

    /**
     * 微信云开发环境 ID；缺省时从 fileID 自动解析（cloud://{env}.{bucket}/path）。
     * <p>
     * 归 {@code wechat} 域的 {@code WechatProperties}：同一键只此一处绑定（不留第二处真源）。
     */
    private final WechatProperties wechatProperties;

    /**
     * Spring 装配入口：云存储下载用的 RestTemplate 走 {@link #newCloudRestTemplate()} 默认超时配置。
     */
    @Autowired
    public UploadServiceImpl(ImageUrlUtil imageUrlUtil,
                             ContentSecurityService contentSecurityService,
                             CosStorageService cosStorageService,
                             WechatAccessTokenProvider tokenProvider,
                             UploadProperties uploadProperties,
                             WechatProperties wechatProperties) {
        this(imageUrlUtil, contentSecurityService, cosStorageService, tokenProvider,
                newCloudRestTemplate(), uploadProperties, wechatProperties);
    }

    /**
     * 测试可注入构造：允许传入由 {@code MockRestServiceServer} 绑定的 RestTemplate，脚本化出网响应。
     * 生产装配走 {@link #UploadServiceImpl(ImageUrlUtil, ContentSecurityService, CosStorageService,
     * WechatAccessTokenProvider, UploadProperties, WechatProperties)}。
     */
    public UploadServiceImpl(ImageUrlUtil imageUrlUtil,
                             ContentSecurityService contentSecurityService,
                             CosStorageService cosStorageService,
                             WechatAccessTokenProvider tokenProvider,
                             RestTemplate cloudRestTemplate,
                             UploadProperties uploadProperties,
                             WechatProperties wechatProperties) {
        this.imageUrlUtil = imageUrlUtil;
        this.contentSecurityService = contentSecurityService;
        this.cosStorageService = cosStorageService;
        this.tokenProvider = tokenProvider;
        this.cloudRestTemplate = cloudRestTemplate;
        this.uploadProperties = uploadProperties;
        this.wechatProperties = wechatProperties;
    }

    // ==================== 链路一：multipart 直传（保留，H5/独立服务器场景） ====================

    @Override
    public UploadResultVO uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("图片大小不能超过 5MB");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("仅支持 jpg、jpeg、png、webp 图片");
        }

        // 文件头 magic number 校验：防止扩展名伪造的恶意文件（如将 .exe 改名为 .png）
        try (java.io.InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (!isImageMagic(header, read)) {
                throw new BusinessException("文件内容不是合法的图片");
            }
        } catch (IOException e) {
            throw new BusinessException("文件读取失败");
        }

        // COS 已配置：UGC 配图统一转存 COS（绝对 URL，不做本地缩略图/像素炸弹解码）
        if (cosStorageService.isConfigured()) {
            byte[] data;
            try {
                data = file.getBytes();
            } catch (IOException e) {
                throw new BusinessException("文件读取失败");
            }
            String cosUrl = cosStorageService.upload(data, extension.toLowerCase(Locale.ROOT));
            // 管理端 web（api/upload.ts）约定：上传结果必须同时含 url 与 relativeUrl，否则前端抛
            // 「上传接口返回缺少图片地址」。COS 场景下二者同为绝对 URL——落库用 relativeUrl，
            // 两端展示层（web toAbsoluteImageUrl / 小程序 getImageUrl）对 http(s) 绝对地址原样返回。
            return new UploadResultVO(cosUrl, cosUrl);
        }

        // COS 未配置：降级本地磁盘存储（开发环境无 COS 仍可用）
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String normalizedExt = extension.toLowerCase(Locale.ROOT);
        String filename = UUID.randomUUID() + "." + normalizedExt;
        Path dir = Paths.get(uploadProperties.getPath(), datePath).toAbsolutePath().normalize();

        Path target = dir.resolve(filename);
        try {
            Files.createDirectories(dir);
            // transferTo 内部由 Spring 负责流的开启与关闭（try-with-resources 语义），此处不手工持有流
            file.transferTo(target);
        } catch (IOException | RuntimeException e) {
            // 落盘中途失败可能留下半成品文件，主动清理避免磁盘垃圾
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // 清理失败不影响主流程错误返回
            }
            throw new BusinessException("图片上传失败");
        }

        // 图像炸弹防护（P1-6）：全图解码前先解析头部宽高，超 4000×4000 直接拒绝并清理原图，
        // 防止小体积高分辨率图片在 ImageIO 解码时耗尽堆内存（仅本地落盘链路需要，COS 链路不解码全图）
        try {
            ImageDimensionChecker.validate(target, normalizedExt);
        } catch (BusinessException e) {
            deleteQuietly(target);
            throw e;
        }

        String relativeUrl = uploadProperties.urlPrefixWithoutTrailingSlash() + "/" + datePath + "/" + filename;
        String absoluteUrl = imageUrlUtil.toAbsoluteUrl(relativeUrl);

        return new UploadResultVO(absoluteUrl, relativeUrl);
    }

    // ==================== 链路二：小程序云存储 fileID 转存（UGC 配图主链路） ====================

    @Override
    public UploadResultVO uploadCloudImage(String fileId) {
        if (!StringUtils.hasText(fileId) || !fileId.startsWith("cloud://")) {
            throw new BusinessException("fileId 不合法，必须为微信云存储 cloud:// 文件标识");
        }

        // 1. batchdownloadfile 用 fileID 换取临时下载链接
        String downloadUrl = fetchCloudDownloadUrl(fileId);

        // 2. 下载图片二进制 —— 大小兜底即在此完成：readCapped 边读边截断，
        // 超过 1MB（imgSecCheck 硬限制）直接抛 400，返回字节恒 ≤1MB（全链路唯一 1MB 兜底点）
        byte[] data = downloadImage(downloadUrl);

        // 3. magic number 格式兜底（jpg/png/webp），扩展名按真实内容推断
        String ext = detectImageExt(data);

        // 4. imgSecCheck 内容安全检测（87014 → 400「图片包含违规内容，无法上传」）
        contentSecurityService.checkImage(data);

        // 5. 转存 COS（key: ugc/{yyyyMMdd}/{uuid}.{ext}），返回绝对 URL
        String url = cosStorageService.upload(data, ext);
        return new UploadResultVO(url, null);
    }

    /**
     * batchdownloadfile：fileID → 临时下载链接（POST JSON，env 缺省时从 fileID 解析）。
     * <p>
     * token 失效自愈（对齐 {@code moderation.service.impl.ContentSecurityServiceImpl} 的 BE-06 模式）：
     * 单次调用遇 40001（invalid credential）/ 42001（access_token expired）时，
     * 先清空 stable_token 缓存再重试一次（重试时 {@code tokenProvider.get()} 会重新拉取）；
     * 重试仍失败则 fail-closed 抛 500。
     */
    private String fetchCloudDownloadUrl(String fileId) {
        String env = resolveCloudEnv(fileId);
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return doFetchCloudDownloadUrl(env, fileId);
            } catch (WechatTokenInvalidException e) {
                if (attempt == 2) {
                    log.error("刷新 access_token 后仍返回失效 errcode={}，fail-closed", e.errcode);
                    throw new BusinessException(500, "云存储服务暂不可用，请稍后重试");
                }
                log.warn("batchdownloadfile access_token 失效（errcode={}），清空缓存后重试一次", e.errcode);
                tokenProvider.invalidate();
            }
        }
        // 循环至多两轮：第二轮要么 return 要么抛出，此分支理论上不可达（防御性兜底）
        throw new BusinessException(500, "云存储服务暂不可用，请稍后重试");
    }

    /** 单次 batchdownloadfile 调用：取 token → 请求 → 判 errcode → 解析临时下载链接 */
    private String doFetchCloudDownloadUrl(String env, String fileId) {
        String url = wechatProperties.api(BATCH_DOWNLOAD_URL)
                + (wechatProperties.isInternalCall() ? "" : "?access_token=" + tokenProvider.get());

        Map<String, Object> reqBody = Map.of(
                "env", env,
                "file_list", List.of(Map.of("fileid", fileId, "max_age", COS_DOWNLOAD_URL_MAX_AGE_SECONDS)));

        String body;
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("User-Agent", WechatApiConst.USER_AGENT);
            body = cloudRestTemplate.postForObject(url,
                    new org.springframework.http.HttpEntity<>(OBJECT_MAPPER.writeValueAsString(reqBody), headers),
                    String.class);
        } catch (ResourceAccessException e) {
            log.error("batchdownloadfile 上游不可达（fileId={}）", fileId, e);
            throw new BusinessException(500, "云存储服务暂不可用，请稍后重试");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("batchdownloadfile 调用失败（fileId={}）", fileId, e);
            throw new BusinessException(400, "云存储文件获取失败，请重试");
        }

        Map<String, Object> resp = parseJsonBody(body);
        Integer errcode = asInt(resp.get("errcode"));
        if (errcode != null && errcode != 0) {
            if (isTokenInvalidErrcode(errcode)) {
                // token 失效类 errcode：向上抛内部标记，由 fetchCloudDownloadUrl 清缓存重试
                throw new WechatTokenInvalidException(errcode);
            }
            log.error("batchdownloadfile 失败 errcode={} errmsg={}", errcode, resp.get("errmsg"));
            throw new BusinessException(400, "云存储文件获取失败，请重试");
        }

        Object fileListRaw = resp.get("file_list");
        if (!(fileListRaw instanceof List<?> fileList) || fileList.isEmpty()
                || !(fileList.get(0) instanceof Map<?, ?> item)) {
            throw new BusinessException(400, "云存储文件不存在或已删除");
        }
        Integer status = asInt(item.get("status"));
        if (status != null && status != 0) {
            log.warn("batchdownloadfile 文件拉取失败 fileId={} status={} errmsg={}", fileId, status, item.get("errmsg"));
            throw new BusinessException(400, "云存储文件不存在或已删除");
        }
        String downloadUrl = item.get("download_url") == null ? null : String.valueOf(item.get("download_url"));
        if (!StringUtils.hasText(downloadUrl)) {
            throw new BusinessException(400, "云存储文件不存在或已删除");
        }
        return downloadUrl;
    }

    /**
     * token 失效类 errcode（微信官方定义）：
     * 40001 = invalid credential / access_token 无效；42001 = access_token timeout。
     */
    private static boolean isTokenInvalidErrcode(int errcode) {
        return errcode == 40001 || errcode == 42001;
    }

    /**
     * token 失效信号（内部标记异常，由 {@link #fetchCloudDownloadUrl} 捕获并触发清缓存重试）。
     */
    private static final class WechatTokenInvalidException extends RuntimeException {
        private final int errcode;

        private WechatTokenInvalidException(int errcode) {
            super("wechat token invalid: " + errcode);
            this.errcode = errcode;
        }
    }

    /** 解析云开发环境 ID：配置优先，缺省从 fileID（cloud://{env}.{bucket}/path）解析 */
    private String resolveCloudEnv(String fileId) {
        if (StringUtils.hasText(wechatProperties.getCloudEnv())) {
            return wechatProperties.getCloudEnv().trim();
        }
        String body = fileId.substring("cloud://".length());
        int dot = body.indexOf('.');
        if (dot <= 0) {
            throw new BusinessException(400, "fileId 不合法，无法解析云环境 ID（可配置 WECHAT_CLOUD_ENV 显式指定）");
        }
        return body.substring(0, dot);
    }

    /**
     * 流式下载云存储图片（BE-05）。
     * <p>
     * 以 {@code ResponseExtractor} 直接消费响应流，边读边累计，超过 1MB 立即中断并抛 400。
     * <b>不用</b> {@code getForObject(byte[].class)}：它会把整个响应体全量缓冲进堆内存，且体积上限在
     * 「下载完成之后」才判定——攻击者只需给出一个几百 MB 的临时链接即可单请求打爆堆（OOM 面）。
     *
     * @return 图片字节（已保证非空且 ≤ {@link WechatApiConst#MAX_IMAGE_SEC_CHECK_BYTES}）
     */
    private byte[] downloadImage(String downloadUrl) {
        try {
            // 说明：RestTemplate 默认的错误处理器会在 extractData 之前对 4xx/5xx 抛
            // HttpClientErrorException / HttpServerErrorException，故此处拿到的响应已是 2xx。
            // 非 2xx 会落入下面的 catch(Exception) 统一转 400，不会误判为成功。
            return cloudRestTemplate.execute(downloadUrl, HttpMethod.GET, null, response -> {
                // try-with-resources：无论正常读完还是超限中断，都确保响应流与底层连接被关闭
                try (ClientHttpResponse resp = response; InputStream in = resp.getBody()) {
                    return readCapped(in, WechatApiConst.MAX_IMAGE_SEC_CHECK_BYTES);
                }
            });
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("云存储图片下载失败（url={}）", maskUrl(downloadUrl), e);
            throw new BusinessException(400, "图片下载失败，请重试");
        }
    }

    /**
     * 边读边截断（云存储链路 <b>唯一的 1MB 兜底点</b>）：累计字节数一旦超过 {@code limit}
     * 立即抛出 400 并停止读取，不把剩余字节读进内存（缓冲区仅 8KB 常驻）。
     */
    private static byte[] readCapped(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > limit) {
                throw new BusinessException(400, "图片超过 1MB 限制，请压缩后重试");
            }
            out.write(buf, 0, n);
        }
        if (total == 0) {
            throw new BusinessException(400, "图片下载失败，请重试");
        }
        return out.toByteArray();
    }

    /** magic number 推断图片真实格式（jpg/png/webp），不合法一律拒绝 */
    private String detectImageExt(byte[] data) {
        if (data == null || data.length < 12) {
            throw new BusinessException(400, "仅支持 jpg、png、webp 图片");
        }
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if ((data[0] & 0xFF) == 0x89 && (data[1] & 0xFF) == 0x50 && (data[2] & 0xFF) == 0x4E
                && (data[3] & 0xFF) == 0x47) {
            return "png";
        }
        if ((data[0] & 0xFF) == 0x52 && (data[1] & 0xFF) == 0x49 && (data[2] & 0xFF) == 0x46
                && (data[3] & 0xFF) == 0x46 && (data[8] & 0xFF) == 0x57 && (data[9] & 0xFF) == 0x45
                && (data[10] & 0xFF) == 0x42 && (data[11] & 0xFF) == 0x50) {
            return "webp";
        }
        throw new BusinessException(400, "仅支持 jpg、png、webp 图片");
    }

    private Map<String, Object> parseJsonBody(String body) {
        if (body == null || body.isBlank()) {
            throw new BusinessException(400, "云存储文件获取失败，请重试");
        }
        try {
            Map<String, Object> map = OBJECT_MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {
            });
            if (map == null) {
                throw new IllegalStateException("响应为空对象");
            }
            return map;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("batchdownloadfile 响应非 JSON：{}", body.length() > 200 ? body.substring(0, 200) : body);
            throw new BusinessException(400, "云存储文件获取失败，请重试");
        }
    }

    private Integer asInt(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 日志脱敏：隐藏临时链接签名参数 */
    private String maskUrl(String url) {
        return url == null ? "" : url.replaceAll("([?&][^=]*=)[^&]{24,}", "$1***");
    }

    private static RestTemplate newCloudRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CLOUD_DOWNLOAD_TIMEOUT_MS);
        factory.setReadTimeout(CLOUD_DOWNLOAD_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    // ==================== 本地存储链路（COS 未配置降级）私有方法 ====================

    /** 静默删除文件（拒绝超尺寸图时清理已落盘的原图，避免磁盘垃圾） */
    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 清理失败不影响主流程错误返回
        }
    }

    /**
     * 校验文件头 magic number 是否为常见图片格式（防扩展名伪造）。
     * JPG: FF D8 FF；PNG: 89 50 4E 47 0D 0A 1A 0A；WEBP: RIFF....WEBP（52 49 46 46 ?? ?? ?? ?? 57 45 42 50）
     */
    private boolean isImageMagic(byte[] header, int len) {
        if (len < 3) return false;
        // JPEG: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return true;
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (len >= 8
                && (header[0] & 0xFF) == 0x89 && (header[1] & 0xFF) == 0x50
                && (header[2] & 0xFF) == 0x4E && (header[3] & 0xFF) == 0x47
                && (header[4] & 0xFF) == 0x0D && (header[5] & 0xFF) == 0x0A
                && (header[6] & 0xFF) == 0x1A && (header[7] & 0xFF) == 0x0A) {
            return true;
        }
        // WEBP: 52 49 46 46 ?? ?? ?? ?? 57 45 42 50
        if (len >= 12
                && (header[0] & 0xFF) == 0x52 && (header[1] & 0xFF) == 0x49
                && (header[2] & 0xFF) == 0x46 && (header[3] & 0xFF) == 0x46
                && (header[8] & 0xFF) == 0x57 && (header[9] & 0xFF) == 0x45
                && (header[10] & 0xFF) == 0x42 && (header[11] & 0xFF) == 0x50) {
            return true;
        }
        return false;
    }
}
