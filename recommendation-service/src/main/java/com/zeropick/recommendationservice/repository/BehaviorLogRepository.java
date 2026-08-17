package com.zeropick.recommendationservice.repository;

import com.zeropick.recommendationservice.domain.BehaviorLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BehaviorLogRepository extends JpaRepository<BehaviorLog, Long> {
    List<BehaviorLog> findByMemberIdOrderByOccurredAtDesc(Long memberId);

    /** 상품별 누적 주문 수량 — 재고 소진 예측의 판매 속도 추정에 쓴다. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT b.productId AS productId, SUM(COALESCE(b.qty, 1)) AS totalQty, MIN(b.occurredAt) AS firstAt "
            + "FROM BehaviorLog b WHERE b.eventType = 'ORDER_COMPLETED' AND b.productId IS NOT NULL "
            + "GROUP BY b.productId")
    List<Object[]> aggregateOrderQty();
}