package com.bjtufood.upload.controller;

import com.bjtufood.common.result.Result;
import com.bjtufood.upload.dto.CloudImageUploadReq;
import com.bjtufood.upload.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "07. 图片上传", description = "上传菜品图、评价图、头像图。需要登录，返回可保存到数据库的图片路径。")
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UploadController {

    private final UploadService uploadService;

    @Operation(
            summary = "上传图片（multipart，独立服务器/H5 场景保留）",
            description = """
                    用途：上传头像、菜品图或评价图。
                    测试：Swagger UI 中选择 multipart/form-data，字段名必须为 file。
                    返回：data.url（完整可访问 URL），本地存储链路额外返回 data.relativeUrl（用于数据库保存的相对路径）。
                    小程序 UGC 配图请优先使用 POST /upload/images（云存储转存链路）。
                    """
    )
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, String>> uploadImage(
            @Parameter(description = "图片文件，支持 jpg/jpeg/png/webp")
            @RequestParam("file") MultipartFile file) {
        return Result.success(uploadService.uploadImage(file));
    }

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
    @PostMapping("/images")
    public Result<Map<String, String>> uploadCloudImage(@Valid @RequestBody CloudImageUploadReq req) {
        return Result.success(uploadService.uploadCloudImage(req.getFileId()));
    }
}
