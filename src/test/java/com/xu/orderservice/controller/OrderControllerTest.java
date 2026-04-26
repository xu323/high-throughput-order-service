package com.xu.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xu.orderservice.dto.CreateOrderRequest;
import com.xu.orderservice.dto.OrderDto;
import com.xu.orderservice.entity.LockStrategy;
import com.xu.orderservice.entity.OrderStatus;
import com.xu.orderservice.exception.NotFoundException;
import com.xu.orderservice.service.OrderEventQueryService;
import com.xu.orderservice.service.OrderService;
import com.xu.orderservice.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean OrderService orderService;
    @MockBean PaymentService paymentService;
    @MockBean OrderEventQueryService eventQueryService;

    @Test
    void create_returns_201_with_order_dto() throws Exception {
        OrderDto dto = new OrderDto(1L, "ORD-1", 1L, OrderStatus.CREATED,
                new BigDecimal("200.00"), LockStrategy.OPTIMISTIC, null, null, List.of());
        when(orderService.create(any())).thenReturn(dto);

        CreateOrderRequest req = new CreateOrderRequest(
                1L, List.of(new CreateOrderRequest.Item(10L, 2)), LockStrategy.OPTIMISTIC);

        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNo").value("ORD-1"));
    }

    @Test
    void get_returns_404_when_not_found() throws Exception {
        when(orderService.getById(eq(99L))).thenThrow(new NotFoundException("nope"));
        mvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void create_validates_request_body() throws Exception {
        // 缺 userId → 應該被 @NotNull 擋下
        String body = "{ \"items\": [ { \"productId\": 1, \"quantity\": 1 } ] }";
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
