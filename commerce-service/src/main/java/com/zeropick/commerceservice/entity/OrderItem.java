package com.zeropick.commerceservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 상품명·단가는 주문 시점 스냅샷 — 상품 정보가 나중에 바뀌어도 주문 내역은 불변.
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 120)
    private String productName;

    @Column(nullable = false)
    private Integer qty;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    protected OrderItem() {
    }

    public OrderItem(Long orderId, Long productId, String productName, Integer qty, Long unitPrice) {
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.qty = qty;
        this.unitPrice = unitPrice;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Integer getQty() { return qty; }
    public Long getUnitPrice() { return unitPrice; }
}
