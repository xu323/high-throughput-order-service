package com.xu.orderservice.dto;

import java.time.LocalDateTime;

public record OrderEventDto(
        Long id,
        Long orderId,
        String eventType,
        String payload,
        LocalDateTime createdAt
) {}
