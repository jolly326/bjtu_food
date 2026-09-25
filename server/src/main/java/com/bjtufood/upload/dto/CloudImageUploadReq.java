package com.bjtufood.upload.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 小程序云存储配图转存请求（POST /upload/cloud-image）。
 * <p>
 * 前端先 {@code wx.cloud.uploadFile} 上传得到 fileID，再调本接口换取经内容安全检测、
 * 转存 COS 后的最终 URL。
 */
@Data
@Schema(description = "云存储图片转存请求")
public class CloudImageUploadReq {

    @NotBlank(message = "fileId 不能为空")
    @Schema(description = "微信云存储文件标识（wx.cloud.uploadFile 返回，形如 cloud://env.bucket/path）",
            example = "cloud://bjtu-dev.6a6x-bjtu-dev-1300000000/ugc/tmp.jpg", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fileId;
}
