package com.xu.orderservice.exception;

import com.xu.orderservice.common.ErrorCode;

public class NotFoundException extends BusinessException {
    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}
