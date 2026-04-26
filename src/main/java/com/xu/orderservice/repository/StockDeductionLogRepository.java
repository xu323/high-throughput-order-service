package com.xu.orderservice.repository;

import com.xu.orderservice.entity.StockDeductionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockDeductionLogRepository extends JpaRepository<StockDeductionLog, Long> {
}
