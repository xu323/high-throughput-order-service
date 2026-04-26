package com.xu.orderservice.controller;

import com.xu.orderservice.dto.ApiResponse;
import com.xu.orderservice.dto.ConcurrencyTestRequest;
import com.xu.orderservice.dto.ConcurrencyTestResult;
import com.xu.orderservice.service.ConcurrencyTestService;
import com.xu.orderservice.service.DemoService;
import io.swagger.v3.oas.annotations.tag.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Demo / Admin")
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoService demoService;
    private final ConcurrencyTestService concurrencyTestService;

    @PostMapping("/seed")
    public ApiResponse<Map<String, Object>> seed() {
        return ApiResponse.ok(demoService.seed(), "seeded");
    }

    @PostMapping("/reset")
    public ApiResponse<Map<String, Object>> reset() {
        return ApiResponse.ok(demoService.reset(), "reset");
    }

    @PostMapping("/concurrency-test")
    public ApiResponse<ConcurrencyTestResult> concurrencyTest(@Valid @RequestBody ConcurrencyTestRequest req) {
        return ApiResponse.ok(concurrencyTestService.run(req));
    }
}
