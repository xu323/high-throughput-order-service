package com.xu.orderservice.service;

import com.xu.orderservice.cache.ProductCacheService;
import com.xu.orderservice.entity.LockStrategy;
import com.xu.orderservice.entity.ProductInventory;
import com.xu.orderservice.entity.StockDeductionLog;
import com.xu.orderservice.exception.InsufficientStockException;
import com.xu.orderservice.exception.LockAcquisitionFailedException;
import com.xu.orderservice.exception.NotFoundException;
import com.xu.orderservice.lock.RedisLockService;
import com.xu.orderservice.repository.ProductInventoryRepository;
import com.xu.orderservice.repository.StockDeductionLogRepository;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 庫存服務：兩種防超賣策略
 *  - OPTIMISTIC：JPA @Version 樂觀鎖 + 條件式 UPDATE，最多重試 N 次。
 *  - REDIS_LOCK：Redis 分散式鎖串行化臨界區，再進行扣減。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final int MAX_RETRIES = 5;

    private final ProductInventoryRepository inventoryRepo;
    private final StockDeductionLogRepository logRepo;
    private final RedisLockService redisLock;
    private final ProductCacheService cache;

    /**
     * 進入點：依策略扣減庫存。
     * 此方法本身為 @Transactional，會與外部呼叫端（OrderService.create）合併到同一交易；
     * 任一 item 扣減失敗，整張訂單會 rollback，避免部分扣減成功的不一致狀態。
     */
    @Transactional
    public void deduct(Long productId, int qty, Long orderId, LockStrategy strategy) {
        if (qty <= 0) throw new IllegalArgumentException("qty must be positive");
        try {
            switch (strategy) {
                case OPTIMISTIC -> deductOptimistic(productId, qty, orderId);
                case REDIS_LOCK -> deductWithRedisLock(productId, qty, orderId);
            }
            cache.evictInventory(productId);
        } catch (RuntimeException ex) {
            writeLog(productId, orderId, qty, strategy, false, ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public void restock(Long productId, int qty) {
        int n = inventoryRepo.restock(productId, qty);
        if (n == 0) throw new NotFoundException("Inventory not found: " + productId);
        cache.evictInventory(productId);
    }

    // ---------------- OPTIMISTIC ----------------
    private void deductOptimistic(Long productId, int qty, Long orderId) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                int n = inventoryRepo.conditionalDeduct(productId, qty);
                if (n == 0) {
                    // 沒扣到 → 庫存不足
                    inventoryRepo.findByProductId(productId)
                            .orElseThrow(() -> new NotFoundException("Inventory not found: " + productId));
                    throw new InsufficientStockException(
                            "Insufficient stock for product %d, need %d".formatted(productId, qty));
                }
                writeLog(productId, orderId, qty, LockStrategy.OPTIMISTIC, true, null);
                return;
            } catch (OptimisticLockException | OptimisticLockingFailureException e) {
                if (attempt >= MAX_RETRIES) {
                    throw new LockAcquisitionFailedException(
                            "Optimistic lock failed after %d attempts".formatted(attempt));
                }
                sleepBackoff(attempt);
            }
        }
    }

    // ---------------- REDIS_LOCK ----------------
    private void deductWithRedisLock(Long productId, int qty, Long orderId) {
        String key = "lock:inventory:%d".formatted(productId);
        try {
            Boolean done = redisLock.runWithLock(key, () -> {
                ProductInventory inv = inventoryRepo.findForUpdate(productId)
                        .orElseThrow(() -> new NotFoundException("Inventory not found: " + productId));
                if (inv.getAvailableStock() < qty) {
                    throw new InsufficientStockException(
                            "Insufficient stock for product %d, need %d".formatted(productId, qty));
                }
                inv.setAvailableStock(inv.getAvailableStock() - qty);
                inventoryRepo.save(inv);
                writeLog(productId, orderId, qty, LockStrategy.REDIS_LOCK, true, null);
                return true;
            });
            if (done == null) {
                throw new LockAcquisitionFailedException("Redis lock not acquired for product " + productId);
            }
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new LockAcquisitionFailedException("Redis lock callback failed: " + e.getMessage());
        }
    }

    private void writeLog(Long productId, Long orderId, int qty, LockStrategy strategy, boolean ok, String err) {
        try {
            logRepo.save(StockDeductionLog.builder()
                    .productId(productId)
                    .orderId(orderId == null ? -1L : orderId)
                    .quantity(qty)
                    .strategy(strategy)
                    .success(ok)
                    .errorMsg(err)
                    .build());
        } catch (Exception e) {
            log.warn("write deduction log failed", e);
        }
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(Math.min(50L * attempt, 200L));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
