package com.xu.orderservice.integration;

import com.xu.orderservice.dto.CreateOrderRequest;
import com.xu.orderservice.entity.LockStrategy;
import com.xu.orderservice.entity.Product;
import com.xu.orderservice.entity.ProductInventory;
import com.xu.orderservice.entity.User;
import com.xu.orderservice.cache.ProductCacheService;
import com.xu.orderservice.event.OrderEventPublisher;
import com.xu.orderservice.repository.ProductInventoryRepository;
import com.xu.orderservice.repository.ProductRepository;
import com.xu.orderservice.repository.UserRepository;
import com.xu.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 並發下單測試：起 16 條執行緒、每條跑 10 筆，每筆扣 1。
 * 庫存只有 50 → 預期 50 筆成功、110 筆失敗、最後庫存歸 0、不超賣。
 *
 * 使用 H2 + JPA 樂觀鎖；同樣的邏輯換成 REDIS_LOCK 也能跑（前提是 Redis 啟動，預設略過）。
 */
@SpringBootTest
@ActiveProfiles("test")
class InventoryConcurrencyIntegrationTest {

    @Autowired UserRepository userRepo;
    @Autowired ProductRepository productRepo;
    @Autowired ProductInventoryRepository inventoryRepo;
    @Autowired OrderService orderService;

    @MockBean OrderEventPublisher eventPublisher;
    @MockBean ProductCacheService cacheService;

    @Test
    void no_oversell_under_concurrent_orders_with_optimistic_lock() throws Exception {
        User u = userRepo.save(User.builder().username("c-user").email("c@example.com").build());
        Product p = productRepo.save(Product.builder().sku("CC-1").name("cc").price(new BigDecimal("10.00")).build());
        inventoryRepo.save(ProductInventory.builder().productId(p.getId())
                .availableStock(50).reservedStock(0).version(0L).build());

        int threads = 16;
        int perThread = 10;
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < perThread; i++) {
                        try {
                            orderService.create(new CreateOrderRequest(u.getId(),
                                    List.of(new CreateOrderRequest.Item(p.getId(), 1)),
                                    LockStrategy.OPTIMISTIC));
                            ok.incrementAndGet();
                        } catch (RuntimeException e) {
                            fail.incrementAndGet();
                        }
                    }
                }));
            }
            for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        int remaining = inventoryRepo.findByProductId(p.getId()).orElseThrow().getAvailableStock();
        assertThat(ok.get()).isEqualTo(50);
        assertThat(fail.get()).isEqualTo(threads * perThread - 50);
        assertThat(remaining).isZero();
    }
}
