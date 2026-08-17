package com.zeropick.stockservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

/**
 * 초기 적재용 카탈로그 조회. 상품 시드의 productId 와 초기 재고값을 그대로 가져와
 * 재고 원장을 구성한다 — CSV 를 중복 파싱하지 않으므로 ID 정합이 항상 보장된다.
 */
@FeignClient(name = "product-service")
public interface ProductCatalogClient {

    @GetMapping("/product-service/products")
    List<Map<String, Object>> getAllProducts();
}
