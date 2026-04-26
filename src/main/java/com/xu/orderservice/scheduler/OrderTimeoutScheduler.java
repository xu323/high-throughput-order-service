package com.xu.orderservice.scheduler;

import com.xu.orderservice.entity.Order;
import com.xu.orderservice.entity.OrderStatus;
import com.xu.orderservice.repository.OrderRepository;
import com.xu.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 逾時訂單清理：每分鐘掃描一次，把過了 expiresAt 的 CREATED 訂單轉為 EXPIRED 並補回庫存。
 * - 每筆訂單交由 OrderService.expire() 在獨立交易中處理（呼叫的是另一個 bean，所以 @Transactional 有效）。
 * - 若有多個 instance，建議改成分散式排程（如 ShedLock、Quartz cluster）以避免重複處理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private final OrderRepository orderRepo;
    private final OrderService orderService;

    @Scheduled(cron = "${order.scheduler.cron:0 */1 * * * *}")
    public void expire() {
        List<Order> expired = orderRepo.findExpiredOrders(OrderStatus.CREATED, LocalDateTime.now());
        if (expired.isEmpty()) return;
        log.info("Found {} expired orders, expiring...", expired.size());
        for (Order o : expired) {
            try {
                orderService.expire(o.getId());
            } catch (Exception e) {
                log.warn("Failed to expire order {}", o.getId(), e);
            }
        }
    }
}
