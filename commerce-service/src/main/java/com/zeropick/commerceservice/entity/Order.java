package com.zeropick.commerceservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {

    public enum Status { PENDING, PAID, COMPLETED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 20)
    private String orderNo;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

    @Column(nullable = false, length = 15)
    private String status = Status.PENDING.name();

    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt = LocalDateTime.now();

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    protected Order() {
    }

    public Order(String orderNo, Long memberId, Long totalPrice) {
        this.orderNo = orderNo;
        this.memberId = memberId;
        this.totalPrice = totalPrice;
    }

    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public Long getMemberId() { return memberId; }
    public Long getTotalPrice() { return totalPrice; }
    public String getStatus() { return status; }
    public String getPaymentMethod() { return paymentMethod; }
    public LocalDateTime getOrderedAt() { return orderedAt; }
    public LocalDateTime getPaidAt() { return paidAt; }

    public void markPaid(String paymentMethod) {
        this.status = Status.PAID.name();
        this.paymentMethod = paymentMethod;
        this.paidAt = LocalDateTime.now();
    }

    public void markCancelled() {
        this.status = Status.CANCELLED.name();
    }
}
