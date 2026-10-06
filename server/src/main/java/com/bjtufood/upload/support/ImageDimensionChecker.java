package com.bjtufood.upload.support;

import com.bjtufood.common.exception.BusinessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * 图片尺寸校验器（<b>图像炸弹</b>防护，P1-6）。
 * <p>
 * 防护原理：小体积高分辨率图（如几 KB 的 20000×20000 PNG）会在 {@code ImageIO}
 * 全图解码时<b>按像素数</b>分配内存，直接 OOM。故在解码前先读文件头取宽高做上限拦截。
 * <p>
 * 实现约束：
 * <ul>
 *   <li>只解析文件头部字节、<b>不解码像素</b>，成本 O(几十字节)；</li>
 *   <li>仅校验 jpg/jpeg/png —— 这三种格式会进入 {@code ImageIO} 全图解码；
 *       webp 无原生解码器，不存在该风险；</li>
 *   <li>解析失败（截断 / 非标头）<b>不拦截</b>：magic number 已在调用方前置校验，此处以防刷为主。</li>
 * </ul>
 * <p>
 * <b>归属</b>：自 {@code UploadServiceImpl} 抽出。本逻辑是<b>纯无状态</b>的
 * （只依赖方法参数，不持有 Bean 状态、不参与上传编排），与「两条上传链路 + COS 编排 +
 * 云存储下载」这些有状态编排关注点正交，故可安全独立并以静态方法调用。
 * 抽出后 {@code UploadServiceImpl} 由 574 行降至约 479 行，职责项由 6 项降为 5 项。
 */
public final class ImageDimensionChecker {

    /**
     * 单边像素上限 4000。
     * <p>
     * 超过视为「图像炸弹」。注意本值与 {@code UploadServiceImpl} 的
     * {@code MAX_IMAGE_DIMENSION} 为同一约束，随本类抽出而<b>唯一化</b>。
     */
    public static final int MAX_IMAGE_DIMENSION = 4000;

    /** 会进入 ImageIO 全图解码、需校验尺寸的格式（其余格式无此风险） */
    private static final Set<String> DECODED_FORMATS = Set.of("jpg", "jpeg", "png");

    private ImageDimensionChecker() {
    }

    /**
     * 校验图片宽高，任一边超过 {@value #MAX_IMAGE_DIMENSION}px 即拒绝。
     * <p>
     * 格式不在校验集内、或头部解析失败时<b>静默放行</b>（见类注释的约束说明）。
     *
     * @param file 已落盘的图片文件（仅读头部，不改内容）
     * @param ext  已规范化的扩展名（jpg/jpeg/png/webp 之一）
     * @throws BusinessException 宽或高超限时抛出（code=400）
     */
    public static void validate(Path file, String ext) {
        if (!DECODED_FORMATS.contains(ext)) {
            return;
        }
        int[] dims;
        try (InputStream in = Files.newInputStream(file)) {
            dims = "png".equals(ext) ? readPngDimensions(in) : readJpegDimensions(in);
        } catch (IOException e) {
            // 头部读取失败不拦截，交由后续 ImageIO 解码与既有异常处理兜底
            return;
        }
        if (dims != null && (dims[0] > MAX_IMAGE_DIMENSION || dims[1] > MAX_IMAGE_DIMENSION)) {
            throw new BusinessException(400, "图片尺寸过大，宽和高均不能超过 "
                    + MAX_IMAGE_DIMENSION + " 像素");
        }
    }

    /**
     * PNG 宽高解析：IHDR chunk 固定位于文件头 16 字节后，宽高各占 4 字节大端序。
     *
     * @return {width, height}；头部不足 24 字节时返回 null（交由调用方放行）
     */
    private static int[] readPngDimensions(InputStream in) throws IOException {
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
     *
     * @return {width, height}；非标头 / 截断时返回 null（交由调用方放行）
     */
    private static int[] readJpegDimensions(InputStream in) throws IOException {
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
}
