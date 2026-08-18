package com.zeropick.productservice.service;

import com.zeropick.productservice.client.StockReadClient;
import com.zeropick.productservice.domain.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class StockOverlay {

    private static final Logger log = LoggerFactory.getLogger(StockOverlay.class);
    private static final int CHUNK = 300;

    private final StockReadClient stockReadClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public StockOverlay(StockReadClient stockReadClient, CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.stockReadClient = stockReadClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public void overlay(List<Product> products) {
        if (products.isEmpty()) {
            return;
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        Map<Long, Integer> online = new HashMap<>();
        for (int i = 0; i < ids.size(); i += CHUNK) {
            List<Long> chunk = ids.subList(i, Math.min(i + CHUNK, ids.size()));
            List<Map<String, Object>> rows = circuitBreakerFactory.create("stockLedger").run(
                    () -> stockReadClient.getStocks(chunk),
                    t -> {
                        log.debug("[재고 오버레이] stock-service 미응답 — 시드 재고 값 유지 ({})", t.getMessage());
                        return List.of();
                    });
            for (Map<String, Object> row : rows) {
                online.put(((Number) row.get("productId")).longValue(),
                        ((Number) row.get("onlineStock")).intValue());
            }
        }
        for (Product p : products) {
            Integer stock = online.get(p.getId());
            if (stock != null) {
                p.setStock(stock);
            }
        }
    }

    public void overlayOne(Product product) {
        overlay(List.of(product));
    }
}
