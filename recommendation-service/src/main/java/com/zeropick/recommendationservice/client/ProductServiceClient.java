package com.zeropick.recommendationservice.client;

import com.zeropick.recommendationservice.client.dto.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

@FeignClient(name = "product-service")
public interface ProductServiceClient {

    @GetMapping("/product-service/products/{id}")
    ProductResponse getProductById(@PathVariable("id") Long id);

    @GetMapping("/product-service/products/compare")
    List<ProductResponse> getProductsByIds(@RequestParam("ids") List<Long> ids);

    @GetMapping("/product-service/products")
    List<ProductResponse> getProducts(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "sweetenerExclude", required = false) String sweetenerExclude,
            @RequestParam(value = "allergenExclude", required = false) String allergenExclude,
            @RequestParam(value = "sugarMax", required = false) BigDecimal sugarMax,
            @RequestParam(value = "kcalMin", required = false) BigDecimal kcalMin,
            @RequestParam(value = "kcalMax", required = false) BigDecimal kcalMax,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "sort", required = false) String sort
    );
}
