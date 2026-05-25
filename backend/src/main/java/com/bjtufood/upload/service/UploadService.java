package com.bjtufood.upload.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传服务接口
 * <p>
 * 处理菜品图片和评价图片的上传。
 * demo 阶段使用本地文件系统存储，后续可对接 OSS。
 * 所有上传用户均可使用（学生和管理员）。
 */
public interface UploadService {

    /**
     * 上传图片
     * <p>
     * 处理流程：
     * 1. 校验文件类型（仅 jpg/png/jpeg）
     * 2. 校验文件大小（≤5MB）
     * 3. 生成唯一文件名（UUID + 原始扩展名）
     * 4. 按日期分目录存储（/yyyy/MM/）
     * 5. 保存到本地 uploads 目录
     *
     * @param file 上传的文件（multipart/form-data）
     * @return 可访问的图片 URL 路径（如 /uploads/2024/01/abc123.jpg）
     * @throws com.bjtufood.common.exception.BusinessException 文件类型/大小不合法
     */
    String uploadImage(MultipartFile file);
}
