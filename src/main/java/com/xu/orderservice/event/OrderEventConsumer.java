package com.xu.orderservice.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xu.orderservice.config.RabbitMQConfig;
import com.xu.orderservice.entity.OrderEvent;
import com.xu.orderservice.repository.OrderEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 訂單事件 consumer：
 *  - 收到事件 → 寫入 order_events（審計）
 *  - 部分 consumer 額外做副作用（例如付款後寄信、通知等；本專案以 log 模擬）
 *
 * 失敗重試 / 死信策略：
 *  - application.yml 已啟用 spring.rabbitmq.listener.simple.retry.*
 *  - queue 在 RabbitMQConfig 已掛 dead letter exchange / queue
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderEventRepository repo;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.Q_ORDER_CREATED)
    public void onOrderCreated(OrderEventPayload payload) {
        record(payload, "ORDER_CREATED");
        log.info("[notify] new order created: {}", payload.orderNo());
    }

    @RabbitListener(queues = RabbitMQConfig.Q_ORDER_PAID)
    @Async("asyncTaskExecutor")
    public void onOrderPaid(OrderEventPayload payload) {
        record(payload, "ORDER_PAID");
        log.info("[notify] order paid: {} amount={}", payload.orderNo(), payload.attributes().get("amount"));
        // 範例：模擬寄送付款通知（耗時操作丟到 ThreadPool）
    }

    @RabbitListener(queues = RabbitMQConfig.Q_ORDER_CANCELLED)
    public void onOrderCancelled(OrderEventPayload payload) {
        record(payload, payload.eventType());
        log.info("[notify] order cancelled/expired: {}", payload.orderNo());
    }

    @RabbitListener(queues = RabbitMQConfig.Q_INVENTORY_DEDUCTED)
    public void onInventoryDeducted(OrderEventPayload payload) {
        record(payload, "INVENTORY_DEDUCTED");
        log.debug("[audit] inventory deducted for order {} : {}", payload.orderNo(), payload.attributes());
    }

    /** 死信佇列範例 listener：方便初學者觀察重試耗盡後流向。 */
    @RabbitListener(queues = RabbitMQConfig.Q_ORDER_CREATED + RabbitMQConfig.DLQ_SUFFIX)
    public void onDlq(OrderEventPayload payload) {
        log.error("[DLQ] order.created event entered DLQ: {}", payload);
    }

    private void record(OrderEventPayload payload, String type) {
        try {
            String json = objectMapper.writeValueAsString(payload.attributes());
            repo.save(OrderEvent.builder()
                    .orderId(payload.orderId())
                    .eventType(type)
                    .payload(json)
                    .build());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize event payload", e);
        }
    }
}
