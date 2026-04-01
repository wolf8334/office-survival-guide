package com.xhr.springai.officeSurvivalGuide.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final StringRedisTemplate redisTemplate;

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * 存入普通字符串
     */
    public void set(String key, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 存入对象（自动转JSON）
     */
    public void setObject(String key, Object value, long timeout, TimeUnit unit) {
        try {
            String json = mapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, timeout, unit);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Redis序列化对象失败", e);
        }
    }

    /**
     * 存入对象（自动转JSON）
     */
    public void setObject(String key, Object value) {
        try {
            String json = mapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Redis序列化对象失败", e);
        }
    }

    /**
     * 获取字符串
     */
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 获取对象（自动从 JSON 转回）
     */
    public <T> T getObject(String key, Class<T> clazz) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Redis反序列化对象失败", e);
        }
    }

    /**
     * 删除 Key
     */
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * 检查是否存在
     */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }
}
