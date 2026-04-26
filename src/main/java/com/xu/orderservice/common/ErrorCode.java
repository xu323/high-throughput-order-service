package com.xu.orderservice.common;

/**
 * 統一錯誤碼。對外 API 使用 code 而非 HTTP status code 表示業務錯誤。
 */
public final class ErrorCode {

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";
    public static final String INVALID_ORDER_STATUS = "INVALID_ORDER_STATUS";
    public static final String LOCK_ACQUISITION_FAILED = "LOCK_ACQUISITION_FAILED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCode() {}
}
