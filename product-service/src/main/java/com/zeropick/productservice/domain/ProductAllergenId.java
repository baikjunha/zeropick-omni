package com.zeropick.productservice.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ProductAllergenId implements Serializable {

    private Long productId;
    private String allergen;

    protected ProductAllergenId() {

    }

    public ProductAllergenId(Long productId, String allergen) {
        this.productId = productId;
        this.allergen = allergen;
    }

    public Long getProductId() {
        return productId;
    }

    public String getAllergen() {
        return allergen;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductAllergenId)) return false;
        ProductAllergenId that = (ProductAllergenId) o;
        return Objects.equals(productId, that.productId)
                && Objects.equals(allergen, that.allergen);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, allergen);
    }
}
