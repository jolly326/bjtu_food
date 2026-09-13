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
import java.awt.Color;
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

    /**
     * 单边像素上限 4000（P1-6）：超过视为「图像炸弹」。
     * 小体积高分辨率图（如几 KB 的 20000×20000 PNG）会在 ImageIO 全图解码时按像素数分配内存，直接 OOM。
     */
    private static final int MAX_IMAGE_DIMENSION = 4000;

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

        // 图像炸弹防护（P1-6）：全图解码前先解析头部宽高，超 4000×4000 直接拒绝并清理原图，
        // 防止小体积高分辨率图片在 ImageIO 解码时耗尽堆内存
        try {
            validateImageDimensions(target, normalizedExt);
        } catch (BusinessException e) {
            deleteQuietly(target);
            throw e;
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
        // 缩略图统一以 JPEG 编码输出（兼容性好、体积小），因此扩展名固定为 .jpg，
        // 避免原实现「jpg 内容写入 .png 文件名」导致扩展名与实际格式不符。
        String baseName = target.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        String thumbFilename = baseName + "_thumb.jpg";
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
            // 先铺白底，避免透明 PNG 缩放后底色为黑（原实现缺此步造成黑底 bug）
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, thumbWidth, thumbHeight);
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

    /**
     * 校验图片头部宽高（P1-6 图像炸弹防护）：超过 {@value MAX_IMAGE_DIMENSION}px 直接拒绝。
     * <p>
     * 只解析文件头部字节、不解码像素，成本 O(几十字节)。仅校验 jpg/jpeg/png
     * （这三个格式会进入 ImageIO 全图解码；webp 无原生解码器，不存在该风险）。
     * 解析失败（截断/非标头）不拦截：magic number 已在前置步骤校验，此处防刷为主。
     */
    private void validateImageDimensions(Path file, String ext) {
        if (!Set.of("jpg", "jpeg", "png").contains(ext)) {
            return;
        }
        int[] dims;
        try (java.io.InputStream in = Files.newInputStream(file)) {
            dims = "png".equals(ext) ? readPngDimensions(in) : readJpegDimensions(in);
        } catch (IOException e) {
            // 头部读取失败不拦截，交由后续 ImageIO 解码与既有异常处理兜底
            return;
        }
        if (dims != null && (dims[0] > MAX_IMAGE_DIMENSION || dims[1] > MAX_IMAGE_DIMENSION)) {
            throw new BusinessException(400, "图片尺寸过大，宽和高均不能超过 4000 像素");
        }
    }

    /**
     * PNG 宽高解析：IHDR chunk 固定位于文件头 16 字节后，宽高各占 4 字节大端序。
     */
    private int[] readPngDimensions(java.io.InputStream in) throws IOException {
        byte[] head = in.readNBytes(24);
        if (head.length < 24) {
            return null;
        }
        long width = ((head[16] & 0xFFL) << 24) | ((head[17] & 0xFFL) << 16)
                | ((head[18] & 0xFFL) << 8) | (head[19] & 0xFFL);
        long height = ((head[20] & 0xFFL) << 24) | ((head[21] & 0xFFL) << 16)
                | ((head[22] & 0xFFL) << 8) | (head[23] & 0xFFL);
        return new int[]{(int) width, (int) height};
    }

    /**
     * JPEG 宽高解析：逐段扫描 SOFn（0xFFC0~0xFFCF，排除 C4/C8/CC 非帧标记），
     * 段内依次为精度(1B)/高(2B 大端)/宽(2B 大端)。仅在头部有限范围内扫描，不做全量解码。
     */
    private int[] readJpegDimensions(java.io.InputStream in) throws IOException {
        byte[] soi = in.readNBytes(2);
        if (soi.length < 2 || (soi[0] & 0xFF) != 0xFF || (soi[1] & 0xFF) != 0xD8) {
            return null;
        }
        while (true) {
            int b = in.read();
            if (b == -1) {
                return null;
            }
            if (b != 0xFF) {
                continue;
            }
            int marker = in.read();
            if (marker == -1) {
                return null;
            }
            if (marker == 0xFF) {
                // 编码器填充的连续 0xFF，继续找有效标记
                continue;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD9)) {
                // 无长度字段的独立标记（TEM/RST/SOI/EOI），跳过
                continue;
            }
            int hi = in.read();
            int lo = in.read();
            if (hi == -1 || lo == -1) {
                return null;
            }
            int segLen = (hi << 8) | lo;
            if (segLen < 2) {
                return null;
            }
            if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                // 命中 SOFn：跳过 1 字节精度后读高、宽
                byte[] data = in.readNBytes(5);
                if (data.length < 5) {
                    return null;
                }
                int height = ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
                int width = ((data[3] & 0xFF) << 8) | (data[4] & 0xFF);
                return new int[]{width, height};
            }
            // 跳过非帧段载荷（长度含 2 字节长度字段自身）
            long toSkip = segLen - 2L;
            while (toSkip > 0) {
                long skipped = in.skip(toSkip);
                if (skipped <= 0) {
                    return null;
                }
                toSkip -= skipped;
            }
        }
    }

    /** 静默删除文件（拒绝超尺寸图时清理已落盘的原图，避免磁盘垃圾） */
    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 清理失败不影响主流程错误返回
        }
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
