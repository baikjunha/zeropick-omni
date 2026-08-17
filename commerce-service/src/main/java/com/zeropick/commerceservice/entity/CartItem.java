package com.zeropick.commerceservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "cart_item", uniqueConstraints = @UniqueConstraint(name = "uk_cart", columnNames = {"member_id", "product_id"}))
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer qty;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt = LocalDateTime.now();

    protected CartItem() {
    }

    public CartItem(Long memberId, Long productId, Integer qty) {
        this.memberId = memberId;
        this.productId = productId;
        this.qty = qty;
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public Long getProductId() { return productId; }
    public Integer getQty() { return qty; }
    public LocalDateTime getAddedAt() { return addedAt; }

    public void setQty(Integer qty) { this.qty = qty; }
}
