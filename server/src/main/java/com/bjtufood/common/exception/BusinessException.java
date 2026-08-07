package com.bjtufood.common.exception;

import lombok.Getter;

/**
 * 业务异常
 * <p>
 * 用于 Service 层主动抛出可预见的业务错误，由 GlobalExceptionHandler 统一处理。
 * 区别于系统异常（NullPointerException 等），业务异常包含友好的提示信息。
 * <p>
 * 使用示例：
 * <pre>
 * if (user == null) {
 *     throw new BusinessException(400, "用户不存在");
 * }
 * if (dish.getStatus().equals("off")) {
 *     throw new BusinessException(400, "该菜品已下架");
 * }
 * </pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务状态码（建议使用 4xx 类状态码） */
    private final int code;

    /**
     * @param code    业务状态码
     * @param message 友好提示信息（会原样返回给前端）
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 快捷构造：默认 code=400 */
    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }
}
