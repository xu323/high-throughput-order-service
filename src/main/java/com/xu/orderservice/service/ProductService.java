package com.xu.orderservice.service;

import com.xu.orderservice.cache.ProductCacheService;
import com.xu.orderservice.dto.CreateProductRequest;
import com.xu.orderservice.dto.InventoryDto;
import com.xu.orderservice.dto.ProductDto;
import com.xu.orderservice.entity.Product;
import com.xu.orderservice.entity.ProductInventory;
import com.xu.orderservice.exception.NotFoundException;
import com.xu.orderservice.mapper.ProductMapper;
import com.xu.orderservice.repository.ProductInventoryRepository;
import com.xu.orderservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepo;
    private final ProductInventoryRepository inventoryRepo;
    private final ProductCacheService cache;
    private final ProductMapper mapper;

    @Transactional
    public ProductDto create(CreateProductRequest req) {
        productRepo.findBySku(req.sku()).ifPresent(p -> {
            throw new IllegalStateException("SKU already exists: " + req.sku());
        });

        Product p = Product.builder()
                .sku(req.sku())
                .name(req.name())
                .description(req.description())
                .price(req.price())
                .build();
        p = productRepo.save(p);

        ProductInventory inv = ProductInventory.builder()
                .productId(p.getId())
                .availableStock(req.initialStock())
                .reservedStock(0)
                .version(0L)
                .build();
        inventoryRepo.save(inv);

        ProductDto dto = mapper.toDto(p);
        cache.putProduct(dto);
        cache.putInventory(mapper.toDto(inv));
        return dto;
    }

    @Transactional(readOnly = true)
    public List<ProductDto> listAll() {
        return productRepo.findAll().stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ProductDto getById(Long id) {
        ProductDto cached = cache.getProduct(id);
        if (cached != null) return cached;
        Product p = productRepo.findById(id).orElseThrow(() -> new NotFoundException("Product not found: " + id));
        ProductDto dto = mapper.toDto(p);
        cache.putProduct(dto);
        return dto;
    }

    @Transactional(readOnly = true)
    public InventoryDto getInventory(Long productId) {
        InventoryDto cached = cache.getInventory(productId);
        if (cached != null) return cached;
        ProductInventory inv = inventoryRepo.findByProductId(productId)
                .orElseThrow(() -> new NotFoundException("Inventory not found: " + productId));
        InventoryDto dto = mapper.toDto(inv);
        cache.putInventory(dto);
        return dto;
    }
}
