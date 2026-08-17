package com.zeropick.recommendationservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "stock-service", contextId = "stockServiceClient")
public interface StockServiceClient {

    @GetMapping("/stock-service/stocks")
    List<Map<String, Object>> getStocks(@RequestParam("ids") List<Long> ids);
}
