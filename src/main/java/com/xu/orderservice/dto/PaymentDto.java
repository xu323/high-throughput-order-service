package com.xu.orderservice.dto;

import com.xu.orderservice.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentDto(
        Long id,
        Long orderId,
        BigDecimal amount,
        PaymentStatus status,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {}
