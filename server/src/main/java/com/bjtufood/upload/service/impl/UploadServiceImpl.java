package com.bjtufood.upload.service.impl;

import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.upload.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    /** 单图大小上限 5MB（与 spring.servlet.multipart.max-file-size 一致，服务层再兜底一次） */
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private final ImageUrlUtil imageUrlUtil;

    @Value("${upload.path:./uploads/images}")
    private String uploadPath;

    @Value("${upload.url-prefix:/images}")
    private String urlPrefix;

    @Override
    public Map<String, String> uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("图片大小不能超过 5MB");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("仅支持 jpg、jpeg、png、webp 图片");
        }

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String filename = UUID.randomUUID() + "." + extension.toLowerCase(Locale.ROOT);
        Path dir = Paths.get(uploadPath, datePath).toAbsolutePath().normalize();

        Path target = dir.resolve(filename);
        try {
            Files.createDirectories(dir);
            // transferTo 内部由 Spring 负责流的开启与关闭（try-with-resources 语义），此处不手工持有流
            file.transferTo(target);
        } catch (IOException | RuntimeException e) {
            // 落盘中途失败可能留下半成品文件，主动清理避免磁盘垃圾
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // 清理失败不影响主流程错误返回
            }
            throw new BusinessException("图片上传失败");
        }

        String relativeUrl = trimEnd(urlPrefix, "/") + "/" + datePath + "/" + filename;
        String absoluteUrl = imageUrlUtil.toAbsoluteUrl(relativeUrl);

        Map<String, String> result = new HashMap<>();
        result.put("url", absoluteUrl);
        result.put("relativeUrl", relativeUrl);
        return result;
    }

    private String trimEnd(String value, String suffix) {
        while (value.endsWith(suffix)) {
            value = value.substring(0, value.length() - suffix.length());
        }
        return value;
    }
}
