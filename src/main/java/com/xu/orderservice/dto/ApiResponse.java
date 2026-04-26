package com.xu.orderservice.dto;

import java.time.OffsetDateTime;

/**
 * 統一回應結構：所有 API 回傳這個包裝。
 *  - success：是否成功
 *  - code：錯誤代碼或 OK
 *  - message：描述
 *  - data：實際資料
 *  - timestamp：產生時間（含時區）
 */
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "success", data, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, "OK", message, data, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null, OffsetDateTime.now());
    }
}
