package com.xu.orderservice.mapper;

import com.xu.orderservice.dto.OrderDto;
import com.xu.orderservice.dto.OrderEventDto;
import com.xu.orderservice.dto.PaymentDto;
import com.xu.orderservice.entity.Order;
import com.xu.orderservice.entity.OrderEvent;
import com.xu.orderservice.entity.OrderItem;
import com.xu.orderservice.entity.Payment;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    default OrderDto toDto(Order entity) {
        if (entity == null) return null;
        List<OrderDto.OrderItemDto> items = entity.getItems() == null ? List.of()
                : entity.getItems().stream().map(this::toItemDto).toList();
        return new OrderDto(
                entity.getId(),
                entity.getOrderNo(),
                entity.getUserId(),
                entity.getStatus(),
                entity.getTotalAmount(),
                entity.getLockStrategy(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                items);
    }

    default OrderDto.OrderItemDto toItemDto(OrderItem i) {
        return new OrderDto.OrderItemDto(i.getProductId(), i.getSku(), i.getQuantity(), i.getUnitPrice(), i.getSubtotal());
    }

    default OrderEventDto toDto(OrderEvent e) {
        return new OrderEventDto(e.getId(), e.getOrderId(), e.getEventType(), e.getPayload(), e.getCreatedAt());
    }

    default PaymentDto toDto(Payment p) {
        return new PaymentDto(p.getId(), p.getOrderId(), p.getAmount(), p.getStatus(), p.getPaidAt(), p.getCreatedAt());
    }
}
