package com.zeropick.commerceservice.repository;

import com.zeropick.commerceservice.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByMemberIdOrderByOrderedAtDesc(Long memberId);
}
