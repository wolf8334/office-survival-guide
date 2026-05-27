package com.xhr.springai.officeSurvivalGuide.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class TempStorage {

    private final RedisTemplate<String,byte[]> redisTemplate;

    public void put(String token, byte[] data) {
        redisTemplate.opsForValue().set(token,data,1, TimeUnit.DAYS);
    }

    public byte[] get(String token) {
        return redisTemplate.opsForValue().get(token);
    }

}
