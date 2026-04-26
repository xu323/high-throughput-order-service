package com.xu.orderservice.controller;

import com.xu.orderservice.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tag.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Health")
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "service", "high-throughput-order-service"));
    }
}
