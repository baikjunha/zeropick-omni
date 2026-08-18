package com.zeropick.possyncservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeropick.possyncservice.client.StockClient;
import com.zeropick.possyncservice.status.SyncStatusTracker;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.log.LogAccessor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PosStockCdcConsumer {

    private static final Logger log = LoggerFactory.getLogger(PosStockCdcConsumer.class);
    private static final LogAccessor LOG_ACCESSOR = new LogAccessor(PosStockCdcConsumer.class);
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

    @KafkaListener(id = "posCdc", topics = "zeropick.pos.pos.pos_stock", groupId = "pos-sync-service", autoStartup = "false")
    public void onCdcEvent(ConsumerRecord<Object, Object> record) {
        Object value = record.value();
        if (value == null) {
            DeserializationException deserEx = SerializationUtils.getExceptionFromHeader(record,
                    SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER, LOG_ACCESSOR);
            if (deserEx != null) {
                tracker.recordDlq("Avro 역직렬화 실패: " + deserEx.getMessage());
                kafkaTemplate.send(DLQ_TOPIC,
                        "{\"deserializationError\":true,\"offset\":" + record.offset() + "}");
                log.error("[POS 동기화] 역직렬화 불가 메시지 → DLQ 표식 전송 (offset={})", record.offset());
            }
            return;
        }
        String json = value.toString();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                apply(json);
                return;
            } catch (Exception e) {
                log.warn("[POS 동기화 실패] 시도 {}/{} — {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    kafkaTemplate.send(DLQ_TOPIC, json);
                    tracker.recordDlq(e.getMessage());
                    log.error("[POS 동기화] 재시도 소진 → DLQ 전송");
                }
            }
        }
    }

    @KafkaListener(id = "posDlqReplay", topics = DLQ_TOPIC, groupId = "pos-sync-dlq-replayer",
            autoStartup = "false", containerFactory = "stringKafkaListenerContainerFactory")
    public void onDlqReplay(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            if (root.path("deserializationError").asBoolean(false)) {
                return;
            }
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
                Map<String, Object> body = new HashMap<>();
                body.put("posStock", 0);
                body.put("storeCode", before.path("store_code").asText(null));
                stockClient.applyPos(productId, body);
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

        Map<String, Object> body = new HashMap<>();
        body.put("posStock", posStock);
        body.put("storeCode", storeCode);
        stockClient.applyPos(productId, body);
        tracker.recordApplied(productId, posStock, op);
        log.info("[POS 동기화] product={} posStock={} (op={})", productId, posStock, op);
    }
}
