package com.bjtufood.upload.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件上传服务接口
 * <p>
 * 两条链路（产品定稿 2026-09-13）：
 * <ul>
 *   <li>{@link #uploadImage(MultipartFile)}：multipart 直传（独立服务器/H5 场景保留）。
 *       COS 已配置时转存 COS，未配置时降级本地磁盘存储（开发环境无 COS 仍可用）；</li>
 *   <li>{@link #uploadCloudImage(String)}：小程序云存储 fileID 转存（UGC 配图主链路）。
 *       fileID → batchdownloadfile 拉临时链接 → 下载 → 大小/格式兜底校验 → imgSecCheck 内容安全检测 →
 *       转存 COS → 返回 COS URL。COS 未配置时返回明确业务错误「图片存储未配置」。</li>
 * </ul>
 */
public interface UploadService {

    /**
     * multipart 上传图片（保留，独立服务器/H5 场景用）
     * <p>
     * 处理流程：
     * 1. 校验文件类型（仅 jpg/png/jpeg/webp）与大小（≤5MB）
     * 2. COS 已配置：转存 COS 并返回 COS 绝对 URL
     *    COS 未配置：生成唯一文件名（UUID），按日期分目录存储到本地 uploads 目录
     *
     * @param file 上传的文件（multipart/form-data）
     * @return Map，包含 url（完整可访问 URL）；本地链路额外含 relativeUrl（相对路径）
     * @throws com.bjtufood.common.exception.BusinessException 文件类型/大小不合法
     */
    Map<String, String> uploadImage(MultipartFile file);

    /**
     * 小程序云存储配图转存（UGC 配图主链路，需登录）
     * <p>
     * 处理流程：
     * 1. batchdownloadfile 用 fileID 换取临时下载链接
     * 2. 下载图片二进制
     * 3. 兜底校验：大小 ≤1MB（imgSecCheck 硬限制）、magic number（jpg/png/webp）
     * 4. imgSecCheck 内容安全检测（87014 违规 → 400「图片包含违规内容，无法上传」）
     * 5. 转存 COS（key: ugc/{yyyyMMdd}/{uuid}.{ext}）
     *
     * @param fileId 微信云存储文件标识（cloud://env.bucket/path）
     * @return Map，包含 url（COS 绝对地址）
     * @throws com.bjtufood.common.exception.BusinessException fileId 不合法/文件失效/超限/违规/存储未配置
     */
    Map<String, String> uploadCloudImage(String fileId);
}
