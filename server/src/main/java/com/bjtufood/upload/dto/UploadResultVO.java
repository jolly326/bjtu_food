package com.bjtufood.upload.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片上传出参（{@code POST /upload/cloud-image}、{@code POST /admin/upload/image}）。
 * <p>
 * {@code relativeUrl} 仅本地磁盘降级链路返回（COS 链路无相对路径），故以
 * {@link JsonInclude.Include#NON_NULL} 保持「键缺省」语义，不产出 {@code "relativeUrl": null}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "图片上传结果")
public class UploadResultVO {

    @Schema(description = "可直接访问的图片绝对 URL")
    private String url;

    @Schema(description = "相对路径（仅本地存储降级链路返回，用于入库保存）")
    private String relativeUrl;
}
