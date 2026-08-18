package com.zeropick.productservice.repository;

import com.zeropick.productservice.domain.ProductSweetener;
import com.zeropick.productservice.domain.ProductSweetenerId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductSweetenerRepository extends JpaRepository<ProductSweetener, ProductSweetenerId> {
    void deleteByIdProductId(Long productId);
}
