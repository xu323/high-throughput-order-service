package com.xu.orderservice.service;

import com.xu.orderservice.cache.ProductCacheService;
import com.xu.orderservice.entity.LockStrategy;
import com.xu.orderservice.entity.ProductInventory;
import com.xu.orderservice.exception.InsufficientStockException;
import com.xu.orderservice.exception.LockAcquisitionFailedException;
import com.xu.orderservice.lock.RedisLockService;
import com.xu.orderservice.repository.ProductInventoryRepository;
import com.xu.orderservice.repository.StockDeductionLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Optional;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock ProductInventoryRepository inventoryRepo;
    @Mock StockDeductionLogRepository logRepo;
    @Mock RedisLockService redisLock;
    @Mock ProductCacheService cache;

    @InjectMocks InventoryService service;

    @Test
    void deduct_optimistic_succeeds_on_first_try() {
        when(inventoryRepo.conditionalDeduct(1L, 2)).thenReturn(1);

        assertThatCode(() -> service.deduct(1L, 2, 100L, LockStrategy.OPTIMISTIC)).doesNotThrowAnyException();
        verify(cache).evictInventory(1L);
    }

    @Test
    void deduct_optimistic_throws_insufficient_when_zero_rows_affected() {
        when(inventoryRepo.conditionalDeduct(1L, 999)).thenReturn(0);
        when(inventoryRepo.findByProductId(1L))
                .thenReturn(Optional.of(ProductInventory.builder().productId(1L).availableStock(10).version(0L).build()));
        assertThatThrownBy(() -> service.deduct(1L, 999, 100L, LockStrategy.OPTIMISTIC))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void deduct_optimistic_retries_on_optimistic_lock_failure_then_succeeds() {
        when(inventoryRepo.conditionalDeduct(1L, 1))
                .thenThrow(new OptimisticLockingFailureException("conflict"))
                .thenReturn(1);

        assertThatCode(() -> service.deduct(1L, 1, 100L, LockStrategy.OPTIMISTIC)).doesNotThrowAnyException();
        verify(inventoryRepo, times(2)).conditionalDeduct(1L, 1);
    }

    @Test
    void deduct_optimistic_throws_lock_acquisition_after_max_retries() {
        when(inventoryRepo.conditionalDeduct(1L, 1)).thenThrow(new OptimisticLockingFailureException("c"));
        assertThatThrownBy(() -> service.deduct(1L, 1, 100L, LockStrategy.OPTIMISTIC))
                .isInstanceOf(LockAcquisitionFailedException.class);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void deduct_redis_lock_runs_action_inside_lock() throws Exception {
        ProductInventory inv = ProductInventory.builder().productId(1L).availableStock(10).version(0L).build();
        when(inventoryRepo.findForUpdate(1L)).thenReturn(Optional.of(inv));
        when(inventoryRepo.save(any(ProductInventory.class))).thenAnswer(i -> i.getArgument(0));
        when(redisLock.runWithLock(eq("lock:inventory:1"), any(Callable.class)))
                .thenAnswer(inv2 -> ((Callable) inv2.getArgument(1)).call());

        assertThatCode(() -> service.deduct(1L, 3, 100L, LockStrategy.REDIS_LOCK)).doesNotThrowAnyException();
        verify(inventoryRepo).save(any(ProductInventory.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void deduct_redis_lock_throws_when_lock_not_acquired() throws Exception {
        when(redisLock.runWithLock(eq("lock:inventory:1"), any(Callable.class))).thenReturn(null);
        assertThatThrownBy(() -> service.deduct(1L, 3, 100L, LockStrategy.REDIS_LOCK))
                .isInstanceOf(LockAcquisitionFailedException.class);
    }
}
