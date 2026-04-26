package com.xu.orderservice.dto;

import com.xu.orderservice.entity.LockStrategy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record CreateOrderRequest(
        @NotNull Long userId,
        @NotEmpty @Valid List<Item> items,
        LockStrategy lockStrategy   // 可選；預設 OPTIMISTIC
) {
    public record Item(
            @NotNull Long productId,
            @NotNull @Min(1) Integer quantity
    ) {}
}
