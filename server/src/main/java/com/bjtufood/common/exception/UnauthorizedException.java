package com.bjtufood.common.exception;

/**
 * 未登录异常
 * <p>
 * JwtAuthFilter 在校验 Token 时，如果请求未携带有效 Token，
 * 抛出此异常，由 GlobalExceptionHandler 返回 401 状态码。
 * <p>
 * 触发场景：
 * - 未携带 Authorization 头
 * - Token 已过期
 * - Token 格式错误
 * - Token 签名不合法
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
