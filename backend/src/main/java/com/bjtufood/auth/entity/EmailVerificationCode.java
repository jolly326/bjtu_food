package com.bjtufood.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("email_verification_code")
@Schema(description = "邮箱验证码记录")
public class EmailVerificationCode {

    @TableId(type = IdType.AUTO)
    @Schema(description = "验证码记录ID")
    private Long id;

    @Schema(description = "邮箱地址", example = "20240001@bjtu.edu.cn")
    private String email;

    @Schema(description = "验证码哈希")
    private String codeHash;

    @Schema(description = "用途：login/register", example = "login")
    private String purpose;

    @Schema(description = "过期时间")
    private LocalDateTime expiresAt;

    @Schema(description = "使用时间")
    private LocalDateTime usedAt;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
