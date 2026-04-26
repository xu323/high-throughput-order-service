package com.xu.orderservice.dto;

import java.io.Serializable;

public record InventoryDto(
        Long productId,
        Integer availableStock,
        Integer reservedStock,
        Long version
) implements Serializable {}
