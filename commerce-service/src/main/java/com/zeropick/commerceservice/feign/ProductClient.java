package com.zeropick.commerceservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service", path = "/product-service/products")
public interface ProductClient {

    @GetMapping("/{id}")
    ProductInfo get(@PathVariable("id") Long id);

    record ProductInfo(Long id, String name, String category, Integer price, Integer stock) {
    }
}
