package com.zeropick.recommendationservice.controller;

import com.zeropick.recommendationservice.client.ProductServiceClient;
import com.zeropick.recommendationservice.client.StockServiceClient;
import com.zeropick.recommendationservice.client.dto.ProductResponse;
import com.zeropick.recommendationservice.repository.BehaviorLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/recommendation-service/stock-forecast")
@RequiredArgsConstructor
public class StockForecastController {

    private final BehaviorLogRepository behaviorLogRepository;
    private final StockServiceClient stockServiceClient;
    private final ProductServiceClient productServiceClient;

    @GetMapping
    public List<Map<String, Object>> forecast(@RequestParam(defaultValue = "5") int limit) {
        List<Object[]> aggregates = behaviorLogRepository.aggregateOrderQty();
        if (aggregates.isEmpty()) {
            return List.of();
        }

        Map<Long, double[]> rateByProduct = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (Object[] row : aggregates) {
            Long productId = ((Number) row[0]).longValue();
            long totalQty = ((Number) row[1]).longValue();
            LocalDateTime firstAt = (LocalDateTime) row[2];
            long days = Math.max(1, Duration.between(firstAt, now).toDays());
            rateByProduct.put(productId, new double[]{totalQty, (double) totalQty / days});
        }

        Map<Long, Integer> stockById = new HashMap<>();
        try {
            for (Map<String, Object> s : stockServiceClient.getStocks(new ArrayList<>(rateByProduct.keySet()))) {
                stockById.put(((Number) s.get("productId")).longValue(),
                        ((Number) s.get("onlineStock")).intValue());
            }
        } catch (Exception e) {
            log.warn("[재고 예측] stock-service 조회 실패 — {}", e.getMessage());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, double[]> entry : rateByProduct.entrySet()) {
            Long productId = entry.getKey();
            Integer stock = stockById.get(productId);
            if (stock == null) {
                continue;
            }
            double dailyRate = entry.getValue()[1];
            double daysLeft = dailyRate > 0 ? stock / dailyRate : Double.MAX_VALUE;

            Map<String, Object> item = new HashMap<>();
            item.put("productId", productId);
            item.put("productName", safeName(productId));
            item.put("onlineStock", stock);
            item.put("totalOrdered", (long) entry.getValue()[0]);
            item.put("dailyRate", Math.round(dailyRate * 100.0) / 100.0);
            item.put("daysLeft", daysLeft == Double.MAX_VALUE ? null : Math.round(daysLeft * 10.0) / 10.0);
            result.add(item);
        }
        result.sort(Comparator.comparingDouble(
                m -> m.get("daysLeft") == null ? Double.MAX_VALUE : ((Number) m.get("daysLeft")).doubleValue()));
        return result.subList(0, Math.min(limit, result.size()));
    }

    private String safeName(Long productId) {
        try {
            ProductResponse p = productServiceClient.getProductById(productId);
            return p != null ? p.getName() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
