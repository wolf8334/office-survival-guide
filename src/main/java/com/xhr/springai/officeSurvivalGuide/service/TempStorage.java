package com.xhr.springai.officeSurvivalGuide.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TempStorage {
    private final Map<String, byte[]> store = new ConcurrentHashMap<>();

    public void put(String token, byte[] data) {
        store.put(token, data);
    }

    public byte[] get(String token) {
        return store.get(token);
    }

    // 定时清理
    @Scheduled(fixedDelay = 24 * 60 * 60 * 1000)
    public void cleanup() {
        store.clear();
    }
}
