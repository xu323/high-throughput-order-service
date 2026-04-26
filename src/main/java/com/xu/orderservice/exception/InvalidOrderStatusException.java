package com.xu.orderservice.exception;

import com.xu.orderservice.common.ErrorCode;

public class InvalidOrderStatusException extends BusinessException {
    public InvalidOrderStatusException(String message) {
        super(ErrorCode.INVALID_ORDER_STATUS, message);
    }
}
