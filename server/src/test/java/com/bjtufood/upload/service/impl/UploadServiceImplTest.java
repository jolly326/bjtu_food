package com.bjtufood.upload.service.impl;

import com.bjtufood.common.config.UploadProperties;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.moderation.service.ContentSecurityService;
import com.bjtufood.upload.dto.UploadResultVO;
import com.bjtufood.upload.service.CosStorageService;
import com.bjtufood.wechat.config.WechatProperties;
import com.bjtufood.wechat.constant.WechatApiConst;
import com.bjtufood.wechat.service.WechatAccessTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 上传服务两条链路的契约单测（口径见 docs/api/web/upload.md 与 docs/api/client/upload.md）：
 * <ol>
 *   <li><b>multipart 直传</b>（{@code POST /admin/upload}）：空文件 / 超 5MB（含恰好 5MB 的边界）/
 *       扩展名白名单（大小写不敏感）/ 文件头 magic number 三格式（jpeg/png/webp）与伪造扩展名拒绝；
 *       COS 已配置 → 转存（{@code url} 与 {@code relativeUrl} 同为 COS 绝对地址）；
 *       COS 未配置 → 本地落盘降级（站内相对路径 + 绝对地址，文件真实落盘）；
 *       落盘失败 → {@code 400} 并清理半成品；图像炸弹 → {@code 400} 且删除已落盘原图；</li>
 *   <li><b>云存储 fileID 转存</b>（{@code POST /upload/cloud-image}）：fileId 形态校验、
 *       云环境 ID 解析失败 → {@code 400}（不发起任何出网请求）、凭据异常原样上抛（不被吞成「获取失败」）；
 *       batchdownloadfile 响应解析（正常 / errcode / 非 JSON / file_list 形态 / status / download_url）、
 *       凭据失效清缓存重试（40001 一轮成功、两轮皆败 fail-closed 500）、
 *       临时链接流式下载（超 1MB 截断、空响应体）。</li>
 * </ol>
 * 断言口径为「错误码 + 文案 + 落库/落盘结果」三锁。
 * <p>
 * <b>出网面</b>：以 {@code MockRestServiceServer} 脚本化出网响应，不做任何真实网络调用 ——
 * batchdownloadfile 与临时链接下载两个出网点共用测试内新建并绑定的 RestTemplate。
 */
class UploadServiceImplTest {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final String MSG_ILLEGAL_FILE_ID = "fileId 不合法，必须为微信云存储 cloud:// 文件标识";
    private static final String MSG_CLOUD_ENV_UNRESOLVED =
            "fileId 不合法，无法解析云环境 ID（可配置 WECHAT_CLOUD_ENV 显式指定）";

    /** batchdownloadfile 端点（平台常量，与实现类同源，避免测试另立一份真源） */
    private static final String BATCH_DOWNLOAD_URL = WechatApiConst.BATCH_DOWNLOAD_URL;

    /** 微信回给的临时下载链接（脚本化 GET 下载用） */
    private static final String DOWNLOAD_URL = "https://cloud.weixin.qq.com/tcb/x.png?sign=abcdefghijklmnop";

    /** 云环境 ID 可由 fileId 解析（cloud://{env}.{bucket}/path），无需依赖 WECHAT_CLOUD_ENV 配置 */
    private static final String FILE_ID = "cloud://env-1.bucket/path.png";

    /** 转存 COS 后回给端上的绝对地址 */
    private static final String COS_URL = "https://bucket.cos.ap-beijing.myqcloud.com/ugc/20261003/x.png";

    @TempDir
    Path tempDir;

    private final ImageUrlUtil imageUrlUtil = new ImageUrlUtil("http://localhost:8080/api/v1");
    private final ContentSecurityService contentSecurityService = mock(ContentSecurityService.class);
    private final CosStorageService cosStorageService = mock(CosStorageService.class);
    private final WechatAccessTokenProvider tokenProvider = mock(WechatAccessTokenProvider.class);
    private final UploadProperties uploadProperties = new UploadProperties();
    private final WechatProperties wechatProperties = new WechatProperties();

    private UploadServiceImpl service;

    /** 出网脚本：绑定测试内新建的 RestTemplate（MockRestServiceServer 会替换其 RequestFactory） */
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        uploadProperties.setPath(tempDir.toString());
        uploadProperties.setUrlPrefix("/images");
        RestTemplate cloudRestTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(cloudRestTemplate).build();
        service = new UploadServiceImpl(imageUrlUtil, contentSecurityService, cosStorageService,
                tokenProvider, cloudRestTemplate, uploadProperties, wechatProperties);
    }

    // ==================== 夹具 ====================

    /** 真实 1×1 PNG（可过 magic number 与「图像炸弹」尺寸校验）。 */
    private static byte[] png() {
        try {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 真实 JPEG 头（FF D8 FF）+ 填充字节，用于验证 magic number 分支。 */
    private static byte[] jpeg() {
        byte[] data = new byte[16];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xD8;
        data[2] = (byte) 0xFF;
        data[3] = (byte) 0xE0;
        return data;
    }

    /** 真实 WEBP 头（RIFF????WEBP）+ 填充字节。 */
    private static byte[] webp() {
        byte[] data = new byte[16];
        byte[] riff = {(byte) 'R', (byte) 'I', (byte) 'F', (byte) 'F'};
        byte[] type = {(byte) 'W', (byte) 'E', (byte) 'B', (byte) 'P'};
        System.arraycopy(riff, 0, data, 0, 4);
        System.arraycopy(type, 0, data, 8, 4);
        return data;
    }

    /**
     * 声明为 5000×5000 的 PNG 头（实际字节极少）—— 小体积高分辨率正是「图像炸弹」的形态：
     * 全图解码会按像素数分配内存，故须在解码前拦截。
     */
    private static byte[] pngDeclaringTooLarge() {
        byte[] data = new byte[24];
        byte[] signature = {(byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, data, 0, 8);
        data[11] = 13;                 // IHDR 长度
        data[12] = (byte) 'I';
        data[13] = (byte) 'H';
        data[14] = (byte) 'D';
        data[15] = (byte) 'R';
        int width = 5000;
        data[16] = (byte) (width >>> 24);
        data[17] = (byte) (width >>> 16);
        data[18] = (byte) (width >>> 8);
        data[19] = (byte) width;
        data[20] = data[16];
        data[21] = data[17];
        data[22] = data[18];
        data[23] = data[19];
        return data;
    }

    /** 断言「业务码 + 文案」双锁（避免每个用例重复强制转换样板）。 */
    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
                                       int code, String message) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .hasMessage(message)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(code));
    }

    /** 统计临时目录下已落盘的文件数（用于断言「失败后不留半成品 / 已删原图」）。 */
    private long landedFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(tempDir)) {
            return walk.filter(Files::isRegularFile).count();
        }
    }

    // ==================== 链路一：multipart 直传 —— 前置校验 ====================

    @Test
    @DisplayName("uploadImage：文件为 null / 空文件 → 400「文件不能为空」，且不碰 COS")
    void uploadImage_nullOrEmpty_rejected400() {
        assertRejected(() -> service.uploadImage(null), 400, "文件不能为空");
        assertRejected(() -> service.uploadImage(new MockMultipartFile("file", "a.png", "image/png", new byte[0])),
                400, "文件不能为空");

        verifyNoInteractions(cosStorageService);
    }

    @Test
    @DisplayName("uploadImage：超过 5MB → 400「图片大小不能超过 5MB」")
    void uploadImage_oversized_rejected400() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(MAX_FILE_SIZE + 1);

        assertRejected(() -> service.uploadImage(file), 400, "图片大小不能超过 5MB");
    }

    @Test
    @DisplayName("uploadImage：恰好 5MB 视为合法（上限含边界），继续走 COS 转存")
    void uploadImage_exactlyMaxSize_accepted() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(MAX_FILE_SIZE);
        when(file.getOriginalFilename()).thenReturn("a.png");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(png()));
        when(file.getBytes()).thenReturn(png());
        when(cosStorageService.isConfigured()).thenReturn(true);
        when(cosStorageService.upload(any(), eq("png")))
                .thenReturn("https://bucket.cos.ap-beijing.myqcloud.com/ugc/x.png");

        UploadResultVO vo = service.uploadImage(file);

        assertThat(vo.getUrl()).isEqualTo("https://bucket.cos.ap-beijing.myqcloud.com/ugc/x.png");
    }

    @Test
    @DisplayName("uploadImage：扩展名缺失 / 不在白名单（gif / 双后缀 / 空后缀）→ 400，且不落盘")
    void uploadImage_illegalExtension_rejected400() throws IOException {
        assertRejected(() -> service.uploadImage(new MockMultipartFile("file", null, null, png())),
                400, "仅支持 jpg、jpeg、png、webp 图片");
        assertRejected(() -> service.uploadImage(new MockMultipartFile("file", "a.gif", "image/gif", png())),
                400, "仅支持 jpg、jpeg、png、webp 图片");
        assertRejected(() -> service.uploadImage(new MockMultipartFile("file", "a.tar.gz", null, png())),
                400, "仅支持 jpg、jpeg、png、webp 图片");
        assertRejected(() -> service.uploadImage(new MockMultipartFile("file", "a.", null, png())),
                400, "仅支持 jpg、jpeg、png、webp 图片");

        assertThat(landedFiles()).isZero();
    }

    @Test
    @DisplayName("uploadImage：扩展名大小写不敏感（.PNG 归一为 png；.JPEG 归一为 jpeg）")
    void uploadImage_extensionCaseInsensitive() {
        when(cosStorageService.isConfigured()).thenReturn(true);
        when(cosStorageService.upload(any(), anyString())).thenReturn("https://bucket.cos.ap-beijing.myqcloud.com/ugc/x.png");

        service.uploadImage(new MockMultipartFile("file", "a.PNG", null, png()));
        verify(cosStorageService).upload(any(), eq("png"));

        service.uploadImage(new MockMultipartFile("file", "b.JPEG", null, jpeg()));
        verify(cosStorageService).upload(any(), eq("jpeg"));
    }

    @Test
    @DisplayName("uploadImage：文件头伪造（.png 内容非图片 / 内容不足 3 字节）→ 400「文件内容不是合法的图片」")
    void uploadImage_forgedExtension_rejected400() {
        assertRejected(() -> service.uploadImage(
                new MockMultipartFile("file", "a.png", null, "not an image!!!".getBytes())),
                400, "文件内容不是合法的图片");
        assertRejected(() -> service.uploadImage(
                new MockMultipartFile("file", "a.png", null, new byte[]{(byte) 0xFF, (byte) 0xD8})),
                400, "文件内容不是合法的图片");

        verifyNoInteractions(cosStorageService);
    }

    @Test
    @DisplayName("uploadImage：magic number 合法的 jpg / webp 同样放行")
    void uploadImage_acceptsJpegAndWebpMagic() {
        when(cosStorageService.isConfigured()).thenReturn(true);
        when(cosStorageService.upload(any(), anyString())).thenReturn("https://bucket.cos.ap-beijing.myqcloud.com/ugc/x.jpg");

        service.uploadImage(new MockMultipartFile("file", "a.jpg", null, jpeg()));
        verify(cosStorageService).upload(any(), eq("jpg"));

        service.uploadImage(new MockMultipartFile("file", "b.webp", null, webp()));
        verify(cosStorageService).upload(any(), eq("webp"));
    }

    // ==================== 链路一：COS 转存 / 本地降级 ====================

    @Test
    @DisplayName("uploadImage：COS 已配置 → 转存并返回绝对地址（url 与 relativeUrl 同为 COS 地址，不落本地盘）")
    void uploadImage_cosConfigured_transfersToCos() throws IOException {
        when(cosStorageService.isConfigured()).thenReturn(true);
        String cosUrl = "https://bucket.cos.ap-beijing.myqcloud.com/ugc/20261003/x.png";
        when(cosStorageService.upload(any(), eq("png"))).thenReturn(cosUrl);

        UploadResultVO vo = service.uploadImage(new MockMultipartFile("file", "a.png", null, png()));

        // 管理端 web 约定：两个字段都必须有值，否则前端报「上传接口返回缺少图片地址」
        assertThat(vo.getUrl()).isEqualTo(cosUrl);
        assertThat(vo.getRelativeUrl()).isEqualTo(cosUrl);
        assertThat(landedFiles()).isZero();
    }

    @Test
    @DisplayName("uploadImage：COS 未配置 → 本地落盘降级（相对路径 + 站内绝对地址，文件真实写入）")
    void uploadImage_cosUnconfigured_fallsBackToLocalDisk() throws IOException {
        byte[] data = png();
        when(cosStorageService.isConfigured()).thenReturn(false);

        UploadResultVO vo = service.uploadImage(new MockMultipartFile("file", "a.png", null, data));

        assertThat(vo.getRelativeUrl()).matches("/images/\\d{4}/\\d{2}/[0-9a-fA-F-]{36}\\.png");
        assertThat(vo.getUrl()).isEqualTo("http://localhost:8080/api/v1" + vo.getRelativeUrl());

        String datePath = vo.getRelativeUrl().substring("/images/".length(), vo.getRelativeUrl().lastIndexOf('/'));
        String filename = vo.getRelativeUrl().substring(vo.getRelativeUrl().lastIndexOf('/') + 1);
        Path saved = tempDir.resolve(datePath).resolve(filename);
        assertThat(saved).exists();
        assertThat(Files.readAllBytes(saved)).isEqualTo(data);
    }

    @Test
    @DisplayName("uploadImage：COS 未配置且落盘失败 → 400「图片上传失败」，不留半成品文件")
    void uploadImage_localDiskFailure_rejected400() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(16L);
        when(file.getOriginalFilename()).thenReturn("a.png");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(png()));
        doThrow(new IOException("disk full")).when(file).transferTo(any(Path.class));
        when(cosStorageService.isConfigured()).thenReturn(false);

        assertRejected(() -> service.uploadImage(file), 400, "图片上传失败");

        assertThat(landedFiles()).isZero();
    }

    @Test
    @DisplayName("uploadImage：COS 已配置但读取字节失败 → 400「文件读取失败」")
    void uploadImage_cosConfiguredButUnreadable_rejected400() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(16L);
        when(file.getOriginalFilename()).thenReturn("a.png");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(png()));
        when(file.getBytes()).thenThrow(new IOException("stream closed"));
        when(cosStorageService.isConfigured()).thenReturn(true);

        assertRejected(() -> service.uploadImage(file), 400, "文件读取失败");

        verify(cosStorageService, never()).upload(any(), anyString());
    }

    @Test
    @DisplayName("uploadImage：图像炸弹（5000×5000 头）→ 400 尺寸超限，且删除已落盘原图")
    void uploadImage_imageBomb_rejected400AndOriginalDeleted() throws IOException {
        when(cosStorageService.isConfigured()).thenReturn(false);

        assertRejected(() -> service.uploadImage(
                        new MockMultipartFile("file", "bomb.png", null, pngDeclaringTooLarge())),
                400, "图片尺寸过大，宽和高均不能超过 4000 像素");

        assertThat(landedFiles()).isZero();
    }

    // ==================== 链路二：云存储 fileID 转存 —— 入参与云环境解析 ====================

    @Test
    @DisplayName("uploadCloudImage：fileId 缺失 / 空白 / 非 cloud:// 前缀 → 400，且不取凭据、不出网")
    void uploadCloudImage_illegalFileId_rejected400() {
        for (String illegal : List.of("", "   ", "https://x/y.png", "cloud:/x/y.png", "fileid")) {
            assertRejected(() -> service.uploadCloudImage(illegal), 400, MSG_ILLEGAL_FILE_ID);
        }
        assertRejected(() -> service.uploadCloudImage(null), 400, MSG_ILLEGAL_FILE_ID);

        verifyNoInteractions(tokenProvider, cosStorageService, contentSecurityService);
    }

    @Test
    @DisplayName("uploadCloudImage：未配置 WECHAT_CLOUD_ENV 且 fileId 无法解析环境 ID → 400，且不取凭据")
    void uploadCloudImage_cloudEnvUnresolved_rejected400() {
        // 「cloud://」后无点号
        assertRejected(() -> service.uploadCloudImage("cloud://nodot"), 400, MSG_CLOUD_ENV_UNRESOLVED);
        // 点号位于首位（环境 ID 为空串）
        assertRejected(() -> service.uploadCloudImage("cloud://.bucket/path.png"), 400, MSG_CLOUD_ENV_UNRESOLVED);

        verify(tokenProvider, never()).get();
        verifyNoInteractions(cosStorageService);
    }

    @Test
    @DisplayName("uploadCloudImage：凭据异常原样上抛（不被吞成「云存储文件获取失败」），且不清 token 缓存")
    void uploadCloudImage_credentialFailure_propagatesUnchanged() {
        wechatProperties.setCloudEnv("");
        when(tokenProvider.get()).thenThrow(new BusinessException(400, "微信凭据未配置"));

        // 环境 ID 可从 fileId 解析（含点号）⇒ 进入 batchdownloadfile 前先取凭据
        assertRejected(() -> service.uploadCloudImage("cloud://env-1.bucket/path.png"),
                400, "微信凭据未配置");

        verify(tokenProvider).get();
        verify(tokenProvider, never()).invalidate();
        verifyNoInteractions(cosStorageService, contentSecurityService);
    }

    @Test
    @DisplayName("uploadCloudImage：已配置 WECHAT_CLOUD_ENV 时环境 ID 优先取配置（fileId 无点号也不报解析错）")
    void uploadCloudImage_configuredCloudEnvTakesPrecedence() {
        wechatProperties.setCloudEnv("env-from-config");
        when(tokenProvider.get()).thenThrow(new BusinessException(400, "微信凭据未配置"));

        assertRejected(() -> service.uploadCloudImage("cloud://nodot"), 400, "微信凭据未配置");

        verify(tokenProvider).get();
    }

    // ============ 链路二：batchdownloadfile 出网响应解析（MockRestServiceServer 脚本化） ============

    /** 脚本化一次 batchdownloadfile 响应（微信固定以 text/plain 返回）。 */
    private void expectBatchDownload(String body) {
        server.expect(once(), requestTo(startsWith(BATCH_DOWNLOAD_URL)))
                .andExpect(method(POST))
                .andRespond(withSuccess(body, MediaType.TEXT_PLAIN));
    }

    /** 脚本化一次临时链接图片下载（GET，响应体即图片字节）。 */
    private void expectDownload(byte[] body) {
        server.expect(once(), requestTo(DOWNLOAD_URL))
                .andExpect(method(GET))
                .andRespond(withSuccess(body, MediaType.IMAGE_PNG));
    }

    /** errcode=0 且 file_list[0] 带回临时下载链接的响应体 */
    private static String batchOk(String downloadUrl) {
        return batchOkWithItem("{\"fileid\":\"" + FILE_ID + "\",\"status\":0,\"download_url\":\""
                + downloadUrl + "\"}");
    }

    /** errcode=0 且 file_list[0] 为给定 JSON 对象的响应体（构造 status / download_url 的异常形态） */
    private static String batchOkWithItem(String itemJson) {
        return "{\"errcode\":0,\"errmsg\":\"ok\",\"file_list\":[" + itemJson + "]}";
    }

    @Test
    @DisplayName("uploadCloudImage：batchdownloadfile 正常 → 取 file_list[0].download_url 下载过检后转存 COS")
    void uploadCloudImage_batchDownloadOk_transfersToCos() {
        when(tokenProvider.get()).thenReturn("tk-1");
        expectBatchDownload(batchOk(DOWNLOAD_URL));
        expectDownload(png());
        when(cosStorageService.upload(any(), eq("png"))).thenReturn(COS_URL);

        UploadResultVO vo = service.uploadCloudImage(FILE_ID);

        // 下载到的字节须原样过内容安全检测，再以真实格式推断的扩展名转存
        assertThat(vo.getUrl()).isEqualTo(COS_URL);
        verify(contentSecurityService).checkImage(any());
        verify(tokenProvider, never()).invalidate();
        server.verify();
    }

    @Test
    @DisplayName("uploadCloudImage：errcode≠0 且非 token 失效类（45011）→ 400「云存储文件获取失败，请重试」，不重试")
    void uploadCloudImage_nonTokenErrcode_rejected400() {
        when(tokenProvider.get()).thenReturn("tk-1");
        expectBatchDownload("{\"errcode\":45011,\"errmsg\":\"api minute-quota reach\"}");

        assertRejected(() -> service.uploadCloudImage(FILE_ID), 400, "云存储文件获取失败，请重试");

        verify(tokenProvider, times(1)).get();
        verify(tokenProvider, never()).invalidate();
        verifyNoInteractions(contentSecurityService, cosStorageService);
        server.verify();
    }

    @Test
    @DisplayName("uploadCloudImage：响应非 JSON（网关 HTML 空壳）→ 400「云存储文件获取失败，请重试」")
    void uploadCloudImage_nonJsonResponse_rejected400() {
        when(tokenProvider.get()).thenReturn("tk-1");
        expectBatchDownload("<html>502 Bad Gateway</html>");

        assertRejected(() -> service.uploadCloudImage(FILE_ID), 400, "云存储文件获取失败，请重试");

        verify(tokenProvider, never()).invalidate();
        verifyNoInteractions(contentSecurityService, cosStorageService);
        server.verify();
    }

    @Test
    @DisplayName("uploadCloudImage：file_list 缺失 / 空数组 / 非数组 → 400「云存储文件不存在或已删除」")
    void uploadCloudImage_emptyOrMalformedFileList_rejected400() {
        when(tokenProvider.get()).thenReturn("tk-1");
        // MockRestServiceServer 不允许「发过请求后再补期望」：三种形态一次注册完，再逐个触发
        expectBatchDownload("{\"errcode\":0,\"file_list\":[]}");
        expectBatchDownload("{\"errcode\":0,\"file_list\":\"oops\"}");
        expectBatchDownload("{\"errcode\":0}");

        assertRejected(() -> service.uploadCloudImage(FILE_ID), 400, "云存储文件不存在或已删除");
        assertRejected(() -> service.uploadCloudImage(FILE_ID), 400, "云存储文件不存在或已删除");
        assertRejected(() -> service.uploadCloudImage(FILE_ID), 400, "云存储文件不存在或已删除");

        verify(tokenProvider, times(3)).get();
        verify(tokenProvider, never()).invalidate();
        verifyNoInteractions(contentSecurityService, cosStorageService);
        server.verify();
    }

    @Test
    @DisplayName("uploadCloudImage：file_list[0].status≠0 或 download_url 空/缺失 → 400「云存储文件不存在或已删除」")
    void uploadCloudImage_downloadUrlUnavailable_rejected400() {
        when(tokenProvider.get()).thenReturn("tk-1");
        // 同「file_list 形态」用例：三种形态一次注册完，再逐个触发
        expectBatchDownload(batchOkWithItem("{\"status\":-1,\"errmsg\":\"file not exist\"}"));
        expectBatchDownload(batchOkWithItem("{\"status\":0,\"download_url\":\"\"}"));
        expectBatchDownload(batchOkWithItem("{\"status\":0}"));

        assertRejected(() -> service.uploadCloudImage(FILE_ID), 400, "云存储文件不存在或已删除");
        assertRejected(() -> service.uploadCloudImage(FILE_ID), 400, "云存储文件不存在或已删除");
        assertRejected(() -> service.uploadCloudImage(FILE_ID), 400, "云存储文件不存在或已删除");

        verify(tokenProvider, times(3)).get();
        verify(tokenProvider, never()).invalidate();
        verifyNoInteractions(contentSecurityService, cosStorageService);
        server.verify();
    }

    @Test
    @DisplayName("uploadCloudImage：首轮 40001 → 清缓存重试一次后成功（invalidate 恰 1 次、get 恰 2 次）")
    void uploadCloudImage_tokenInvalidThenSuccess_retriesOnce() {
        when(tokenProvider.get()).thenReturn("tk-1", "tk-2");
        expectBatchDownload("{\"errcode\":40001,\"errmsg\":\"invalid credential\"}");
        expectBatchDownload(batchOk(DOWNLOAD_URL));
        expectDownload(png());
        when(cosStorageService.upload(any(), eq("png"))).thenReturn(COS_URL);

        UploadResultVO vo = service.uploadCloudImage(FILE_ID);

        assertThat(vo.getUrl()).isEqualTo(COS_URL);
        verify(tokenProvider, times(2)).get();
        verify(tokenProvider, times(1)).invalidate();
        server.verify();
    }

    @Test
    @DisplayName("uploadCloudImage：两轮均 40001 → 500「云存储服务暂不可用，请稍后重试」且 invalidate 恰 1 次（fail-closed）")
    void uploadCloudImage_tokenInvalidTwice_failClosed500() {
        when(tokenProvider.get()).thenReturn("tk-1", "tk-2");
        expectBatchDownload("{\"errcode\":40001,\"errmsg\":\"invalid credential\"}");
        expectBatchDownload("{\"errcode\":40001,\"errmsg\":\"invalid credential\"}");

        assertRejected(() -> service.uploadCloudImage(FILE_ID), 500, "云存储服务暂不可用，请稍后重试");

        verify(tokenProvider, times(2)).get();
        verify(tokenProvider, times(1)).invalidate();
        verifyNoInteractions(contentSecurityService, cosStorageService);
        server.verify();
    }

    @Test
    @DisplayName("uploadCloudImage：下载体超过 1MB → 边读边截断抛 400「图片超过 1MB 限制，请压缩后重试」，不落 COS")
    void uploadCloudImage_downloadExceeds1Mb_rejected400() {
        when(tokenProvider.get()).thenReturn("tk-1");
        expectBatchDownload(batchOk(DOWNLOAD_URL));
        // 比硬上限多 1 字节：须在读完整个响应体之前中断（缓冲区仅 8KB 常驻）
        expectDownload(new byte[1024 * 1024 + 1]);

        assertRejected(() -> service.uploadCloudImage(FILE_ID), 400, "图片超过 1MB 限制，请压缩后重试");

        verify(cosStorageService, never()).upload(any(), anyString());
        verifyNoInteractions(contentSecurityService);
        server.verify();
    }

    @Test
    @DisplayName("uploadCloudImage：下载响应体为空 → 400「图片下载失败，请重试」，不落 COS")
    void uploadCloudImage_emptyDownloadBody_rejected400() {
        when(tokenProvider.get()).thenReturn("tk-1");
        expectBatchDownload(batchOk(DOWNLOAD_URL));
        expectDownload(new byte[0]);

        assertRejected(() -> service.uploadCloudImage(FILE_ID), 400, "图片下载失败，请重试");

        verify(cosStorageService, never()).upload(any(), anyString());
        verifyNoInteractions(contentSecurityService);
        server.verify();
    }
}
