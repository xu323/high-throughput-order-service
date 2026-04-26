package com.xu.orderservice.service;

import com.xu.orderservice.dto.OrderDto;
import com.xu.orderservice.entity.Payment;
import com.xu.orderservice.entity.PaymentStatus;
import com.xu.orderservice.exception.NotFoundException;
import com.xu.orderservice.repository.OrderRepository;
import com.xu.orderservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 模擬付款流程：實際只是把 Payment 寫入並把 Order 狀態改為 PAID。
 * 在實務上應整合金流 SDK；本專案聚焦在後端架構，所以模擬即可。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepo;
    private final OrderRepository orderRepo;
    private final OrderService orderService;

    @Transactional
    public OrderDto pay(Long orderId) {
        var order = orderRepo.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));

        Payment p = Payment.builder()
                .orderId(order.getId())
                .amount(order.getTotalAmount())
                .status(PaymentStatus.SUCCESS)
                .paidAt(LocalDateTime.now())
                .build();
        paymentRepo.save(p);

        return orderService.markPaid(orderId);
    }
}
