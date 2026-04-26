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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository productRepo;
    @Mock ProductInventoryRepository inventoryRepo;
    @Mock ProductCacheService cache;
    @Mock ProductMapper mapper;

    @InjectMocks ProductService service;

    @Test
    void create_persists_product_and_inventory_and_writes_cache() {
        CreateProductRequest req = new CreateProductRequest("SKU-X", "name", "desc", new BigDecimal("9.99"), 50);

        when(productRepo.findBySku("SKU-X")).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });
        when(inventoryRepo.save(any(ProductInventory.class))).thenAnswer(i -> i.getArgument(0));
        ProductDto fakeDto = new ProductDto(1L, "SKU-X", "name", "desc", new BigDecimal("9.99"), null, null);
        InventoryDto fakeInv = new InventoryDto(1L, 50, 0, 0L);
        when(mapper.toDto(any(Product.class))).thenReturn(fakeDto);
        when(mapper.toDto(any(ProductInventory.class))).thenReturn(fakeInv);

        ProductDto result = service.create(req);

        assertThat(result.id()).isEqualTo(1L);
        verify(cache).putProduct(fakeDto);
        verify(cache).putInventory(fakeInv);
    }

    @Test
    void getById_uses_cache_when_present() {
        ProductDto cached = new ProductDto(1L, "SKU-X", "n", "d", new BigDecimal("1.00"), null, null);
        when(cache.getProduct(1L)).thenReturn(cached);

        ProductDto result = service.getById(1L);

        assertThat(result).isSameAs(cached);
        verifyNoInteractions(productRepo);
    }

    @Test
    void getById_loads_from_db_and_caches_when_miss() {
        Product p = Product.builder().id(1L).sku("SKU-X").name("n").price(new BigDecimal("1.00")).build();
        ProductDto dto = new ProductDto(1L, "SKU-X", "n", null, new BigDecimal("1.00"), null, null);
        when(cache.getProduct(1L)).thenReturn(null);
        when(productRepo.findById(1L)).thenReturn(Optional.of(p));
        when(mapper.toDto(p)).thenReturn(dto);

        ProductDto result = service.getById(1L);

        assertThat(result).isEqualTo(dto);
        verify(cache).putProduct(dto);
    }

    @Test
    void getById_throws_when_missing() {
        when(cache.getProduct(99L)).thenReturn(null);
        when(productRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(99L)).isInstanceOf(NotFoundException.class);
    }
}
