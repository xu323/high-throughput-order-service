package com.xu.orderservice.event;

import com.xu.orderservice.config.RabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitMQEventPublisherTest {

    @Mock RabbitTemplate template;
    @InjectMocks OrderEventPublisher publisher;

    @Test
    void publishOrderCreated_uses_correct_exchange_and_routing_key() {
        OrderEventPayload p = OrderEventPayload.of("ORDER_CREATED", 1L, "ORD-1", Map.of("k", "v"));
        publisher.publishOrderCreated(p);
        verify(template).convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq(RabbitMQConfig.RK_ORDER_CREATED), eq(p));
    }

    @Test
    void publishOrderPaid_uses_correct_routing_key() {
        OrderEventPayload p = OrderEventPayload.of("ORDER_PAID", 1L, "ORD-1", Map.of());
        publisher.publishOrderPaid(p);
        verify(template).convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq(RabbitMQConfig.RK_ORDER_PAID), eq(p));
    }

    @Test
    void publishOrderCancelled_uses_correct_routing_key() {
        OrderEventPayload p = OrderEventPayload.of("ORDER_CANCELLED", 1L, "ORD-1", Map.of());
        publisher.publishOrderCancelled(p);
        verify(template).convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq(RabbitMQConfig.RK_ORDER_CANCELLED), eq(p));
    }

    @Test
    void publishInventoryDeducted_uses_correct_routing_key() {
        OrderEventPayload p = OrderEventPayload.of("INVENTORY_DEDUCTED", 1L, "ORD-1", Map.of());
        publisher.publishInventoryDeducted(p);
        verify(template).convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq(RabbitMQConfig.RK_INVENTORY_DEDUCTED), eq(p));
    }
}
