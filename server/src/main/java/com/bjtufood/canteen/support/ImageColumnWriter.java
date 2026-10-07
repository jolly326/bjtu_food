package com.bjtufood.canteen.support;

import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JsonListUtil;

import java.util.List;

/**
 * 管理端图片列的入库编码（食堂 {@code canteen.images} 与档口 {@code stall.images} 共用）。
 *
 * <p>两步口径与 {@code dish.images} 一致：
 * <ol>
 *   <li><b>绝对地址还原为站内相对路径</b>（{@link ImageUrlUtil#toRelativePaths}）——
 *       该列是「相对路径列」，出参才由 {@link ImageUrlUtil} 转绝对 URL；
 *       若把绝对地址直接入库，出参会二次拼域名 ⇒ 图片 404；</li>
 *   <li><b>列宽硬校验</b>：序列化后长度超过列宽即 {@code 400}，<b>禁止静默截断</b>
 *       （截断会产出半截 URL，属最难排查的一类脏数据）。</li>
 * </ol>
 */
public final class ImageColumnWriter {

    private ImageColumnWriter() {
    }

    /**
     * 把图片地址列表编码为可入库的 JSON 字符串。
     *
     * @param imageUrlUtil  URL 归一工具（由调用方注入，避免本类持有 Spring Bean）
     * @param urls          图片地址列表（**调用方须先判非 null** —— null 语义是「不修改」，不是「清空」）
     * @param maxJsonLength 目标列的字符宽度上限（{@code canteen.images} / {@code stall.images} = 1024）
     * @param tooLongMessage 超长时的 {@code 400} 文案（含列宽，便于运营理解）
     * @return JSON 数组字符串（空列表 ⇒ {@code "[]"}）
     * @throws BusinessException code=400 序列化后超长
     */
    public static String encode(ImageUrlUtil imageUrlUtil, List<String> urls, int maxJsonLength,
                                String tooLongMessage) {
        String json = JsonListUtil.toJson(imageUrlUtil.toRelativePaths(urls));
        if (json.length() > maxJsonLength) {
            throw new BusinessException(tooLongMessage);
        }
        return json;
    }
}
