package com.xu.orderservice.service;

import com.xu.orderservice.dto.OrderEventDto;
import com.xu.orderservice.mapper.OrderMapper;
import com.xu.orderservice.repository.OrderEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderEventQueryService {

    private final OrderEventRepository repo;
    private final OrderMapper mapper;

    @Transactional(readOnly = true)
    public List<OrderEventDto> listByOrder(Long orderId) {
        return repo.findByOrderIdOrderByCreatedAtAsc(orderId).stream().map(mapper::toDto).toList();
    }
}
