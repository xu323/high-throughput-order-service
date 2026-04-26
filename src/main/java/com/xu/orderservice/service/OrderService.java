package com.xu.orderservice.service;

import com.xu.orderservice.common.OrderNoGenerator;
import com.xu.orderservice.dto.CreateOrderRequest;
import com.xu.orderservice.dto.OrderDto;
import com.xu.orderservice.entity.LockStrategy;
import com.xu.orderservice.entity.Order;
import com.xu.orderservice.entity.OrderItem;
import com.xu.orderservice.entity.OrderStatus;
import com.xu.orderservice.entity.Product;
import com.xu.orderservice.event.OrderEventPayload;
import com.xu.orderservice.event.OrderEventPublisher;
import com.xu.orderservice.exception.InvalidOrderStatusException;
import com.xu.orderservice.exception.NotFoundException;
import com.xu.orderservice.mapper.OrderMapper;
import com.xu.orderservice.repository.OrderRepository;
import com.xu.orderservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepo;
    private final ProductRepository productRepo;
    private final InventoryService inventoryService;
    private final OrderEventPublisher eventPublisher;
    private final OrderNoGenerator noGen;
    private final OrderMapper mapper;

    @Value("${order.timeout-minutes:15}")
    private int timeoutMinutes;

    /**
     * 建立訂單：
     *  1) 建立 Order + OrderItems（CREATED）
     *  2) 對每個 item 扣庫存（依 lockStrategy 選擇樂觀鎖或 Redis 鎖）
     *  3) 發送 order.created / inventory.deducted 事件
     *  4) 回傳 OrderDto
     *
     * 單一交易內完成「建立訂單 + 扣庫存」；任一步驟失敗就 rollback。
     */
    @Transactional
    public OrderDto create(CreateOrderRequest req) {
        LockStrategy strategy = req.lockStrategy() == null ? LockStrategy.OPTIMISTIC : req.lockStrategy();

        Order order = Order.builder()
                .orderNo(noGen.next())
                .userId(req.userId())
                .status(OrderStatus.CREATED)
                .totalAmount(BigDecimal.ZERO)
                .lockStrategy(strategy)
                .expiresAt(LocalDateTime.now().plusMinutes(timeoutMinutes))
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderRequest.Item it : req.items()) {
            Product p = productRepo.findById(it.productId())
                    .orElseThrow(() -> new NotFoundException("Product not found: " + it.productId()));
            BigDecimal subtotal = p.getPrice().multiply(BigDecimal.valueOf(it.quantity()));
            order.addItem(OrderItem.builder()
                    .productId(p.getId())
                    .sku(p.getSku())
                    .quantity(it.quantity())
                    .unitPrice(p.getPrice())
                    .subtotal(subtotal)
                    .build());
            total = total.add(subtotal);
        }
        order.setTotalAmount(total);
        order = orderRepo.save(order);

        // 扣庫存（每個 item 一個獨立的 REQUIRES_NEW 交易，更清楚的失敗邊界）
        for (OrderItem it : order.getItems()) {
            inventoryService.deduct(it.getProductId(), it.getQuantity(), order.getId(), strategy);

            // inventory.deducted 事件
            eventPublisher.publishInventoryDeducted(OrderEventPayload.of(
                    "INVENTORY_DEDUCTED", order.getId(), order.getOrderNo(),
                    Map.of("productId", it.getProductId(), "quantity", it.getQuantity(), "strategy", strategy.name())));
        }

        // order.created 事件
        eventPublisher.publishOrderCreated(OrderEventPayload.of(
                "ORDER_CREATED", order.getId(), order.getOrderNo(),
                Map.of("userId", order.getUserId(), "totalAmount", order.getTotalAmount())));

        return mapper.toDto(order);
    }

    @Transactional(readOnly = true)
    public OrderDto getById(Long id) {
        Order o = orderRepo.findById(id).orElseThrow(() -> new NotFoundException("Order not found: " + id));
        // 強制 init items
        o.getItems().size();
        return mapper.toDto(o);
    }

    @Transactional(readOnly = true)
    public java.util.List<OrderDto> listByUser(Long userId) {
        return orderRepo.findTop100ByUserIdOrderByCreatedAtDesc(userId).stream().map(mapper::toDto).toList();
    }

    /**
     * 取消訂單（使用者主動或逾時觸發）。
     *  - CREATED → CANCELLED 並補回庫存。
     *  - 已 PAID / COMPLETED 不能取消。
     */
    @Transactional
    public OrderDto cancel(Long id) {
        Order o = orderRepo.findById(id).orElseThrow(() -> new NotFoundException("Order not found: " + id));
        if (o.getStatus() != OrderStatus.CREATED) {
            throw new InvalidOrderStatusException(
                    "Cannot cancel order in status: " + o.getStatus());
        }
        o.setStatus(OrderStatus.CANCELLED);
        for (OrderItem it : o.getItems()) {
            inventoryService.restock(it.getProductId(), it.getQuantity());
        }
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("reason", "USER_CANCEL");
        eventPublisher.publishOrderCancelled(OrderEventPayload.of(
                "ORDER_CANCELLED", o.getId(), o.getOrderNo(), attrs));
        return mapper.toDto(o);
    }

    /**
     * 標記訂單已付款（PaymentService 會在內部呼叫此方法）。
     */
    @Transactional
    public OrderDto markPaid(Long id) {
        Order o = orderRepo.findById(id).orElseThrow(() -> new NotFoundException("Order not found: " + id));
        if (o.getStatus() != OrderStatus.CREATED) {
            throw new InvalidOrderStatusException("Cannot pay order in status: " + o.getStatus());
        }
        o.setStatus(OrderStatus.PAID);
        eventPublisher.publishOrderPaid(OrderEventPayload.of(
                "ORDER_PAID", o.getId(), o.getOrderNo(),
                Map.of("amount", o.getTotalAmount())));
        return mapper.toDto(o);
    }

    /**
     * 由 scheduler 呼叫：將指定 ID 的逾時訂單轉為 EXPIRED 並補庫存。
     */
    @Transactional
    public void expire(Long id) {
        Order o = orderRepo.findById(id).orElse(null);
        if (o == null || o.getStatus() != OrderStatus.CREATED) return;
        o.setStatus(OrderStatus.EXPIRED);
        for (OrderItem it : o.getItems()) {
            inventoryService.restock(it.getProductId(), it.getQuantity());
        }
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("reason", "TIMEOUT");
        eventPublisher.publishOrderCancelled(OrderEventPayload.of(
                "ORDER_EXPIRED", o.getId(), o.getOrderNo(), attrs));
    }
}
