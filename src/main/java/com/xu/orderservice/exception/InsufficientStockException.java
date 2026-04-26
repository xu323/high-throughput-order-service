package com.xu.orderservice.exception;

import com.xu.orderservice.common.ErrorCode;

public class InsufficientStockException extends BusinessException {
    public InsufficientStockException(String message) {
        super(ErrorCode.INSUFFICIENT_STOCK, message);
    }
}
