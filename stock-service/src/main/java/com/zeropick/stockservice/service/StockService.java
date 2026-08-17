package com.zeropick.stockservice.service;

import com.zeropick.stockservice.client.ProductCatalogClient;
import com.zeropick.stockservice.domain.Stock;
import com.zeropick.stockservice.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);
    private static final int INIT_MAX_RETRY = 30;
    private static final long INIT_RETRY_MS = 5_000L;

    private final StockRepository stockRepository;
    private final ProductCatalogClient productCatalogClient;

    public StockService(StockRepository stockRepository, ProductCatalogClient productCatalogClient) {
        this.stockRepository = stockRepository;
        this.productCatalogClient = productCatalogClient;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void initFromCatalog() {
        if (stockRepository.count() > 0) {
            log.info("[재고 초기화] 기존 원장 {}건 존재 — 건너뜀", stockRepository.count());
            return;
        }
        for (int attempt = 1; attempt <= INIT_MAX_RETRY; attempt++) {
            try {
                List<Map<String, Object>> products = productCatalogClient.getAllProducts();
                List<Stock> rows = products.stream()
                        .map(p -> new Stock(
                                ((Number) p.get("id")).longValue(),
                                p.get("stock") == null ? 0 : ((Number) p.get("stock")).intValue()))
                        .toList();
                stockRepository.saveAll(rows);
                log.info("[재고 초기화] 카탈로그 {}건 → 재고 원장 구성 완료", rows.size());
                return;
            } catch (Exception e) {
                log.warn("[재고 초기화] product-service 대기 중 ({}/{}) — {}",
                        attempt, INIT_MAX_RETRY, e.getMessage());
                try {
                    Thread.sleep(INIT_RETRY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("[재고 초기화] product-service 응답 없음 — 초기화 포기 (수동 재기동 필요)");
    }

    public Stock get(Long productId) {
        return stockRepository.findById(productId)
                .orElseThrow(() -> new StockNotFoundException(productId));
    }

    public List<Stock> getAll(List<Long> productIds) {
        return stockRepository.findByProductIdIn(productIds);
    }

    @Transactional
    public Stock deduct(Long productId, int qty) {
        if (!stockRepository.existsById(productId)) {
            throw new StockNotFoundException(productId);
        }
        int updated = stockRepository.deductIfEnough(productId, qty);
        if (updated == 0) {
            throw new OutOfStockException(productId, qty);
        }
        return get(productId);
    }

    @Transactional
    public Stock restore(Long productId, int qty) {
        int updated = stockRepository.restore(productId, qty);
        if (updated == 0) {
            throw new StockNotFoundException(productId);
        }
        return get(productId);
    }

    @Transactional
    public Stock applyPos(Long productId, int posStock, String storeCode) {
        Stock stock = stockRepository.findById(productId)
                .orElseGet(() -> stockRepository.save(new Stock(productId, 0)));
        stock.applyPosStock(posStock, storeCode);
        return stock;
    }

    public static class StockNotFoundException extends RuntimeException {
        public StockNotFoundException(Long productId) {
            super("재고 원장에 없는 상품입니다: " + productId);
        }
    }

    public static class OutOfStockException extends RuntimeException {
        public OutOfStockException(Long productId, int qty) {
            super("재고 부족: productId=" + productId + ", 요청 수량=" + qty);
        }
    }
}
