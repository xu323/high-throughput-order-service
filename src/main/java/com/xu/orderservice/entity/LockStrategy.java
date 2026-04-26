package com.xu.orderservice.entity;

/**
 * 防止超賣的兩種策略：
 *  - OPTIMISTIC：MySQL version 樂觀鎖，重試 N 次。適合衝突率不高、強一致性需求。
 *  - REDIS_LOCK：Redis 分散式鎖串行化臨界區。適合短臨界區、跨多節點。
 */
public enum LockStrategy {
    OPTIMISTIC,
    REDIS_LOCK
}
