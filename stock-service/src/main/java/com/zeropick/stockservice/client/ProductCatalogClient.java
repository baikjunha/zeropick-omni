package com.zeropick.stockservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@FeignClient(name = "product-service")
public interface ProductCatalogClient {

    @GetMapping("/product-service/products")
    List<Map<String, Object>> getAllProducts();
}
