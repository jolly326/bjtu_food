package com.bjtufood.upload.controller;

import com.bjtufood.common.result.Result;
import com.bjtufood.upload.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
            summary = "上传图片",
            description = """
                    用途：上传头像、菜品图或评价图。
                    测试：Knife4j 中选择 multipart/form-data，字段名必须为 file。
                    返回：data.url，可直接作为 avatar、images 字段保存。
                    """
    )
    @PostMapping("/image")
    public Result<?> uploadImage(
            @Parameter(description = "图片文件，支持 jpg/jpeg/png/webp")
            @RequestParam("file") MultipartFile file) {
        String url = uploadService.uploadImage(file);
        return Result.success(Map.of("url", url));
    }
}
