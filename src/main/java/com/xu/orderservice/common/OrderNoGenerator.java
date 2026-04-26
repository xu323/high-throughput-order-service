package com.xu.orderservice.common;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 訂單編號產生器：時間戳 + 序號 + 隨機尾碼。
 * 不要把資料庫主鍵直接暴露給客戶端。
 */
@Component
public class OrderNoGenerator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final AtomicLong seq = new AtomicLong(0);

    public String next() {
        long s = seq.incrementAndGet() % 10000;
        int rnd = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "ORD" + LocalDateTime.now().format(FMT) + String.format("%04d", s) + rnd;
    }
}
