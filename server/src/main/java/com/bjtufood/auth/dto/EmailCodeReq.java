package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
@Schema(description = "邮箱验证码请求参数（邮箱可由学号推导，二选一）")
public class EmailCodeReq {

    @Schema(description = "学号/账号；填此字段时邮箱自动推导为 {学号}@bjtu.edu.cn，无需再传 email", example = "20240001")
    private String username;

    @Email(message = "邮箱格式不正确")
    @Schema(description = "校园邮箱（可选）。不传时由 username 推导为 {username}@bjtu.edu.cn", example = "20240001@bjtu.edu.cn")
    private String email;

    @Schema(description = "验证码用途：仅支持 verify（学号邮箱认证，spec §5.y）。为空时默认为 verify", example = "verify")
    private String purpose;
}
