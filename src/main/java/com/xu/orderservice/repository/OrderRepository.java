package com.xu.orderservice.repository;

import com.xu.orderservice.entity.Order;
import com.xu.orderservice.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    List<Order> findTop100ByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("""
            select o from Order o
             where o.status = :status
               and o.expiresAt is not null
               and o.expiresAt < :now
            """)
    List<Order> findExpiredOrders(@Param("status") OrderStatus status,
                                  @Param("now") LocalDateTime now);
}
