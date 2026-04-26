package com.xu.orderservice.cache;

import com.xu.orderservice.dto.InventoryDto;
import com.xu.orderservice.dto.ProductDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 商品/庫存 cache（Cache-Aside Pattern）：
 *  - 讀取：先讀 Redis；miss 才打 DB；DB 結果再寫回 Redis。
 *  - 更新：先更新 DB → 再刪除 cache（不是更新 cache，避免併發寫造成髒讀）。
 *  - 庫存的 TTL 故意調短，避免 cache 與真實庫存差距過大。
 */
@Slf4j
@Service
public class ProductCacheService {

    private static final String PRODUCT_KEY = "product:%d";
    private static final String INVENTORY_KEY = "inventory:%d";

    private final RedisTemplate<String, Object> redis;

    @Value("${cache.product-ttl-seconds:300}")
    private long productTtl;

    @Value("${cache.inventory-ttl-seconds:30}")
    private long inventoryTtl;

    public ProductCacheService(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    public ProductDto getProduct(Long id) {
        Object v = redis.opsForValue().get(PRODUCT_KEY.formatted(id));
        return v instanceof ProductDto p ? p : null;
    }

    public void putProduct(ProductDto p) {
        redis.opsForValue().set(PRODUCT_KEY.formatted(p.id()), p, Duration.ofSeconds(productTtl));
    }

    public void evictProduct(Long id) {
        redis.delete(PRODUCT_KEY.formatted(id));
    }

    public InventoryDto getInventory(Long productId) {
        Object v = redis.opsForValue().get(INVENTORY_KEY.formatted(productId));
        return v instanceof InventoryDto i ? i : null;
    }

    public void putInventory(InventoryDto i) {
        redis.opsForValue().set(INVENTORY_KEY.formatted(i.productId()), i, Duration.ofSeconds(inventoryTtl));
    }

    public void evictInventory(Long productId) {
        redis.delete(INVENTORY_KEY.formatted(productId));
    }
}
