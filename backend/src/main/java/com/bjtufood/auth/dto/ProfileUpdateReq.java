package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人信息请求 DTO
 * <p>
 * PUT /auth/profile 的请求体
 */
@Data
@Schema(description = "修改个人信息请求参数")
public class ProfileUpdateReq {

    @Size(max = 20, message = "昵称最多20字")
    @Schema(description = "新昵称", example = "新昵称")
    private String nickname;

    @Schema(description = "头像相对路径（需先通过上传接口获取）", example = "/images/2026/05/xxx.jpg")
    private String avatar;
}
