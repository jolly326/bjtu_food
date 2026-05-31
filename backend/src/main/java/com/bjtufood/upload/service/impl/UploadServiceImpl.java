package com.bjtufood.upload.service.impl;

import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.upload.service.UploadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class UploadServiceImpl implements UploadService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    @Value("${upload.path:./uploads}")
    private String uploadPath;

    @Override
    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File is empty");
        }
        String original = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(original);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("Unsupported file type");
        }
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String filename = UUID.randomUUID() + "." + extension.toLowerCase(Locale.ROOT);
        Path dir = Path.of(uploadPath, datePath);
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename));
        } catch (IOException e) {
            throw new BusinessException("Upload failed");
        }
        return "/uploads/" + datePath + "/" + filename;
    }
}
