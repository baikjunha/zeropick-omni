package com.zeropick.commerceservice.web;

import com.zeropick.commerceservice.dto.CommerceDtos.BehaviorRequest;
import com.zeropick.commerceservice.kafka.AvroEventPublisher;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/commerce-service/behaviors")
public class BehaviorController {

    private final AvroEventPublisher eventPublisher;

    public BehaviorController(AvroEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@Valid @RequestBody BehaviorRequest req) {
        if (!"PRODUCT_VIEWED".equals(req.eventType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_EVENT",
                    "이 경로는 PRODUCT_VIEWED 만 받습니다");
        }
        long occurredAt = parseOccurredAt(req.occurredAt());
        eventPublisher.publishProductViewed(req.memberId(), req.productId(), req.category(), occurredAt);
        return ResponseEntity.accepted().build();
    }

    private long parseOccurredAt(String value) {
        if (value == null || value.isBlank()) {
            return System.currentTimeMillis();
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
}
