package com.xu.orderservice.dto;

import com.xu.orderservice.entity.LockStrategy;
import jakarta.validation.constraints.*;

public record ConcurrencyTestRequest(
        @NotNull Long productId,
        @NotNull @Min(1) Integer threads,
        @NotNull @Min(1) Integer ordersPerThread,
        @NotNull @Min(1) Integer quantityPerOrder,
        LockStrategy lockStrategy
) {}
