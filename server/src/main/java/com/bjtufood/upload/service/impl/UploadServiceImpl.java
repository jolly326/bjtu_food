package com.bjtufood.upload.service.impl;

import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.upload.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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

        // 文件头 magic number 校验：防止扩展名伪造的恶意文件（如将 .exe 改名为 .png）
        try (java.io.InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (!isImageMagic(header, read)) {
                throw new BusinessException("文件内容不是合法的图片");
            }
        } catch (IOException e) {
            throw new BusinessException("文件读取失败");
        }

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String normalizedExt = extension.toLowerCase(Locale.ROOT);
        String filename = UUID.randomUUID() + "." + normalizedExt;
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

        // 生成缩略图（仅 jpg/jpeg/png，ImageIO 原生支持；webp 降级不生成）。失败静默，不阻塞上传主流程。
        String thumbRelativeUrl = generateThumbnail(target, normalizedExt, datePath);

        String relativeUrl = trimEnd(urlPrefix, "/") + "/" + datePath + "/" + filename;
        String absoluteUrl = imageUrlUtil.toAbsoluteUrl(relativeUrl);

        Map<String, String> result = new HashMap<>();
        result.put("url", absoluteUrl);
        result.put("relativeUrl", relativeUrl);
        if (thumbRelativeUrl != null) {
            result.put("thumbUrl", imageUrlUtil.toAbsoluteUrl(thumbRelativeUrl));
            result.put("thumbRelativeUrl", thumbRelativeUrl);
        }
        return result;
    }

    /**
     * 用 ImageIO 为原图生成宽 400px 的等比缩略图，命名 {base}_thumb.{ext} 同目录落盘。
     * 仅 jpg/jpeg/png 生成；webp 或生成失败时返回 null（降级，不抛异常、不影响上传主流程）。
     *
     * @return 缩略图相对 URL（/images/yyyy/MM/xxx_thumb.ext），失败返回 null
     */
    private String generateThumbnail(Path target, String ext, String datePath) {
        if (!Set.of("jpg", "jpeg", "png").contains(ext)) {
            return null;
        }
        String thumbFilename = target.getFileName().toString().replaceFirst("\\.([^.]+)$", "_thumb.$1");
        Path thumb = target.getParent().resolve(thumbFilename);
        try {
            BufferedImage original = ImageIO.read(target.toFile());
            if (original == null) {
                return null;
            }
            int thumbWidth = 400;
            int thumbHeight = Math.max(1, (int) Math.round(original.getHeight() * (thumbWidth / (double) original.getWidth())));
            BufferedImage scaled = new BufferedImage(thumbWidth, thumbHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(original, 0, 0, thumbWidth, thumbHeight, null);
            g.dispose();
            // 统一按 jpg 输出缩略图（体积小、兼容性最好），降低失败面；透明度统一铺白底
            if (!ImageIO.write(scaled, "jpg", thumb.toFile())) {
                return null;
            }
        } catch (IOException | RuntimeException e) {
            // 生成失败静默降级：清理半成品缩略图，不影响上传主流程
            try {
                Files.deleteIfExists(thumb);
            } catch (IOException ignored) {
                // 忽略清理失败
            }
            return null;
        }
        return trimEnd(urlPrefix, "/") + "/" + datePath + "/" + thumbFilename;
    }

    private String trimEnd(String value, String suffix) {
        while (value.endsWith(suffix)) {
            value = value.substring(0, value.length() - suffix.length());
        }
        return value;
    }

    /**
     * 校验文件头 magic number 是否为常见图片格式（防扩展名伪造）。
     * JPG: FF D8 FF；PNG: 89 50 4E 47 0D 0A 1A 0A；WEBP: RIFF....WEBP（52 49 46 46 ?? ?? ?? ?? 57 45 42 50）
     */
    private boolean isImageMagic(byte[] header, int len) {
        if (len < 3) return false;
        // JPEG: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return true;
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (len >= 8
                && (header[0] & 0xFF) == 0x89 && (header[1] & 0xFF) == 0x50
                && (header[2] & 0xFF) == 0x4E && (header[3] & 0xFF) == 0x47
                && (header[4] & 0xFF) == 0x0D && (header[5] & 0xFF) == 0x0A
                && (header[6] & 0xFF) == 0x1A && (header[7] & 0xFF) == 0x0A) {
            return true;
        }
        // WEBP: 52 49 46 46 ?? ?? ?? ?? 57 45 42 50
        if (len >= 12
                && (header[0] & 0xFF) == 0x52 && (header[1] & 0xFF) == 0x49
                && (header[2] & 0xFF) == 0x46 && (header[3] & 0xFF) == 0x46
                && (header[8] & 0xFF) == 0x57 && (header[9] & 0xFF) == 0x45
                && (header[10] & 0xFF) == 0x42 && (header[11] & 0xFF) == 0x50) {
            return true;
        }
        return false;
    }
}
