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
@Tag(name = "文件上传", description = "上传图片（菜品图/评价图），返回可访问的 URL")
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @Operation(summary = "上传图片", description = "上传图片文件，返回可直接访问的 URL 路径")
    @PostMapping("/image")
    public Result<?> uploadImage(@RequestParam("file") MultipartFile file) {
        // TODO: 调用 UploadService.uploadImage(file)
        // 返回：{ "url": "/uploads/2024/01/abc123.jpg" }
        return Result.success("图片上传成功");
    }
}
