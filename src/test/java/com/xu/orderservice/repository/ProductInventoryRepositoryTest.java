package com.xu.orderservice.repository;

import com.xu.orderservice.entity.Product;
import com.xu.orderservice.entity.ProductInventory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用 H2 測試 JPA 行為與 SQL 條件式扣庫存。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProductInventoryRepositoryTest {

    @Autowired ProductRepository productRepo;
    @Autowired ProductInventoryRepository inventoryRepo;

    @Test
    void conditional_deduct_returns_1_when_stock_enough() {
        Product p = productRepo.save(Product.builder().sku("X").name("n").price(BigDecimal.ONE).build());
        inventoryRepo.save(ProductInventory.builder().productId(p.getId())
                .availableStock(10).reservedStock(0).version(0L).build());

        int n = inventoryRepo.conditionalDeduct(p.getId(), 5);
        assertThat(n).isEqualTo(1);
        assertThat(inventoryRepo.findByProductId(p.getId()).orElseThrow().getAvailableStock()).isEqualTo(5);
    }

    @Test
    void conditional_deduct_returns_0_when_stock_not_enough() {
        Product p = productRepo.save(Product.builder().sku("X2").name("n").price(BigDecimal.ONE).build());
        inventoryRepo.save(ProductInventory.builder().productId(p.getId())
                .availableStock(2).reservedStock(0).version(0L).build());

        int n = inventoryRepo.conditionalDeduct(p.getId(), 5);
        assertThat(n).isEqualTo(0);
        assertThat(inventoryRepo.findByProductId(p.getId()).orElseThrow().getAvailableStock()).isEqualTo(2);
    }

    @Test
    void restock_increases_available_stock() {
        Product p = productRepo.save(Product.builder().sku("X3").name("n").price(BigDecimal.ONE).build());
        inventoryRepo.save(ProductInventory.builder().productId(p.getId())
                .availableStock(0).reservedStock(0).version(0L).build());

        int n = inventoryRepo.restock(p.getId(), 7);
        assertThat(n).isEqualTo(1);
        assertThat(inventoryRepo.findByProductId(p.getId()).orElseThrow().getAvailableStock()).isEqualTo(7);
    }
}
