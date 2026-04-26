package com.xu.orderservice.service;

import com.xu.orderservice.common.OrderNoGenerator;
import com.xu.orderservice.dto.CreateOrderRequest;
import com.xu.orderservice.dto.OrderDto;
import com.xu.orderservice.entity.LockStrategy;
import com.xu.orderservice.entity.Order;
import com.xu.orderservice.entity.OrderStatus;
import com.xu.orderservice.entity.Product;
import com.xu.orderservice.event.OrderEventPublisher;
import com.xu.orderservice.exception.InvalidOrderStatusException;
import com.xu.orderservice.exception.NotFoundException;
import com.xu.orderservice.mapper.OrderMapper;
import com.xu.orderservice.repository.OrderRepository;
import com.xu.orderservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepo;
    @Mock ProductRepository productRepo;
    @Mock InventoryService inventoryService;
    @Mock OrderEventPublisher eventPublisher;
    @Mock OrderNoGenerator noGen;
    @Mock OrderMapper mapper;

    @InjectMocks OrderService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "timeoutMinutes", 15);
    }

    @Test
    void create_calls_inventory_deduct_and_publishes_events() {
        when(noGen.next()).thenReturn("ORD-1");
        when(productRepo.findById(10L)).thenReturn(Optional.of(
                Product.builder().id(10L).sku("S").name("n").price(new BigDecimal("100.00")).build()));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });
        when(mapper.toDto(any(Order.class))).thenReturn(
                new OrderDto(1L, "ORD-1", 1L, OrderStatus.CREATED, new BigDecimal("200.00"),
                        LockStrategy.OPTIMISTIC, null, null, List.of()));

        OrderDto result = service.create(new CreateOrderRequest(
                1L, List.of(new CreateOrderRequest.Item(10L, 2)), null));

        assertThat(result.orderNo()).isEqualTo("ORD-1");
        verify(inventoryService).deduct(eq(10L), eq(2), any(), eq(LockStrategy.OPTIMISTIC));
        verify(eventPublisher).publishInventoryDeducted(any());
        verify(eventPublisher).publishOrderCreated(any());
    }

    @Test
    void cancel_throws_when_status_not_created() {
        Order o = Order.builder().id(1L).status(OrderStatus.PAID).build();
        when(orderRepo.findById(1L)).thenReturn(Optional.of(o));
        assertThatThrownBy(() -> service.cancel(1L)).isInstanceOf(InvalidOrderStatusException.class);
    }

    @Test
    void cancel_restocks_each_item_and_publishes_event() {
        Order o = Order.builder().id(1L).status(OrderStatus.CREATED).orderNo("ORD-1").items(new java.util.ArrayList<>()).build();
        o.addItem(com.xu.orderservice.entity.OrderItem.builder().productId(10L).quantity(2).build());
        when(orderRepo.findById(1L)).thenReturn(Optional.of(o));
        when(mapper.toDto(o)).thenReturn(null);

        service.cancel(1L);

        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryService).restock(10L, 2);
        verify(eventPublisher).publishOrderCancelled(any());
    }

    @Test
    void getById_throws_when_missing() {
        when(orderRepo.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(9L)).isInstanceOf(NotFoundException.class);
    }
}
