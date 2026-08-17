package com.zeropick.possyncservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "stock-service")
public interface StockClient {

    @PutMapping("/stock-service/stocks/{productId}/pos")
    Map<String, Object> applyPos(@PathVariable("productId") Long productId,
                                 @RequestBody Map<String, Object> body);
}
