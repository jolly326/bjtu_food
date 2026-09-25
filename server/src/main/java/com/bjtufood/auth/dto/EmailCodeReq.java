package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "邮箱验证码请求参数（校园邮箱由学号推导，仅需学号）")
public class EmailCodeReq {

    @NotBlank(message = "请填写学号")
    @Schema(description = "学号/账号；校园邮箱自动推导为 {学号}@bjtu.edu.cn，无需再传 email", example = "20240001")
    private String username;
}
