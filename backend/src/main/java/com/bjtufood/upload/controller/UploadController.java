package com.bjtufood.upload.controller;

import com.bjtufood.common.result.Result;
import com.bjtufood.upload.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件上传控制器
 * <p>
 * 所有已登录用户均可上传图片。
 * 学生上传评价图片，管理员上传菜品图片。
 * 支持格式：jpg/png/jpeg，单文件 ≤5MB。
 */
@Tag(name = "文件上传", description = "上传图片（菜品图/评价图），返回相对图片路径")
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @Operation(summary = "上传图片", description = "上传图片文件，返回相对图片路径，如 /images/2026/05/xxx.jpg")
    @PostMapping("/image")
    public Result<?> uploadImage(@RequestParam("file") MultipartFile file) {
        String url = uploadService.uploadImage(file);
        return Result.success(Map.of("url", url));
    }
}
