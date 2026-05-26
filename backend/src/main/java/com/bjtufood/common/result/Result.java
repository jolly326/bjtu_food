package com.bjtufood.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应封装
 * <p>
 * 所有 Controller 接口统一使用此类作为响应体，格式：
 * <pre>
 * {
 *   "code": 200,       // HTTP 状态码
 *   "message": "成功",  // 提示信息
 *   "data": { ... }    // 业务数据
 * }
 * </pre>
 *
 * @param <T> data 字段的类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一响应结果")
public class Result<T> {

    /** HTTP 状态码（200=成功, 400=参数错误, 401=未登录, 403=无权限, 500=服务器错误） */
    @Schema(description = "状态码", example = "200")
    private int code;

    /** 提示信息 */
    @Schema(description = "提示信息", example = "操作成功")
    private String message;

    /** 业务数据 */
    @Schema(description = "数据")
    private T data;

    // ==================== 静态工厂方法 ====================

    /** 成功（无返回数据） */
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    /** 成功（有返回数据） */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /** 成功（自定义提示） */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    /** 参数错误 */
    public static <T> Result<T> badRequest(String message) {
        return new Result<>(400, message, null);
    }

    /** 未登录 */
    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(401, message, null);
    }

    /** 无权限 */
    public static <T> Result<T> forbidden(String message) {
        return new Result<>(403, message, null);
    }

    /** 资源不存在 */
    public static <T> Result<T> notFound(String message) {
        return new Result<>(404, message, null);
    }

    /** 服务器内部错误 */
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }

    /** 自定义状态码 */
    public static <T> Result<T> of(int code, String message, T data) {
        return new Result<>(code, message, data);
    }
}
