package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "修改个人资料请求参数")
public class ProfileUpdateReq {

    @Size(max = 20, message = "昵称最多20字")
    @Schema(description = "新的昵称", example = "新的昵称")
    private String nickname;

    @Schema(description = "头像图片路径。建议先调用 /upload/image 获取 URL。", example = "/images/seed/dishes/tomato-egg.jpg")
    private String avatar;
}
