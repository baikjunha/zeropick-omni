package com.zeropick.recommendationservice.consumer;

import com.zeropick.recommendationservice.domain.BehaviorLog;
import com.zeropick.recommendationservice.repository.BehaviorLogRepository;
import com.zeropick.recommendationservice.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class BehaviorEventConsumer {

    private final BehaviorLogRepository behaviorLogRepository;
    private final RecommendationService recommendationService;

    @Transactional
    @KafkaListener(
            topics = {"product-viewed", "cart-added", "order-completed"},
            groupId = "${spring.kafka.consumer.group-id:recommendation-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeBehaviorEvent(ConsumerRecord<String, GenericRecord> record) {
        String topic = record.topic();
        GenericRecord value = record.value();

        if (value == null) {
            log.warn("[Kafka] 빈 메시지 수신: topic={}", topic);
            return;
        }

        try {

            String eventType = switch (topic) {
                case "product-viewed" -> "PRODUCT_VIEWED";
                case "cart-added" -> "CART_ADDED";
                case "order-completed" -> "ORDER_COMPLETED";
                default -> "UNKNOWN";
            };

            Long memberId = extractLong(value, "memberId");
            Long productId = extractLong(value, "productId");
            String category = extractString(value, "category");
            LocalDateTime occurredAt = extractDateTime(value, "occurredAt");

            Integer qty = extractInteger(value, "qty");
            Long unitPrice = extractLong(value, "unitPrice");
            String orderNo = extractString(value, "orderNo");
            String paymentMethod = extractString(value, "paymentMethod");

            BehaviorLog behaviorLog = BehaviorLog.builder()
                    .memberId(memberId)
                    .productId(productId)
                    .category(category)
                    .eventType(eventType)
                    .qty(qty)
                    .unitPrice(unitPrice)
                    .orderNo(orderNo)
                    .paymentMethod(paymentMethod)
                    .occurredAt(occurredAt != null ? occurredAt : LocalDateTime.now())
                    .build();

            behaviorLogRepository.save(behaviorLog);

            log.info("[Kafka 적재 성공] topic={}, memberId={}, productId={}, eventType={}",
                    topic, memberId, productId, eventType);

            if (memberId != null) {
                recommendationService.calculateAndGetRecommendations(memberId);
                log.info("[추천 점수 갱신 완료] memberId={}", memberId);
            }
        } catch (Exception e) {
            log.error("[Kafka 처리 실패] topic={}, error={}", topic, e.getMessage(), e);
        }
    }

    private Long extractLong(GenericRecord record, String fieldName) {
        if (record.getSchema().getField(fieldName) == null) return null;
        Object val = record.get(fieldName);
        return val != null ? ((Number) val).longValue() : null;
    }

    private Integer extractInteger(GenericRecord record, String fieldName) {
        if (record.getSchema().getField(fieldName) == null) return null;
        Object val = record.get(fieldName);
        return val != null ? ((Number) val).intValue() : null;
    }

    private String extractString(GenericRecord record, String fieldName) {
        if (record.getSchema().getField(fieldName) == null) return null;
        Object val = record.get(fieldName);
        return val != null ? val.toString() : null;
    }

    private LocalDateTime extractDateTime(GenericRecord record, String fieldName) {
        if (record.getSchema().getField(fieldName) == null) return LocalDateTime.now();
        Object val = record.get(fieldName);
        if (val instanceof Number num) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(num.longValue()), ZoneId.systemDefault());
        }
        return LocalDateTime.now();
    }
}
