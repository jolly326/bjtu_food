package com.bjtufood.auth.service;

/**
 * 邮箱验证码服务接口
 * <p>
 * 负责生成验证码、校验邮箱合法性、发送邮件。
 * 由 EmailCodeServiceImpl 实现真实 SMTP 发送逻辑。
 * <p>
 * 校园邮箱规则：邮箱 = {学号}@bjtu.edu.cn。调用方可只传学号（username）或只传 email，
 * 若传 username 且未传 email，则自动推导收件邮箱，无需用户手动输入邮箱。
 */
public interface EmailCodeService {

    /**
     * 生成并发送邮箱验证码（认证用途 verify，spec §5.y.5）
     *
     * @param username 学号/账号（可选）；未传 email 时收件邮箱推导为 {username}@bjtu.edu.cn
     * @param email    收件邮箱（可选）；与 username 二选一
     * @param purpose  用途：仅 verify（学号邮箱认证）；其他取值一律归一为 verify
     */
    void sendCode(String username, String email, String purpose);
}
