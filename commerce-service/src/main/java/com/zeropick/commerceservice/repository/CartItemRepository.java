package com.zeropick.commerceservice.repository;

import com.zeropick.commerceservice.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByMemberIdOrderByAddedAt(Long memberId);

    Optional<CartItem> findByMemberIdAndProductId(Long memberId, Long productId);
}
