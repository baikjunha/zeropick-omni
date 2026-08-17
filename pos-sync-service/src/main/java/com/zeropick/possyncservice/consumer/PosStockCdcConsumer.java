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
            return;
        }
        long productId = after.get("product_id").asLong();
        int posStock = after.get("stock").asInt();
        String storeCode = after.path("store_code").asText(null);

        stockClient.applyPos(productId, Map.of("posStock", posStock, "storeCode", storeCode));
        tracker.recordApplied(productId, posStock, op);
        log.info("[POS 동기화] product={} posStock={} (op={})", productId, posStock, op);
    }
}
