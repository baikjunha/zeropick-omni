package com.zeropick.commerceservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// Eureka 서비스명으로 product-service 를 호출한다. 컨트롤러가 프리픽스를 포함하므로 path 도 동일하게.
@FeignClient(name = "product-service", path = "/product-service/products")
public interface ProductClient {

    @GetMapping("/{id}")
    ProductInfo get(@PathVariable("id") Long id);

    record ProductInfo(Long id, String name, String category, Integer price, Integer stock) {
    }
}
