package com.xu.orderservice.service;

import com.xu.orderservice.dto.ConcurrencyTestRequest;
import com.xu.orderservice.dto.ConcurrencyTestResult;
import com.xu.orderservice.dto.CreateOrderRequest;
import com.xu.orderservice.entity.LockStrategy;
import com.xu.orderservice.entity.ProductInventory;
import com.xu.orderservice.exception.NotFoundException;
import com.xu.orderservice.repository.ProductInventoryRepository;
import com.xu.orderservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 併發壓測：
 *  - 啟 N 個執行緒，每個跑 M 筆下單，每筆扣 quantityPerOrder。
 *  - 結束後比對：剩餘庫存 + 成功扣減量 == 起始庫存 → 沒有超賣。
 *  - 也檢查是否出現 availableStock < 0 的超賣現象（理論上不該發生）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConcurrencyTestService {

    private final OrderService orderService;
    private final ProductInventoryRepository inventoryRepo;
    private final UserRepository userRepo;

    public ConcurrencyTestResult run(ConcurrencyTestRequest req) {
        ProductInventory before = inventoryRepo.findByProductId(req.productId())
                .orElseThrow(() -> new NotFoundException("Product not found: " + req.productId()));
        int initial = before.getAvailableStock();
        Long anyUserId = userRepo.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No user; call /api/demo/seed first")).getId();

        LockStrategy strategy = req.lockStrategy() == null ? LockStrategy.OPTIMISTIC : req.lockStrategy();

        ExecutorService pool = Executors.newFixedThreadPool(req.threads());
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        long start = System.currentTimeMillis();

        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int t = 0; t < req.threads(); t++) {
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < req.ordersPerThread(); i++) {
                        try {
                            orderService.create(new CreateOrderRequest(
                                    anyUserId,
                                    List.of(new CreateOrderRequest.Item(req.productId(), req.quantityPerOrder())),
                                    strategy));
                            ok.incrementAndGet();
                        } catch (RuntimeException ex) {
                            fail.incrementAndGet();
                        }
                    }
                }));
            }
            for (Future<?> f : futures) {
                try { f.get(60, TimeUnit.SECONDS); } catch (Exception ignored) {}
            }
        } finally {
            pool.shutdownNow();
        }

        long elapsed = System.currentTimeMillis() - start;
        ProductInventory after = inventoryRepo.findByProductId(req.productId()).orElseThrow();
        int expectedDeducted = ok.get() * req.quantityPerOrder();
        boolean overSold = after.getAvailableStock() < 0
                || (initial - after.getAvailableStock()) != expectedDeducted;

        return new ConcurrencyTestResult(
                req.productId(),
                initial,
                after.getAvailableStock(),
                expectedDeducted,
                ok.get(),
                fail.get(),
                elapsed,
                strategy.name(),
                overSold);
    }
}
