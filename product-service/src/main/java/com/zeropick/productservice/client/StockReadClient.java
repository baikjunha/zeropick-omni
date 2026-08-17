package com.zeropick.productservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "stock-service")
public interface StockReadClient {

    @GetMapping("/stock-service/stocks")
    List<Map<String, Object>> getStocks(@RequestParam("ids") List<Long> ids);
}
