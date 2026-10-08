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
 * 归入 {@code /admin/**} 命名空间 → 由 {@code AdminAuthFilter} 按**管理端 JWT**
 * （{@code Authorization: Bearer <token>}）统一守卫；不接受学生 JWT。
 */
@Tag(name = "07. 图片上传（管理端）", description = "管理端菜品图上传（multipart）。鉴权：管理端 JWT（Authorization: Bearer）。")
@RestController
@RequestMapping("/admin/upload")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class AdminUploadController {

    private final UploadService uploadService;

    @Operation(
            summary = "上传图片（multipart）",
            description = """
                    用途：管理端上传素材（菜品图 / Banner 图），契约真源见 docs/api/web/upload.md「管理端素材上传」。
                    鉴权：请求头 Authorization: Bearer <管理端 JWT>（由 AdminAuthFilter 校验，未带 / 失效即 401）。
                    测试：Swagger UI 中选择 multipart/form-data，字段名必须为 file。
                    限制：单文件 ≤5MB；仅 jpg / jpeg / png / webp；含文件头 magic number 校验。
                    返回：data.url（可直接访问的图片地址，COS 链路为绝对 URL、本地降级链路为站内相对路径）
                          与 data.relativeUrl（本地降级链路才有；两者都可直接入库）。
                    小程序 UGC 配图请使用 POST /upload/cloud-image（云存储转存链路，学生 JWT）。
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    examples = @ExampleObject(value = "file: <binary>"))))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadResultVO> uploadImage(
            @Parameter(description = "图片文件，支持 jpg/jpeg/png/webp，单文件 ≤5MB")
            @RequestParam("file") MultipartFile file) {
        return Result.success(uploadService.uploadImage(file));
    }
}
