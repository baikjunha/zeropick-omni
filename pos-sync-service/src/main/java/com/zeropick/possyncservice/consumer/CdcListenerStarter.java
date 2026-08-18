package com.zeropick.possyncservice.consumer;

import com.zeropick.possyncservice.client.StockClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CdcListenerStarter {

    private static final Logger log = LoggerFactory.getLogger(CdcListenerStarter.class);
    private static final int MAX_RETRY = 60;
    private static final long RETRY_MS = 5_000L;

    private final StockClient stockClient;
    private final KafkaListenerEndpointRegistry registry;

    public CdcListenerStarter(StockClient stockClient, KafkaListenerEndpointRegistry registry) {
        this.stockClient = stockClient;
        this.registry = registry;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void startWhenLedgerReady() {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                if (!stockClient.getStocks(List.of(1L)).isEmpty()) {
                    registry.getListenerContainer("posCdc").start();
                    registry.getListenerContainer("posDlqReplay").start();
                    log.info("[CDC 소비 시작] 재고 원장 준비 확인 (시도 {}회)", attempt);
                    return;
                }
            } catch (Exception e) {
                log.info("[CDC 대기] 재고 원장 준비 전 ({}/{}) — {}", attempt, MAX_RETRY, e.getMessage());
            }
            try {
                Thread.sleep(RETRY_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.error("[CDC 소비] 재고 원장 확인 실패 — 리스너 미시작");
    }
}
