package com.zeropick.commerceservice.dto;

import com.zeropick.commerceservice.entity.CartItem;
import com.zeropick.commerceservice.entity.Member;
import com.zeropick.commerceservice.entity.Order;
import com.zeropick.commerceservice.entity.OrderItem;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public final class CommerceDtos {

    private CommerceDtos() {
    }

    public record MemberCreate(@NotBlank @Email String email, @NotBlank String password, @NotBlank String name) {
    }

    public record MemberResponse(Long id, String email, String name) {
        public static MemberResponse of(Member m) {
            return new MemberResponse(m.getId(), m.getEmail(), m.getName());
        }
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record LoginResponse(Long memberId, String name, String token) {
    }

    public record CartAdd(@NotNull Long memberId, @NotNull Long productId, @NotNull @Min(1) Integer qty) {
    }

    public record CartQty(@NotNull @Min(1) Integer qty) {
    }

    public record CartItemResponse(Long id, Long memberId, Long productId, Integer qty) {
        public static CartItemResponse of(CartItem c) {
            return new CartItemResponse(c.getId(), c.getMemberId(), c.getProductId(), c.getQty());
        }
    }

    public record OrderCreateItem(@NotNull Long productId, @NotNull @Min(1) Integer qty) {
    }

    public record OrderCreate(@NotNull Long memberId, @NotEmpty List<OrderCreateItem> items) {
    }

    public record PayRequest(@NotBlank String paymentMethod) {
    }

    public record OrderItemResponse(Long productId, String productName, Integer qty, Long unitPrice) {
        public static OrderItemResponse of(OrderItem i) {
            return new OrderItemResponse(i.getProductId(), i.getProductName(), i.getQty(), i.getUnitPrice());
        }
    }

    public record OrderResponse(Long id, String orderNo, Long memberId, Long totalPrice,
                                String status, String paymentMethod, LocalDateTime orderedAt,
                                List<OrderItemResponse> items) {
        public static OrderResponse of(Order o, List<OrderItem> items) {
            return new OrderResponse(o.getId(), o.getOrderNo(), o.getMemberId(), o.getTotalPrice(),
                    o.getStatus(), o.getPaymentMethod(), o.getOrderedAt(),
                    items.stream().map(OrderItemResponse::of).toList());
        }
    }

    public record BehaviorRequest(@NotNull Long memberId, @NotNull Long productId,
                                  @NotBlank String eventType, @NotBlank String category,
                                  String occurredAt) {
    }

    public record ErrorResponse(String code, String message) {
    }
}
