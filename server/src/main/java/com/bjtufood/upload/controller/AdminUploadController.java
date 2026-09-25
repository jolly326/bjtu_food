package com.bjtufood.upload.controller;

import com.bjtufood.common.result.Result;
import com.bjtufood.upload.dto.UploadResultVO;
import com.bjtufood.upload.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端图片上传（multipart 直传）。
 * <p>
 * 归入 {@code /admin/**} 命名空间 → 由 {@code AdminTokenFilter} 统一按口令
 * {@code X-Admin-Token} 守卫（不声明 bearerAuth：本端点不接受学生 JWT）。
 */
@Tag(name = "07. 图片上传（管理端）", description = "管理端菜品图上传（multipart）。鉴权：管理端口令 X-Admin-Token。")
@RestController
@RequestMapping("/admin/upload")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class AdminUploadController {

    private final UploadService uploadService;

    @Operation(
            summary = "上传图片（multipart）",
            description = """
                    用途：管理端上传菜品图。
                    鉴权：请求头 X-Admin-Token 必须等于环境变量 ADMIN_TOKEN（未配置即 fail-closed 403）。
                    测试：Swagger UI 中选择 multipart/form-data，字段名必须为 file。
                    返回：data.url（完整可访问 URL），本地存储降级链路额外返回 data.relativeUrl。
                    小程序 UGC 配图请使用 POST /upload/cloud-image（云存储转存链路，学生 JWT）。
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    examples = @ExampleObject(value = "file: <binary>"))))
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadResultVO> uploadImage(
            @Parameter(description = "图片文件，支持 jpg/jpeg/png/webp")
            @RequestParam("file") MultipartFile file) {
        return Result.success(uploadService.uploadImage(file));
    }
}
