package com.xu.orderservice.event;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 通用事件結構。所有 publisher / consumer 都用同一份。
 */
public record OrderEventPayload(
        String eventType,
        Long orderId,
        String orderNo,
        Map<String, Object> attributes,
        OffsetDateTime occurredAt
) implements Serializable {

    public static OrderEventPayload of(String type, Long orderId, String orderNo, Map<String, Object> attrs) {
        return new OrderEventPayload(type, orderId, orderNo, attrs, OffsetDateTime.now());
    }
}
