package com.bjtufood.auth.service;

/**
 * 邮箱验证码服务接口
 * <p>
 * 负责生成验证码、校验邮箱合法性、发送邮件。
 * 由 EmailCodeServiceImpl 实现真实 SMTP 发送逻辑。
 */
public interface EmailCodeService {

    /**
     * 生成并发送邮箱验证码
     *
     * @param email   收件邮箱
     * @param purpose 用途（login / register）
     */
    void sendCode(String email, String purpose);
}
