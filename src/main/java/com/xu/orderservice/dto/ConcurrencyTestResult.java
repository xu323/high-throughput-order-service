package com.xu.orderservice.dto;

public record ConcurrencyTestResult(
        Long productId,
        Integer initialStock,
        Integer remainingStock,
        Integer expectedDeducted,
        Integer actualSuccessOrders,
        Integer failedOrders,
        Long elapsedMillis,
        String lockStrategy,
        boolean overSold
) {}
