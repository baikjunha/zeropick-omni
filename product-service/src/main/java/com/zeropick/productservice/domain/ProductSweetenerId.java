package com.zeropick.productservice.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ProductSweetenerId implements Serializable {

    private Long productId;
    private Long sweetenerId;

    protected ProductSweetenerId() {

    }

    public ProductSweetenerId(Long productId, Long sweetenerId) {
        this.productId = productId;
        this.sweetenerId = sweetenerId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getSweetenerId() {
        return sweetenerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductSweetenerId)) return false;
        ProductSweetenerId that = (ProductSweetenerId) o;
        return Objects.equals(productId, that.productId)
                && Objects.equals(sweetenerId, that.sweetenerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, sweetenerId);
    }
}
