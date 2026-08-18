package com.zeropick.stockservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock")
public class Stock {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "online_stock", nullable = false)
    private Integer onlineStock = 0;

    @Column(name = "pos_stock", nullable = false)
    private Integer posStock = 0;

    @Column(name = "store_code", length = 20)
    private String storeCode;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected Stock() {
    }

    public Stock(Long productId, Integer onlineStock) {
        this.productId = productId;
        this.onlineStock = onlineStock;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getProductId() {
        return productId;
    }

    public void setOnlineStock(Integer onlineStock) {
        this.onlineStock = onlineStock;
    }

    public Integer getOnlineStock() {
        return onlineStock;
    }

    public Integer getPosStock() {
        return posStock;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void applyPosStock(Integer posStock, String storeCode) {
        this.posStock = posStock;
        this.storeCode = storeCode;
        this.updatedAt = LocalDateTime.now();
    }
}
