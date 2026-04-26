package com.xu.orderservice.integration;

import com.xu.orderservice.dto.CreateOrderRequest;
import com.xu.orderservice.dto.OrderDto;
import com.xu.orderservice.entity.LockStrategy;
import com.xu.orderservice.entity.Order;
import com.xu.orderservice.entity.OrderStatus;
import com.xu.orderservice.entity.Product;
import com.xu.orderservice.entity.ProductInventory;
import com.xu.orderservice.entity.User;
import com.xu.orderservice.cache.ProductCacheService;
import com.xu.orderservice.event.OrderEventPublisher;
import com.xu.orderservice.repository.OrderRepository;
import com.xu.orderservice.repository.ProductInventoryRepository;
import com.xu.orderservice.repository.ProductRepository;
import com.xu.orderservice.repository.UserRepository;
import com.xu.orderservice.service.OrderService;
import com.xu.orderservice.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端對端：建立訂單 → 付款 → 庫存被扣 → 狀態為 PAID。
 * 用 H2 + 模擬 RabbitTemplate（Mock）跑，避免 CI 必須安裝 RabbitMQ。
 *
 * 測試類別命名以 IntegrationTest 結尾，會被 maven-failsafe-plugin 在 verify phase 執行。
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderFlowIntegrationTest {

    @Autowired UserRepository userRepo;
    @Autowired ProductRepository productRepo;
    @Autowired ProductInventoryRepository inventoryRepo;
    @Autowired OrderRepository orderRepo;
    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;

    // 不啟動真正 RabbitMQ / Redis：Mock 兩者，避免測試依賴外部 broker
    @MockBean OrderEventPublisher eventPublisher;
    @MockBean ProductCacheService cacheService;

    @Test
    void full_flow_create_pay_completes_with_inventory_decreased() {
        User u = userRepo.save(User.builder().username("ti-user").email("ti@example.com").build());
        Product p = productRepo.save(Product.builder().sku("IT-1").name("ittest").price(new BigDecimal("100.00")).build());
        inventoryRepo.save(ProductInventory.builder().productId(p.getId())
                .availableStock(5).reservedStock(0).version(0L).build());

        OrderDto created = orderService.create(new CreateOrderRequest(
                u.getId(),
                List.of(new CreateOrderRequest.Item(p.getId(), 2)),
                LockStrategy.OPTIMISTIC));

        assertThat(created.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(inventoryRepo.findByProductId(p.getId()).orElseThrow().getAvailableStock()).isEqualTo(3);

        OrderDto paid = paymentService.pay(created.id());
        assertThat(paid.status()).isEqualTo(OrderStatus.PAID);

        Order reloaded = orderRepo.findById(created.id()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
    }
}
