package com.bjtufood.upload.controller;

import com.bjtufood.auth.support.SecurityUtil;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.ratelimit.IpRateLimiter;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.ClientIpUtil;
import com.bjtufood.upload.dto.CloudImageUploadReq;
import com.bjtufood.upload.dto.UploadResultVO;
import com.bjtufood.upload.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端图片上传（小程序云存储配图主链路）。
 * 鉴权：学生 JWT（{@code Authorization: Bearer}）。
 * 管理端 multipart 上传见 {@link AdminUploadController}（{@code POST /admin/upload}）。
 */
@Tag(name = "07. 图片上传（学生端）", description = "小程序 UGC 配图：云存储 fileID → imgSecCheck → COS 转存。鉴权：学生 JWT。")
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    /** IP 维度限频器（与其余写入口同一实现） */
    private final IpRateLimiter ipRateLimiter;

    /**
     * 转存限频：IP 维度 6/分 · 30/时。
     * <p>
     * 阈值高于 UGC 文本写入口（2/分）：本端点是**逐张**调用 —— 一次提交最多 3 张配图即连发 3 次，
     * 按文本入口的阈值会误伤正常用户。
     */
    private static final IpRateLimiter.Rule PER_MINUTE = new IpRateLimiter.Rule(6, 60_000L);

    private static final IpRateLimiter.Rule PER_HOUR = new IpRateLimiter.Rule(30, 3_600_000L);

    @Operation(
            summary = "云存储配图转存（UGC 配图主链路）",
            description = """
                    用途：评价/反馈配图。前端先 wx.cloud.uploadFile 上传得 fileID，再调本接口：
                    后端 batchdownloadfile 拉临时链接 → 下载 → 大小/格式兜底校验 → imgSecCheck 内容安全检测 →
                    转存 COS → 返回最终 COS URL（data.url）。
                    限制：图片 ≤1MB（imgSecCheck 硬限制），jpg/png/webp。
                    违规返回 400「图片包含违规内容，无法上传」；COS 未配置返回 400「图片存储未配置」。
                    """,
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    examples = @ExampleObject(value = """
                            {
                              "fileId": "cloud://bjtu-dev.6a6x-bjtu-dev-1300000000/ugc/tmp.jpg"
                            }
                            """)))
    )
    @PostMapping("/cloud-image")
    public Result<UploadResultVO> uploadCloudImage(@Valid @RequestBody CloudImageUploadReq req) {
        checkRateLimit(SecurityUtil.getCurrentUserId());
        return Result.success(uploadService.uploadCloudImage(req.getFileId()));
    }

    /**
     * 双维度滥用防护（**IP × 账号**，任一超限即拒）。
     * <p>
     * 本端点是**唯一有外部成本的写入口**：每次调用都会让后端向微信换取临时链接、下载图片、
     * 送 {@code imgSecCheck} 并转存对象存储 —— 既烧微信调用配额也烧对象存储流量。
     * 账号维度是换 IP 也绕不过的那一道。
     */
    private void checkRateLimit(Long userId) {
        long waitSeconds = ipRateLimiter.tryAcquire(
                "cloud-image", ClientIpUtil.resolveCurrent(), PER_MINUTE, PER_HOUR);
        if (waitSeconds == 0L) {
            waitSeconds = ipRateLimiter.tryAcquire("cloud-image:user",
                    String.valueOf(userId), PER_MINUTE, PER_HOUR);
        }
        if (waitSeconds > 0) {
            throw BusinessException.tooManyRequests("上传过于频繁，请 " + waitSeconds + " 秒后再试");
        }
    }
}
