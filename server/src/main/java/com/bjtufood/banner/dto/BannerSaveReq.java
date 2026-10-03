package com.bjtufood.banner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A5 Banner 新增 / 编辑请求（**只收图片地址**）。
 *
 * <p>顺序走 `PUT /admin/banners/sort`（拖拽整体提交）、启停走 `PUT /admin/banners/{id}/status`，
 * 故本请求**不含** `order` / `status`。
 * 图片地址来自 `POST /admin/upload`（**原样入库**：本地链路为站内相对路径、COS 链路为绝对 URL）。
 */
@Data
@Schema(description = "Banner 保存请求")
public class BannerSaveReq {

    @Schema(description = "轮播图地址（经 /admin/upload 取得，原样入库）", example = "/images/seed/banners/1.jpg")
    @NotBlank(message = "请上传 Banner 图片")
    @Size(max = 500, message = "图片地址过长")
    private String imageUrl;
}
