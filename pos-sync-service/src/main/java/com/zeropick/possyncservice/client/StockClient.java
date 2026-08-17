package com.zeropick.possyncservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "stock-service")
public interface StockClient {

    /** POS 재고 절대값 반영 — 멱등이라 재시도·재처리에 안전하다. */
    @PutMapping("/stock-service/stocks/{productId}/pos")
    Map<String, Object> applyPos(@PathVariable("productId") Long productId,
                                 @RequestBody Map<String, Object> body);
}
