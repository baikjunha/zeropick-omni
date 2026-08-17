package com.zeropick.productservice.service;

import com.zeropick.productservice.client.StockReadClient;
import com.zeropick.productservice.domain.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 상품 응답의 stock 값을 재고 원장(stock-service)의 온라인 재고로 덮어쓴다.
 *
 * stock-service 가 아직 준비 전이거나 장애일 때는 시드 시점의 자체 컬럼 값을
 * 그대로 둔다(폴백). stock-service 의 최초 원장 구성도 이 폴백 경로를 통해
 * 시드 재고를 읽어가므로 기동 순환 의존이 생기지 않는다.
 */
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
                    p.setStock(stock);          // 조회 전용 경로 — 트랜잭션 밖(detached)이라 DB 반영 없음
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
