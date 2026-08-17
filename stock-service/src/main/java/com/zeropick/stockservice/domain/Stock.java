package com.zeropick.stockservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * 재고 원장 — 재고의 단일 진실 공급원(source of truth).
 *
 * onlineStock : 온라인몰 판매 가능 재고 (주문 시 차감·취소 시 복구)
 * posStock    : 오프라인 매장 재고 (CDC 파이프라인이 절대값으로 동기화)
 * version     : 낙관적 락 — CDC 반영과 주문 차감이 동시에 들어와도 갱신 유실 방지
 */
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
