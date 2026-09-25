package com.bjtufood.auth.service;

/**
 * 邮箱验证码服务接口
 * <p>
 * 负责生成验证码、校验邮箱合法性、发送邮件。
 * 由 EmailCodeServiceImpl 实现真实 SMTP 发送逻辑。
 * <p>
 * 校园邮箱规则：邮箱 = {学号}@bjtu.edu.cn，由 username 推导，无需调用方传 email。
 */
public interface EmailCodeService {

    /**
     * 生成并发送邮箱验证码（认证用途 verify，spec §5.y.5）
     * <p>
     * 校园邮箱 = {学号}@bjtu.edu.cn，由 username 推导，无需调用方传 email / purpose。
     *
     * @param username 学号/账号（必填）；收件邮箱推导为 {username}@bjtu.edu.cn
     */
    void sendCode(String username);
}
