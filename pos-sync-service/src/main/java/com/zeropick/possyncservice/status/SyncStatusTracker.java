package com.zeropick.possyncservice.status;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** 동기화 처리 현황 집계 — 관리자 화면(재고 동기화 현황)의 데이터 원천. */
@Component
public class SyncStatusTracker {

    private static final int RECENT_LIMIT = 20;

    private final AtomicLong applied = new AtomicLong();
    private final AtomicLong dlq = new AtomicLong();
    private final AtomicLong replayed = new AtomicLong();
    private final Deque<Map<String, Object>> recent = new ArrayDeque<>();
    private volatile String lastError;
    private volatile LocalDateTime lastAppliedAt;

    public synchronized void recordApplied(long productId, int posStock, String op) {
        applied.incrementAndGet();
        lastAppliedAt = LocalDateTime.now();
        recent.addFirst(Map.of(
                "productId", productId, "posStock", posStock, "op", op,
                "at", lastAppliedAt.toString()));
        while (recent.size() > RECENT_LIMIT) {
            recent.removeLast();
        }
    }

    public void recordDlq(String reason) {
        dlq.incrementAndGet();
        lastError = reason;
    }

    public void recordReplay() {
        replayed.incrementAndGet();
    }

    public synchronized Map<String, Object> snapshot() {
        return Map.of(
                "appliedCount", applied.get(),
                "dlqCount", dlq.get(),
                "replayedCount", replayed.get(),
                "lastAppliedAt", lastAppliedAt == null ? "" : lastAppliedAt.toString(),
                "lastError", lastError == null ? "" : lastError,
                "recentEvents", new ArrayList<>(recent));
    }

    public List<Map<String, Object>> recentEvents() {
        synchronized (this) {
            return new ArrayList<>(recent);
        }
    }
}
