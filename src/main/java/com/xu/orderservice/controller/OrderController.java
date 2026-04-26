package com.xu.orderservice.controller;

import com.xu.orderservice.dto.ApiResponse;
import com.xu.orderservice.dto.CreateOrderRequest;
import com.xu.orderservice.dto.OrderDto;
import com.xu.orderservice.dto.OrderEventDto;
import com.xu.orderservice.service.OrderEventQueryService;
import com.xu.orderservice.service.OrderService;
import com.xu.orderservice.service.PaymentService;
import io.swagger.v3.oas.annotations.tag.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Orders")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final OrderEventQueryService eventQueryService;

    @GetMapping
    public ApiResponse<List<OrderDto>> listByUser(@RequestParam Long userId) {
        return ApiResponse.ok(orderService.listByUser(userId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderDto>> create(@Valid @RequestBody CreateOrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(orderService.create(req), "created"));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDto> get(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getById(id));
    }

    @PostMapping("/{id}/pay")
    public ApiResponse<OrderDto> pay(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.pay(id), "paid");
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderDto> cancel(@PathVariable Long id) {
        return ApiResponse.ok(orderService.cancel(id), "cancelled");
    }

    @GetMapping("/{id}/events")
    public ApiResponse<List<OrderEventDto>> events(@PathVariable Long id) {
        return ApiResponse.ok(eventQueryService.listByOrder(id));
    }
}
