package com.xu.orderservice.event;

import com.xu.orderservice.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishOrderCreated(OrderEventPayload payload) {
        publish(RabbitMQConfig.RK_ORDER_CREATED, payload);
    }

    public void publishOrderPaid(OrderEventPayload payload) {
        publish(RabbitMQConfig.RK_ORDER_PAID, payload);
    }

    public void publishOrderCancelled(OrderEventPayload payload) {
        publish(RabbitMQConfig.RK_ORDER_CANCELLED, payload);
    }

    public void publishInventoryDeducted(OrderEventPayload payload) {
        publish(RabbitMQConfig.RK_INVENTORY_DEDUCTED, payload);
    }

    private void publish(String routingKey, OrderEventPayload payload) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, payload);
        log.debug("Published event {} for order {}", routingKey, payload.orderNo());
    }
}
