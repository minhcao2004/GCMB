/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 27/11/2026
 * Description: Cache ngắn hạn trong bộ nhớ cho các truy vấn tổng hợp báo cáo/dashboard; tự xóa khi có thao tác ghi.
 */

package com.gcmb.backend.dashboard;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedCaseInsensitiveMap;

@Component
public class ReportCache {

    private static final int MAX_ENTRIES = 1000;

    private record Entry(Object value, long expiresAt) {}

    private final Map<List<Object>, Entry> entries = new ConcurrentHashMap<>();
    // Tăng mỗi lần xóa để bỏ kết quả đã đọc trước hoặc trong lúc có thao tác ghi.
    private final AtomicLong generation = new AtomicLong();
    private final long ttlNanos;

    public ReportCache(@Value("${gcmb.report-cache.ttl-seconds:300}") long ttlSeconds) {
        this.ttlNanos = Duration.ofSeconds(Math.max(0, ttlSeconds)).toNanos();
    }

    /** Trả bản sao kết quả đã cache theo tên truy vấn và tham số; chạy loader khi chưa có hoặc đã hết hạn. */
    @SuppressWarnings("unchecked")
    public <T> T get(String name, Supplier<T> loader, Object... parameters) {
        if (ttlNanos == 0) return loader.get();
        List<Object> key = new ArrayList<>(parameters.length + 1);
        key.add(name);
        key.addAll(Arrays.asList(parameters));
        long now = System.nanoTime();
        Entry cached = entries.get(key);
        if (cached != null && now - cached.expiresAt() < 0) return (T) copy(cached.value());
        long startedGeneration = generation.get();
        T value = loader.get();
        if (generation.get() == startedGeneration) {
            if (entries.size() >= MAX_ENTRIES) entries.clear();
            entries.put(key, new Entry(copy(value), now + ttlNanos));
        }
        return value;
    }

    /** Xóa toàn bộ cache sau khi dữ liệu nghiệp vụ có thể đã thay đổi. */
    public void clear() {
        generation.incrementAndGet();
        entries.clear();
    }

    // Sao chép sâu List/Map để nơi gọi có thể sửa kết quả mà không làm hỏng bản trong cache.
    private static Object copy(Object value) {
        if (value instanceof LinkedCaseInsensitiveMap<?> map) {
            LinkedCaseInsensitiveMap<Object> copied = new LinkedCaseInsensitiveMap<>(map.size());
            map.forEach((key, item) -> copied.put(key, copy(item)));
            return copied;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copied = new LinkedHashMap<>(map.size());
            map.forEach((key, item) -> copied.put(key, copy(item)));
            return copied;
        }
        if (value instanceof List<?> list) {
            List<Object> copied = new ArrayList<>(list.size());
            list.forEach(item -> copied.add(copy(item)));
            return copied;
        }
        return value;
    }
}
