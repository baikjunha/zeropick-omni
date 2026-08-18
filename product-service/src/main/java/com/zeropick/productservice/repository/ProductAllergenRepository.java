package com.zeropick.productservice.repository;

import com.zeropick.productservice.domain.ProductAllergen;
import com.zeropick.productservice.domain.ProductAllergenId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductAllergenRepository extends JpaRepository<ProductAllergen, ProductAllergenId> {
    void deleteByIdProductId(Long productId);
}
