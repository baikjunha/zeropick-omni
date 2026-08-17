package com.zeropick.productservice.service;

import com.zeropick.productservice.client.StockReadClient;
import com.zeropick.productservice.domain.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class StockOverlay {

    private static final Logger log = LoggerFactory.getLogger(StockOverlay.class);

    private final StockReadClient stockReadClient;

    public StockOverlay(StockReadClient stockReadClient) {
        this.stockReadClient = stockReadClient;
    }

    public void overlay(List<Product> products) {
        if (products.isEmpty()) {
            return;
        }
        try {
            List<Long> ids = products.stream().map(Product::getId).toList();
            Map<Long, Integer> online = new HashMap<>();
            for (Map<String, Object> row : stockReadClient.getStocks(ids)) {
                online.put(((Number) row.get("productId")).longValue(),
                        ((Number) row.get("onlineStock")).intValue());
            }
            for (Product p : products) {
                Integer stock = online.get(p.getId());
                if (stock != null) {
                    p.setStock(stock);
                }
            }
        } catch (Exception e) {
            log.debug("[재고 오버레이] stock-service 미응답 — 시드 재고 값 유지 ({})", e.getMessage());
        }
    }

    public void overlayOne(Product product) {
        overlay(List.of(product));
    }
}
