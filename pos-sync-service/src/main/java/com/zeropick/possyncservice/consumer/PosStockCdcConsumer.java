package com.zeropick.possyncservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeropick.possyncservice.client.StockClient;
import com.zeropick.possyncservice.status.SyncStatusTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Debezium CDC 이벤트 컨슈머 — 가상 POS 재고 변경을 재고 원장에 실시간 반영한다.
 *
 * 이벤트 봉투(JsonConverter, schemas.enable=true):
 *   { "schema": {...}, "payload": { "op": "c|u|d|r", "before": {...}, "after": {...}, "source": {...} } }
 *
 * 반영 정책:
 *  - after 의 절대값을 그대로 세팅한다 → 멱등. 스냅샷(r)·재시도·DLQ 재소비 모두 안전.
 *  - 삭제(d)는 posStock=0 처리.
 *  - 처리 실패는 3회 재시도 후 DLQ(zeropick.pos.dlq)로 보낸다 — CDC 신뢰성 요구(RTL-H #10).
 */
@Component
public class PosStockCdcConsumer {

    private static final Logger log = LoggerFactory.getLogger(PosStockCdcConsumer.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final String DLQ_TOPIC = "zeropick.pos.dlq";

    private final ObjectMapper objectMapper;
    private final StockClient stockClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SyncStatusTracker tracker;

    public PosStockCdcConsumer(ObjectMapper objectMapper, StockClient stockClient,
                               KafkaTemplate<String, String> kafkaTemplate, SyncStatusTracker tracker) {
        this.objectMapper = objectMapper;
        this.stockClient = stockClient;
        this.kafkaTemplate = kafkaTemplate;
        this.tracker = tracker;
    }

    @KafkaListener(topics = "zeropick.pos.pos.pos_stock", groupId = "pos-sync-service")
    public void onCdcEvent(String message) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                apply(message);
                return;
            } catch (Exception e) {
                log.warn("[POS 동기화 실패] 시도 {}/{} — {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    kafkaTemplate.send(DLQ_TOPIC, message);
                    tracker.recordDlq(e.getMessage());
                    log.error("[POS 동기화] 재시도 소진 → DLQ 전송");
                }
            }
        }
    }

    /** DLQ 재처리 — 운영자가 원인 해소 후 그대로 재소비하면 된다(절대값 반영이라 멱등). */
    @KafkaListener(topics = DLQ_TOPIC, groupId = "pos-sync-dlq-replayer", autoStartup = "false")
    public void onDlqReplay(String message) {
        try {
            apply(message);
            tracker.recordReplay();
        } catch (Exception e) {
            log.error("[DLQ 재처리 실패] {}", e.getMessage());
        }
    }

    private void apply(String message) throws Exception {
        JsonNode root = objectMapper.readTree(message);
        JsonNode payload = root.has("payload") ? root.get("payload") : root;
        String op = payload.path("op").asText("");

        if ("d".equals(op)) {
            JsonNode before = payload.get("before");
            if (before != null && !before.isNull()) {
                long productId = before.get("product_id").asLong();
                stockClient.applyPos(productId, Map.of("posStock", 0, "storeCode",
                        before.path("store_code").asText(null)));
                tracker.recordApplied(productId, 0, op);
            }
            return;
        }

        JsonNode after = payload.get("after");
        if (after == null || after.isNull()) {
            return;                            // 스키마 변경 등 데이터 없는 이벤트는 무시
        }
        long productId = after.get("product_id").asLong();
        int posStock = after.get("stock").asInt();
        String storeCode = after.path("store_code").asText(null);

        stockClient.applyPos(productId, Map.of("posStock", posStock, "storeCode", storeCode));
        tracker.recordApplied(productId, posStock, op);
        log.info("[POS 동기화] product={} posStock={} (op={})", productId, posStock, op);
    }
}
