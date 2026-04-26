package com.xu.orderservice.controller;

import com.xu.orderservice.dto.ApiResponse;
import com.xu.orderservice.dto.CreateProductRequest;
import com.xu.orderservice.dto.InventoryDto;
import com.xu.orderservice.dto.ProductDto;
import com.xu.orderservice.service.ProductService;
import io.swagger.v3.oas.annotations.tag.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Products")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ApiResponse<List<ProductDto>> list() {
        return ApiResponse.ok(productService.listAll());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductDto>> create(@Valid @RequestBody CreateProductRequest req) {
        ProductDto p = productService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(p, "created"));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductDto> get(@PathVariable Long id) {
        return ApiResponse.ok(productService.getById(id));
    }

    @GetMapping("/{id}/inventory")
    public ApiResponse<InventoryDto> inventory(@PathVariable Long id) {
        return ApiResponse.ok(productService.getInventory(id));
    }
}
