package com.xu.orderservice.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * 簡化版 Redis 分散式鎖。
 * - 加鎖：SET key uuid NX PX leaseMillis
 * - 解鎖：Lua 腳本「比對 value 後刪除」，避免別人的鎖被誤刪。
 * 適合短臨界區、單機 Redis；跨機房請改用 Redisson + RedLock 或同等方案。
 */
@Slf4j
@Service
public class RedisLockService {

    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else return 0 end";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT =
            new DefaultRedisScript<>(UNLOCK_LUA, Long.class);

    private final StringRedisTemplate redis;

    @Value("${lock.redis.wait-millis:200}")
    private long waitMillis;

    @Value("${lock.redis.lease-millis:5000}")
    private long leaseMillis;

    public RedisLockService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 嘗試加鎖；最多等 wait-millis。回傳 token，呼叫端必須拿這個 token 解鎖。
     */
    public String tryLock(String key) {
        String token = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + waitMillis;
        do {
            Boolean ok = redis.opsForValue().setIfAbsent(key, token, Duration.ofMillis(leaseMillis));
            if (Boolean.TRUE.equals(ok)) {
                return token;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        } while (System.currentTimeMillis() < deadline);
        return null;
    }

    public boolean unlock(String key, String token) {
        if (token == null) return false;
        Long r = redis.execute(UNLOCK_SCRIPT, List.of(key), token);
        return r != null && r > 0;
    }

    /**
     * 包成 callable：取得鎖 → 執行 → 自動解鎖；若搶不到鎖回傳 null（呼叫端決定如何處理）。
     */
    public <T> T runWithLock(String key, Callable<T> action) throws Exception {
        String token = tryLock(key);
        if (token == null) {
            return null;
        }
        try {
            return action.call();
        } finally {
            unlock(key, token);
        }
    }
}
