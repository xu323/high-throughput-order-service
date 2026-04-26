package com.xu.orderservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品庫存表。
 * - 與 products 1:1，但獨立成表是為了避免熱資料行影響商品的一般讀取。
 * - version 欄位用於 JPA 樂觀鎖；@Version 由 Hibernate 自動處理。
 */
@Entity
@Table(name = "product_inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductInventory implements Serializable {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "available_stock", nullable = false)
    private Integer availableStock;

    @Column(name = "reserved_stock", nullable = false)
    private Integer reservedStock;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
