package com.xhr.springai.officeSurvivalGuide.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
public class TokenBucketService {

    private final long MAX_TOKEN;
    private final double ratio = 0.8;
    private final long ESTIMATED_TOKENS = 3000L;
    private final long CAPACITY;
    private final long REFILL_PER_MIN;
    private final long LOW_WATERMARK;

    private final Bucket bucket;

    public TokenBucketService() {
        MAX_TOKEN = 50000L;
        CAPACITY = (long) (MAX_TOKEN * ratio);
        REFILL_PER_MIN = (long) (MAX_TOKEN * ratio);
        LOW_WATERMARK = (long) (CAPACITY * 0.05);
        Bandwidth limit = Bandwidth.builder()
                .capacity(CAPACITY)
                .refillGreedy(REFILL_PER_MIN, Duration.ofMinutes(1))
                .build();
        this.bucket = Bucket.builder().addLimit(limit).build();
    }

    public TokenBucketService(long maxToken) {
        MAX_TOKEN = maxToken;
        CAPACITY = (long) (MAX_TOKEN * ratio);
        REFILL_PER_MIN = (long) (MAX_TOKEN * ratio);
        LOW_WATERMARK = (long) (CAPACITY * 0.05);
        Bandwidth limit = Bandwidth.builder()
                .capacity(CAPACITY)
                .refillGreedy(REFILL_PER_MIN, Duration.ofMinutes(1))
                .build();
        this.bucket = Bucket.builder().addLimit(limit).build();
    }

    public void reserveTokens() {
        reserveTokens(ESTIMATED_TOKENS);
    }

    public void reserveTokens(long tokens) {
        try {
            // 1. 低水位时等待恢复到安全水位
            long needed = LOW_WATERMARK - bucket.getAvailableTokens();
            if (needed > 0) {
                log.info("Token 不足，等待恢复中，当前剩余: {}, 低水位阈值: {}",bucket.getAvailableTokens(), LOW_WATERMARK);
                bucket.asBlocking().consume(needed);
                log.info("Token 已恢复，继续执行");
            }

            // 2. 预扣占位，阻塞直到令牌足够
            bucket.asBlocking().consume(ESTIMATED_TOKENS);
        } catch (Exception _) {
        }

    }

    public long getReversedTokens(){
        return ESTIMATED_TOKENS;
    }

    public void settle(long estimated, long actual) {
        long delta = actual - estimated;
        if (delta < 0) {
            bucket.forceAddTokens(-delta);
        }
    }

    public boolean isHealthy() {
        return bucket.getAvailableTokens() >= LOW_WATERMARK;
    }

    private long estimateWaitMs() {
        long needed = LOW_WATERMARK - bucket.getAvailableTokens();
        return (needed * 1000L) / (REFILL_PER_MIN / 60);
    }
}
