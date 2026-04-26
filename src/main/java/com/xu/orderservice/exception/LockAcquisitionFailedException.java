package com.xu.orderservice.exception;

import com.xu.orderservice.common.ErrorCode;

public class LockAcquisitionFailedException extends BusinessException {
    public LockAcquisitionFailedException(String message) {
        super(ErrorCode.LOCK_ACQUISITION_FAILED, message);
    }
}
