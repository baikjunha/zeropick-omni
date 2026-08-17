package com.zeropick.productservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 재고 원장(stock-service) 읽기 전용 클라이언트.
 * 재고 소유권은 stock-service 로 분리됐고, 상품 응답의 stock 은 이 조회로 덮어쓴다.
 */
@FeignClient(name = "stock-service")
public interface StockReadClient {

    @GetMapping("/stock-service/stocks")
    List<Map<String, Object>> getStocks(@RequestParam("ids") List<Long> ids);
}
