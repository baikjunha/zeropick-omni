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
        for (int attempt = 1; attempt <= INIT_MAX_RETRY; attempt++) {
            try {
                List<Map<String, Object>> products = productCatalogClient.getAllProducts();
                int[] counts = applyCatalog(products);
                log.info("[재고 초기화] 카탈로그 {}건 — 신규 {}, 보충 {}",
                        products.size(), counts[0], counts[1]);
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

    @Transactional
    public Map<String, Integer> syncFromCatalog() {
        List<Map<String, Object>> products = productCatalogClient.getAllProducts();
        int[] counts = applyCatalog(products);
        log.info("[재고 동기화] 카탈로그 {}건 — 신규 {}, 보충 {}",
                products.size(), counts[0], counts[1]);
        return Map.of("total", products.size(), "created", counts[0], "refilled", counts[1]);
    }

    private int[] applyCatalog(List<Map<String, Object>> products) {
        int created = 0;
        int filled = 0;
        for (Map<String, Object> p : products) {
            long productId = ((Number) p.get("id")).longValue();
            int seedStock = p.get("stock") == null ? 0 : ((Number) p.get("stock")).intValue();
            Stock existing = stockRepository.findById(productId).orElse(null);
            if (existing == null) {
                stockRepository.save(new Stock(productId, seedStock));
                created++;
            } else if (existing.getOnlineStock() == 0 && seedStock > 0) {
                stockRepository.restore(productId, seedStock);
                filled++;
            }
        }
        return new int[]{created, filled};
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
