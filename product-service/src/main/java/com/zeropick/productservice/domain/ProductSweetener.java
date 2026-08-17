package com.zeropick.productservice.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "product_sweetener")
public class ProductSweetener {

    @EmbeddedId
    private ProductSweetenerId id;

    @Column(name = "amount_g", precision = 6, scale = 2)
    private BigDecimal amountG;

    protected ProductSweetener() {

    }

    public ProductSweetener(Long productId, Long sweetenerId, BigDecimal amountG) {
        this.id = new ProductSweetenerId(productId, sweetenerId);
        this.amountG = amountG;
    }

    public ProductSweetenerId getId() {
        return id;
    }

    public BigDecimal getAmountG() {
        return amountG;
    }

    public void setAmountG(BigDecimal amountG) {
        this.amountG = amountG;
    }
}
