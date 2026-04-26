package com.xu.orderservice.repository;

import com.xu.orderservice.entity.ProductInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductInventoryRepository extends JpaRepository<ProductInventory, Long> {

    /** 預設讀取（支援 JPA 樂觀鎖；@Version 衝突時會丟擲 OptimisticLockException）。 */
    Optional<ProductInventory> findByProductId(Long productId);

    /** 悲觀寫入鎖（給 Redis 鎖策略內部使用，避免另一節點同時更新）。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ProductInventory i where i.productId = :productId")
    Optional<ProductInventory> findForUpdate(@Param("productId") Long productId);

    /**
     * 直接以 SQL 條件式扣庫存：available_stock - q >= 0 才更新。
     * 回傳影響筆數；若為 0 表示庫存不足或被其他交易搶走。
     * 這是另一個防超賣的常見手段，與樂觀鎖併用更穩。
     */
    @Modifying
    @Query(value = """
            UPDATE product_inventory
               SET available_stock = available_stock - :qty,
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE product_id = :productId
               AND available_stock >= :qty
            """, nativeQuery = true)
    int conditionalDeduct(@Param("productId") Long productId, @Param("qty") int qty);

    @Modifying
    @Query(value = """
            UPDATE product_inventory
               SET available_stock = available_stock + :qty,
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE product_id = :productId
            """, nativeQuery = true)
    int restock(@Param("productId") Long productId, @Param("qty") int qty);
}
