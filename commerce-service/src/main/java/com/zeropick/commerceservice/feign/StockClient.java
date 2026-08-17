package com.zeropick.commerceservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "stock-service", path = "/stock-service/stocks")
public interface StockClient {

    @PutMapping("/{id}/deduct")
    StockInfo deduct(@PathVariable("id") Long id, @RequestBody Map<String, Integer> body);

    @PutMapping("/{id}/restore")
    StockInfo restore(@PathVariable("id") Long id, @RequestBody Map<String, Integer> body);

    record StockInfo(Long productId, Integer onlineStock, Integer posStock, Integer totalStock) {
    }
}
