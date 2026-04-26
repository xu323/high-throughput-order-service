package com.xu.orderservice.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisLockServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock @SuppressWarnings("rawtypes") ValueOperations valueOps;

    @InjectMocks RedisLockService lockService;

    @BeforeEach
    void init() {
        ReflectionTestUtils.setField(lockService, "waitMillis", 50L);
        ReflectionTestUtils.setField(lockService, "leaseMillis", 1000L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryLock_returns_token_when_setIfAbsent_succeeds() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("k"), any(String.class), any(Duration.class))).thenReturn(true);

        String token = lockService.tryLock("k");
        assertThat(token).isNotBlank();
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryLock_returns_null_when_always_taken() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(false);

        String token = lockService.tryLock("k");
        assertThat(token).isNull();
    }

    @Test
    void unlock_returns_true_when_script_returns_1() {
        when(redis.execute(any(RedisScript.class), eq(List.of("k")), eq("t"))).thenReturn(1L);
        assertThat(lockService.unlock("k", "t")).isTrue();
    }

    @Test
    void unlock_returns_false_when_script_returns_0() {
        when(redis.execute(any(RedisScript.class), eq(List.of("k")), eq("t"))).thenReturn(0L);
        assertThat(lockService.unlock("k", "t")).isFalse();
    }
}
