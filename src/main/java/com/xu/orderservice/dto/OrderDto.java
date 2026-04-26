package com.xu.orderservice.dto;

import com.xu.orderservice.entity.LockStrategy;
import com.xu.orderservice.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDto(
        Long id,
        String orderNo,
        Long userId,
        OrderStatus status,
        BigDecimal totalAmount,
        LockStrategy lockStrategy,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        List<OrderItemDto> items
) {
    public record OrderItemDto(
            Long productId,
            String sku,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {}
}
