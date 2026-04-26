package com.xu.orderservice.service;

import com.xu.orderservice.cache.ProductCacheService;
import com.xu.orderservice.entity.Product;
import com.xu.orderservice.entity.ProductInventory;
import com.xu.orderservice.entity.User;
import com.xu.orderservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 一鍵 seed / reset：方便初學者體驗系統。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoService {

    private final UserRepository userRepo;
    private final ProductRepository productRepo;
    private final ProductInventoryRepository inventoryRepo;
    private final OrderItemRepository orderItemRepo;
    private final OrderRepository orderRepo;
    private final PaymentRepository paymentRepo;
    private final OrderEventRepository orderEventRepo;
    private final StockDeductionLogRepository stockLogRepo;
    private final ProductCacheService cache;

    @Transactional
    public Map<String, Object> seed() {
        if (userRepo.count() == 0) {
            userRepo.save(User.builder().username("alice").email("alice@example.com").build());
            userRepo.save(User.builder().username("bob").email("bob@example.com").build());
        }
        if (productRepo.count() == 0) {
            createProductWithStock("SKU-DEMO-1", "Demo Sneakers", new BigDecimal("3990.00"), 100);
            createProductWithStock("SKU-DEMO-2", "Demo Ticket",   new BigDecimal("2500.00"), 50);
            createProductWithStock("SKU-DEMO-3", "Demo Skin Pack", new BigDecimal("199.00"), 1000);
        }
        return Map.of(
                "users", userRepo.count(),
                "products", productRepo.count(),
                "inventory", inventoryRepo.count());
    }

    private void createProductWithStock(String sku, String name, BigDecimal price, int stock) {
        Product p = productRepo.save(Product.builder().sku(sku).name(name).price(price).build());
        inventoryRepo.save(ProductInventory.builder()
                .productId(p.getId()).availableStock(stock).reservedStock(0).version(0L).build());
    }

    /**
     * 清除「下單相關」資料；保留商品、使用者、庫存（庫存補回到 100/50/1000）。
     */
    @Transactional
    public Map<String, Object> reset() {
        stockLogRepo.deleteAllInBatch();
        orderEventRepo.deleteAllInBatch();
        paymentRepo.deleteAllInBatch();
        orderItemRepo.deleteAllInBatch();
        orderRepo.deleteAllInBatch();

        // 對所有商品的庫存清掉 reserved，補回 available（以原本 seed 的數量為主）
        for (ProductInventory inv : inventoryRepo.findAll()) {
            int reset = switch (inv.getProductId().intValue() % 3) {
                case 1 -> 100;
                case 2 -> 50;
                default -> 1000;
            };
            inv.setAvailableStock(reset);
            inv.setReservedStock(0);
            inventoryRepo.save(inv);
            cache.evictInventory(inv.getProductId());
        }

        return Map.of(
                "orders", orderRepo.count(),
                "events", orderEventRepo.count());
    }
}
